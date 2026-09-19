package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONObject

/**
 * طبقة تجميع سيادية: مقصد + قرآن كله + منهج القرآن والهدي النبوي + إنسان أولًا + نظام أنظمة + حاكم موارد + مصفوفة قرار + تفوق شامل + قيادة ذاتية + مبادرة مفيدة + نزاهة شرعية + سجل مهمة + نسيج تكامل.
 * الاستقلالية لا تتجاوز حدود السلطة أو الأمان أو الخصوصية؛ عند الفشل تعيد التخطيط ولا توسع الصلاحيات.
 */
object HakimSovereignEngine {
    data class Assessment(
        val mission: HakimMissionLedger.Mission,
        val decision: HakimDecisionMatrix.Decision,
        val quranic: HakimQuranicFramework.Assessment,
        val religious: HakimReligiousIntegrity.Assessment,
        val shubuhat: HakimHalalShubuhatGuard.Decision?,
        val kernelFingerprint: String,
        val isolationMode: String,
        val preferredCapabilities: List<String>,
        val failureBudgetRemaining: Int,
        val route: String,
        val shouldResearchFirst: Boolean,
        val needsApproval: Boolean,
        val blocked: Boolean
    )

    private const val MAX_CONSECUTIVE_FAILURES = 3
    private const val HARD_FAILURE_LIMIT = 5

    fun assess(
        context: Context,
        goal: String,
        highImpact: Boolean = false,
        sensitive: Boolean = false
    ): Assessment {
        HakimQuranicInvariantKernel.requireInherited("sovereign_assess")
        HakimIntegrationFabric.requireCore(context, "sovereign_assess")
        HakimLearning.initialize(context)
        HakimProactiveEngine.initialize(context)
        val mission = HakimMissionLedger.beginOrResume(context, goal)
        val one = HakimSovereignOneKernel.frame(context, goal, highImpact, sensitive)
        val decision = one.decision
        val quranic = one.quranic
        val religious = one.religious
        val shubuhat = one.shubuhat
        HakimHumanCapabilityBoundary.assess(goal)

        // النواة الواحدة هي مصدر المسار. نعيد حساب شرط البحث هنا كـ invariant فقط
        // حتى يفشل النظام مغلقًا إذا انحرفت النواة عن العقود القديمة المثبتة.
        val failures = mission.failures
        val localQuranReady = HakimVerifiedQuranCorpus.isReady(context)
        val expectedResearch = failures >= MAX_CONSECUTIVE_FAILURES ||
            decision.mode == HakimDecisionMatrix.Mode.RESEARCH_FIRST ||
            (quranic.exactQuranTextRequired && !localQuranReady) ||
            religious.exactSourceRequired ||
            shubuhat?.gate == HakimHalalShubuhatGuard.Gate.VERIFY_FIRST ||
            shubuhat?.gate == HakimHalalShubuhatGuard.Gate.ABSTAIN
        check(one.shouldResearchFirst == expectedResearch) { "انحراف بين النواة السيادية وعقد إعادة البحث" }
        check(one.failureBudgetRemaining == (HARD_FAILURE_LIMIT - failures).coerceAtLeast(0)) {
            "انحراف ميزانية الفشل في النواة السيادية"
        }

        val route = one.route
        HakimAdaptiveLearning.noteMissionRoute(context, mission.id, route)
        HakimSovereignOneKernel.recordSignal(
            context,
            HakimSovereignOneKernel.SignalKind.MISSION,
            "sovereign_engine",
            "route=$route;decision=${decision.mode};isolation=${one.isolationMode}",
            decision.confidence
        )
        HakimMissionLedger.progress(context, HakimMissionLedger.Phase.PLAN, "المسار=$route؛ القرار=${decision.mode}؛ النواة=${one.fingerprint.take(12)}")
        return Assessment(
            mission = HakimMissionLedger.active(context) ?: mission,
            decision = decision,
            quranic = quranic,
            religious = religious,
            shubuhat = shubuhat,
            kernelFingerprint = one.fingerprint,
            isolationMode = one.isolationMode,
            preferredCapabilities = one.preferredCapabilities,
            failureBudgetRemaining = one.failureBudgetRemaining,
            route = route,
            shouldResearchFirst = one.shouldResearchFirst,
            needsApproval = one.needsApproval,
            blocked = one.blocked
        )
    }

