package com.nyfaria.moddingtools

import com.google.gson.JsonArray
import com.google.gson.JsonObject
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
abstract class PublishModrinthTask : DefaultTask() {

    @get:Input
    abstract val projectId: Property<String>

    @get:Input
    abstract val token: Property<String>

    @get:Input
    abstract val versionNumber: Property<String>

    @get:Input
    abstract val displayName: Property<String>

    @get:Input
    abstract val changelog: Property<String>

    @get:Input
    abstract val versionType: Property<String>

    @get:Input
    abstract val loaders: ListProperty<String>

    @get:Input
    abstract val gameVersions: ListProperty<String>

    @get:Input
    abstract val featured: Property<Boolean>

    @get:Input
    abstract val dryRun: Property<Boolean>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val file: RegularFileProperty

    init {
        group = "modding tools publishing"
        description = "Publishes the mod to Modrinth"
    }

    @TaskAction
    fun publish() {
        val jar = file.get().asFile
        if (projectId.get().isBlank()) {
            throw GradleException("Modrinth project id is not set (modrinth_project_id)")
        }
        if (gameVersions.get().isEmpty()) {
            throw GradleException("Modrinth publish needs at least one game version (minecraft_version)")
        }

        val data = JsonObject().apply {
            addProperty("name", displayName.get())
            addProperty("version_number", versionNumber.get())
            addProperty("changelog", changelog.get())
            add("dependencies", JsonArray())
            add("game_versions", gameVersions.get().toJsonArray())
            addProperty("version_type", versionType.get().lowercase())
            add("loaders", loaders.get().map { it.lowercase() }.toJsonArray())
            addProperty("featured", featured.get())
            addProperty("project_id", projectId.get())
            add("file_parts", listOf("file").toJsonArray())
            addProperty("primary_file", "file")
        }

        if (dryRun.get()) {
            logger.lifecycle("[ModdingTools] (dry run) Modrinth upload of ${jar.name}\n$data")
            return
        }

        val tokenValue = token.get()
        if (tokenValue.isBlank()) {
            throw GradleException("Modrinth token not set (modrinth_token property or MODRINTH_TOKEN env)")
        }

        val result = MultipartHttp.postMultipart(
            url = "https://api.modrinth.com/v2/version",
            headers = mapOf(
                "Authorization" to tokenValue,
                "User-Agent" to "NyfsModdingTools",
            ),
            fields = mapOf("data" to data.toString()),
            fileFieldName = "file",
            file = jar,
        )

        if (!result.successful) {
            throw GradleException("Modrinth upload failed (${result.code}): ${result.body}")
        }
        logger.lifecycle("[ModdingTools] Published ${jar.name} to Modrinth")
    }

    private fun List<String>.toJsonArray(): JsonArray {
        val array = JsonArray()
        forEach { array.add(it) }
        return array
    }
}
