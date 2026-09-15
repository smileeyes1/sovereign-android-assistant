package ps.hakim.phoneagent

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ListView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import java.util.WeakHashMap
import kotlin.math.roundToInt

/**
 * حارس احترافي لمربع الكتابة مع لوحة المفاتيح.
 *
 * نبقي adjustResize لمسار التوافق الرسمي، لكن لا نفترض أنه كافٍ على كل جهاز/OEM.
 * إذا بقي composer متداخلًا فعليًا مع IME، نحسب مقدار التداخل الحقيقي فقط
 * ونرفعه بالـtranslation دون إضافة ارتفاع لوحة المفاتيح إلى Padding الجذر.
 * كما نزامن الحركة مع WindowInsetsAnimationCompat على Android 11+، ونزيد
 * padding سجل الرسائل بمقدار التداخل فقط كي يبقى آخر محتوى قابلًا للقراءة.
 */
object HakimImeResilience {
    private var installed = false

    private data class UiState(
        val baseContentPadding: IntArray,
        val baseListPadding: IntArray,
        var pendingInsets: WindowInsetsCompat? = null,
        var animationRunning: Boolean = false,
        var animationStartRenderedTop: Float? = null,
        var animationStartTranslation: Float = 0f,
        var animationTargetTranslation: Float = 0f,
        var lastTargetOverlap: Int = 0
    )

    private val configured = WeakHashMap<View, UiState>()

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
        val content = activity.findViewById<View>(android.R.id.content) ?: return
        val input = findFirstEditText(content) ?: return
        val inputRow = input.parent as? View ?: input
        val composer = inputRow.parent as? View ?: inputRow
        val messageList = findFirstListView(content) ?: return

        val state = synchronized(configured) {
            configured[content]?.let { return }
            UiState(
                baseContentPadding = intArrayOf(
                    content.paddingLeft,
                    content.paddingTop,
                    content.paddingRight,
                    content.paddingBottom
                ),
                baseListPadding = intArrayOf(
                    messageList.paddingLeft,
                    messageList.paddingTop,
                    messageList.paddingRight,
                    messageList.paddingBottom
                )
            ).also { configured[content] = it }
        }

        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            state.pendingInsets = insets

