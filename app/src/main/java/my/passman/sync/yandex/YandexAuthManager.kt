package my.passman.sync.yandex

import android.net.Uri
import my.passman.BuildConfig
import my.passman.data.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

sealed class YandexAuthResult {
    data class Authorized(
        val accessToken: String,
    ) : YandexAuthResult()

    /** No cached token — the user needs to complete the browser/WebView login flow. */
    data object LoginRequired : YandexAuthResult()
}

/**
 * Yandex ID uses an implicit OAuth flow for mobile apps: [authorizeUrl] is loaded in a
 * WebView, the user logs in, and Yandex redirects to [redirectUri] with `access_token`
 * in the URL fragment — no client secret or token-exchange call needed. The resulting
 * token is cached (encrypted) so most syncs, including background ones, never need to
 * show the WebView again.
 */
@Singleton
class YandexAuthManager @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    val authorizeUrl: String =
        "https://oauth.yandex.ru/authorize" +
            "?response_type=token" +
            "&client_id=${Uri.encode(BuildConfig.YANDEX_CLIENT_ID)}" +
            "&redirect_uri=${Uri.encode(BuildConfig.YANDEX_REDIRECT_URI)}"

    val redirectUri: String = BuildConfig.YANDEX_REDIRECT_URI

    suspend fun authorize(): YandexAuthResult {
        val token = settingsRepository.getYandexAccessToken()
        return if (token != null) YandexAuthResult.Authorized(token) else YandexAuthResult.LoginRequired
    }

    suspend fun saveToken(accessToken: String) {
        settingsRepository.setYandexAccessToken(accessToken)
    }
}
