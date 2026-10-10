package com.nyfaria.moddingtools

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.text.DecimalFormat
import java.time.Instant
import java.time.format.DateTimeFormatter

data class ReleaseFile(val name: String, val size: Long)

data class ReleaseAnnouncement(
    val modName: String,
    val version: String,
    val displayName: String,
    val modAuthor: String,
    val changelog: String,
    val releaseType: String,
    val loaders: List<String>,
    val minecraftVersions: List<String>,
    val javaVersion: String,
    val files: List<ReleaseFile>,
    val links: Map<String, String>,
    val username: String,
    val avatarUrl: String,
    val thumbnailUrl: String,
    val bannerUrl: String,
    val footerText: String,
    val footerIconUrl: String,
    val mentionRoleId: String,
    val color: Int?,
)

object DiscordWebhook {

    private const val NEWLINE = "\n"
    private const val CONTENT_LIMIT = 2000
    private const val TITLE_LIMIT = 256
    private const val DESCRIPTION_LIMIT = 4096
    private const val FIELD_VALUE_LIMIT = 1024
    private const val FOOTER_LIMIT = 2048
    private const val AUTHOR_LIMIT = 256

    fun colorFor(releaseType: String): Int = when (releaseType.lowercase()) {
        "release" -> 0x57F287
        "beta" -> 0xFEE75C
        "alpha" -> 0xED4245
        else -> 0x5865F2
    }

    fun loaderLabel(loader: String): String = when (loader.lowercase()) {
        "fabric" -> "Fabric"
        "neoforge" -> "NeoForge"
        "forge" -> "Forge"
        "quilt" -> "Quilt"
        else -> loader.replaceFirstChar { it.uppercase() }
    }

    private fun badgeFor(releaseType: String): String = when (releaseType.lowercase()) {
        "release" -> "Release"
        "beta" -> "Beta"
        "alpha" -> "Alpha"
        else -> releaseType.replaceFirstChar { it.uppercase() }
    }

    fun humanSize(bytes: Long): String {
        if (bytes <= 0) return "unknown"
        val units = listOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return DecimalFormat("#,##0.#").format(value) + " " + units[unit]
    }

    fun truncate(text: String, limit: Int): String {
        if (text.length <= limit) return text
        val cut = text.take(limit - 3)
        val boundary = cut.lastIndexOf('\n')
        return (if (boundary > limit / 2) cut.take(boundary) else cut).trimEnd() + "..."
    }

    fun build(a: ReleaseAnnouncement): String {
        val payload = JsonObject()
        if (a.username.isNotBlank()) payload.addProperty("username", a.username)
        if (a.avatarUrl.isNotBlank()) payload.addProperty("avatar_url", a.avatarUrl)
        if (a.mentionRoleId.isNotBlank()) {
            payload.addProperty("content", truncate("<@&${a.mentionRoleId}>", CONTENT_LIMIT))
            payload.add("allowed_mentions", JsonObject().apply {
                add("roles", JsonArray().apply { add(a.mentionRoleId) })
            })
        }

        val embed = JsonObject()
        embed.addProperty("title", truncate("${a.modName} ${a.version}", TITLE_LIMIT))
        a.links.values.firstOrNull()?.let { embed.addProperty("url", it) }
        embed.addProperty("color", a.color ?: colorFor(a.releaseType))
        embed.addProperty("timestamp", DateTimeFormatter.ISO_INSTANT.format(Instant.now()))

        val changelog = a.changelog.trim()
        if (changelog.isNotEmpty()) {
            embed.addProperty("description", truncate(changelog, DESCRIPTION_LIMIT))
        }

        if (a.modAuthor.isNotBlank()) {
            embed.add("author", JsonObject().apply {
                addProperty("name", truncate(a.modAuthor, AUTHOR_LIMIT))
                if (a.avatarUrl.isNotBlank()) addProperty("icon_url", a.avatarUrl)
            })
        }
        if (a.thumbnailUrl.isNotBlank()) {
            embed.add("thumbnail", JsonObject().apply { addProperty("url", a.thumbnailUrl) })
        }
        if (a.bannerUrl.isNotBlank()) {
            embed.add("image", JsonObject().apply { addProperty("url", a.bannerUrl) })
        }

        val fields = JsonArray()
        fun field(name: String, value: String, inline: Boolean) {
            if (value.isBlank()) return
            fields.add(JsonObject().apply {
                addProperty("name", name)
                addProperty("value", truncate(value, FIELD_VALUE_LIMIT))
                addProperty("inline", inline)
            })
        }

        field("Minecraft", a.minecraftVersions.joinToString("  ") { "`$it`" }, true)
        field("Loader", a.loaders.joinToString("  •  ") { loaderLabel(it) }, true)
        field("Channel", badgeFor(a.releaseType), true)

        if (a.links.isNotEmpty()) {
            field("Download", a.links.entries.joinToString("  •  ") { "[${it.key}](${it.value})" }, false)
        }
        if (a.files.isNotEmpty()) {
            val label = if (a.files.size == 1) "File" else "Files"
            field(label, a.files.joinToString(NEWLINE) { f ->
                val size = if (f.size > 0) "  `${humanSize(f.size)}`" else ""
                "`${f.name}`$size"
            }, false)
        }
        if (a.javaVersion.isNotBlank()) field("Java", "`${a.javaVersion}`", true)
        if (fields.size() > 0) embed.add("fields", fields)

        if (a.footerText.isNotBlank()) {
            embed.add("footer", JsonObject().apply {
                addProperty("text", truncate(a.footerText, FOOTER_LIMIT))
                if (a.footerIconUrl.isNotBlank()) addProperty("icon_url", a.footerIconUrl)
            })
        }

        payload.add("embeds", JsonArray().apply { add(embed) })
        return payload.toString()
    }

    fun attachmentReference(file: java.io.File): String = "attachment://${file.name}"

    fun send(webhookUrl: String, payload: String, attachment: java.io.File? = null): HttpResult {
        val url = "$webhookUrl?wait=true"
        if (attachment == null || !attachment.isFile) {
            return MultipartHttp.postJson(url, payload)
        }
        return MultipartHttp.postMultipart(
            url = url,
            headers = mapOf("User-Agent" to "NyfsModdingTools"),
            fields = mapOf("payload_json" to payload),
            fileFieldName = "files[0]",
            file = attachment,
            fileContentType = contentTypeFor(attachment.name),
        )
    }

    private fun contentTypeFor(name: String): String = when (name.substringAfterLast('.').lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }
}
