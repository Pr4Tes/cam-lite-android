package com.camlite.android

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class CameraConfig(
    val id: String = UUID.randomUUID().toString(), val name: String,
    val host: String, val port: Int = 554, val user: String = "", val password: String = "",
    val channel: Int = 1, val substream: Boolean = true, val customUrl: String = ""
) {
    fun uri(): Uri {
        val base = if (customUrl.isNotBlank()) Uri.parse(customUrl) else {
            val address = if (host.contains(':') && !host.startsWith('[')) "[$host]" else host
            Uri.parse("rtsp://$address:$port/cam/realmonitor?channel=$channel&subtype=${if (substream) 1 else 0}")
        }
        if (user.isEmpty()) return base
        val authority = base.encodedAuthority.orEmpty().substringAfterLast('@')
        return base.buildUpon().encodedAuthority("${Uri.encode(user)}:${Uri.encode(password)}@$authority").build()
    }
    fun endpoint(): String {
        val url = if (customUrl.isBlank()) null else Uri.parse(customUrl)
        val endpointHost = url?.host ?: host.removePrefix("[").removeSuffix("]")
        val endpointPort = url?.port?.takeIf { it != -1 } ?: port
        val endpointChannel = url?.getQueryParameter("channel")?.toIntOrNull() ?: channel
        return "${endpointHost.trim().lowercase()}:$endpointPort:$endpointChannel"
    }
    fun json() = JSONObject().put("id", id).put("name", name).put("host", host).put("port", port)
        .put("user", user).put("password", password).put("channel", channel).put("substream", substream).put("customUrl", customUrl)
    companion object {
        fun from(j: JSONObject) = CameraConfig(j.getString("id"), j.getString("name"), j.getString("host"), j.getInt("port"),
            j.optString("user"), j.optString("password"), j.optInt("channel", 1), j.optBoolean("substream", true), j.optString("customUrl"))
    }
}

data class Settings(var layout: Int = 4, var orientation: Int = 0, var keepOn: Boolean = true,
    var brightness: Int = -1, var startMonitor: Boolean = false, var home: Boolean = false,
    var performance: PerformanceProfile = PerformanceProfile.NORMAL, var screen: ScreenSettings = ScreenSettings()) {
    fun json() = JSONObject().put("layout", layout).put("orientation", orientation).put("keepOn", keepOn)
        .put("brightness", brightness).put("startMonitor", startMonitor).put("home", home).put("performance", performance.name).put("screen", screen.json())
    companion object {
        fun from(j: JSONObject) = Settings(j.optInt("layout", 4).coerceIn(1, 4), j.optInt("orientation"), j.optBoolean("keepOn", true),
            j.optInt("brightness", -1).coerceIn(-1, 100), j.optBoolean("startMonitor"), j.optBoolean("home"),
            PerformanceProfile.entries.firstOrNull { it.name == j.optString("performance") } ?: PerformanceProfile.NORMAL,
            j.optJSONObject("screen")?.let { ScreenSettings.from(it) } ?: ScreenSettings())
    }
}

data class AppConfig(val cameras: MutableList<CameraConfig> = mutableListOf(), val settings: Settings = Settings()) {
    fun validate() {
        require(cameras.size <= 4 && cameras.map { it.id }.distinct().size == cameras.size)
        cameras.forEach { require(it.id.isNotBlank() && it.name.isNotBlank() && (it.host.isNotBlank() || it.customUrl.isNotBlank()) && it.port in 1..65535 && it.channel > 0) }
        require(settings.layout in 1..4 && settings.orientation in 0..2 && settings.brightness in -1..100)
        settings.screen.validate()
    }
    fun json() = JSONObject().put("schemaVersion", SCHEMA_VERSION).put("cameras", JSONArray().apply { cameras.forEach { put(it.json()) } }).put("settings", settings.json())
    companion object {
        const val SCHEMA_VERSION = 2
        fun from(j: JSONObject): AppConfig {
            require(j.optInt("schemaVersion", 1) in 1..SCHEMA_VERSION) { "Versão de configuração incompatível" }
            val a = j.getJSONArray("cameras")
            require(a.length() <= 4)
            return AppConfig((0 until a.length()).map { CameraConfig.from(a.getJSONObject(it)) }.toMutableList(), Settings.from(j.getJSONObject("settings"))).apply { validate() }
        }
    }
}
