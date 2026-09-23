# Reviewer credentials

Do not commit live reviewer secrets to this repository.

At submission time:
1. Set strong Railway-only values for HAKIM_REVIEW_USER and HAKIM_REVIEW_PASSWORD.
2. Set HAKIM_PUBLIC_REVIEW_DEMO=1.
3. Enter those credentials only in the OpenAI submission portal's reviewer credential fields.
4. The reviewer OAuth path creates a synthetic credential whose topic starts with hakim_review_. Tool handlers then return deterministic demo fixtures and do not contact a real device.
5. Rotate or remove the reviewer credential after review if no longer needed.
