package ps.hakim.phoneagent

import android.app.Activity
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject

/**
 * حلقة تنفيذ محلية مغلقة: شاشة -> فعل آمن -> تحقق -> إعادة تقدير.
 * لا تتخذ قرارًا عالي الأثر ولا تكتب في حقل حساس ولا تكشف بيانات الخزنة لموقع غير معتمد.
 */
object HakimAutonomousExecutor {
    data class Outcome(
        val progressed: Boolean,
        val completed: Boolean,
        val steps: Int,
        val needsApproval: Boolean,
        val needsCredential: Boolean,
        val needsDataTrust: Boolean,
        val reason: String
    )

    private const val MAX_STEPS = 8

    fun run(
        activity: Activity,
        goal: String,
        onProgress: (String) -> Unit = {},
        onComplete: (Outcome) -> Unit
    ) {
        val handler = Handler(Looper.getMainLooper())
        val service = HakimAccessibilityService.instance
        if (service == null) {
            onComplete(Outcome(false, false, 0, false, false, false, "خدمة الوصول غير مفعلة"))
            return
        }

        val seen = linkedSetOf<String>()
        var steps = 0
        var progressed = false
        var finished = false

        fun finish(
            completed: Boolean,
            needsApproval: Boolean = false,
            needsCredential: Boolean = false,
            needsDataTrust: Boolean = false,
            reason: String
        ) {
            if (finished) return
            finished = true
            onComplete(Outcome(progressed, completed, steps, needsApproval, needsCredential, needsDataTrust, reason))
        }

        lateinit var iterate: () -> Unit
        iterate = {
            if (finished || activity.isFinishing || activity.isDestroyed) {
                if (!finished) finish(false, reason = "انتهت واجهة التنفيذ")
            } else {
                val snapshot = service.uiSnapshot(160)
                if (snapshot.length() == 0) {
                    finish(false, reason = "لا توجد عناصر شاشة قابلة للفهم الآن")
                } else if (HakimActionPolicy.isSuccessState(snapshot) && progressed) {
                    finish(true, reason = "ظهرت حالة نجاح مرئية بعد التنفيذ")
                } else if (steps >= MAX_STEPS) {
                    finish(false, reason = "وصلت الحلقة إلى حد الخطوات الآمن وتحتاج إعادة تقدير")
                } else {
                    val fingerprint = fingerprint(snapshot)
                    if (!seen.add(fingerprint) && progressed) {
                        finish(false, reason = "لم تتغير الشاشة بعد آخر خطوة؛ أوقفت التكرار")
                    } else {
                        val profileCandidate = firstProfileCandidate(activity, snapshot)
                        if (profileCandidate != null && !HakimSiteTrust.canUseProfile(activity, snapshot)) {
                            finish(
                                false,
                                needsDataTrust = true,
                                reason = "الموقع/التطبيق الحالي غير معتمد لإخراج بيانات خزنة حكيم"
                            )
                        } else if (profileCandidate != null) {
                            val (node, match) = profileCandidate
                            val field = match.first
                            val value = match.second
                            val id = node.optString("id")
                            val textHint = visibleLabel(node)
                            val ok = service.action(
                                JSONObject()
                                    .put("action", "set_text")
                                    .put("id", id)
                                    .put("text", textHint)
                                    .put("value", value)
                            )
                            if (ok) {
                                progressed = true
                                steps += 1
                                onProgress("عبأت «${field.title}» محليًا في موقع معتمد دون إرسال القيمة إلى نموذج الذكاء.")
                                handler.postDelayed(iterate, 500L)
                            } else {
                                finish(false, reason = "تعذر تعبئة الحقل المطابق بأمان")
                            }
                        } else if (HakimActionPolicy.screenHasSensitiveInput(snapshot)) {
                            finish(false, needsCredential = true, reason = "توجد خطوة اعتماد حساسة؛ تُترك لمدير اعتماد أندرويد/الحقل الآمن")
                        } else {
                            val next = nextSafeContinuation(snapshot)
                            if (next != null) {
                                val ok = service.action(JSONObject().put("action", "click_text").put("text", next))
                                if (ok) {
                                    progressed = true
                                    steps += 1
                                    onProgress("نفذت الخطوة الآمنة التالية «$next» وتحققت من الانتقال قبل المتابعة.")
                                    handler.postDelayed(iterate, 650L)
                                } else {
                                    finish(false, reason = "تعذر تنفيذ عنصر المتابعة الظاهر")
                                }
                            } else {
                                val gated = nextApprovalAction(snapshot)
                                if (gated != null) {
                                    finish(false, needsApproval = true, reason = "وصلت إلى الفعل النهائي «$gated» بعد إكمال التحضير الآمن")
                                } else {
                                    finish(false, reason = if (progressed) "أغلقت كل الخطوات المحلية الواضحة وتحتاج المهمة استدلالًا إضافيًا" else "لا يوجد فعل محلي واضح وآمن يمكن استنتاجه من الشاشة")
                                }
                            }
                        }
                    }
                }
            }
        }

        onProgress("بدأ حكيم دورة التنفيذ الذاتي للهدف: ${goal.take(180)}")
        handler.post(iterate)
    }

