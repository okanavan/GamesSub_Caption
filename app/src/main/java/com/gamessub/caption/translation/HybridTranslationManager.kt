package com.gamessub.caption.translation

import com.gamessub.caption.model.TranslationEngineType
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Zero-Server-Cost Hybrid Translation Engine:
 * 1. OFFLINE_MLKIT: Google ML Kit On-Device Translation (59+ languages, completely offline after model download).
 * 2. DEEPL_BYOK: User's own Free/Pro DeepL API Key.
 * 3. GEMINI_AI_BYOK: User's own Gemini API Key with rolling 4-line game dialogue context memory.
 */
class HybridTranslationManager {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private var cachedMlKitPair: Pair<String, String>? = null
    private var activeTranslator: Translator? = null

    // Keeps the last 4 subtitle lines for context-aware Gemini AI translation
    private val recentDialogueContext = ArrayDeque<String>(4)

    suspend fun translate(
        text: String,
        sourceLangCode: String,
        targetLangCode: String,
        engine: TranslationEngineType,
        deepLApiKey: String,
        geminiApiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanInput = text.trim()
        if (cleanInput.isEmpty()) return@withContext Result.success("")
        if (sourceLangCode == targetLangCode) return@withContext Result.success(cleanInput)

        try {
            val translated = when (engine) {
                TranslationEngineType.OFFLINE_MLKIT -> {
                    translateWithMlKitOffline(cleanInput, sourceLangCode, targetLangCode)
                }
                TranslationEngineType.DEEPL_BYOK -> {
                    if (deepLApiKey.isBlank()) {
                        // Graceful fallback to offline if key is empty
                        translateWithMlKitOffline(cleanInput, sourceLangCode, targetLangCode)
                    } else {
                        translateWithDeepL(cleanInput, sourceLangCode, targetLangCode, deepLApiKey.trim())
                    }
                }
                TranslationEngineType.GEMINI_AI_BYOK -> {
                    if (geminiApiKey.isBlank()) {
                        translateWithMlKitOffline(cleanInput, sourceLangCode, targetLangCode)
                    } else {
                        translateWithGeminiContext(
                            text = cleanInput,
                            sourceLangCode = sourceLangCode,
                            targetLangCode = targetLangCode,
                            apiKey = geminiApiKey.trim()
                        )
                    }
                }
            }

            rememberContextLine(cleanInput)
            Result.success(translated)
        } catch (e: Exception) {
            // Fallback to Offline ML Kit if online BYOK call fails
            try {
                val fallback = translateWithMlKitOffline(cleanInput, sourceLangCode, targetLangCode)
                Result.success(fallback)
            } catch (inner: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun rememberContextLine(line: String) {
        if (recentDialogueContext.size >= 4) {
            recentDialogueContext.removeFirst()
        }
        recentDialogueContext.addLast(line)
    }

    private suspend fun translateWithMlKitOffline(
        text: String,
        sourceLangCode: String,
        targetLangCode: String
    ): String {
        val srcTag = TranslateLanguage.fromLanguageTag(sourceLangCode) ?: TranslateLanguage.ENGLISH
        val tgtTag = TranslateLanguage.fromLanguageTag(targetLangCode) ?: TranslateLanguage.TURKISH
        val pair = srcTag to tgtTag

        val translator = if (cachedMlKitPair == pair && activeTranslator != null) {
            activeTranslator!!
        } else {
            activeTranslator?.close()
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(srcTag)
                .setTargetLanguage(tgtTag)
                .build()
            val client = Translation.getClient(options)
            cachedMlKitPair = pair
            activeTranslator = client
            client
        }

        val conditions = DownloadConditions.Builder().build()
        translator.downloadModelIfNeeded(conditions).await()
        return translator.translate(text).await()
    }

    private fun translateWithDeepL(
        text: String,
        sourceLangCode: String,
        targetLangCode: String,
        apiKey: String
    ): String {
        val isFreeKey = apiKey.endsWith(":fx")
        val endpoint = if (isFreeKey) {
            "https://api-free.deepl.com/v2/translate"
        } else {
            "https://api.deepl.com/v2/translate"
        }

        val formBody = FormBody.Builder()
            .add("text", text)
            .add("source_lang", sourceLangCode.uppercase())
            .add("target_lang", targetLangCode.uppercase())
            .build()

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "DeepL-Auth-Key $apiKey")
            .post(formBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("DeepL HTTP ${response.code}: $bodyStr")
            }
            val json = JSONObject(bodyStr)
            val translations = json.getJSONArray("translations")
            if (translations.length() > 0) {
                return translations.getJSONObject(0).getString("text").trim()
            }
            throw IllegalStateException("DeepL boş yanıt döndürdü.")
        }
    }

    private fun translateWithGeminiContext(
        text: String,
        sourceLangCode: String,
        targetLangCode: String,
        apiKey: String
    ): String {
        val contextBlock = if (recentDialogueContext.isEmpty()) {
            "Yok"
        } else {
            recentDialogueContext.joinToString(separator = " | ")
        }

        val prompt = """
            Sen profesyonel bir video oyunu çevirmenisin.
            Önceki altyazı bağlamı: [$contextBlock]
            Kaynak dil kodu: $sourceLangCode -> Hedef dil kodu: $targetLangCode.
            Kurallar:
            1. Eğer metin başında 'İsim:' veya '[İsim]' varsa karakter ismini koru ve sadece konuşmayı doğal oyun dilinde çevir.
            2. Sadece çevrilmiş metni döndür, açıklama ekleme.
            Çevrilecek metin:
            $text
        """.trimIndent()

        val payload = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt))
                    )
                )
            )
        }

        val url =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Gemini HTTP ${response.code}")
            }
            val json = JSONObject(bodyStr)
            val candidates = json.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val parts = candidates.getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                if (parts.length() > 0) {
                    return parts.getJSONObject(0).getString("text").trim()
                }
            }
            throw IllegalStateException("Gemini yanıtı çözümlenemedi.")
        }
    }

    fun close() {
        activeTranslator?.close()
        activeTranslator = null
    }
}

