package com.nyfaria.moddingtools

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.jvm.tasks.Jar

class ModDependencyPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val modDeps = ModDependencyHandler(project, project.dependencies)
        project.extensions.add("modDeps", modDeps)

        project.afterEvaluate {
            setupMetadataModification(project)
        }
    }

    private fun setupMetadataModification(project: Project) {
        val trackedDeps = ModDependencyTracker.getDependencies(project.path)
        if (trackedDeps.isEmpty()) {
            return
        }

        val platform = detectPlatform(project)
        if (platform == null) {
            project.logger.info("[ModdingTools] Could not detect platform, skipping metadata modification")
            return
        }

        val theModId = project.findProperty("mod_id")?.toString()
            ?: project.findProperty("modId")?.toString()
            ?: project.name

        val thePlatform = platform
        val theDeps = trackedDeps

        val jarTask = project.tasks.findByName("jar") as? Jar
        if (jarTask != null) {
            val modifyTask = project.tasks.register("modifyModMetadata", ModifyModMetadataTask::class.java)
            modifyTask.configure {
                modId.set(theModId)
                this.platform.set(thePlatform)
                dependencies.set(theDeps)
                jarFile.set(jarTask.archiveFile)
            }
            jarTask.finalizedBy(modifyTask)
        }

        if (thePlatform == "fabric") {
            val remapJarTask = project.tasks.findByName("remapJar") as? AbstractArchiveTask
            if (remapJarTask != null) {
                val remapModifyTask = project.tasks.register("modifyRemappedModMetadata", ModifyModMetadataTask::class.java)
                remapModifyTask.configure {
                    modId.set(theModId)
                    this.platform.set(thePlatform)
                    dependencies.set(theDeps)
                    jarFile.set(remapJarTask.archiveFile)
                }
                remapJarTask.finalizedBy(remapModifyTask)
            }
        }

        if (thePlatform == "neoforge" || thePlatform == "forge") {
            val jarJarTask = project.tasks.findByName("jarJar") as? AbstractArchiveTask
            if (jarJarTask != null) {
                val jarJarModifyTask = project.tasks.register("modifyJarJarModMetadata", ModifyModMetadataTask::class.java)
                jarJarModifyTask.configure {
                    modId.set(theModId)
                    this.platform.set(thePlatform)
                    dependencies.set(theDeps)
                    jarFile.set(jarJarTask.archiveFile)
                }
                jarJarTask.finalizedBy(jarJarModifyTask)
            }
        }
    }

    private fun detectPlatform(project: Project): String? {
        val projectName = project.name.lowercase()
        val projectPath = project.path.lowercase()

        return when {
            projectName.contains("fabric") || projectPath.contains("fabric") -> "fabric"
            projectName.contains("neoforge") || projectPath.contains("neoforge") -> "neoforge"
            projectName.contains("forge") || projectPath.contains("forge") -> "forge"
            project.plugins.hasPlugin("fabric-loom") -> "fabric"
            project.plugins.hasPlugin("net.neoforged.moddev") -> "neoforge"
            project.plugins.hasPlugin("net.minecraftforge.gradle") -> "forge"
            else -> null
        }
    }
}
