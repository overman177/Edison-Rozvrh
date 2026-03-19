package cz.tal0052.edisonrozvrh

import cz.tal0052.edisonrozvrh.app.Lesson
import cz.tal0052.edisonrozvrh.app.buildPositionedLessonsForDay
import cz.tal0052.edisonrozvrh.data.parser.EdisonParser
import cz.tal0052.edisonrozvrh.data.parser.StudyPageData

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.text.Normalizer
import java.util.Locale

class EdisonParserFixtureTest {
    @Test
    fun parseWebCredit_fixture_parsesBalanceAndCurrency() {
        val file = sequenceOf(
            File("fixtures/WebCredit.html"),
            File("../fixtures/WebCredit.html")
        ).firstOrNull { it.exists() } ?: File("fixtures/WebCredit.html")
        assertTrue("Fixture not found: ${file.absolutePath}", file.exists())

        val html = file.readText()
        val webCredit = EdisonParser.parseWebCredit(html)

        assertNotNull("WebCredit data is null", webCredit)
        assertEquals(511.30, webCredit!!.balance ?: 0.0, 0.001)
        assertEquals("CZK", webCredit.currencyCode)
    }

    @Test
    fun parseSchedulePreview_fixture_hasAllActivities() {
        val file = sequenceOf(
            File("fixtures/schedule_preview.html"),
            File("../fixtures/schedule_preview.html")
        ).firstOrNull { it.exists() } ?: File("fixtures/schedule_preview.html")
        assertTrue("Fixture not found: ${file.absolutePath}", file.exists())

        val html = file.readText()
        val context = EdisonParser.parseScheduleContext(html)
        assertNotNull("Schedule context is null", context)

        val activities = context!!.activities
        assertTrue("Expected at least 10 activities", activities.size >= 10)
    }

    @Test
    fun buildPositionedLessonsForDay_fixture_preservesAllSubjectsPerDay() {
        val file = sequenceOf(
            File("fixtures/schedule_preview.html"),
            File("../fixtures/schedule_preview.html")
        ).firstOrNull { it.exists() } ?: File("fixtures/schedule_preview.html")
        assertTrue("Fixture not found: ${file.absolutePath}", file.exists())

        val html = file.readText()
        val context = EdisonParser.parseScheduleContext(html)
        assertNotNull("Schedule context is null", context)

        val lessons = context!!.activities.map { seed ->
            Lesson(
                subject = seed.subject,
                teacher = seed.teacher,
                room = seed.room,
                day = seed.day,
                time = seed.time,
                type = seed.type,
                weekPattern = seed.weekPattern
            )
        }

        val monday = buildPositionedLessonsForDay(lessons, "Pondeli")
        val tuesday = buildPositionedLessonsForDay(lessons, "Utery")
        val wednesday = buildPositionedLessonsForDay(lessons, "Streda")
        val thursday = buildPositionedLessonsForDay(lessons, "Ctvrtek")

        assertTrue("Monday should have at least 3 subjects", monday.size >= 3)
        assertTrue("Tuesday should have at least 3 subjects", tuesday.size >= 3)
        assertTrue("Wednesday should have at least 3 subjects", wednesday.size >= 3)
        assertTrue("Thursday should have at least 3 subjects", thursday.size >= 3)
        assertTrue(
            "A/IV-FEI should remain complete",
            thursday.any { it.lesson.subject == "A/IV-FEI" }
        )
    }
    @Test
    fun parseSportActivities_fixture_parsesMyActivitiesTable() {
        val file = sequenceOf(
            File("fixtures/sports_preview.html"),
            File("../fixtures/sports_preview.html")
        ).firstOrNull { it.exists() } ?: File("fixtures/sports_preview.html")
        assertTrue("Fixture not found: ${file.absolutePath}", file.exists())

        val html = file.readText()
        val activities = EdisonParser.parseSportActivities(html)

        assertTrue("Expected at least one sport activity", activities.isNotEmpty())
        val sport = activities.first()
        assertTrue("Expected parsed sport day Utery", sport.day == "Utery")
        assertTrue("Expected parsed sport time", sport.time == "9:00 - 10:30")
        assertTrue("Expected parsed sport subject", sport.subject == "Volejbal")
        assertEquals("every", sport.weekPattern)
    }
    @Test
    fun parseSportActivitiesJson_fixture_parsesApiPayload() {
        val json = """
            [
              {
                "sportTitle": "Volejbal",
                "weekDayAbbrev": "�t",
                "scheduleWindowTimeText": "9:00-10:30",
                "sportPlaceTitle": "Telocvicna Kolej A",
                "teacherName": "Kyselov�"
              }
            ]
        """.trimIndent()

        val activities = EdisonParser.parseSportActivitiesJson(json)

        assertTrue("Expected at least one sport activity from JSON", activities.isNotEmpty())
        val sport = activities.first()
        assertTrue("Expected parsed sport day Utery", sport.day == "Utery")
        assertTrue("Expected parsed sport time", sport.time == "9:00 - 10:30")
        assertTrue("Expected parsed sport type", sport.type == "sport")
        assertEquals("every", sport.weekPattern)
    }