    fun promptContext(
        context: Context,
        goal: String,
        highImpact: Boolean = false,
        sensitive: Boolean = false
    ): String {
        HakimQuranicInvariantKernel.requireInherited("sovereign_prompt")
        HakimIntegrationFabric.requireCore(context, "sovereign_prompt")
        val a = assess(context, goal, highImpact, sensitive)
        val verifiedQuran = HakimVerifiedQuranCorpus.status(context)
        val wholeQuranScan = if (
            a.quranic.wholeQuranCorpusRequested && verifiedQuran.optBoolean("ready")
        ) HakimVerifiedQuranCorpus.fullCorpusScan(context, goal, 28) else null
        val faults = HakimFaultLedger.status(context)
        return buildString {
            appendLine("[المحرك السيادي لحكيم]")
            appendLine("مهمة واحدة نشطة فقط WIP=1. المرحلة=${a.mission.phase}، المسار=${a.route}، ميزانية الفشل المتبقية=${a.failureBudgetRemaining}.")
            appendLine("النواة الواحدة=${a.kernelFingerprint}؛ نمط العزل=${a.isolationMode}؛ القدرات=${a.preferredCapabilities.joinToString(" ← ")}.")
            appendLine("كل الإشارات والأفكار والأدوات والعلوم والسياسات والتعلم والتطور تعود إلى نواة قرار محلية واحدة؛ الخارج مصدر/وسيلة لا حاكم.")
            append(HakimQuranicFramework.promptContext(goal))
            append(HakimQuranSunnahMethod.promptContext(goal))
            append(HakimQuestionOperator.promptContext())
            append(HakimAdaptiveNStarLoop.promptContext())
            append(HakimScientificEngineeringKernel.promptContext(goal))
            append(HakimTaqwaCultivationCycle.promptContext(goal))
            append(HakimPersonalSovereignty.externalSafeContext(context))
            append(HakimEliteWisdomEngine.promptContext(goal))
            append(HakimDeliberationQuality.promptContext(goal))
            appendLine("[حالة النص القرآني المحلي المتحقق]")
            appendLine(if (verifiedQuran.optBoolean("ready")) "قاعدة حفص المحلية متحققة من المصدر الرسمي: ${verifiedQuran.optInt("surah_count")} سورة / ${verifiedQuran.optInt("ayah_count")} آية. يجوز استخدامها للنص الدقيق مع إبقاء طبقات التفسير/الاستنباط منفصلة." else "الحاكمية القرآنية الشاملة مفعلة، لكن نص القرآن الكامل ليس متحققًا محليًا بعد؛ لا تنقل نصًا دقيقًا من الذاكرة، واستخدم مصدرًا رسميًا موثوقًا قبل الجزم.")
            if (a.quranic.wholeQuranCorpusRequested) {
                appendLine("[استقراء القرآن كله — تنفيذ فعلي لا شعار]")
                if (wholeQuranScan == null) {
                    appendLine("تعذر تنفيذ مسح السور الـ١١٤ محليًا لأن corpus النص الموثق غير جاهز. لا تدّع الشمول؛ انتقل إلى بحث مصدري موثوق ثم أعد التخطيط.")
                } else {
                    appendLine("تم المرور الفعلي على ${wholeQuranScan.scannedSurahCount} سورة و${wholeQuranScan.scannedAyahCount} آية؛ اكتمال التغطية=${wholeQuranScan.coverageComplete}.")
                    appendLine(wholeQuranScan.reason)
                    appendLine("الآيات التالية مرشحات استرجاع لفظي من النص الموثق وليست تفسيرًا ولا حكمًا ولا إثباتًا للصلة بذاتها؛ افحص السياق والدلالة والتفسير الموثوق قبل الاستنباط:")
                    wholeQuranScan.candidates.forEach { c ->
                        appendLine("• ${c.surahNameAr} ${c.surah}:${c.ayah} — ${c.text}")
                    }
                    if (wholeQuranScan.candidates.isEmpty()) {
                        appendLine("لم ينتج الاسترجاع اللفظي مرشحات كافية؛ لا تملأ الفراغ بالتكلف. استخدم بحثًا/تفسيرًا موثوقًا مع بقاء إثبات مسح الـ١١٤ سورة مستقلًا عن نتيجة الصلة.")
                    }
                }
            }
            append(HakimHumanFirstPolicy.promptContext())
            append(HakimHumanCapabilityBoundary.promptContext(goal))
            append(HakimSystemOfSystems.promptContext(context, goal))
            append(HakimIntegrationFabric.promptContext(context))
            append(HakimProactiveEngine.promptContext(context))
            append(HakimDecisionMatrix.promptContext(goal, highImpact, sensitive))
            append(HakimExcellenceOptimizer.promptContext())
            append(HakimSelfLeadershipController.promptContext(context, goal))
            append(HakimAuthorityEnvelope.promptContext())
            append(HakimReligiousIntegrity.promptContext(goal))
            append(HakimHalalShubuhatGuard.promptContext(goal, highImpact))
            appendLine("التعلم التكيفي المحلي: ${HakimAdaptiveLearning.status(context).optString("last_adaptation_decision", "COLLECTING_EVIDENCE")}؛ يغيّر ترتيب البدائل الآمنة فقط، ولا يوسع السلطة أو يبدل الدستور.")
            appendLine("منع الفشل الصامت: أحداث حديثة=${faults.optInt("recent_event_count")}, عطل مادي متكرر=${faults.optBoolean("repeated_material_fault")}. الخطأ المتكرر يفرض سببًا جذريًا قبل ادعاء الاكتمال.")
            appendLine("سلسلة الاستقلالية المتكاملة: اعرض الغاية والأثر على الميزان القرآني→افحص الهدي النبوي الصحيح ذي الصلة دون اختلاق نسبة→احفظ كرامة الإنسان وحقوقه→افهم المقصد→كوّن نظام المهمة المنبثق بأقل الأنظمة اللازمة→ثبّت العقد→افحص التكامل والموارد والقدرات والسلطة→طبّق و؟→و؟→و؟→لِمَ؟→و؟→و؟→اعتمد→أصلح→أكمل→هَيّا→ابحث ذاتيًا عن كل مكسب مفيد آمن→ولّد البدائل اللازمة→رشّحها بالبوابات والترتيب الأعلى→استفد من الخبرة المحلية المثبتة دون كسر خط الأساس→فوّض الوكلاء→نفّذ أقل خطوة كافية→تحقق من الأثر→سجّل أي فشل بدل إخفائه→أصلح السبب→استعد الوصل/تعافَ/أعد التخطيط→تعلم محكومًا→واصل تلقائيًا ما دام هناك مكسب مادي آمن→أغلق بالدليل.")
            appendLine("صمم للإنسان الحقيقي وللهاتف الحقيقي: لا تفترض خبرة تقنية، لا تستغل الطيبة أو الرحمة، لا تفسر السكوت أو الإشارة العامة كموافقة عالية الأثر، وخفف العبء المعرفي والإجرائي والحمل الخلفي ما دام ذلك لا يسلب القرار الجوهري أو يخفض جودة الحكم.")
            appendLine("عند فشل وسيلة أو وصلة لا تعتبر الغاية فاشلة؛ بدّل إلى بديل مشروع ومصرح أو استعد الوصلة. بعد ثلاثة إخفاقات متتابعة أعد البحث/التخطيط، وبعد خمسة أوقف التكرار حتى يتغير الدليل أو الحالة.")
            appendLine("لا تُنشئ نظامًا أو نشاطًا لمجرد الكثرة؛ إذا لم يبق مكسب مادي آمن ومثبت فأغلق المهمة. لا تعيد خطوة ثبت نجاحها، ولا تغيّر خط الأساس المثبت لتحسين شكلي.")
            appendLine("الاستمرارية والتكامل والمبادرة والأنظمة المنبثقة لا تعني التحكم الخفي أو تجاوز موافقة؛ وكلمة إلغاء/توقف من المستخدم توقف المهمة وتعلو على الاستئناف.")
        }.take(44000)
    }

