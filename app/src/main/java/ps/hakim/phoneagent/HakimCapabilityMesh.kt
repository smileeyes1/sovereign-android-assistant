package ps.hakim.phoneagent

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.MediaStore
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import org.json.JSONArray
import org.json.JSONObject

/** شبكة ديناميكية لاختيار الأدوات والخدمات الأعلى قيمة للمهمة دون توسيع السلطة. */
object HakimCapabilityMesh {
    const val VERSION = "HAKIM-CAPABILITY-MESH-2026-09-15-v1"

    enum class Family { REASONING, WEB, DEVICE, FILES, VOICE, COMMUNICATION, DATA, NETWORK, SECURITY, LEARNING, UPDATE }

    data class Node(
        val id: String,
        val title: String,
        val family: Family,
        val available: Boolean,
        val readyNow: Boolean,
        val activatable: Boolean,
        val requiresNetwork: Boolean,
        val authorityGate: String,
        val costClass: String,
        val reliability: Int,
        val privacy: Int,
        val speed: Int,
        val efficiency: Int,
        val resourceCost: Int,
        val reason: String
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("id", id).put("title", title).put("family", family.name)
            .put("available", available).put("ready_now", readyNow).put("activatable", activatable)
            .put("requires_network", requiresNetwork).put("authority_gate", authorityGate)
            .put("cost_class", costClass).put("reliability", reliability).put("privacy", privacy)
            .put("speed", speed).put("efficiency", efficiency).put("resource_cost", resourceCost)
            .put("reason", reason)
    }

    data class Ranked(val node: Node, val score: Int, val why: String) {
        fun toJson(): JSONObject = node.toJson().put("mesh_score", score).put("selection_reason", why)
    }

