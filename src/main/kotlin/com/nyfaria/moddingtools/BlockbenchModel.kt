package com.nyfaria.moddingtools

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

enum class ModelEligibility {
    CONVERTIBLE,
    NOT_A_GECKOLIB_MODEL,
}

sealed interface ConversionOutcome {
    data class Converted(val model: ConvertedModel) : ConversionOutcome
    data class Ignored(val modelFormat: String) : ConversionOutcome
    data class Failed(val reason: String) : ConversionOutcome
}

class BlockbenchModel(val root: JsonObject) {

    val meta: JsonObject get() = root.getAsJsonObject("meta") ?: JsonObject()

    val modelFormat: String get() = meta.string("model_format") ?: "unknown"

    val formatVersion: String get() = meta.string("format_version") ?: "unknown"

    val boxUv: Boolean get() = meta.bool("box_uv") ?: false

    val textureWidth: Int get() = root.getAsJsonObject("resolution")?.int("width") ?: 16

    val textureHeight: Int get() = root.getAsJsonObject("resolution")?.int("height") ?: 16

    val elements: JsonArray get() = root.getAsJsonArray("elements") ?: JsonArray()

    val outliner: JsonArray get() = root.getAsJsonArray("outliner") ?: JsonArray()

    val textures: JsonArray get() = root.getAsJsonArray("textures") ?: JsonArray()

    val animations: JsonArray get() = root.getAsJsonArray("animations") ?: JsonArray()

    val groups: JsonArray get() = root.getAsJsonArray("groups") ?: JsonArray()

    fun groupsByUuid(): Map<String, JsonObject> =
        groups.mapNotNull { it as? JsonObject }
            .mapNotNull { group -> group.string("uuid")?.let { it to group } }
            .toMap()

    fun eligibility(): ModelEligibility =
        if (modelFormat in CONVERTIBLE_FORMATS) ModelEligibility.CONVERTIBLE
        else ModelEligibility.NOT_A_GECKOLIB_MODEL

    fun elementsByUuid(): Map<String, JsonObject> =
        elements.mapNotNull { it as? JsonObject }
            .mapNotNull { element -> element.string("uuid")?.let { it to element } }
            .toMap()

    companion object {
        val CONVERTIBLE_FORMATS = setOf("geckolib_model", "animated_entity_model", "bedrock")

        fun parse(text: String): BlockbenchModel {
            val root = JsonParser.parseString(text) as? JsonObject
                ?: throw IllegalArgumentException("not a JSON object")
            return BlockbenchModel(root)
        }
    }
}

internal fun JsonObject.string(key: String): String? =
    get(key)?.takeIf { it.isJsonPrimitive }?.asString

internal fun JsonObject.int(key: String): Int? =
    get(key)?.takeIf { it.isJsonPrimitive }?.asInt

internal fun JsonObject.double(key: String): Double? =
    get(key)?.takeIf { it.isJsonPrimitive }?.asDouble

internal fun JsonObject.bool(key: String): Boolean? =
    get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean

internal fun JsonObject.vec(key: String): DoubleArray? {
    val array = get(key) as? JsonArray ?: return null
    if (array.size() < 3) return null
    return DoubleArray(3) { array[it].asDouble }
}

internal fun JsonObject.vec2(key: String): DoubleArray? {
    val array = get(key) as? JsonArray ?: return null
    if (array.size() < 2) return null
    return DoubleArray(2) { array[it].asDouble }
}

internal fun jsonArrayOf(vararg values: Double): JsonArray =
    JsonArray().apply { values.forEach { add(trim(it)) } }

internal fun trim(value: Double): Number {
    val rounded = Math.round(value * 100000.0) / 100000.0
    return if (rounded == Math.floor(rounded) && !rounded.isInfinite()) rounded.toLong() else rounded
}
