package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * طبقة ChatGPT المقيدة: لا تقرأ ولا تغيّر أي واجهة ما لم تكن نافذة ChatGPT الأصلية هي النشطة.
 * القراءة مستقلة عن الأفعال المتغيرة للحالة؛ الأخيرة تصل فقط عبر chatgpt_action وبوابة موافقة القناة الموحدة.
 */
object HakimChatGptController {
    const val CHATGPT_PACKAGE = "com.openai.chatgpt"
    private const val MAX_MESSAGE_CHARS = 12000

    private val ignoredLabels = setOf(
        "chatgpt", "openai", "search", "بحث", "settings", "الإعدادات",
        "new chat", "محادثة جديدة", "library", "المكتبة", "menu", "القائمة"
    )

    fun read(context: Context, payload: JSONObject): JSONObject {
        val service = HakimAccessibilityService.instance
            ?: return error("accessibility_unavailable")
        if (!service.isActivePackage(CHATGPT_PACKAGE)) return error("chatgpt_not_active")

        val mode = payload.optString("mode", "snapshot")
        return when (mode) {
            "snapshot" -> JSONObject()
                .put("ok", true)
                .put("scope", CHATGPT_PACKAGE)
                .put("nodes", service.uiSnapshotForPackage(CHATGPT_PACKAGE, payload.optInt("limit", 250).coerceIn(25, 500)))

            "list_visible_conversations" -> {
                val snapshot = service.uiSnapshotForPackage(CHATGPT_PACKAGE, 500)
                val titles = visibleConversationCandidates(snapshot)
                val stored = HakimChatGptIndex.storeVisibleTitles(context, titles)
                JSONObject()
                    .put("ok", true)
                    .put("scope", CHATGPT_PACKAGE)
                    .put("titles", titles)
                    .put("encrypted_index_count", stored)
                    .put("coverage", "visible_only")
            }

            "read_visible_conversation" -> {
                val snapshot = service.uiSnapshotForPackage(CHATGPT_PACKAGE, 500)
                JSONObject()
                    .put("ok", true)
                    .put("scope", CHATGPT_PACKAGE)
                    .put("messages", readableText(snapshot))
                    .put("coverage", "visible_only")
            }

            "index" -> JSONObject()
                .put("ok", true)
                .put("scope", CHATGPT_PACKAGE)
                .put("titles", HakimChatGptIndex.load(context))
                .put("coverage", "previously_visible_titles_only")

            "search_index" -> {
                val q = payload.optString("query").trim()
                if (q.isBlank()) return error("query_required")
                val all = HakimChatGptIndex.load(context)
                val matches = JSONArray()
                for (i in 0 until all.length()) {
                    val title = all.optString(i)
                    if (title.contains(q, ignoreCase = true)) matches.put(title)
                }
                JSONObject()
                    .put("ok", true)
                    .put("scope", CHATGPT_PACKAGE)
                    .put("query", q)
                    .put("matches", matches)
            }

            else -> error("unsupported_chatgpt_read_mode")
        }
    }

    fun action(context: Context, payload: JSONObject): JSONObject {
        val service = HakimAccessibilityService.instance
            ?: return error("accessibility_unavailable")
        if (!service.isActivePackage(CHATGPT_PACKAGE)) return error("chatgpt_not_active")

        val mode = payload.optString("mode")
        val ok = when (mode) {
            "open_conversation" -> {
                val title = payload.optString("title").trim()
                title.isNotBlank() && service.clickTextWithinPackage(CHATGPT_PACKAGE, title)
            }
            "new_chat" -> clickAny(service, listOf("New chat", "محادثة جديدة", "دردشة جديدة"))
            "send_message" -> {
                val message = payload.optString("message")
                if (message.isBlank() || message.length > MAX_MESSAGE_CHARS) return error("invalid_message")
                val written = service.setTextFirstEditableWithinPackage(CHATGPT_PACKAGE, message)
                written && clickAny(service, listOf("Send", "إرسال", "Send prompt", "إرسال الرسالة"))
            }
            "scroll_forward" -> service.scrollWithinPackage(CHATGPT_PACKAGE, true)
            "scroll_backward" -> service.scrollWithinPackage(CHATGPT_PACKAGE, false)
            else -> return error("unsupported_chatgpt_action_mode")
        }
        return JSONObject()
            .put("ok", ok)
            .put("scope", CHATGPT_PACKAGE)
            .put("mode", mode)
            .put("error", if (ok) JSONObject.NULL else "chatgpt_action_failed")
    }

    private fun clickAny(service: HakimAccessibilityService, labels: List<String>): Boolean {
        for (label in labels) {
            if (service.clickTextWithinPackage(CHATGPT_PACKAGE, label)) return true
        }
        return false
    }

    private fun visibleConversationCandidates(snapshot: JSONArray): JSONArray {
        val out = JSONArray()
        val seen = LinkedHashSet<String>()
        for (i in 0 until snapshot.length()) {
            val node = snapshot.optJSONObject(i) ?: continue
            if (node.optBoolean("sensitive") || node.optBoolean("editable") || !node.optBoolean("clickable")) continue
            val text = node.optString("text").trim().replace(Regex("\\s+"), " ")
            if (text.length !in 2..160) continue
            if (text.lowercase() in ignoredLabels) continue
            if (seen.add(text)) out.put(text)
        }
        return out
    }

    private fun readableText(snapshot: JSONArray): JSONArray {
        val out = JSONArray()
        val seen = LinkedHashSet<String>()
        for (i in 0 until snapshot.length()) {
            val node = snapshot.optJSONObject(i) ?: continue
            if (node.optBoolean("sensitive")) continue
            for (key in listOf("text", "desc")) {
                val value = node.optString(key).trim().replace(Regex("\\s+"), " ")
                if (value.isNotBlank() && value != "[مخفي]" && seen.add(value)) out.put(value)
            }
        }
        return out
    }

    private fun error(code: String): JSONObject =
        JSONObject().put("ok", false).put("error", code).put("scope", CHATGPT_PACKAGE)
}
