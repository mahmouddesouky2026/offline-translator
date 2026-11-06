package com.example.offlinetranslator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null

    private var fromEnglish by mutableStateOf(true)
    private var heard by mutableStateOf("")
    private var translated by mutableStateOf("")
    private var status by mutableStateOf("جاهز")
    private var listening by mutableStateOf(false)

    private val enToAr: Translator by lazy { buildTranslator(TranslateLanguage.ENGLISH, TranslateLanguage.ARABIC) }
    private val arToEn: Translator by lazy { buildTranslator(TranslateLanguage.ARABIC, TranslateLanguage.ENGLISH) }

    private fun buildTranslator(src: String, dst: String): Translator =
        Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(dst).build()
        )

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startListening() else status = "محتاج إذن الميكروفون"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(listener)

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.padding(24.dp).fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("مترجم أوفلاين", style = MaterialTheme.typography.headlineMedium)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = fromEnglish,
                                onClick = { fromEnglish = true },
                                label = { Text("English → عربي") }
                            )
                            FilterChip(
                                selected = !fromEnglish,
                                onClick = { fromEnglish = false },
                                label = { Text("عربي → English") }
                            )
                        }

                        OutlinedButton(onClick = { downloadModels() }) {
                            Text("تنزيل نماذج الترجمة (مرة واحدة)")
                        }

                        Button(
                            onClick = { onMicClicked() },
                            modifier = Modifier.fillMaxWidth().height(64.dp)
                        ) { Text(if (listening) "بسمعك..." else "اضغط وتكلم") }

                        Text("الحالة: $status", style = MaterialTheme.typography.bodySmall)

                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("النص الأصلي", style = MaterialTheme.typography.labelMedium)
                                Text(heard, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("الترجمة", style = MaterialTheme.typography.labelMedium)
                                Text(translated, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------- تنزيل نماذج الترجمة ----------
    private fun downloadModels() {
        status = "جاري التنزيل..."
        val conditions = DownloadConditions.Builder().build()
        enToAr.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                arToEn.downloadModelIfNeeded(conditions)
                    .addOnSuccessListener { status = "النماذج جاهزة، تشتغل أوفلاين" }
                    .addOnFailureListener { status = "فشل تنزيل عربي→إنجليزي" }
            }
            .addOnFailureListener { status = "فشل تنزيل إنجليزي→عربي" }
    }

    // ---------- الكلام ← نص ----------
    private fun onMicClicked() {
        val ok = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (ok) startListening() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status = "التعرف على الكلام مش متاح على الجهاز"
            return
        }
        val tag = if (fromEnglish) "en-US" else "ar-EG"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true) // أوفلاين
        }
        listening = true
        status = "بسمع..."
        recognizer?.startListening(intent)
    }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            listening = false
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (text.isNullOrBlank()) {
                status = "مسمعتش حاجة"
                return
            }
            heard = text
            translateAndSpeak(text)
        }

        override fun onError(error: Int) {
            listening = false
            status = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "مفهمتش الكلام، جرب تاني"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "مفيش صوت"
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                    "نزّل حزمة اللغة أوفلاين من إعدادات الجهاز"
                else -> "خطأ ($error)"
            }
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // ---------- الترجمة ----------
    private fun translateAndSpeak(text: String) {
        status = "بترجم..."
        val translator = if (fromEnglish) enToAr else arToEn
        translator.translate(text)
            .addOnSuccessListener { result ->
                translated = result
                status = "تمام"
                speak(result)
            }
            .addOnFailureListener { status = "الترجمة فشلت، اتأكد إنك نزلت النماذج" }
    }

    // ---------- نص ← كلام ----------
    override fun onInit(code: Int) {
        if (code != TextToSpeech.SUCCESS) status = "محرك الصوت مش شغال"
    }

    private fun speak(text: String) {
        val locale = if (fromEnglish) Locale("ar") else Locale.US
        val avail = tts?.setLanguage(locale)
        if (avail == TextToSpeech.LANG_MISSING_DATA || avail == TextToSpeech.LANG_NOT_SUPPORTED) {
            status = "نزّل صوت ${if (fromEnglish) "العربي" else "الإنجليزي"} من إعدادات TTS"
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "translation")
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.shutdown()
        enToAr.close()
        arToEn.close()
        super.onDestroy()
    }
}
