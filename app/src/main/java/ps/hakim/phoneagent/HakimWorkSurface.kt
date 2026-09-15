package ps.hakim.phoneagent

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.view.ViewCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.WeakHashMap

/**
 * طبقة «العمل الجاري» في حكيم.
 *
 * أثناء تنفيذ مهمة يرى الإنسان ماذا يفعل حكيم الآن، وما الأداة/المسار المستخدم،
 * وما آخر خطوة مثبتة، بدون كشف سلسلة التفكير الخاصة أو الأسرار أو telemetry خام.
 * اللوحة تُحقن داخل composerOuter حتى تتحرك مع إصلاح IME وتبقى فوق لوحة المفاتيح.
 */
object HakimWorkSurface {
    const val VERSION = "WORK-SURFACE-2026-09-15-v1"
    private const val PANEL_TAG = "hakim_work_surface_panel"
    private const val HISTORY_PREFS = "hakim_work_surface_history"
    private const val HISTORY_KEY = "events"
    private const val MAX_HISTORY = 12

    private var installed = false
    private val controllers = WeakHashMap<HakimAgentsChatActivity, Controller>()

    fun install(app: Application) {
        if (installed) return
        installed = true
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                val chat = activity as? HakimAgentsChatActivity ?: return
                chat.window.decorView.post { attach(chat) }
            }

            override fun onActivityStarted(activity: Activity) = Unit

            override fun onActivityResumed(activity: Activity) {
                val chat = activity as? HakimAgentsChatActivity ?: return
                chat.window.decorView.post {
                    attach(chat)
                    controllers[chat]?.resume()
                }
            }

