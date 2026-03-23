package cz.tal0052.edisonrozvrh.data.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

data class RoundcubeAttachment(
    val name: String,
    val size: String,
    val downloadUrl: String
)

data class RoundcubeMessageDetail(
    val uid: String,
    val mailbox: String,
    val subject: String,
    val from: String,
    val to: String,
    val date: String,
    val summary: String,
    val bodyBaseUrl: String,
    val bodyHtml: String,
    val bodyText: String,
    val attachments: List<RoundcubeAttachment>
)

object RoundcubeMessageDetailParser {

    fun parse(html: String): RoundcubeMessageDetail? {
        val doc = Jsoup.parse(html, "https://posta.vsb.cz")
        val subject = doc.selectFirst("#message-header h2.subject")?.ownText()?.trim().orEmpty()
        val summary = doc.selectFirst("#message-header .header-summary")?.text()?.trim().orEmpty()
        val body = doc.selectFirst("#messagebody") ?: return null
        val from = doc.selectFirst(".header-headers td.header.from")?.text()?.trim().orEmpty()
        val to = doc.selectFirst(".header-headers td.header.to")?.text()?.trim().orEmpty()
        val date = doc.selectFirst(".header-headers td.header.date")?.text()?.trim().orEmpty()
        val uid = UID_REGEX.find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val mailbox = MAILBOX_REGEX.find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val permaUrl = PERMA_URL_REGEX.find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val extWindowUrl = doc.selectFirst("#message-header a.extwin")?.absUrl("href").orEmpty()

        if (subject.isBlank() || uid.isBlank() || mailbox.isBlank()) return null

        val attachments = doc.select("#attachment-list li a.filename").mapNotNull { link ->
            val name = link.selectFirst(".attachment-name")?.text()?.trim().orEmpty()
            if (name.isBlank()) return@mapNotNull null

            RoundcubeAttachment(
                name = name,
                size = link.selectFirst(".attachment-size")?.text()?.trim().orEmpty(),
                downloadUrl = link.absUrl("href").ifBlank { link.attr("href").trim() }
            )
        }

        return RoundcubeMessageDetail(
            uid = uid,
            mailbox = mailbox,
            subject = subject,
            from = from,
            to = to,
            date = date,
            summary = summary,
            bodyBaseUrl = resolveBaseUrl(permaUrl, extWindowUrl),
            bodyHtml = body.html().trim(),
            bodyText = extractBodyText(body),
            attachments = attachments
        )
    }

    private fun resolveBaseUrl(permaUrl: String, extWindowUrl: String): String {
        return when {
            permaUrl.startsWith("http://") || permaUrl.startsWith("https://") -> permaUrl
            permaUrl.startsWith("/") -> "https://posta.vsb.cz$permaUrl"
            permaUrl.isNotBlank() -> "https://posta.vsb.cz/roundcube/$permaUrl"
            extWindowUrl.isNotBlank() -> extWindowUrl
            else -> "https://posta.vsb.cz/roundcube/"
        }
    }

    private fun extractBodyText(element: Element): String {
        val htmlWithBreaks = element.html()
            .replace("<br>", "\n", ignoreCase = true)
            .replace("<br/>", "\n", ignoreCase = true)
            .replace("<br />", "\n", ignoreCase = true)
            .replace("</p>", "\n\n", ignoreCase = true)

        return Jsoup.parse(htmlWithBreaks)
            .text()
            .replace(NON_BREAKING_SPACE, ' ')
            .replace(Regex("\\s+\\n"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private const val NON_BREAKING_SPACE = '\u00A0'
    private val UID_REGEX = Regex(""""uid"\s*:\s*"([^"]+)"""")
    private val MAILBOX_REGEX = Regex(""""mailbox"\s*:\s*"([^"]+)"""")
    private val PERMA_URL_REGEX = Regex(""""permaurl"\s*:\s*"([^"]+)"""")
}
