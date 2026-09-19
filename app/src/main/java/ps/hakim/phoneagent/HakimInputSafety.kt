package ps.hakim.phoneagent

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import java.util.WeakHashMap

/**
 * يعطي الكتابة اليدوية أولوية مطلقة على المبادرة التلقائية.
 * لا يقرأ النص ولا يخزنه؛ يتابع حالة التركيز فقط.
 */
object HakimInputSafety {
    const val VERSION = "INPUT-SAFETY-2026-09-15-v1"
    private const val RESUME_GRACE_MS = 8_000L
    private const val TYPING_SUPPRESS_MS = 60_000L
    private const val AFTER_TYPING_GRACE_MS = 1_500L

    private var installed = false
    private val configured = WeakHashMap<EditText, Boolean>()

    fun install(app: Application) {
        if (installed) return
        installed = true
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity is HakimAgentsChatActivity) {
                    activity.window.decorView.post { configure(activity) }
                }
            }

            override fun onActivityStarted(activity: Activity) = Unit

            override fun onActivityResumed(activity: Activity) {
                if (activity !is HakimAgentsChatActivity) return
                HakimCrashShield.suppressProactiveResumeFor(
                    activity,
                    RESUME_GRACE_MS,
                    "foreground_input_grace"
                )
                activity.window.decorView.post { configure(activity) }
            }

            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun configure(activity: HakimAgentsChatActivity) {
        walk(activity.window.decorView) { view ->
            val edit = view as? EditText ?: return@walk
            synchronized(configured) {
                if (configured.containsKey(edit)) return@synchronized
                configured[edit] = true
                edit.setOnFocusChangeListener { _, hasFocus ->
                    HakimCrashShield.suppressProactiveResumeFor(
                        activity,
                        if (hasFocus) TYPING_SUPPRESS_MS else AFTER_TYPING_GRACE_MS,
                        if (hasFocus) "typing_active" else "typing_ended"
                    )
                }
            }
        }
    }

    private fun walk(view: View, visit: (View) -> Unit) {
        visit(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) walk(view.getChildAt(i), visit)
        }
    }

    fun status(): Map<String, Any> = linkedMapOf(
        "version" to VERSION,
        "user_typing_preempts_proactive_resume" to true,
        "reads_or_stores_user_text" to false,
        "foreground_grace_ms" to RESUME_GRACE_MS,
        "typing_suppression_ms" to TYPING_SUPPRESS_MS
    )
}
