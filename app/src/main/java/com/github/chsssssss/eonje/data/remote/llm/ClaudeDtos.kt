package com.github.chsssssss.eonje.data.remote.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

@Serializable
data class ClaudeMessageRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val tools: List<ClaudeToolDto>,
    @SerialName("tool_choice") val toolChoice: ClaudeToolChoiceDto,
    val messages: List<ClaudeMessageDto>,
)

@Serializable
data class ClaudeToolDto(
    val name: String,
    val description: String,
    @SerialName("input_schema") val inputSchema: JsonElement,
)

@Serializable
data class ClaudeToolChoiceDto(
    val type: String = "tool",
    val name: String,
)

@Serializable
data class ClaudeMessageDto(
    val role: String,
    val content: JsonArray,
)

@Serializable
data class ClaudeMessageResponse(
    val id: String? = null,
    val content: List<ClaudeContentBlockDto> = emptyList(),
    @SerialName("stop_reason") val stopReason: String? = null,
)

@Serializable
data class ClaudeContentBlockDto(
    val type: String,
    val text: String? = null,
    val name: String? = null,
    val input: JsonElement? = null,
)

@Serializable
data class ExtractPlacesInput(
    val places: List<ExtractedPlaceDto> = emptyList(),
)

@Serializable
data class ExtractedPlaceDto(
    val name: String,
    val region: String? = null,
    val menu: String? = null,
)
