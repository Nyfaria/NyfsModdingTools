package com.nyfaria.moddingtools

import com.google.gson.JsonObject
import java.io.File

object PublishResults {

    fun write(target: File?, label: String, url: String) {
        val file = target ?: return
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(JsonObject().apply {
                addProperty("label", label)
                addProperty("url", url)
            }.toString())
        }
    }
}
