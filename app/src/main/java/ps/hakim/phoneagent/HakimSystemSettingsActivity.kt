package ps.hakim.phoneagent

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** إعداد النظام الحاكم والبيانات المتكررة؛ لا يقبل كلمات المرور أو رموز التحقق أو البطاقات. */
class HakimSystemSettingsActivity : Activity() {
    private lateinit var globalInstructions: EditText
    private lateinit var siteHost: EditText
    private lateinit var siteInstructions: EditText
    private lateinit var trustSiteForProfile: CheckBox
    private lateinit var shareWithReasoning: CheckBox
    private val profileInputs = linkedMapOf<String, EditText>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        load()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(22, 24, 22, 28)
        }
        val scroll = ScrollView(this).apply { addView(root) }

        root.addView(title("النظام الحاكم والبيانات — حكيم"))
        root.addView(note("اكتب قواعدك مرة واحدة. تُحفظ محليًا ومشفرة. لا تضع كلمات مرور أو رموز تحقق أو بطاقات هنا؛ هذه تبقى لدى مدير اعتماد أندرويد أو الموقع نفسه."))

        root.addView(section("النظام الحاكم العام"))
        globalInstructions = EditText(this).apply {
            hint = "قواعدك وتفضيلاتك الدائمة…"
            minLines = 6
            maxLines = 16
            gravity = Gravity.TOP or Gravity.RIGHT
            textDirection = View.TEXT_DIRECTION_RTL
        }
        root.addView(globalInstructions)

        root.addView(section("تعليمات خاصة بالموقع"))
        siteHost = EditText(this).apply {
            hint = "مثال: chatgpt.com"
            setSingleLine(true)
            textDirection = View.TEXT_DIRECTION_LTR
        }
        root.addView(siteHost)
        siteInstructions = EditText(this).apply {
            hint = "قواعد تطبق فقط عند العمل على هذا الموقع"
            minLines = 3
            maxLines = 10
            gravity = Gravity.TOP or Gravity.RIGHT
        }
        root.addView(siteInstructions)

        trustSiteForProfile = CheckBox(this).apply {
            text = "أثق بهذا الموقع لاستخدام بيانات التعبئة الشخصية داخل متصفح حكيم"
            textSize = 15f
        }
        root.addView(trustSiteForProfile)
        root.addView(note("حكيم لا يملأ الاسم أو البريد أو الهاتف من خزنته في موقع غير معتمد، حتى لو كان الحقل يبدو صحيحًا."))

        root.addView(section("بيانات متكررة للتعبئة المحلية"))
        HakimPersonalVault.fields.forEach { field ->
            root.addView(TextView(this).apply {
                text = field.title
                textSize = 15f
                gravity = Gravity.RIGHT
                setPadding(4, 10, 4, 2)
            })
            val edit = EditText(this).apply {
                hint = field.title
                setSingleLine(true)
                inputType = when (field.id) {
                    "email" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    "phone" -> InputType.TYPE_CLASS_PHONE
                    else -> InputType.TYPE_CLASS_TEXT
                }
            }
            profileInputs[field.id] = edit
            root.addView(edit)
        }

        shareWithReasoning = CheckBox(this).apply {
            text = "السماح لمحرك الاستدلال برؤية هذه البيانات غير الحساسة عند الحاجة"
            textSize = 15f
        }
        root.addView(shareWithReasoning)
        root.addView(note("الافتراضي أكثر خصوصية: حكيم يملأ القيم محليًا دون إرسالها إلى نموذج الذكاء. تفعيل الخيار أعلاه مفيد فقط عندما يحتاج النموذج القيم نفسها لاتخاذ القرار."))

        root.addView(Button(this).apply {
            text = "حفظ واعتماد"
            textSize = 18f
            setOnClickListener { save() }
        })

        setContentView(scroll)
    }

    private fun load() {
        globalInstructions.setText(HakimGovernanceStore.global(this))
        val currentHost = HakimGovernanceStore.currentHost(this)
        if (currentHost.isNotBlank()) {
            siteHost.setText(currentHost)
            siteInstructions.setText(HakimGovernanceStore.site(this, currentHost))
            trustSiteForProfile.isChecked = HakimSiteTrust.isTrusted(this, currentHost)
        }
        HakimPersonalVault.fields.forEach { field ->
            profileInputs[field.id]?.setText(HakimPersonalVault.get(this, field.id).orEmpty())
        }
        shareWithReasoning.isChecked = HakimPersonalVault.reasoningSharingEnabled(this)
    }

    private fun save() {
        val globalOk = HakimGovernanceStore.setGlobal(this, globalInstructions.text.toString())
        val host = siteHost.text.toString().trim()
        val siteOk = if (host.isBlank() && siteInstructions.text.toString().isBlank()) true
        else HakimGovernanceStore.setSite(this, host, siteInstructions.text.toString())
        val trustOk = if (host.isBlank()) !trustSiteForProfile.isChecked
        else HakimSiteTrust.setTrusted(this, host, trustSiteForProfile.isChecked)

        var profileOk = true
        profileInputs.forEach { (id, edit) ->
            if (!HakimPersonalVault.save(this, id, edit.text.toString())) profileOk = false
        }
        HakimPersonalVault.setReasoningSharing(this, shareWithReasoning.isChecked)

        if (globalOk && siteOk && trustOk && profileOk) {
            Toast.makeText(this, "تم حفظ النظام والبيانات والثقة بالموقع محليًا", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "لم يُحفظ أحد الحقول لأنه حساس أو غير صالح. لم تُخزَّن القيمة المحظورة.", Toast.LENGTH_LONG).show()
        }
    }

    private fun title(text: String) = TextView(this).apply {
        this.text = text
        textSize = 24f
        gravity = Gravity.CENTER
        setPadding(4, 4, 4, 14)
    }

    private fun section(text: String) = TextView(this).apply {
        this.text = text
        textSize = 19f
        gravity = Gravity.RIGHT
        setPadding(4, 18, 4, 6)
    }

    private fun note(text: String) = TextView(this).apply {
        this.text = text
        textSize = 14f
        gravity = Gravity.RIGHT
        setPadding(4, 4, 4, 10)
    }
}
