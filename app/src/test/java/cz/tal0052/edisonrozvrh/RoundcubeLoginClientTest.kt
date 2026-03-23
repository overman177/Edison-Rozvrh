package cz.tal0052.edisonrozvrh

import cz.tal0052.edisonrozvrh.data.parser.RoundcubeInboxParser
import cz.tal0052.edisonrozvrh.data.parser.RoundcubeMailboxShellParser
import cz.tal0052.edisonrozvrh.data.parser.RoundcubeRemoteListParser
import cz.tal0052.edisonrozvrh.data.repository.RoundcubeLoginClient
import cz.tal0052.edisonrozvrh.data.repository.RoundcubeLoginRequest
import cz.tal0052.edisonrozvrh.data.repository.RoundcubeLoginTransport
import cz.tal0052.edisonrozvrh.data.repository.RoundcubeRemoteListTransport
import cz.tal0052.edisonrozvrh.data.repository.RoundcubeTransportResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RoundcubeLoginClientTest {
    private fun loadFixture(name: String): String {
        val file = sequenceOf(
            File("fixtures/$name"),
            File("../fixtures/$name")
        ).firstOrNull { it.exists() } ?: error("Fixture not found: $name")

        return file.readText()
    }

    @Test
    fun loginAndFetchInbox_success_returnsParsedInbox() {
        val transport = FakeRoundcubeLoginTransport(
            getResponses = ArrayDeque(
                listOf(
                    RoundcubeTransportResponse(200, loadFixture("mail_login.html")),
                    RoundcubeTransportResponse(200, successInboxHtml())
                )
            ),
            postResponses = ArrayDeque(
                listOf(
                    RoundcubeTransportResponse(200, successInboxHtml())
                )
            )
        )
        val client = RoundcubeLoginClient(transport = transport)

        val result = client.loginAndFetchInbox("Tal0052", "secret")

        assertTrue(result.success)
        assertEquals("Tal0052", result.usernameUsed)
        assertNotNull(result.inboxPage)
        assertEquals("Messages 1 to 2 of 2", result.inboxPage!!.countText)
        assertEquals(2, result.inboxPage!!.messages.size)
        assertEquals("889", result.inboxPage!!.messages.first().uid)
        assertEquals("Letni ubytovani na kolejich VSB-TUO", result.inboxPage!!.messages.first().subject)
        assertEquals("Tal0052", transport.lastPostRequest!!.parameters["_user"])
        assertEquals("secret", transport.lastPostRequest!!.parameters["_pass"])
    }

    @Test
    fun loginAndFetchInbox_failedLogin_returnsFailure() {
        val failedLoginHtml = """
            <!DOCTYPE html>
            <html lang="en">
            <body class="task-login action-login">
              <form id="login-form" action="https://posta.vsb.cz/roundcube/?_task=login">
                <input type="hidden" name="_token" value="abc123">
                <input type="hidden" name="_task" value="login">
                <input type="hidden" name="_action" value="login">
                <input type="text" name="_user" value="Tal0052">
                <input type="password" name="_pass" value="">
                <button type="submit">Login</button>
              </form>
              <div class="error">Login failed.</div>
            </body>
            </html>
        """.trimIndent()

        val transport = FakeRoundcubeLoginTransport(
            getResponses = ArrayDeque(
                listOf(
                    RoundcubeTransportResponse(200, loadFixture("mail_login.html")),
                    RoundcubeTransportResponse(200, loadFixture("mail_login.html")),
                    RoundcubeTransportResponse(200, loadFixture("mail_login.html")),
                    RoundcubeTransportResponse(200, loadFixture("mail_login.html"))
                )
            ),
            postResponses = ArrayDeque(
                listOf(
                    RoundcubeTransportResponse(401, failedLoginHtml),
                    RoundcubeTransportResponse(401, failedLoginHtml)
                )
            )
        )
        val client = RoundcubeLoginClient(transport = transport)

        val result = client.loginAndFetchInbox("Tal0052", "wrong")

        assertFalse(result.success)
        assertEquals(401, result.statusCode)
        assertEquals("TAL0052", result.usernameUsed)
        assertEquals("Roundcube login nebyl prijat.", result.errorMessage)
    }

    @Test
    fun inboxParser_successHtml_parsesExpectedRows() {
        val page = RoundcubeInboxParser.parse(successInboxHtml())

        assertNotNull(page)
        assertEquals(3, page!!.folders.size)
        assertEquals(2, page.messages.size)
        assertTrue(page.messages.first().unread)
        assertTrue(page.messages.last().hasAttachment)
    }

    @Test
    fun loginAndFetchInbox_shellInbox_fetchesRemoteList() {
        val shellHtml = loadFixture("Roundcube.html")
        val transport = FakeRoundcubeLoginTransport(
            getResponses = ArrayDeque(
                listOf(
                    RoundcubeTransportResponse(200, loadFixture("mail_login.html")),
                    RoundcubeTransportResponse(200, shellHtml)
                )
            ),
            postResponses = ArrayDeque(
                listOf(
                    RoundcubeTransportResponse(200, shellHtml)
                )
            ),
            remoteResponses = ArrayDeque(
                listOf(
                    RoundcubeTransportResponse(200, remoteListJson())
                )
            )
        )
        val client = RoundcubeLoginClient(transport = transport)

        val result = client.loginAndFetchInbox("Tal0052", "secret")

        assertTrue(result.success)
        assertNotNull(result.inboxPage)
        assertEquals("Messages 1 to 2 of 889", result.inboxPage!!.countText)
        assertEquals(2, result.inboxPage!!.messages.size)
        assertEquals("andrea.hustakova@vsb.cz", result.inboxPage!!.messages.first().sender)
        assertEquals("Letni ubytovani na kolejich VSB-TUO", result.inboxPage!!.messages.first().subject)
    }

    @Test
    fun shellAndRemoteParsers_realShellAndSyntheticRemote_workTogether() {
        val shellPage = RoundcubeMailboxShellParser.parse(loadFixture("Roundcube.html"))
        val remotePage = RoundcubeRemoteListParser.parse(
            jsonText = remoteListJson(),
            fallbackFolders = shellPage!!.folders,
            mailbox = shellPage.mailbox
        )

        assertNotNull(remotePage)
        assertEquals("INBOX", shellPage.mailbox)
        assertEquals("Messages 1 to 2 of 889", remotePage!!.countText)
        assertEquals(2, remotePage.messages.size)
        assertTrue(remotePage.messages.last().hasAttachment)
    }

    @Test
    fun fetchMessageDetail_parsesFixtureDetail() {
        val transport = FakeRoundcubeLoginTransport(
            getResponses = ArrayDeque(
                listOf(
                    RoundcubeTransportResponse(200, successInboxHtml()),
                    RoundcubeTransportResponse(200, loadFixture("mail_response.html"))
                )
            ),
            postResponses = ArrayDeque()
        )
        val client = RoundcubeLoginClient(transport = transport)

        val result = client.fetchMessageDetail(
            username = "Tal0052",
            password = "secret",
            detailUrl = "https://posta.vsb.cz/roundcube/?_task=mail&_action=show&_uid=887&_mbox=INBOX"
        )

        assertTrue(result.success)
        assertNotNull(result.messageDetail)
        assertEquals("887", result.messageDetail!!.uid)
        assertEquals("EPS - order paid", result.messageDetail!!.subject)
        assertTrue(result.messageDetail!!.bodyText.contains("your order No. 881681"))
    }

    private fun successInboxHtml(): String {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head><title>Roundcube :: Inbox</title></head>
            <body class="task-mail action-none">
              <ul id="mailboxlist" class="treelist listing folderlist">
                <li class="mailbox inbox selected"><a rel="INBOX" href="/roundcube/?_task=mail&_mbox=INBOX">Inbox</a></li>
                <li class="mailbox drafts"><a rel="INBOX.Drafts" href="/roundcube/?_task=mail&_mbox=INBOX.Drafts">Drafts</a></li>
                <li class="mailbox sent"><a rel="INBOX.Sent" href="/roundcube/?_task=mail&_mbox=INBOX.Sent">Sent</a></li>
              </ul>
              <span id="rcmcountdisplay">Messages 1 to 2 of 2</span>
              <table id="messagelist">
                <tbody>
                  <tr id="rcmrowODg5" class="message unread focused">
                    <td class="subject">
                      <span class="fromto skip-on-drag"><span class="adr"><span class="rcmContactAddress">andrea.hustakova@vsb.cz</span></span></span>
                      <span class="date skip-on-drag">Today 07:50</span>
                      <span class="size skip-on-drag">11 KB</span>
                      <span class="subject"><a href="/roundcube/?_task=mail&_mbox=INBOX&_uid=889&_action=show"><span>Letni ubytovani na kolejich VSB-TUO</span></a></span>
                    </td>
                    <td class="flags">
                      <span class="flag"><span class="unflagged" title="Not Flagged"></span></span>
                      <span class="attachment">&nbsp;</span>
                    </td>
                  </tr>
                  <tr id="rcmrowODg3" class="message">
                    <td class="subject">
                      <span class="fromto skip-on-drag"><span class="adr"><span class="rcmContactAddress">eps-help@vsb.cz</span></span></span>
                      <span class="date skip-on-drag">Thu 16:49</span>
                      <span class="size skip-on-drag">8 KB</span>
                      <span class="subject"><a href="/roundcube/?_task=mail&_mbox=INBOX&_uid=887&_action=show"><span>EPS - order paid</span></a></span>
                    </td>
                    <td class="flags">
                      <span class="flag"><span class="unflagged" title="Not Flagged"></span></span>
                      <span class="attachment"><span class="attachment" title="With attachment"></span></span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </body>
            </html>
        """.trimIndent()
    }

    private fun remoteListJson(): String {
        return """
            {
              "action": "list",
              "exec": "this.set_rowcount(\"Messages 1 to 2 of 889\");this.add_message_row(\"889\",{\"fromto\":\"andrea.hustakova@vsb.cz\",\"subject\":\"Letni ubytovani na kolejich VSB-TUO\",\"date\":\"Today 07:50\",\"size\":\"11 KB\"},{\"mbox\":\"INBOX\",\"seen\":false,\"flagged\":false,\"hasattachment\":false},false);this.add_message_row(\"887\",{\"fromto\":\"eps-help@vsb.cz\",\"subject\":\"EPS - order paid\",\"date\":\"Thu 16:49\",\"size\":\"8 KB\"},{\"mbox\":\"INBOX\",\"seen\":true,\"flagged\":false,\"hasattachment\":true},false);"
            }
        """.trimIndent()
    }

    private class FakeRoundcubeLoginTransport(
        private val getResponses: ArrayDeque<RoundcubeTransportResponse>,
        private val postResponses: ArrayDeque<RoundcubeTransportResponse>,
        private val remoteResponses: ArrayDeque<RoundcubeTransportResponse> = ArrayDeque()
    ) : RoundcubeLoginTransport, RoundcubeRemoteListTransport {
        var lastPostRequest: RoundcubeLoginRequest? = null

        override fun get(url: String, refererUrl: String?): RoundcubeTransportResponse {
            return getResponses.removeFirstOrNull()
                ?: error("Unexpected GET request for $url")
        }

        override fun post(request: RoundcubeLoginRequest): RoundcubeTransportResponse {
            lastPostRequest = request
            return postResponses.removeFirstOrNull()
                ?: error("Unexpected POST request for ${request.actionUrl}")
        }

        override fun fetchRemoteList(
            shellPage: cz.tal0052.edisonrozvrh.data.parser.RoundcubeMailboxShellPage,
            page: Int,
            includeRefresh: Boolean
        ): RoundcubeTransportResponse {
            return remoteResponses.removeFirstOrNull()
                ?: error("Unexpected remote list request for ${shellPage.mailbox}")
        }
    }
}
