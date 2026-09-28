package com.example.service.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class VoiceState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING
}

class VoiceAssistantManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isTtsInitialized = false

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    private val _spokenOutput = MutableStateFlow("")
    val spokenOutput: StateFlow<String> = _spokenOutput.asStateFlow()

    var onSpeechResultReceived: ((String) -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
        setupSpeechRecognizer()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            configureFemaleVoice()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _voiceState.value = VoiceState.SPEAKING
                }

                override fun onDone(utteranceId: String?) {
                    _voiceState.value = VoiceState.IDLE
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _voiceState.value = VoiceState.IDLE
                }
            })
        }
    }

    private fun configureFemaleVoice() {
        val ttsEngine = tts ?: return
        try {
            // Attempt to select an Urdu or English locale
            val localeUrdu = Locale("ur", "PK")
            val available = ttsEngine.isLanguageAvailable(localeUrdu)

            // Look through installed voices for high quality female voice
            val voices: Set<Voice>? = ttsEngine.voices
            val femaleVoice = voices?.firstOrNull { voice ->
                voice.name.contains("female", ignoreCase = true) ||
                voice.name.contains("f00", ignoreCase = true) ||
                voice.name.contains("woman", ignoreCase = true)
            }

            if (femaleVoice != null) {
                ttsEngine.voice = femaleVoice
            } else {
                if (available >= TextToSpeech.LANG_AVAILABLE) {
                    ttsEngine.language = localeUrdu
                } else {
                    ttsEngine.language = Locale.ENGLISH
                }
            }

            // A higher pitch (1.20f - 1.25f) gives a distinct, warm, natural female tone
            ttsEngine.setPitch(1.22f)
            ttsEngine.setSpeechRate(1.02f)
        } catch (e: Exception) {
            ttsEngine.language = Locale.US
            ttsEngine.setPitch(1.25f)
        }
    }

    private fun setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _voiceState.value = VoiceState.LISTENING
                    }

                    override fun onBeginningOfSpeech() {
                        _voiceState.value = VoiceState.LISTENING
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _voiceState.value = VoiceState.PROCESSING
                    }

                    override fun onError(error: Int) {
                        _voiceState.value = VoiceState.IDLE
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) {
                            _recognizedText.value = text
                            _voiceState.value = VoiceState.PROCESSING
                            onSpeechResultReceived?.invoke(text)
                        } else {
                            _voiceState.value = VoiceState.IDLE
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        matches?.firstOrNull()?.let {
                            _recognizedText.value = it
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    fun startListening() {
        // Stop current speech if speaking
        stopSpeaking()
        _recognizedText.value = ""

        if (speechRecognizer != null && SpeechRecognizer.isRecognitionAvailable(context)) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK")
                putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, arrayListOf("ur-PK", "en-US", "ur"))
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Zoya is listening...")
            }
            try {
                speechRecognizer?.startListening(intent)
                _voiceState.value = VoiceState.LISTENING
            } catch (e: Exception) {
                _voiceState.value = VoiceState.IDLE
            }
        } else {
            _voiceState.value = VoiceState.LISTENING
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {}
        _voiceState.value = VoiceState.IDLE
    }

    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        _spokenOutput.value = text
        if (!isTtsInitialized) {
            onComplete?.invoke()
            return
        }

        val utteranceId = "ZOYA_${System.currentTimeMillis()}"
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stopSpeaking() {
        tts?.stop()
        _voiceState.value = VoiceState.IDLE
    }

    fun destroy() {
        try {
            tts?.stop()
            tts?.shutdown()
            speechRecognizer?.destroy()
        } catch (e: Exception) {}
    }
}