    @Test
    fun parseCurrentResults_fixture_parsesRowsAndSummary() {
        val file = sequenceOf(
            File("fixtures/results_preview.html"),
            File("../fixtures/results_preview.html")
        ).firstOrNull { it.exists() } ?: File("fixtures/results_preview.html")
        assertTrue("Fixture not found: ${file.absolutePath}", file.exists())

        val html = file.readText()
        val currentResults = EdisonParser.parseCurrentResults(html)

        assertNotNull("Current results are null", currentResults)
        assertTrue("Expected at least 10 result rows", currentResults!!.items.size >= 10)
        assertEquals("Expected academic year", "2025/2026", currentResults.academicYear)
        assertTrue("Warning note should be parsed", currentResults.warningNote.isNotBlank())
    }

    @Test
    fun parseCurrentResults_fixture_parsesIconAndNumericCells() {
        val file = sequenceOf(
            File("fixtures/results_preview.html"),
            File("../fixtures/results_preview.html")
        ).firstOrNull { it.exists() } ?: File("fixtures/results_preview.html")
        assertTrue("Fixture not found: ${file.absolutePath}", file.exists())

        val html = file.readText()
        val currentResults = EdisonParser.parseCurrentResults(html)
        assertNotNull("Current results are null", currentResults)

        val fyi = currentResults!!.items.firstOrNull { it.subjectShortcut == "FYI" }
        assertNotNull("FYI row was not parsed", fyi)
        assertEquals("22", fyi!!.creditPoints)
        assertEquals("35", fyi.examPoints)
        assertEquals("57", fyi.totalPoints)
        assertEquals("3", fyi.grade)
        assertEquals("E", fyi.ectsGrade)
        assertTrue("Detail URL should be absolute", fyi.detailUrl.startsWith("https://edison.sso.vsb.cz/"))

        val alg = currentResults.items.firstOrNull { it.subjectShortcut == "ALG I" }
        assertNotNull("ALG I row was not parsed", alg)
        assertEquals("...", alg!!.creditPoints)
        assertEquals("...", alg.totalPoints)
    }
    @Test
    fun parseSchedulePreview_fixture_parsesWeekPatternHints() {
        val file = sequenceOf(
            File("fixtures/schedule_preview.html"),
            File("../fixtures/schedule_preview.html")
        ).firstOrNull { it.exists() } ?: File("fixtures/schedule_preview.html")
        assertTrue("Fixture not found: ${file.absolutePath}", file.exists())

        val html = file.readText()
        val context = EdisonParser.parseScheduleContext(html)
        assertNotNull("Schedule context is null", context)

        val oop = context!!.activities.firstOrNull { it.subject == "OOP" }
        assertNotNull("OOP lesson was not parsed", oop)
        assertEquals("even", oop!!.weekPattern)

        assertTrue(
            "Expected at least one weekly lesson",
            context.activities.any { it.weekPattern == "every" }
        )
    }

    @Test
    fun parseScheduleContext_inlineHtml_parsesOddWeekPattern() {
        val html = """
            <html><body>
              <form id="f" name="f" action="/schedule">
                <table class="schedTable">
                  <tbody>
                    <tr>
                      <th>Den</th>
                      <th>8:00<br/>8:45</th>
                    </tr>
                    <tr>
                      <th>Pondel�</th>
                      <td>
                        <table class="actTable schedLecture">
                          <tr>
                            <td><a href="#"><abbr>ALG</abbr></a><b>Lich�</b></td>
                            <td class="rightAlign topAlign" rowspan="2">
                              <a class="commandLink" onclick="return myfaces.oam.submitForm('f','f:detailLink',null,[['concreteActivityId','123']]);"></a>
                            </td>
                          </tr>
                          <tr><td>Ing. Test</td></tr>
                          <tr><td>PORUA1</td><td class="rightAlign">P/<span class="outputText">01</span></td></tr>
                        </table>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </form>
            </body></html>
        """.trimIndent()

        val context = EdisonParser.parseScheduleContext(html)
        assertNotNull("Schedule context should be parsed", context)

        val activity = context!!.activities.firstOrNull()
        assertNotNull("Expected parsed activity", activity)
        assertEquals("odd", activity!!.weekPattern)
    }
    @Test
    fun parseSudyLichyFixture_parsesAllWeekPatterns() {
        val file = sequenceOf(
            File("fixtures/sudy_lichy_example.html"),
            File("../fixtures/sudy_lichy_example.html")
        ).firstOrNull { it.exists() } ?: File("fixtures/sudy_lichy_example.html")
        assertTrue("Fixture not found: ${file.absolutePath}", file.exists())

        val html = file.readText()
        val context = EdisonParser.parseScheduleContext(html)
        assertNotNull("Schedule context should be parsed", context)

        val bySubject = context!!.activities.associateBy { it.subject }
        assertEquals("odd", bySubject["ALG"]?.weekPattern)
        assertEquals("even", bySubject["OOP"]?.weekPattern)
        assertEquals("every", bySubject["SWI"]?.weekPattern)
    }

