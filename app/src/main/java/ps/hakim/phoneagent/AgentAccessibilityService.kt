package ps.hakim.phoneagent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.net.Uri
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class AgentAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: AgentAccessibilityService? = null
        private const val COMMAND_TOPIC = "__COMMAND_TOPIC__"
        private const val RESULT_TOPIC = "__RESULT_TOPIC__"
    }

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private var socket: WebSocket? = null
    private var reconnectDelay = 1500L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        connect()
    }

    override fun onDestroy() {
        instance = null
        socket?.cancel()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun connect() {
        socket?.cancel()
        val req = Request.Builder().url("wss://ntfy.sh/$COMMAND_TOPIC/ws").build()
        socket = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectDelay = 1500L
                sendResult(JSONObject().put("request_id", "system").put("status", "online").put("message", "جاهز"))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val envelope = JSONObject(text)
                    if (envelope.optString("event") != "message") return
                    val payload = envelope.optString("message")
                    if (payload.isBlank()) return
                    executeCommand(JSONObject(payload))
                } catch (_: Exception) {}
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { reconnectLater() }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { reconnectLater() }
        })
    }

    private fun reconnectLater() {
        val delay = reconnectDelay.coerceAtMost(30000L)
        reconnectDelay = (reconnectDelay * 2).coerceAtMost(30000L)
        android.os.Handler(mainLooper).postDelayed({ connect() }, delay)
    }

    private fun executeCommand(cmd: JSONObject) {
        android.os.Handler(mainLooper).post {
            val requestId = cmd.optString("request_id", UUID.randomUUID().toString())
            try {
                val type = cmd.optString("type")
                val ok = when (type) {
                    "ping" -> true
                    "open_url" -> openUrl(cmd.getString("url"))
                    "snapshot" -> true
                    "tap_text" -> tapText(cmd.getString("text"), cmd.optBoolean("exact", false))
                    "set_text" -> setText(cmd.optString("target"), cmd.getString("value"))
                    "global_action" -> doGlobal(cmd.getString("action"))
                    "scroll" -> scroll(cmd.optString("direction", "down"))
                    "tap_xy" -> tapXY(cmd.getDouble("x").toFloat(), cmd.getDouble("y").toFloat())
                    "sequence" -> executeSequence(cmd.optJSONArray("steps") ?: JSONArray())
                    else -> false
                }
                android.os.Handler(mainLooper).postDelayed({
                    val out = JSONObject()
                        .put("request_id", requestId)
                        .put("status", if (ok) "ok" else "failed")
                        .put("type", type)
                        .put("package", rootInActiveWindow?.packageName?.toString() ?: "")
                        .put("snapshot", snapshot())
                    sendResult(out)
                }, cmd.optLong("after_ms", 700L).coerceIn(100L, 5000L))
            } catch (e: Exception) {
                sendResult(JSONObject().put("request_id", requestId).put("status", "error").put("message", e.message ?: "خطأ"))
            }
        }
    }

    private fun executeSequence(steps: JSONArray): Boolean {
        var result = true
        for (i in 0 until steps.length()) {
            val step = steps.optJSONObject(i) ?: continue
            result = result && when (step.optString("type")) {
                "open_url" -> openUrl(step.getString("url"))
                "tap_text" -> tapText(step.getString("text"), step.optBoolean("exact", false))
                "set_text" -> setText(step.optString("target"), step.getString("value"))
                "global_action" -> doGlobal(step.getString("action"))
                "scroll" -> scroll(step.optString("direction", "down"))
                "tap_xy" -> tapXY(step.getDouble("x").toFloat(), step.getDouble("y").toFloat())
                else -> false
            }
            try { Thread.sleep(step.optLong("wait_ms", 350L).coerceIn(0L, 2500L)) } catch (_: Exception) {}
        }
        return result
    }

    private fun openUrl(url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        startActivity(intent)
        return true
    }

    private fun tapText(text: String, exact: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collect(root, nodes, 0, 120)
        val target = nodes.firstOrNull { n ->
            val t = n.text?.toString().orEmpty()
            val d = n.contentDescription?.toString().orEmpty()
            if (exact) t == text || d == text else t.contains(text, true) || d.contains(text, true)
        } ?: return false
        return clickNodeOrParent(target)
    }

    private fun setText(targetHint: String, value: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collect(root, nodes, 0, 120)
        val editable = nodes.filter { it.isEditable || it.className?.toString()?.contains("EditText") == true }
        val target = if (targetHint.isBlank()) {
            editable.firstOrNull { it.isFocused } ?: editable.firstOrNull()
        } else {
            editable.firstOrNull {
                it.text?.toString().orEmpty().contains(targetHint, true) ||
                it.contentDescription?.toString().orEmpty().contains(targetHint, true) ||
                it.viewIdResourceName.orEmpty().contains(targetHint, true)
            } ?: editable.firstOrNull()
        } ?: return false
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun doGlobal(action: String): Boolean = when (action.lowercase()) {
        "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
        "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
        "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        else -> false
    }

    private fun scroll(direction: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collect(root, nodes, 0, 120)
        val scrollable = nodes.firstOrNull { it.isScrollable } ?: root
        val action = if (direction.equals("up", true)) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        return scrollable.performAction(action)
    }

    private fun tapXY(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 70)).build()
        return dispatchGesture(gesture, null, null)
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        repeat(6) {
            val n = current ?: return false
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            current = n.parent
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun snapshot(): JSONArray {
        val root = rootInActiveWindow ?: return JSONArray()
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collect(root, nodes, 0, 90)
        val arr = JSONArray()
        for (n in nodes) {
            val text = n.text?.toString().orEmpty()
            val desc = n.contentDescription?.toString().orEmpty()
            val id = n.viewIdResourceName.orEmpty()
            if (text.isBlank() && desc.isBlank() && id.isBlank() && !n.isClickable && !n.isEditable) continue
            val r = android.graphics.Rect(); n.getBoundsInScreen(r)
            arr.put(JSONObject()
                .put("text", text.take(180)).put("desc", desc.take(180)).put("id", id.take(160))
                .put("class", n.className?.toString().orEmpty().substringAfterLast('.'))
                .put("clickable", n.isClickable).put("editable", n.isEditable).put("focused", n.isFocused)
                .put("bounds", "${r.left},${r.top},${r.right},${r.bottom}"))
            if (arr.length() >= 45) break
        }
        return arr
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>, depth: Int, max: Int) {
        if (out.size >= max || depth > 18) return
        out.add(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, out, depth + 1, max)
            if (out.size >= max) break
        }
    }

    private fun sendResult(obj: JSONObject) {
        val raw = obj.toString()
        val requestId = obj.optString("request_id", "system")
        val chunkSize = 1900
        val total = ((raw.length + chunkSize - 1) / chunkSize).coerceAtLeast(1)
        for (i in 0 until total) {
            val part = if (raw.isEmpty()) "" else raw.substring(i * chunkSize, minOf(raw.length, (i + 1) * chunkSize))
            val body = JSONObject().put("request_id", requestId).put("chunk", i + 1).put("total", total).put("data", part).toString()
            val req = Request.Builder().url("https://ntfy.sh/$RESULT_TOPIC")
                .post(body.toRequestBody("text/plain; charset=utf-8".toMediaType())).build()
            client.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {}
                override fun onResponse(call: Call, response: Response) { response.close() }
            })
        }
    }
}
