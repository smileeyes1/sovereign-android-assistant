package ps.hakim.phoneagent

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
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

/** إعداد النظام الحاكم وبيانات التعبئة الشخصية؛ لا يقبل كلمات المرور أو رموز التحقق أو البطاقات. */
class HakimSystemSettingsActivity : Activity() {
    private lateinit var globalInstructions: EditText
    private lateinit var siteHost: EditText
    private lateinit var siteInstructions: EditText
    private lateinit var trustSiteForProfile: CheckBox
    private lateinit var shareWithReasoning: CheckBox
    private lateinit var proactiveEnabled: CheckBox
    private lateinit var quranCorpusStatus: TextView
    private val profileInputs = linkedMapOf<String, EditText>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        load()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQ_EXPORT -> runCatching {
                val bytes = HakimSovereignPortability.exportJson(this).toByteArray(Charsets.UTF_8)
                contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
                    ?: error("تعذر فتح وجهة الحفظ")
            }.onSuccess {
                Toast.makeText(
                    this,
                    "تم حفظ النسخة السيادية في المكان الذي اخترته. الأسرار مستبعدة، لكن النسخة قد تتضمن بياناتك الشخصية المحفوظة؛ احفظها في وجهة موثوقة.",
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure {
                HakimFaultLedger.record(this, "sovereign_export", it, severity = HakimFaultLedger.Severity.WARNING)
                Toast.makeText(this, "تعذر حفظ النسخة السيادية: ${it.message.orEmpty().take(160)}", Toast.LENGTH_LONG).show()
            }

            REQ_IMPORT -> runCatching {
                contentResolver.openInputStream(uri)?.use { input ->
                    val dataBytes = input.readBytes()
                    if (dataBytes.size > 512 * 1024) error("الملف أكبر من الحد الآمن")
                    String(dataBytes, Charsets.UTF_8)
                } ?: error("تعذر قراءة الملف")
            }.onSuccess { raw ->
                AlertDialog.Builder(this)
                    .setTitle("استعادة نسخة حكيم السيادية")
                    .setMessage("سيتم استبدال النظام الحاكم وتعليمات المواقع وبيانات التعبئة الشخصية المحفوظة والثقة والمبادرة بما في هذه النسخة. لا تُستورد كلمات مرور أو رموز تحقق أو مفاتيح توقيع. هل تريد المتابعة؟")
                    .setPositiveButton("استعادة") { _, _ ->
                        val result = HakimSovereignPortability.importJson(this, raw, confirmed = true)
                        if (result.success) {
                            load()
                            Toast.makeText(this, "${result.message} (${result.changed} عنصرًا/إعدادًا)", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                        }
                    }
                    .setNegativeButton("إلغاء", null)
                    .show()
            }.onFailure {
                HakimFaultLedger.record(this, "sovereign_import", it, severity = HakimFaultLedger.Severity.WARNING)
                Toast.makeText(this, "تعذر قراءة النسخة: ${it.message.orEmpty().take(160)}", Toast.LENGTH_LONG).show()
            }

            REQ_QURAN_IMPORT -> importVerifiedQuran(uri)
            REQ_QURAN_EXPORT -> exportVerifiedQuran(uri)
            REQ_QURAN_RESTORE -> restoreVerifiedQuran(uri)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(22, 24, 22, 28)
        }
        val scroll = ScrollView(this).apply { addView(root) }

        root.addView(title("النظام الحاكم والبيانات — حكيم"))
        root.addView(note("حكيم يبدأ بنواة حاكمة مكتملة تلقائيًا، لا بخانة فارغة. يمكنك تخصيصها، والفراغ يعني الرجوع إلى النواة الافتراضية لا إزالة الحاكمية. لا تضع كلمات مرور أو رموز تحقق أو بطاقات هنا."))

        root.addView(section("الاختبار الميداني ومنع الأخطاء الصامتة"))
        root.addView(note("هذا الاختبار يعمل على الهاتف نفسه ويفرق بين سلامة الكود وبين الجاهزية الفعلية. لا يعلن FIELD_VERIFIED تلقائيًا؛ بل يعطي PASS/PARTIAL/BLOCKED بالدليل."))
        root.addView(Button(this).apply {
            text = "تشغيل الاختبار الميداني الشامل"
            textSize = 16f
            setOnClickListener { runFieldValidation() }
        })

        root.addView(section("القرآن المحلي المتحقق — حفص"))
        quranCorpusStatus = note("")
        root.addView(quranCorpusStatus)
        root.addView(note("تغطية السور الـ١١٤ في الحاكمية لا تعني تلقائيًا أن نص كل آية مخزن محليًا. حكيم لا يعتمد النص المحلي إلا بعد مطابقة بصمة ملف رسمي منشور من مجمع الملك فهد وفحص ١١٤ سورة و٦٢٣٦ آية."))
        val quranRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        quranRow.addView(Button(this).apply {
            text = "فتح المصدر الرسمي"
            textSize = 15f
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(HakimVerifiedQuranCorpus.OFFICIAL_SOURCE_PAGE)))
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        quranRow.addView(Button(this).apply {
            text = "اعتماد الملف الرسمي"
            textSize = 15f
            setOnClickListener { chooseOfficialQuranArchive() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(quranRow)

        val quranPortabilityRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        quranPortabilityRow.addView(Button(this).apply {
            text = "تصدير المصدر الموثق"
            textSize = 15f
            setOnClickListener { exportVerifiedQuranArchive() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        quranPortabilityRow.addView(Button(this).apply {
            text = "استعادة المصدر الموثق"
            textSize = 15f
            setOnClickListener { restoreVerifiedQuranArchive() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(quranPortabilityRow)
        root.addView(note("بعد اعتماد المصدر الرسمي يحتفظ حكيم بنسخته الأصلية محليًا. يمكنك تصديرها لنسخة احتياطية خاصة بك ثم استعادتها دون شبكة؛ حكيم يعيد فحص البصمة الرسمية والسور والآيات قبل الاعتماد، ولا يصدّر أسرارًا أو بيانات حسابات."))

        root.addView(section("الاستقلال السيادي والمبادرة"))
        root.addView(note("الحالة: ${if (HakimSovereignIndependence.isCoreSovereign(this)) "القلب السيادي مستقل عن مزود خارجي منفرد" else "توجد فجوة استقلال بنيوية تحتاج إصلاحًا"}. الخدمات الخارجية قدرات قابلة للاستبدال وليست حاكمًا."))
        proactiveEnabled = CheckBox(this).apply {
            text = "المبادرة الذاتية المفيدة — ينفذ حكيم تلقائيًا كل عمل آمن ومفيد لا يحتاج موافقة جديدة"
            textSize = 16f
        }
        root.addView(proactiveEnabled)
        root.addView(note("مفعلة افتراضيًا. تشمل الفحص والتعلم والتحسين والتعافي واستعادة الاتصال وفحص التحديثات واستئناف المهمة الآمنة. المال والحذف النهائي والإرسال الحساس والأسرار والصلاحيات الكبيرة تبقى عند بوابة موافقتك."))

        val portabilityRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        portabilityRow.addView(Button(this).apply {
            text = "تصدير نسخة سيادية"
            textSize = 15f
            setOnClickListener { exportSovereignBackup() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        portabilityRow.addView(Button(this).apply {
            text = "استعادة نسخة سيادية"
            textSize = 15f
            setOnClickListener { importSovereignBackup() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(portabilityRow)
        root.addView(note("النسخة السيادية تنقل النواة المخصصة وتعليمات المواقع وبيانات التعبئة الشخصية التي حفظتها والثقة والتفضيلات. كلمات المرور ورموز التحقق والبطاقات ومفتاح توقيع التطبيق مستبعدة عمدًا؛ ملف النسخة نفسه يحتاج مكان حفظ موثوقًا."))

        root.addView(section("النظام الحاكم العام — مفعّل افتراضيًا"))
        globalInstructions = EditText(this).apply {
            hint = "نواة حكيم الحاكمة ستظهر هنا تلقائيًا"
            minLines = 10
            maxLines = 24
            gravity = Gravity.TOP or Gravity.RIGHT
            textDirection = View.TEXT_DIRECTION_RTL
        }
        root.addView(globalInstructions)
        root.addView(Button(this).apply {
            text = "استعادة نواة حكيم الأعلى"
            textSize = 16f
            setOnClickListener {
                globalInstructions.setText(HakimGovernanceStore.DEFAULT_GLOBAL_INSTRUCTIONS)
                Toast.makeText(this@HakimSystemSettingsActivity, "تمت استعادة نواة حكيم الافتراضية في الحقل — اضغط حفظ واعتماد لتثبيت تخصيصك إن أردت", Toast.LENGTH_LONG).show()
            }
        })
        root.addView(note("المسار الحاكم المدمج: و؟ → و؟ → و؟ → لِمَ؟ → و؟ → و؟ → اعتمد → أصلح → أكمل → هَيّا. وهو يعمل مع المقصد والدليل والإنسان أولًا والاستقلال والتعلم والتعافي ومنع التخمين والانحدار."))

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

        root.addView(section("بيانات شخصية متكررة للتعبئة المحلية"))
        root.addView(note("هذه البيانات قد تشمل الاسم والبريد والهاتف والعنوان وجهة العمل. ليست كلمات مرور أو رموز تحقق، لكنها بيانات شخصية وتبقى محلية افتراضيًا."))
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
            text = "السماح لمحرك الاستدلال برؤية بيانات التعبئة الشخصية عند الحاجة"
            textSize = 15f
        }
        root.addView(shareWithReasoning)
        root.addView(note("الافتراضي أكثر خصوصية: حكيم يملأ القيم محليًا دون إرسالها إلى نموذج الذكاء. فعّل المشاركة فقط عندما تريد أن يرى محرك الاستدلال القيم نفسها لمهمة تحتاجها."))

        root.addView(Button(this).apply {
            text = "حفظ واعتماد"
            textSize = 18f
            setOnClickListener { save() }
        })

        setContentView(scroll)
    }

    private fun runFieldValidation() {
        Toast.makeText(this, "يجري حكيم الاختبار الميداني غير الهدّام…", Toast.LENGTH_SHORT).show()
        Thread {
            val report = runCatching { HakimFieldValidation.run(this) }.getOrElse {
                HakimFaultLedger.record(this, "field_validation_ui", it, severity = HakimFaultLedger.Severity.CRITICAL)
                org.json.JSONObject().put("overall", "BLOCKED").put("blocked", 1).put("partial", 0)
            }
            runOnUiThread {
                val overall = report.optString("overall", "BLOCKED")
                val checks = report.optJSONArray("checks")
                val lines = ArrayList<String>()
                if (checks != null) for (i in 0 until checks.length()) {
                    val c = checks.optJSONObject(i) ?: continue
                    lines += "${c.optString("level")}: ${c.optString("name")} — ${c.optString("detail")}"
                }
                AlertDialog.Builder(this)
                    .setTitle("نتيجة الاختبار الميداني: $overall")
                    .setMessage((lines.joinToString("\n\n") + "\n\nFIELD_VERIFIED لا يُعلن حتى تنجح سيناريوهات فعلية كاملة على الهاتف.").take(14000))
                    .setPositiveButton("حسنًا", null)
                    .show()
            }
        }.start()
    }

    private fun chooseOfficialQuranArchive() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, REQ_QURAN_IMPORT)
    }

    private fun importVerifiedQuran(uri: Uri) {
        Toast.makeText(this, "يتحقق حكيم من البصمة الرسمية وبنية القرآن…", Toast.LENGTH_LONG).show()
        Thread {
            val result = HakimVerifiedQuranCorpus.importOfficialArchive(this, uri)
            runOnUiThread {
                refreshQuranCorpusStatus()
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun exportVerifiedQuranArchive() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, "Hakim-Quran-Verified-Source.zip")
        }
        startActivityForResult(intent, REQ_QURAN_EXPORT)
    }

    private fun exportVerifiedQuran(uri: Uri) {
        Toast.makeText(this, "يعيد حكيم فحص بصمة المصدر قبل التصدير…", Toast.LENGTH_SHORT).show()
        Thread {
            val result = HakimVerifiedQuranCorpus.exportPreservedOfficialArchive(this, uri)
            runOnUiThread { Toast.makeText(this, result.message, Toast.LENGTH_LONG).show() }
        }.start()
    }

    private fun restoreVerifiedQuranArchive() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
        }
        startActivityForResult(intent, REQ_QURAN_RESTORE)
    }

    private fun restoreVerifiedQuran(uri: Uri) {
        Toast.makeText(this, "يتحقق حكيم من المصدر كاملًا قبل الاستعادة…", Toast.LENGTH_LONG).show()
        Thread {
            val result = HakimVerifiedQuranCorpus.restorePreservedOfficialArchive(this, uri)
            runOnUiThread {
                refreshQuranCorpusStatus()
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun refreshQuranCorpusStatus() {
        if (!::quranCorpusStatus.isInitialized) return
        val s = HakimVerifiedQuranCorpus.status(this)
        quranCorpusStatus.text = if (s.optBoolean("ready")) {
            val offline = if (s.optBoolean("offline_reimport_source_available")) "المصدر الرسمي محفوظ محليًا وقابل للتصدير/الاستعادة دون شبكة" else "النص متحقق لكن نسخة المصدر الاحتياطية غير مثبتة"
            "جاهز ومتحقق محليًا: ${s.optInt("surah_count")} سورة، ${s.optInt("ayah_count")} آية، المصدر=${s.optString("source_title")}, تحديث=${s.optString("source_update")}. $offline."
        } else {
            "غير مثبت محليًا بعد. تبقى الحاكمية القرآنية مفعلة، لكن النص الدقيق لا يُنسب من الذاكرة؛ استخدم المصدر الرسمي ثم اختر «اعتماد الملف الرسمي»."
        }
    }

    private fun exportSovereignBackup() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "Hakim-Sovereign-Backup.json")
        }
        startActivityForResult(intent, REQ_EXPORT)
    }

    private fun importSovereignBackup() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        startActivityForResult(intent, REQ_IMPORT)
    }

    private fun load() {
        proactiveEnabled.isChecked = HakimProactiveEngine.isEnabled(this)
        globalInstructions.setText(HakimGovernanceStore.global(this))
        refreshQuranCorpusStatus()
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
        HakimProactiveEngine.setEnabled(this, proactiveEnabled.isChecked)
        val globalOk = HakimGovernanceStore.setGlobal(this, globalInstructions.text.toString())
        if (globalInstructions.text.toString().isBlank()) globalInstructions.setText(HakimGovernanceStore.global(this))

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
            Toast.makeText(this, "تم حفظ واعتماد نظام حكيم والمبادرة الذاتية والبيانات والثقة بالموقع محليًا", Toast.LENGTH_LONG).show()
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

    companion object {
        private const val REQ_EXPORT = 7301
        private const val REQ_IMPORT = 7302
        private const val REQ_QURAN_IMPORT = 7303
        private const val REQ_QURAN_EXPORT = 7304
        private const val REQ_QURAN_RESTORE = 7305
    }
}
