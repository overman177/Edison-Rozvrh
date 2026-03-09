package cz.tal0052.edisonrozvrh.data.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.json.JSONArray
import java.text.Normalizer
import java.util.Locale

data class ActivitySeed(
    val concreteActivityId: String?,
    val idcl: String?,
    val day: String,
    val time: String,
    val type: String,
    val weekPattern: String,
    val subject: String,
    val teacher: String,
    val room: String
)

data class ScheduleContext(
    val actionUrl: String,
    val formName: String,
    val baseParams: Map<String, String>,
    val activities: List<ActivitySeed>
)

data class LessonDetail(
    val subject: String,
    val teacher: String,
    val room: String,
    val type: String
)

data class CurrentResultItem(
    val semesterCode: String,
    val subjectNumber: String,
    val subjectShortcut: String,
    val subjectName: String,
    val creditPoints: String,
    val examPoints: String,
    val totalPoints: String,
    val grade: String,
    val ectsGrade: String,
    val detailUrl: String,
    val detail: CurrentResultDetail? = null
)

data class CurrentResultsData(
    val academicYear: String,
    val warningNote: String,
    val items: List<CurrentResultItem>
)
data class CurrentResultDetail(
    val semester: String,
    val program: String,
    val form: String,
    val studyType: String,
    val totalRows: List<CurrentResultTotalRow>,
    val checkpoints: List<CurrentResultCheckpoint>
)

data class CurrentResultTotalRow(
    val label: String,
    val points: String,
    val rating: String
)

data class CurrentResultCheckpoint(
    val section: String,
    val sectionRequirement: String,
    val label: String,
    val requirement: String,
    val points: String,
    val status: String
)
object EdisonParser {

    private data class ParsedRequest(
        val concreteActivityId: String,
        val idcl: String
    )

    private val concreteIdRegex = Regex("concreteActivityId','([^']+)'")
    private val idclRegex = Regex("submitForm\\('[^']+','([^']+)'")
    private val timeRegex = Regex("(\\d{1,2}:\\d{2})")
    private val canonicalDays = setOf("Pondeli", "Utery", "Streda", "Ctvrtek", "Patek")
    private const val WEEK_PATTERN_EVERY = "every"
    private const val WEEK_PATTERN_EVEN = "even"
    private const val WEEK_PATTERN_ODD = "odd"

    fun parseCurrentResults(html: String): CurrentResultsData? {
        val doc = Jsoup.parse(html, "https://edison.sso.vsb.cz")
        val rows = doc.select("tbody.ui-datatable-data tr[data-ri]")
        if (rows.isEmpty()) return null

        val items = rows.mapNotNull { row ->
            val cells = row.select("> td")
            if (cells.size < 10) return@mapNotNull null

            val subjectName = normalizeWhitespace(
                cells[3].selectFirst("a")?.text().orEmpty().ifBlank { cells[3].text() }
            )
            if (subjectName.isBlank()) return@mapNotNull null

            CurrentResultItem(
                semesterCode = normalizeWhitespace(cells[0].text()),
                subjectNumber = normalizeWhitespace(cells[1].text()),
                subjectShortcut = normalizeWhitespace(cells[2].text()),
                subjectName = subjectName,
                creditPoints = parseResultCell(cells[4]),
                examPoints = parseResultCell(cells[5]),
                totalPoints = parseResultCell(cells[6]),
                grade = parseResultCell(cells[7]),
                ectsGrade = parseResultCell(cells[8]),
                detailUrl = cells[9]
                    .selectFirst("a[href]")
                    ?.absUrl("href")
                    .orEmpty()
            )
        }

        if (items.isEmpty()) return null

        val academicYear = normalizeWhitespace(
            doc.selectFirst("select[name$='semesterMenu'] option[selected]")?.text().orEmpty()
        )

        val warningNote = normalizeWhitespace(
            doc.select("span.outputText")
                .firstOrNull { it.text().contains("ozna", ignoreCase = true) }
                ?.text()
                .orEmpty()
        )

        return CurrentResultsData(
            academicYear = academicYear,
            warningNote = warningNote,
            items = items
        )
    }


