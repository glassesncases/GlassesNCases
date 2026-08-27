package com.neha.tempglass.util

object ModelNameCanonicalizer {

    private val cmfNothingPrefix = Regex("^CMF\\s+Nothing\\b\\s*", RegexOption.IGNORE_CASE)
    private val cmfPrefix = Regex("^CMF\\b\\s*", RegexOption.IGNORE_CASE)
    private val nothingPrefix = Regex("^Nothing\\b\\s*", RegexOption.IGNORE_CASE)
    private val redmiPrefix = Regex("^Redmi\\b\\s*", RegexOption.IGNORE_CASE)
    private val miPrefix = Regex("^Mi\\b\\s*", RegexOption.IGNORE_CASE)
    private val renoPrefix = Regex("^Reno\\b\\s*", RegexOption.IGNORE_CASE)

    fun canonicalizeName(rawName: String): String {
        val input = rawName.trim().replace(Regex("\\s+"), " ")
        if (input.isBlank()) return input

        return when {
            cmfNothingPrefix.containsMatchIn(input) -> {
                val rest = cmfNothingPrefix.replace(input, "").trim()
                joinPrefix("CMF Nothing", rest)
            }

            cmfPrefix.containsMatchIn(input) -> {
                val rest = cmfPrefix.replace(input, "").trim()
                joinPrefix("CMF Nothing", rest)
            }

            nothingPrefix.containsMatchIn(input) -> {
                val rest = nothingPrefix.replace(input, "").trim()
                joinPrefix("CMF Nothing", rest)
            }

            miPrefix.containsMatchIn(input) -> {
                val rest = miPrefix.replace(input, "").trim()
                joinPrefix("Redmi", rest)
            }

            redmiPrefix.containsMatchIn(input) -> {
                val rest = redmiPrefix.replace(input, "").trim()
                joinPrefix("Redmi", rest)
            }

            renoPrefix.containsMatchIn(input) -> {
                val rest = renoPrefix.replace(input, "").trim()
                joinPrefix("Oppo Reno", rest)
            }

            else -> input
        }
    }

    fun canonicalBrand(modelName: String): String? {
        return when {
            cmfNothingPrefix.containsMatchIn(modelName) ||
                cmfPrefix.containsMatchIn(modelName) ||
                nothingPrefix.containsMatchIn(modelName) -> "CMF Nothing"

            redmiPrefix.containsMatchIn(modelName) ||
                miPrefix.containsMatchIn(modelName) -> "Redmi"

            renoPrefix.containsMatchIn(modelName) -> "Oppo"

            else -> null
        }
    }

    private fun joinPrefix(prefix: String, rest: String): String {
        return if (rest.isBlank()) prefix else "$prefix $rest"
    }
}
