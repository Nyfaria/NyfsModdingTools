package com.nyfaria.moddingtools

import org.gradle.api.provider.Property

interface NyfsModdingToolsExtension {
    val clearCache: Property<Boolean>
    val versionsUrl: Property<String>
}

