package com.camlite.android.updates

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection

class UpdateTransport {
    private fun open(url: String): HttpsURLConnection {
        var current = url
        repeat(6) {
            if (!UpdateSource.approvedHttps(current)) throw UpdateRejected("Redirecionamento não autorizado.")
            val connection = URI(current).toURL().openConnection() as HttpsURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 12000; connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "CamLiteAndroid-Updater/1")
            connection.setRequestProperty("Accept-Encoding", "identity")
            try {
                val code = connection.responseCode
                if (code in listOf(301,302,303,307,308)) {
                    val location = connection.getHeaderField("Location") ?: throw UpdateRejected("Redirecionamento inválido.")
                    current = URI(current).resolve(location).toString()
                    connection.disconnect()
                } else {
                    if (code != HttpURLConnection.HTTP_OK) throw java.io.IOException("Servidor indisponível")
                    return connection
                }
            } catch (e: Exception) { connection.disconnect(); throw e }
        }
        throw UpdateRejected("Redirecionamentos excessivos.")
    }
    fun read(url: String, limit: Int): ByteArray {
        val c = open(url)
        try {
            if (c.contentLengthLong > limit) throw UpdateRejected("Resposta fora do limite.")
            return c.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) { val n = input.read(buffer); if (n < 0) break; if (output.size() + n > limit) throw UpdateRejected("Resposta fora do limite."); output.write(buffer,0,n) }
                output.toByteArray()
            }
        } finally { c.disconnect() }
    }
    fun download(release: ReleaseManifest, target: File, cancel: AtomicBoolean, progress: (Long) -> Unit) {
        val c = open(release.url)
        try {
            if (c.contentLengthLong >= 0 && c.contentLengthLong != release.size) throw UpdateRejected("Tamanho do download incompatível.")
            c.inputStream.use { VerifiedDownload.copy(it,target,release,cancel,progress) }
        } catch(e:Exception) { target.delete(); throw e } finally { c.disconnect() }
    }
}
