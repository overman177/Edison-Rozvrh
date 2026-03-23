package cz.tal0052.edisonrozvrh.data.parser

import org.jsoup.Jsoup

data class RoundcubeMailboxShellPage(
    val requestToken: String,
    val commPath: String,
    val mailbox: String,
    val currentPage: Int,
    val layout: String,
    val folders: List<RoundcubeFolder>
)

object RoundcubeMailboxShellParser {

    fun parse(html: String): RoundcubeMailboxShellPage? {
        val doc = Jsoup.parse(html, "https://posta.vsb.cz")
        val folders = doc.select("#mailboxlist li.mailbox").mapNotNull { item ->
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

        val requestToken = REQUEST_TOKEN_REGEX.find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val commPath = COMM_PATH_REGEX.find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val mailbox = MAILBOX_REGEX.find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val currentPage = CURRENT_PAGE_REGEX.find(html)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        val layout = LAYOUT_REGEX.find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty().ifBlank { "widescreen" }

        if (requestToken.isBlank() || commPath.isBlank() || mailbox.isBlank()) return null

        return RoundcubeMailboxShellPage(
            requestToken = requestToken,
            commPath = if (commPath.startsWith("http")) commPath else "https://posta.vsb.cz$commPath",
            mailbox = mailbox,
            currentPage = currentPage.coerceAtLeast(1),
            layout = layout,
            folders = folders
        )
    }

    private val REQUEST_TOKEN_REGEX = Regex("\"request_token\"\\s*:\\s*\"([^\"]+)\"")
    private val COMM_PATH_REGEX = Regex("\"comm_path\"\\s*:\\s*\"([^\"]+)\"")
    private val MAILBOX_REGEX = Regex("\"mailbox\"\\s*:\\s*\"([^\"]+)\"")
    private val CURRENT_PAGE_REGEX = Regex("\"current_page\"\\s*:\\s*(\\d+)")
    private val LAYOUT_REGEX = Regex("\"layout\"\\s*:\\s*\"([^\"]+)\"")
}
