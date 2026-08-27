package com.neha.tempglass.data

import android.content.Context
import com.neha.tempglass.util.ModelNameCanonicalizer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val CURVED_BOX_START = 79
private const val CURVED_BOX_END = 99

@Serializable
data class InitialModelDto(
    val modelName: String,
    val boxNumber: Int,
    val type: String? = null,
    val additionalCompatible: List<String> = emptyList()
)

object InitialDataParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(
        context: Context,
        assetName: String = "initial_models.json"
    ): List<ModelEntity> {
        val raw = runCatching {
            context.assets.open(assetName).bufferedReader().use { it.readText() }
        }.getOrDefault("[]")

        val parsed = runCatching {
            json.decodeFromString<List<InitialModelDto>>(raw)
        }.getOrElse {
            emptyList()
        }

        return parsed
            .mapNotNull { dto ->
                val cleanName = ModelNameCanonicalizer.canonicalizeName(dto.modelName)
                if (cleanName.isBlank() || dto.boxNumber <= 0) return@mapNotNull null

                ModelEntity(
                    modelName = cleanName,
                    boxNumber = dto.boxNumber,
                    additionalCompatible = CompatibleModelsCodec.encode(dto.additionalCompatible),
                    type = when (dto.type?.trim()?.lowercase()) {
                        "curved" -> ModelType.CURVED.storageValue
                        "flat" -> ModelType.FLAT.storageValue
                        "membrane" -> ModelType.MEMBRANE.storageValue
                        else -> if (dto.boxNumber in CURVED_BOX_START..CURVED_BOX_END) {
                            ModelType.CURVED.storageValue
                        } else {
                            ModelType.FLAT.storageValue
                        }
                    },
                    isUserAdded = false,
                    isDeleted = false,
                    isFavorite = false
                )
            }
            .distinctBy { it.modelName.lowercase() }
    }
}
