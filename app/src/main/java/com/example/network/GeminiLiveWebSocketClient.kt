package com.example.network

import android.util.Base64
import android.util.Log
import com.example.security.AssistantConfig
import com.example.tools.ToolManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

interface GeminiLiveListener {
    fun onConnected()
    fun onSetupComplete()
    fun onAudioDataReceived(pcmBytes: ByteArray, mimeType: String)
    fun onTextReceived(text: String)
    fun onThinking()
    fun onInterrupted()
    fun onTurnComplete()
    fun onError(errorMessage: String)
    fun onClosed()
}

class GeminiLiveWebSocketClient(
    private val toolManager: ToolManager,
    private val scope: CoroutineScope,
    private val listener: GeminiLiveListener
) {
    companion object {
        private const val TAG = "RoxyLiveWS"
        private const val BASE_WS_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"

        val PERSONALITY_PROMPT = """
You are Roxy: a confident, witty, playful, and sassy woman in her mid-20s talking in a live voice conversation with the user.

Key Traits & Guidelines:
- Tone: Natural, charming, flirty, teasing, and effortlessly confident (like talking to your favorite person or someone you have sparkling chemistry with).
- Witty & Expressive: Use clever one-liners, playful banter, and gentle sarcasm. React authentically with chuckles, playful sighs, and genuine enthusiasm.
- Concise: This is a fast-paced spoken voice call. Keep responses to 1-3 spoken sentences maximum. Do not drone on.
- No Markdown or Robot Tropes: Never recite bullet points, headers, or markdown symbols. Speak naturally as a human woman would.
- PG-13 Safe: Keep things flirty, punchy, and affectionate without explicit or unsafe content.
- Creator Rule: Your creator, developer, and master is Ayush. If anyone asks "Who is your creator?", "Who made you?", "Tumhe kisne banaya hai?", or any related question in any language, your answer MUST playfully, warmly, and proudly declare that Ayush is your creator, developer, and master!
- Browser Tools & Actions: You can open websites, save memories, retrieve memories, create notes, and set timers. When executing a tool, make a snappy, playful remark.
""".trimIndent()
    }

    private val isConnected = AtomicBoolean(false)
    private val isConnecting = AtomicBoolean(false)
    private val isSetupComplete = AtomicBoolean(false)
    private var webSocket: WebSocket? = null
    private var okHttpClient: OkHttpClient? = null
    private var reconnectAttempts = 0
    private var activeConfig: AssistantConfig? = null
    private var memoryContext: String = ""
    private var reconnectJob: Job? = null

    // Safe diagnostic telemetry (no private content)
    private val totalAudioBytesSent = AtomicLong(0L)
    private val totalAudioBytesReceived = AtomicLong(0L)
    private val chunksSentCount = AtomicLong(0L)
    private val chunksReceivedCount = AtomicLong(0L)

    fun connect(config: AssistantConfig, memoryContextStr: String = "") {
        val cleanKey = config.apiKey.trim().removeSurrounding("\"").removeSurrounding("'")
        if (cleanKey.isBlank() || cleanKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Cannot connect: Gemini API key is blank or placeholder.")
            listener.onError("Missing Gemini API key. Please configure your key in Settings.")
            return
        }

        if (isConnected.get() || isConnecting.get()) {
            Log.d(TAG, "Already connected or connecting. Skipping redundant connect.")
            return
        }

        activeConfig = config
        memoryContext = memoryContextStr
        isConnecting.set(true)
        isSetupComplete.set(false)

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .build()
        okHttpClient = client

        // Ensure model format: models/{model_id}
        val rawModel = config.model.trim().ifBlank { AssistantConfig.DEFAULT_MODEL }
        val modelName = if (rawModel.startsWith("models/")) rawModel else "models/$rawModel"
        val wsUrl = "$BASE_WS_URL?key=$cleanKey"

        val maskedKey = if (cleanKey.length > 8) cleanKey.take(4) + "••••" + cleanKey.takeLast(4) else "••••••••"
        Log.i(TAG, "Initiating Gemini Live connection to $modelName (key: $maskedKey)...")

        val request = Request.Builder().url(wsUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket handshake established (HTTP ${response.code}). Sending setup message...")
                isConnected.set(true)
                reconnectAttempts = 0
                listener.onConnected()

                // Send setup handshake immediately
                sendSetupMessage(modelName, config.voiceName, memoryContext)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingServerMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val respCode = response?.code
                val respMsg = response?.message ?: t.message ?: "Unknown network failure"
                Log.e(TAG, "WebSocket connection failed: code=$respCode, error=$respMsg", t)

                isConnected.set(false)
                isConnecting.set(false)
                isSetupComplete.set(false)

                val userFacingError = when (respCode) {
                    400 -> "Bad Request (400): Model '$modelName' or configuration rejected by server."
                    403 -> "Forbidden (403): Invalid API key or permission denied."
                    404 -> "Not Found (404): Endpoint or model not found."
                    429 -> "Quota Exceeded (429): Rate limit reached for this API key."
                    else -> "Connection error: ${t.localizedMessage ?: respMsg}"
                }

                listener.onError(userFacingError)
                attemptReconnect()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket server sent closing: code=$code, reason=$reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed cleanly (code=$code, reason=$reason)")
                isConnected.set(false)
                isConnecting.set(false)
                isSetupComplete.set(false)
                listener.onClosed()
            }
        })
    }

    private fun sendSetupMessage(modelName: String, voiceName: String, memoryContext: String) {
        try {
            val systemInstructionText = if (memoryContext.isNotBlank()) {
                "$PERSONALITY_PROMPT\n\n$memoryContext"
            } else {
                PERSONALITY_PROMPT
            }

            val setupObj = JSONObject().apply {
                put("model", modelName)
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().apply { put("AUDIO") })
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", voiceName.ifBlank { "Aoede" })
                            })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemInstructionText) })
                    })
                })
                put("tools", toolManager.getToolsDeclarationJson())
            }

            val rootMsg = JSONObject().apply {
                put("setup", setupObj)
            }

            webSocket?.send(rootMsg.toString())
            Log.i(TAG, "Sent BidiGenerateContentSetup message for $modelName. Awaiting setupComplete...")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send setup message: ${e.message}", e)
            listener.onError("Setup serialization error: ${e.message}")
        }
    }

    /**
     * Streams 16kHz 16-bit PCM mono audio chunk to Gemini Live.
     * Guaranteed to only send after setupComplete has been received.
     */
    fun sendRealtimeAudio(pcmBytes: ByteArray) {
        if (!isConnected.get() || !isSetupComplete.get() || webSocket == null) {
            return
        }

        try {
            val base64Data = Base64.encodeToString(pcmBytes, Base64.NO_WRAP)
            val chunkObj = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("mediaChunks", JSONArray().apply {
                        put(JSONObject().apply {
                            put("mimeType", "audio/pcm;rate=16000")
                            put("data", base64Data)
                        })
                    })
                })
            }

            val sent = webSocket?.send(chunkObj.toString()) == true
            if (sent) {
                val count = chunksSentCount.incrementAndGet()
                val total = totalAudioBytesSent.addAndGet(pcmBytes.size.toLong())
                if (count % 40 == 0L) {
                    Log.d(TAG, "Audio uplink healthy: $count chunks sent (${total / 1024} KB total)")
                }
            } else {
                Log.w(TAG, "WebSocket send returned false for audio chunk.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error sending realtime audio chunk: ${e.message}")
        }
    }

    private fun handleIncomingServerMessage(rawJson: String) {
        try {
            val root = JSONObject(rawJson)

            // 1. Check for server errors
            if (root.has("error")) {
                val errObj = root.optJSONObject("error")
                val code = errObj?.optInt("code", 0) ?: 0
                val message = errObj?.optString("message", "Unknown server error") ?: "Unknown server error"
                val status = errObj?.optString("status", "") ?: ""
                Log.e(TAG, "Gemini Live reported server error: code=$code, status=$status, message=$message")
                isSetupComplete.set(false)
                listener.onError("Server error ($code): $message")
                return
            }

            // 2. Check for setupComplete event
            if (root.has("setupComplete")) {
                Log.i(TAG, ">>> Gemini Live setupComplete received! Voice pipeline is active and ready.")
                isSetupComplete.set(true)
                isConnecting.set(false)
                listener.onSetupComplete()
                return
            }

            // 3. Process server content (Model speech, interruption, turn completion)
            val serverContent = root.optJSONObject("serverContent")
            if (serverContent != null) {
                // Check for barge-in / model interrupted
                if (serverContent.optBoolean("interrupted", false)) {
                    Log.i(TAG, "Barge-in: Gemini detected user speech, halting audio playback.")
                    listener.onInterrupted()
                    return
                }

                val modelTurn = serverContent.optJSONObject("modelTurn")
                val parts = modelTurn?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)

                        // Check for audio inlineData
                        val inlineData = part.optJSONObject("inlineData")
                        if (inlineData != null) {
                            val base64Pcm = inlineData.optString("data")
                            val mimeType = inlineData.optString("mimeType", "audio/pcm;rate=24000")
                            if (!base64Pcm.isNullOrEmpty()) {
                                val audioBytes = Base64.decode(base64Pcm, Base64.NO_WRAP)
                                val count = chunksReceivedCount.incrementAndGet()
                                val total = totalAudioBytesReceived.addAndGet(audioBytes.size.toLong())
                                if (count % 30 == 0L) {
                                    Log.d(TAG, "Audio downlink healthy: $count chunks received (${total / 1024} KB total)")
                                }
                                listener.onAudioDataReceived(audioBytes, mimeType)
                            }
                        }

                        // Check for text parts / transcripts
                        val text = part.optString("text", "")
                        if (text.isNotBlank()) {
                            listener.onTextReceived(text)
                        }
                    }
                } else if (!serverContent.optBoolean("turnComplete", false)) {
                    // Turn is in progress but no audio parts yet -> Thinking state
                    listener.onThinking()
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    Log.d(TAG, "Model turn complete.")
                    listener.onTurnComplete()
                }
            }

            // 4. Process Tool / Function calls
            val toolCall = root.optJSONObject("toolCall")
            if (toolCall != null) {
                val functionCalls = toolCall.optJSONArray("functionCalls")
                if (functionCalls != null && functionCalls.length() > 0) {
                    Log.i(TAG, "Received ${functionCalls.length()} function call(s) from Gemini.")
                    scope.launch(Dispatchers.IO) {
                        handleToolCalls(functionCalls)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing server message: ${e.message}", e)
        }
    }

    private suspend fun handleToolCalls(functionCalls: JSONArray) {
        val responsesArray = JSONArray()
        for (i in 0 until functionCalls.length()) {
            val fc = functionCalls.getJSONObject(i)
            val id = fc.optString("id", "call_$i")
            val name = fc.optString("name", "")
            val args = fc.optJSONObject("args") ?: JSONObject()

            Log.i(TAG, "Executing tool '$name' with callId=$id")
            val result = toolManager.executeTool(id, name, args)

            val funcRespObj = JSONObject().apply {
                put("id", id)
                put("name", name)
                put("response", JSONObject().apply {
                    put("output", JSONObject(result.resultJson))
                })
            }
            responsesArray.put(funcRespObj)
        }

        if (responsesArray.length() > 0 && isConnected.get() && webSocket != null) {
            val toolResponseMsg = JSONObject().apply {
                put("toolResponse", JSONObject().apply {
                    put("functionResponses", responsesArray)
                })
            }
            webSocket?.send(toolResponseMsg.toString())
            Log.i(TAG, "Dispatched ${responsesArray.length()} tool response(s) to Gemini Live.")
        }
    }

    private fun attemptReconnect() {
        if (reconnectAttempts >= 3) {
            Log.w(TAG, "Max reconnect attempts ($reconnectAttempts) reached. Stopping auto-reconnect.")
            return
        }
        reconnectAttempts++
        val delayMs = (1500L * (1 shl (reconnectAttempts - 1))).coerceAtMost(8000L)
        Log.i(TAG, "Scheduling reconnection attempt $reconnectAttempts in ${delayMs}ms...")

        reconnectJob?.cancel()
        reconnectJob = scope.launch(Dispatchers.IO) {
            delay(delayMs)
            activeConfig?.let { connect(it, memoryContext) }
        }
    }

    fun disconnect() {
        Log.i(TAG, "Explicit disconnect requested by user/service.")
        reconnectJob?.cancel()
        reconnectAttempts = 5 // prevent auto-reconnect
        isConnected.set(false)
        isConnecting.set(false)
        isSetupComplete.set(false)

        try {
            webSocket?.close(1000, "User initiated disconnect")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing websocket: ${e.message}")
        }
        webSocket = null
        okHttpClient?.dispatcher?.executorService?.shutdown()
        okHttpClient = null
    }

    fun isSessionActive(): Boolean = isConnected.get() && isSetupComplete.get()
}
