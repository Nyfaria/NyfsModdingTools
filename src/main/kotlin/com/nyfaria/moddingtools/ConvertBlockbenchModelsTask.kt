package com.nyfaria.moddingtools

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import java.io.File

@CacheableTask
abstract class ConvertBlockbenchModelsTask : DefaultTask() {

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:Input
    abstract val sourceRoot: Property<String>

    @get:Input
    abstract val modId: Property<String>

    @get:Input
    abstract val animations: Property<Boolean>

    @get:Input
    abstract val textures: Property<Boolean>

    @get:Input
    abstract val textureFolder: Property<String>

    @get:Input
    abstract val modelsFolder: Property<String>

    @get:Input
    abstract val animationsFolder: Property<String>

    @get:Input
    abstract val strict: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    init {
        group = "modding tools"
        description = "Converts Blockbench models into GeckoLib resources"
    }

    @TaskAction
    fun convert() {
        val output = outputDir.get().asFile
        output.deleteRecursively()
        output.mkdirs()

        val root = File(sourceRoot.get())
        val options = ConversionOptions(
            animations = animations.get(),
            textures = textures.get(),
            textureFolder = textureFolder.get(),
            modelsFolder = modelsFolder.get(),
            animationsFolder = animationsFolder.get(),
        )

        var converted = 0
        var ignored = 0
        val failed = mutableListOf<String>()

        sourceFiles.files.sortedBy { it.path }.forEach { file ->
            val relative = file.relativeTo(root).path
            when (val outcome = BlockbenchModelConverter.convert(file.readText(), relative, modId.get(), options)) {
                is ConversionOutcome.Converted -> {
                    write(output, outcome.model)
                    converted++
                }

                is ConversionOutcome.Ignored -> ignored++

                is ConversionOutcome.Failed -> {
                    failed.add("$relative (${outcome.reason})")
                    logger.warn("[ModdingTools] skipped $relative: ${outcome.reason}")
                }
            }
        }

        val summary = "[ModdingTools] blockbench: converted $converted, skipped ${failed.size}, " +
            "ignored $ignored non-GeckoLib model(s)"
        if (failed.isEmpty()) {
            logger.lifecycle(summary)
        } else {
            logger.warn("$summary -> ${failed.joinToString(", ")}")
            if (strict.get()) {
                throw GradleException("Blockbench conversion failed for ${failed.size} model(s): ${failed.joinToString(", ")}")
            }
        }
    }

    private fun write(output: File, model: ConvertedModel) {
        File(output, model.geoPath).apply { parentFile.mkdirs() }.writeText(model.geo)
        model.animation?.let { File(output, model.animationPath!!).apply { parentFile.mkdirs() }.writeText(it) }
        model.texture?.let { File(output, model.texturePath!!).apply { parentFile.mkdirs() }.writeBytes(it) }
    }
}
