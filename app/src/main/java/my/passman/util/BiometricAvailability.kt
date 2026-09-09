package my.passman.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class BiometricAvailability @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun isAvailable(): Boolean = BiometricAuthenticator.isAvailable(context)
}
