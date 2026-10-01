package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * مدير مهام حكيم المحلي.
 *
 * يحفظ المهام على الهاتف، يدير WIP=1 للمهام التنفيذية، ويعرض حالة عامة للسحابة
 * بلا تسريب المقصد أو التفاصيل. لا يعتبر بدء التنفيذ نجاحًا.
 */
object HakimTaskManager {
    private const val PREFS = "hakim_task_manager"
    private const val KEY_TASKS = "tasks"
    private const val KEY_RESUME_REQUEST = "resume_request"
    private const val MAX_TASKS = 48
    const val NETWORK_TASK_ID = "system-network-protection"

    enum class State {
        QUEUED,
        RUNNING,
        VERIFYING,
        WAITING,
        BLOCKED,
        PAUSED,
        COMPLETE,
        FAILED,
        CANCELLED
    }

    data class Task(
        val id: String,
        val kind: String,
        val title: String,
        val goal: String,
        val acceptance: String,
        val state: State,
        val phase: String,
        val priority: String,
        val createdAt: Long,
        val updatedAt: Long,
        val attempts: Int,
        val resumable: Boolean,
        val autoResume: Boolean,
        val lastDetail: String,
        val lastEvidence: String,
        val blocker: String,
        val nextAction: String
    )

    @Synchronized
    fun beginExecutive(
        context: Context,
        id: String,
        goal: String,
        acceptance: String
    ) {
        val now = System.currentTimeMillis()
        val items = load(context)
        for (item in items) {
            val state = stateOf(item.optString("state"))
            if (item.optString("id") != id &&
                item.optString("kind", "user_goal") == "user_goal" &&
                state in activeStates()
            ) {
                item.put("state", State.PAUSED.name)
                item.put("updated_at", now)
                item.put("blocker", "حُفظت المهمة عند بدء مهمة أخرى")
                item.put("next_action", "استئناف التنفيذ من المقصد المحفوظ")
                item.put("resumable", true)
            }
        }
        val current = items.firstOrNull { it.optString("id") == id }
            ?: JSONObject().also { items.add(it) }
        current
            .put("id", id)
            .put("kind", "user_goal")
            .put("title", titleFromGoal(goal))
            .put("goal", goal.take(4000))
            .put("acceptance", acceptance.take(4000))
            .put("state", State.RUNNING.name)
            .put("phase", HakimExecutiveLoop.Phase.UNDERSTANDING.name)
            .put("priority", "P1")
            .put("created_at", current.optLong("created_at", now).takeIf { it > 0L } ?: now)
            .put("updated_at", now)
            .put("attempts", current.optInt("attempts", 0).coerceAtLeast(0) + 1)
            .put("resumable", true)
            .put("auto_resume", false)
            .put("last_detail", "فهم المقصد وتثبيت معيار الاكتمال")
            .put("last_evidence", current.optString("last_evidence"))
            .put("blocker", "")
            .put("next_action", "اختيار الخطة والتنفيذ")
        save(context, items)
    }

    @Synchronized
    fun syncExecutive(
        context: Context,
        session: HakimExecutiveLoop.Session?,
        phase: HakimExecutiveLoop.Phase,
        detail: String
    ) {
        val s = session ?: return
        val items = load(context)
        val item = items.firstOrNull { it.optString("id") == s.id }
            ?: JSONObject().also {
                items.add(it)
                it.put("id", s.id)
                    .put("kind", "user_goal")
                    .put("title", titleFromGoal(s.goal))
                    .put("goal", s.goal.take(4000))
                    .put("acceptance", s.acceptance.take(4000))
                    .put("priority", "P1")
                    .put("created_at", System.currentTimeMillis())
                    .put("attempts", s.cycle.coerceAtLeast(1))
                    .put("resumable", true)
                    .put("auto_resume", false)
            }

        val state = when (phase) {
            HakimExecutiveLoop.Phase.VERIFYING -> State.VERIFYING
            HakimExecutiveLoop.Phase.WAITING_EXTERNAL -> State.WAITING
            HakimExecutiveLoop.Phase.GATED -> State.BLOCKED
            HakimExecutiveLoop.Phase.COMPLETE -> State.COMPLETE
            HakimExecutiveLoop.Phase.CANCELLED -> State.CANCELLED
            else -> State.RUNNING
        }
        item.put("state", state.name)
            .put("phase", phase.name)
            .put("updated_at", System.currentTimeMillis())
            .put("attempts", s.cycle.coerceAtLeast(1))
            .put("last_detail", detail.trim().take(500))
            .put("blocker", if (state == State.BLOCKED) detail.trim().take(500) else "")
            .put("next_action", nextAction(phase))
            .put("resumable", state !in terminalStates())
        save(context, items)
    }

