package cz.tal0052.edisonrozvrh.data.repository

import cz.tal0052.edisonrozvrh.data.parser.RoundcubeInboxPage
import cz.tal0052.edisonrozvrh.data.parser.RoundcubeInboxParser
import cz.tal0052.edisonrozvrh.data.parser.RoundcubeLoginParser
import cz.tal0052.edisonrozvrh.data.parser.RoundcubeMailboxShellParser
import cz.tal0052.edisonrozvrh.data.parser.RoundcubeMessageDetail
import cz.tal0052.edisonrozvrh.data.parser.RoundcubeMessageDetailParser
import cz.tal0052.edisonrozvrh.data.parser.RoundcubeRemoteListParser
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class RoundcubeTransportResponse(
    val code: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap()
)

interface RoundcubeLoginTransport {
    fun get(url: String, refererUrl: String? = null): RoundcubeTransportResponse
    fun post(request: RoundcubeLoginRequest): RoundcubeTransportResponse
}

data class RoundcubeLoginResult(
    val success: Boolean,
    val usernameUsed: String,
    val statusCode: Int,
    val responseHtml: String,
    val responseHeaders: Map<String, String>,
    val errorMessage: String? = null
)

data class RoundcubeInboxFetchResult(
    val success: Boolean,
    val usernameUsed: String,
    val statusCode: Int,
    val inboxPage: RoundcubeInboxPage? = null,
    val responseHtml: String,
    val responseHeaders: Map<String, String>,
    val errorMessage: String? = null
)

data class RoundcubeMessageDetailFetchResult(
    val success: Boolean,
    val usernameUsed: String,
    val statusCode: Int,
    val messageDetail: RoundcubeMessageDetail? = null,
    val responseHtml: String,
    val responseHeaders: Map<String, String>,
    val errorMessage: String? = null
)

data class RoundcubeMessageActionResult(
    val success: Boolean,
    val usernameUsed: String,
    val statusCode: Int,
    val errorMessage: String? = null
)

