package cz.tal0052.edisonrozvrh.data.repository

import cz.tal0052.edisonrozvrh.data.parser.RoundcubeMailboxShellPage
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class RoundcubeOkHttpTransport : RoundcubeLoginTransport, RoundcubeRemoteListTransport, RoundcubeMessageActionTransport, RoundcubeCookieProvider {
    private val cookieJar = InMemoryCookieJar()
    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    override fun get(url: String, refererUrl: String?): RoundcubeTransportResponse {
        val request = baseRequest(url, refererUrl)
            .get()
            .build()

        return execute(request)
    }

    override fun post(request: RoundcubeLoginRequest): RoundcubeTransportResponse {
        val httpRequest = baseRequest(request.actionUrl, request.refererUrl)
            .header("Origin", "https://posta.vsb.cz")
            .post(request.toFormBody())
            .build()

        return execute(httpRequest)
    }

    override fun fetchRemoteList(
        shellPage: RoundcubeMailboxShellPage,
        includeRefresh: Boolean
    ): RoundcubeTransportResponse {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("posta.vsb.cz")
            .encodedPath("/roundcube/")
            .addQueryParameter("_task", "mail")
            .addQueryParameter("_action", "list")
            .addQueryParameter("_layout", shellPage.layout)
            .addQueryParameter("_mbox", shellPage.mailbox)
            .addQueryParameter("_page", shellPage.currentPage.toString())
            .addQueryParameter("_remote", "1")
            .addQueryParameter("_unlock", "0")
            .apply {
                if (includeRefresh) {
                    addQueryParameter("_refresh", "1")
                }
            }
            .build()

        val request = baseRequest(url.toString(), shellPage.commPath)
            .header("X-Roundcube-Request", shellPage.requestToken)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .build()

        return execute(request)
    }

    override fun markMessage(
        shellPage: RoundcubeMailboxShellPage,
        uid: String,
        flag: String
    ): RoundcubeTransportResponse {
        val url = shellPage.commPath.toHttpUrlOrNull()
            ?.newBuilder()
            ?.setQueryParameter("_action", "mark")
            ?.build()
            ?.toString()
            ?: shellPage.commPath

        val request = baseRequest(url, shellPage.commPath)
            .header("Origin", "https://posta.vsb.cz")
            .header("X-Roundcube-Request", shellPage.requestToken)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .post(
                RoundcubeLoginRequest(
                    actionUrl = url,
                    refererUrl = shellPage.commPath,
                    parameters = linkedMapOf(
                        "_uid" to uid,
                        "_flag" to flag,
                        "_mbox" to shellPage.mailbox,
                        "_remote" to "1",
                        "_quiet" to "1"
                    )
                ).toFormBody()
            )
            .build()

        return execute(request)
    }

    override fun cookiesFor(url: String): List<String> {
        return cookieJar.cookiesFor(url)
    }

    private fun baseRequest(url: String, refererUrl: String?): Request.Builder {
        return Request.Builder()
            .url(url)
            .header("User-Agent", ROUND_CUBE_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "cs-CZ,cs;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("Cache-Control", "no-cache")
            .apply {
                refererUrl?.let { header("Referer", it) }
            }
    }

    private fun execute(request: Request): RoundcubeTransportResponse {
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            val headers = response.headers.names().associateWith { name ->
                response.headers.values(name).joinToString("; ")
            }

            return RoundcubeTransportResponse(
                code = response.code,
                body = body,
                headers = headers
            )
        }
    }

    private class InMemoryCookieJar : CookieJar {
        private val cookies = mutableListOf<Cookie>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(this.cookies) {
                cookies.forEach { newCookie ->
                    this.cookies.removeAll { existing ->
                        existing.name == newCookie.name &&
                            existing.domain == newCookie.domain &&
                            existing.path == newCookie.path
                    }
                    this.cookies += newCookie
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return synchronized(cookies) {
                cookies.filter { it.matches(url) }
            }
        }

        fun cookiesFor(url: String): List<String> {
            val httpUrl = url.toHttpUrlOrNull() ?: return emptyList()

            return synchronized(cookies) {
                cookies
                    .filter { it.matches(httpUrl) }
                    .map { cookie -> "${cookie.name}=${cookie.value}" }
            }
        }
    }

    private companion object {
        private const val ROUND_CUBE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; EdisonRozvrh) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36"
    }
}
