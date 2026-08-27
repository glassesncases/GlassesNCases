package com.neha.tempglass.data

import kotlinx.serialization.json.Json

object CompatibleModelsCodec {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(values: List<String>): String {
        val cleaned = values
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return json.encodeToString(cleaned)
    }

    fun decode(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()

        return runCatching {
            json.decodeFromString<List<String>>(raw)
        }.getOrElse {
            raw.split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
        }
    }
}