    private fun firstProfileCandidate(activity: Activity, snapshot: JSONArray): Pair<JSONObject, Pair<HakimPersonalVault.Field, String>>? {
        for (i in 0 until snapshot.length()) {
            val node = snapshot.optJSONObject(i) ?: continue
            if (!node.optBoolean("editable", false) || node.optBoolean("sensitive", false)) continue
            val label = buildString {
                append(node.optString("text")); append(' ')
                append(node.optString("desc")); append(' ')
                append(node.optString("id"))
            }.trim()
            if (label.isBlank()) continue
            if (HakimActionPolicy.classify(label, snapshot).level != HakimActionPolicy.Level.AUTO) continue
            val match = HakimPersonalVault.valueForLabel(activity, label) ?: continue
            val visibleText = node.optString("text").trim()
            if (visibleText == match.second) continue
            return node to match
        }
        return null
    }

    private fun nextSafeContinuation(snapshot: JSONArray): String? {
        val preferred = listOf(
            "التالي", "متابعة", "استمرار", "أكمل", "اكمل", "تابع", "حسنًا", "حسنا",
            "next", "continue", "proceed", "ok"
        )
        for (candidate in preferred) {
            for (i in 0 until snapshot.length()) {
                val node = snapshot.optJSONObject(i) ?: continue
                if (!node.optBoolean("clickable", false) || node.optBoolean("sensitive", false)) continue
                val label = visibleLabel(node)
                if (label.isBlank()) continue
                if (!label.lowercase().contains(candidate.lowercase())) continue
                if (!HakimActionPolicy.isSafeContinuation(label, snapshot)) continue
                return label
            }
        }
        return null
    }

    private fun nextApprovalAction(snapshot: JSONArray): String? {
        for (i in 0 until snapshot.length()) {
            val node = snapshot.optJSONObject(i) ?: continue
            if (!node.optBoolean("clickable", false) || node.optBoolean("sensitive", false)) continue
            val label = visibleLabel(node)
            if (label.isBlank()) continue
            if (HakimActionPolicy.classify(label, snapshot).level == HakimActionPolicy.Level.APPROVAL) return label
        }
        return null
    }

    private fun visibleLabel(node: JSONObject): String =
        node.optString("text").trim().ifBlank { node.optString("desc").trim() }.take(240)

    private fun fingerprint(snapshot: JSONArray): String {
        val s = buildString {
            for (i in 0 until snapshot.length().coerceAtMost(80)) {
                val n = snapshot.optJSONObject(i) ?: continue
                append(n.optString("package")); append('|')
                append(n.optString("id")); append('|')
                append(if (n.optBoolean("sensitive", false)) "[مخفي]" else n.optString("text")); append('|')
                append(if (n.optBoolean("sensitive", false)) "[مخفي]" else n.optString("desc")); append(';')
            }
        }
        return s.hashCode().toString()
    }
}
