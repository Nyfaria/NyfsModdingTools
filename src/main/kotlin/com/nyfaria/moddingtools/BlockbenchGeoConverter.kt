package com.nyfaria.moddingtools

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

object BlockbenchGeoConverter {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun convert(model: BlockbenchModel, identifier: String): String {
        val geometry = JsonObject().apply {
            add("description", description(model, identifier))
            add("bones", bones(model))
        }
        val root = JsonObject().apply {
            addProperty("format_version", "1.12.0")
            add("minecraft:geometry", JsonArray().apply { add(geometry) })
        }
        return gson.toJson(root)
    }

    private fun description(model: BlockbenchModel, identifier: String): JsonObject {
        val bounds = visibleBounds(model)
        return JsonObject().apply {
            addProperty("identifier", identifier)
            addProperty("texture_width", model.textureWidth)
            addProperty("texture_height", model.textureHeight)
            addProperty("visible_bounds_width", trim(bounds[0]))
            addProperty("visible_bounds_height", trim(bounds[1]))
            add("visible_bounds_offset", jsonArrayOf(0.0, bounds[2], 0.0))
        }
    }

    private fun bones(model: BlockbenchModel): JsonArray {
        val byUuid = model.elementsByUuid()
        val groups = model.groupsByUuid()
        val bones = JsonArray()
        val loose = mutableListOf<JsonObject>()

        model.outliner.forEach { entry ->
            when {
                entry.isJsonObject -> walkGroup(resolveGroup(entry.asJsonObject, groups), null, byUuid, groups, model, bones)
                entry.isJsonPrimitive -> byUuid[entry.asString]?.let { loose.add(it) }
            }
        }

        if (loose.isNotEmpty()) {
            bones.add(JsonObject().apply {
                addProperty("name", "bb_main")
                add("pivot", jsonArrayOf(0.0, 0.0, 0.0))
                add("cubes", JsonArray().apply { loose.forEach { add(cube(it, model)) } })
            })
        }
        return bones
    }

    private fun resolveGroup(node: JsonObject, groups: Map<String, JsonObject>): JsonObject {
        if (node.has("name")) return node
        val definition = node.string("uuid")?.let { groups[it] } ?: return node
        return JsonObject().apply {
            definition.entrySet().forEach { (key, value) -> add(key, value) }
            node.get("children")?.let { add("children", it) }
        }
    }

    private fun walkGroup(
        group: JsonObject,
        parent: String?,
        byUuid: Map<String, JsonObject>,
        groups: Map<String, JsonObject>,
        model: BlockbenchModel,
        out: JsonArray,
    ) {
        if (group.bool("export") == false) return

        val name = group.string("name") ?: "bone"
        val bone = JsonObject().apply {
            addProperty("name", name)
            parent?.let { addProperty("parent", it) }
            val pivot = group.vec("origin") ?: doubleArrayOf(0.0, 0.0, 0.0)
            add("pivot", jsonArrayOf(-pivot[0], pivot[1], pivot[2]))
            group.vec("rotation")?.takeIf { it.any { v -> v != 0.0 } }?.let {
                add("rotation", jsonArrayOf(-it[0], -it[1], it[2]))
            }
        }

        out.add(bone)

        val cubes = JsonArray()
        group.getAsJsonArray("children")?.forEach { child ->
            when {
                child.isJsonPrimitive -> byUuid[child.asString]
                    ?.takeIf { it.bool("export") != false && (it.string("type") ?: "cube") == "cube" }
                    ?.let { cubes.add(cube(it, model)) }

                child.isJsonObject -> walkGroup(resolveGroup(child.asJsonObject, groups), name, byUuid, groups, model, out)
            }
        }
        if (cubes.size() > 0) bone.add("cubes", cubes)
    }

    private fun cube(element: JsonObject, model: BlockbenchModel): JsonObject {
        val from = element.vec("from") ?: doubleArrayOf(0.0, 0.0, 0.0)
        val to = element.vec("to") ?: from
        val rotation = element.vec("rotation")?.takeIf { it.any { v -> v != 0.0 } }

        return JsonObject().apply {
            add("origin", jsonArrayOf(-to[0], from[1], from[2]))
            add("size", jsonArrayOf(to[0] - from[0], to[1] - from[1], to[2] - from[2]))
            if (rotation != null) {
                val pivot = element.vec("origin") ?: doubleArrayOf(0.0, 0.0, 0.0)
                add("pivot", jsonArrayOf(-pivot[0], pivot[1], pivot[2]))
                add("rotation", jsonArrayOf(-rotation[0], -rotation[1], rotation[2]))
            }
            element.double("inflate")?.takeIf { it != 0.0 }?.let { addProperty("inflate", trim(it)) }
            if (element.bool("box_uv") ?: model.boxUv) {
                if (element.bool("mirror_uv") == true) addProperty("mirror", true)
                val offset = element.vec2("uv_offset") ?: doubleArrayOf(0.0, 0.0)
                add("uv", jsonArrayOf(offset[0], offset[1]))
            } else {
                add("uv", perFaceUv(element))
            }
        }
    }

    private fun perFaceUv(element: JsonObject): JsonObject {
        val faces = element.getAsJsonObject("faces") ?: JsonObject()
        val out = JsonObject()
        FACES.forEach { faceName ->
            val face = faces.getAsJsonObject(faceName) ?: return@forEach
            if (face.int("texture") == null && face.get("texture")?.isJsonNull != false) return@forEach
            val uv = face.getAsJsonArray("uv") ?: return@forEach
            if (uv.size() < 4) return@forEach
            val x1 = uv[0].asDouble
            val y1 = uv[1].asDouble
            val x2 = uv[2].asDouble
            val y2 = uv[3].asDouble
            val width = abs(x2 - x1)
            val height = abs(y2 - y1)
            val entry = JsonObject()
            if (faceName == "down") {
                entry.add("uv", jsonArrayOf(min(x1, x2), max(y1, y2)))
                entry.add("uv_size", jsonArrayOf(width, -height))
            } else {
                entry.add("uv", jsonArrayOf(min(x1, x2), min(y1, y2)))
                entry.add("uv_size", jsonArrayOf(width, height))
            }
            out.add(faceName, entry)
        }
        return out
    }

    private fun visibleBounds(model: BlockbenchModel): DoubleArray {
        var minX = Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var minZ = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        var maxZ = -Double.MAX_VALUE
        var seen = false

        model.elements.mapNotNull { it as? JsonObject }.forEach { element ->
            val from = element.vec("from") ?: return@forEach
            val to = element.vec("to") ?: return@forEach
            seen = true
            minX = min(minX, min(from[0], to[0]))
            minY = min(minY, min(from[1], to[1]))
            minZ = min(minZ, min(from[2], to[2]))
            maxX = max(maxX, max(from[0], to[0]))
            maxY = max(maxY, max(from[1], to[1]))
            maxZ = max(maxZ, max(from[2], to[2]))
        }
        if (!seen) return doubleArrayOf(1.0, 1.0, 0.0)

        val reach = maxOf(abs(minX), abs(maxX), abs(minZ), abs(maxZ))
        val width = ceil(reach * 2.0 / 16.0 * 2.0) / 2.0
        val height = ceil((maxY - minY) / 16.0 * 2.0) / 2.0
        val offset = Math.round((maxY + minY) / 2.0 / 16.0 * 4.0) / 4.0
        return doubleArrayOf(width, height, offset)
    }

    private val FACES = listOf("north", "east", "south", "west", "up", "down")
}
