package com.camlite.android.updates

import org.json.JSONObject
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64

object UpdateSource {
    const val REPOSITORY = "Pr4Tes/cam-lite-android-releases"
    const val MANIFEST_URL = "https://github.com/$REPOSITORY/releases/latest/download/version.json"
    const val APK_SIGNER = "9e7287463942b2d3a9e836a6273811f8ba20dfe74ee35d9e86109385636d193d"
    const val KEY_ID = "prates-updates-2026"
    const val MAX_APK_SIZE = 64L * 1024 * 1024
    const val DAY_MS = 86400000L
    fun approvedHttps(url: String): Boolean = try {
        val uri = URI(url)
        uri.scheme == "https" && uri.rawUserInfo == null && uri.rawFragment == null &&
            uri.port in listOf(-1, 443) && uri.host in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com") &&
            (uri.host != "github.com" || uri.path.startsWith("/$REPOSITORY/releases/"))
    } catch (_: Exception) { false }
    fun officialApk(url: String): Boolean = approvedHttps(url) && try {
        val uri = URI(url)
        uri.host == "github.com" && uri.rawQuery == null && uri.path.startsWith("/$REPOSITORY/releases/download/") &&
            uri.path.endsWith(".apk") && !uri.path.contains("/../") && !uri.path.contains("/./")
    } catch (_: Exception) { false }
}

class UpdateRejected(message: String) : Exception(message)

data class ReleaseManifest(val version: String, val code: Long, val minSdk: Int, val architectures: Set<String>,
    val url: String, val size: Long, val sha256: String, val notes: String, val published: Instant, val expires: Instant)

class UpdateProtocol(private val publicKey: PublicKey) {
    init { require(publicKey is RSAPublicKey && publicKey.modulus.bitLength() >= 3072) }
    fun verify(raw: ByteArray, signature: ByteArray, now: Instant, sdk: Int, abis: Set<String>, installedCode: Long, highestSeen: Long = 0): ReleaseManifest {
        if (raw.size !in 1..65536 || signature.size !in 1..4096) throw UpdateRejected("Metadados fora do limite.")
        val signed = try { Base64.getMimeDecoder().decode(signature) } catch (_: Exception) { throw UpdateRejected("Assinatura dos metadados inválida.") }
        val valid = try { Signature.getInstance("SHA256withRSA").run { initVerify(publicKey); update(raw); verify(signed) } } catch (_: Exception) { false }
        if (!valid) throw UpdateRejected("Assinatura dos metadados inválida.")
        // JSON is not parsed or displayed until the exact UTF-8 bytes have been authenticated.
        val j = try {
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString()
            JSONObject(text)
        } catch (_: Exception) { throw UpdateRejected("Formato dos metadados inválido.") }
        try {
            if (j.getInt("schema") != 1 || j.getString("app_id") != "com.camlite.android" || j.getString("os") != "android" ||
                j.getString("package_type") != "android-apk" || j.getString("channel") != "stable" || j.getString("key_id") != UpdateSource.KEY_ID)
                throw UpdateRejected("Publicação incompatível com este aplicativo/canal.")
            val code = j.getLong("version_code")
            if (code <= 0 || code < installedCode || code < highestSeen) throw UpdateRejected("Versão antiga rejeitada.")
            val version = j.getString("version")
            if (!version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) throw UpdateRejected("Versão inválida.")
            val minSdk = j.getInt("min_sdk")
            if (minSdk < 26 || minSdk > sdk) throw UpdateRejected("Android incompatível com esta versão.")
            val a = j.getJSONArray("architectures")
            if (a.length() !in 1..8) throw UpdateRejected("Arquiteturas inválidas.")
            val architectures = (0 until a.length()).map { a.getString(it) }.toSet()
            if (architectures.intersect(abis).isEmpty() || (j.getString("arch") != "universal" && j.getString("arch") !in abis))
                throw UpdateRejected("Arquitetura incompatível.")
            val url = j.getString("url")
            if (!UpdateSource.officialApk(url)) throw UpdateRejected("Origem do APK não autorizada.")
            val size = j.getLong("size")
            val hash = j.getString("sha256")
            if (size !in 1..UpdateSource.MAX_APK_SIZE || !hash.matches(Regex("[a-f0-9]{64}"))) throw UpdateRejected("Tamanho ou hash inválido.")
            val published = Instant.parse(j.getString("published_at"))
            val expires = Instant.parse(j.getString("expires_at"))
            if (published.isAfter(now.plusSeconds(300)) || !expires.isAfter(now) || !expires.isAfter(published) ||
                expires.isAfter(published.plusSeconds(90L * 86400))) throw UpdateRejected("Metadados vencidos ou datas inválidas. Confira o relógio do aparelho.")
            val notes = j.optString("notes")
            if (notes.length > 16384) throw UpdateRejected("Notas fora do limite.")
            return ReleaseManifest(version, code, minSdk, architectures, url, size, hash, notes, published, expires)
        } catch (e: UpdateRejected) { throw e } catch (_: Exception) { throw UpdateRejected("Campos dos metadados inválidos.") }
    }
    companion object {
        fun publicKey(pem: String): PublicKey {
            val der = Base64.getMimeDecoder().decode(pem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", ""))
            return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
        }
        fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

object UpdatePolicy {
    fun automaticDue(enabled: Boolean, now: Long, elapsed: Long, boot: Int, last: Long, lastElapsed: Long, lastBoot: Int): Boolean {
        if (!enabled) return false
        if (last == 0L) return true
        return if (boot >= 0 && boot == lastBoot && elapsed >= lastElapsed) elapsed - lastElapsed >= UpdateSource.DAY_MS
        else now >= last && now - last >= UpdateSource.DAY_MS
    }
    fun shouldNotify(code: Long, installed: Long, enabled: Boolean, permission: Boolean, monitor: Boolean, postponed: Long, notified: Long) =
        code > installed && enabled && permission && !monitor && code > postponed && code != notified
}
