package ps.hakim.phoneagent

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * واجهة محلية لاختيار أو إنشاء عينة صوت يملكها المستخدم ثم حفظها في الخزنة.
 *
 * لا يطلب حكيم صلاحية الميكروفون هنا. خيار التسجيل يفوض تطبيق مسجل الصوت
 * في أندرويد، ثم يستورد النتيجة إذا أعاد URI صالحًا. هذا يحافظ على نسخة
 * Consumer دون صلاحية RECORD_AUDIO ويعطي الاستيراد كمسار احتياطي دائم.
 */
class HakimVoiceProfileActivity : Activity() {
    companion object {
        private const val REQ_PICK_AUDIO = 8701
        private const val REQ_SYSTEM_RECORDER = 8702
    }

    private lateinit var status: TextView
    private var player: MediaPlayer? = null
    private var previewFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        refresh()
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }

    private fun buildUi() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 30, 28, 30)
        }

        content.addView(TextView(this).apply {
            text = "صوتي في حكيم"
            gravity = Gravity.CENTER
            HakimUiKit.title(this)
        })

        content.addView(TextView(this).apply {
            text = "عينة صوت شخصية تحفظ محليًا ومشفرة بمفتاح أندرويد. لا تُرفع تلقائيًا إلى السحابة أو GitHub، ولا تدخل في نسخة الاستقلال."
            gravity = Gravity.CENTER
            setPadding(8, 12, 8, 18)
            HakimUiKit.status(this)
        })

        status = TextView(this).apply {
            gravity = Gravity.CENTER
            setPadding(14, 14, 14, 14)
            HakimUiKit.conversation(this)
        }
        content.addView(status)

        content.addView(TextView(this).apply {
            text = "نص مرجعي قصير"
            setPadding(4, 20, 4, 6)
            textSize = 18f
        })

        content.addView(TextView(this).apply {
            text = "السلام عليكم. أنا محمد غنّام، وهذا صوتي الطبيعي. أتحدث بهدوء ووضوح وبسرعة مريحة. أحب أن يكون الشرح بسيطًا ومفهومًا وقريبًا من الطفل. لا بأس أن نخطئ؛ المهم أن نفهم سبب الخطأ ونحاول مرة أخرى. هل أنت مستعد؟ ممتاز، نبدأ الآن. انظر جيدًا، فكر قليلًا، ثم اختر الإجابة التي تراها صحيحة. أحسنت، إجابة رائعة. انتبه إلى العشرات أولًا، ثم انظر إلى الآحاد. واحد، اثنان، ثلاثة، أربعة، خمسة، ستة، سبعة، ثمانية، تسعة، عشرة."
            setPadding(14, 12, 14, 18)
            textSize = 17f
            HakimUiKit.card(this)
        })

        content.addView(button("تسجيل عينة عبر مسجل الهاتف", primary = true) {
            confirmOwnership { openSystemRecorder() }
        })

        content.addView(button("اختيار تسجيل موجود من الهاتف") {
            confirmOwnership { pickExistingAudio() }
        })

        content.addView(button("تشغيل العينة المحفوظة") {
            playSaved()
        })

        content.addView(button("حذف العينة من حكيم") {
            confirmDelete()
        })

        content.addView(button("العودة إلى الإعدادات") { finish() })

        val scroll = ScrollView(this).apply { addView(content) }
        HakimArabicPolicy.applyUiDefaults(content)
        setContentView(scroll)
    }

    private fun confirmOwnership(action: () -> Unit) {
        val block = HakimEnterprisePolicy.blockReason(this, "voice")
        if (block != null) {
            Toast.makeText(this, block, Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("تأكيد ملكية الصوت")
            .setMessage(
                "أؤكد أن العينة صوتي أنا أو أنني أملك الحق في استخدامها، " +
                    "وأوافق على حفظها محليًا داخل حكيم لاستخدامي الشخصي والتعليمي. " +
                    "لن تُرفع تلقائيًا إلى خدمة خارجية."
            )
            .setPositiveButton("أؤكد") { _, _ -> action() }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    @Suppress("DEPRECATION")
    private fun openSystemRecorder() {
        val intent = Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION)
        if (intent.resolveActivity(packageManager) == null) {
            Toast.makeText(
                this,
                "لم يعثر حكيم على مسجل يعيد ملفًا مباشرة؛ استخدم «اختيار تسجيل موجود».",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        startActivityForResult(intent, REQ_SYSTEM_RECORDER)
    }

    @Suppress("DEPRECATION")
    private fun pickExistingAudio() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "audio/*"
            },
            REQ_PICK_AUDIO
        )
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        if (requestCode != REQ_PICK_AUDIO && requestCode != REQ_SYSTEM_RECORDER) return

        val uri = data?.data
        if (uri == null) {
            status.text =
                "لم يُرجع مسجل الهاتف ملفًا قابلًا للاستيراد. سجّل في تطبيق المسجل ثم اختر الملف من الخيار الثاني."
            return
        }

        runCatching { HakimVoiceProfileStore.saveFromUri(this, uri) }
            .onSuccess {
                status.text =
                    "حُفظت عينة صوتك محليًا ومشفرة. البصمة التقنية: " +
                    it.sha256.take(12) + "…"
            }
            .onFailure {
                status.text = "رُفض الملف أو تعذر حفظه بأمان؛ لم تُعتمد عينة جديدة."
            }
        refresh()
    }

    private fun playSaved() {
        stopPlayback()
        val temp = runCatching { HakimVoiceProfileStore.materializePreview(this) }.getOrNull()
        if (temp == null) {
            status.text = "لا توجد عينة قابلة للتشغيل."
            return
        }

        previewFile = temp
        val p = MediaPlayer()
        player = p
        runCatching {
            p.setDataSource(temp.absolutePath)
            p.setOnCompletionListener { stopPlayback(); refresh() }
            p.setOnErrorListener { _, _, _ -> stopPlayback(); refresh(); true }
            p.prepare()
            p.start()
            status.text = "يشغّل حكيم العينة محليًا الآن."
        }.onFailure {
            stopPlayback()
            status.text = "تعذر تشغيل العينة؛ لم تُرسل إلى أي خدمة خارجية."
        }
    }

    private fun stopPlayback() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        previewFile?.delete()
        previewFile = null
    }

    private fun confirmDelete() {
        if (!HakimVoiceProfileStore.hasProfile(this)) {
            status.text = "لا توجد عينة صوت محفوظة."
            return
        }

        AlertDialog.Builder(this)
            .setTitle("حذف عينة الصوت")
            .setMessage(
                "سيحذف حكيم العينة المشفرة ومفتاحها من هذا التطبيق. " +
                    "لن يُحذف التسجيل الأصلي الذي اخترته من هاتفك."
            )
            .setPositiveButton("حذف") { _, _ ->
                stopPlayback()
                HakimVoiceProfileStore.delete(this)
                refresh()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun refresh() {
        val m = HakimVoiceProfileStore.metadata(this)
        status.text = if (m == null) {
            "لا توجد عينة صوت محفوظة داخل حكيم بعد."
        } else {
            val whenText = DateFormat.getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.SHORT
            ).format(Date(m.updatedAt))
            "عينة الصوت محفوظة ومشفرة محليًا · " +
                (m.sizeBytes / 1024) + " كيلوبايت · آخر تحديث: " + whenText
        }
    }

    private fun button(
        label: String,
        primary: Boolean = false,
        action: () -> Unit
    ): Button = Button(this).apply {
        text = label
        textSize = 17f
        if (primary) HakimUiKit.primary(this) else HakimUiKit.secondary(this)
        setOnClickListener { action() }
    }
}
