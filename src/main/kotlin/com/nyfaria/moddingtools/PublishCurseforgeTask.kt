package com.nyfaria.moddingtools

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Uploads an artifact to a remote service")
abstract class PublishCurseforgeTask : DefaultTask() {

    @get:Input
    abstract val projectId: Property<String>

    @get:Input
    abstract val token: Property<String>

    @get:Input
    abstract val displayName: Property<String>

    @get:Input
    abstract val changelog: Property<String>

    @get:Input
    abstract val changelogType: Property<String>

    @get:Input
    abstract val releaseType: Property<String>

    @get:Input
    abstract val clientSide: Property<Boolean>

    @get:Input
    abstract val serverSide: Property<Boolean>

    @get:Input
    abstract val loader: Property<String>

    @get:Input
    abstract val gameVersions: ListProperty<String>

    @get:Input
    abstract val javaVersions: ListProperty<String>

    @get:Input
    abstract val dryRun: Property<Boolean>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val file: RegularFileProperty

    init {
        group = "modding tools publishing"
        description = "Publishes the mod to CurseForge"
    }

    @TaskAction
    fun publish() {
        val jar = file.get().asFile
        val project = projectId.get()
        if (project.isBlank() || project == "0") {
            throw GradleException("CurseForge project id is not set (curseforge_project_id)")
        }

        val loaderLabel = when (loader.get().lowercase()) {
            "fabric" -> "Fabric"
            "neoforge" -> "NeoForge"
            "forge" -> "Forge"
            "quilt" -> "Quilt"
            else -> loader.get().replaceFirstChar { it.uppercase() }
        }

        val tokenValue = token.get()
        if (tokenValue.isBlank()) {
            if (dryRun.get()) {
                logger.lifecycle("[ModdingTools] (dry run) CurseForge upload of ${jar.name} for $loaderLabel ${gameVersions.get()} (no token set, skipping id resolution)")
                return
            }
            throw GradleException("CurseForge token not set (curseforge_token property or CURSEFORGE_TOKEN env)")
        }

        val versionIds = resolveGameVersionIds(tokenValue, loaderLabel, clientSide.get(), serverSide.get())

        if (dryRun.get()) {
            logger.lifecycle("[ModdingTools] (dry run) CurseForge upload of ${jar.name}: resolved gameVersions=$versionIds")
            return
        }

        if (versionIds.isEmpty()) {
            throw GradleException("CurseForge: could not resolve any game version ids for $loaderLabel ${gameVersions.get()}")
        }

        val metadata = JsonObject().apply {
            addProperty("changelog", changelog.get())
            addProperty("changelogType", changelogType.get())
            addProperty("displayName", displayName.get())
            add("gameVersions", JsonArray().apply { versionIds.forEach { add(it) } })
            addProperty("releaseType", releaseType.get().lowercase())
        }

        val result = MultipartHttp.postMultipart(
            url = "https://minecraft.curseforge.com/api/projects/$project/upload-file",
            headers = mapOf("X-Api-Token" to tokenValue),
            fields = mapOf("metadata" to metadata.toString()),
            fileFieldName = "file",
            file = jar,
        )

        if (!result.successful) {
            throw GradleException("CurseForge upload failed (${result.code}): ${result.body}")
        }
        logger.lifecycle("[ModdingTools] Published ${jar.name} to CurseForge")
    }

    private fun resolveGameVersionIds(tokenValue: String, loaderLabel: String, clientSide: Boolean, serverSide: Boolean): List<Int> {
        val headers = mapOf("X-Api-Token" to tokenValue)

        val typesResponse = MultipartHttp.get("https://minecraft.curseforge.com/api/game/version-types", headers)
        if (!typesResponse.successful) {
            throw GradleException("CurseForge: failed to fetch version types (${typesResponse.code}): ${typesResponse.body}")
        }
        val types = JsonParser.parseString(typesResponse.body).asJsonArray.map { it.asJsonObject }
        val minecraftTypeIds = types.filter { it.get("slug")?.asString?.startsWith("minecraft") == true }.mapNotNull { it.get("id")?.asInt }.toSet()
        val modloaderTypeIds = types.filter { it.get("slug")?.asString == "modloader" }.mapNotNull { it.get("id")?.asInt }.toSet()
        val environmentTypeIds = types.filter { it.get("slug")?.asString == "environment" }.mapNotNull { it.get("id")?.asInt }.toSet()
        val javaTypeIds = types.filter { it.get("slug")?.asString == "java" }.mapNotNull { it.get("id")?.asInt }.toSet()

        val response = MultipartHttp.get("https://minecraft.curseforge.com/api/game/versions", headers)
        if (!response.successful) {
            throw GradleException("CurseForge: failed to fetch game versions (${response.code}): ${response.body}")
        }
        val entries = JsonParser.parseString(response.body).asJsonArray.map { it.asJsonObject }

        fun findId(name: String, typeIds: Set<Int>): Int? = entries.firstOrNull { entry ->
            entry.get("gameVersionTypeID")?.asInt in typeIds && entry.get("name")?.asString.equals(name, ignoreCase = true)
        }?.get("id")?.asInt

        val ids = LinkedHashSet<Int>()

        gameVersions.get().forEach { mc ->
            val id = findId(mc, minecraftTypeIds) ?: throw GradleException("CurseForge: no Minecraft version '$mc' found")
            ids.add(id)
        }

        val loaderId = findId(loaderLabel, modloaderTypeIds) ?: throw GradleException("CurseForge: no modloader '$loaderLabel' found")
        ids.add(loaderId)

        if (clientSide) findId("Client", environmentTypeIds)?.let { ids.add(it) }
        if (serverSide) findId("Server", environmentTypeIds)?.let { ids.add(it) }

        javaVersions.get().forEach { java ->
            findId(normalizeJava(java), javaTypeIds)?.let { ids.add(it) }
        }

        logger.lifecycle("[ModdingTools] CurseForge resolved gameVersion ids: $ids")
        return ids.toList()
    }

    private fun normalizeJava(value: String): String {
        val digits = value.filter { it.isDigit() }
        return if (digits.isEmpty()) value else "Java $digits"
    }
}