class RoundcubeLoginClient(
    private val transport: RoundcubeLoginTransport,
    private val loginUrl: String = "https://posta.vsb.cz/roundcube/?_task=login",
    private val inboxUrl: String = "https://posta.vsb.cz/roundcube/?_task=mail&_mbox=INBOX"
) {
    fun login(username: String, password: String): RoundcubeLoginResult {
        val attempts = buildUsernameAttempts(username)
        var lastFailure: RoundcubeLoginResult? = null

        attempts.forEach { candidate ->
            val result = loginOnce(candidate, password)
            if (result.success) return result
            lastFailure = result
        }

        return lastFailure ?: RoundcubeLoginResult(
            success = false,
            usernameUsed = username.trim(),
            statusCode = 0,
            responseHtml = "",
            responseHeaders = emptyMap(),
            errorMessage = "Roundcube login request nejde sestavit bez credentials."
        )
    }

    fun loginAndFetchInbox(
        username: String,
        password: String,
        page: Int = 1
    ): RoundcubeInboxFetchResult {
        val loginResult = login(username, password)
        if (!loginResult.success) {
            return RoundcubeInboxFetchResult(
                success = false,
                usernameUsed = loginResult.usernameUsed,
                statusCode = loginResult.statusCode,
                responseHtml = loginResult.responseHtml,
                responseHeaders = loginResult.responseHeaders,
                errorMessage = loginResult.errorMessage
            )
        }

        val inboxResponse = transport.get(inboxUrl, refererUrl = loginUrl)
        if (inboxResponse.code !in 200..299) {
            return RoundcubeInboxFetchResult(
                success = false,
                usernameUsed = loginResult.usernameUsed,
                statusCode = inboxResponse.code,
                responseHtml = inboxResponse.body,
                responseHeaders = inboxResponse.headers,
                errorMessage = "Roundcube inbox request selhal."
            )
        }

        val inboxPage = RoundcubeInboxParser.parse(inboxResponse.body)
            ?: return RoundcubeInboxFetchResult(
                success = false,
                usernameUsed = loginResult.usernameUsed,
                statusCode = inboxResponse.code,
                responseHtml = inboxResponse.body,
                responseHeaders = inboxResponse.headers,
                errorMessage = "Roundcube inbox ma neocekavanou strukturu."
            )

        if (transport is RoundcubeRemoteListTransport) {
            val shellPage = RoundcubeMailboxShellParser.parse(inboxResponse.body)
            if (shellPage != null) {
                val remotePage = fetchRemoteInboxPage(shellPage, page)
                if (remotePage != null) {
                    return RoundcubeInboxFetchResult(
                        success = true,
                        usernameUsed = loginResult.usernameUsed,
                        statusCode = inboxResponse.code,
                        inboxPage = remotePage,
                        responseHtml = inboxResponse.body,
                        responseHeaders = inboxResponse.headers
                    )
                }
            }
        }

        return RoundcubeInboxFetchResult(
            success = true,
            usernameUsed = loginResult.usernameUsed,
            statusCode = inboxResponse.code,
            inboxPage = inboxPage,
            responseHtml = inboxResponse.body,
            responseHeaders = inboxResponse.headers
        )
    }

    fun fetchMessageDetail(
        username: String,
        password: String,
        detailUrl: String,
        loadRemoteContent: Boolean = false
    ): RoundcubeMessageDetailFetchResult {
        val loginResult = login(username, password)
        if (!loginResult.success) {
            return RoundcubeMessageDetailFetchResult(
                success = false,
                usernameUsed = loginResult.usernameUsed,
                statusCode = loginResult.statusCode,
                responseHtml = loginResult.responseHtml,
                responseHeaders = loginResult.responseHeaders,
                errorMessage = loginResult.errorMessage
            )
        }

        val resolvedDetailUrl = resolveDetailUrl(detailUrl, loadRemoteContent)
        val detailResponse = transport.get(resolvedDetailUrl, refererUrl = inboxUrl)
        if (detailResponse.code !in 200..299) {
            return RoundcubeMessageDetailFetchResult(
                success = false,
                usernameUsed = loginResult.usernameUsed,
                statusCode = detailResponse.code,
                responseHtml = detailResponse.body,
                responseHeaders = detailResponse.headers,
                errorMessage = "Roundcube detail zpravy se nepodarilo nacist."
            )
        }

        val detail = RoundcubeMessageDetailParser.parse(detailResponse.body)
            ?: return RoundcubeMessageDetailFetchResult(
                success = false,
                usernameUsed = loginResult.usernameUsed,
                statusCode = detailResponse.code,
                responseHtml = detailResponse.body,
                responseHeaders = detailResponse.headers,
                errorMessage = "Roundcube detail zpravy ma neocekavanou strukturu."
            )

        return RoundcubeMessageDetailFetchResult(
            success = true,
            usernameUsed = loginResult.usernameUsed,
            statusCode = detailResponse.code,
            messageDetail = detail,
            responseHtml = detailResponse.body,
            responseHeaders = detailResponse.headers
        )
    }

    private fun loginOnce(username: String, password: String): RoundcubeLoginResult {
        val loginPageResponse = transport.get(inboxUrl, refererUrl = loginUrl)
        if (loginPageResponse.code !in 200..299) {
            return RoundcubeLoginResult(
                success = false,
                usernameUsed = username,
                statusCode = loginPageResponse.code,
                responseHtml = loginPageResponse.body,
                responseHeaders = loginPageResponse.headers,
                errorMessage = "Roundcube login page request selhal."
            )
        }

        if (RoundcubeInboxParser.parse(loginPageResponse.body) != null) {
            return RoundcubeLoginResult(
                success = true,
                usernameUsed = username,
                statusCode = loginPageResponse.code,
                responseHtml = loginPageResponse.body,
                responseHeaders = loginPageResponse.headers
            )
        }

        val loginPage = RoundcubeLoginParser.parse(loginPageResponse.body)
            ?: return RoundcubeLoginResult(
                success = false,
                usernameUsed = username,
                statusCode = loginPageResponse.code,
                responseHtml = loginPageResponse.body,
                responseHeaders = loginPageResponse.headers,
                errorMessage = "Roundcube login page ma neocekavanou strukturu."
            )

        val loginRequest = RoundcubeLoginRequestBuilder.build(loginPage, username, password)
            ?: return RoundcubeLoginResult(
                success = false,
                usernameUsed = username,
                statusCode = 0,
                responseHtml = "",
                responseHeaders = emptyMap(),
                errorMessage = "Roundcube login request nejde sestavit bez credentials."
            )

        val loginResponse = transport.post(loginRequest)
        val returnedLoginPage = RoundcubeLoginParser.parse(loginResponse.body)
        val rejected = loginResponse.code >= 400 || returnedLoginPage != null

        return RoundcubeLoginResult(
            success = !rejected,
            usernameUsed = username,
            statusCode = loginResponse.code,
            responseHtml = loginResponse.body,
            responseHeaders = loginResponse.headers,
            errorMessage = if (rejected) "Roundcube login nebyl prijat." else null
        )
    }

    fun setMessageFlag(
        username: String,
        password: String,
        uid: String,
        flag: String
    ): RoundcubeMessageActionResult {
        val loginResult = login(username, password)
        if (!loginResult.success) {
            return RoundcubeMessageActionResult(
                success = false,
                usernameUsed = loginResult.usernameUsed,
                statusCode = loginResult.statusCode,
                errorMessage = loginResult.errorMessage
            )
        }

        val actionTransport = transport as? RoundcubeMessageActionTransport
            ?: return RoundcubeMessageActionResult(
                success = false,
                usernameUsed = loginResult.usernameUsed,
                statusCode = 0,
                errorMessage = "Roundcube transport nepodporuje message actions."
            )

        val shellResponse = transport.get(inboxUrl, refererUrl = loginUrl)
        if (shellResponse.code !in 200..299) {
            return RoundcubeMessageActionResult(
                success = false,
                usernameUsed = loginResult.usernameUsed,
                statusCode = shellResponse.code,
                errorMessage = "Roundcube inbox shell pro message action selhal."
            )
        }

        val shellPage = RoundcubeMailboxShellParser.parse(shellResponse.body)
            ?: return RoundcubeMessageActionResult(
                success = false,
                usernameUsed = loginResult.usernameUsed,
                statusCode = shellResponse.code,
                errorMessage = "Roundcube inbox shell ma neocekavanou strukturu."
            )

        val actionResponse = actionTransport.markMessage(
            shellPage = shellPage,
            uid = uid,
            flag = flag
        )

        return RoundcubeMessageActionResult(
            success = actionResponse.code in 200..299,
            usernameUsed = loginResult.usernameUsed,
            statusCode = actionResponse.code,
            errorMessage = if (actionResponse.code in 200..299) null else "Roundcube mark action selhala."
        )
    }

    private fun buildUsernameAttempts(username: String): List<String> {
        val trimmed = username.trim()
        if (trimmed.isBlank()) return emptyList()

        return listOf(
            trimmed,
            trimmed.uppercase(Locale.ROOT)
        ).distinct()
    }

    private fun fetchRemoteInboxPage(
        shellPage: cz.tal0052.edisonrozvrh.data.parser.RoundcubeMailboxShellPage,
        page: Int
    ): RoundcubeInboxPage? {
        val remoteTransport = transport as? RoundcubeRemoteListTransport ?: return null

        val firstResponse = remoteTransport.fetchRemoteList(
            shellPage = shellPage,
            page = page,
            includeRefresh = false
        )
        if (firstResponse.code in 200..299) {
            val firstPage = RoundcubeRemoteListParser.parse(
                jsonText = firstResponse.body,
                fallbackFolders = shellPage.folders,
                mailbox = shellPage.mailbox
            )
            if (firstPage != null && (firstPage.messages.isNotEmpty() || firstPage.countText.isNotBlank())) {
                return firstPage
            }
        }

        val refreshResponse = remoteTransport.fetchRemoteList(
            shellPage = shellPage,
            page = page,
            includeRefresh = true
        )
        if (refreshResponse.code !in 200..299) return null

        return RoundcubeRemoteListParser.parse(
            jsonText = refreshResponse.body,
            fallbackFolders = shellPage.folders,
            mailbox = shellPage.mailbox
        )
    }

    private fun resolveDetailUrl(detailUrl: String, loadRemoteContent: Boolean): String {
        if (!loadRemoteContent) return detailUrl

        val httpUrl = detailUrl.toHttpUrlOrNull() ?: return detailUrl
        return httpUrl.newBuilder()
            .setQueryParameter("_safe", "1")
            .build()
            .toString()
    }
}
