# خطوات التشغيل

## 1. مشروع جديد
Android Studio ← New Project ← **Empty Activity** (Compose)
- Package name: `com.example.offlinetranslator`
- Minimum SDK: 24 أو أعلى

## 2. استبدل MainActivity.kt
انسخ الملف المرفق مكان `MainActivity.kt` اللي في المشروع.

## 3. أضف المكتبة في `app/build.gradle.kts`
```kotlin
dependencies {
    implementation("com.google.mlkit:translate:17.0.3")
    // باقي مكتبات Compose بتبقى موجودة من القالب
}
```
ثم Sync Project.

## 4. عدّل `AndroidManifest.xml`
قبل وسم `<application>` أضف:
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />

<queries>
    <intent><action android:name="android.speech.RecognitionService" /></intent>
    <intent><action android:name="android.intent.action.TTS_SERVICE" /></intent>
</queries>
```
(الإنترنت مطلوب بس لتنزيل نماذج الترجمة أول مرة.)

## 5. جهّز الجهاز للأوفلاين (مرة واحدة)
1. **التعرف على الكلام:** إعدادات ← Google ← أو "Google app" ← Voice ← Offline speech recognition ← نزّل **English** و **Arabic (Egypt)**.
2. **الصوت (TTS):** إعدادات ← إدارة عامة ← اللغة ← Text-to-speech ← Google ← إعدادات ← Install voice data ← نزّل العربي والإنجليزي.
3. **الترجمة:** افتح التطبيق واضغط "تنزيل نماذج الترجمة" وانت متصل بالنت.

بعدها فعّل وضع الطيران وجرّب.

## ملاحظات
- لو الجهاز مفيهوش خدمة Google، التعرف على الكلام ممكن ميشتغلش، والحل وقتها Whisper أو Vosk.
- جودة التعرف على العربي بتختلف حسب الجهاز واللهجة.
