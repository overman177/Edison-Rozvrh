package cz.tal0052.edisonrozvrh.data.repository

import cz.tal0052.edisonrozvrh.app.Lesson
import cz.tal0052.edisonrozvrh.data.parser.CurrentResultsData
import cz.tal0052.edisonrozvrh.data.parser.EdisonParser
import cz.tal0052.edisonrozvrh.data.parser.ScheduleContext

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

object EdisonRepository {

    private const val SCHEDULE_URL = "https://edison.sso.vsb.cz/wps/myportal/student/rozvrh/rozvrh"
    private const val RESULTS_URL = "https://edison.sso.vsb.cz/wps/myportal/student/studium/aktualni-vysledky"
    private const val SPORTS_URL = "https://edison.sso.vsb.cz/wps/myportal/student/rozvrh/volba-sportu"
    private const val SPORTS_MY_ACTIVITIES_URL =
        "https://edison.sso.vsb.cz/wps/.cz.vsb.edison.edu.study.pass.portlet/jaxrs/sports/myactivities"

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun downloadDetailedSchedule(cookies: String?): List<Lesson>? {
        if (cookies.isNullOrBlank()) return null

        val firstScheduleHtml = downloadSchedule(cookies) ?: return null
        val firstContext = EdisonParser.parseScheduleContext(firstScheduleHtml) ?: return null

        val seeds = firstContext.activities
        val lessons = mutableListOf<Lesson>()

        seeds.forEachIndexed { index, seed ->
            val canOpenDetail = !seed.concreteActivityId.isNullOrBlank() && !seed.idcl.isNullOrBlank()

            val detail = if (canOpenDetail) {
                val context = if (index == 0) {
                    firstContext
                } else {
                    val freshHtml = downloadSchedule(cookies)
                    if (freshHtml != null) {
                        EdisonParser.parseScheduleContext(freshHtml) ?: firstContext
                    } else {
                        firstContext
                    }
                }

                val requestSeed = context.activities.firstOrNull {
                    it.concreteActivityId == seed.concreteActivityId
                }

                val detailHtml = if (requestSeed != null && !requestSeed.idcl.isNullOrBlank()) {
                    downloadActivityDetail(
                        cookies = cookies,
                        context = context,
                        concreteActivityId = seed.concreteActivityId,
                        idcl = requestSeed.idcl
                    )
                } else {
                    null
                }

                detailHtml?.let { EdisonParser.parseLessonDetail(it) }

            } else {
                null
            }

            val mergedType = when {
                detail == null -> seed.type
                detail.type == "other" -> seed.type
                detail.type.isBlank() -> seed.type
                else -> detail.type
            }

            val lesson = Lesson(
                subject = detail?.subject?.ifBlank { seed.subject } ?: seed.subject,
                teacher = detail?.teacher?.ifBlank { seed.teacher } ?: seed.teacher,
                room = detail?.room?.ifBlank { seed.room } ?: seed.room,
                day = seed.day,
                time = seed.time,
                type = mergedType,
                weekPattern = seed.weekPattern
            )

            lessons.add(lesson)
        }

        val sportsHtml = downloadSportsPage(cookies)
        val sportSeedsFromHtml = sportsHtml?.let { EdisonParser.parseSportActivities(it) }.orEmpty()
        val sportSeeds = if (sportSeedsFromHtml.isNotEmpty()) {
            sportSeedsFromHtml
        } else {
            val portletId = sportsHtml?.let { EdisonParser.extractSportsPortletId(it) }
            val json = if (portletId.isNullOrBlank()) null else {
                downloadSportActivitiesJson(cookies, portletId)
            }
            json?.let { EdisonParser.parseSportActivitiesJson(it) }.orEmpty()
        }

        sportSeeds.forEach { seed ->
            lessons.add(
                Lesson(
                    subject = seed.subject,
                    teacher = seed.teacher,
                    room = seed.room,
                    day = seed.day,
                    time = seed.time,
                    type = if (seed.type.isBlank()) "sport" else seed.type,
                    weekPattern = seed.weekPattern
                )
            )
        }

        val merged = lessons.distinctBy { lesson ->
            listOf(
                lesson.subject.lowercase(),
                lesson.day.lowercase(),
                lesson.time.lowercase(),
                lesson.teacher.lowercase(),
                lesson.room.lowercase(),
                lesson.weekPattern.lowercase()
            ).joinToString("|")
        }

        return merged.ifEmpty { null }
    }

    fun downloadCurrentResults(cookies: String?): CurrentResultsData? {
        if (cookies.isNullOrBlank()) return null

        val request = Request.Builder()
            .url(RESULTS_URL)
            .addHeader("Cookie", cookies)
            .addHeader("Referer", SCHEDULE_URL)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val html = response.body.string()
            val parsed = EdisonParser.parseCurrentResults(html) ?: return null

            val enrichedItems = parsed.items.map { item ->
                if (item.detailUrl.isBlank()) {
                    item
                } else {
                    val detailHtml = downloadCurrentResultDetail(cookies, item.detailUrl)
                    val detail = detailHtml?.let { EdisonParser.parseCurrentResultDetail(it) }
                    item.copy(detail = detail)
                }
            }

            return parsed.copy(items = enrichedItems)
        }
    }

    private fun downloadSchedule(cookies: String): String? {
        val request = Request.Builder()
            .url(SCHEDULE_URL)
            .addHeader("Cookie", cookies)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body.string()
        }
    }

    private fun downloadSportsPage(cookies: String): String? {
        val request = Request.Builder()
            .url(SPORTS_URL)
            .addHeader("Cookie", cookies)
            .addHeader("Referer", SCHEDULE_URL)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body.string()
        }
    }

    private fun downloadSportActivitiesJson(cookies: String, portletId: String): String? {
        val request = Request.Builder()
            .url("$SPORTS_MY_ACTIVITIES_URL?portletId=$portletId")
            .addHeader("Cookie", cookies)
            .addHeader("Referer", SPORTS_URL)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body.string()
        }
    }

    private fun downloadCurrentResultDetail(cookies: String, detailUrl: String): String? {
        val request = Request.Builder()
            .url(detailUrl)
            .addHeader("Cookie", cookies)
            .addHeader("Referer", RESULTS_URL)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body.string()
        }
    }
    private fun downloadActivityDetail(
        cookies: String,
        context: ScheduleContext,
        concreteActivityId: String,
        idcl: String
    ): String? {

        val formBodyBuilder = FormBody.Builder()

        context.baseParams.forEach { (key, value) ->
            formBodyBuilder.add(key, value)
        }

        val submitKey = "${context.formName}_SUBMIT"
        if (!context.baseParams.containsKey(submitKey)) {
            formBodyBuilder.add(submitKey, "1")
        }

        formBodyBuilder.add("${context.formName}:_idcl", idcl)
        formBodyBuilder.add("concreteActivityId", concreteActivityId)

        val detailRequest = Request.Builder()
            .url(context.actionUrl)
            .addHeader("Cookie", cookies)
            .addHeader("Referer", SCHEDULE_URL)
            .post(formBodyBuilder.build())
            .build()

        client.newCall(detailRequest).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body.string()
        }
    }
}




