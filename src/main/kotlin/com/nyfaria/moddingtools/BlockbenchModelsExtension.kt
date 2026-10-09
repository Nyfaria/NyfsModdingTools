package com.nyfaria.moddingtools

import org.gradle.api.provider.Property

interface BlockbenchModelsExtension {
    val sourceDir: Property<String>
    val modId: Property<String>
    val animations: Property<Boolean>
    val textures: Property<Boolean>
    val textureFolder: Property<String>
    val geckolibVersion: Property<String>
    val modelsFolder: Property<String>
    val animationsFolder: Property<String>
    val strict: Property<Boolean>
}