    @Synchronized
    fun noteEvidence(context: Context, id: String?, evidence: String) {
        if (id.isNullOrBlank()) return
        val items = load(context)
        val item = items.firstOrNull { it.optString("id") == id } ?: return
        item.put("last_evidence", evidence.trim().take(1200))
            .put("updated_at", System.currentTimeMillis())
        save(context, items)
    }

    @Synchronized
    fun requestResume(context: Context, id: String): Task? {
        val items = load(context)
        val item = items.firstOrNull { it.optString("id") == id } ?: return null
        val task = toTask(item) ?: return null
        if (!task.resumable || task.state in terminalStates()) return null
        val now = System.currentTimeMillis()
        for (other in items) {
            if (other.optString("id") != id &&
                other.optString("kind", "user_goal") == "user_goal" &&
                stateOf(other.optString("state")) in activeStates()
            ) {
                other.put("state", State.PAUSED.name)
                    .put("updated_at", now)
                    .put("next_action", "استئناف التنفيذ من المقصد المحفوظ")
            }
        }
        item.put("state", State.QUEUED.name)
            .put("updated_at", now)
            .put("blocker", "")
            .put("next_action", "استئناف التنفيذ الآن")
        save(context, items)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_RESUME_REQUEST, id).apply()
        return toTask(item)
    }

    fun consumeResumeRequest(context: Context): Task? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = p.getString(KEY_RESUME_REQUEST, "").orEmpty()
        if (id.isBlank()) return null
        p.edit().remove(KEY_RESUME_REQUEST).apply()
        return get(context, id)
    }

    @Synchronized
    fun cancel(context: Context, id: String): Boolean {
        val items = load(context)
        val item = items.firstOrNull { it.optString("id") == id } ?: return false
        item.put("state", State.CANCELLED.name)
            .put("updated_at", System.currentTimeMillis())
            .put("blocker", "")
            .put("next_action", "لا توجد خطوة تالية")
            .put("resumable", false)
        save(context, items)
        return true
    }

    @Synchronized
    fun syncSystemTasks(context: Context) {
        syncNetworkProtection(context)
    }

    @Synchronized
    fun syncNetworkProtection(context: Context) {
        val guardian = HakimNetworkGuardian.status(context)
        val now = System.currentTimeMillis()
        val items = load(context)
        val item = items.firstOrNull { it.optString("id") == NETWORK_TASK_ID }
            ?: JSONObject().also { items.add(it) }

        val familyConfigured = guardian.optBoolean("family_dns_configured", false)
        val resolverVerified = guardian.optBoolean("family_resolver_verified", false)
        val fullBypass = guardian.optBoolean("full_bypass_prevention", false)
        val localSession = guardian.optBoolean("web_local_session_present", false)
        val rawState = guardian.optString("state")
        val taskState = when {
            fullBypass -> State.COMPLETE
            familyConfigured && resolverVerified -> State.VERIFYING
            rawState == "ROUTER_AUTH_REQUIRED" -> State.BLOCKED
            rawState.startsWith("FAMILY_DNS_ROLLBACK") || rawState.startsWith("FAMILY_DNS_ROLLED_BACK") -> State.FAILED
            localSession && rawState == "TR064_LANHOST_NOT_FOUND" -> State.RUNNING
            else -> State.RUNNING
        }
        val detail = when {
            fullBypass -> "ثبتت طبقات الحماية والالتفاف المطلوبة"
            familyConfigured && resolverVerified -> "DNS العائلي مثبت؛ بقي اختبار طبقات الالتفاف"
            rawState == "ROUTER_AUTH_REQUIRED" -> "الراوتر ينتظر مصادقة محلية"
            localSession && rawState == "TR064_LANHOST_NOT_FOUND" -> "جلسة الراوتر موجودة؛ استئناف DNS داخل WebView"
            else -> "حارس الشبكة يعمل ولم يثبت الاكتمال بعد"
        }
        val next = when {
            fullBypass -> "لا توجد خطوة تالية"
            familyConfigured && resolverVerified -> "اختبار DNS الخارجي وDoT وDoH وVPN وIPv6"
            rawState == "ROUTER_AUTH_REQUIRED" -> "فتح المصادقة المحلية داخل حكيم"
            localSession -> "فتح واجهة DHCP/DNS داخل جلسة الراوتر المحلية"
            else -> "متابعة الحارس حتى ظهور دليل جديد"
        }

        item.put("id", NETWORK_TASK_ID)
            .put("kind", "network_protection")
            .put("title", "حماية الشبكة من الإباحية والتجاوز")
            .put("goal", "حماية شبكة المنزل والهاتف من المحتوى الإباحي ومسارات التجاوز ضمن المأذون")
            .put("acceptance", "DNS عائلي متحقق ثم اختبار طبقات الالتفاف دون كسر الإنترنت")
            .put("state", taskState.name)
            .put("phase", rawState.take(120))
            .put("priority", "P0")
            .put("created_at", item.optLong("created_at", now).takeIf { it > 0L } ?: now)
            .put("updated_at", now)
            .put("attempts", item.optInt("attempts", 1).coerceAtLeast(1))
            .put("resumable", !fullBypass)
            .put("auto_resume", true)
            .put("last_detail", detail)
            .put("last_evidence", if (familyConfigured && resolverVerified) "family_dns_configured + family_resolver_verified" else "")
            .put("blocker", if (taskState == State.BLOCKED) detail else "")
            .put("next_action", next)
        save(context, items)
    }

    fun shouldAutoOpenRouterProtection(context: Context): Boolean {
        val guardian = HakimNetworkGuardian.status(context)
        if (guardian.optBoolean("family_dns_configured", false) &&
            guardian.optBoolean("family_resolver_verified", false)
        ) return false
        val state = guardian.optString("state")
        val local = guardian.optBoolean("web_local_session_present", false)
        return state == "ROUTER_AUTH_REQUIRED" ||
            (local && state == "TR064_LANHOST_NOT_FOUND")
    }

    @Synchronized
    fun get(context: Context, id: String): Task? =
        load(context).firstOrNull { it.optString("id") == id }?.let(::toTask)

    @Synchronized
    fun all(context: Context): List<Task> =
        load(context).mapNotNull(::toTask)
            .sortedWith(compareBy<Task> { priorityRank(it.priority) }
                .thenBy { stateRank(it.state) }
                .thenByDescending { it.updatedAt })

    fun summaryText(context: Context): String {
        syncSystemTasks(context)
        val tasks = all(context)
        if (tasks.isEmpty()) return "لا توجد مهام محفوظة"
        val active = tasks.count { it.state in activeStates() }
        val waiting = tasks.count { it.state in setOf(State.WAITING, State.BLOCKED, State.PAUSED, State.QUEUED) }
        val done = tasks.count { it.state == State.COMPLETE }
        return "المهام: $active نشطة · $waiting بانتظار/استئناف · $done مكتملة"
    }

    fun publicStatus(context: Context): JSONObject {
        syncSystemTasks(context)
        val tasks = all(context)
        val active = tasks.count { it.state in activeStates() }
        val waiting = tasks.count { it.state in setOf(State.WAITING, State.BLOCKED, State.PAUSED, State.QUEUED) }
        val completed = tasks.count { it.state == State.COMPLETE }
        val failed = tasks.count { it.state == State.FAILED }
        val top = tasks.firstOrNull()
        return JSONObject()
            .put("total", tasks.size)
            .put("active", active)
            .put("waiting", waiting)
            .put("completed", completed)
            .put("failed", failed)
            .put("top_state", top?.state?.name?.lowercase().orEmpty())
            .put("top_priority", top?.priority.orEmpty())
            .put("network_task_state", get(context, NETWORK_TASK_ID)?.state?.name?.lowercase().orEmpty())
    }

    private fun toTask(o: JSONObject): Task? {
        val id = o.optString("id")
        if (id.isBlank()) return null
        return Task(
            id = id,
            kind = o.optString("kind", "user_goal"),
            title = o.optString("title", "مهمة").take(180),
            goal = o.optString("goal").take(4000),
            acceptance = o.optString("acceptance").take(4000),
            state = stateOf(o.optString("state")),
            phase = o.optString("phase").take(120),
            priority = o.optString("priority", "P1").take(8),
            createdAt = o.optLong("created_at", 0L),
            updatedAt = o.optLong("updated_at", 0L),
            attempts = o.optInt("attempts", 1).coerceAtLeast(1),
            resumable = o.optBoolean("resumable", false),
            autoResume = o.optBoolean("auto_resume", false),
            lastDetail = o.optString("last_detail").take(500),
            lastEvidence = o.optString("last_evidence").take(1200),
            blocker = o.optString("blocker").take(500),
            nextAction = o.optString("next_action").take(500)
        )
    }

    private fun stateOf(raw: String): State =
        runCatching { State.valueOf(raw) }.getOrDefault(State.PAUSED)

    private fun load(context: Context): MutableList<JSONObject> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TASKS, "[]").orEmpty()
        val arr = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        val out = mutableListOf<JSONObject>()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let(out::add)
        return out
    }

    private fun save(context: Context, items: MutableList<JSONObject>) {
        val sorted = items.sortedByDescending { it.optLong("updated_at", 0L) }.take(MAX_TASKS)
        val arr = JSONArray()
        sorted.forEach(arr::put)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_TASKS, arr.toString()).apply()
    }

    private fun titleFromGoal(goal: String): String =
        goal.trim().replace(Regex("\\s+"), " ").take(72).ifBlank { "مهمة حكيم" }

    private fun nextAction(phase: HakimExecutiveLoop.Phase): String = when (phase) {
        HakimExecutiveLoop.Phase.UNDERSTANDING -> "تثبيت معيار الاكتمال"
        HakimExecutiveLoop.Phase.PLANNING -> "اختيار المسار"
        HakimExecutiveLoop.Phase.ROUTING -> "بدء التنفيذ"
        HakimExecutiveLoop.Phase.EXECUTING -> "التحقق من الأثر"
        HakimExecutiveLoop.Phase.VERIFYING -> "اعتماد النتيجة أو الإصلاح"
        HakimExecutiveLoop.Phase.REPAIRING -> "إعادة التنفيذ بعد تغيير سببي"
        HakimExecutiveLoop.Phase.WAITING_EXTERNAL -> "الاستئناف عند وصول الأثر"
        HakimExecutiveLoop.Phase.GATED -> "معالجة المانع أو اختيار مسار بديل"
        HakimExecutiveLoop.Phase.COMPLETE -> "لا توجد خطوة تالية"
        HakimExecutiveLoop.Phase.CANCELLED -> "لا توجد خطوة تالية"
    }

    private fun activeStates() = setOf(State.RUNNING, State.VERIFYING)
    private fun terminalStates() = setOf(State.COMPLETE, State.CANCELLED)

    private fun priorityRank(raw: String): Int = when (raw) {
        "P0" -> 0
        "P1" -> 1
        "P2" -> 2
        else -> 3
    }

    private fun stateRank(state: State): Int = when (state) {
        State.RUNNING -> 0
        State.VERIFYING -> 1
        State.BLOCKED -> 2
        State.WAITING -> 3
        State.QUEUED -> 4
        State.PAUSED -> 5
        State.FAILED -> 6
        State.COMPLETE -> 7
        State.CANCELLED -> 8
    }
}
