package cz.tal0052.edisonrozvrh.widget

import cz.tal0052.edisonrozvrh.R
import cz.tal0052.edisonrozvrh.app.PositionedLesson
import cz.tal0052.edisonrozvrh.app.buildPositionedLessonsForDay
import cz.tal0052.edisonrozvrh.app.loadSchedule
import cz.tal0052.edisonrozvrh.app.normalizeWeekPatternCode

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

class ScheduleWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return ScheduleWidgetFactory(applicationContext)
    }
}

private data class WidgetLessonItem(
    val positioned: PositionedLesson,
    val roomText: String,
    val teacherText: String,
    val gapBeforeMinutes: Int,
    val rowStartMinutes: Int
)

private class ScheduleWidgetFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<WidgetLessonItem> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        try {
            val lessons = loadSchedule(context).orEmpty()
            val day = LocalDate.now().toUiDay()
            val positioned = buildPositionedLessonsForDay(lessons, day).take(8)

            if (positioned.isEmpty()) {
                items = emptyList()
                return
            }

            // First lesson starts right under header; limit huge gaps between lessons.
            var previousEnd = positioned.first().startMinutes
            items = positioned.mapIndexed { index, item ->
                val rawGap = (item.startMinutes - previousEnd).coerceAtLeast(0)
                val gapBefore = if (index == 0) 0 else rawGap.coerceAtMost(45)
                val rowStart = if (index == 0) item.startMinutes else item.startMinutes - gapBefore
                previousEnd = max(previousEnd, item.endMinutes)

                WidgetLessonItem(
                    positioned = item,
                    roomText = item.lesson.room.trim(),
                    teacherText = item.lesson.teacher.trim(),
                    gapBeforeMinutes = gapBefore,
                    rowStartMinutes = rowStart
                )
            }
        } catch (_: Throwable) {
            items = emptyList()
        }
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        return try {
            if (position !in items.indices) {
                return RemoteViews(context.packageName, R.layout.widget_schedule_item)
            }

            val item = items[position]
            val lesson = item.positioned.lesson
            val views = RemoteViews(context.packageName, R.layout.widget_schedule_item)

            val pxPerMinute = context.resources.displayMetrics.density * 0.72f
            val gapPx = (item.gapBeforeMinutes * pxPerMinute).roundToInt().coerceAtLeast(0)
            val durationMinutes = (item.positioned.endMinutes - item.positioned.startMinutes).coerceAtLeast(30)
            val minBubblePx = dpToPx(context, 64)
            val durationPx = max((durationMinutes * pxPerMinute).roundToInt(), minBubblePx)
            val rowHeightPx = gapPx + durationPx

            views.setViewPadding(R.id.itemContentRow, 0, gapPx, 0, 0)
            views.setInt(R.id.widgetBubble, "setMinimumHeight", durationPx)

            views.setTextViewText(R.id.itemTime, formatWidgetTime(item.positioned.shownTime, lesson.weekPattern))
            views.setTextViewText(R.id.itemSubject, lesson.subject)

            if (item.teacherText.isBlank()) {
                views.setViewVisibility(R.id.itemTeacher, View.GONE)
            } else {
                views.setViewVisibility(R.id.itemTeacher, View.VISIBLE)
                views.setTextViewText(R.id.itemTeacher, item.teacherText)
            }

            views.setTextViewText(R.id.itemRoom, item.roomText.ifBlank { "-" })

            val bubbleBg = when (lesson.type.trim().lowercase(Locale.ROOT)) {
                "cviko" -> R.drawable.widget_card_bg_cviko
                "prednaska" -> R.drawable.widget_card_bg_prednaska
                "lab" -> R.drawable.widget_card_bg_lab
                "sport" -> R.drawable.widget_card_bg_sport
                else -> R.drawable.widget_card_bg_default
            }
            views.setInt(R.id.widgetBubble, "setBackgroundResource", bubbleBg)

            val nowMinutes = LocalTime.now().hour * 60 + LocalTime.now().minute
            val rowStart = item.rowStartMinutes
            val rowEnd = item.positioned.endMinutes

            if (nowMinutes in rowStart..rowEnd) {
                val lineHeightPx = dpToPx(context, 3)
                val rawLineY = ((nowMinutes - rowStart) * pxPerMinute).roundToInt()
                val lineY = rawLineY.coerceIn(0, max(0, rowHeightPx - lineHeightPx))

                views.setViewVisibility(R.id.itemNowLine, View.VISIBLE)
                views.setFloat(R.id.itemNowLine, "setTranslationY", lineY.toFloat())
            } else {
                views.setViewVisibility(R.id.itemNowLine, View.GONE)
                views.setFloat(R.id.itemNowLine, "setTranslationY", 0f)
            }

            views
        } catch (_: Throwable) {
            RemoteViews(context.packageName, R.layout.widget_schedule_item)
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false
}

private fun dpToPx(context: Context, dp: Int): Int {
    return (dp * context.resources.displayMetrics.density).roundToInt()
}


private fun formatWidgetTime(timeRange: String, weekPattern: String): String {
    return when (normalizeWeekPatternCode(weekPattern)) {
        "odd" -> "$timeRange • Lichy"
        "even" -> "$timeRange • Sudy"
        else -> timeRange
    }
}
