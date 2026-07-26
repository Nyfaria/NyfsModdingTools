package com.nyfaria.moddingtools

import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class HttpResult(val code: Int, val body: String) {
    val successful: Boolean get() = code in 200..299
}

object MultipartHttp {

    private const val CRLF = "\r\n"

    fun get(url: String, headers: Map<String, String>): HttpResult {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        return connection.read()
    }

    fun postMultipart(
        url: String,
        headers: Map<String, String>,
        fields: Map<String, String>,
        fileFieldName: String,
        file: File,
        fileContentType: String = "application/java-archive",
    ): HttpResult {
        val boundary = "NyfsModdingTools${System.currentTimeMillis()}"
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 15000
        connection.readTimeout = 120000
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }

        val body = ByteArrayOutputStream()
        fun write(text: String) = body.write(text.toByteArray(Charsets.UTF_8))

        fields.forEach { (name, value) ->
            write("--$boundary$CRLF")
            write("Content-Disposition: form-data; name=\"$name\"$CRLF")
            write("Content-Type: application/json$CRLF$CRLF")
            write(value)
            write(CRLF)
        }

        write("--$boundary$CRLF")
        write("Content-Disposition: form-data; name=\"$fileFieldName\"; filename=\"${file.name}\"$CRLF")
        write("Content-Type: $fileContentType$CRLF$CRLF")
        body.write(file.readBytes())
        write(CRLF)
        write("--$boundary--$CRLF")

        val payload = body.toByteArray()
        connection.setRequestProperty("Content-Length", payload.size.toString())
        connection.outputStream.use { it.write(payload) }
        return connection.read()
    }

    private fun HttpURLConnection.read(): HttpResult {
        val code = responseCode
        val stream = if (code in 200..299) inputStream else errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        return HttpResult(code, text)
    }
}
