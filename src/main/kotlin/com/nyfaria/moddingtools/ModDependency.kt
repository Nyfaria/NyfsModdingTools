package com.nyfaria.moddingtools

import java.io.Serializable

data class ModDependency(
    val modId: String,
    val version: String,
    val type: DependencyType
) : Serializable

enum class DependencyType {
    REQUIRED,
    OPTIONAL,
    EMBEDDED
}
