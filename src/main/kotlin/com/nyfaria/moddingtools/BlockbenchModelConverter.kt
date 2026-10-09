package com.nyfaria.moddingtools

import java.util.Base64

data class ConvertedModel(
    val geo: String,
    val geoPath: String,
    val animation: String?,
    val animationPath: String?,
    val texture: ByteArray?,
    val texturePath: String?,
)

data class ConversionOptions(
    val animations: Boolean = true,
    val textures: Boolean = true,
    val textureFolder: String = "",
    val modelsFolder: String = GECKOLIB_5_MODELS,
    val animationsFolder: String = GECKOLIB_5_ANIMATIONS,
) {
    companion object {
        const val GECKOLIB_5_MODELS = "geckolib/models"
        const val GECKOLIB_5_ANIMATIONS = "geckolib/animations"
        const val GECKOLIB_4_MODELS = "geo"
        const val GECKOLIB_4_ANIMATIONS = "animations"

        fun modelsFolderFor(geckolibMajor: Int): String =
            if (geckolibMajor >= 5) GECKOLIB_5_MODELS else GECKOLIB_4_MODELS

        fun animationsFolderFor(geckolibMajor: Int): String =
            if (geckolibMajor >= 5) GECKOLIB_5_ANIMATIONS else GECKOLIB_4_ANIMATIONS

        fun majorVersionOf(version: String?): Int =
            version?.trimStart('v')?.substringBefore('.')?.toIntOrNull() ?: 5
    }
}

object BlockbenchModelConverter {

    fun convert(
        text: String,
        relativePath: String,
        modId: String,
        options: ConversionOptions = ConversionOptions(),
    ): ConversionOutcome {
        val model = try {
            BlockbenchModel.parse(text)
        } catch (e: Exception) {
            return ConversionOutcome.Failed("not valid JSON: ${e.message}")
        }

        if (model.eligibility() == ModelEligibility.NOT_A_GECKOLIB_MODEL) {
            return ConversionOutcome.Ignored(model.modelFormat)
        }

        return try {
            val name = resourceName(relativePath)
            val geo = BlockbenchGeoConverter.convert(model, "geometry.${name.replace('/', '.')}")
            val animation = if (options.animations) BlockbenchAnimationConverter.convert(model) else null
            val texture = if (options.textures) texture(model) else null
            val textureFolder = options.textureFolder.trim('/').let { if (it.isEmpty()) "" else "$it/" }
            val models = options.modelsFolder.trim('/')
            val anims = options.animationsFolder.trim('/')

            ConversionOutcome.Converted(
                ConvertedModel(
                    geo = geo,
                    geoPath = "assets/$modId/$models/$name.geo.json",
                    animation = animation,
                    animationPath = animation?.let { "assets/$modId/$anims/$name.animation.json" },
                    texture = texture,
                    texturePath = texture?.let { "assets/$modId/textures/$textureFolder$name.png" },
                )
            )
        } catch (e: Exception) {
            ConversionOutcome.Failed(e.message ?: e::class.java.simpleName)
        }
    }

    private fun texture(model: BlockbenchModel): ByteArray? {
        val first = model.textures.firstOrNull()?.asJsonObject ?: return null
        val source = first.string("source") ?: return null
        val index = source.indexOf(BASE64_MARKER)
        if (index < 0) return null
        return runCatching {
            Base64.getDecoder().decode(source.substring(index + BASE64_MARKER.length))
        }.getOrNull()
    }

    fun resourceName(relativePath: String): String =
        relativePath.removeSuffix(".bbmodel")
            .replace('\\', '/')
            .lowercase()
            .map { if (it in ALLOWED) it else '_' }
            .joinToString("")

    private const val ALLOWED = "abcdefghijklmnopqrstuvwxyz0123456789_.-/"
    private const val BASE64_MARKER = "base64,"
}
