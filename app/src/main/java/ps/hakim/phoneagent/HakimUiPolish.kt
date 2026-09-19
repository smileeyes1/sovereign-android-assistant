package ps.hakim.phoneagent

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView

/** صقل واجهة المحادثة دون تغيير منطق المهمة: أقل نص، أوضح فعل، ولا مصطلحات داخلية في الواجهة الأساسية. */
object HakimUiPolish {
    const val VERSION = "UI-POLISH-2026-09-15-v1"
    @Volatile private var installed = false

    fun install(app: Application) {
        if (installed) return
        installed = true
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity is HakimAgentsChatActivity) activity.window.decorView.post { apply(activity) }
            }
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) {
                if (activity is HakimAgentsChatActivity) activity.window.decorView.post { apply(activity) }
            }
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun apply(activity: HakimAgentsChatActivity) {
        walk(activity.window.decorView) { view ->
            when (view) {
                is EditText -> if (view.hint?.toString()?.startsWith("اكتب ما تريد") == true) {
                    view.hint = "ماذا تريد أن تنجز؟"
                }
                is TextView -> when (view.text?.toString()) {
                    "تلقائي — حكيم يختار" -> view.text = "تلقائي"
                    "حكيم قد يتوقف فقط عند قرار جوهري أو سر أو صلاحية لا يمكن تجاوزها بأمان." ->
                        view.text = "خصوصيتك أولًا • توقف في أي وقت"
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
        "simple_human_facing_labels" to true,
        "technical_detail_hidden_by_default" to true,
        "rtl_preserved" to true
    )
}
