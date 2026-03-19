package cz.tal0052.edisonrozvrh.ui.auth

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import cz.tal0052.edisonrozvrh.app.Lesson
import cz.tal0052.edisonrozvrh.app.saveCurrentResults
import cz.tal0052.edisonrozvrh.app.saveSchedule
import cz.tal0052.edisonrozvrh.app.saveStudyInfo
import cz.tal0052.edisonrozvrh.app.saveWebCredit
import cz.tal0052.edisonrozvrh.data.parser.CurrentResultsData
import cz.tal0052.edisonrozvrh.data.parser.EdisonParser
import cz.tal0052.edisonrozvrh.data.parser.StudyInfoData
import cz.tal0052.edisonrozvrh.data.repository.EdisonRepository
import org.json.JSONArray
import org.json.JSONObject

private const val EDISON_PORTAL_URL = "https://edison.sso.vsb.cz/wps/myportal"
private const val WEB_CREDIT_MENU_URL = "https://stravovani.vsb.cz/webkredit/Ordering/Menu"
private const val WEB_CREDIT_TIMEOUT_MS = 20000L
private const val WEB_CREDIT_LOG_TAG = "WebCreditSync"
private const val WEB_CREDIT_MODEL_SCRIPT = """
(function() {
  try {
    if (!window.wkIndexModel) return null;
    return JSON.stringify(window.wkIndexModel);
  } catch (e) {
    return null;
  }
})();
"""
private const val WEB_CREDIT_HTML_SCRIPT = """
(function() {
  try {
    return document.documentElement.outerHTML;
  } catch (e) {
    return null;
  }
})();
"""

private fun decodeEvaluateJavascriptString(result: String?): String? {
    if (result.isNullOrBlank() || result == "null") return null
    return runCatching {
        JSONArray("[$result]").getString(0)
    }.getOrNull()
}

private fun extractWebCreditSsoUrl(json: String?): String? {
    if (json.isNullOrBlank()) return null
    return runCatching {
        val root = JSONObject(json)
        val model = root.optJSONObject("model") ?: root
        val app = model.optJSONObject("app")
        app?.optString("ssoButtonUrl")?.trim().orEmpty().ifBlank { null }
    }.getOrNull()
}

private fun debugWebCreditBalanceNode(json: String?): String {
    if (json.isNullOrBlank()) return "null"
    return runCatching {
        val root = JSONObject(json)
        val model = root.optJSONObject("model") ?: root
        model.opt("balance")?.toString().orEmpty().ifBlank { "missing" }
    }.getOrElse { error -> "json_error:${error.javaClass.simpleName}" }
}

