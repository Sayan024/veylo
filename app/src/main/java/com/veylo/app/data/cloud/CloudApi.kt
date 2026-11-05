package com.veylo.app.data.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class CloudException(message: String, val network: Boolean) : Exception(message)

/**
 * Talks to the VEYLO Supabase project — the same database functions the web app uses.
 * Only the public anon key is used; all tables are locked and every call is checked server-side.
 */
class CloudApi(baseUrl: String, private val anonKey: String) {
    private val url = baseUrl.trimEnd('/')
    val enabled: Boolean = url.startsWith("https://") && anonKey.isNotBlank()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun rpc(fn: String, args: JsonObject): JsonElement = withContext(Dispatchers.IO) {
        if (!enabled) throw CloudException("Cloud sync is not configured", false)
        val conn = (URL("$url/rest/v1/rpc/$fn").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("apikey", anonKey)
            setRequestProperty("Authorization", "Bearer $anonKey")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            conn.outputStream.use { it.write(args.toString().toByteArray()) }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val msg = runCatching { json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.content }.getOrNull()
                throw CloudException(msg ?: "Server error ($code)", network = code >= 500 || code == 429)
            }
            if (body.isBlank()) JsonNull else json.parseToJsonElement(body)
        } catch (e: IOException) {
            throw CloudException(e.message ?: "Network error", network = true)
        } finally {
            conn.disconnect()
        }
    }

    /** Tells web clients in the group to refresh now (Realtime broadcast; carries no data). Best effort. */
    suspend fun ping(groupId: String) = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext
        runCatching {
            val conn = (URL("$url/realtime/v1/api/broadcast").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5_000
                readTimeout = 5_000
                doOutput = true
                setRequestProperty("apikey", anonKey)
                setRequestProperty("Authorization", "Bearer $anonKey")
                setRequestProperty("Content-Type", "application/json")
            }
            val body = buildJsonObject {
                put("messages", buildJsonArray {
                    add(buildJsonObject {
                        put("topic", "veylo-$groupId")
                        put("event", "changed")
                        putJsonObject("payload") {}
                    })
                })
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            conn.responseCode
            conn.disconnect()
        }
    }

    companion object {
        val EMPTY_ARRAY = JsonArray(emptyList())
    }
}
