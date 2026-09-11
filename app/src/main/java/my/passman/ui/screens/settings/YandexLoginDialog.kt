package my.passman.ui.screens.settings

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import my.passman.R

/**
 * Hosts the Yandex ID implicit OAuth flow in a WebView: loads [authorizeUrl], and once
 * the page navigates to [redirectUri] (which Yandex never actually serves — the
 * `access_token` arrives in that URL's fragment), intercepts the navigation and reports
 * the token via [onToken] instead of letting the WebView try to load it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YandexLoginDialog(
    authorizeUrl: String,
    redirectUri: String,
    onToken: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.yandex_login_title)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                        }
                    },
                )
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            webViewClient =
                                object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: WebResourceRequest,
                                    ): Boolean {
                                        val url = request.url.toString()
                                        if (url.startsWith(redirectUri)) {
                                            val token = extractAccessToken(url)
                                            if (token != null) onToken(token) else onDismiss()
                                            return true
                                        }
                                        return false
                                    }
                                }
                            loadUrl(authorizeUrl)
                        }
                    },
                )
            }
        }
    }
}

private fun extractAccessToken(redirectUrl: String): String? {
    // Yandex returns access_token in the URL fragment (after '#'), not the query string.
    val fragment = redirectUrl.substringAfter('#', "")
    if (fragment.isEmpty()) return null
    return fragment
        .split('&')
        .mapNotNull { param ->
            val parts = param.split('=', limit = 2)
            if (parts.size == 2) parts[0] to Uri.decode(parts[1]) else null
        }.toMap()["access_token"]
}