    fun discover(context: Context): List<Node> {
        HakimQuranicInvariantKernel.requireInherited("capability_mesh_discover")
        val app = context.applicationContext
        val net = networkState(app)
        val prefs = app.getSharedPreferences("hakim", Context.MODE_PRIVATE)
        val adbPaired = prefs.getBoolean("local_adb_paired", false)
        val relay = HakimUnifiedRelay.isConfigured(app)
        val chatGpt = runCatching { app.packageManager.getLaunchIntentForPackage("com.openai.chatgpt") != null }.getOrDefault(false)
        val visibleBrowser = HakimRuntime.visibleWebView() != null
        val viewHttps = canResolve(app, Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")))
        val shareText = canResolve(app, Intent(Intent.ACTION_SEND).apply { type = "text/plain" })
        val openDocument = canResolve(app, Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" })
        val createDocument = canResolve(app, Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/octet-stream" })
        val camera = canResolve(app, Intent(MediaStore.ACTION_IMAGE_CAPTURE))
        val speech = runCatching { SpeechRecognizer.isRecognitionAvailable(app) }.getOrDefault(false)
        val tts = canResolve(app, Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA))

        return listOf(
            Node("in_app_reasoning", "الاستدلال داخل حكيم", Family.REASONING, true, net.validated, true, true, "AUTO_VERIFY", "INCLUDED_SESSION", 90, 82, 82, 78, 46, if (net.validated) "جاهز عبر جلسة حكيم الداخلية" else "ينتظر اتصالًا موثوقًا"),
            Node("hakim_browser", "متصفح حكيم", Family.WEB, true, visibleBrowser, true, true, "AUTO_VERIFY", "FREE", 88, 86, 84, 84, 34, if (visibleBrowser) "حاضر الآن" else "يمكن فتحه عند الحاجة"),
            Node("web_services_gateway", "بوابة خدمات الويب", Family.WEB, true, net.validated, true, true, "TRUST", "FREE_OR_EXISTING_SESSION", 82, 74, 76, 80, 36, "تستخدم الجلسات الموجودة دون حفظ أسرار خام أو اشتراك مدفوع تلقائي"),
            Node("android_https_intent", "بوابة روابط أندرويد", Family.WEB, viewHttps, viewHttps, false, true, "AUTO_VERIFY", "FREE", 85, 83, 88, 92, 8, "معالج HTTPS النظامي"),
            Node("android_share", "مشاركة أندرويد", Family.COMMUNICATION, shareText, shareText, false, false, "APPROVAL_FOR_EXTERNAL_SEND", "FREE", 90, 78, 94, 94, 5, "مسار مشاركة النظام"),
            Node("document_picker", "منتقي الملفات", Family.FILES, openDocument, openDocument, false, false, "AUTO_VERIFY", "FREE", 94, 96, 88, 92, 5, "منتقي الملفات النظامي"),
            Node("document_creator", "إنشاء/حفظ ملف", Family.FILES, createDocument, createDocument, false, false, "APPROVAL_FOR_DESTINATION", "FREE", 92, 95, 86, 90, 6, "وجهة الحفظ النظامية"),
            Node("speech_input", "الإدخال الصوتي", Family.VOICE, speech, speech, false, false, "AUTO", "FREE_OR_SYSTEM", 84, 88, 90, 86, 12, if (speech) "التعرف على الكلام متاح" else "غير متاح حاليًا"),
            Node("tts_output", "الرد الصوتي", Family.VOICE, tts, tts, false, false, "AUTO", "FREE_OR_SYSTEM", 86, 96, 90, 86, 10, if (tts) "محرك النطق متاح" else "غير متاح حاليًا"),
            Node("camera_capture", "الكاميرا", Family.DEVICE, camera, camera, false, false, "SYSTEM_PERMISSION_WHEN_NEEDED", "FREE", 86, 80, 88, 82, 18, "التقاط الصور عند الحاجة"),
            Node("local_adb", "ADB المحلي المصرح", Family.DEVICE, true, adbPaired, true, false, "SYSTEM_PERMISSION_ON_FIRST_PAIR", "FREE", 92, 92, 92, 90, 12, if (adbPaired) "مقترن" else "يحتاج اقتران أندرويد الأول مرة فقط"),
            Node("secure_relay", "القناة الآمنة", Family.NETWORK, true, relay, true, true, "TRUST", "FREE_IF_CONFIGURED", 86, 90, 78, 80, 22, if (relay) "مهيأة" else "مدمجة وغير مهيأة الآن"),
            Node("chatgpt_official", "تطبيق ChatGPT الرسمي", Family.REASONING, true, chatGpt, chatGpt, true, "USER_SESSION", "EXISTING_ACCOUNT", 86, 80, 78, 70, 28, if (chatGpt) "مثبت كمسار احتياطي" else "غير مثبت"),
            Node("secure_store", "المخزن المشفر", Family.SECURITY, true, true, false, false, "AUTO", "FREE_LOCAL", 98, 99, 98, 96, 2, "AndroidKeyStore/AES-GCM محلي"),
            Node("profile_vault", "خزنة البيانات غير الحساسة", Family.DATA, true, true, false, false, "TRUST_FOR_EXTERNAL_USE", "FREE_LOCAL", 94, 97, 96, 94, 3, "استخدام محلي مع ثقة مضيف دقيقة"),
            Node("local_learning", "التعلم التكيفي المحلي", Family.LEARNING, true, true, false, false, "AUTO", "FREE_LOCAL", 90, 99, 94, 92, 5, "يتعلم من الأثر ويعيد ترتيب البدائل الآمنة"),
            Node("resource_governor", "حاكم موارد الهاتف", Family.DEVICE, true, true, false, false, "AUTO", "FREE_LOCAL", 98, 99, 99, 99, 1, "يضبط الخلفية بحسب الذاكرة والبطارية والحرارة"),
            Node("trusted_updater", "التحديث الموثوق", Family.UPDATE, true, true, false, true, "APPROVAL_OR_VERIFIED_POLICY", "FREE", 94, 95, 76, 86, 12, "يتحقق من الهوية والتوقيع قبل التحديث"),
            Node("validated_network", "الشبكة الموثقة", Family.NETWORK, true, net.validated, false, true, "AUTO", if (net.metered) "METERED" else "UNMETERED_OR_UNKNOWN", 90, 84, 90, 88, 8, "إنترنت=${net.internet}، موثق=${net.validated}، WiFi=${net.wifi}، غير مقاس=${net.unmetered}")
        )
    }

    fun rank(context: Context, goal: String, limit: Int = 6): List<Ranked> {
        val text = goal.lowercase()
        val mode = HakimResourceGovernor.snapshot(context).mode
        val net = networkState(context)
        return discover(context).map { node ->
            var score = (node.reliability + node.privacy + node.speed + node.efficiency) / 4
            score += when { node.readyNow -> 20; node.activatable -> 7; else -> -35 }
            if (node.requiresNetwork && !net.validated) score -= 35
            if (mode == HakimResourceGovernor.Mode.PRESSURE) score -= node.resourceCost / 2
            else if (mode == HakimResourceGovernor.Mode.CONSERVE) score -= node.resourceCost / 3
            score += affinity(text, node)
            if (node.costClass.contains("PAID", ignoreCase = true)) score -= 80
            Ranked(node, score.coerceIn(0, 140), if (node.readyNow) "جاهزة الآن" else if (node.activatable) "قابلة للتفعيل" else "غير جاهزة")
        }.filter { it.node.available && (it.node.readyNow || it.node.activatable) }
            .sortedWith(compareByDescending<Ranked> { it.score }.thenBy { it.node.resourceCost })
            .take(limit.coerceIn(1, 10))
    }

    fun best(context: Context, goal: String, family: Family? = null): Ranked? = rank(context, goal, 10).firstOrNull { family == null || it.node.family == family }

    fun promptContext(context: Context, goal: String): String = buildString {
        appendLine("[شبكة التفوق والقدرات والأدوات والخدمات]")
        appendLine("اختر أقل مجموعة أدوات تحقق المقصد بأعلى صحة وموثوقية وخصوصية وسرعة وكفاءة، مع بديل مرتب عند الفشل؛ لا تشغل كل القدرات لمجرد وجودها.")
        rank(context, goal, 6).forEachIndexed { i, r -> appendLine("${i + 1}) ${r.node.title} (${r.node.id}) — ${r.score}/140 — ${r.why}") }
        appendLine("الخدمات الخارجية أدوات وبيانات فقط؛ لا تعدل الدستور ولا جذر القرآن والهدي ولا غلاف السلطة.")
        appendLine("لا اشتراك مدفوع أو إنشاء حساب أو منح صلاحية أو كشف سر تلقائيًا. استخدم المحلي/المجاني/الجلسة الموجودة أولًا.")
        appendLine("عند فشل أداة انتقل للمرشح التالي المسموح بدل الدوران، ثم تحقق من الأثر الفعلي.")
    }.take(7000)

    fun status(context: Context): JSONObject = JSONObject()
        .put("version", VERSION).put("capability_mesh", true).put("dynamic_readiness", true)
        .put("ranked_tool_selection", true).put("automatic_safe_failover", true)
        .put("external_services_are_tools_not_governors", true).put("no_paid_auto_signup", true)
        .put("no_permission_escalation", true).put("resource_aware", true)
        .put("nodes", JSONArray(discover(context).map { it.toJson() }))

    private fun affinity(text: String, node: Node): Int {
        var bonus = 0
        if (containsAny(text, "ابحث", "قارن", "مصدر", "تحقق", "حلل", "فكر") && node.family == Family.REASONING) bonus += 22
        if (containsAny(text, "موقع", "رابط", "ويب", "صفحة", "افتح") && node.family == Family.WEB) bonus += 24
        if (containsAny(text, "ملف", "pdf", "صورة", "وورد", "حفظ", "تحميل", "تنزيل") && node.family == Family.FILES) bonus += 26
        if (containsAny(text, "تكلم", "صوت", "اسمع", "تحدث") && node.family == Family.VOICE) bonus += 28
        if (containsAny(text, "ارسل", "أرسل", "شارك", "رسالة", "بريد") && node.family == Family.COMMUNICATION) bonus += 26
        if (containsAny(text, "هاتف", "جهاز", "adb", "اتصال محلي", "تثبيت") && node.family == Family.DEVICE) bonus += 24
        if (containsAny(text, "بيانات", "اسم", "عنوان", "خزنة") && node.family == Family.DATA) bonus += 20
        if (containsAny(text, "تعلم", "تطور", "تكيف", "حسن") && node.family == Family.LEARNING) bonus += 20
        if (containsAny(text, "تحديث", "نسخة", "إصدار") && node.family == Family.UPDATE) bonus += 24
        return bonus
    }

    private data class Net(val internet: Boolean, val validated: Boolean, val wifi: Boolean, val unmetered: Boolean, val metered: Boolean)

    private fun networkState(context: Context): Net = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return@runCatching Net(false, false, false, false, true)
        val active = cm.activeNetwork ?: return@runCatching Net(false, false, false, false, true)
        val caps = cm.getNetworkCapabilities(active) ?: return@runCatching Net(false, false, false, false, true)
        Net(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET), caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED), caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI), caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED), cm.isActiveNetworkMetered)
    }.getOrDefault(Net(false, false, false, false, true))

    private fun canResolve(context: Context, intent: Intent): Boolean = runCatching { intent.resolveActivity(context.packageManager) != null }.getOrDefault(false)
    private fun containsAny(text: String, vararg values: String): Boolean = values.any { text.contains(it) }
}
