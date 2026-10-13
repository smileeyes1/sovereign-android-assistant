# حكيم — كمبيوتر لينكس مجاني مؤقت من متصفح الهاتف

**الإصدار:** ١٫١ — ١٣ تشرين الأول ٢٠٢٦.

إذا لم يكن لديك حساب Oracle بعد، فهذا مسار **مستقل مؤقت** عبر GitHub Codespaces، ولا يحتاج إلى تشغيل الكمبيوتر الشخصي.

## افتح واجهة الإنشاء

https://codespaces.new/smileeyes1/sovereign-android-assistant/tree/feature/hakim-cloud-desktop-kit-20261013?quickstart=1

اختر إنشاء Codespace بحساب GitHub الشخصي وبجهاز **نواتين** إن أتيح. يفتح **محرر VS Code في المتصفح مع طرفية لينكس**؛ وهو ليس هاتف أندرويد افتراضيًا ولا سطح مكتب XFCE دائمًا. لا تتطلب هذه الخطوة بطاقة دفع عند توفر الرصيد المجاني الشخصي.

## حدود المصروفات والاستخدام

- GitHub Free الشخصي: ١٢٠ ساعة معالجة محسوبة على أساس النوى بالشهر؛ على نواتين تساوي نحو ٦٠ ساعة تشغيل؛ تخزين ١٥ جيجابايت-شهر. راجع الحصص الحالية قبل التشغيل.
- يُوقف Codespaces الخامل آليًا (افتراضيًا بعد ٣٠ دقيقة). التخزين يستهلك من الرصيد ما بقيت المساحة محفوظة.
- **لا تفعّل الإنفاق المدفوع، ولا ترفع حد الإنفاق، ولا تُنشئ أكثر من Codespace بلا حاجة.**
- استخدم صورة التطوير الافتراضية لـGitHub ولا تضف حاوية ثقيلة لتجنب استهلاك التخزين أكثر من اللازم.
- بعد إنشاء الكمبيوتر، ابقِ إعدادات سطح مكتب Oracle منفصلة؛ لا تشغّل `bootstrap.sh` على Codespaces؛ إنه مخصص لخادم Ubuntu مستقل لديك.

## تحقق بسيط من داخل الكمبيوتر

```bash
uname -a
pwd
git status --short --branch
bash -n cloud-desktop-kit/*.sh
```

هذه أوامر تحقق؛ إنشاء Codespace والوصول إليه فعليًا لم يحدث داخل هذه المحادثة.

**توثيق:**
- https://docs.github.com/en/billing/concepts/product-billing/github-codespaces
- https://docs.github.com/en/codespaces/setting-up-your-project-for-codespaces/setting-up-your-repository/facilitating-quick-creation-and-resumption-of-codespaces
