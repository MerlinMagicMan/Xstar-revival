package io.xstarrevival.app.gs

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

internal enum class UnrealVideoStatus { LOADING, READY, ERROR }

internal data class UnrealVideoUiState(
    val status: UnrealVideoStatus = UnrealVideoStatus.LOADING,
    val detail: String? = null
)

private const val VIEWPORT_FIX_SCRIPT = """
    (() => {
        const applyViewportSize = () => {
            const height = window.innerHeight + "px";
            const sizeElement = (element) => {
                if (!element) return;
                element.style.width = "100%";
                element.style.height = height;
                element.style.minHeight = height;
                element.style.maxHeight = height;
            };

            sizeElement(document.documentElement);
            sizeElement(document.body);
            sizeElement(document.getElementById("playerUI"));
            sizeElement(document.getElementById("videoElementParent"));
            document.body.style.margin = "0";
            document.body.style.overflow = "hidden";

            const video = document.querySelector("video");
            sizeElement(video);
            if (video) {
                video.style.objectFit = "cover";
                video.style.display = "block";
            }
        };

        applyViewportSize();
        if (!window.__xstarViewportFixInstalled) {
            window.__xstarViewportFixInstalled = true;
            window.addEventListener("resize", applyViewportSize);
            new MutationObserver(applyViewportSize).observe(
                document.documentElement,
                { childList: true, subtree: true }
            );
        }
        return true;
    })();
"""

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
internal fun UnrealSimulatorVideo(
    streamUrl: String,
    modifier: Modifier = Modifier,
    onStateChanged: (UnrealVideoUiState) -> Unit
) {
    val playerUrl = simulatorPlayerUrl(streamUrl)
    val allowedOrigin = Uri.parse(playerUrl).let { "${it.scheme}://${it.encodedAuthority}" }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.BLACK)
                isFocusable = false
                isFocusableInTouchMode = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setOnTouchListener { _, _ -> true }
                setOnGenericMotionListener { _, _ -> true }
                setOnKeyListener { _, _, _ -> true }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportMultipleWindows(false)
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.safeBrowsingEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val requestedOrigin = request.url.let { "${it.scheme}://${it.encodedAuthority}" }
                        return requestedOrigin != allowedOrigin
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        view.evaluateJavascript(VIEWPORT_FIX_SCRIPT) {
                            onStateChanged(UnrealVideoUiState(UnrealVideoStatus.READY))
                        }
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError
                    ) {
                        if (request.isForMainFrame) {
                            onStateChanged(
                                UnrealVideoUiState(
                                    UnrealVideoStatus.ERROR,
                                    error.description?.toString() ?: "Unreal video endpoint unavailable"
                                )
                            )
                        }
                    }
                }
                onStateChanged(UnrealVideoUiState(UnrealVideoStatus.LOADING))
                loadUrl(playerUrl)
            }
        },
        update = { view ->
            if (view.url != playerUrl) {
                onStateChanged(UnrealVideoUiState(UnrealVideoStatus.LOADING))
                view.loadUrl(playerUrl)
            }
        },
        onRelease = { view ->
            view.stopLoading()
            view.loadUrl("about:blank")
            view.destroy()
        }
    )
}
