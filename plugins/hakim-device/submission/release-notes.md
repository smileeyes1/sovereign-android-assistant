Initial public submission of Hakim Device.

Hakim Device connects ChatGPT to a user-authorized Android device through a narrowly scoped, approval-gated execution bridge. The public version supports minimal device status, opening an explicit app or HTTP/HTTPS link, Home/Back/Recents navigation, and checking an existing operation. It intentionally excludes shell/root, raw screenshots, notifications, arbitrary typing, and generic tap/swipe control.

Authentication uses OAuth 2.1 authorization code with PKCE and refresh tokens when offline_access is requested. Reviewer credentials use an isolated demo fixture and never access a real device.
