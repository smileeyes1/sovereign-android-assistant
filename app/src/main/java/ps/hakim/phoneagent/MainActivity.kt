package ps.hakim.phoneagent

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("hakim", MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(48, 70, 48, 48)
        }
        root.addView(TextView(this).apply {
            text = "حكيم الهاتف"; textSize = 28f; gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "الصق رمز الاقتران مرة واحدة، احفظه، ثم فعّل خدمة «حكيم الهاتف». بعدها تصبح الأوامر من محادثتك مع ChatGPT."
            textSize = 18f; gravity = Gravity.RIGHT; setPadding(0, 28, 0, 18)
        })

        val pair = EditText(this).apply {
            hint = "رمز الاقتران"
            textSize = 17f
            setSingleLine(true)
        }
        root.addView(pair)

        val save = Button(this).apply {
            text = "حفظ الاقتران"
            textSize = 18f
        }
        val status = TextView(this).apply {
            textSize = 17f; gravity = Gravity.CENTER; setPadding(0, 16, 0, 20)
        }
        fun refresh() {
            val paired = prefs.getString("command_topic", "").orEmpty().isNotBlank() &&
                         prefs.getString("result_topic", "").orEmpty().isNotBlank()
            status.text = when {
                AgentAccessibilityService.instance != null && paired -> "الحالة: متصل وجاهز"
                paired -> "الحالة: تم الاقتران — فعّل خدمة الوصول"
                else -> "الحالة: يحتاج رمز الاقتران"
            }
        }
        save.setOnClickListener {
            val parts = pair.text.toString().trim().split("|")
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                prefs.edit().putString("command_topic", parts[0]).putString("result_topic", parts[1]).apply()
                pair.setText("")
                Toast.makeText(this, "تم حفظ الاقتران", Toast.LENGTH_SHORT).show()
                AgentAccessibilityService.instance?.reconnectNow()
                refresh()
            } else Toast.makeText(this, "رمز الاقتران غير صحيح", Toast.LENGTH_SHORT).show()
        }
        root.addView(save)
        root.addView(status)

        root.addView(Button(this).apply {
            text = "فتح إعدادات خدمة الوصول"; textSize = 18f
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        root.addView(Button(this).apply {
            text = "فتح المتصفح للاختبار"; textSize = 18f
            setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com"))) }
        })
        setContentView(root)
        refresh()
    }
}
