# أمن جسر حكيم

- لا أسرار ثابتة في المستودع.
- لا OpenAI API key.
- لا session cookies لـChatGPT.
- لا shell ولا root.
- command carrier: HC1 / AES-256-GCM + HMAC-SHA256 + expiry + request id.
- result carrier: HR1 / AES-256-GCM.
- state-changing operations تبقى approval-gated على Android.
- replay protection موجود في HakimUnifiedRelay.
- Bearer credential يمثل اقتران جهاز واحد ويعامل كسر.
- أي تسريب أو اختلاف بروتوكول يجب أن يفشل مغلقًا.
