package tv.own.owntv.interflash

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

/**
 * Inter Flash TV-only panel integration for OwnTV. Never controls ISP clients, routers,
 * invoices or WhatsApp. Endpoint contract: isp-manager-new/isp_tv_native.py.
 *
 * Important: an approved device alone is not a streaming authorization when billing is
 * enabled; downstream playback must stop on HTTP 402. Do not store credentials in logs.
 */
class InterFlashTvPanelClient(
    private val context: Context,
    private val panel: String = "https://interflash-isp-manager.up.railway.app",
) {
    private val preferences = context.getSharedPreferences("interflash_tv_pairing", Context.MODE_PRIVATE)
    private val deviceToken: String by lazy {
        val existing = preferences.getString("token", null)
        if (existing?.matches(Regex("[0-9a-f]{64}")) == true) existing
        else {
            val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }.also {
                preferences.edit().putString("token", it).apply()
            }
        }
    }

    data class Pairing(val code: String, val approved: Boolean, val revoked: Boolean)

    sealed interface TvConfig {
        data class Ready(val revision: Long, val name: String, val source: Source, val manualUrl: String?, val manualRevision: Long) : TvConfig
        data object PendingApproval : TvConfig
        data object NoSource : TvConfig
        data object Suspended : TvConfig
    }

    sealed interface Source {
        data class M3u(val url: String) : Source
        data class Xtream(val server: String, val username: String, val password: String) : Source
    }

    suspend fun register(name: String = "Inter Flash TV"): Pairing = withContext(Dispatchers.IO) {
        val data = JSONObject().put("token", deviceToken).put("name", name)
        val (status, json) = call("/api/tv-native/register", "POST", data, false)
        if (status != 200) throw IOException(context.getString(tv.own.owntv.R.string.if_tv_pair_error, status))
        Pairing(json.getString("code"), json.optBoolean("approved"), json.optBoolean("revoked"))
    }

    suspend fun config(): TvConfig = withContext(Dispatchers.IO) {
        val (status, json) = call("/api/tv-native/config", "GET", null, true)
        when (status) {
            401, 403 -> TvConfig.PendingApproval
            402 -> TvConfig.Suspended
            409 -> TvConfig.NoSource
            200 -> {
                val source = when (json.optString("kind")) {
                    "m3u" -> Source.M3u(approvedUrl(json.getString("url")))
                    "xtream" -> Source.Xtream(
                        approvedUrl(json.getString("server")),
                        json.getString("username"), json.getString("password")
                    )
                    else -> throw IOException(context.getString(tv.own.owntv.R.string.if_tv_source_unsupported))
                }
                TvConfig.Ready(json.getLong("revision"), json.optString("name", "Inter Flash TV"), source,
                    json.optString("manual_url").takeIf { it.isNotBlank() }?.let { approvedUrl(it) },
                    json.optLong("manual_revision", 0L))
            }
            else -> throw IOException(context.getString(tv.own.owntv.R.string.if_tv_panel_error, status))
        }
    }

    private fun approvedUrl(raw: String): String {
        val parsed = URL(raw)
        if (parsed.protocol != "https" || parsed.host.isNullOrBlank() || parsed.userInfo != null)
            throw IOException(context.getString(tv.own.owntv.R.string.if_tv_https_only))
        return raw
    }

    private fun call(path: String, method: String, body: JSONObject?, authenticated: Boolean): Pair<Int, JSONObject> {
        val connection = URL(panel + path).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("Accept", "application/json")
            if (authenticated) connection.setRequestProperty("Authorization", "Bearer $deviceToken")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val input = if (code in 200..299) connection.inputStream else connection.errorStream
            val bytes = input?.use { stream ->
                val buffer = ByteArray(4096)
                val output = java.io.ByteArrayOutputStream()
                while (output.size() <= 16_384) {
                    val n = stream.read(buffer, 0, minOf(buffer.size, 16_385 - output.size()))
                    if (n <= 0) break
                    output.write(buffer, 0, n)
                }
                output.toByteArray()
            } ?: byteArrayOf()
            if (bytes.size > 16_384) throw IOException(context.getString(tv.own.owntv.R.string.if_tv_response_too_large))
            Pair(code, runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrDefault(JSONObject()))
        } finally {
            connection.disconnect()
        }
    }
}
