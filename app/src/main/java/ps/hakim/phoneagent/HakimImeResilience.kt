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
 * يحمي واجهة المحادثة من تغطية لوحة المفاتيح، خصوصًا مع فرض edge-to-edge
 * على Android 15+ للتطبيقات التي تستهدف API 35.
 *
 * على Android 14 وما قبله يكفي adjustResize التقليدي في الـManifest.
 * على Android 15+ نضيف حجزًا صريحًا لمساحة IME/شريط التنقل لأن الواجهة
 * مبنية بViews مخصصة وليست Material/Compose ذات معالجة insets تلقائية.
 */
object HakimImeResilience {
    private var installed = false
    private val configured = WeakHashMap<View, IntArray>()

    fun install(app: Application) {
        if (installed) return
        installed = true
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
            val navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val bottomInset = if (imeVisible) maxOf(ime.bottom, navigation.bottom) else navigation.bottom
            view.setPadding(base[0], base[1], base[2], base[3] + bottomInset)
            insets
        }
        ViewCompat.requestApplyInsets(content)
    }

    fun status(): Map<String, Any> = linkedMapOf(
        "manifest_adjust_resize_required" to true,
        "android_15_plus_ime_insets_handled" to true,
        "composer_must_remain_visible_with_keyboard" to true,
        "legacy_android_relies_on_adjust_resize" to true
    )
}
