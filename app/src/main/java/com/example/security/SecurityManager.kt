package com.example.security

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig

data class AssistantConfig(
    val apiKey: String,
    val model: String = DEFAULT_MODEL,
    val voiceName: String = DEFAULT_VOICE,
    val useRestFallback: Boolean = false
) {
    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash-native-audio-preview-12-2025"
        const val DEFAULT_VOICE = "Aoede"
    }
}

class SecurityManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("roxy_secure_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CUSTOM_API_KEY = "custom_gemini_api_key"
        private const val KEY_MODEL = "gemini_model_name"
        private const val KEY_VOICE = "gemini_voice_name"
        private const val KEY_REST_FALLBACK = "gemini_rest_fallback"

        const val SECURITY_ADVISORY = """
SECURITY NOTICE:
In Android applications, client-side API keys exposed in APK binaries or BuildConfig can be decompiled and extracted by attackers.

Best Practices for Production:
1. For enterprise/production applications, route requests through a trusted backend or use Firebase AI Logic with Firebase App Check attestation.
2. Restrict your Gemini API keys in Google Cloud Console by IP, SHA-1 certificate fingerprint, and API service.
3. For local prototyping, keys configured in AI Studio Secrets are injected at build time via BuildConfig and kept out of version control.
"""
    }

    /**
     * Resolves the effective API key:
     * 1. User manual override from private preferences (if set)
     * 2. BuildConfig.GEMINI_API_KEY from .env / Secrets panel
     */
    fun getEffectiveApiKey(): String {
        val customKey = prefs.getString(KEY_CUSTOM_API_KEY, "")
            ?.trim()?.removeSurrounding("\"")?.removeSurrounding("'") ?: ""
        if (customKey.isNotEmpty()) {
            return customKey
        }
        val buildConfigKey = runCatching { BuildConfig.GEMINI_API_KEY }.getOrDefault("")
            .trim().removeSurrounding("\"").removeSurrounding("'")
        return if (buildConfigKey.isNotBlank() && buildConfigKey != "MY_GEMINI_API_KEY") buildConfigKey else ""
    }

    fun hasValidApiKey(): Boolean {
        val key = getEffectiveApiKey()
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
    }

    fun getMaskedApiKey(): String {
        val key = getEffectiveApiKey()
        if (key.length <= 8) return "••••••••"
        return key.take(4) + "••••••••" + key.takeLast(4)
    }

    fun setCustomApiKey(key: String) {
        val cleaned = key.trim().removeSurrounding("\"").removeSurrounding("'")
        prefs.edit().putString(KEY_CUSTOM_API_KEY, cleaned).apply()
    }

    fun clearCustomApiKey() {
        prefs.edit().remove(KEY_CUSTOM_API_KEY).apply()
    }

    fun getSelectedModel(): String {
        val model = prefs.getString(KEY_MODEL, AssistantConfig.DEFAULT_MODEL) ?: AssistantConfig.DEFAULT_MODEL
        // Heal any stored invalid or non-websocket model
        return if (model == "gemini-2.5-flash" || model == "gemini-3.1-flash-live-preview") {
            AssistantConfig.DEFAULT_MODEL
        } else {
            model
        }
    }

    fun setSelectedModel(model: String) {
        prefs.edit().putString(KEY_MODEL, model.trim()).apply()
    }

    fun getSelectedVoice(): String {
        return prefs.getString(KEY_VOICE, AssistantConfig.DEFAULT_VOICE) ?: AssistantConfig.DEFAULT_VOICE
    }

    fun setSelectedVoice(voice: String) {
        prefs.edit().putString(KEY_VOICE, voice.trim()).apply()
    }

    fun getUseRestFallback(): Boolean {
        return prefs.getBoolean(KEY_REST_FALLBACK, false)
    }

    fun setUseRestFallback(enable: Boolean) {
        prefs.edit().putBoolean(KEY_REST_FALLBACK, enable).apply()
    }

    fun getConfig(): AssistantConfig {
        return AssistantConfig(
            apiKey = getEffectiveApiKey(),
            model = getSelectedModel(),
            voiceName = getSelectedVoice(),
            useRestFallback = getUseRestFallback()
        )
    }
}
