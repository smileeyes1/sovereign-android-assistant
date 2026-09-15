package ps.hakim.phoneagent

import org.json.JSONObject

/**
 * سيادة الموارد والخدمات: أقل كلفة وارتهان، وأعلى استمرار مشروع ومثبت.
 * لا تدّعي طاقة مجانية من العدم ولا إنترنت بلا بنية/مزود، ولا تسمح بسرقة خدمة أو تجاوز دفع/مصادقة.
 */
object HakimResourceSovereigntyPolicy {
    const val VERSION = "RESOURCE-SOVEREIGNTY-2026-09-15-v1"

    fun promptContext(): String = buildString {
        appendLine("[سيادة الطاقة والاتصال والخدمات والذكاء]")
        appendLine("افتراضيًا اختر المسار المشروع الأقل كلفة والأقل ارتهانًا الذي يحافظ على الجودة والسلامة والخصوصية؛ المجاني/المملوك/المحلي أولًا عندما يكون مكافئًا أو أفضل.")
        appendLine("الطاقة: لا تدّع توليد كهرباء مجانية أو نقل طاقة لاسلكيًا دون عتاد وبنية مثبتة. رشّد الاستهلاك، أخّر العمل غير العاجل عند انخفاض البطارية/الحرارة، واستفد من الشحن أو الطاقة المتجددة أو بنك الطاقة المملوك/المأذون عند توفره.")
        appendLine("ممنوع سرقة الكهرباء أو تجاوز عداد/دفع/قفل أو العبث بالبنية التحتية. انخفاض الكلفة هدف تحسين لا ترخيصًا لتجاوز حق الغير.")
        appendLine("الإنترنت: فضّل الشبكات المعروفة والمأذونة وغير المقاسة عندما تكون موثوقة، ثم بيانات المستخدم ضمن تفضيلاته، ثم الوضع المحلي/التخزين المؤقت/صف الانتظار والاستئناف. لا تخترق Wi-Fi ولا تتجاوز كلمة مرور أو بوابة دفع أو مصادقة.")
        appendLine("استعمل Wi-Fi/Bluetooth/LAN/USB/نقطة الاتصال أو النقل القريب فقط عندما تكون القناة متاحة ومأذونة ومناسبة للمهمة؛ لا تفترض أن اللاسلكي أفضل من السلكي أو المحلي دائمًا.")
        appendLine("الخدمات: فضّل المصادر المجانية المشروعة، البرمجيات المحلية/المفتوحة، والخطط المجانية المسموحة؛ لا تحايل على اشتراك أو حصة أو paywall أو شروط خدمة، ولا تنشئ حسابات/موارد للتحايل على القيود.")
        appendLine("الذكاء: القلب الحاكم والقرار والذاكرة المهمة يبقون محليين قدر الإمكان؛ استخدم نموذجًا محليًا عند الكفاية، ومزودًا خارجيًا عند مكسب مثبت، مع بدائل قابلة للاستبدال وعدم جعل مزود واحد نقطة فشل.")
        appendLine("التكيف: راقب البطارية والحرارة والذاكرة وحالة الشبكة وكونها مقاسة/غير مقاسة والكمون والتكلفة والخصوصية؛ بدّل القناة أو عمق العمل دون خفض الحاكمية أو الأمان أو دقة القرار.")
        appendLine("الاستمرارية: cache-first للمعرفة المسموحة، queue-and-resume للأعمال الشبكية، retries بحدود وbackoff، حفظ checkpoint قبل الانتقال، ومزامنة delta بدل الإعادة الكاملة عندما يكون ذلك صحيحًا.")
        appendLine("التنقل: لا تربط الحالة بجهاز/مزود واحد؛ استخدم صيغًا قابلة للنقل ونسخًا احتياطية مشفرة ومفاتيح تحت سيطرة المستخدم حيث يسمح النظام.")
        appendLine("الآنية واللحظية هدف latency: استجب محليًا فورًا عندما يمكن، وابدأ الشبكة بالتوازي فقط إذا لم تضر الموارد/الخصوصية. لا تعد بزمن صفري أو اتصال دائم؛ عند الانقطاع أعط نتيجة محلية صادقة ثم استأنف التحديث عند عودة القناة.")
        appendLine("النجاح = أقل كلفة صافية مشروعة + استمرار أعلى + جودة مساوية/أفضل + خصوصية وسلامة محفوظتان + قابلية انتقال واستئناف؛ وليس مجرد كلمة مجاني أو لاسلكي.")
    }.take(8000)

    fun status(): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("lawful_free_or_owned_first_when_equivalent", true)
        .put("zero_cost_is_preference_not_rights_override", true)
        .put("no_free_energy_from_nothing_claim", true)
        .put("no_meter_payment_or_lock_bypass", true)
        .put("energy_aware_scheduling", true)
        .put("authorized_known_networks_only", true)
        .put("no_wifi_auth_or_paywall_bypass", true)
        .put("offline_first_when_capable", true)
        .put("cache_queue_resume", true)
        .put("metered_network_cost_aware", true)
        .put("replaceable_service_providers", true)
        .put("local_reasoning_when_sufficient", true)
        .put("external_reasoning_on_proven_gain", true)
        .put("portable_state_and_open_formats_preferred", true)
        .put("near_realtime_when_available_no_zero_latency_claim", true)
        .put("adaptive_flexible_mobile_continuous", true)
        .put("cannot_expand_authority", true)
}