            override fun onActivityPaused(activity: Activity) {
                (activity as? HakimAgentsChatActivity)?.let { controllers[it]?.pause() }
            }

            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) {
                val chat = activity as? HakimAgentsChatActivity ?: return
                controllers.remove(chat)?.destroy()
            }
        })
    }

    private fun attach(activity: HakimAgentsChatActivity) {
        synchronized(controllers) {
            if (controllers.containsKey(activity)) return
        }
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val input = findFirstEditText(content) ?: return
        val inputRow = input.parent as? View ?: return
        val composerOuter = inputRow.parent as? LinearLayout ?: return
        if (composerOuter.findViewWithTag<View>(PANEL_TAG) != null) return

        val controller = Controller(activity)
        composerOuter.addView(controller.panel, 0)
        synchronized(controllers) { controllers[activity] = controller }
        controller.resume()
        ViewCompat.requestApplyInsets(content)
    }

    private class Controller(private val activity: HakimAgentsChatActivity) : SharedPreferences.OnSharedPreferenceChangeListener {
        private val palette = HakimChatUi.palette(activity)
        private val missionPrefs = activity.getSharedPreferences("hakim_mission_meta", Context.MODE_PRIVATE)
        private val agentPrefs = activity.getSharedPreferences("hakim_agents", Context.MODE_PRIVATE)
        private val historyPrefs = activity.getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE)
        private var listening = false
        private var expanded = false
        private var lastFingerprint = ""

        private val current = TextView(activity).apply {
            textSize = 13.5f
            setTextColor(palette.text)
            textDirection = View.TEXT_DIRECTION_RTL
            gravity = Gravity.START
            includeFontPadding = false
            maxLines = 2
        }

        private val details = TextView(activity).apply {
            textSize = 12.5f
            setTextColor(palette.muted)
            textDirection = View.TEXT_DIRECTION_RTL
            gravity = Gravity.START
            includeFontPadding = false
            visibility = View.GONE
            setPadding(0, HakimChatUi.dp(activity, 6f), 0, 0)
        }

        private val expand = actionText("التفاصيل") {
            expanded = !expanded
            details.visibility = if (expanded) View.VISIBLE else View.GONE
            refresh(remember = false)
        }

        private val tools = actionText("الأدوات") { anchor -> showTools(anchor) }

        val panel: LinearLayout = LinearLayout(activity).apply {
            tag = PANEL_TAG
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(
                HakimChatUi.dp(activity, 12f),
                HakimChatUi.dp(activity, 9f),
                HakimChatUi.dp(activity, 12f),
                HakimChatUi.dp(activity, 9f)
            )
            background = HakimChatUi.rounded(palette.surface, 16f, activity, palette.border)

            val head = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                gravity = Gravity.CENTER_VERTICAL
            }
            head.addView(TextView(activity).apply {
                text = "العمل الجاري"
                textSize = 13f
                setTextColor(palette.text)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                includeFontPadding = false
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            head.addView(tools)
            head.addView(expand)
            addView(head)
            addView(current)
            addView(details)
        }.also {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, HakimChatUi.dp(activity, 6f))
            it.layoutParams = lp
        }

        fun resume() {
            if (!listening) {
                missionPrefs.registerOnSharedPreferenceChangeListener(this)
                agentPrefs.registerOnSharedPreferenceChangeListener(this)
                listening = true
            }
            refresh(remember = false)
        }

        fun pause() {
            if (!listening) return
            missionPrefs.unregisterOnSharedPreferenceChangeListener(this)
            agentPrefs.unregisterOnSharedPreferenceChangeListener(this)
            listening = false
        }

        fun destroy() = pause()

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            activity.runOnUiThread { refresh(remember = true) }
        }

        private fun refresh(remember: Boolean) {
            if (activity.isFinishing || activity.isDestroyed) return
            val phase = missionPrefs.getString("phase", "IDLE").orEmpty()
            val active = missionPrefs.getBoolean("active", false)
            val evidence = safeText(missionPrefs.getString("evidence", "").orEmpty())
            val plan = parsePlan(agentPrefs.getString("last_plan", "").orEmpty())
            val tool = currentTool(plan)
            val phaseLabel = phaseLabel(phase, active)
            current.text = buildString {
                append(phaseLabel)
                if (tool.isNotBlank()) append("  •  الأداة: ").append(tool)
            }

            val fingerprint = "$phase|$tool|$evidence"
            if (remember && fingerprint != lastFingerprint && (active || evidence.isNotBlank())) {
                appendHistory(phaseLabel, tool, evidence)
            }
            lastFingerprint = fingerprint

            val recent = readHistory().takeLast(if (expanded) 6 else 3)
            details.text = buildString {
                if (evidence.isNotBlank()) {
                    append("الآن: ").append(evidence.take(220))
                    if (recent.isNotEmpty()) append('\n')
                }
                recent.forEachIndexed { index, event ->
                    append(if (index == recent.lastIndex) "● " else "✓ ")
                    append(event)
                    if (index != recent.lastIndex) append('\n')
                }
            }.ifBlank { "لا توجد عملية نشطة. عند بدء مهمة ستظهر خطواتها هنا لحظةً بلحظة." }
            details.visibility = if (expanded || active) View.VISIBLE else View.GONE
            expand.text = if (expanded) "إخفاء" else "التفاصيل"
        }

        private fun parsePlan(raw: String): JSONObject? = runCatching {
            if (raw.isBlank()) null else JSONObject(raw)
        }.getOrNull()

        private fun currentTool(plan: JSONObject?): String {
            if (HakimRuntime.visibleWebView() != null) return "متصفح حكيم"
            val route = plan?.optString("route").orEmpty()
            val agents = plan?.optJSONArray("agents") ?: JSONArray()
            val names = buildSet {
                for (i in 0 until agents.length()) add(agents.optString(i))
            }
            return when {
                route == "browser" || "BROWSER" in names -> "متصفح حكيم"
                route == "research_then_replan" || "RESEARCH" in names -> "البحث والتحقق"
                "FILES" in names -> "الملفات"
                "FORMS" in names -> "النماذج"
                "COMMUNICATION" in names -> "التواصل"
                route == "reasoning" -> "محرك الاستدلال"
                else -> "التنفيذ المحلي"
            }
        }

        private fun phaseLabel(phase: String, active: Boolean): String = when (phase) {
            "UNDERSTAND" -> "أفهم المطلوب وأجمع السياق"
            "PLAN" -> "أخطط وأختار أفضل الأدوات"
            "EXECUTE" -> "أنفذ الخطوة الحالية"
            "VERIFY" -> "أتحقق من النتيجة الفعلية"
            "RECOVER" -> "أتعافى وأعيد التخطيط دون تكرار المنجز"
            "WAITING_APPROVAL" -> "جاهز للخطوة التالية — بانتظار موافقتك"
            "WAITING_CREDENTIAL" -> "بانتظار اعتماد حساس منك"
            "WAITING_TRUST" -> "بانتظار اعتماد الموقع/الخدمة"
            "COMPLETE" -> "اكتملت المهمة وتحقق الأثر"
            "CANCELLED" -> "المهمة متوقفة بأمرك"
            "BLOCKED" -> "المهمة متوقفة بأمان"
            else -> if (active) "أتابع المهمة الحالية" else "جاهز للعمل"
        }

        private fun appendHistory(phase: String, tool: String, evidence: String) {
            val array = runCatching { JSONArray(historyPrefs.getString(HISTORY_KEY, "[]")) }.getOrDefault(JSONArray())
            val text = buildString {
                append(phase)
                if (tool.isNotBlank()) append(" — ").append(tool)
                if (evidence.isNotBlank()) append(": ").append(evidence.take(180))
            }
            if (text.isBlank()) return
            array.put(safeText(text))
            val compact = JSONArray()
            val start = (array.length() - MAX_HISTORY).coerceAtLeast(0)
            for (i in start until array.length()) compact.put(array.optString(i))
            historyPrefs.edit().putString(HISTORY_KEY, compact.toString()).apply()
        }

        private fun readHistory(): List<String> {
            val array = runCatching { JSONArray(historyPrefs.getString(HISTORY_KEY, "[]")) }.getOrDefault(JSONArray())
            val out = ArrayList<String>(array.length())
            for (i in 0 until array.length()) {
                val item = safeText(array.optString(i))
                if (item.isNotBlank()) out += item
            }
            return out
        }

        private fun showTools(anchor: View) {
            val toolList = HakimWorkToolHub.tools(activity)
            val popup = PopupMenu(activity, anchor)
            toolList.forEachIndexed { index, tool ->
                popup.menu.add(0, 7000 + index, index, tool.menuLabel())
            }
            popup.setOnMenuItemClickListener { item ->
                val tool = toolList.getOrNull(item.itemId - 7000) ?: return@setOnMenuItemClickListener false
                HakimWorkToolHub.open(activity, tool.id)
                true
            }
            popup.show()
        }

        private fun actionText(label: String, action: (View) -> Unit): TextView = TextView(activity).apply {
            text = label
            textSize = 12f
            setTextColor(palette.accent)
            gravity = Gravity.CENTER
            includeFontPadding = false
            isClickable = true
            isFocusable = true
            setPadding(
                HakimChatUi.dp(activity, 8f),
                HakimChatUi.dp(activity, 6f),
                HakimChatUi.dp(activity, 8f),
                HakimChatUi.dp(activity, 6f)
            )
            setOnClickListener { action(it) }
        }
    }

    private fun findFirstEditText(view: View): EditText? {
        if (view is EditText) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findFirstEditText(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun safeText(raw: String): String {
        var out = raw.replace(Regex("\\s+"), " ").trim()
        out = Regex("(?i)(password|passcode|otp|pin|cvv|cvc|api.?key|secret|كلمة\\s*المرور|رمز\\s*التحقق|رمز\\s*الأمان|مفتاح\\s*سري)\\s*[:=]?\\s*\\S+")
            .replace(out) { "${it.groupValues[1]}: [سري محذوف]" }
        out = Regex("(?<!\\d)\\d{13,19}(?!\\d)").replace(out, "[رقم حساس محذوف]")
        return out.take(320)
    }

    fun status(): Map<String, Any> = linkedMapOf(
        "version" to VERSION,
        "live_operation_surface" to true,
        "current_tool_visible" to true,
        "recent_steps_visible" to true,
        "private_chain_of_thought_exposed" to false,
        "secret_redaction" to true,
        "injected_inside_composer_outer" to true,
        "moves_with_ime_docked_composer" to true,
        "persistent_recent_history_limit" to MAX_HISTORY
    )
}
