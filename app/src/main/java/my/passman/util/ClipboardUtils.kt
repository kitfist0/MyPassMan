package my.passman.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast

object ClipboardUtils {
    fun copyToClipboard(
        context: Context,
        text: String,
        label: String = "text",
        isSensitive: Boolean = false,
        toastMessage: String? = null
    ) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)

        if (isSensitive) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }

        clipboard.setPrimaryClip(clip)

        // On Android 13+ (API 33), the system shows its own confirmation UI.
        // We only show a Toast manually on older versions.
        if (toastMessage != null && Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
        }
    }
}
