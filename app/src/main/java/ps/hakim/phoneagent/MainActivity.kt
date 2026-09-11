package ps.hakim.phoneagent

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(48, 80, 48, 48)
        }

        val title = TextView(this).apply {
            text = "حكيم الهاتف"
            textSize = 28f
            gravity = Gravity.CENTER
        }
        val info = TextView(this).apply {
            text = "بعد التثبيت، فعّل خدمة «حكيم الهاتف» مرة واحدة. بعدها يمكن تنفيذ أوامر المتصفح من محادثتك مع ChatGPT."
            textSize = 18f
            gravity = Gravity.RIGHT
            setPadding(0, 32, 0, 32)
        }
        val status = TextView(this).apply {
            text = if (AgentAccessibilityService.instance != null) "الحالة: متصل وجاهز" else "الحالة: يحتاج تفعيل خدمة الوصول"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 28)
        }
        val button = Button(this).apply {
            text = "فتح إعدادات خدمة الوصول"
            textSize = 18f
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        val test = Button(this).apply {
            text = "فتح المتصفح للاختبار"
            textSize = 18f
            setOnClickListener {
                val i = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com"))
                startActivity(i)
            }
        }
        root.addView(title)
        root.addView(info)
        root.addView(status)
        root.addView(button)
        root.addView(test)
        setContentView(root)
    }
}
