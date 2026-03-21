package cz.tal0052.edisonrozvrh.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

data class EdisonCredentials(
    val username: String,
    val password: String
)

private const val AUTH_PREFS_NAME = "edison_auth"
private const val AUTH_USERNAME_KEY = "username"
private const val AUTH_PASSWORD_KEY = "password"

private fun createSecurePrefs(context: Context): SharedPreferences {
    val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    return EncryptedSharedPreferences.create(
        AUTH_PREFS_NAME,
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

fun loadEdisonCredentials(context: Context): EdisonCredentials? {
    return runCatching {
        val prefs = createSecurePrefs(context.applicationContext)
        val username = prefs.getString(AUTH_USERNAME_KEY, null)?.trim().orEmpty()
        val password = prefs.getString(AUTH_PASSWORD_KEY, null).orEmpty()

        if (username.isBlank() || password.isBlank()) {
            null
        } else {
            EdisonCredentials(
                username = username,
                password = password
            )
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

    runCatching {
        createSecurePrefs(context.applicationContext)
            .edit()
            .putString(AUTH_USERNAME_KEY, normalizedUsername)
            .putString(AUTH_PASSWORD_KEY, password)
            .apply()
    }
}