private fun debugWebCreditHtmlSnippet(html: String?): String {
    if (html.isNullOrBlank()) return "null"
    val marker = "window.wkIndexModel"
    val index = html.indexOf(marker)
    if (index < 0) return "marker_missing"

    val end = minOf(html.length, index + 300)
    return html.substring(index, end)
        .replace(Regex("\\s+"), " ")
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebCreditSyncView(
    modifier: Modifier,
    allowInteractiveAuth: Boolean,
    onAuthRequired: () -> Unit,
    onFinished: () -> Unit
) {
    val latestOnAuthRequired by rememberUpdatedState(onAuthRequired)
    val latestOnFinished by rememberUpdatedState(onFinished)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                val appContext = context.applicationContext
                val cookieManager = CookieManager.getInstance()
                var resolved = false
                var authAttempted = false
                var pageVersion = 0
                var authRequiredReported = false

                fun finish(reason: String) {
                    if (resolved) return
                    resolved = true
                    Log.d(WEB_CREDIT_LOG_TAG, "finish: $reason")
                    stopLoading()
                    latestOnFinished()
                }

                fun tryHtmlFallback() {
                    if (resolved) return

                    evaluateJavascript(WEB_CREDIT_HTML_SCRIPT) { htmlResult ->
                        if (resolved) return@evaluateJavascript

                        val html = decodeEvaluateJavascriptString(htmlResult)
                        Log.d(WEB_CREDIT_LOG_TAG, "html callback length=${html?.length ?: 0}")
                        Log.d(WEB_CREDIT_LOG_TAG, "html snippet=${debugWebCreditHtmlSnippet(html)}")
                        val webCredit = html?.let { EdisonParser.parseWebCredit(it) }

                        if (webCredit?.balance != null) {
                            saveWebCredit(appContext, webCredit)
                            Log.d(
                                WEB_CREDIT_LOG_TAG,
                                "balance loaded from html: balance=${webCredit.balance} currency=${webCredit.currencyCode}"
                            )
                            finish("success_html")
                        } else {
                            Log.d(WEB_CREDIT_LOG_TAG, "html parser returned no balance, finishing as parse_failed")
                            finish("parse_failed")
                        }
                    }
                }

                fun evaluateMenuPage() {
                    if (resolved) return

                    evaluateJavascript(WEB_CREDIT_MODEL_SCRIPT) { modelResult ->
                        if (resolved) return@evaluateJavascript

                        val modelJson = decodeEvaluateJavascriptString(modelResult)
                        val ssoUrl = extractWebCreditSsoUrl(modelJson)
                        Log.d(WEB_CREDIT_LOG_TAG, "window callback length=${modelJson?.length ?: 0}")
                        Log.d(WEB_CREDIT_LOG_TAG, "window balance node=${debugWebCreditBalanceNode(modelJson)}")
                        val webCredit = modelJson?.let { EdisonParser.parseWebCreditModelJson(it) }

                        if (webCredit?.balance != null) {
                            saveWebCredit(appContext, webCredit)
                            Log.d(
                                WEB_CREDIT_LOG_TAG,
                                "balance loaded from window: balance=${webCredit.balance} currency=${webCredit.currencyCode}"
                            )
                            finish("success_window")
                        } else if (!authAttempted && !ssoUrl.isNullOrBlank()) {
                            authAttempted = true
                            Log.d(WEB_CREDIT_LOG_TAG, "window model returned no balance, loading ssoButtonUrl=$ssoUrl")
                            loadUrl(ssoUrl)
                        } else {
                            Log.d(WEB_CREDIT_LOG_TAG, "window model returned no balance, switching to html fallback")
                            tryHtmlFallback()
                        }
                    }
                }

                cookieManager.setAcceptCookie(true)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        Log.d(
                            WEB_CREDIT_LOG_TAG,
                            "page finished: $url cookies=${cookieManager.getCookie(WEB_CREDIT_MENU_URL).orEmpty().length}"
                        )
                        if (resolved) return
                        if (url.isNullOrBlank()) return

                        if (url.contains("www.sso.vsb.cz/login", ignoreCase = true) && !authRequiredReported) {
                            authRequiredReported = true
                            if (allowInteractiveAuth) {
                                latestOnAuthRequired()
                            } else {
                                finish("auth_required")
                            }
                            return
                        }

                        if (!url.contains("/webkredit/Ordering/Menu", ignoreCase = true)) return

                        pageVersion += 1
                        val currentVersion = pageVersion
                        postDelayed({
                            if (!resolved && currentVersion == pageVersion) {
                                evaluateMenuPage()
                            }
                        }, 300L)
                    }
                }

                postDelayed({ finish("timeout") }, WEB_CREDIT_TIMEOUT_MS)
                loadUrl(WEB_CREDIT_MENU_URL)
            }
        }
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EdisonLoginScreen(
    onDataLoaded: (List<Lesson>, CurrentResultsData?, StudyInfoData?) -> Unit
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true

                var edisonStarted = false
                var deliveryDone = false

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (deliveryDone) return
                        if (url.isNullOrBlank()) return
                        if (edisonStarted) return
                        if (!url.contains("/wps/myportal")) return

                        val cookies = CookieManager.getInstance()
                            .getCookie("https://edison.sso.vsb.cz")

                        if (cookies.isNullOrBlank()) return

                        edisonStarted = true

                        Thread {
                            val lessons = EdisonRepository.downloadDetailedSchedule(cookies)

                            if (lessons.isNullOrEmpty()) {
                                edisonStarted = false
                                return@Thread
                            }

                            val currentResults = EdisonRepository.downloadCurrentResults(cookies)
                            val studyInfo = EdisonRepository.downloadStudyInfo(cookies)

                            saveSchedule(context, lessons)
                            if (currentResults != null) {
                                saveCurrentResults(context, currentResults)
                            }
                            if (studyInfo != null) {
                                saveStudyInfo(context, studyInfo)
                            }

                            view?.post {
                                if (deliveryDone) return@post

                                deliveryDone = true
                                onDataLoaded(lessons, currentResults, studyInfo)
                            }
                        }.start()
                    }
                }

                loadUrl(EDISON_PORTAL_URL)
            }
        }
    )
}