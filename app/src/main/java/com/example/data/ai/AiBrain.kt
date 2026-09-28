package com.example.data.ai

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class AssistantAction {
    data class CallPhone(val target: String) : AssistantAction()
    data class CopyFolder(val source: String, val target: String) : AssistantAction()
    data class GetNews(val category: String, val query: String = "") : AssistantAction()
    data class WebBrowse(val urlOrQuery: String) : AssistantAction()
    data class ToggleFlashlight(val enable: Boolean) : AssistantAction()
    data object CheckBattery : AssistantAction()
    data class AdjustVolume(val direction: String) : AssistantAction() // UP, DOWN, MUTE
    data class OpenApp(val appName: String) : AssistantAction()
    data object None : AssistantAction()
}

data class AssistantResponse(
    val spokenText: String,
    val action: AssistantAction = AssistantAction.None,
    val displayDetails: String = ""
)

object AiBrain {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val API_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    suspend fun processUserCommand(input: String): AssistantResponse = withContext(Dispatchers.IO) {
        val trimmed = input.trim()
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }

        // If API key is available and not dummy placeholder, try Gemini first
        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val geminiResult = callGemini(trimmed, apiKey)
                if (geminiResult != null) {
                    return@withContext geminiResult
                }
            } catch (e: Exception) {
                // Fall back gracefully to local rule parser
            }
        }

        // Local Urdu / Roman Urdu / English Intent Parser
        parseCommandLocally(trimmed)
    }

    private fun callGemini(prompt: String, apiKey: String): AssistantResponse? {
        val systemPrompt = """
            You are Zoya, a smart female AI phone assistant and controller. You communicate with warm, polite, and confident female persona in Urdu, Roman Urdu, or English depending on how user speaks.
            Analyze the user's command and output STRICT JSON in this exact structure:
            {
              "spokenText": "Polite response in female tone (Roman Urdu/Urdu/English)",
              "actionType": "CALL" | "COPY_FOLDER" | "NEWS" | "WEB_BROWSE" | "FLASHLIGHT_ON" | "FLASHLIGHT_OFF" | "BATTERY" | "VOLUME" | "OPEN_APP" | "NONE",
              "actionParam1": "target contact/number, source folder, search url, or app name",
              "actionParam2": "destination folder or volume direction",
              "details": "short summary of what was performed"
            }
        """.trimIndent()

        val jsonRequest = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", systemPrompt) })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.4)
            })
        }

        val request = Request.Builder()
            .url("$API_URL?key=$apiKey")
            .post(jsonRequest.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            val candidates = json.optJSONArray("candidates") ?: return null
            val firstCandidate = candidates.optJSONObject(0) ?: return null
            val content = firstCandidate.optJSONObject("content") ?: return null
            val parts = content.optJSONArray("parts") ?: return null
            val textPart = parts.optJSONObject(0)?.optString("text") ?: return null

            val parsedJson = JSONObject(textPart)
            val spokenText = parsedJson.optString("spokenText", "Ji, mein samajh gayi.")
            val actionType = parsedJson.optString("actionType", "NONE")
            val p1 = parsedJson.optString("actionParam1", "")
            val p2 = parsedJson.optString("actionParam2", "")
            val details = parsedJson.optString("details", "")

            val action = when (actionType.uppercase()) {
                "CALL" -> AssistantAction.CallPhone(p1.ifBlank { "Unknown" })
                "COPY_FOLDER" -> AssistantAction.CopyFolder(
                    source = p1.ifBlank { "Downloads" },
                    target = p2.ifBlank { "Documents" }
                )
                "NEWS" -> AssistantAction.GetNews(p1.ifBlank { "headlines" }, p2)
                "WEB_BROWSE" -> AssistantAction.WebBrowse(p1.ifBlank { "https://www.google.com" })
                "FLASHLIGHT_ON" -> AssistantAction.ToggleFlashlight(true)
                "FLASHLIGHT_OFF" -> AssistantAction.ToggleFlashlight(false)
                "BATTERY" -> AssistantAction.CheckBattery
                "VOLUME" -> AssistantAction.AdjustVolume(p2.ifBlank { "UP" })
                "OPEN_APP" -> AssistantAction.OpenApp(p1)
                else -> AssistantAction.None
            }

            return AssistantResponse(
                spokenText = spokenText,
                action = action,
                displayDetails = details
            )
        }
    }

    fun parseCommandLocally(input: String): AssistantResponse {
        val lower = input.lowercase()

        // 1. Folder / File Copy commands (Urdu & English)
        // e.g. "folder copy kr do", "Downloads sy Documents me copy kro", "data copy kr k dosry ma dal do"
        if (lower.contains("copy") || lower.contains("dal do") || lower.contains("bhejo") || lower.contains("transfer")) {
            var src = "Downloads"
            var dest = "Documents"

            if (lower.contains("downloads") && lower.contains("documents")) {
                src = "Downloads"
                dest = "Documents"
            } else if (lower.contains("dcim") || lower.contains("pictures") || lower.contains("photos")) {
                src = "Pictures"
                dest = "Documents"
            } else if (lower.contains("music") || lower.contains("audio")) {
                src = "Music"
                dest = "Downloads"
            }

            val spoken = "Ji bilkul, mein $src folder ka data $dest folder mein copy kar rahi hoon."
            return AssistantResponse(
                spokenText = spoken,
                action = AssistantAction.CopyFolder(src, dest),
                displayDetails = "Folder Transfer: $src ➔ $dest"
            )
        }

        // 2. Call command
        // e.g. "ksi ko cal kr ni ha", "call ammi", "Ali ko call milao", "call 0300"
        if (lower.contains("cal") || lower.contains("call") || lower.contains("phone milao") || lower.contains("dial")) {
            val phoneRegex = Regex("\\+?[0-9]{5,15}")
            val phoneMatch = phoneRegex.find(input)?.value

            val target = if (phoneMatch != null) {
                phoneMatch
            } else {
                val stopWords = setOf("call", "cal", "kr", "ni", "ha", "karni", "hai", "kro", "karo", "ko", "par", "phone", "milao", "dial", "lagao", "karna", "please")
                val remaining = lower.split(Regex("\\s+")).filter { it !in stopWords && it.isNotBlank() }
                val clean = remaining.joinToString(" ").trim()
                if (clean.isBlank()) "Ammi" else clean.capitalizeWords()
            }

            val spoken = "Ji, mein $target ko call mila rahi hoon."
            return AssistantResponse(
                spokenText = spoken,
                action = AssistantAction.CallPhone(target),
                displayDetails = "Calling: $target"
            )
        }

        // 3. News commands
        // e.g. "AJ ki fresh news kiya ha", "khabrain sunao", "latest news"
        if (lower.contains("news") || lower.contains("khabar") || lower.contains("khabrain") || lower.contains("taza")) {
            val category = when {
                lower.contains("tech") || lower.contains("technology") -> "Technology"
                lower.contains("sport") || lower.contains("cricket") -> "Sports"
                lower.contains("business") || lower.contains("economy") -> "Business"
                else -> "Top Headlines"
            }
            val spoken = "Ji zaroor, mein aap ke liye $category ki taza tareen khabrain laa rahi hoon."
            return AssistantResponse(
                spokenText = spoken,
                action = AssistantAction.GetNews(category),
                displayDetails = "Live News: $category"
            )
        }

        // 4. Web Browsing / Search commands
        // e.g. "browse google", "search wiki", "real time web browsing", "website kholo"
        if (lower.contains("browse") || lower.contains("search") || lower.contains("google") || lower.contains("website") || lower.contains("internet") || lower.contains("kholo")) {
            val query = if (lower.contains("google")) {
                "https://www.google.com"
            } else if (lower.contains("wikipedia")) {
                "https://www.wikipedia.org"
            } else if (lower.contains("techcrunch")) {
                "https://techcrunch.com"
            } else {
                var search = lower
                    .replace("browse", "")
                    .replace("search", "")
                    .replace("website", "")
                    .replace("kholo", "")
                    .replace("open", "")
                    .trim()
                if (search.isBlank()) "https://www.google.com" else "https://www.google.com/search?q=${search.replace(" ", "+")}"
            }
            val spoken = "Mein real-time web browser open kar rahi hoon."
            return AssistantResponse(
                spokenText = spoken,
                action = AssistantAction.WebBrowse(query),
                displayDetails = "Web Browser: $query"
            )
        }

        // 5. Flashlight / Torch
        if (lower.contains("flash") || lower.contains("torch") || lower.contains("batti")) {
            val enable = !lower.contains("off") && !lower.contains("band")
            val spoken = if (enable) "Flashlight on kar di hai." else "Flashlight band kar di hai."
            return AssistantResponse(
                spokenText = spoken,
                action = AssistantAction.ToggleFlashlight(enable),
                displayDetails = if (enable) "Flashlight: ON" else "Flashlight: OFF"
            )
        }

        // 6. Battery Status
        if (lower.contains("battery") || lower.contains("charge") || lower.contains("charging")) {
            return AssistantResponse(
                spokenText = "Aap ki phone battery check kar rahi hoon.",
                action = AssistantAction.CheckBattery,
                displayDetails = "Battery Diagnostic"
            )
        }

        // 7. Volume
        if (lower.contains("volume") || lower.contains("awaz")) {
            val direction = if (lower.contains("barhao") || lower.contains("up") || lower.contains("zyada")) "UP"
            else if (lower.contains("kam") || lower.contains("down") || lower.contains("low")) "DOWN"
            else "MUTE"
            val spoken = "Volume adjust kar diya gaya hai."
            return AssistantResponse(
                spokenText = spoken,
                action = AssistantAction.AdjustVolume(direction),
                displayDetails = "Volume $direction"
            )
        }

        // 8. Open specific apps
        if (lower.contains("whatsapp") || lower.contains("youtube") || lower.contains("camera") || lower.contains("settings")) {
            val app = when {
                lower.contains("whatsapp") -> "WhatsApp"
                lower.contains("youtube") -> "YouTube"
                lower.contains("camera") -> "Camera"
                else -> "Settings"
            }
            return AssistantResponse(
                spokenText = "Ji, $app open kar rahi hoon.",
                action = AssistantAction.OpenApp(app),
                displayDetails = "Launch App: $app"
            )
        }

        // Default Friendly conversational response
        val greetings = listOf(
            "Salam! Mein Zoya hoon, aap ki AI assistant. Mein phone control, folder copy, phone calls, real-time web browsing aur fresh news sunane ke liye tayyar hoon. Aap kya hukum karein ge?",
            "Ji farmaiye! Mein aap ke phone ke tamam operations execute karne ke liye ready hoon.",
            "Mein Zoya, aap ki female voice assistant. Aap mujhe folder copy karne, kisi ko call lagane ya fresh news sunne ka hukum de saktay hain."
        )
        return AssistantResponse(
            spokenText = greetings.random(),
            action = AssistantAction.None,
            displayDetails = "AI Conversation"
        )
    }

    private fun String.capitalizeWords(): String {
        return split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
    }
}
