package my.passman.util

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import my.passman.data.AppTheme
import javax.inject.Inject

/**
 * Tells the system which theme the app uses. The system draws the splash screen before any app
 * code runs, so on API 31+ this is the only way for it to follow the in-app theme instead of the
 * system one. Older versions keep following the system theme.
 */
class AppNightMode @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun set(theme: AppTheme) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val mode =
            when (theme) {
                AppTheme.LIGHT -> UiModeManager.MODE_NIGHT_NO
                AppTheme.DARK -> UiModeManager.MODE_NIGHT_YES
                AppTheme.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            }
        context.getSystemService(UiModeManager::class.java).setApplicationNightMode(mode)
    }
}
