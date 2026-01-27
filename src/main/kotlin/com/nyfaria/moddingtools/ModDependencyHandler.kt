package com.nyfaria.moddingtools

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.provider.Provider
import java.util.concurrent.ConcurrentHashMap

object ModDependencyTracker {
    private val projectDependencies = ConcurrentHashMap<String, MutableList<ModDependency>>()

    fun addDependency(projectPath: String, dependency: ModDependency) {
        projectDependencies.getOrPut(projectPath) { mutableListOf() }.add(dependency)
    }

    fun getDependencies(projectPath: String): List<ModDependency> {
        return projectDependencies[projectPath]?.toList() ?: emptyList()
    }

    fun clear(projectPath: String) {
        projectDependencies.remove(projectPath)
    }
}

open class ModDependencyHandler(
    private val project: Project,
    private val dependencyHandler: DependencyHandler
) {
    fun requiredMod(dependencyNotation: Any): Dependency? {
        return requiredMod(dependencyNotation, null, null)
    }

    fun requiredMod(dependencyNotation: Any, modId: String?, version: String?): Dependency? {
        return addModDependency(dependencyNotation, modId, version, DependencyType.REQUIRED, "implementation")
    }

    fun optionalMod(dependencyNotation: Any): Dependency? {
        return optionalMod(dependencyNotation, null, null)
    }

    fun optionalMod(dependencyNotation: Any, modId: String?, version: String?): Dependency? {
        return addModDependency(dependencyNotation, modId, version, DependencyType.OPTIONAL, "implementation")
    }

    fun embeddedMod(dependencyNotation: Any): Dependency? {
        return embeddedMod(dependencyNotation, null, null)
    }

    fun embeddedMod(dependencyNotation: Any, modId: String?, version: String?): Dependency? {
        return addModDependency(dependencyNotation, modId, version, DependencyType.EMBEDDED, "implementation")
    }

    private fun addModDependency(
        dependencyNotation: Any,
        modId: String?,
        version: String?,
        type: DependencyType,
        configuration: String?
    ): Dependency? {
        val dep = resolveDependency(dependencyNotation)

        if (dep != null) {
            val resolvedModId = modId ?: extractModId(dep)
            val resolvedVersion = version ?: extractVersion(dep)

            if (resolvedModId != null && resolvedVersion != null) {
                ModDependencyTracker.addDependency(project.path, ModDependency(resolvedModId, resolvedVersion, type))
            }

            if (configuration != null) {
                try {
                    dependencyHandler.add(configuration, dep)
                } catch (e: Exception) {
                    project.logger.warn("[ModdingTools] Could not add to $configuration: ${e.message}")
                }
            }

            if (type == DependencyType.EMBEDDED) {
                addToEmbedConfiguration(dep)
            }
        }

        return dep
    }

    private fun resolveDependency(dependencyNotation: Any): Dependency? {
        return when (dependencyNotation) {
            is Dependency -> dependencyNotation
            is Provider<*> -> {
                val provided = dependencyNotation.orNull
                when (provided) {
                    is MinimalExternalModuleDependency -> dependencyHandler.create(provided)
                    is Dependency -> provided
                    else -> try {
                        dependencyHandler.create(dependencyNotation)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            is String -> dependencyHandler.create(dependencyNotation)
            else -> try {
                dependencyHandler.create(dependencyNotation)
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun extractModId(dep: Dependency): String? {
        val name = dep.name.lowercase()
        val cleaned = name
            .replace("-fabric", "")
            .replace("-forge", "")
            .replace("-neoforge", "")
            .replace("_fabric", "")
            .replace("_forge", "")
            .replace("_neoforge", "")
            .replace("-common", "")
            .replace("_common", "")
            .replace("-", "_")
        return cleaned.ifEmpty { null }
    }

    private fun extractVersion(dep: Dependency): String? {
        val version = dep.version ?: return null
        val versionMatch = Regex("^[0-9]+\\.[0-9]+\\.?[0-9]*").find(version)
        return versionMatch?.value ?: version.split("-").firstOrNull() ?: version.split("+").firstOrNull() ?: version
    }

    private fun addToEmbedConfiguration(dep: Dependency) {
        try { dependencyHandler.add("include", dep); return } catch (e: Exception) { }
        try { dependencyHandler.add("jarJar", dep); return } catch (e: Exception) { }
    }
}
