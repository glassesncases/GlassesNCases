package com.neha.tempglass.data

import android.content.Context
import com.neha.tempglass.util.ModelNameCanonicalizer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class InitialCaseModelDto(
    val modelName: String,
    val quantity: Int = 0
)

object InitialCaseDataParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(
        context: Context,
        assetName: String = "initial_case_models.json"
    ): List<CaseModelEntity> {
        val raw = runCatching {
            context.assets.open(assetName).bufferedReader().use { it.readText() }
        }.getOrDefault("[]")

        val parsed = runCatching {
            json.decodeFromString<List<InitialCaseModelDto>>(raw)
        }.getOrElse {
            emptyList()
        }

        val qtyByModel = linkedMapOf<String, Int>()

        parsed.forEach { dto ->
            val cleanName = ModelNameCanonicalizer.canonicalizeName(dto.modelName)
            if (cleanName.isBlank()) return@forEach

            val safeQty = dto.quantity.coerceAtLeast(0)
            qtyByModel[cleanName] = (qtyByModel[cleanName] ?: 0) + safeQty
        }

        return qtyByModel.map { (modelName, quantity) ->
            CaseModelEntity(
                modelName = modelName,
                quantity = quantity,
                isUserAdded = false,
                isDeleted = false
            )
        }
    }
}
