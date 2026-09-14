package ps.hakim.phoneagent

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.TextView

/**
 * طبقة عرض خفيفة للمحادثة: لا مكتبات UI إضافية، ولا قائمة نصية تكبر بلا حد.
 * تستخدم إعادة تدوير Views من Android، وتقلل عدد الرسائل المحتفظ بها حسب ضغط الموارد.
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
                background = Color.rgb(32, 33, 35),
                surface = Color.rgb(45, 46, 49),
                surfaceStrong = Color.rgb(55, 56, 60),
                text = Color.rgb(244, 244, 245),
                muted = Color.rgb(181, 181, 187),
                accent = Color.rgb(27, 140, 101),
                onAccent = Color.WHITE,
                userBubble = Color.rgb(47, 75, 65),
                assistantBubble = Color.rgb(45, 46, 49),
                border = Color.rgb(72, 73, 77),
                danger = Color.rgb(196, 76, 76)
            )
        } else {
            Palette(
                background = Color.rgb(255, 255, 255),
                surface = Color.rgb(247, 247, 248),
                surfaceStrong = Color.rgb(239, 239, 241),
                text = Color.rgb(32, 33, 35),
                muted = Color.rgb(104, 104, 116),
                accent = Color.rgb(18, 138, 102),
                onAccent = Color.WHITE,
                userBubble = Color.rgb(232, 247, 241),
                assistantBubble = Color.rgb(247, 247, 248),
                border = Color.rgb(224, 224, 227),
                danger = Color.rgb(184, 62, 62)
            )
        }
    }

    fun rounded(fill: Int, radiusDp: Float, context: Context, stroke: Int? = null): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(context, radiusDp).toFloat()
            if (stroke != null) setStroke(dp(context, 1f), stroke)
        }
    }

    fun dp(context: Context, value: Float): Int = (value * context.resources.displayMetrics.density + 0.5f).toInt()
}

class HakimChatMessageAdapter(private val context: Context) : BaseAdapter() {
    enum class Role { USER, ASSISTANT }
    data class Message(val id: Long, val role: Role, val text: String)

    private val palette = HakimChatUi.palette(context)
    private val items = ArrayList<Message>(64)
    private var nextId = 1L
    private var maxMessages = 120

    fun append(role: Role, text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        updateResourceBudget()
        items.add(Message(nextId++, role, clean.take(12000)))
        trimToBudget()
        notifyDataSetChanged()
    }

    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }

    private fun updateResourceBudget() {
        maxMessages = when (HakimResourceGovernor.snapshot(context).mode) {
            HakimResourceGovernor.Mode.PRESSURE -> 40
            HakimResourceGovernor.Mode.CONSERVE -> 70
            HakimResourceGovernor.Mode.BALANCED -> 110
            HakimResourceGovernor.Mode.PERFORMANCE -> 160
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
        val screenWidth = context.resources.displayMetrics.widthPixels
        val bubble = TextView(context).apply {
            textSize = 16.5f
            setTextColor(palette.text)
            textDirection = View.TEXT_DIRECTION_RTL
            gravity = Gravity.START
            includeFontPadding = false
            setLineSpacing(0f, 1.12f)
            maxWidth = (screenWidth * if (role == Role.USER) 0.84f else 0.94f).toInt()
            setPadding(
                HakimChatUi.dp(context, 14f),
                HakimChatUi.dp(context, 10f),
                HakimChatUi.dp(context, 14f),
                HakimChatUi.dp(context, 10f)
            )
            background = HakimChatUi.rounded(
                if (role == Role.USER) palette.userBubble else palette.assistantBubble,
                if (role == Role.USER) 18f else 14f,
                context,
                if (role == Role.USER) null else palette.border
            )
            if (role == Role.ASSISTANT) setTypeface(typeface, Typeface.NORMAL)
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (role == Role.USER) Gravity.END else Gravity.START
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(
                HakimChatUi.dp(context, 12f),
                HakimChatUi.dp(context, 5f),
                HakimChatUi.dp(context, 12f),
                HakimChatUi.dp(context, 5f)
            )
            addView(bubble, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }
}
