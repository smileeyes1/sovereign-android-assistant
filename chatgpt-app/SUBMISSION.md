# حزمة تقديم «حكيم» إلى دليل ChatGPT

## النوع
With MCP — Universal remote MCP server

## اسم التطبيق
حكيم

## الوصف القصير
Connect ChatGPT to Hakim

## الوصف الطويل
حكيم يجعل ChatGPT الرسمي طبقة المحادثة والاستدلال، بينما يبقى تطبيق حكيم على جهاز المستخدم ذراع التنفيذ. يمكن لـChatGPT قراءة حالة الجهاز والواجهة والإشعارات المأذونة ولقطة الشاشة، وطلب فتح تطبيق/رابط أو تنفيذ فعل واجهة محدود. لا يوفر الجسر shell أو root، ولا يحتاج OpenAI API key. الأفعال التي تغيّر حالة الهاتف تبقى خلف موافقة Android.

## الفئة المقترحة
Productivity / Developer Tools

## الروابط
- Website: https://hakim-chatgpt-bridge-production.up.railway.app/
- MCP: https://hakim-chatgpt-bridge-production.up.railway.app/mcp
- Privacy: https://hakim-chatgpt-bridge-production.up.railway.app/privacy
- Terms: https://hakim-chatgpt-bridge-production.up.railway.app/terms
- Support: https://hakim-chatgpt-bridge-production.up.railway.app/support

## المصادقة
OAuth 2.1 authorization-code + PKCE S256 + CIMD.
Scopes:
- hakim.read
- hakim.write

## الأدوات
- status — قراءة فقط
- ui — قراءة فقط
- notifications — قراءة فقط
- screenshot — قراءة فقط
- check_request — قراءة فقط
- launch — كتابة/تغيير حالة، خلف موافقة Android
- action — كتابة/تغيير حالة، خلف موافقة Android

## رسائل بداية مقترحة
1. ما حالة جهاز حكيم الآن؟
2. اعرض لي التطبيق المفتوح على جهازي.
3. تحقق من آخر إشعارات حكيم المأذونة.
4. افتح المتصفح على جهازي.
5. نفذ هذا الإجراء على هاتفي بعد أن أوافق.

## ملاحظات الإصدار
الإصدار الأول من جسر حكيم العام: MCP بعيد، OAuth 2.1/PKCE، تشفير HC1/HR1، موافقات Android للأفعال المتغيرة، بلا OpenAI API key وبلا shell/root.

## حالة التقديم
غير مُرسل بعد. يلزم قبل الضغط على Submit:
- هوية مطور/ناشر موثقة في OpenAI Platform.
- صلاحية Apps Management / api.apps.write.
- شعار نهائي.
- رمز domain verification من بوابة التقديم يوضع مؤقتًا في OPENAI_APP_CHALLENGE.
- اعتماد reviewer/demo flow لا يتطلب جهاز المستخدم الحقيقي.
- موافقة المستخدم الصريحة على الإرسال للمراجعة العامة.
