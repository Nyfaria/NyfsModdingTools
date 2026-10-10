package com.nyfaria.moddingtools

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Posts an announcement to a remote service")
abstract class PublishDiscordTask : DefaultTask() {

    @get:Input
    abstract val webhookUrl: Property<String>

    @get:Input
    abstract val modName: Property<String>

    @get:Input
    abstract val modAuthor: Property<String>

    @get:Input
    abstract val version: Property<String>

    @get:Input
    abstract val displayName: Property<String>

    @get:Input
    abstract val changelog: Property<String>

    @get:Input
    abstract val releaseType: Property<String>

    @get:Input
    abstract val loaders: ListProperty<String>

    @get:Input
    abstract val minecraftVersions: ListProperty<String>

    @get:Input
    abstract val javaVersion: Property<String>

    @get:Input
    abstract val username: Property<String>

    @get:Input
    abstract val avatarUrl: Property<String>

    @get:Input
    abstract val thumbnailUrl: Property<String>

    @get:Input
    abstract val bannerUrl: Property<String>

    @get:Input
    abstract val footerText: Property<String>

    @get:Input
    abstract val footerIconUrl: Property<String>

    @get:Input
    abstract val mentionRoleId: Property<String>

    @get:Input
    @get:Optional
    abstract val color: Property<Int>

    @get:Input
    abstract val dryRun: Property<Boolean>

    @get:org.gradle.api.tasks.InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val files: org.gradle.api.file.ConfigurableFileCollection

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val iconFile: RegularFileProperty

    @get:org.gradle.api.tasks.Internal
    abstract val resultDirs: org.gradle.api.file.ConfigurableFileCollection

    init {
        group = "modding tools publishing"
        description = "Announces the release to a Discord webhook"
    }

    @TaskAction
    fun announce() {
        val jars = files.files.filter { it.isFile }.sortedBy { it.name }
        val icon = iconFile.orNull?.asFile?.takeIf { it.isFile }
        val thumbnail = when {
            icon != null -> DiscordWebhook.attachmentReference(icon)
            else -> thumbnailUrl.get()
        }
        val announcement = ReleaseAnnouncement(
            modName = modName.get(),
            version = version.get(),
            displayName = displayName.get(),
            modAuthor = modAuthor.get(),
            changelog = changelog.get(),
            releaseType = releaseType.get(),
            loaders = loaders.get(),
            minecraftVersions = minecraftVersions.get(),
            javaVersion = javaVersion.get(),
            files = jars.map { ReleaseFile(it.name, it.length()) },
            links = collectLinks(),
            username = username.get(),
            avatarUrl = avatarUrl.get(),
            thumbnailUrl = thumbnail,
            bannerUrl = bannerUrl.get(),
            footerText = footerText.get(),
            footerIconUrl = footerIconUrl.get(),
            mentionRoleId = mentionRoleId.get(),
            color = color.orNull,
        )

        val payload = DiscordWebhook.build(announcement)

        if (dryRun.get()) {
            val attached = icon?.let { " (+ icon ${it.name})" } ?: ""
            logger.lifecycle("[ModdingTools] (dry run) Discord announcement payload$attached:\n$payload")
            return
        }

        val url = webhookUrl.get()
        if (url.isBlank()) {
            throw GradleException("Discord webhook URL not set (discord_webhook_url property or DISCORD_WEBHOOK_URL env)")
        }

        val result = DiscordWebhook.send(url, payload, icon)
        if (!result.successful) {
            throw GradleException("Discord announcement failed (${result.code}): ${result.body}")
        }
        logger.lifecycle("[ModdingTools] Announced ${announcement.modName} ${announcement.version} to Discord")
    }

    private fun collectLinks(): Map<String, String> {
        val links = LinkedHashMap<String, String>()
        resultDirs.files
            .filter { it.isDirectory }
            .flatMap { it.listFiles()?.toList() ?: emptyList() }
            .sortedBy { it.name }
            .forEach { file ->
                runCatching {
                    val json = JsonParser.parseString(file.readText()) as? JsonObject ?: return@runCatching
                    val label = json.string("label") ?: return@runCatching
                    val url = json.string("url") ?: return@runCatching
                    links[label] = url
                }
            }
        return links
    }
}