    fun parseCurrentResultDetail(html: String): CurrentResultDetail? {
        val doc = Jsoup.parse(html, "https://edison.sso.vsb.cz")
        val root = doc.selectFirst("#card-mysubject") ?: doc.body()

        val detailTable = root.selectFirst("table.detail")
        val detailValues = mutableMapOf<String, String>()
        detailTable?.select("tr")?.forEach { row ->
            val cells = row.select("> td")
            var index = 0
            while (index + 1 < cells.size) {
                val label = normalizeToken(cells[index].text())
                val value = normalizeWhitespace(cells[index + 1].text())
                if (label.isNotBlank() && value.isNotBlank()) {
                    detailValues[label] = value
                }
                index += 2
            }
        }

        val summaryTable = root.selectFirst("table.dataTable")
        val totalRows = summaryTable
            ?.select("tr.total, tr.totalincl")
            ?.mapNotNull { row ->
                val cells = row.select("> td")
                if (cells.isEmpty()) return@mapNotNull null

                val label = normalizeWhitespace(cells.getOrNull(0)?.text().orEmpty())
                val pointsCell: Element = if (cells.size > 3) cells[3] else cells[cells.lastIndex]
                val ratingCell: Element = if (cells.size > 4) cells[4] else cells[cells.lastIndex]
                val points = parseResultCell(pointsCell)
                val rating = parseResultCell(ratingCell)
                if (label.isBlank()) return@mapNotNull null

                CurrentResultTotalRow(
                    label = label,
                    points = points,
                    rating = rating
                )
            }
            .orEmpty()

        val sectionHeadings = root.select("h3, H3")
            .filterNot { normalizeToken(it.text()).contains("celkove hodnoceni predmetu") }

        val checkpoints = sectionHeadings.flatMap { heading ->
            val sectionText = normalizeWhitespace(heading.text())
            val sectionRequirement = extractBracketRequirement(sectionText)
            val sectionName = sectionText.substringBefore(" (").trim()
            val table = heading.nextElementSibling()
                ?.let { sibling ->
                    if (sibling.tagName().equals("table", ignoreCase = true) &&
                        sibling.classNames().contains("dataTable")
                    ) {
                        sibling
                    } else {
                        sibling.nextElementSibling()?.takeIf {
                            it.tagName().equals("table", ignoreCase = true) &&
                                it.classNames().contains("dataTable")
                        }
                    }
                }
                ?: return@flatMap emptyList()

            table.select("tbody > tr").mapNotNull { row ->
                val cells = row.select("> td")
                if (cells.size < 4) return@mapNotNull null

                val firstCellText = normalizeWhitespace(cells[0].text())
                if (firstCellText.isBlank()) return@mapNotNull null
                if (normalizeToken(firstCellText).startsWith("in total for")) return@mapNotNull null

                val requirement = extractBracketRequirement(firstCellText)
                val label = firstCellText.substringBefore(" (").trim()
                val pointsCell = cells[3]
                val points = parseResultCell(pointsCell)
                val status = when {
                    pointsCell.selectFirst(".notenough") != null -> "nesplneno"
                    points.isBlank() || points == "..." -> ""
                    else -> "splneno"
                }

                CurrentResultCheckpoint(
                    section = sectionName,
                    sectionRequirement = sectionRequirement,
                    label = label.ifBlank { firstCellText },
                    requirement = requirement,
                    points = points,
                    status = status
                )
            }
        }

        val semester = detailValues.entries
            .firstOrNull { it.key.contains("semestr") }
            ?.value
            .orEmpty()
        val program = detailValues.entries
            .firstOrNull { it.key.contains("program") }
            ?.value
            .orEmpty()
        val form = detailValues.entries
            .firstOrNull { it.key.contains("forma") }
            ?.value
            .orEmpty()
        val studyType = detailValues.entries
            .firstOrNull { it.key == "typ" || it.key.contains("typ") }
            ?.value
            .orEmpty()

        if (
            semester.isBlank() &&
            program.isBlank() &&
            form.isBlank() &&
            studyType.isBlank() &&
            totalRows.isEmpty() &&
            checkpoints.isEmpty()
        ) {
            return null
        }

        return CurrentResultDetail(
            semester = semester,
            program = program,
            form = form,
            studyType = studyType,
            totalRows = totalRows,
            checkpoints = checkpoints
        )
    }
    fun parseScheduleContext(html: String): ScheduleContext? {
        val doc = Jsoup.parse(html, "https://edison.sso.vsb.cz")
        val scheduleTable: Element = doc.selectFirst("table.schedTable") ?: return null
        val form: Element = findOwnerForm(scheduleTable) ?: return null

        val actionUrl = form.absUrl("action").ifBlank { form.attr("action") }
        if (actionUrl.isBlank()) return null

        val formName = form.attr("name").ifBlank { form.attr("id") }
        if (formName.isBlank()) return null

        val baseParams = mutableMapOf<String, String>()

        form.select("input[name]").forEach { input ->
            val name = input.attr("name")
            if (name.isBlank()) return@forEach

            val type = input.attr("type").lowercase(Locale.ROOT)
            if (type == "submit" || type == "button" || type == "image") return@forEach

            baseParams[name] = input.`val`()
        }

        form.select("select[name]").forEach { select ->
            val name = select.attr("name")
            if (name.isBlank()) return@forEach

            val selectedValue = select.selectFirst("option[selected]")?.`val`()
                ?: select.selectFirst("option")?.`val`()
                ?: ""

            if (selectedValue.isNotBlank()) {
                baseParams[name] = selectedValue
            }
        }

        val slots: List<Pair<String, String>> = parseTimeSlots(scheduleTable)
        if (slots.isEmpty()) return null

        val activities = mutableListOf<ActivitySeed>()

        val rows: List<Element> = directRows(scheduleTable)
        var currentDay = ""
        rows.forEach { row ->
            val dayCell: Element? = row.children().firstOrNull { it.tagName().equals("th", ignoreCase = true) }
            if (dayCell != null && dayCell.text().isNotBlank()) {
                val normalizedDay = normalizeDay(dayCell.text())
                if (normalizedDay in canonicalDays) {
                    currentDay = normalizedDay
                }
            }

            if (currentDay.isBlank()) return@forEach

            val cells: List<Element> = row.children().filter { it.tagName().equals("td", ignoreCase = true) }
            var rowSlotIndex = 0
            cells.forEach { cell ->
                val colspan = cell.attr("colspan").toIntOrNull() ?: 1

                val start = slots.getOrNull(rowSlotIndex)?.first.orEmpty()
                val endIndex = (rowSlotIndex + colspan - 1).coerceAtMost(slots.lastIndex)
                val end = slots.getOrNull(endIndex)?.second.orEmpty()
                val slotTime = if (start.isNotBlank() && end.isNotBlank()) {
                    "$start - $end"
                } else {
                    ""
                }

                cell.select("table.actTable").forEach { activity ->
                    val link: Element? = activity.selectFirst("a.commandLink[onclick*=concreteActivityId]")
                    val parsedRequest = link?.let { parseRequest(it.attr("onclick")) }

                    val subject = normalizeWhitespace(
                        activity.selectFirst("abbr")?.text().orEmpty()
                    )

                    val teacher = parseActivityTeacher(activity)
                    val room = parseActivityRoom(activity)
                    val type = toLessonType(extractActivityTypeCode(activity))
                    val weekPattern = extractActivityWeekPattern(activity)

                    if (subject.isBlank() || slotTime.isBlank()) return@forEach

                    activities.add(
                        ActivitySeed(
                            concreteActivityId = parsedRequest?.concreteActivityId,
                            idcl = parsedRequest?.idcl,
                            day = currentDay,
                            time = slotTime,
                            type = type,
                            weekPattern = weekPattern,
                            subject = subject,
                            teacher = teacher,
                            room = room
                        )
                    )
                }

                rowSlotIndex += colspan
            }
        }

        if (activities.isEmpty()) return null

        println("EDISON PARSER: activities=${activities.size}")
        activities
            .groupBy { it.day }
            .forEach { (day, list) ->
                println("EDISON PARSER: day=$day count=${list.size}")
            }

        return ScheduleContext(
            actionUrl = actionUrl,
            formName = formName,
            baseParams = baseParams,
            activities = activities
        )
    }
    fun parseSportActivities(html: String): List<ActivitySeed> {
        val doc = Jsoup.parse(html, "https://edison.sso.vsb.cz")
        val table = doc.selectFirst("div[id$=':myactivities'] table.dataTable")
            ?: return emptyList()

        return table.select("tr")
            .mapNotNull { row ->
                val cells = row.select("td")
                if (cells.size < 5) return@mapNotNull null

                val subject = normalizeWhitespace(cells[0].text())
                val day = normalizeDay(cells[1].text())
                val time = normalizeWhitespace(cells[2].text()).replace(Regex("\\s*-\\s*"), " - ")
                val room = normalizeWhitespace(cells[3].text())
                val teacher = normalizeWhitespace(cells[4].text())

                if (subject.isBlank() || day !in canonicalDays || time.isBlank()) {
                    return@mapNotNull null
                }

                ActivitySeed(
                    concreteActivityId = null,
                    idcl = null,
                    day = day,
                    time = time,
                    type = "sport",
                    weekPattern = WEEK_PATTERN_EVERY,
                    subject = subject,
                    teacher = teacher,
                    room = room
                )
            }
    }
    fun extractSportsPortletId(html: String): String? {
        val doc = Jsoup.parse(html, "https://edison.sso.vsb.cz")
        val myActivitiesDiv = doc.selectFirst("div[id$=':myactivities']") ?: return null
        val id = myActivitiesDiv.id().trim()
        if (id.isBlank() || !id.contains(':')) return null
        return id.substringBefore(':').takeIf { it.isNotBlank() }
    }

