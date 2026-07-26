package com.nyfaria.moddingtools

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.bundling.AbstractArchiveTask

class ModPublishingPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val ext = project.extensions.create("modPublishing", ModPublishingExtension::class.java)

        val platform = detectPlatform(project) ?: project.name
        val loaderLabel = when (platform) {
            "fabric" -> "Fabric"
            "neoforge" -> "NeoForge"
            "forge" -> "Forge"
            else -> platform.replaceFirstChar { it.uppercase() }
        }

        fun prop(name: String): String? = project.findProperty(name)?.toString()
        val modName = prop("mod_name") ?: prop("mod_id") ?: project.name
        val mcVersion = prop("minecraft_version") ?: ""

        ext.type.convention("release")
        ext.changelogType.convention("markdown")
        ext.dryRun.convention((prop("dry_run") ?: "false").toBoolean())
        ext.loaders.convention(listOf(platform))
        ext.minecraftVersions.convention(if (mcVersion.isNotBlank()) listOf(mcVersion) else emptyList())
        ext.javaVersions.convention(emptyList())
        ext.clientSide.convention(true)
        ext.serverSide.convention(true)
        ext.featured.convention(false)
        ext.version.convention(project.provider { project.version.toString() })
        ext.displayName.convention(project.provider { "$modName-$mcVersion-[$loaderLabel]-${project.version}" })

        val changelogFile = project.rootProject.file("common/changelog.md")
        ext.changelog.convention(project.provider { if (changelogFile.exists()) changelogFile.readText() else "" })

        ext.curseforge.enabled.convention((prop("curseforge_publishing_enabled") ?: "false").toBoolean())
        ext.curseforge.projectId.convention(prop("curseforge_project_id") ?: "")
        ext.curseforge.token.convention(tokenProvider(project, "curseforge_token", "CURSEFORGE_TOKEN"))

        ext.modrinth.enabled.convention((prop("modrinth_publishing_enabled") ?: "false").toBoolean())
        ext.modrinth.projectId.convention(prop("modrinth_project_id") ?: "")
        ext.modrinth.token.convention(tokenProvider(project, "modrinth_token", "MODRINTH_TOKEN"))

        project.afterEvaluate {
            val jarTaskName = if (platform == "fabric") "remapJar" else "jar"
            val jarTask = project.tasks.findByName(jarTaskName) as? AbstractArchiveTask
            if (jarTask != null) {
                ext.file.convention(jarTask.archiveFile)
            }

            val curseTask = project.tasks.register("publishCurseforge", PublishCurseforgeTask::class.java)
            curseTask.configure {
                projectId.set(ext.curseforge.projectId)
                token.set(ext.curseforge.token)
                displayName.set(ext.displayName)
                changelog.set(ext.changelog)
                changelogType.set(ext.changelogType)
                releaseType.set(ext.type)
                loader.set(platform)
                gameVersions.set(ext.minecraftVersions)
                javaVersions.set(ext.javaVersions)
                clientSide.set(ext.clientSide)
                serverSide.set(ext.serverSide)
                dryRun.set(ext.dryRun)
                file.set(ext.file)
                onlyIf { ext.curseforge.enabled.get() }
                jarTask?.let { dependsOn(it) }
            }

            val modrinthTask = project.tasks.register("publishModrinth", PublishModrinthTask::class.java)
            modrinthTask.configure {
                projectId.set(ext.modrinth.projectId)
                token.set(ext.modrinth.token)
                versionNumber.set(ext.version)
                displayName.set(ext.displayName)
                changelog.set(ext.changelog)
                versionType.set(ext.type)
                loaders.set(ext.loaders)
                gameVersions.set(ext.minecraftVersions)
                featured.set(ext.featured)
                dryRun.set(ext.dryRun)
                file.set(ext.file)
                onlyIf { ext.modrinth.enabled.get() }
                jarTask?.let { dependsOn(it) }
            }

            val publishMod = project.tasks.register("publishMod")
            publishMod.configure {
                group = "modding tools publishing"
                description = "Publishes the mod to all enabled platforms"
                dependsOn(curseTask, modrinthTask)
            }
        }
    }

    private fun tokenProvider(project: Project, propertyName: String, envName: String): Provider<String> =
        project.providers.gradleProperty(propertyName)
            .orElse(project.providers.environmentVariable(envName))
            .orElse("")

    private fun detectPlatform(project: Project): String? {
        val name = project.name.lowercase()
        val path = project.path.lowercase()
        return when {
            name.contains("fabric") || path.contains("fabric") -> "fabric"
            name.contains("neoforge") || path.contains("neoforge") -> "neoforge"
            name.contains("forge") || path.contains("forge") -> "forge"
            project.plugins.hasPlugin("fabric-loom") -> "fabric"
            project.plugins.hasPlugin("net.neoforged.moddev") -> "neoforge"
            project.plugins.hasPlugin("net.neoforged.moddev.legacyforge") -> "forge"
            else -> null
        }
    }
}
