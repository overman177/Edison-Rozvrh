package cz.tal0052.edisonrozvrh.ui.auth

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
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
import cz.tal0052.edisonrozvrh.data.auth.EdisonCredentials
import cz.tal0052.edisonrozvrh.data.auth.loadEdisonCredentials
import cz.tal0052.edisonrozvrh.data.auth.saveEdisonCredentials
import cz.tal0052.edisonrozvrh.data.parser.CurrentResultsData
import cz.tal0052.edisonrozvrh.data.parser.EdisonParser
import cz.tal0052.edisonrozvrh.data.parser.StudyInfoData
import cz.tal0052.edisonrozvrh.data.repository.EdisonRepository
import org.json.JSONArray
import org.json.JSONObject

private const val EDISON_PORTAL_URL = "https://edison.sso.vsb.cz/wps/myportal"
private const val WEB_CREDIT_MENU_URL = "https://stravovani.vsb.cz/webkredit/Ordering/Menu"
private const val WEB_CREDIT_TIMEOUT_MS = 20000L
private const val WEB_CREDIT_INTERACTIVE_TIMEOUT_MS = 180000L
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

private class EdisonCredentialBridge(
    private val onCredentialsCaptured: (EdisonCredentials) -> Unit
) {
    @JavascriptInterface
    fun cacheCredentials(username: String?, password: String?) {
        val normalizedUsername = username?.trim().orEmpty()
        val normalizedPassword = password.orEmpty()
        if (normalizedUsername.isBlank() || normalizedPassword.isBlank()) return

        onCredentialsCaptured(
            EdisonCredentials(
                username = normalizedUsername,
                password = normalizedPassword
            )
        )
    }
}

private fun isSsoLoginUrl(url: String): Boolean {
    return runCatching {
        val uri = Uri.parse(url)
        val host = uri.host.orEmpty()
        host.contains("sso.vsb.cz", ignoreCase = true) &&
            uri.path.orEmpty().contains("/login", ignoreCase = true)
    }.getOrDefault(false)
}

