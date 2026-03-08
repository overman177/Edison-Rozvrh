package cz.tal0052.edisonrozvrh

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EdisonParserFixtureTest {

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
                type = seed.type
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
    }
    @Test
    fun parseSportActivitiesJson_fixture_parsesApiPayload() {
        val json = """
            [
              {
                "sportTitle": "Volejbal",
                "weekDayAbbrev": "Út",
                "scheduleWindowTimeText": "9:00-10:30",
                "sportPlaceTitle": "Tělocvična Kolej A",
                "teacherName": "Kyselová"
              }
            ]
        """.trimIndent()

        val activities = EdisonParser.parseSportActivitiesJson(json)

        assertTrue("Expected at least one sport activity from JSON", activities.isNotEmpty())
        val sport = activities.first()
        assertTrue("Expected parsed sport day Utery", sport.day == "Utery")
        assertTrue("Expected parsed sport time", sport.time == "9:00 - 10:30")
        assertTrue("Expected parsed sport type", sport.type == "sport")
    }
}
