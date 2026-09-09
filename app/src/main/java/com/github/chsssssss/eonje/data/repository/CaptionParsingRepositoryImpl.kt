package com.github.chsssssss.eonje.data.repository

import com.github.chsssssss.eonje.BuildConfig
import com.github.chsssssss.eonje.data.remote.llm.ClaudeApi
import com.github.chsssssss.eonje.data.remote.llm.ClaudeMessageDto
import com.github.chsssssss.eonje.data.remote.llm.ClaudeMessageRequest
import com.github.chsssssss.eonje.data.remote.llm.ClaudeToolChoiceDto
import com.github.chsssssss.eonje.data.remote.llm.ClaudeToolDto
import com.github.chsssssss.eonje.data.remote.llm.ExtractPlacesInput
import com.github.chsssssss.eonje.domain.model.ExtractedPlace
import com.github.chsssssss.eonje.domain.repository.CaptionParsingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject

private const val CLAUDE_MODEL = "claude-haiku-4-5-20251001"
private const val MAX_IMAGES = 3

class CaptionParsingRepositoryImpl @Inject constructor(
    private val api: ClaudeApi,
    private val json: Json,
) : CaptionParsingRepository {

    override suspend fun extractPlaces(caption: String, imageUrls: List<String>): Result<List<ExtractedPlace>> {
        val apiKey = BuildConfig.ANTHROPIC_API_KEY
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("Anthropic API 키가 설정되지 않았어요"))
        }
        return try {
            val content = buildJsonArray {
                addJsonObject {
                    put("type", "text")
                    put("text", caption)
                }
                imageUrls.take(MAX_IMAGES).forEach { url ->
                    addJsonObject {
                        put("type", "image")
                        putJsonObject("source") {
                            put("type", "url")
                            put("url", url)
                        }
                    }
                }
            }

            val request = ClaudeMessageRequest(
                model = CLAUDE_MODEL,
                maxTokens = 1024,
                tools = listOf(EXTRACT_PLACES_TOOL),
                toolChoice = ClaudeToolChoiceDto(name = "extract_places"),
                messages = listOf(ClaudeMessageDto(role = "user", content = content)),
            )

            val response = api.createMessage(apiKey = apiKey, request = request)
            val toolInput = response.content.firstOrNull { it.type == "tool_use" }?.input
                ?: return Result.success(emptyList())
            val parsed = json.decodeFromJsonElement<ExtractPlacesInput>(toolInput)
            Result.success(parsed.places.map { ExtractedPlace(it.name, it.region, it.menu) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private val EXTRACT_PLACES_TOOL = ClaudeToolDto(
            name = "extract_places",
            description = "인스타그램 게시물의 캡션과 이미지에서 언급되거나 보이는 맛집 정보를 추출한다. 맛집이 없으면 빈 배열을 반환한다.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("places") {
                        put("type", "array")
                        putJsonObject("items") {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("name") {
                                    put("type", "string")
                                    put("description", "상호명")
                                }
                                putJsonObject("region") {
                                    put("type", "string")
                                    put("description", "지역, 예: 성수동")
                                }
                                putJsonObject("menu") {
                                    put("type", "string")
                                    put("description", "대표 메뉴")
                                }
                            }
                            putJsonArray("required") { add("name") }
                        }
                    }
                }
                putJsonArray("required") { add("places") }
            },
        )
    }
}
