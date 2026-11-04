package com.veylo.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

data class Profile(
    val deviceId: String,
    val name: String,
    val upiId: String,
    /** "system", "light" or "dark" */
    val theme: String,
    val onboarded: Boolean,
)

/** Local profile. No account or login: identity is a random per-install device id. */
class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("veylo_profile", Context.MODE_PRIVATE)
    private val _profile = MutableStateFlow(load())
    val profile: StateFlow<Profile> = _profile

    val current: Profile get() = _profile.value

    private fun load(): Profile {
        var id = prefs.getString("deviceId", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString("deviceId", id).apply()
        }
        return Profile(
            deviceId = id,
            name = prefs.getString("name", "") ?: "",
            upiId = prefs.getString("upi", "") ?: "",
            // Light cream (the VEYLO look) by default; Dark / System are opt-in from Profile.
            theme = prefs.getString("themeMode", "light") ?: "light",
            onboarded = prefs.getBoolean("onboarded", false),
        )
    }

    fun update(transform: (Profile) -> Profile) {
        val p = transform(_profile.value)
        prefs.edit()
            .putString("name", p.name)
            .putString("upi", p.upiId)
            .putString("themeMode", p.theme)
            .putBoolean("onboarded", p.onboarded)
            .apply()
        _profile.value = p
    }
}
