package com.nyfaria.moddingtools

import com.google.gson.Gson
import com.google.gson.JsonObject

data class LibraryDef(
    val group: String,
    val artifact: String,
    val versionRef: String
)

data class PluginDef(
    val id: String,
    val version: String
)

data class VersionData(
    val versions: Map<String, String>,
    val libraries: Map<String, LibraryDef>,
    val bundles: Map<String, List<String>>,
    val plugins: Map<String, PluginDef>
)

object MinecraftVersions {
    private val gson = Gson()
    private val versionCache = mutableMapOf<String, VersionData>()
    private var baseData: VersionData? = null
    private var availableVersions: Set<String>? = null

    fun getVersionsFor(minecraftVersion: String): VersionData {
        return getVersionsForInternal(minecraftVersion, mutableSetOf())
    }

    private fun getVersionsForInternal(minecraftVersion: String, tried: MutableSet<String>): VersionData {
        versionCache[minecraftVersion]?.let { return it }

        if (minecraftVersion in tried) {
            val base = getBaseData()
            versionCache[minecraftVersion] = base
            return base
        }
        tried.add(minecraftVersion)

        val base = getBaseData()

        val jsonContent = javaClass.getResourceAsStream("/versions/$minecraftVersion.json")
            ?.bufferedReader()
            ?.readText()

        if (jsonContent != null) {
            val versionSpecific = parseVersionJson(jsonContent)
            val merged = mergeVersionData(base, versionSpecific)
            versionCache[minecraftVersion] = merged
            return merged
        }

        val fallback = getSupportedVersions().find {
            it != minecraftVersion && minecraftVersion.startsWith(it.substringBeforeLast('.'))
        } ?: getSupportedVersions().firstOrNull { it !in tried } ?: return base

        return getVersionsForInternal(fallback, tried)
    }

    private fun getBaseData(): VersionData {
        baseData?.let { return it }

        val baseJson = javaClass.getResourceAsStream("/versions/_base.json")
            ?.bufferedReader()
            ?.readText()

        val data = if (baseJson != null) {
            parseVersionJson(baseJson)
        } else {
            VersionData(emptyMap(), emptyMap(), emptyMap(), emptyMap())
        }

        baseData = data
        return data
    }

    private fun mergeVersionData(base: VersionData, override: VersionData): VersionData {
        return VersionData(
            versions = base.versions + override.versions,
            libraries = base.libraries + override.libraries,
            bundles = base.bundles + override.bundles,
            plugins = base.plugins + override.plugins
        )
    }

    private fun parseVersionJson(json: String): VersionData {
        val obj = gson.fromJson(json, JsonObject::class.java)

        val versions = mutableMapOf<String, String>()
        obj.getAsJsonObject("versions")?.entrySet()?.forEach { (key, value) ->
            versions[key] = value.asString
        }

        val libraries = mutableMapOf<String, LibraryDef>()
        obj.getAsJsonObject("libraries")?.entrySet()?.forEach { (key, value) ->
            val libObj = value.asJsonObject
            libraries[key] = LibraryDef(
                group = libObj.get("group").asString,
                artifact = libObj.get("artifact").asString,
                versionRef = libObj.get("version").asString
            )
        }

        val bundles = mutableMapOf<String, List<String>>()
        obj.getAsJsonObject("bundles")?.entrySet()?.forEach { (key, value) ->
            bundles[key] = value.asJsonArray.map { it.asString }
        }

        val plugins = mutableMapOf<String, PluginDef>()
        obj.getAsJsonObject("plugins")?.entrySet()?.forEach { (key, value) ->
            val pluginObj = value.asJsonObject
            plugins[key] = PluginDef(
                id = pluginObj.get("id").asString,
                version = pluginObj.get("version").asString
            )
        }

        return VersionData(
            versions = versions,
            libraries = libraries,
            bundles = bundles,
            plugins = plugins
        )
    }

    fun getSupportedVersions(): Set<String> {
        availableVersions?.let { return it }

        val versions = mutableSetOf<String>()
        val indexContent = javaClass.getResourceAsStream("/versions/index.txt")
            ?.bufferedReader()
            ?.readLines()

        indexContent?.forEach { line ->
            if (line.isNotBlank() && !line.startsWith("_")) {
                versions.add(line.trim())
            }
        }

        availableVersions = versions
        return versions
    }
}
