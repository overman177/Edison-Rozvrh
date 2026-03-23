package cz.tal0052.edisonrozvrh

import cz.tal0052.edisonrozvrh.data.parser.RoundcubeMessageDetailParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RoundcubeMessageDetailParserFixtureTest {
    private fun loadFixture(name: String): String {
        val file = sequenceOf(
            File("fixtures/$name"),
            File("../fixtures/$name")
        ).firstOrNull { it.exists() } ?: error("Fixture not found: $name")

        return file.readText()
    }

    @Test
    fun parseMailResponseFixture_extractsMessageDetail() {
        val detail = RoundcubeMessageDetailParser.parse(loadFixture("mail_response.html"))

        assertNotNull(detail)
        assertEquals("887", detail!!.uid)
        assertEquals("INBOX", detail.mailbox)
        assertEquals("EPS - order paid", detail.subject)
        assertEquals("eps-help@vsb.cz", detail.from)
        assertEquals("vojtech.talman.st@vsb.cz", detail.to)
        assertEquals(
            "https://posta.vsb.cz/roundcube/?_task=mail&_action=show&_uid=887&_mbox=INBOX",
            detail.bodyBaseUrl
        )
        assertTrue(detail.summary.contains("2026-03-19 16:49"))
        assertTrue(detail.bodyHtml.contains("message-part"))
        assertTrue(detail.bodyText.contains("your order No. 881681"))
        assertEquals(1, detail.attachments.size)
        assertEquals("smime.p7s", detail.attachments.first().name)
    }
}
