package ps.hakim.phoneagent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** تحكم محلي مصرح به في واجهة أندرويد، داخل تطبيق حكيم نفسه. */
class HakimAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }

    fun uiSnapshot(limit: Int = 250): JSONArray {
        val arr = JSONArray()
        val root = rootInActiveWindow ?: return arr
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty() && arr.length() < limit) {
            val n = queue.removeFirst()
            val r = android.graphics.Rect()
            n.getBoundsInScreen(r)
            val sensitive = isSensitive(n)
            arr.put(
                JSONObject()
                    .put("package", n.packageName?.toString().orEmpty())
                    .put("text", if (sensitive) "[مخفي]" else n.text?.toString().orEmpty().take(500))
                    .put("desc", if (sensitive) "[مخفي]" else n.contentDescription?.toString().orEmpty().take(500))
                    .put("id", n.viewIdResourceName.orEmpty().take(300))
                    .put("class", n.className?.toString().orEmpty().take(200))
                    .put("clickable", n.isClickable)
                    .put("editable", n.isEditable)
                    .put("scrollable", n.isScrollable)
                    .put("sensitive", sensitive)
                    .put("bounds", JSONArray(listOf(r.left, r.top, r.right, r.bottom)))
            )
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return arr
    }

    fun action(obj: JSONObject): Boolean = when (obj.optString("action")) {
        "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
        "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
        "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        "quick_settings" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        "click_text" -> clickText(obj.optString("text"))
        "set_text" -> setText(obj.optString("id"), obj.optString("text"), obj.optString("value"))
        "set_first_editable" -> setFirstEditableForPackage(obj.optString("package"), obj.optString("value"))
        "click_text_in_package" -> clickTextInPackage(obj.optString("package"), obj.optString("text"))
        "tap" -> tap(obj.optDouble("x").toFloat(), obj.optDouble("y").toFloat())
        "swipe" -> swipe(
            obj.optDouble("x1").toFloat(), obj.optDouble("y1").toFloat(),
            obj.optDouble("x2").toFloat(), obj.optDouble("y2").toFloat(),
            obj.optLong("duration", 400L)
        )
        else -> false
    }

    fun foregroundPackage(): String = rootInActiveWindow?.packageName?.toString().orEmpty()

    /** نص مرئي من حزمة مسموحة فقط، مع تنقيح الحقول الحساسة. */
    fun visibleTextForPackage(packageName: String, limit: Int = 220): String {
        if (!safeAutomationPackages.contains(packageName)) return ""
        val root = rootInActiveWindow ?: return ""
        if (root.packageName?.toString() != packageName) return ""
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        val lines = linkedSetOf<String>()
        while (queue.isNotEmpty() && lines.size < limit) {
            val n = queue.removeFirst()
            if (!isSensitive(n) && n.isVisibleToUser) {
                val text = n.text?.toString().orEmpty().trim()
                val desc = n.contentDescription?.toString().orEmpty().trim()
                if (text.isNotBlank()) lines += text.take(1200)
                else if (desc.isNotBlank()) lines += desc.take(600)
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return lines.joinToString("\n").take(30000)
    }

    /** كتابة مقيدة بمحرك مسموح، ولا تعمل على أي حقل حساس. */
    fun setFirstEditableForPackage(packageName: String, value: String): Boolean {
        if (!safeAutomationPackages.contains(packageName) || value.isBlank()) return false
        val root = rootInActiveWindow ?: return false
        if (root.packageName?.toString() != packageName) return false
        val editable = editableNodes(root)
        val preferred = editable.firstOrNull { n ->
            val probe = listOf(n.viewIdResourceName.orEmpty(), n.contentDescription?.toString().orEmpty())
                .joinToString(" ").lowercase()
            Regex("message|prompt|composer|chat|رسالة|اكتب|محادثة").containsMatchIn(probe)
        } ?: editable.lastOrNull() ?: return false
        val b = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value.take(24000)) }
        return preferred.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
    }

    fun clickTextInPackage(packageName: String, text: String): Boolean {
        if (!safeAutomationPackages.contains(packageName) || text.isBlank()) return false
        val root = rootInActiveWindow ?: return false
        if (root.packageName?.toString() != packageName) return false
        val target = text.trim().lowercase()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (!isSensitive(n)) {
                val label = listOf(n.text?.toString().orEmpty(), n.contentDescription?.toString().orEmpty())
                    .joinToString(" ").trim().lowercase()
                if (label == target || label.contains(target)) {
                    var cur: AccessibilityNodeInfo? = n
                    repeat(5) {
                        if (cur?.isClickable == true) return cur!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        cur = cur?.parent
                    }
                }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return false
    }

    fun performImeEnterForPackage(packageName: String): Boolean {
        if (!safeAutomationPackages.contains(packageName) || Build.VERSION.SDK_INT < 30) return false
        val root = rootInActiveWindow ?: return false
        if (root.packageName?.toString() != packageName) return false
        val editable = editableNodes(root).lastOrNull() ?: return false
        return editable.performAction(AccessibilityNodeInfo.ACTION_IME_ENTER)
    }

    private fun editableNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        val editable = mutableListOf<AccessibilityNodeInfo>()
        while (queue.isNotEmpty() && editable.size < 12) {
            val n = queue.removeFirst()
            if (n.isEditable && n.isVisibleToUser && !isSensitive(n)) editable += n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return editable
    }

    private fun isSensitive(n: AccessibilityNodeInfo): Boolean {
        if (n.isPassword) return true
        val probe = listOf(
            n.viewIdResourceName.orEmpty(),
            n.text?.toString().orEmpty(),
            n.contentDescription?.toString().orEmpty()
        ).joinToString(" ").lowercase()
        return Regex("password|passcode|otp|one.?time|pin|cvv|cvc|card.?number|security.?code|كلمة.?المرور|رمز.?التحقق|رمز.?الأمان|رقم.?البطاقة").containsMatchIn(probe)
    }

    private fun clickText(text: String): Boolean {
        if (text.isBlank()) return false
        val root = rootInActiveWindow ?: return false
        for (n in root.findAccessibilityNodeInfosByText(text)) {
            if (isSensitive(n)) continue
            var cur: AccessibilityNodeInfo? = n
            repeat(5) {
                if (cur?.isClickable == true) return cur!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                cur = cur?.parent
            }
        }
        return false
    }

    private fun setText(id: String, textHint: String, value: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = when {
            id.isNotBlank() -> runCatching { root.findAccessibilityNodeInfosByViewId(id) }.getOrDefault(emptyList())
            textHint.isNotBlank() -> root.findAccessibilityNodeInfosByText(textHint)
            else -> emptyList()
        }
        val node = nodes.firstOrNull { it.isEditable && !isSensitive(it) } ?: return false
        val b = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
    }

    private fun tap(x: Float, y: Float): Boolean {
        val p = Path().apply { moveTo(x, y) }
        return dispatchGesture(
            GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, 80)).build(),
            null,
            null
        )
    }

    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long): Boolean {
        val p = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        return dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(p, 0, duration.coerceIn(100, 3000)))
                .build(),
            null,
            null
        )
    }

    fun screenshotBase64(): String? {
        if (Build.VERSION.SDK_INT < 30) return null
        val latch = CountDownLatch(1)
        var result: String? = null
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(s: ScreenshotResult) {
                try {
                    val hw = s.hardwareBuffer
                    val bmp = Bitmap.wrapHardwareBuffer(hw, s.colorSpace ?: ColorSpace.get(ColorSpace.Named.SRGB))
                    val copy = bmp?.copy(Bitmap.Config.ARGB_8888, false)
                    val out = ByteArrayOutputStream()
                    copy?.compress(Bitmap.CompressFormat.PNG, 90, out)
                    result = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                    copy?.recycle()
                    hw.close()
                } catch (_: Exception) {
                } finally {
                    latch.countDown()
                }
            }

            override fun onFailure(errorCode: Int) { latch.countDown() }
        })
        latch.await(3, TimeUnit.SECONDS)
        return result
    }

    companion object {
        private val safeAutomationPackages = setOf("com.openai.chatgpt")
        @Volatile var instance: HakimAccessibilityService? = null
            private set
    }
}
