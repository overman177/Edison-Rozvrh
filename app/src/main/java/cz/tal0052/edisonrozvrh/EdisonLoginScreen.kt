package cz.tal0052.edisonrozvrh

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EdisonLoginScreen(
    onScheduleLoaded: (List<Lesson>) -> Unit
) {

    AndroidView(
        factory = { context ->

            WebView(context).apply {

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true

                var loadStarted = false

                webViewClient = object : WebViewClient() {

                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (loadStarted) return
                        if (url.isNullOrBlank()) return
                        if (!url.contains("/wps/myportal")) return

                        val cookies = CookieManager.getInstance()
                            .getCookie("https://edison.sso.vsb.cz")

                        if (cookies.isNullOrBlank()) return

                        loadStarted = true

                        Thread {
                            val lessons = EdisonRepository.downloadDetailedSchedule(cookies)

                            if (lessons.isNullOrEmpty()) {
                                loadStarted = false
                                return@Thread
                            }

                            saveSchedule(context, lessons)
                            view?.post {
                                onScheduleLoaded(lessons)
                            }
                        }.start()
                    }
                }

                loadUrl("https://edison.sso.vsb.cz/wps/myportal")
            }
        }
    )
}