    fun parseSportActivitiesJson(json: String): List<ActivitySeed> {
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { idx ->
                val obj = array.optJSONObject(idx) ?: return@mapNotNull null

                val subject = normalizeWhitespace(obj.optString("sportTitle"))
                val day = normalizeDay(obj.optString("weekDayAbbrev"))
                val time = normalizeWhitespace(obj.optString("scheduleWindowTimeText"))
                    .replace(Regex("\\s*-\\s*"), " - ")
                val room = normalizeWhitespace(obj.optString("sportPlaceTitle"))
                val teacher = normalizeWhitespace(obj.optString("teacherName"))

                if (subject.isBlank() || day !in canonicalDays || time.isBlank()) {
                    return@mapNotNull null
                }

                ActivitySeed(
                    concreteActivityId = null,
                    idcl = null,
                    day = day,
                    time = time,
                    type = "sport",
                    weekPattern = WEEK_PATTERN_EVERY,
                    subject = subject,
                    teacher = teacher,
                    room = room
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
    fun parseLessonDetail(html: String): LessonDetail? {
        val doc = Jsoup.parse(html)
        val detailTable: Element = doc.selectFirst("div.detail table.panelGrid") ?: return null

        var subject = ""
        var activity = ""
        var room = ""
        var teacher = ""

        detailTable.select("tr").forEach { row ->
            val cells = row.select("td")
            if (cells.isEmpty()) return@forEach

            var index = 0
            while (index + 1 < cells.size) {
                val label = normalizeToken(cells[index].text())
                val value = normalizeWhitespace(cells[index + 1].text())

                when {
                    label.contains("predmet") -> subject = value
                    label.contains("aktivita") -> activity = value
                    label.contains("mistnost") -> room = value
                    label.contains("vyucujici") -> teacher = value
                }

                index += 2
            }
        }

        return LessonDetail(
            subject = parseSubject(subject),
            teacher = teacher,
            room = room,
            type = toLessonType(activity)
        )
    }

    private fun parseTimeSlots(table: Element): List<Pair<String, String>> {
        val firstRow: Element = directRows(table).firstOrNull() ?: return emptyList()
        val headerCells: List<Element> = firstRow.children()
            .filter { it.tagName().equals("th", ignoreCase = true) }
            .drop(1)

        return headerCells.mapNotNull { cell ->
            val times: List<String> = timeRegex.findAll(normalizeWhitespace(cell.text()))
                .map { it.groupValues[1] }
                .toList()

            when {
                times.size >= 2 -> times[0] to times[1]
                times.size == 1 -> times[0] to times[0]
                else -> null
            }
        }
    }

    private fun parseActivityTeacher(activity: Element): String {
        return normalizeWhitespace(
            activity.select("tr").getOrNull(1)
                ?.selectFirst("td")
                ?.text()
                .orEmpty()
        )
    }

    private fun parseActivityRoom(activity: Element): String {
        return normalizeWhitespace(
            activity.select("tr").getOrNull(2)
                ?.select("td")
                ?.getOrNull(0)
                ?.text()
                .orEmpty()
        )
    }

    private fun extractActivityTypeCode(activity: Element): String {
        val codeCellText = normalizeWhitespace(
            activity.select("tr").getOrNull(2)
                ?.select("td")
                ?.getOrNull(1)
                ?.text()
                .orEmpty()
        )

        if (codeCellText.isNotBlank()) {
            val prefix = codeCellText.substringBefore('/').trim()
            if (prefix.isNotBlank()) return prefix
        }

        val fullText = normalizeWhitespace(activity.text()).uppercase(Locale.ROOT)
        val match = Regex("\\b([PCL])\\s*/").find(fullText)
        return match?.groupValues?.getOrNull(1).orEmpty()
    }


    private fun extractActivityWeekPattern(activity: Element): String {
        val headerCell = activity.select("tr").getOrNull(0)?.selectFirst("td")
        val emphasized = normalizeWhitespace(
            headerCell?.select("b, strong")?.text().orEmpty()
        )
        val rawText = if (emphasized.isNotBlank()) {
            emphasized
        } else {
            normalizeWhitespace(headerCell?.text().orEmpty())
        }
        val token = normalizeToken(rawText)

        return when {
            token.contains("lich") || token.contains("odd") || token.contains("nepar") -> WEEK_PATTERN_ODD
            token.contains("sud") || token.contains("even") || Regex("\\bpar\\w*").containsMatchIn(token) -> WEEK_PATTERN_EVEN
            token.contains("kazd") || token.contains("every") -> WEEK_PATTERN_EVERY
            else -> WEEK_PATTERN_EVERY
        }
    }
    private fun findOwnerForm(element: Element): Element? {
        var current: Element? = element
        while (current != null) {
            if (current.tagName().equals("form", ignoreCase = true)) {
                return current
            }
            current = current.parent()
        }
        return null
    }

    private fun directRows(table: Element): List<Element> {
        val tbody: Element? = table.children().firstOrNull { it.tagName().equals("tbody", ignoreCase = true) }
        val container: Element = tbody ?: table
        return container.children().filter { it.tagName().equals("tr", ignoreCase = true) }
    }

    private fun parseRequest(onclick: String): ParsedRequest? {
        val concreteActivityId = concreteIdRegex.find(onclick)?.groupValues?.getOrNull(1)
        val idcl = idclRegex.find(onclick)?.groupValues?.getOrNull(1)

        if (concreteActivityId.isNullOrBlank() || idcl.isNullOrBlank()) {
            return null
        }

        return ParsedRequest(
            concreteActivityId = concreteActivityId,
            idcl = idcl
        )
    }

    private fun parseSubject(raw: String): String {
        val normalized = normalizeWhitespace(raw)
        if (normalized.isBlank()) return ""

        val splitIndex = normalized.indexOf(" / ")
        if (splitIndex > 0) {
            val first = normalized.substring(0, splitIndex).trim()
            if (first.isNotBlank()) return first
        }

        return normalized
    }

    private fun toLessonType(activity: String): String {
        val code = normalizeWhitespace(activity).uppercase(Locale.ROOT)
        return when {
            code.startsWith("C") || code.startsWith("C/") -> "cviko"
            code.startsWith("P") || code.startsWith("P/") -> "prednaska"
            code.startsWith("L") || code.startsWith("L/") -> "lab"
            else -> "other"
        }
    }

    private fun normalizeDay(raw: String): String {
        val value = normalizeToken(raw)
        val compact = value.replace(" ", "")
        return when {
            compact == "po" || compact.contains("pond") -> "Pondeli"
            compact == "ut" || compact == "u" || compact.contains("uter") || compact.contains("ter") -> "Utery"
            compact == "st" || compact.contains("stred") || compact.contains("str") -> "Streda"
            compact == "ct" || compact.contains("ctvrt") || compact.contains("ctv") -> "Ctvrtek"
            compact == "pa" || compact.contains("pat") -> "Patek"
            else -> normalizeWhitespace(raw)
        }
    }

    private fun normalizeWhitespace(raw: String): String {
        return raw
            .replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun parseResultCell(cell: Element): String {
        val pointsText = normalizeWhitespace(
            cell.selectFirst("span.spoints")?.text().orEmpty()
        )
        if (pointsText.isNotBlank()) return pointsText

        val directText = normalizeWhitespace(cell.text())
        if (directText.isNotBlank()) return directText

        val iconSrc = cell.selectFirst("img[src]")?.attr("src").orEmpty().lowercase(Locale.ROOT)
        return when {
            iconSrc.contains("selected") -> "OK"
            iconSrc.contains("dot") -> "..."
            else -> ""
        }
    }


    private fun extractBracketRequirement(text: String): String {
        val match = Regex("\\(([^)]*)\\)").find(text) ?: return ""
        return normalizeWhitespace(match.groupValues.getOrNull(1).orEmpty())
    }
    private fun normalizeToken(raw: String): String {
        val withoutDiacritics = Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")

        return withoutDiacritics
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }
}