            // Android 15+ يفرض edge-to-edge على targetSdk 35، لذلك نحمي حواف النظام
            // فقط. لا نضع ime.bottom أبدًا في Padding الجذر.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
                val left = state.baseContentPadding[0] + bars.left
                val top = state.baseContentPadding[1] + bars.top
                val right = state.baseContentPadding[2] + bars.right
                val bottom = state.baseContentPadding[3] + bars.bottom
                if (view.paddingLeft != left || view.paddingTop != top ||
                    view.paddingRight != right || view.paddingBottom != bottom
                ) {
                    view.setPadding(left, top, right, bottom)
                }
            }

            // عند عدم وجود حركة IME (مثل أول attach أو استعادة النشاط)، سوِّ الوضع بعد layout.
            view.post {
                if (!state.animationRunning) {
                    settle(activity, composer, messageList, state, insets)
                }
            }
            insets
        }

        // Android 11+ يتيح مزامنة composer مع انزلاق الكيبورد دون إعادة layout للجذر كل frame.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ViewCompat.setWindowInsetsAnimationCallback(
                content,
                object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                    override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                        if (!isImeAnimation(animation)) return
                        state.animationRunning = true
                        state.animationStartRenderedTop = renderedTopOnScreen(composer)
                    }

                    override fun onStart(
                        animation: WindowInsetsAnimationCompat,
                        bounds: WindowInsetsAnimationCompat.BoundsCompat
                    ): WindowInsetsAnimationCompat.BoundsCompat {
                        if (!isImeAnimation(animation)) return bounds
                        val finalInsets = state.pendingInsets ?: ViewCompat.getRootWindowInsets(content)
                        val baseTop = baseTopOnScreen(composer)
                        val renderedStart = state.animationStartRenderedTop ?: renderedTopOnScreen(composer)
                        val targetOverlap = finalInsets?.let {
                            calculateImeOverlap(activity, composer, it)
                        } ?: 0

                        state.animationStartTranslation = renderedStart - baseTop
                        state.animationTargetTranslation = -targetOverlap.toFloat()
                        state.lastTargetOverlap = targetOverlap
                        updateMessageListPadding(messageList, state, targetOverlap)
                        composer.translationY = state.animationStartTranslation
                        return bounds
                    }

                    override fun onProgress(
                        insets: WindowInsetsCompat,
                        runningAnimations: MutableList<WindowInsetsAnimationCompat>
                    ): WindowInsetsCompat {
                        val imeAnimation = runningAnimations.firstOrNull(::isImeAnimation)
                            ?: return insets
                        val fraction = imeAnimation.interpolatedFraction.coerceIn(0f, 1f)
                        composer.translationY = state.animationStartTranslation +
                            (state.animationTargetTranslation - state.animationStartTranslation) * fraction
                        return insets
                    }

                    override fun onEnd(animation: WindowInsetsAnimationCompat) {
                        if (!isImeAnimation(animation)) return
                        state.animationRunning = false
                        state.animationStartRenderedTop = null
                        val finalInsets = state.pendingInsets ?: ViewCompat.getRootWindowInsets(content)
                        if (finalInsets != null) {
                            settle(activity, composer, messageList, state, finalInsets)
                        } else {
                            composer.translationY = state.animationTargetTranslation
                        }
                    }
                }
            )
        }

        ViewCompat.requestApplyInsets(content)
    }

    private fun settle(
        activity: Activity,
        composer: View,
        messageList: ListView,
        state: UiState,
        insets: WindowInsetsCompat
    ) {
        if (!ViewCompat.isLaidOut(composer) || composer.height <= 0) return
        val overlap = calculateImeOverlap(activity, composer, insets)
        state.lastTargetOverlap = overlap
        state.animationTargetTranslation = -overlap.toFloat()
        updateMessageListPadding(messageList, state, overlap)
        val target = -overlap.toFloat()
        if (composer.translationY != target) composer.translationY = target
    }

    /**
     * يعيد فقط مقدار التداخل الحقيقي بين أسفل composer وأعلى IME.
     * إذا نجح adjustResize أصلًا يكون الناتج صفرًا؛ وهكذا لا يحدث رفع مزدوج.
     */
    private fun calculateImeOverlap(
        activity: Activity,
        composer: View,
        insets: WindowInsetsCompat
    ): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return 0
        if (!insets.isVisible(WindowInsetsCompat.Type.ime())) return 0
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        if (ime.bottom <= 0) return 0

        val windowBottom = activity.windowManager.currentWindowMetrics.bounds.bottom
        val keyboardTop = windowBottom - ime.bottom
        val composerBaseBottom = baseTopOnScreen(composer) + composer.height
        val safetyGap = HakimChatUi.dp(activity, 4f)
        return (composerBaseBottom + safetyGap - keyboardTop)
            .roundToInt()
            .coerceAtLeast(0)
    }

    private fun updateMessageListPadding(messageList: ListView, state: UiState, overlap: Int) {
        val left = state.baseListPadding[0]
        val top = state.baseListPadding[1]
        val right = state.baseListPadding[2]
        val bottom = state.baseListPadding[3] + overlap.coerceAtLeast(0)
        if (messageList.paddingLeft != left || messageList.paddingTop != top ||
            messageList.paddingRight != right || messageList.paddingBottom != bottom
        ) {
            messageList.setPadding(left, top, right, bottom)
        }
    }

    private fun renderedTopOnScreen(view: View): Float {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return location[1].toFloat()
    }

    private fun baseTopOnScreen(view: View): Float = renderedTopOnScreen(view) - view.translationY

    private fun findFirstEditText(view: View): EditText? {
        if (view is EditText) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findFirstEditText(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun findFirstListView(view: View): ListView? {
        if (view is ListView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findFirstListView(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun isImeAnimation(animation: WindowInsetsAnimationCompat): Boolean =
        animation.typeMask and WindowInsetsCompat.Type.ime() != 0

    fun status(): Map<String, Any> = linkedMapOf(
        "manifest_adjust_resize_required" to true,
        "android_15_plus_system_insets_handled" to true,
        "ime_overlap_measured_against_window_bounds" to true,
        "ime_overlap_docked_with_translation" to true,
        "window_insets_animation_synchronized" to true,
        "full_ime_height_root_padding_forbidden" to true,
        "composer_must_remain_visible_with_keyboard" to true,
        "message_list_scroll_space_tracks_only_actual_overlap" to true,
        "legacy_android_relies_on_adjust_resize" to true,
        "typing_preempts_proactive_resume" to true,
        "double_lift_prevented_when_adjust_resize_already_works" to true
    )
}
