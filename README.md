# Photo Studio v3.0 — Android editor + consent-based Telegram controls

This is a clean source project, not a modified/repacked copy of the suspicious APK.

## Android app features
- Dark, lightweight photo editor: open image, brightness, contrast, rotate, export.
- Remote Control screen with an explicit connection/pairing step.
- Telegram requests for status, opening the visible camera app (front/rear hint), and flashlight ON/OFF.
- Every camera or flashlight request is shown in a confirmation dialog on the phone.
- No hidden camera capture, no background sensor control, no storage scanner, no automatic file upload, no accessibility abuse, and no token embedded in the APK.
- Polling runs only while the Remote Control screen is open. Close it or press Disconnect to stop.

## Project structure
- `app/` — Android Studio Java source.
- `backend/` — Node.js Telegram bot and pairing/API bridge.

## 1. Configure the Telegram bot backend
1. Create a bot with Telegram's official BotFather and copy its token.
2. Obtain your own numeric Telegram user ID from a trusted ID bot or your Telegram client tooling.
3. Copy `backend/.env.example` to `backend/.env` and set `TELEGRAM_BOT_TOKEN`, `ADMIN_IDS`, and `PORT`.
4. Keep `backend/.env` private. Never put the Telegram bot token in Android source, APK resources, or a public repository.
5. Deploy the backend behind HTTPS (for example, Nginx + a valid TLS certificate). The Android app intentionally requires an `https://` server URL.
6. In `backend/`, run `npm install`, then `npm start`. Ensure outbound HTTPS to Telegram is allowed.

Example Nginx location (adjust domain, certificate, and upstream to your environment):

```nginx
location / {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

## 2. Build Android app
1. Open this project folder in a recent Android Studio.
2. Allow Gradle to download Android Gradle Plugin 8.6.1 and Android SDK Platform 35.
3. Build → Build APK(s). Expected output: `app/build/outputs/apk/debug/app-debug.apk`.
4. Install on your own test phone and open **Remote**.

Minimum Android version: Android 10 (API 29). Build requires internet access to fetch Gradle dependencies; this environment has not compiled or device-tested the APK.

## 3. Pair and test
1. Enter your public HTTPS backend URL in the app and tap **Register / Connect**.
2. Send `/pair 123456` (replace with the displayed code) to your bot in a private chat from an ID listed in `ADMIN_IDS`. Pair codes expire after 10 minutes.
3. Keep the Remote Control screen open. Use the bot's inline buttons.
4. Camera requests open the visible system camera app. The front/rear hint may be ignored by some camera apps; no photo is taken or sent automatically.
5. Flashlight changes require an on-device confirmation and Android camera permission.
6. Press **Disconnect** to clear the local pairing token. Restarting the backend clears in-memory pairing/command state; press Disconnect in the app, register again, then pair the new code.

## Security limitations
- This is a lab prototype, not a hardened production MDM product.
- The API keeps device pairings and pending commands in memory; restart clears them.
- Use HTTPS, keep dependencies updated, restrict `ADMIN_IDS`, and never expose the bot token.
- Only status, visible camera-app launch, and flashlight controls are implemented. The app does not take photos covertly, scan files, or silently upload personal data.
