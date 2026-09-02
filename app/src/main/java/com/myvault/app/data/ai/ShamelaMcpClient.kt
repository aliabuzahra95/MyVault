package com.myvault.app.data.ai

import com.myvault.app.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class ShamelaMcpClient @Inject constructor(
    private val authRepository: ShamelaAuthRepository,
) {
    private val requestIds = AtomicLong(1L)
    private val discoveryMutex = Mutex()
    private var activeSession: McpSession? = null

    suspend fun discover(): ShamelaMcpContract = discoveryMutex.withLock {
        val session = initialize()
        activeSession = session
        val tools = listAllTools(session)
        ShamelaMcpContract(
            protocolVersion = session.protocolVersion,
            serverName = session.serverName,
            serverVersion = session.serverVersion,
            serverInstructions = session.serverInstructions,
            serverCapabilities = session.serverCapabilities,
            transport = session.transport,
            usesSessionId = session.sessionId != null,
            tools = tools,
        )
    }

    suspend fun callTool(name: String, arguments: JSONObject): JSONObject {
        val session = activeSession ?: discoveryMutex.withLock {
            activeSession ?: initialize().also { activeSession = it }
        }
        return request(
            method = "tools/call",
            params = JSONObject().put("name", name).put("arguments", arguments),
            session = session,
        ).getJSONObject("result")
    }

    fun clearSession() {
        activeSession = null
    }

    private suspend fun initialize(): McpSession {
        val response = post(
            message = JSONObject()
                .put("jsonrpc", JsonRpcVersion)
                .put("id", requestIds.getAndIncrement())
                .put("method", "initialize")
                .put(
                    "params",
                    JSONObject()
                        .put("protocolVersion", LatestSupportedProtocol)
                        .put("capabilities", JSONObject())
                        .put(
                            "clientInfo",
                            JSONObject()
                                .put("name", "MyVault Android")
                                .put("version", BuildConfig.VERSION_NAME),
                        ),
                ),
            protocolVersion = null,
            sessionId = null,
        )
        val payload = ShamelaMcpWire.parseResponse(response.body, response.contentType)
        val result = payload.requireResult()
        val negotiatedVersion = result.getString("protocolVersion")
        require(negotiatedVersion in SupportedProtocols) {
            "Shamela selected unsupported MCP version $negotiatedVersion."
        }
        val session = McpSession(
            protocolVersion = negotiatedVersion,
            sessionId = response.sessionId,
            serverName = result.optJSONObject("serverInfo")?.optString("name").orEmpty(),
            serverVersion = result.optJSONObject("serverInfo")?.optString("version").orEmpty(),
            serverInstructions = result.optString("instructions").takeIf(String::isNotBlank),
            serverCapabilities = result.optJSONObject("capabilities") ?: JSONObject(),
            transport = if (response.contentType.isSse()) McpTransport.Sse else McpTransport.Json,
        )
        post(
            message = JSONObject()
                .put("jsonrpc", JsonRpcVersion)
                .put("method", "notifications/initialized"),
            protocolVersion = session.protocolVersion,
            sessionId = session.sessionId,
            notification = true,
        )
        return session
    }

    private suspend fun listAllTools(session: McpSession): List<ShamelaMcpTool> {
        val tools = mutableListOf<ShamelaMcpTool>()
        var cursor: String? = null
        var pages = 0
        do {
            check(++pages <= MaxToolPages) { "Shamela returned too many tool pages." }
            val params = JSONObject().apply { cursor?.let { put("cursor", it) } }
            val result = request("tools/list", params, session).getJSONObject("result")
            val page = result.optJSONArray("tools") ?: JSONArray()
            for (index in 0 until page.length()) {
                check(tools.size < MaxToolCount) { "Shamela returned too many tools." }
                val tool = page.getJSONObject(index)
                tools += ShamelaMcpTool(
                    name = tool.getString("name"),
                    title = tool.optString("title").takeIf(String::isNotBlank),
                    description = tool.optString("description"),
                    inputSchema = tool.optJSONObject("inputSchema") ?: JSONObject(),
                    outputSchema = tool.optJSONObject("outputSchema"),
                    annotations = tool.optJSONObject("annotations"),
                    raw = JSONObject(tool.toString()),
                )
            }
            cursor = result.optString("nextCursor").takeIf(String::isNotBlank)
        } while (cursor != null)
        return tools
    }

    private suspend fun request(method: String, params: JSONObject, session: McpSession): JSONObject {
        val response = post(
            message = JSONObject()
                .put("jsonrpc", JsonRpcVersion)
                .put("id", requestIds.getAndIncrement())
                .put("method", method)
                .put("params", params),
            protocolVersion = session.protocolVersion,
            sessionId = session.sessionId,
        )
        return ShamelaMcpWire.parseResponse(response.body, response.contentType).also { it.requireResult() }
    }

    private suspend fun post(
        message: JSONObject,
        protocolVersion: String?,
        sessionId: String?,
        notification: Boolean = false,
    ): McpHttpResponse = withContext(Dispatchers.IO) {
        val token = authRepository.freshAccessToken()
        currentCoroutineContext().ensureActive()
        val connection = (URL(ShamelaAuthRepository.ResourceEndpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = ConnectTimeoutMillis
            readTimeout = ReadTimeoutMillis
            doOutput = true
            useCaches = false
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json, text/event-stream")
            protocolVersion?.let { setRequestProperty("MCP-Protocol-Version", it) }
            sessionId?.let { setRequestProperty("Mcp-Session-Id", it) }
        }
        val cancellationHandle = currentCoroutineContext().job.invokeOnCompletion { cause ->
            if (cause is CancellationException) connection.disconnect()
        }
        try {
            connection.outputStream.use { it.write(message.toString().encodeToByteArray()) }
            val status = connection.responseCode
            val contentType = connection.contentType.orEmpty()
            val responseSessionId = connection.getHeaderField("Mcp-Session-Id")
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { it.readUtf8Bounded(MaxResponseBytes) }.orEmpty()
            if (notification && status == HttpURLConnection.HTTP_ACCEPTED) {
                return@withContext McpHttpResponse(status, contentType, body, responseSessionId)
            }
            if (status !in 200..299) {
                val detail = ShamelaMcpWire.safeErrorMessage(body)
                throw ShamelaMcpException("Shamela MCP HTTP $status${detail?.let { ": $it" }.orEmpty()}", status)
            }
            if (!notification && body.isBlank()) throw ShamelaMcpException("Shamela MCP returned an empty response.")
            McpHttpResponse(status, contentType, body, responseSessionId)
        } finally {
            cancellationHandle.dispose()
            connection.disconnect()
        }
    }

    private companion object {
        const val JsonRpcVersion = "2.0"
        const val LatestSupportedProtocol = "2025-11-25"
        val SupportedProtocols = setOf("2025-11-25", "2025-06-18", "2025-03-26")
        const val ConnectTimeoutMillis = 20_000
        const val ReadTimeoutMillis = 45_000
        const val MaxResponseBytes = 4 * 1024 * 1024
        const val MaxToolPages = 20
        const val MaxToolCount = 500
    }
}

data class ShamelaMcpContract(
    val protocolVersion: String,
    val serverName: String,
    val serverVersion: String,
    val serverInstructions: String?,
    val serverCapabilities: JSONObject,
    val transport: McpTransport,
    val usesSessionId: Boolean,
    val tools: List<ShamelaMcpTool>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("protocolVersion", protocolVersion)
        .put("serverName", serverName)
        .put("serverVersion", serverVersion)
        .put("serverInstructions", serverInstructions)
        .put("serverCapabilities", serverCapabilities)
        .put("transport", transport.name.lowercase())
        .put("usesSessionId", usesSessionId)
        .put("tools", JSONArray().apply { tools.forEach { put(it.raw) } })
}

data class ShamelaMcpTool(
    val name: String,
    val title: String?,
    val description: String,
    val inputSchema: JSONObject,
    val outputSchema: JSONObject?,
    val annotations: JSONObject?,
    val raw: JSONObject,
)

enum class McpTransport { Json, Sse }

sealed interface ShamelaMcpConnectionState {
    data object Idle : ShamelaMcpConnectionState
    data object Connecting : ShamelaMcpConnectionState
    data class Ready(val contract: ShamelaMcpContract) : ShamelaMcpConnectionState
    data class Error(val message: String) : ShamelaMcpConnectionState
}

class ShamelaMcpException(message: String, val httpStatus: Int? = null) : Exception(message)

internal object ShamelaMcpWire {
    fun parseResponse(body: String, contentType: String): JSONObject {
        val candidates = if (contentType.isSse() || body.lineSequence().any { it.startsWith("data:") }) {
            parseSseData(body)
        } else {
            listOf(body.trim())
        }
        return candidates.asSequence()
            .filter(String::isNotBlank)
            .mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
            .firstOrNull { it.has("result") || it.has("error") }
            ?: throw ShamelaMcpException("Shamela MCP returned malformed JSON-RPC data.")
    }

    fun parseSseData(body: String): List<String> {
        val events = mutableListOf<String>()
        val data = mutableListOf<String>()
        fun flush() {
            if (data.isNotEmpty()) events += data.joinToString("\n")
            data.clear()
        }
        body.lineSequence().forEach { rawLine ->
            val line = rawLine.removeSuffix("\r")
            when {
                line.isEmpty() -> flush()
                line.startsWith("data:") -> data += line.removePrefix("data:").removePrefix(" ")
            }
        }
        flush()
        return events
    }

    fun safeErrorMessage(body: String): String? = runCatching {
        val json = JSONObject(body)
        json.optJSONObject("error")?.optString("message")?.takeIf(String::isNotBlank)
            ?: json.optString("message").takeIf(String::isNotBlank)
    }.getOrNull()
}

private data class McpSession(
    val protocolVersion: String,
    val sessionId: String?,
    val serverName: String,
    val serverVersion: String,
    val serverInstructions: String?,
    val serverCapabilities: JSONObject,
    val transport: McpTransport,
)

private data class McpHttpResponse(
    val status: Int,
    val contentType: String,
    val body: String,
    val sessionId: String?,
)

private fun JSONObject.requireResult(): JSONObject {
    optJSONObject("error")?.let { error ->
        val code = error.optInt("code")
        val message = error.optString("message", "Unknown MCP error")
        throw ShamelaMcpException("Shamela MCP error $code: $message")
    }
    return optJSONObject("result") ?: throw ShamelaMcpException("Shamela MCP response has no result.")
}

private fun String.isSse(): Boolean = startsWith("text/event-stream", ignoreCase = true)

private fun InputStream.readUtf8Bounded(maxBytes: Int): String {
    val output = ByteArrayOutputStream(minOf(maxBytes, 32 * 1024))
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > maxBytes) throw ShamelaMcpException("Shamela MCP response exceeded the safe size limit.")
        output.write(buffer, 0, read)
    }
    return output.toByteArray().decodeToString()
}