    @Test
    fun parseCurrentResultDetail_fixture_parsesSubjectDetailSections() {
        val file = sequenceOf(
            File("fixtures/subject_detail.example.html"),
            File("../fixtures/subject_detail.example.html")
        ).firstOrNull { it.exists() } ?: File("fixtures/subject_detail.example.html")
        assertTrue("Fixture not found: ${file.absolutePath}", file.exists())

        val html = file.readText()
        val detail = EdisonParser.parseCurrentResultDetail(html)
        assertNotNull("Subject detail is null", detail)

        val parsed = detail!!
        assertEquals("2025/2026 letn�", parsed.semester)
        assertTrue("Program should be parsed", parsed.program.contains("PSS"))
        assertEquals("prezencn�", parsed.form)
        assertEquals("bakal�rsk�", parsed.studyType)
        assertTrue("Expected at least one summary row", parsed.totalRows.isNotEmpty())
        assertTrue("Expected at least three checkpoints", parsed.checkpoints.size >= 3)
    }
    @Test
    fun parsePersonalStudyPage_fixture_parsesCoreFields() {
        val file = sequenceOf(
            File("fixtures/osobni_udaje.html"),
            File("../fixtures/osobni_udaje.html")
        ).firstOrNull { it.exists() } ?: File("fixtures/osobni_udaje.html")
        assertTrue("Fixture not found: ${file.absolutePath}", file.exists())

        val html = file.readText()
        val page = EdisonParser.parsePersonalStudyPage(html)
        assertNotNull("Personal study page is null", page)

        val parsed = page!!
        assertTrue("Expected parsed personal sections", parsed.sections.isNotEmpty())

        val studentName = studyFieldValue(parsed, "jmeno")
        assertTrue("Expected parsed student name", studentName.contains("Talman"))

        val email = studyFieldValue(parsed, "email")
        assertTrue("Expected parsed email", email.contains("@"))
    }

    @Test
    fun parseMatriculationStudyPage_fixture_parsesSummaryAndStages() {
        val file = sequenceOf(
            File("fixtures/matricni_udaje.html"),
            File("../fixtures/matricni_udaje.html")
        ).firstOrNull { it.exists() } ?: File("fixtures/matricni_udaje.html")
        assertTrue("Fixture not found: ${file.absolutePath}", file.exists())

        val html = file.readText()
        val page = EdisonParser.parseMatriculationStudyPage(html)
        assertNotNull("Matriculation page is null", page)

        val parsed = page!!
        assertEquals("1", studyFieldValue(parsed, "pocet soubeznych studii"))
        assertTrue("Expected at least one matriculation table", parsed.tables.isNotEmpty())

        val firstRow = parsed.tables.first().rows.firstOrNull().orEmpty()
        assertTrue("Expected stage row with start date", firstRow.any { it.contains("1.7.2024") })
    }

    @Test
    fun parseAdmissionStudyPage_fixture_parsesStatusAndAverages() {
        val file = sequenceOf(
            File("fixtures/udaje_z_prijimaciho_rizeni.html"),
            File("../fixtures/udaje_z_prijimaciho_rizeni.html")
        ).firstOrNull { it.exists() } ?: File("fixtures/udaje_z_prijimaciho_rizeni.html")
        assertTrue("Fixture not found: ${file.absolutePath}", file.exists())

        val html = file.readText()
        val page = EdisonParser.parseAdmissionStudyPage(html)
        assertNotNull("Admission page is null", page)

        val parsed = page!!
        val applicationStatus = studyFieldValue(parsed, "stav prihlasky")
        assertEquals("zapsan", normalizeStudyToken(applicationStatus))

        val decision = studyFieldValue(parsed, "rozhodnuti")
        assertTrue("Expected admission decision", normalizeStudyToken(decision).contains("prijat"))

        val hasAverage = parsed.tables
            .flatMap { table -> table.rows }
            .flatten()
            .any { value -> value.contains("1,64") }
        assertTrue("Expected parsed average from admission table", hasAverage)
    }

    private fun studyFieldValue(page: StudyPageData, labelHint: String): String {
        val hint = normalizeStudyToken(labelHint)

        return page.sections.asSequence()
            .flatMap { section -> section.fields.asSequence() }
            .firstOrNull { field ->
                field.value.isNotBlank() && normalizeStudyToken(field.label).contains(hint)
            }
            ?.value
            .orEmpty()
    }

    private fun normalizeStudyToken(raw: String): String {
        val noDiacritics = Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")

        return noDiacritics
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }
}

