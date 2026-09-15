package ps.hakim.phoneagent

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject

/**
 * ينفذ فقط بروتوكول حكيم المحدود، ويعيد فحص كل خطوة محليًا على الصفحة الفعلية قبل التنفيذ.
 * المسار الأول هو WebView حكيم نفسه بأقل صلاحية؛ Accessibility يبقى احتياطًا فقط إن كان متاحًا.
 */
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
        if (HakimMissionLedger.isCancelled(activity)) {
            onComplete(Outcome(false, false, 0, false, false, false, "المهمة ملغاة بأمر المستخدم"))
            return
        }
        val mission = HakimMissionLedger.active(activity)
        val professionalAudit = HakimDeliberationQuality.audit(plan, mission?.goal.orEmpty())
        if (!professionalAudit.acceptable) {
            if (professionalAudit.blocked) {
                HakimMissionLedger.block(activity, professionalAudit.reason)
            } else {
                HakimMissionLedger.progress(activity, HakimMissionLedger.Phase.PLAN, professionalAudit.reason)
            }
            onComplete(
                Outcome(
                    progressed = false,
                    completed = false,
                    steps = 0,
                    needsApproval = false,
                    needsDataTrust = false,
                    blocked = professionalAudit.blocked,
                    reason = professionalAudit.reason
                )
            )
            return
        }
        if ((mission?.failures ?: 0) >= 5) {
            HakimMissionLedger.block(activity, "تجاوزت المهمة حد الإخفاقات؛ يلزم تغير دليل/حالة قبل التنفيذ")
            onComplete(Outcome(false, false, 0, false, false, true, "أوقف حاكم الاستمرارية التنفيذ بعد فشل متكرر"))
            return
        }
        val transactionGate = HakimExecutionTransaction.prepare(activity, mission, plan)
        if (!transactionGate.proceed) {
            HakimMissionLedger.progress(
                activity,
                if (transactionGate.requiresVerification) HakimMissionLedger.Phase.VERIFY else HakimMissionLedger.Phase.PLAN,
                transactionGate.reason
            )
            onComplete(Outcome(false, false, 0, false, false, false, transactionGate.reason))
            return
        }

        val handler = Handler(Looper.getMainLooper())
        val service = HakimAccessibilityService.instance
        HakimMissionLedger.progress(activity, HakimMissionLedger.Phase.EXECUTE, "بدء تنفيذ خطة الاستدلال المقيدة بعد اجتياز التدقيق المهني ${professionalAudit.score}/100", attempted = true)
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
            if (HakimMissionLedger.isCancelled(activity)) {
                onComplete(Outcome(progressed, false, steps, false, false, false, "المهمة ملغاة بأمر المستخدم"))
                return
            }
            when {
                completed -> HakimSovereignEngine.complete(activity, reason)
                needsApproval -> HakimMissionLedger.progress(activity, HakimMissionLedger.Phase.WAITING_APPROVAL, reason)
                needsDataTrust -> HakimMissionLedger.progress(activity, HakimMissionLedger.Phase.WAITING_TRUST, reason)
                blocked -> HakimMissionLedger.block(activity, reason)
                isActualFailure(reason) -> {
                    HakimExecutionTransaction.markRecovering(activity, mission, plan)
                    HakimSovereignEngine.recordVerification(activity, false, reason)
                }
                else -> HakimMissionLedger.progress(activity, HakimMissionLedger.Phase.PLAN, reason)
            }
            onComplete(Outcome(progressed, completed, steps, needsApproval, needsDataTrust, blocked, reason))
        }

        fun recordStep(evidence: String) {
            HakimExecutionTransaction.markProgressed(activity, mission, plan)
            HakimSovereignEngine.recordExecution(activity, evidence)
        }

        fun gateAction(label: String, snapshot: JSONArray): HakimAuthorityEnvelope.Decision =
            HakimAuthorityEnvelope.classifyUiAction(label, snapshot)

        fun snapshotFor(web: WebView?, callback: (JSONArray) -> Unit) {
            if (HakimWebAutomation.isUsable(web)) {
                HakimWebAutomation.snapshot(web!!, callback)
            } else {
                callback(service?.uiSnapshot(160) ?: JSONArray())
            }
        }

        fun clickLeastPrivilege(web: WebView?, target: String, callback: (Boolean) -> Unit) {
            if (HakimWebAutomation.isUsable(web)) {
                HakimWebAutomation.clickText(web!!, target) { nativeOk ->
                    if (nativeOk) callback(true)
                    else callback(service?.action(JSONObject().put("action", "click_text").put("text", target)) == true)
                }
            } else {
                callback(service?.action(JSONObject().put("action", "click_text").put("text", target)) == true)
            }
        }

        fun setTextLeastPrivilege(web: WebView?, target: String, value: String, callback: (Boolean) -> Unit) {
            if (HakimWebAutomation.isUsable(web)) {
                HakimWebAutomation.setText(web!!, target, value) { nativeOk ->
                    if (nativeOk) callback(true)
                    else callback(service?.action(JSONObject().put("action", "set_text").put("text", target).put("value", value)) == true)
                }
            } else {
                callback(service?.action(JSONObject().put("action", "set_text").put("text", target).put("value", value)) == true)
            }
        }

        fun backLeastPrivilege(web: WebView?): Boolean {
            if (HakimWebAutomation.isUsable(web) && HakimWebAutomation.goBack(web!!)) return true
            return service?.action(JSONObject().put("action", "back")) == true
        }

        lateinit var next: () -> Unit
        next = {
            if (HakimMissionLedger.isCancelled(activity)) {
                finish(false, reason = "المهمة ملغاة بأمر المستخدم")
            } else if (finished || activity.isFinishing || activity.isDestroyed) {
                if (!finished) finish(false, reason = "انتهت واجهة التنفيذ")
            } else if (index >= plan.actions.size) {
                if (plan.phase == "execute" && plan.actions.isNotEmpty()) {
                    HakimExecutionTransaction.markAwaitingVerification(activity, mission, plan)
                }
                val verifiedDone = plan.done && plan.actions.isEmpty() && plan.phase == "verify"
                if (verifiedDone) HakimExecutionTransaction.markVerified(activity, mission)
                finish(
                    verifiedDone,
                    reason = if (verifiedDone) "جولة تحقق مستقلة أكدت postcondition ومعيار النجاح؛ لا حاجة إلى فعل إضافي"
                    else "انتهت أفعال الجولة؛ يلزم تحقق مستقل من الحالة الفعلية قبل إعلان الاكتمال"
                )
            } else {
                val action = plan.actions[index++]
                when (action.type) {
                    "open_url" -> {
                        val raw = action.args.optString("url").trim()
                        val uri = runCatching { Uri.parse(raw) }.getOrNull()
                        if (uri == null || uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
                            finish(false, blocked = true, reason = "رفضت رابطًا غير آمن أو غير صالح من خطة الاستدلال")
                        } else {
                            HakimExecutionTransaction.markAttempting(activity, mission, plan)
                            activity.getSharedPreferences("hakim", Activity.MODE_PRIVATE).edit().putString("last_url", raw).apply()
                            activity.startActivity(Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                            progressed = true
                            steps += 1
                            recordStep("فتح رابط ويب آمن بعد التحقق من البنية")
                            onProgress("فتحت المسار الذي اختاره الوكيل بعد التحقق من أنه رابط ويب آمن.")
                            handler.postDelayed(next, 900L)
                        }
                    }

                    "click_text" -> {
                        val target = action.args.optString("text").trim()
                        ensureHakimBrowser(activity, handler, 0) { web ->
                            if (web == null && service == null) {
                                finish(false, reason = "تعذر استعادة صفحة حكيم ولا يوجد مسار وصول احتياطي")
                            } else {
                                snapshotFor(web) { targetSnapshot ->
                                    val authority = gateAction(target, targetSnapshot)
                                    when (authority.gate) {
                                        HakimAuthorityEnvelope.Gate.BLOCK, HakimAuthorityEnvelope.Gate.CREDENTIAL -> finish(false, blocked = true, reason = authority.reason)
                                        HakimAuthorityEnvelope.Gate.APPROVAL, HakimAuthorityEnvelope.Gate.SYSTEM_PERMISSION -> finish(false, needsApproval = true, reason = authority.reason)
                                        else -> {
                                            HakimExecutionTransaction.markAttempting(activity, mission, plan)
                                            clickLeastPrivilege(web, target) { ok ->
                                            if (!ok) finish(false, reason = "لم أجد العنصر الذي حدده الاستدلال على صفحة الموقع")
                                            else {
                                                progressed = true
                                                steps += 1
                                                recordStep("نقرة منخفضة الأثر: ${target.take(120)}")
                                                onProgress("نفذت نقرة منخفضة الأثر داخل متصفح حكيم بعد فحص الصفحة وغلاف السلطة.")
                                                handler.postDelayed(next, 600L)
                                            }
                                        }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "fill_profile" -> {
                        val target = action.args.optString("target").trim()
                        val fieldId = action.args.optString("field_id").trim()
                        val value = HakimPersonalVault.get(activity, fieldId).orEmpty().trim()
                        if (value.isBlank()) {
                            finish(false, needsDataTrust = true, reason = "بيان «$fieldId» غير محفوظ في خزنة حكيم")
                        } else {
                            ensureHakimBrowser(activity, handler, 0) { web ->
                                if (web == null && service == null) {
                                    finish(false, reason = "تعذر استعادة صفحة حكيم قبل تعبئة بيانات الخزنة")
                                } else {
                                    snapshotFor(web) { targetSnapshot ->
                                        val authority = gateAction(target, targetSnapshot)
                                        when {
                                            authority.gate == HakimAuthorityEnvelope.Gate.BLOCK || authority.gate == HakimAuthorityEnvelope.Gate.CREDENTIAL -> finish(false, blocked = true, reason = authority.reason)
                                            authority.gate == HakimAuthorityEnvelope.Gate.APPROVAL || authority.gate == HakimAuthorityEnvelope.Gate.SYSTEM_PERMISSION -> finish(false, needsApproval = true, reason = authority.reason)
                                            !HakimSiteTrust.canUseProfile(activity, targetSnapshot) -> finish(false, needsDataTrust = true, reason = "الموقع غير معتمد لاستخدام بيانات الخزنة")
                                            else -> {
                                                HakimExecutionTransaction.markAttempting(activity, mission, plan)
                                                setTextLeastPrivilege(web, target, value) { ok ->
                                                if (!ok) finish(false, reason = "تعذر العثور على حقل «$target» لتعبئته من الخزنة")
                                                else {
                                                    progressed = true
                                                    steps += 1
                                                    recordStep("تعبئة محلية من الخزنة: $fieldId")
                                                    onProgress("عبأت «$fieldId» محليًا في موقع معتمد دون كشف قيمته لمحرك الاستدلال.")
                                                    handler.postDelayed(next, 500L)
                                                }
                                            }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "set_text" -> {
                        val target = action.args.optString("target").trim()
                        val value = action.args.optString("value")
                        ensureHakimBrowser(activity, handler, 0) { web ->
                            if (web == null && service == null) {
                                finish(false, reason = "تعذر استعادة صفحة حكيم قبل تقييم الكتابة")
                            } else {
                                snapshotFor(web) { targetSnapshot ->
                                    val authority = gateAction("$target $value", targetSnapshot)
                                    when {
                                        authority.gate == HakimAuthorityEnvelope.Gate.BLOCK || authority.gate == HakimAuthorityEnvelope.Gate.CREDENTIAL -> finish(false, blocked = true, reason = authority.reason)
                                        authority.gate == HakimAuthorityEnvelope.Gate.APPROVAL || authority.gate == HakimAuthorityEnvelope.Gate.SYSTEM_PERMISSION -> finish(false, needsApproval = true, reason = authority.reason)
                                        containsStoredProfileValue(activity, value) && !HakimSiteTrust.canUseProfile(activity, targetSnapshot) -> finish(false, needsDataTrust = true, reason = "الخطة ستستخدم قيمة من خزنة المستخدم في موقع غير معتمد")
                                        else -> {
                                            HakimExecutionTransaction.markAttempting(activity, mission, plan)
                                            setTextLeastPrivilege(web, target, value.take(6000)) { ok ->
                                            if (!ok) finish(false, reason = "تعذر العثور على الحقل المحدد في الخطة على صفحة الموقع")
                                            else {
                                                progressed = true
                                                steps += 1
                                                recordStep("كتابة محتوى غير حساس في ${target.take(120)}")
                                                onProgress("كتبت محتوى غير حساس داخل متصفح حكيم بعد فحص الصفحة والسياسة وغلاف السلطة.")
                                                handler.postDelayed(next, 500L)
                                            }
                                        }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "back" -> ensureHakimBrowser(activity, handler, 0) { web ->
                        if (web == null && service == null) finish(false, reason = "تعذر استعادة المتصفح للرجوع")
                        else {
                            HakimExecutionTransaction.markAttempting(activity, mission, plan)
                            val ok = backLeastPrivilege(web)
                            if (!ok) finish(false, reason = "تعذر الرجوع")
                            else {
                                progressed = true
                                steps += 1
                                recordStep("رجوع آمن داخل المسار الأقل صلاحية")
                                handler.postDelayed(next, 500L)
                            }
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

    private fun isActualFailure(reason: String): Boolean =
        reason.startsWith("تعذر") || reason.startsWith("لم أجد") || reason.contains("غير صالح")

    private fun ensureHakimBrowser(activity: Activity, handler: Handler, attempt: Int, onReady: (WebView?) -> Unit) {
        if (HakimMissionLedger.isCancelled(activity)) {
            onReady(null)
            return
        }
        val web = HakimRuntime.visibleWebView()
        if (HakimWebAutomation.isUsable(web) && (web?.progress ?: 0) >= 60) {
            onReady(web)
            return
        }
        if (attempt == 0) {
            activity.startActivity(Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
        if (attempt >= 14) {
            val last = HakimRuntime.visibleWebView()
            onReady(if (HakimWebAutomation.isUsable(last)) last else null)
            return
        }
        handler.postDelayed({ ensureHakimBrowser(activity, handler, attempt + 1, onReady) }, 300L)
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