private fun buildSsoCredentialScript(
    credentials: EdisonCredentials?,
    autoSubmit: Boolean
): String {
    val username = JSONObject.quote(credentials?.username.orEmpty())
    val password = JSONObject.quote(credentials?.password.orEmpty())

    return """
(function() {
  const savedUsername = $username;
  const savedPassword = $password;
  const shouldAutoSubmit = ${if (autoSubmit) "true" else "false"};
  const maxAttempts = 20;
  let attempts = 0;

  function first(selectors) {
    for (const selector of selectors) {
      const element = document.querySelector(selector);
      if (element) return element;
    }
    return null;
  }

  function setValue(field, value) {
    const valueDescriptor = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value");
    if (valueDescriptor && valueDescriptor.set) {
      valueDescriptor.set.call(field, value);
    } else {
      field.value = value;
    }
    field.dispatchEvent(new Event("input", { bubbles: true }));
    field.dispatchEvent(new Event("change", { bubbles: true }));
  }

  function capture(userField, passwordField) {
    const usernameValue = (userField.value || "").trim();
    const passwordValue = passwordField.value || "";
    if (!usernameValue || !passwordValue) return;
    if (window.EdisonCredentialBridge && window.EdisonCredentialBridge.cacheCredentials) {
      window.EdisonCredentialBridge.cacheCredentials(usernameValue, passwordValue);
    }
  }

  function hook(userField, passwordField) {
    if (userField.dataset.edisonCredentialHooked === "1") return;
    userField.dataset.edisonCredentialHooked = "1";
    passwordField.dataset.edisonCredentialHooked = "1";

    const submitHandler = function() {
      capture(userField, passwordField);
    };

    userField.addEventListener("change", submitHandler, true);
    passwordField.addEventListener("change", submitHandler, true);
    passwordField.addEventListener("keyup", function(event) {
      if (event.key === "Enter") submitHandler();
    }, true);

    const form = passwordField.form || userField.form || document.querySelector("form");
    if (form && form.dataset.edisonCredentialHooked !== "1") {
      form.dataset.edisonCredentialHooked = "1";
      form.addEventListener("submit", submitHandler, true);
    }

    document.querySelectorAll('button[type="submit"], input[type="submit"]').forEach(function(button) {
      if (button.dataset.edisonCredentialHooked === "1") return;
      button.dataset.edisonCredentialHooked = "1";
      button.addEventListener("click", submitHandler, true);
    });
  }

  function submit(userField, passwordField) {
    const form = passwordField.form || userField.form || document.querySelector("form");
    const submitButton = form
      ? form.querySelector('button[type="submit"], input[type="submit"]')
      : document.querySelector('button[type="submit"], input[type="submit"]');

    capture(userField, passwordField);

    if (submitButton && typeof submitButton.click === "function") {
      submitButton.click();
      return;
    }

    if (form && typeof form.requestSubmit === "function") {
      form.requestSubmit();
      return;
    }

    if (form && typeof form.submit === "function") {
      form.submit();
    }
  }

  function init() {
    const userField = first([
      'input[autocomplete="username"]',
      'input[name="username"]',
      'input[name="user"]',
      'input[name="j_username"]',
      'input[id="username"]',
      'input[id*="user"]',
      'input[type="email"]',
      'input[type="text"]'
    ]);
    const passwordField = first([
      'input[autocomplete="current-password"]',
      'input[name="password"]',
      'input[name="pass"]',
      'input[name="j_password"]',
      'input[id="password"]',
      'input[id*="pass"]',
      'input[type="password"]'
    ]);

    if (!userField || !passwordField) {
      attempts += 1;
      if (attempts < maxAttempts) {
        window.setTimeout(init, 150);
      }
      return;
    }

    hook(userField, passwordField);
    capture(userField, passwordField);

    const shouldFill = !!savedUsername && !!savedPassword &&
      (!userField.value || !passwordField.value || shouldAutoSubmit);

    if (shouldFill) {
      setValue(userField, savedUsername);
      setValue(passwordField, savedPassword);
      capture(userField, passwordField);
    }

    if (shouldAutoSubmit && document.body.dataset.edisonAutoLoginDone !== "1") {
      document.body.dataset.edisonAutoLoginDone = "1";
      window.setTimeout(function() {
        submit(userField, passwordField);
      }, 150);
    }
  }

  init();
})();
"""
}

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
                val storedCredentials = loadEdisonCredentials(appContext)
                var resolved = false
                var authAttempted = false
                var autoLoginAttempted = false
                var pageVersion = 0
                var authRequiredReported = false
                lateinit var timeoutRunnable: Runnable

                fun finish() {
                    if (resolved) return
                    resolved = true
                    removeCallbacks(timeoutRunnable)
                    stopLoading()
                    latestOnFinished()
                }

                fun scheduleTimeout(interactive: Boolean = authRequiredReported && allowInteractiveAuth) {
                    removeCallbacks(timeoutRunnable)
                    val timeoutMs = if (interactive) {
                        WEB_CREDIT_INTERACTIVE_TIMEOUT_MS
                    } else {
                        WEB_CREDIT_TIMEOUT_MS
                    }
                    postDelayed(timeoutRunnable, timeoutMs)
                }

                fun tryHtmlFallback() {
                    if (resolved) return

                    evaluateJavascript(WEB_CREDIT_HTML_SCRIPT) { htmlResult ->
                        if (resolved) return@evaluateJavascript

                        val html = decodeEvaluateJavascriptString(htmlResult)
                        val webCredit = html?.let { EdisonParser.parseWebCredit(it) }

                        if (webCredit?.balance != null) {
                            saveWebCredit(appContext, webCredit)
                        }
                        finish()
                    }
                }

                fun evaluateMenuPage() {
                    if (resolved) return

                    evaluateJavascript(WEB_CREDIT_MODEL_SCRIPT) { modelResult ->
                        if (resolved) return@evaluateJavascript

                        val modelJson = decodeEvaluateJavascriptString(modelResult)
                        val ssoUrl = extractWebCreditSsoUrl(modelJson)
                        val webCredit = modelJson?.let { EdisonParser.parseWebCreditModelJson(it) }

                        if (webCredit?.balance != null) {
                            saveWebCredit(appContext, webCredit)
                            cookieManager.flush()
                            finish()
                        } else if (!authAttempted && !ssoUrl.isNullOrBlank()) {
                            authAttempted = true
                            loadUrl(ssoUrl)
                        } else {
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
                        if (resolved) return
                        if (url.isNullOrBlank()) return

                        if (url.contains("www.sso.vsb.cz/login", ignoreCase = true)) {
                            val shouldAutoSubmit = storedCredentials != null && !autoLoginAttempted
                            if (shouldAutoSubmit) {
                                autoLoginAttempted = true
                                scheduleTimeout(interactive = true)
                                view?.evaluateJavascript(
                                    buildSsoCredentialScript(
                                        credentials = storedCredentials,
                                        autoSubmit = true
                                    ),
                                    null
                                )
                                return
                            }

                            if (authRequiredReported) return
                            authRequiredReported = true
                            if (allowInteractiveAuth) {
                                scheduleTimeout(interactive = true)
                                latestOnAuthRequired()
                            } else {
                                finish()
                            }
                            return
                        }

                        if (!url.contains("/webkredit/Ordering/Menu", ignoreCase = true)) {
                            scheduleTimeout()
                            return
                        }

                        pageVersion += 1
                        val currentVersion = pageVersion
                        scheduleTimeout()
                        postDelayed({
                            if (!resolved && currentVersion == pageVersion) {
                                evaluateMenuPage()
                            }
                        }, 300L)
                    }
                }

                timeoutRunnable = Runnable { finish() }
                scheduleTimeout()
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
                val appContext = context.applicationContext
                val cookieManager = CookieManager.getInstance()
                val storedCredentials = loadEdisonCredentials(appContext)

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                var edisonStarted = false
                var deliveryDone = false
                var autoLoginAttempted = false
                var pendingCredentials: EdisonCredentials? = null

                addJavascriptInterface(
                    EdisonCredentialBridge { credentials ->
                        pendingCredentials = credentials
                    },
                    "EdisonCredentialBridge"
                )

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (deliveryDone) return
                        if (url.isNullOrBlank()) return

                        if (isSsoLoginUrl(url)) {
                            val shouldAutoSubmit = storedCredentials != null && !autoLoginAttempted
                            if (shouldAutoSubmit) {
                                autoLoginAttempted = true
                            }

                            view?.evaluateJavascript(
                                buildSsoCredentialScript(
                                    credentials = storedCredentials,
                                    autoSubmit = shouldAutoSubmit
                                ),
                                null
                            )
                            return
                        }

                        if (edisonStarted) return
                        if (!url.contains("/wps/myportal")) return

                        val cookies = cookieManager.getCookie("https://edison.sso.vsb.cz")
                        if (cookies.isNullOrBlank()) return

                        edisonStarted = true
                        pendingCredentials?.let { credentials ->
                            saveEdisonCredentials(
                                context = appContext,
                                username = credentials.username,
                                password = credentials.password
                            )
                        }
                        cookieManager.flush()

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
