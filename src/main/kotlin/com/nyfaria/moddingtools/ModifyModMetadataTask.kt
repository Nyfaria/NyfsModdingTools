package com.nyfaria.moddingtools

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

abstract class ModifyModMetadataTask : DefaultTask() {

    @get:InputFile
    abstract val jarFile: RegularFileProperty

    @get:Input
    abstract val modId: Property<String>

    @get:Input
    abstract val dependencies: ListProperty<ModDependency>

    @get:Input
    abstract val platform: Property<String>

    init {
        group = "modding tools"
        description = "Modifies mod metadata files in the jar to add dependency information"
    }

    @TaskAction
    fun modifyMetadata() {
        val jar = jarFile.get().asFile
        val deps = dependencies.get()
        val modIdValue = modId.get()
        val platformValue = platform.get()

        if (deps.isEmpty()) {
            logger.lifecycle("[ModdingTools] No mod dependencies to add")
            return
        }

        if (!jar.exists()) {
            logger.warn("[ModdingTools] Jar file does not exist: ${jar.absolutePath}")
            return
        }

        val tempJar = File(jar.parentFile, "${jar.nameWithoutExtension}-temp.${jar.extension}")

        ZipFile(jar).use { zipIn ->
            ZipOutputStream(FileOutputStream(tempJar)).use { zipOut ->
                val entries = zipIn.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val content = zipIn.getInputStream(entry).readBytes()

                    val modifiedContent = when {
                        platformValue == "fabric" && entry.name == "fabric.mod.json" -> {
                            modifyFabricModJson(content, deps)
                        }
                        platformValue == "forge" && entry.name == "META-INF/mods.toml" -> {
                            modifyForgeModsToml(content, modIdValue, deps)
                        }
                        platformValue == "neoforge" && entry.name == "META-INF/neoforge.mods.toml" -> {
                            modifyNeoForgeModsToml(content, modIdValue, deps)
                        }
                        platformValue == "neoforge" && entry.name == "META-INF/mods.toml" -> {
                            modifyForgeModsToml(content, modIdValue, deps)
                        }
                        else -> content
                    }

                    val newEntry = ZipEntry(entry.name)
                    zipOut.putNextEntry(newEntry)
                    zipOut.write(modifiedContent)
                    zipOut.closeEntry()
                }
            }
        }

        jar.delete()
        tempJar.renameTo(jar)

        logger.lifecycle("[ModdingTools] Modified mod metadata with ${deps.size} dependencies for $platformValue")
    }

    private fun modifyFabricModJson(content: ByteArray, deps: List<ModDependency>): ByteArray {
        val json = JsonParser.parseString(String(content)).asJsonObject

        val depends = if (json.has("depends") && json.get("depends").isJsonObject) {
            json.getAsJsonObject("depends")
        } else {
            JsonObject().also { json.add("depends", it) }
        }

        val recommends = if (json.has("recommends") && json.get("recommends").isJsonObject) {
            json.getAsJsonObject("recommends")
        } else {
            JsonObject().also { json.add("recommends", it) }
        }

        for (dep in deps) {
            val versionConstraint = ">=${dep.version}"
            when (dep.type) {
                DependencyType.REQUIRED, DependencyType.EMBEDDED -> {
                    depends.addProperty(dep.modId, versionConstraint)
                }
                DependencyType.OPTIONAL -> {
                    recommends.addProperty(dep.modId, versionConstraint)
                }
            }
        }

        val gson = GsonBuilder().setPrettyPrinting().create()
        return gson.toJson(json).toByteArray()
    }

    private fun modifyForgeModsToml(content: ByteArray, modIdValue: String, deps: List<ModDependency>): ByteArray {
        val text = String(content)
        val sb = StringBuilder(text)

        for (dep in deps) {
            val mandatory = when (dep.type) {
                DependencyType.REQUIRED, DependencyType.EMBEDDED -> true
                DependencyType.OPTIONAL -> false
            }

            sb.append("\n\n[[dependencies.$modIdValue]]\n")
            sb.append("modId=\"${dep.modId}\"\n")
            sb.append("mandatory=$mandatory\n")
            sb.append("versionRange=\"[${dep.version},)\"\n")
            sb.append("ordering=\"NONE\"\n")
            sb.append("side=\"BOTH\"")
        }

        return sb.toString().toByteArray()
    }

    private fun modifyNeoForgeModsToml(content: ByteArray, modIdValue: String, deps: List<ModDependency>): ByteArray {
        val text = String(content)
        val sb = StringBuilder(text)

        for (dep in deps) {
            val depType = when (dep.type) {
                DependencyType.REQUIRED, DependencyType.EMBEDDED -> "required"
                DependencyType.OPTIONAL -> "optional"
            }

            sb.append("\n\n[[dependencies.$modIdValue]]\n")
            sb.append("modId=\"${dep.modId}\"\n")
            sb.append("type=\"$depType\"\n")
            sb.append("versionRange=\"[${dep.version},)\"\n")
            sb.append("ordering=\"NONE\"\n")
            sb.append("side=\"BOTH\"")
        }

        return sb.toString().toByteArray()
    }
}