    fun recordExecution(context: Context, evidence: String) {
        HakimQuranicInvariantKernel.requireInherited("sovereign_execute")
        HakimIntegrationFabric.requireCore(context, "sovereign_execute")
        HakimMissionLedger.progress(context, HakimMissionLedger.Phase.EXECUTE, evidence, attempted = true)
    }

    fun recordVerification(context: Context, success: Boolean, evidence: String) {
        HakimQuranicInvariantKernel.requireInherited("sovereign_verify")
        HakimIntegrationFabric.requireCore(context, "sovereign_verify")
        if (HakimMissionLedger.isCancelled(context)) return
        if (success) {
            HakimMissionLedger.progress(context, HakimMissionLedger.Phase.VERIFY, evidence)
            HakimLearning.recordResult(context, "sovereign_mission", true)
        } else {
            HakimMissionLedger.failure(context, evidence)
            HakimLearning.recordResult(context, "sovereign_mission", false)
            HakimFaultLedger.record(context, "sovereign_verification", message = evidence, severity = HakimFaultLedger.Severity.MATERIAL)
            val failed = HakimMissionLedger.active(context)
            if (failed != null && failed.failures >= HARD_FAILURE_LIMIT) {
                HakimAdaptiveLearning.recordMissionOutcome(context, failed.id, false)
            }
        }
    }

