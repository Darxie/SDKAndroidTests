//package cz.feldis.sdkandroidtests.auth
//
//import android.content.Context
//import android.content.SharedPreferences
//import com.sygic.lib.auth.Storage
//import com.sygic.sdk.context.SygicContext
//import com.sygic.sdk.diagnostics.LogConnector
//import com.sygic.sdk.low.system.SygicPreferences
//import java.util.concurrent.TimeUnit
//
//internal class AuthStorageImpl(private val context: Context) : Storage {
//
//    companion object {
//        private const val TAG = "AuthStorage"
//        private const val SHARED_PREFS_NAME = "sygic_auth_settings"
//
//        private const val IsLoginTypeDeviceOldSettings = "Library::ESetting::OnlineIsLoginTypeDevice"
//        private const val RefreshTokenOldSettings = "Library::ESetting::OnlineRefreshToken"
//        private const val AccessTokenOldSettings = "Library::ESetting::OnlineAccessToken"
//        private const val TokenCreatedOldSettings = "Library::ESetting::OnlineTokenCreated"
//        private const val TokenExpiresInOldSettings = "Library::ESetting::OnlineTokenExpiresIn"
//
//        private const val SignInWithAccountNewSettings = "signed_in_with_account"
//        private const val AccessTokenNewSettings = "access_token"
//        private const val RefreshTokenNewSettings = "refresh_token"
//        private const val TokenCreatedNewSettings = "created_timestamp"
//        private const val TokenExpiresInNewSettings = "expires_in"
//        private const val TokenTypeNewSettings = "token_type"
//        private const val TokenTypeValue = "Bearer"
//
//        private const val SettingsMigratedFlag = "settings_migrated"
//
//        // amount of seconds from 1.1.1970 do 1.1.2001
//        private const val EpochTimeToSygicTime = 978307200
//    }
//
//    init {
//        migrateStorageFromOldSettings()
//    }
//
//    private fun getPreferences(): SharedPreferences {
//        return context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
//    }
//
//    private fun migrateStorageFromOldSettings() {
//        if (getInt(SettingsMigratedFlag, 0) == 1) {
//            return
//        }
//        val isLoginTypeDevice = SharedPreferences.getValue(context, IsLoginTypeDeviceOldSettings) ?: return
//        val refreshToken = SygicPreferences.getValue(context, RefreshTokenOldSettings) ?: return
//        val accessToken = SygicPreferences.getValue(context, AccessTokenOldSettings) ?: return
//        val tokenCreated = SygicPreferences.getValue(context, TokenCreatedOldSettings) ?: return
//        val tokenExpiresIn = SygicPreferences.getValue(context, TokenExpiresInOldSettings) ?: return
//
//        runCatching {
//            getPreferences().edit().apply {
//                putInt(SignInWithAccountNewSettings, if (isLoginTypeDevice.toInt() == 1) 0 else 1)
//                putString(AccessTokenNewSettings, accessToken)
//                putString(RefreshTokenNewSettings, refreshToken)
//                putLong(TokenCreatedNewSettings, TimeUnit.SECONDS.toMillis(tokenCreated.toLong() + EpochTimeToSygicTime))
//                putInt(TokenExpiresInNewSettings, TimeUnit.SECONDS.toMillis(tokenExpiresIn.toLong()).toInt())
//                putString(TokenTypeNewSettings, TokenTypeValue)
//                putInt(SettingsMigratedFlag, 1)
//                apply()
//            }
//        }.onFailure {
//            SygicContext.getInstance().log(TAG, it.message, LogConnector.LogLevel.Warn)
//        }
//    }
//
//    override fun getInt(key: String, defaultValue: Int?): Int? {
//        val prefs = getPreferences()
//        return if (prefs.contains(key)) prefs.getInt(key, defaultValue ?: 0) else null
//    }
//
//    override fun getLong(key: String, defaultValue: Long?): Long? {
//        val prefs = getPreferences()
//        return if (prefs.contains(key)) prefs.getLong(key, defaultValue ?: 0L) else null
//    }
//
//    override fun getString(key: String, defaultValue: String?): String? {
//        return getPreferences().getString(key, defaultValue)
//    }
//
//    override fun setInt(key: String, value: Int) {
//        getPreferences().edit().putInt(key, value).apply()
//    }
//
//    override fun setLong(key: String, value: Long) {
//        getPreferences().edit().putLong(key, value).apply()
//    }
//
//    override fun setString(key: String, value: String) {
//        getPreferences().edit().putString(key, value).apply()
//    }
//}