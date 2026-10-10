package com.nyfaria.moddingtools

import org.gradle.api.Action
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested

abstract class PlatformPublishOptions {
    abstract val enabled: Property<Boolean>
    abstract val projectId: Property<String>
    abstract val token: Property<String>
}

abstract class DiscordPublishOptions {
    abstract val enabled: Property<Boolean>
    abstract val webhookUrl: Property<String>
    abstract val username: Property<String>
    abstract val avatarUrl: Property<String>
    abstract val thumbnailUrl: Property<String>
    abstract val iconFile: org.gradle.api.file.RegularFileProperty
    abstract val bannerUrl: Property<String>
    abstract val footerText: Property<String>
    abstract val footerIconUrl: Property<String>
    abstract val mentionRoleId: Property<String>
    abstract val color: Property<Int>
}

abstract class ModPublishingExtension {

    abstract val displayName: Property<String>
    abstract val version: Property<String>
    abstract val changelog: Property<String>
    abstract val changelogType: Property<String>
    abstract val type: Property<String>
    abstract val file: RegularFileProperty
    abstract val loaders: ListProperty<String>
    abstract val minecraftVersions: ListProperty<String>
    abstract val javaVersions: ListProperty<String>
    abstract val clientSide: Property<Boolean>
    abstract val serverSide: Property<Boolean>
    abstract val featured: Property<Boolean>
    abstract val dryRun: Property<Boolean>

    @get:Nested
    abstract val curseforge: PlatformPublishOptions

    @get:Nested
    abstract val modrinth: PlatformPublishOptions

    @get:Nested
    abstract val discord: DiscordPublishOptions

    fun curseforge(action: Action<PlatformPublishOptions>) = action.execute(curseforge)

    fun modrinth(action: Action<PlatformPublishOptions>) = action.execute(modrinth)

    fun discord(action: Action<DiscordPublishOptions>) = action.execute(discord)
}
