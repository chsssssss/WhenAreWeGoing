package com.github.chsssssss.eonje.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.net.toUri
import com.github.chsssssss.eonje.BuildConfig
import com.github.chsssssss.eonje.data.analytics.Events
import com.github.chsssssss.eonje.domain.model.ExtractedPlace
import com.github.chsssssss.eonje.domain.repository.CaptionParsingRepository
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
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

// gemini-2.5-flash는 신규 사용자에게 이미 종료됨(2026-09 확인) — 만료 임박 시
// https://ai.google.dev/gemini-api/docs/deprecations 에서 최신 모델 ID 확인할 것.
private const val GEMINI_MODEL_NAME = "gemini-3.6-flash"
private const val MAX_IMAGES = 3
private const val TAG = "CaptionParsing"

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
    @param:ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val analytics: FirebaseAnalytics,
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
            parsePlaces(text)?.also { logParse(Events.Engine.NANO, Events.Stage.CAPTION, it.size) }
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
            val places = response.text?.let { parsePlaces(it) }.orEmpty()
            val stage = if (imageUrls.isEmpty()) Events.Stage.CAPTION else Events.Stage.IMAGE
            logParse(Events.Engine.CLOUD, stage, places.size)
            Result.success(places)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Gemini 클라우드 파싱 실패", e)
            Result.failure(e.asRetryableNetworkFailure())
        }
    }

    private fun logParse(engine: String, stage: String, extractedCount: Int) {
        analytics.logEvent(Events.CAPTION_PARSE) {
            param(Events.Param.ENGINE, engine)
            param(Events.Param.STAGE, stage)
            param(Events.Param.EXTRACTED_COUNT, extractedCount.toLong())
        }
    }

    private fun buildFirebaseModel(): GenerativeModel? {
        val projectId = BuildConfig.FIREBASE_GEMINI_PROJECT_ID
        val applicationId = BuildConfig.FIREBASE_GEMINI_APPLICATION_ID
        val apiKey = BuildConfig.FIREBASE_GEMINI_API_KEY
        if (projectId.isBlank() || applicationId.isBlank() || apiKey.isBlank()) return null

        val options = FirebaseOptions.Builder()
            .setProjectId(projectId)
            .setApplicationId(applicationId)
            .setApiKey(apiKey)
            .build()
        // google-services.json 없이 기본(default) FirebaseApp을 직접 초기화한다.
        // 이름 있는 별도 앱으로 초기화하면 Firebase AI SDK 내부 validateResponse가
        // 이름 무관하게 기본 앱을 찾다가 "Default FirebaseApp is not initialized"로 죽는다.
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            ?: FirebaseApp.initializeApp(context, options)

        // fir-gemini는 Firebase AI Logic에 App Check 재생 보호가 켜져 있어서, 재사용 가능한 일반 토큰을 보내면
        // "Firebase App Check token is invalid"(콘솔 지표상 "재사용된 토큰")로 전부 거부된다. 호출마다 1회용 토큰을 받는다.
        return Firebase.ai(
            app = app,
            backend = GenerativeBackend.googleAI(),
            useLimitedUseAppCheckTokens = true,
        ).generativeModel(
            modelName = GEMINI_MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = EXTRACTED_PLACE_LIST_SCHEMA
            },
        )
    }

    /** [url]은 원격 URL이거나 ThumbnailStore가 받아둔 file:// 경로다. */
    private suspend fun downloadBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val uri = url.toUri()
            if (uri.scheme == "file") return@runCatching BitmapFactory.decodeFile(uri.path)
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

    /**
     * MatchPostUseCase는 IOException만 재시도 대상으로 본다. 그래서 다시 불러서 나아질 수 있는 Gemini 오류는
     * IOException으로 감싸 넘긴다 — 그러지 않으면 일시 오류인데도 곧바로 UNRESOLVED(PLACE_NOT_FOUND)로 확정된다.
     * - QuotaExceededException: 무료 티어 호출 제한(429)
     * - ServerException: 5xx와, 따로 분류되지 않은 서버 거부 전부 — "Firebase App Check token is invalid"도 여기로 온다
     * - RequestTimeoutException: 요청 시간 초과
     * API 키 오류·서비스 비활성화·프롬프트 차단처럼 다시 불러도 같은 결과인 예외는 그대로 둔다.
     * 재시도가 끝내 안 통하면 WorkManager 한도(5회) 후 UNRESOLVED(NETWORK_ERROR)가 된다.
     */
    private fun Throwable.asRetryableNetworkFailure(): Throwable {
        if (this is QuotaExceededException || this is ServerException || this is RequestTimeoutException) {
            return IOException("Gemini 일시 오류: $message", this)
        }
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
