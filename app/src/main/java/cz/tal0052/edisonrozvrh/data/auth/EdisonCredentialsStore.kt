package cz.tal0052.edisonrozvrh.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

data class EdisonCredentials(
    val username: String,
    val password: String
)

private const val AUTH_PREFS_NAME = "edison_auth"
private const val AUTH_FALLBACK_PREFS_NAME = "edison_auth_fallback"
private const val AUTH_USERNAME_KEY = "username"
private const val AUTH_PASSWORD_KEY = "password"
private const val AUTH_LOG_TAG = "EdisonAuth"

private fun createSecurePrefsOrNull(context: Context): SharedPreferences? {
    return runCatching {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

        EncryptedSharedPreferences.create(
            AUTH_PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.onFailure { error ->
        Log.w(AUTH_LOG_TAG, "Encrypted credentials store unavailable, using fallback", error)
    }.getOrNull()
}

private fun createFallbackPrefs(context: Context): SharedPreferences {
    return context.applicationContext.getSharedPreferences(
        AUTH_FALLBACK_PREFS_NAME,
        Context.MODE_PRIVATE
    )
}

private fun readCredentials(prefs: SharedPreferences?): EdisonCredentials? {
    if (prefs == null) return null
    val username = prefs.getString(AUTH_USERNAME_KEY, null)?.trim().orEmpty()
    val password = prefs.getString(AUTH_PASSWORD_KEY, null).orEmpty()

    return if (username.isBlank() || password.isBlank()) {
        null
    } else {
        EdisonCredentials(
            username = username,
            password = password
        )
    }
}

fun loadEdisonCredentials(context: Context): EdisonCredentials? {
    return runCatching {
        val appContext = context.applicationContext
        val fallbackCredentials = readCredentials(createFallbackPrefs(appContext))
        val secureCredentials = readCredentials(createSecurePrefsOrNull(appContext))

        when {
            fallbackCredentials != null && secureCredentials != null && fallbackCredentials != secureCredentials -> {
                Log.w(AUTH_LOG_TAG, "Encrypted and fallback credentials differ, preferring fallback values")
                fallbackCredentials
            }
            fallbackCredentials != null -> fallbackCredentials
            else -> secureCredentials
        }
    }.getOrNull()
}

fun saveEdisonCredentials(
    context: Context,
    username: String,
    password: String
) {
    val normalizedUsername = username.trim()
    if (normalizedUsername.isBlank() || password.isBlank()) return

    val appContext = context.applicationContext
    val securePrefs = createSecurePrefsOrNull(appContext)

    if (securePrefs != null) {
        runCatching {
            securePrefs.edit()
                .putString(AUTH_USERNAME_KEY, normalizedUsername)
                .putString(AUTH_PASSWORD_KEY, password)
                .commit()
        }.onSuccess { committed ->
            if (committed) {
                createFallbackPrefs(appContext).edit().clear().apply()
                return
            }
            Log.w(AUTH_LOG_TAG, "Encrypted credentials commit returned false, using fallback store")
        }.onFailure { error ->
            Log.w(AUTH_LOG_TAG, "Encrypted credentials save failed, using fallback store", error)
        }
    }

    createFallbackPrefs(appContext)
        .edit()
        .putString(AUTH_USERNAME_KEY, normalizedUsername)
        .putString(AUTH_PASSWORD_KEY, password)
        .apply()
}

fun clearEdisonCredentials(context: Context) {
    val appContext = context.applicationContext
    createSecurePrefsOrNull(appContext)?.edit()?.clear()?.apply()
    createFallbackPrefs(appContext).edit().clear().apply()
}
