package ps.hakim.phoneagent

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

object HakimUiKit {
    const val VERSION = "UI-KIT-2026-09-24-v1"

    private const val TEXT = 0xFF171717.toInt()
    private const val MUTED = 0xFF666666.toInt()
    private const val SURFACE = 0xFFF5F5F3.toInt()
    private const val PRIMARY = 0xFF171717.toInt()
    private const val PRIMARY_TEXT = 0xFFFFFFFF.toInt()

    private fun round(fill: Int, stroke: Int? = null, radius: Float = 22f): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = radius
            if (stroke != null) setStroke(1, stroke)
        }

    fun title(view: TextView) {
        view.setTextColor(TEXT)
        view.textSize = 28f
    }

    fun status(view: TextView) {
        view.setTextColor(MUTED)
        view.textSize = 14f
    }

    fun conversation(view: TextView) {
        view.setTextColor(TEXT)
        view.textSize = 18f
        view.background = round(Color.WHITE, 0xFFE8E8E5.toInt(), 24f)
        view.setPadding(22, 20, 22, 20)
    }

    fun composer(view: EditText) {
        view.setTextColor(TEXT)
        view.setHintTextColor(0xFF8A8A8A.toInt())
        view.background = round(Color.WHITE, 0xFFDADAD6.toInt(), 26f)
        view.setPadding(20, 16, 20, 16)
    }

    fun primary(button: Button) {
        button.setTextColor(PRIMARY_TEXT)
        button.background = round(PRIMARY, null, 24f)
        button.setAllCaps(false)
        button.minHeight = 52
        button.setPadding(18, 12, 18, 12)
    }

    fun secondary(button: Button) {
        button.setTextColor(TEXT)
        button.background = round(SURFACE, 0xFFE1E1DD.toInt(), 24f)
        button.setAllCaps(false)
        button.minHeight = 48
        button.setPadding(16, 10, 16, 10)
    }

    fun card(view: View) {
        view.background = round(Color.WHITE, 0xFFE8E8E5.toInt(), 24f)
    }
}
