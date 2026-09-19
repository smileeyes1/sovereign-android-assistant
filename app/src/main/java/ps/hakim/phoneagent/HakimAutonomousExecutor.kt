package ps.hakim.phoneagent

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject

/**
 * حلقة تنفيذ محلية مغلقة: صفحة/شاشة -> فعل آمن -> تحقق -> إعادة تقدير.
 * WebView حكيم هو المسار الأول بأقل صلاحية، وAccessibility احتياط فقط إن كان متاحًا.
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
        HakimLearning.initialize(activity)
        HakimMissionLedger.beginOrResume(activity, goal)
        if (HakimMissionLedger.isCancelled(activity)) {
            onComplete(Outcome(false, false, 0, false, false, false, "المهمة ملغاة بأمر المستخدم"))
            return
        }
        val sovereign = HakimSovereignEngine.assess(activity, goal)
        if (sovereign.blocked) {
            if (!HakimMissionLedger.isCancelled(activity)) HakimMissionLedger.block(activity, "المحرك السيادي منع التنفيذ المحلي")
            onComplete(Outcome(false, false, 0, false, false, false, if (HakimMissionLedger.isCancelled(activity)) "المهمة ملغاة بأمر المستخدم" else "المحرك السيادي منع التنفيذ حتى يتغير الدليل أو الحالة"))
            return
        }
        if (sovereign.shouldResearchFirst) {
            HakimMissionLedger.progress(activity, HakimMissionLedger.Phase.PLAN, "يلزم تحقق/بحث قبل أي تنفيذ محلي")
            onComplete(Outcome(false, false, 0, false, false, false, "يلزم تحقق/بحث وإعادة تخطيط قبل التنفيذ"))
            return
        }

        val service = HakimAccessibilityService.instance
        HakimMissionLedger.progress(activity, HakimMissionLedger.Phase.EXECUTE, "بدء حلقة التنفيذ المحلي", attempted = true)

        val seen = linkedSetOf<String>()
        var steps = 0
        var progressed = false
        var finished = false
        var pendingAdaptiveAction: String? = null
        var pendingAdaptiveFingerprint: String? = null

        fun finish(
            completed: Boolean,
            needsApproval: Boolean = false,
            needsCredential: Boolean = false,
            needsDataTrust: Boolean = false,
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
                needsCredential -> HakimMissionLedger.progress(activity, HakimMissionLedger.Phase.WAITING_CREDENTIAL, reason)
                needsDataTrust -> HakimMissionLedger.progress(activity, HakimMissionLedger.Phase.WAITING_TRUST, reason)
                isLegitimateHandoff(reason) -> HakimMissionLedger.progress(activity, HakimMissionLedger.Phase.PLAN, reason)
                else -> HakimSovereignEngine.recordVerification(activity, false, reason)
            }
            onComplete(Outcome(progressed, completed, steps, needsApproval, needsCredential, needsDataTrust, reason))
        }

        fun recordStep(evidence: String) {
            HakimSovereignEngine.recordExecution(activity, evidence)
        }

        fun snapshotFor(callback: (WebView?, JSONArray) -> Unit) {
            val web = HakimRuntime.visibleWebView()
            if (HakimWebAutomation.isUsable(web)) {
                HakimWebAutomation.snapshot(web!!) { nativeSnapshot ->
                    if (nativeSnapshot.length() > 0) callback(web, nativeSnapshot)
                    else callback(web, service?.uiSnapshot(160) ?: nativeSnapshot)
                }
            } else {
                callback(null, service?.uiSnapshot(160) ?: JSONArray())
            }
        }

        fun setTextLeastPrivilege(web: WebView?, node: JSONObject, target: String, value: String, callback: (Boolean) -> Unit) {
            if (HakimWebAutomation.isUsable(web)) {
                HakimWebAutomation.setText(web!!, target, value) { nativeOk ->
                    if (nativeOk) callback(true)
                    else callback(
                        service?.action(
                            JSONObject().put("action", "set_text")
                                .put("id", node.optString("id"))
                                .put("text", target)
                                .put("value", value)
                        ) == true
                    )
                }
            } else {
                callback(
                    service?.action(
                        JSONObject().put("action", "set_text")
                            .put("id", node.optString("id"))
                            .put("text", target)
                            .put("value", value)
                    ) == true
                )
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

        lateinit var iterate: () -> Unit
        iterate = iterateLoop@ {
            if (HakimMissionLedger.isCancelled(activity)) {
                finish(false, reason = "المهمة ملغاة بأمر المستخدم")
                return@iterateLoop
            }
            if (finished || activity.isFinishing || activity.isDestroyed) {
                if (!finished) finish(false, reason = "انتهت واجهة التنفيذ")
                return@iterateLoop
            }

            snapshotFor { web, snapshot ->
                if (finished || HakimMissionLedger.isCancelled(activity)) return@snapshotFor
                if (snapshot.length() == 0) {
                    finish(false, reason = "لا يوجد فعل محلي واضح وآمن الآن؛ تحتاج المهمة استدلالًا إضافيًا")
                    return@snapshotFor
                }

                val currentFingerprint = fingerprint(snapshot)
                val pending = pendingAdaptiveAction
                val before = pendingAdaptiveFingerprint
                if (pending != null && before != null) {
                    HakimAdaptiveLearning.recordActionOutcome(activity, pending, currentFingerprint != before)
                    pendingAdaptiveAction = null
                    pendingAdaptiveFingerprint = null
                }

                if (HakimActionPolicy.isSuccessState(snapshot) && progressed) {
                    HakimSovereignEngine.recordVerification(activity, true, "ظهرت حالة نجاح مرئية بعد التنفيذ")
                    finish(true, reason = "ظهرت حالة نجاح مرئية بعد التنفيذ")
                } else if (steps >= MAX_STEPS) {
                    finish(false, reason = "وصلت الحلقة إلى حد الخطوات الآمن وتحتاج إعادة تقدير")
                } else if (!seen.add(currentFingerprint) && progressed) {
                    finish(false, reason = "لم تتغير الصفحة بعد آخر خطوة؛ أوقفت التكرار")
                } else {
                    val profileCandidate = firstProfileCandidate(activity, snapshot)
                    if (profileCandidate != null && !HakimSiteTrust.canUseProfile(activity, snapshot)) {
                        finish(false, needsDataTrust = true, reason = "الموقع/التطبيق الحالي غير معتمد لإخراج بيانات خزنة حكيم")
                    } else if (profileCandidate != null) {
                        val (node, match) = profileCandidate
                        val field = match.first
                        val value = match.second
                        val textHint = visibleLabel(node)
                        val authority = HakimAuthorityEnvelope.classifyUiAction(textHint, snapshot)
                        when (authority.gate) {
                            HakimAuthorityEnvelope.Gate.BLOCK -> finish(false, reason = authority.reason)
                            HakimAuthorityEnvelope.Gate.APPROVAL, HakimAuthorityEnvelope.Gate.SYSTEM_PERMISSION -> finish(false, needsApproval = true, reason = authority.reason)
                            HakimAuthorityEnvelope.Gate.CREDENTIAL -> finish(false, needsCredential = true, reason = authority.reason)
                            else -> {
                                val adaptiveKey = "profile:${field.id}"
                                setTextLeastPrivilege(web, node, textHint, value) { ok ->
                                    if (ok) {
                                        progressed = true
                                        steps += 1
                                        pendingAdaptiveAction = adaptiveKey
                                        pendingAdaptiveFingerprint = currentFingerprint
                                        recordStep("عبئت ${field.id} محليًا في موقع معتمد")
                                        onProgress("عبأت «${field.title}» محليًا في موقع معتمد دون إرسال القيمة إلى نموذج الذكاء.")
                                        handler.postDelayed(iterate, 500L)
                                    } else {
                                        HakimAdaptiveLearning.recordActionOutcome(activity, adaptiveKey, false)
                                        finish(false, reason = "تعذر تعبئة الحقل المطابق بأمان")
                                    }
                                }
                            }
                        }
                    } else if (HakimActionPolicy.screenHasSensitiveInput(snapshot)) {
                        finish(false, needsCredential = true, reason = "توجد خطوة اعتماد حساسة؛ تُترك لمدير اعتماد أندرويد/الحقل الآمن")
                    } else {
                        val next = nextSafeContinuation(activity, snapshot)
                        if (next != null) {
                            val authority = HakimAuthorityEnvelope.classifyUiAction(next, snapshot)
                            when (authority.gate) {
                                HakimAuthorityEnvelope.Gate.BLOCK -> finish(false, reason = authority.reason)
                                HakimAuthorityEnvelope.Gate.APPROVAL, HakimAuthorityEnvelope.Gate.SYSTEM_PERMISSION -> finish(false, needsApproval = true, reason = authority.reason)
                                HakimAuthorityEnvelope.Gate.CREDENTIAL -> finish(false, needsCredential = true, reason = authority.reason)
                                else -> {
                                    val matrix = HakimDecisionMatrix.evaluate(next)
                                    if (matrix.mode == HakimDecisionMatrix.Mode.BLOCK) {
                                        finish(false, reason = "منعت مصفوفة القرار الخطوة التالية")
                                    } else if (matrix.mode == HakimDecisionMatrix.Mode.APPROVAL_GATE) {
                                        finish(false, needsApproval = true, reason = "مصفوفة القرار تطلب موافقة قبل «$next»")
                                    } else {
                                        clickLeastPrivilege(web, next) { ok ->
                                            if (ok) {
                                                progressed = true
                                                steps += 1
                                                pendingAdaptiveAction = next
                                                pendingAdaptiveFingerprint = currentFingerprint
                                                recordStep("نفذت متابعة آمنة: ${next.take(160)}")
                                                onProgress("نفذت الخطوة الآمنة التالية «$next» وتحققت من الانتقال قبل المتابعة.")
                                                handler.postDelayed(iterate, 650L)
                                            } else {
                                                HakimAdaptiveLearning.recordActionOutcome(activity, next, false)
                                                finish(false, reason = "تعذر تنفيذ عنصر المتابعة الظاهر")
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            val gated = nextApprovalAction(snapshot)
                            if (gated != null) {
                                finish(false, needsApproval = true, reason = "وصلت إلى الفعل النهائي «$gated» بعد إكمال التحضير الآمن")
                            } else {
                                finish(false, reason = if (progressed) "أغلقت كل الخطوات المحلية الواضحة وتحتاج المهمة استدلالًا إضافيًا" else "لا يوجد فعل محلي واضح وآمن يمكن استنتاجه من الصفحة")
                            }
                        }
                    }
                }
            }
        }

        onProgress("بدأ حكيم دورة التنفيذ الذاتي للهدف: ${goal.take(180)}")
        handler.post(iterate)
    }

    private fun isLegitimateHandoff(reason: String): Boolean =
        reason.contains("استدلال") || reason.contains("لا يوجد فعل محلي") || reason.contains("إعادة تقدير") || reason.contains("تحقق/بحث")

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

    private fun nextSafeContinuation(context: Context, snapshot: JSONArray): String? {
        val baseline = safeContinuationCandidates(snapshot)
        return HakimAdaptiveLearning.rankSafeCandidates(context, baseline).firstOrNull()
    }

    // يبقى خط الأساس مستقلًا وقابلًا للاسترجاع؛ التكيف لا يغيّر مجموعة المرشحات.
    private fun nextSafeContinuation(snapshot: JSONArray): String? = safeContinuationCandidates(snapshot).firstOrNull()

    private fun safeContinuationCandidates(snapshot: JSONArray): List<String> {
        val preferred = listOf("التالي", "متابعة", "استمرار", "أكمل", "اكمل", "تابع", "حسنًا", "حسنا", "next", "continue", "proceed", "ok")
        val labels = linkedSetOf<String>()
        for (candidate in preferred) {
            for (i in 0 until snapshot.length()) {
                val node = snapshot.optJSONObject(i) ?: continue
                if (!node.optBoolean("clickable", false) || node.optBoolean("sensitive", false)) continue
                val label = visibleLabel(node)
                if (label.isBlank()) continue
                if (!label.lowercase().contains(candidate.lowercase())) continue
                if (!HakimActionPolicy.isSafeContinuation(label, snapshot)) continue
                labels += label
            }
        }
        return labels.toList()
    }

    private fun nextApprovalAction(snapshot: JSONArray): String? {
        for (i in 0 until snapshot.length()) {
            val node = snapshot.optJSONObject(i) ?: continue
            if (!node.optBoolean("clickable", false) || node.optBoolean("sensitive", false)) continue
            val label = visibleLabel(node)
            if (label.isBlank()) continue
            val authority = HakimAuthorityEnvelope.classifyUiAction(label, snapshot)
            if (authority.gate == HakimAuthorityEnvelope.Gate.APPROVAL || authority.gate == HakimAuthorityEnvelope.Gate.SYSTEM_PERMISSION) return label
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