    fun complete(context: Context, evidence: String) {
        HakimQuranicInvariantKernel.requireInherited("sovereign_complete")
        HakimIntegrationFabric.requireCore(context, "sovereign_complete")
        if (HakimMissionLedger.isCancelled(context)) return
        check(!HakimFaultLedger.repeatedMaterialFault(context)) { "لا يجوز إغلاق المهمة مع عطل مادي متكرر غير معالج" }

        val active = HakimMissionLedger.active(context)
        if (active != null && HakimQuranicCorpusPolicy.assess(active.goal).wholeCorpusRequested) {
            val scan = HakimVerifiedQuranCorpus.fullCorpusScan(context, active.goal, 1)
            check(
                scan.ready && scan.coverageComplete &&
                    scan.scannedSurahCount == HakimQuranicCorpusPolicy.SURAH_COUNT &&
                    scan.scannedAyahCount == 6236
            ) { "لا يجوز إغلاق طلب استقراء القرآن كله قبل إثبات المرور الفعلي على السور الـ١١٤ والآيات الـ٦٢٣٦ من النص المحلي المتحقق" }
        }

        val missionId = active?.id.orEmpty()
        HakimMissionLedger.complete(context, evidence)
        HakimLearning.recordResult(context, "sovereign_mission", true)
        HakimAdaptiveLearning.recordMissionOutcome(context, missionId, true)
        HakimFaultLedger.resolve(context, "sovereign_mission", evidence)
    }

    fun status(context: Context): JSONObject = JSONObject()
        .put("sovereign_engine", true)
        .put("one_sovereign_kernel", HakimSovereignOneKernel.status(context))
        .put("wip_one", true)
        .put("closed_loop", true)
        .put("failure_replan_threshold", MAX_CONSECUTIVE_FAILURES)
        .put("hard_failure_limit", HARD_FAILURE_LIMIT)
        .put("mission", HakimMissionLedger.status(context))
        .put("decision_dimensions", HakimDecisionMatrix.dimensions())
        .put("elite_wisdom", HakimEliteWisdomEngine.status())
        .put("deliberation_quality", HakimDeliberationQuality.status())
        .put("execution_transaction", HakimExecutionTransaction.status(context))
        .put("professional_readiness", HakimProfessionalReadiness.status(context))
        .put("question_operator", HakimQuestionOperator.status())
        .put("adaptive_nstar_loop", HakimAdaptiveNStarLoop.status())
        .put("scientific_engineering_kernel", HakimScientificEngineeringKernel.status())
        .put("taqwa_cultivation_cycle", HakimTaqwaCultivationCycle.status())
        .put("personal_sovereignty", HakimPersonalSovereignty.status(context))
        .put("quranic_framework", HakimQuranicFramework.status())
        .put("quran_sunnah_method", HakimQuranSunnahMethod.status())
        .put("quranic_invariant_kernel", HakimQuranicInvariantKernel.status())
        .put("verified_quran_corpus", HakimVerifiedQuranCorpus.status(context))
        .put("human_first", HakimHumanFirstPolicy.status())
        .put("human_capability_boundary", HakimHumanCapabilityBoundary.status())
        .put("fault_ledger", HakimFaultLedger.status(context))
        .put("resource_governor", HakimResourceGovernor.status(context))
        .put("system_of_systems", HakimSystemOfSystems.status(context))
        .put("integration_fabric", HakimIntegrationFabric.status(context))
        .put("excellence_optimizer", HakimExcellenceOptimizer.status())
        .put("self_leadership", HakimSelfLeadershipController.status(context))
        .put("proactive_engine", HakimProactiveEngine.status(context))
        .put("authority_envelope", HakimAuthorityEnvelope.status())
        .put("capability_registry", HakimCapabilityRegistry.status(context))
        .put("adaptive_learning", HakimAdaptiveLearning.status(context))
        .put("religious_integrity", true)
        .put("halal_shubuhat_guard", HakimHalalShubuhatGuard.status())
        .put("independence_policy", "قيادة ذاتية سيادية بنظام أنظمة متكامل وإنساني ومتعلّم ومبادر ومحكوم بالموارد داخل غلاف السلطة وتحت منهج القرآن والهدي النبوي الصحيح: المستخدم يملك WHAT/WHY/الحدود، وحكيم يملك HOW وتكوين الأنظمة المنبثقة والتفويض والوصل والتعافي والتحقق والمبادرة بكل مكسب آمن؛ أقصى مساعدة رقمية ممكنة دون ادعاء تكافؤ الإنسان جسديًا أو قانونيًا، ولا توسع صلاحيات ولا تعديل كود ذاتي ولا نجاح بلا دليل ولا استغلال للطيبة أو الجهل التقني")
}
