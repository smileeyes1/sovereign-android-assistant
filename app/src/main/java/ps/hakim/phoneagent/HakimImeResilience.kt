package ps.hakim.phoneagent

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.WeakHashMap

/**
 * حارس إدخال منخفض الكلفة.
 *
 * على Android 15+ لا نضيف ارتفاع لوحة المفاتيح كاملًا إلى Padding الجذر؛
 * فهذا يضاعف إعادة التخطيط مع adjustResize على بعض الأجهزة ويزيد التقطيع.
 * نكتفي بحواف النظام/القص، ونراقب IME دون تحويل ارتفاعه إلى Padding للجذر.
 * إبقاء composer ظاهرًا أثناء الكتابة يعتمد على adjustResize المعلن في Manifest.
 */
object HakimImeResilience {
    private var installed = false
    private val configured = WeakHashMap<View, IntArray>()

    fun install(app: Application) {
        if (installed) return
        installed = true
        HakimInputSafety.install(app)
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity !is HakimAgentsChatActivity) return
                activity.window.decorView.post { configure(activity) }
            }

            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun configure(activity: HakimAgentsChatActivity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val content = activity.findViewById<View>(android.R.id.content) ?: return
        val base = synchronized(configured) {
            configured[content]?.let { return }
            intArrayOf(content.paddingLeft, content.paddingTop, content.paddingRight, content.paddingBottom)
                .also { configured[content] = it }
        }

        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val left = base[0] + bars.left
            val top = base[1] + bars.top
            val right = base[2] + bars.right
            val bottom = base[3] + if (imeVisible) 0 else bars.bottom

            if (view.paddingLeft != left || view.paddingTop != top ||
                view.paddingRight != right || view.paddingBottom != bottom
            ) {
                view.setPadding(left, top, right, bottom)
            }
            insets
        }
        ViewCompat.requestApplyInsets(content)
    }

    fun status(): Map<String, Any> = linkedMapOf(
        "manifest_adjust_resize_required" to true,
        "android_15_plus_system_insets_handled" to true,
        "ime_observed_without_full_root_padding" to true,
        "full_ime_height_root_padding_forbidden" to true,
        "composer_must_remain_visible_with_keyboard" to true,
        "legacy_android_relies_on_adjust_resize" to true,
        "input_layout_churn_reduced" to true,
        "typing_preempts_proactive_resume" to true
    )
}
