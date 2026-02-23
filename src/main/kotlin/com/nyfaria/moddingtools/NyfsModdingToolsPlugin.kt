package com.nyfaria.moddingtools

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.dsl.VersionCatalogBuilder

class NyfsModdingToolsPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        val extension = settings.extensions.create("nyfsModdingTools", NyfsModdingToolsExtension::class.java)

        extension.clearCache.convention(false)
        extension.versionsUrl.convention(MinecraftVersions.DEFAULT_VERSIONS_URL)

        settings.gradle.settingsEvaluated {
            if (extension.clearCache.get()) {
                MinecraftVersions.clearCache()
            }

            val customUrl = extension.versionsUrl.orNull
            if (customUrl != null && customUrl != MinecraftVersions.DEFAULT_VERSIONS_URL) {
                MinecraftVersions.setVersionsUrl(customUrl)
            }
        }

        val minecraftVersion = settings.providers
            .gradleProperty("minecraft_version")
            .orElse(settings.providers.gradleProperty("minecraftVersion"))
            .orNull ?: "1.20.1"

        configurePluginRepositories(settings)

        settings.dependencyResolutionManagement {
            versionCatalogs {
                create("nyfs") {
                    applyVersionsForMinecraft(minecraftVersion, this)
                }
            }
            repositories {
                configureModdingRepositories()
            }
        }

        settings.gradle.beforeProject(object : Action<Project> {
            override fun execute(project: Project) {
                project.repositories.configureModdingRepositories()
                if (project == project.rootProject) {
                    AutoPackageSync.syncIfNeeded(project)
                }
            }
        })
    }

    private fun isIdeSync(settings: Settings): Boolean {
        val taskNames = settings.gradle.startParameter.taskNames

        if (taskNames.any { it.contains("ideSync", ignoreCase = true) ||
                           it.contains("prepareKotlinBuildScriptModel", ignoreCase = true) ||
                           it.contains("Sync", ignoreCase = true) }) {
            return true
        }

        if (System.getProperty("idea.sync.active") == "true" ||
            System.getProperty("idea.version") != null) {
            return true
        }

        return System.getenv("IDEA_INITIAL_DIRECTORY") != null ||
               System.getenv("__INTELLIJ_COMMAND_HISTFILE__") != null
    }



    private fun configurePluginRepositories(settings: Settings) {
        settings.pluginManagement {
            repositories {
                gradlePluginPortal()
                maven { url = java.net.URI("https://maven.fabricmc.net/") }
                maven { url = java.net.URI("https://maven.neoforged.net/releases") }
                maven { url = java.net.URI("https://maven.100media.dev/") }
            }
        }
    }

    private fun org.gradle.api.artifacts.dsl.RepositoryHandler.configureModdingRepositories() {
        mavenCentral()
        maven { url = java.net.URI("https://maven.100media.dev/") }
        maven { url = java.net.URI("https://maven.neoforged.net/releases") }
        maven { url = java.net.URI("https://maven.fabricmc.net/") }
        maven { url = java.net.URI("https://maven.parchmentmc.org/") }
        maven { url = java.net.URI("https://maven.shedaniel.me/") }
        maven { url = java.net.URI("https://maven.terraformersmc.com/") }
        maven { url = java.net.URI("https://maven.ladysnake.org/releases") }
        maven { url = java.net.URI("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/") }
        maven { url = java.net.URI("https://dl.cloudsmith.io/public/tslat/sbl/maven/") }
        maven { url = java.net.URI("https://maven.kosmx.dev/") }
        maven { url = java.net.URI("https://maven.blamejared.com/") }
        maven { url = java.net.URI("https://api.modrinth.com/maven") }
        maven { url = java.net.URI("https://cursemaven.com") }
    }

    private fun applyVersionsForMinecraft(minecraftVersion: String, catalog: VersionCatalogBuilder) {
        val data = MinecraftVersions.getVersionsFor(minecraftVersion)

        data.versions.forEach { (key, value) ->
            catalog.version(key.replace("_", "-"), value)
        }

        data.libraries.forEach { (alias, lib) ->
            catalog.library(alias, lib.group, lib.artifact).versionRef(lib.versionRef.replace("_", "-"))
        }

        data.bundles.forEach { (alias, libs) ->
            catalog.bundle(alias, libs)
        }

        data.plugins.forEach { (alias, plugin) ->
            if (plugin.version.isEmpty()) {
                catalog.plugin(alias, plugin.id).version("")
            } else if (data.versions.containsKey(plugin.version) || data.versions.containsKey(plugin.version.replace("-", "_"))) {
                catalog.plugin(alias, plugin.id).versionRef(plugin.version.replace("_", "-"))
            } else {
                catalog.plugin(alias, plugin.id).version(plugin.version)
            }
        }
    }
}
