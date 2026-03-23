package cz.tal0052.edisonrozvrh

import cz.tal0052.edisonrozvrh.data.parser.RoundcubeRemoteListParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundcubeRemoteListParserTest {

    @Test
    fun parse_handlesFlaggedRowsFromRoundcubeExecPayload() {
        val json = """
            {
              "exec": "this.set_rowcount('Messages 1 to 1 of 1','INBOX');rcmail.add_message_row('123',{\"fromto\":\"<span>news@vsb.cz</span>\",\"subject\":\"<a href=\\\"/roundcube/?_task=mail&_mbox=INBOX&_uid=123&_action=show\\\">Flagged mail</a>\",\"date\":\"Today 08:00\",\"size\":\"10 KB\"},{\"mbox\":\"INBOX\",\"seen\":\"true\",\"flagged\":\"flagged\",\"hasattachment\":\"1\"},false);"
            }
        """.trimIndent()

        val page = RoundcubeRemoteListParser.parse(
            jsonText = json,
            fallbackFolders = emptyList(),
            mailbox = "INBOX"
        )

        assertNotNull(page)
        assertEquals(1, page!!.messages.size)
        assertEquals("Messages 1 to 1 of 1", page.countText)
        assertTrue(page.messages.first().flagged)
        assertFalse(page.messages.first().unread)
        assertTrue(page.messages.first().hasAttachment)
    }
}
