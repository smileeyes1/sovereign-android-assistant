package ps.hakim.phoneagent

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONObject

/** ينفذ فقط بروتوكول حكيم المحدود، ويعيد فحص كل خطوة محليًا قبل التنفيذ. */
object HakimReasoningPlanExecutor {
    data class Outcome(
        val progressed: Boolean,
        val completed: Boolean,
        val steps: Int,
        val needsApproval: Boolean,
        val needsDataTrust: Boolean,
        val blocked: Boolean,
        val reason: String
    )

    fun run(
        activity: Activity,
        plan: HakimReasoningProtocol.Plan,
        onProgress: (String) -> Unit = {},
        onComplete: (Outcome) -> Unit
    ) {
        val handler = Handler(Looper.getMainLooper())
        val service = HakimAccessibilityService.instance
        if (service == null) {
            onComplete(Outcome(false, false, 0, false, false, true, "خدمة الوصول غير مفعلة"))
            return
        }
        var index = 0
        var steps = 0
        var progressed = false
        var finished = false

        fun finish(
            completed: Boolean,
            needsApproval: Boolean = false,
            needsDataTrust: Boolean = false,
            blocked: Boolean = false,
            reason: String
        ) {
            if (finished) return
            finished = true
            onComplete(Outcome(progressed, completed, steps, needsApproval, needsDataTrust, blocked, reason))
        }

        lateinit var next: () -> Unit
        next = {
            if (finished || activity.isFinishing || activity.isDestroyed) {
                if (!finished) finish(false, reason = "انتهت واجهة التنفيذ")
            } else if (index >= plan.actions.size) {
                finish(plan.done, reason = if (plan.done) "محرك الاستدلال أعلن اكتمال الجولة" else "انتهت أفعال الجولة وتحتاج إعادة استدلال")
            } else {
                val action = plan.actions[index++]
                val snapshot = service.uiSnapshot(140)
                when (action.type) {
                    "open_url" -> {
                        val raw = action.args.optString("url").trim()
                        val uri = runCatching { Uri.parse(raw) }.getOrNull()
                        if (uri == null || uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
                            finish(false, blocked = true, reason = "رفضت رابطًا غير آمن أو غير صالح من خطة الاستدلال")
                        } else {
                            activity.getSharedPreferences("hakim", Activity.MODE_PRIVATE).edit().putString("last_url", raw).apply()
                            activity.startActivity(Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                            progressed = true
                            steps += 1
                            onProgress("فتحت المسار الذي اختاره الوكيل بعد التحقق من أنه رابط ويب آمن.")
                            handler.postDelayed(next, 900L)
                        }
                    }
                    "click_text" -> {
                        val target = action.args.optString("text").trim()
                        val decision = HakimActionPolicy.classify(target, snapshot)
                        when (decision.level) {
                            HakimActionPolicy.Level.BLOCK -> finish(false, blocked = true, reason = decision.reason)
                            HakimActionPolicy.Level.APPROVAL -> finish(false, needsApproval = true, reason = decision.reason)
                            HakimActionPolicy.Level.AUTO -> runOnHakimBrowser(activity, handler) {
                                val ok = service.action(JSONObject().put("action", "click_text").put("text", target))
                                if (!ok) finish(false, reason = "لم أجد العنصر الذي حدده الاستدلال")
                                else {
                                    progressed = true; steps += 1
                                    onProgress("نفذت نقرة منخفضة الأثر على «${target.take(120)}».")
                                    handler.postDelayed(next, 600L)
                                }
                            }
                        }
                    }
                    "set_text" -> {
                        val target = action.args.optString("target").trim()
                        val value = action.args.optString("value")
                        val decision = HakimActionPolicy.classify("$target $value", snapshot)
                        if (decision.level == HakimActionPolicy.Level.BLOCK) {
                            finish(false, blocked = true, reason = decision.reason)
                        } else if (decision.level == HakimActionPolicy.Level.APPROVAL) {
                            finish(false, needsApproval = true, reason = decision.reason)
                        } else if (containsStoredProfileValue(activity, value) && !HakimSiteTrust.canUseProfile(activity, snapshot)) {
                            finish(false, needsDataTrust = true, reason = "الخطة ستستخدم قيمة من خزنة المستخدم في موقع غير معتمد")
                        } else {
                            runOnHakimBrowser(activity, handler) {
                                val ok = service.action(
                                    JSONObject().put("action", "set_text").put("text", target).put("value", value.take(6000))
                                )
                                if (!ok) finish(false, reason = "تعذر العثور على الحقل المحدد في الخطة")
                                else {
                                    progressed = true; steps += 1
                                    onProgress("كتبت محتوى غير حساس في «${target.take(120)}» بعد فحص السياسة.")
                                    handler.postDelayed(next, 500L)
                                }
                            }
                        }
                    }
                    "back" -> runOnHakimBrowser(activity, handler) {
                        val ok = service.action(JSONObject().put("action", "back"))
                        if (!ok) finish(false, reason = "تعذر الرجوع")
                        else {
                            progressed = true; steps += 1
                            handler.postDelayed(next, 500L)
                        }
                    }
                    "wait" -> {
                        val ms = action.args.optLong("ms", 700L).coerceIn(150L, 2500L)
                        handler.postDelayed(next, ms)
                    }
                    else -> finish(false, blocked = true, reason = "نوع فعل غير مسموح")
                }
            }
        }
        handler.post(next)
    }

    private fun runOnHakimBrowser(activity: Activity, handler: Handler, block: () -> Unit) {
        val pkg = HakimAccessibilityService.instance?.foregroundPackage().orEmpty()
        if (pkg.startsWith("ps.hakim.stable")) {
            block()
        } else {
            activity.startActivity(Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            handler.postDelayed(block, 750L)
        }
    }

    private fun containsStoredProfileValue(activity: Activity, value: String): Boolean {
        val v = value.trim()
        if (v.isBlank()) return false
        return HakimPersonalVault.all(activity).values.any { saved ->
            val s = saved.trim()
            s.length >= 3 && (v == s || v.contains(s))
        }
    }
}
