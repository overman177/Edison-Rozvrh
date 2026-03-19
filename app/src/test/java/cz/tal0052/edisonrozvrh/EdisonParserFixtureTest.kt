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
    private fun loadFixture(name: String): String {
        val file = sequenceOf(
            File("fixtures/$name"),
            File("../fixtures/$name")
        ).firstOrNull { it.exists() } ?: error("Fixture not found: $name")

        return file.readText()
    }

    @Test
    fun parseWebCredit_fixture_parsesBalanceAndCurrency() {
        val html = """
            <html>
              <body>
                <script>
                  window.wkIndexModel = {
                    "model": {
                      "currency": {
                        "code": "CZK",
                        "symbol": "Kc"
                      },
                      "balance": {
                        "balance": 511.30
                      }
                    }
                  };
                </script>
              </body>
            </html>
        """.trimIndent()
        val webCredit = EdisonParser.parseWebCredit(html)

        assertNotNull("WebCredit data is null", webCredit)
        assertEquals(511.30, webCredit!!.balance ?: 0.0, 0.001)
        assertEquals("CZK", webCredit.currencyCode)
    }

    @Test
    fun parseSchedulePreview_fixture_hasAllActivities() {
        val html = loadFixture("schedule_preview.html")
        val context = EdisonParser.parseScheduleContext(html)
        assertNotNull("Schedule context is null", context)

        val activities = context!!.activities
        assertTrue("Expected at least 10 activities", activities.size >= 10)
    }

    @Test
    fun buildPositionedLessonsForDay_fixture_preservesAllSubjectsPerDay() {
        val html = loadFixture("schedule_preview.html")
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
        val html = loadFixture("sports_preview.html")
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
                "weekDayAbbrev": "Ut",
                "scheduleWindowTimeText": "9:00-10:30",
                "sportPlaceTitle": "Telocvicna Kolej A",
                "teacherName": "Kyselova"
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
        val html = loadFixture("results_preview.html")
        val currentResults = EdisonParser.parseCurrentResults(html)

        assertNotNull("Current results are null", currentResults)
        assertTrue("Expected at least 10 result rows", currentResults!!.items.size >= 10)
        assertEquals("Expected academic year", "2025/2026", currentResults.academicYear)
        assertTrue("Warning note should be parsed", currentResults.warningNote.isNotBlank())
    }

    @Test
    fun parseCurrentResults_fixture_parsesIconAndNumericCells() {
        val html = loadFixture("results_preview.html")
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
        val html = loadFixture("schedule_preview.html")
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
                      <th>Pondeli</th>
                      <td>
                        <table class="actTable schedLecture">
                          <tr>
                            <td><a href="#"><abbr>ALG</abbr></a><b>Liche</b></td>
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
        val html = loadFixture("sudy_lichy_example.html")
        val context = EdisonParser.parseScheduleContext(html)
        assertNotNull("Schedule context should be parsed", context)

        val bySubject = context!!.activities.associateBy { it.subject }
        assertEquals("odd", bySubject["ALG"]?.weekPattern)
        assertEquals("even", bySubject["OOP"]?.weekPattern)
        assertEquals("every", bySubject["SWI"]?.weekPattern)
    }

    @Test
    fun parseCurrentResultDetail_fixture_parsesSubjectDetailSections() {
        val html = loadFixture("subject_detail.example.html")
        val detail = EdisonParser.parseCurrentResultDetail(html)
        assertNotNull("Subject detail is null", detail)

        val parsed = detail!!
        assertEquals("2025 2026 letni", normalizeStudyToken(parsed.semester))
        assertTrue("Program should be parsed", parsed.program.contains("PSS"))
        assertEquals("prezencni", normalizeStudyToken(parsed.form))
        assertEquals("bakalarsky", normalizeStudyToken(parsed.studyType))
        assertTrue("Expected at least one summary row", parsed.totalRows.isNotEmpty())
        assertTrue("Expected at least three checkpoints", parsed.checkpoints.size >= 3)
    }
    @Test
    fun parsePersonalStudyPage_fixture_parsesCoreFields() {
        val html = loadFixture("osobni_udaje.html")
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
        val html = loadFixture("matricni_udaje.html")
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
        val html = loadFixture("udaje_z_prijimaciho_rizeni.html")
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
