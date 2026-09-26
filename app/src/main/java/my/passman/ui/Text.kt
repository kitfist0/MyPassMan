package my.passman.ui

import android.content.Context
import androidx.annotation.StringRes

/**
 * A UI text a ViewModel can build without holding a [Context] itself — either a literal string
 * or a string resource (with format args), resolved later at the UI layer via [resolve]. Lets
 * events like [AppEvent.ShowToast] carry either kind interchangeably, and resource-backed ones
 * localize automatically.
 */
sealed class Text {
    data class Plain(
        val value: String,
    ) : Text()

    data class Resource(
        @StringRes val resId: Int,
        val args: List<Any> = emptyList(),
    ) : Text()

    fun resolve(context: Context): String =
        when (this) {
            is Plain -> value
            is Resource -> context.getString(resId, *args.toTypedArray())
        }
}

fun String.asText(): Text = Text.Plain(this)

fun textOf(
    @StringRes resId: Int,
    vararg args: Any,
): Text = Text.Resource(resId, args.toList())
