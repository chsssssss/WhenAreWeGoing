package com.github.chsssssss.eonje.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.github.chsssssss.eonje.BuildConfig
import com.github.chsssssss.eonje.domain.model.ExtractedPlace
import com.github.chsssssss.eonje.domain.repository.CaptionParsingRepository
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject

// 2026-10-16에 만료 예정 — 교체 전에 https://ai.google.dev/gemini-api/docs/deprecations 에서 최신 모델 ID 확인할 것.
private const val GEMINI_MODEL_NAME = "gemini-2.5-flash"
private const val FIREBASE_APP_NAME = "eonje-genai"
private const val MAX_IMAGES = 3

@Serializable
private data class ExtractedPlaceDto(
    val name: String,
    val region: String? = null,
    val menu: String? = null,
)

/**
 * F2 캡션 파싱: 온디바이스(ML Kit GenAI, Gemini Nano)를 먼저 시도하고,
 * 미지원 기기이거나 실패하면 Firebase AI Logic(Gemini 클라우드, 무료 티어)으로 폴백한다.
 * 이미지가 포함된 2차 파싱은 온디바이스 비전이 아직 불안정해 곧바로 클라우드로 보낸다.
 */
class CaptionParsingRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) : CaptionParsingRepository {

    private val onDeviceModel by lazy { Generation.getClient() }
    private val firebaseModel: GenerativeModel? by lazy { buildFirebaseModel() }

    override suspend fun extractPlaces(caption: String, imageUrls: List<String>): Result<List<ExtractedPlace>> {
        if (imageUrls.isEmpty()) {
            extractOnDevice(caption)?.let { return Result.success(it) }
        }
        return extractViaFirebase(caption, imageUrls)
    }

    private suspend fun extractOnDevice(caption: String): List<ExtractedPlace>? {
        return try {
            if (onDeviceModel.checkStatus() != FeatureStatus.AVAILABLE) return null
            val response = onDeviceModel.generateContent(buildExtractionPrompt(caption))
            val text = response.candidates.firstOrNull()?.text ?: return null
            parsePlaces(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 온디바이스 실패는 네트워크 문제가 아니라 그냥 폴백 대상 — 조용히 클라우드로 넘어간다.
            null
        }
    }

    private suspend fun extractViaFirebase(caption: String, imageUrls: List<String>): Result<List<ExtractedPlace>> {
        val model = firebaseModel
            ?: return Result.failure(IllegalStateException("Firebase 프로젝트가 아직 설정되지 않았어요"))
        return try {
            val bitmaps = imageUrls.take(MAX_IMAGES).mapNotNull { downloadBitmap(it) }
            val prompt = content {
                text(buildExtractionPrompt(caption))
                bitmaps.forEach { image(it) }
            }
            val response = model.generateContent(prompt)
            val text = response.text ?: return Result.success(emptyList())
            Result.success(parsePlaces(text).orEmpty())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e.asRetryableNetworkFailure())
        }
    }

    private fun buildFirebaseModel(): GenerativeModel? {
        val projectId = BuildConfig.FIREBASE_PROJECT_ID
        val applicationId = BuildConfig.FIREBASE_APPLICATION_ID
        val apiKey = BuildConfig.FIREBASE_API_KEY
        if (projectId.isBlank() || applicationId.isBlank() || apiKey.isBlank()) return null

        val options = FirebaseOptions.Builder()
            .setProjectId(projectId)
            .setApplicationId(applicationId)
            .setApiKey(apiKey)
            .build()
        // google-services.json 없이도 동작하도록, 기본 앱과 분리된 이름 있는 FirebaseApp을 직접 초기화한다.
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == FIREBASE_APP_NAME }
            ?: FirebaseApp.initializeApp(context, options, FIREBASE_APP_NAME)

        return Firebase.ai(app = app, backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = GEMINI_MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = EXTRACTED_PLACE_LIST_SCHEMA
            },
        )
    }

    private suspend fun downloadBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                response.body?.byteStream()?.use { BitmapFactory.decodeStream(it) }
            }
        }.getOrNull()
    }

    private fun parsePlaces(text: String): List<ExtractedPlace>? = runCatching {
        json.decodeFromString<List<ExtractedPlaceDto>>(extractJsonArray(text))
            .map { ExtractedPlace(it.name, it.region, it.menu) }
    }.getOrNull()

    /** 모델이 지시를 무시하고 ```json 코드펜스로 감싸서 응답하는 경우까지 방어한다. */
    private fun extractJsonArray(text: String): String {
        val trimmed = text.trim()
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(trimmed)
        return fenced?.groupValues?.get(1)?.trim() ?: trimmed
    }

    private fun buildExtractionPrompt(caption: String): String = """
        다음은 인스타그램 게시물의 캡션이다. 언급되거나 이미지에 보이는 맛집 정보를 JSON 배열로만 응답해라.
        각 항목은 {"name": "상호명", "region": "지역(선택)", "menu": "대표 메뉴(선택)"} 형식이다.
        맛집이 없으면 빈 배열 []만 반환해라. JSON 외의 다른 텍스트는 절대 포함하지 마라.

        캡션:
        $caption
    """.trimIndent()

    /** Firebase SDK가 던지는 예외 타입이 확실치 않아, 원인 체인을 훑어 IOException이면 그걸로 재시도 판단을 맡긴다. */
    private fun Throwable.asRetryableNetworkFailure(): Throwable {
        var cause: Throwable? = this
        while (cause != null) {
            if (cause is IOException) return cause
            cause = cause.cause
        }
        return this
    }

    private companion object {
        val EXTRACTED_PLACE_LIST_SCHEMA: Schema = Schema.array(
            Schema.obj(
                properties = mapOf(
                    "name" to Schema.string(description = "상호명"),
                    "region" to Schema.string(description = "지역, 예: 성수동"),
                    "menu" to Schema.string(description = "대표 메뉴"),
                ),
                optionalProperties = listOf("region", "menu"),
            )
        )
    }
}
