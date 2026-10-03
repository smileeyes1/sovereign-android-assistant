package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONObject

/**
 * منفذ آمن للمهام النصية التي يمكن إنجازها دون واجهة أو صلاحية جديدة.
 *
 * لا ينفذ متصفحًا أو مشاركة أو ملفات أو أفعالًا عالية الأثر في الخلفية.
 * الناتج يُحفظ داخل محادثة حكيم ثم يبقى VERIFYING حتى يُعرض فعليًا للمستخدم.
 */
object HakimAutonomousGoalRunner {
    const val VERSION = "AUTONOMOUS-GOAL-RUNNER-2026-10-03-v1"
    private const val PREFS = "hakim_autonomous_goal_runner"

    @Volatile private var running = false

    @Synchronized
    fun scheduleIfSafe(context: Context, task: HakimTaskManager.Task, reason: String): Boolean {
        val app = context.applicationContext
        if (running) return false
        if (task.kind != "user_goal" || !task.autoResume || !task.resumable || task.goal.isBlank()) return false
        if (HakimAttachmentSessionStore.restore(app).isNotEmpty()) return false

        val intent = HakimIntentEngine.resolve(app, task.goal)
        if (intent.highImpact || intent.needsUserGate) return false

        val decision = HakimModelToolRouter.decide(app, task.goal, emptyList())
        if (decision.channel !in setOf(
                HakimModelToolRouter.Channel.LOCAL_RESPONSE,
                HakimModelToolRouter.Channel.DIRECT_MODEL
            )
        ) return false

        running = true
        prefs(app).edit()
            .putString("version", VERSION)
            .putString("running_task_id", task.id)
            .putString("last_reason", reason.take(80))
            .putString("last_channel", decision.channel.name)
            .putLong("started_at", System.currentTimeMillis())
            .apply()

        Thread {
            try {
                execute(app, task, decision)
            } finally {
                running = false
                prefs(app).edit()
                    .remove("running_task_id")
                    .putLong("finished_at", System.currentTimeMillis())
                    .apply()
            }
        }.start()
        return true
    }

    private fun execute(
        context: Context,
        task: HakimTaskManager.Task,
        decision: HakimModelToolRouter.Decision
    ) {
        HakimExecutiveLoop.resume(context, task)
        HakimExecutiveLoop.record(
            context,
            HakimExecutiveLoop.Phase.EXECUTING,
            "استئناف نصي ذاتي في الخلفية ضمن مسار يعيد النتيجة إلى حكيم"
        )

        when (decision.channel) {
            HakimModelToolRouter.Channel.LOCAL_RESPONSE -> {
                val answer = HakimModelToolRouter.localReply(task.goal).orEmpty()
                if (answer.isBlank()) {
                    fail(context, task, "local_response_empty", false)
                    return
                }
                storePendingVisibleResult(context, task, answer, "local_response")
            }

            HakimModelToolRouter.Channel.DIRECT_MODEL -> {
                val engine = HakimEngineRegistry.directEngines(context)
                    .firstOrNull { it.id == decision.engineId }
                    ?: HakimEngineRegistry.bestGeneralChat(context, task.goal, emptyList())

                if (engine == null) {
                    fail(context, task, "direct_engine_unavailable", true)
                    return
                }

                val instruction = HakimIntentDirector.build(context, task.goal, 0).instruction
                val started = System.currentTimeMillis()
                val result = engine.complete(instruction, emptyList()) { }
                val latency = (System.currentTimeMillis() - started).coerceAtLeast(0L)

                when (result) {
                    is HakimInferenceEngine.Result.Success -> {
                        HakimEngineTelemetry.record(context, engine.id, true, latency)
                        val answer = HakimProductOutput.clean(result.text)
                        if (answer.isBlank()) {
                            fail(context, task, "direct_result_empty", true)
                        } else {
                            storePendingVisibleResult(context, task, answer, "direct_model")
                        }
                    }
                    is HakimInferenceEngine.Result.NeedsAuthorization -> {
                        HakimEngineTelemetry.record(context, engine.id, false, latency)
                        HakimExecutiveLoop.record(
                            context,
                            HakimExecutiveLoop.Phase.GATED,
                            "يتطلب المسار تفويضًا جديدًا قبل متابعة التنفيذ"
                        )
                        prefs(context).edit()
                            .putString("last_state", "GATED")
                            .putString("last_failure", "authorization_required")
                            .apply()
                    }
                    is HakimInferenceEngine.Result.Unavailable -> {
                        HakimEngineTelemetry.record(context, engine.id, false, latency)
                        fail(context, task, "engine_unavailable", true)
                    }
                    is HakimInferenceEngine.Result.Failure -> {
                        HakimEngineTelemetry.record(context, engine.id, false, latency)
                        fail(context, task, "engine_failure", result.retryable)
                    }
                }
            }

            else -> Unit
        }
    }

