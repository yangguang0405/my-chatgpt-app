package cn.gekeai.mychatgpt.ui.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** One scripted turn pulled from the server: a human prompt and the AI reply to it. */
data class ScriptTurn(
    val human: String,
    val ai: String,
)

/**
 * Client for the scripted-conversation endpoint. The server hands out one turn at a
 * time: a 200 with a payload when a turn is queued, or a 404 when nothing is waiting.
 */
object ChatScriptApi {
    private const val ENDPOINT = "https://jmq-api.mjm-ai.cn/ai-chat-scripts/next"
    private const val ROLE = "ChatGPT"
    private const val API_KEY = "LTAI5tCyvq3bQbrpp8kTeHDe"

    /**
     * Polls once for the next turn.
     *
     * @return the turn when one is available, or `null` when the server replies 404
     *   (no message queued). Network/parse failures also return `null` so the caller
     *   can simply keep polling.
     */
    suspend fun fetchNext(): ScriptTurn? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-API-Key", API_KEY)
            }

            connection.outputStream.use { out ->
                out.write(JSONObject().put("role", ROLE).toString().toByteArray(Charsets.UTF_8))
            }

            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    parseTurn(body)
                }
                // No message queued right now — nothing to show.
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> null
            }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseTurn(body: String): ScriptTurn? {
        return try {
            val data = JSONObject(body).optJSONObject("data") ?: return null
            val human = data.optString("human")
            val ai = data.optString("ai")
            if (human.isEmpty() && ai.isEmpty()) null else ScriptTurn(human = human, ai = ai)
        } catch (_: Exception) {
            null
        }
    }
}
