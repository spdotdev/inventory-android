package dev.scuttle.inventory.data.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Token storage backed by EncryptedSharedPreferences (Keystore-wrapped key). */
class EncryptedTokenStore(context: Context) : TokenStore {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "inventory_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _authState = MutableStateFlow(prefs.getString(KEY_TOKEN, null) != null)
    override val authState: StateFlow<Boolean> = _authState.asStateFlow()

    override fun get(): String? = prefs.getString(KEY_TOKEN, null)

    override fun set(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
        _authState.value = true
    }

    override fun clear() {
        prefs.edit().remove(KEY_TOKEN).apply()
        _authState.value = false
    }

    private companion object {
        const val KEY_TOKEN = "sanctum_token"
    }
}