    private fun storePendingVisibleResult(
        context: Context,
        task: HakimTaskManager.Task,
        raw: String,
        route: String
    ) {
        val answer = raw.trim().take(8_000)
        val cp = context.getSharedPreferences("hakim_conversation", Context.MODE_PRIVATE)
        val current = cp.getString("recent", "").orEmpty().trim()
        val entry = "حكيم:\n" + answer
        val next = if (current.isBlank()) entry else current + "\n\n" + entry
        cp.edit().putString("recent", next.takeLast(12_000)).apply()

        HakimExecutiveLoop.record(
            context,
            HakimExecutiveLoop.Phase.VERIFYING,
            "أُنتجت النتيجة ذاتيًا وحُفظت داخل حكيم؛ بقي تحقق العرض الفعلي للمستخدم"
        )
        HakimGoalSupervisor.recordEvidence(
            context,
            HakimGoalSupervisor.EvidenceStage.DISPATCHED.name,
            "headless_result_stored_in_hakim_conversation",
            effectVerified = false
        )
        HakimTaskManager.noteEvidence(context, task.id, "headless_result_ready_for_ui_observation")

        prefs(context).edit()
            .putString("pending_visible_task_id", task.id)
            .putString("last_route", route)
            .putString("last_state", "WAITING_UI_OBSERVATION")
            .putLong("result_ready_at", System.currentTimeMillis())
            .remove("last_failure")
            .apply()
    }

    fun finalizeIfVisible(context: Context): Boolean {
        val app = context.applicationContext
        val p = prefs(app)
        val id = p.getString("pending_visible_task_id", "").orEmpty()
        if (id.isBlank()) return false
        val task = HakimTaskManager.get(app, id) ?: run {
            p.edit().remove("pending_visible_task_id").apply()
            return false
        }
        val current = HakimExecutiveLoop.current(app)
        if (current != null && current.id != task.id) return false
        if (current == null) HakimExecutiveLoop.resume(app, task)

        val closed = HakimExecutiveLoop.complete(
            app,
            "النتيجة الذاتية أصبحت مرئية داخل محادثة حكيم",
            HakimGoalSupervisor.EvidenceStage.UI_OBSERVED
        )
        if (closed) {
            p.edit()
                .remove("pending_visible_task_id")
                .putString("last_state", "VISIBLE_AND_VERIFIED")
                .putLong("visible_verified_at", System.currentTimeMillis())
                .apply()
        }
        return closed
    }

    private fun fail(context: Context, task: HakimTaskManager.Task, code: String, retryable: Boolean) {
        HakimGoalSupervisor.toolFailed(context, "autonomous_goal_runner:" + code)
        HakimExecutiveLoop.record(
            context,
            if (retryable) HakimExecutiveLoop.Phase.REPAIRING else HakimExecutiveLoop.Phase.GATED,
            if (retryable) "فشل المسار النصي الذاتي؛ سيُعاد التوجيه عند نبضة جديدة" else "المسار الحالي غير متاح دون تغيير شرط"
        )
        prefs(context).edit()
            .putString("last_state", if (retryable) "REROUTE" else "GATED")
            .putString("last_failure", code.take(80))
            .putLong("last_failure_at", System.currentTimeMillis())
            .apply()
        HakimLearning.recordResult(context, "autonomous_goal_runner_" + code.take(32), false)
        HakimTaskManager.noteEvidence(context, task.id, "autonomous_runner_failure:" + code)
    }

    fun status(context: Context): JSONObject {
        val p = prefs(context)
        return JSONObject()
            .put("autonomous_goal_runner", true)
            .put("version", p.getString("version", VERSION))
            .put("running", running)
            .put("safe_background_channels_only", true)
            .put("high_impact_background_execution", false)
            .put("attachments_background_execution", false)
            .put("ui_observation_required_before_close", true)
            .put("last_state", p.getString("last_state", ""))
            .put("last_route", p.getString("last_route", ""))
            .put("last_failure", p.getString("last_failure", ""))
            .put("result_ready_at", p.getLong("result_ready_at", 0L))
            .put("visible_verified_at", p.getLong("visible_verified_at", 0L))
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
