package ps.hakim.phoneagent

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.TextView

/**
 * طبقة عرض خفيفة للمحادثة: لا مكتبات UI إضافية ولا transcript يتضخم بلا حد.
 * رد حكيم نص نظيف، ورسالة المستخدم فقاعة خفيفة، مع ميزانية ذاكرة تكيفية حسب ضغط الهاتف.
 */
object HakimChatUi {
    data class Palette(
        val background: Int,
        val surface: Int,
        val surfaceStrong: Int,
        val text: Int,
        val muted: Int,
        val accent: Int,
        val onAccent: Int,
        val userBubble: Int,
        val assistantBubble: Int,
        val border: Int,
        val danger: Int
    )

    fun palette(context: Context): Palette {
        val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        return if (dark) {
            Palette(
                background = Color.rgb(24, 24, 27),
                surface = Color.rgb(38, 38, 42),
                surfaceStrong = Color.rgb(49, 49, 54),
                text = Color.rgb(247, 247, 248),
                muted = Color.rgb(164, 164, 174),
                accent = Color.rgb(20, 148, 111),
                onAccent = Color.WHITE,
                userBubble = Color.rgb(45, 64, 58),
                assistantBubble = Color.TRANSPARENT,
                border = Color.rgb(61, 61, 67),
                danger = Color.rgb(203, 72, 72)
            )
        } else {
            Palette(
                background = Color.rgb(255, 255, 255),
                surface = Color.rgb(246, 246, 247),
                surfaceStrong = Color.rgb(235, 235, 237),
                text = Color.rgb(25, 25, 28),
                muted = Color.rgb(112, 112, 122),
                accent = Color.rgb(16, 143, 103),
                onAccent = Color.WHITE,
                userBubble = Color.rgb(238, 246, 243),
                assistantBubble = Color.TRANSPARENT,
                border = Color.rgb(224, 224, 227),
                danger = Color.rgb(190, 58, 58)
            )
        }
    }

    fun rounded(fill: Int, radiusDp: Float, context: Context, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(context, radiusDp).toFloat()
            if (stroke != null) setStroke(dp(context, 1f), stroke)
        }

    fun dp(context: Context, value: Float): Int = (value * context.resources.displayMetrics.density + 0.5f).toInt()
}

class HakimChatMessageAdapter(private val context: Context) : BaseAdapter() {
    enum class Role { USER, ASSISTANT }
    data class Message(val id: Long, val role: Role, val text: String)

    private val palette = HakimChatUi.palette(context)
    private val items = ArrayList<Message>(48)
    private var nextId = 1L
    private var maxMessages = 90

    fun append(role: Role, text: String) {
        val clean = humanFacing(role, text).trim()
        if (clean.isBlank()) return
        if (role == Role.ASSISTANT && items.lastOrNull()?.role == role && items.lastOrNull()?.text == clean) return
        updateResourceBudget()
        items.add(Message(nextId++, role, clean.take(16000)))
        trimToBudget()
        notifyDataSetChanged()
    }

    private fun humanFacing(role: Role, raw: String): String {
        if (role == Role.USER) return raw
        val text = raw.trim()
        if (text.startsWith("أنا حكيم. اكتب أو تحدث بطريقتك الطبيعية")) {
            return "أنا حكيم. اكتب ما تريد إنجازه، وسأتولى الباقي ضمن حدودك. يمكنك قول «توقف» في أي وقت."
        }
        if (text.startsWith("حكيم يفكر عبر") || text.startsWith("أرسلت المهمة المحكومة إلى") ||
            text.startsWith("حكيم يفكر عبر Gemini")) {
            return "أعمل على أفضل مسار للمهمة…"
        }
        return text.lineSequence()
            .filterNot { line ->
                val t = line.trim()
                t.startsWith("الثقة:") || t.startsWith("الوكلاء:") || t.startsWith("المسار:") ||
                    t.startsWith("قرار المصفوفة:") || t.startsWith("درجة فهم المقصد:")
            }
            .joinToString("\n")
            .trim()
    }

    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }

    fun lastAssistantText(): String = items.lastOrNull { it.role == Role.ASSISTANT }?.text.orEmpty()

    private fun updateResourceBudget() {
        maxMessages = when (HakimResourceGovernor.snapshot(context).mode) {
            HakimResourceGovernor.Mode.PRESSURE -> 30
            HakimResourceGovernor.Mode.CONSERVE -> 55
            HakimResourceGovernor.Mode.BALANCED -> 90
            HakimResourceGovernor.Mode.PERFORMANCE -> 130
        }
    }

    private fun trimToBudget() {
        val overflow = items.size - maxMessages
        if (overflow <= 0) return
        repeat(overflow) { if (items.isNotEmpty()) items.removeAt(0) }
    }

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): Message = items[position]
    override fun getItemId(position: Int): Long = items[position].id
    override fun hasStableIds(): Boolean = true
    override fun getViewTypeCount(): Int = 2
    override fun getItemViewType(position: Int): Int = if (items[position].role == Role.USER) 0 else 1

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val role = items[position].role
        val row = (convertView as? LinearLayout) ?: createRow(role)
        val bubble = row.getChildAt(0) as TextView
        bubble.text = items[position].text
        return row
    }

    private fun createRow(role: Role): LinearLayout {
        val user = role == Role.USER
        val screenWidth = context.resources.displayMetrics.widthPixels
        val bubble = TextView(context).apply {
            textSize = if (user) 16.5f else 17f
            setTextColor(palette.text)
            textDirection = View.TEXT_DIRECTION_RTL
            gravity = Gravity.START
            includeFontPadding = false
            setLineSpacing(HakimChatUi.dp(context, 1.5f).toFloat(), 1.13f)
            maxWidth = (screenWidth * if (user) 0.82f else 0.94f).toInt()
            setPadding(
                HakimChatUi.dp(context, if (user) 14f else 5f),
                HakimChatUi.dp(context, if (user) 10f else 7f),
                HakimChatUi.dp(context, if (user) 14f else 5f),
                HakimChatUi.dp(context, if (user) 10f else 7f)
            )
            if (user) {
                background = HakimChatUi.rounded(palette.userBubble, 20f, context)
            } else {
                background = null
                setTextIsSelectable(true)
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (user) Gravity.END else Gravity.START
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(
                HakimChatUi.dp(context, 14f),
                HakimChatUi.dp(context, if (user) 4f else 7f),
                HakimChatUi.dp(context, 14f),
                HakimChatUi.dp(context, if (user) 4f else 7f)
            )
            addView(bubble, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }
}
