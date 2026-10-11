package com.mastertrader.app.data.api

import com.squareup.moshi.Json

data class GenerateContentRequest(
    @field:Json(name = "contents") val contents: List<Content>,
    @field:Json(name = "systemInstruction") val systemInstruction: Content? = null,
    @field:Json(name = "generationConfig") val generationConfig: GenerationConfig? = null
)

data class Content(
    @field:Json(name = "role") val role: String? = null,
    @field:Json(name = "parts") val parts: List<Part>
)

data class Part(
    @field:Json(name = "text") val text: String? = null,
    @field:Json(name = "inlineData") val inlineData: InlineData? = null
)

data class InlineData(
    @field:Json(name = "mimeType") val mimeType: String,
    @field:Json(name = "data") val data: String
)

data class GenerationConfig(
    @field:Json(name = "temperature") val temperature: Float = 0.3f,
    @field:Json(name = "topP") val topP: Float = 0.8f
)

data class GenerateContentResponse(
    @field:Json(name = "candidates") val candidates: List<Candidate>?,
    @field:Json(name = "error") val error: ApiError?
)

data class Candidate(
    @field:Json(name = "content") val content: Content?
)

data class ApiError(
    @field:Json(name = "code") val code: Int?,
    @field:Json(name = "message") val message: String?
)

data class ModelListResponse(
    @field:Json(name = "models") val models: List<ModelItem>? = null
)

data class ModelItem(
    @field:Json(name = "name") val name: String,
    @field:Json(name = "supportedGenerationMethods") val supportedGenerationMethods: List<String>? = null
)
