package cz.tal0052.edisonrozvrh.data.parser

import org.jsoup.Jsoup

data class RoundcubeFolder(
    val id: String,
    val name: String,
    val selected: Boolean
)

data class RoundcubeInboxMessage(
    val uid: String,
    val sender: String,
    val subject: String,
    val date: String,
    val size: String,
    val unread: Boolean,
    val flagged: Boolean,
    val hasAttachment: Boolean,
    val detailUrl: String
)

data class RoundcubeInboxPage(
    val title: String,
    val countText: String,
    val folders: List<RoundcubeFolder>,
    val messages: List<RoundcubeInboxMessage>
)

object RoundcubeInboxParser {

    fun parse(html: String): RoundcubeInboxPage? {
        val doc = Jsoup.parse(html, "https://posta.vsb.cz")
        val mailboxList = doc.selectFirst("#mailboxlist") ?: return null
        val messageTable = doc.selectFirst("#messagelist") ?: return null

        val folders = mailboxList.select("li.mailbox").mapNotNull { item ->
            val link = item.selectFirst("a[rel], a[href]") ?: return@mapNotNull null
            val id = link.attr("rel").trim().ifBlank { item.id().trim() }
            val name = link.text().trim()
            if (id.isBlank() || name.isBlank()) return@mapNotNull null

            RoundcubeFolder(
                id = id,
                name = name,
                selected = item.hasClass("selected")
            )
        }

        val messages = messageTable.select("tbody tr.message").mapNotNull { row ->
            val subjectCell = row.selectFirst("td.subject") ?: return@mapNotNull null
            val subjectLink = subjectCell.selectFirst("span.subject a, a[href*=_action=show]") ?: return@mapNotNull null
            val detailUrl = subjectLink.absUrl("href").ifBlank { subjectLink.attr("href").trim() }
            val uid = UID_REGEX.find(detailUrl)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val sender = subjectCell.selectFirst("span.fromto .rcmContactAddress, span.fromto")?.text()?.trim().orEmpty()
            val subject = subjectLink.text().trim()
            val date = subjectCell.selectFirst("span.date")?.text()?.trim().orEmpty()
            val size = subjectCell.selectFirst("span.size")?.text()?.trim().orEmpty()
            val flagsCell = row.selectFirst("td.flags, td.flag")

            if (uid.isBlank() || subject.isBlank()) return@mapNotNull null

            RoundcubeInboxMessage(
                uid = uid,
                sender = sender,
                subject = subject,
                date = date,
                size = size,
                unread = row.hasClass("unread") || row.selectFirst(".msgicon.unread") != null,
                flagged = row.hasClass("flagged") || flagsCell?.selectFirst(".flagged[title], .flagged") != null,
                hasAttachment = flagsCell?.selectFirst(".attachment[title]") != null,
                detailUrl = detailUrl
            )
        }

        return RoundcubeInboxPage(
            title = doc.title().trim(),
            countText = doc.selectFirst("#rcmcountdisplay")?.text()?.trim().orEmpty(),
            folders = folders,
            messages = messages
        )
    }

    private val UID_REGEX = Regex("[?&]_uid=([^&]+)")
}
