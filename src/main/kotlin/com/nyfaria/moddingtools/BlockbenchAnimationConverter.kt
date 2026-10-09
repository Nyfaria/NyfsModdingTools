package com.nyfaria.moddingtools

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.math.BigDecimal
import java.math.RoundingMode

object BlockbenchAnimationConverter {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun convert(model: BlockbenchModel): String? {
        val animations = JsonObject()

        model.animations.mapNotNull { it as? JsonObject }.forEach { animation ->
            val bones = bones(animation)
            if (bones.size() == 0) return@forEach

            val name = animation.string("name") ?: return@forEach
            animations.add(name, JsonObject().apply {
                when (animation.string("loop")) {
                    "loop" -> addProperty("loop", true)
                    "hold" -> addProperty("loop", "hold_on_last_frame")
                }
                animation.double("length")?.let { addProperty("animation_length", trim(it)) }
                add("bones", bones)
            })
        }

        if (animations.size() == 0) return null
        return gson.toJson(JsonObject().apply {
            addProperty("format_version", "1.8.0")
            add("animations", animations)
        })
    }

    private fun bones(animation: JsonObject): JsonObject {
        val bones = JsonObject()
        val animators = animation.getAsJsonObject("animators") ?: return bones

        animators.entrySet().forEach { (_, value) ->
            val animator = value as? JsonObject ?: return@forEach
            val name = animator.string("name") ?: return@forEach
            val keyframes = animator.getAsJsonArray("keyframes") ?: return@forEach
            if (keyframes.size() == 0) return@forEach

            val channels = JsonObject()
            keyframes.mapNotNull { it as? JsonObject }.forEach { keyframe ->
                val channel = keyframe.string("channel") ?: return@forEach
                if (channel !in CHANNELS) return@forEach
                val point = keyframe.getAsJsonArray("data_points")
                    ?.firstOrNull()?.asJsonObject ?: return@forEach

                val entry = JsonObject().apply {
                    add("vector", vector(channel, point))
                    keyframe.string("easing")?.let { addProperty("easing", it) }
                }
                val target = channels.getAsJsonObject(channel)
                    ?: JsonObject().also { channels.add(channel, it) }
                target.add(timeKey(keyframe.double("time") ?: 0.0), entry)
            }
            if (channels.size() > 0) bones.add(name, channels)
        }
        return bones
    }

    private fun vector(channel: String, point: JsonObject) =
        com.google.gson.JsonArray().apply {
            val negateX = true
            val negateY = channel == "rotation"
            add(component(point, "x", negateX))
            add(component(point, "y", negateY))
            add(component(point, "z", false))
        }

    private fun component(point: JsonObject, axis: String, negate: Boolean): com.google.gson.JsonElement {
        val raw = point.get(axis)
        val number = when {
            raw == null || raw.isJsonNull -> 0.0
            raw.isJsonPrimitive && raw.asJsonPrimitive.isNumber -> raw.asDouble
            else -> raw.asString.toDoubleOrNull()
                ?: return com.google.gson.JsonPrimitive(molang(raw.asString, negate))
        }
        return com.google.gson.JsonPrimitive(trim(if (negate) -number else number))
    }

    private fun molang(expression: String, negate: Boolean): String =
        if (negate) "-($expression)" else expression

    private fun timeKey(time: Double): String {
        val value = BigDecimal(time).setScale(5, RoundingMode.HALF_UP).stripTrailingZeros()
        return if (value.scale() <= 0) value.setScale(1).toPlainString() else value.toPlainString()
    }

    private val CHANNELS = setOf("rotation", "position", "scale")
}
