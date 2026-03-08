package cz.tal0052.edisonrozvrh.widget

import cz.tal0052.edisonrozvrh.R
import cz.tal0052.edisonrozvrh.app.PositionedLesson
import cz.tal0052.edisonrozvrh.app.buildPositionedLessonsForDay
import cz.tal0052.edisonrozvrh.app.loadSchedule

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class ScheduleWidgetSnapshotProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_TICK -> refreshAll(context)
        }
        super.onReceive(context, intent)
    }

    companion object {
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ScheduleWidgetSnapshotProvider::class.java))
            if (ids.isEmpty()) return

            ids.forEach { appWidgetId ->
                updateWidget(context, manager, appWidgetId)
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_schedule_snapshot)

            val now = LocalTime.now()
            val today = LocalDate.now()
            val day = today.toUiDay()
            val lessons = buildPositionedLessonsForDay(loadSchedule(context).orEmpty(), day)

            val nowMinutes = now.hour * 60 + now.minute
            val ongoing = lessons.firstOrNull { nowMinutes in it.startMinutes until it.endMinutes }
            val nextFromNow = lessons.firstOrNull { it.startMinutes > nowMinutes }
            val primary = ongoing ?: nextFromNow
            val anchorStart = primary?.startMinutes ?: nowMinutes

            val upcoming = lessons
                .filter { it.startMinutes > anchorStart }
                .take(2)

            views.setTextViewText(R.id.snapshotDay, today.toCzechLongDay())
            views.setTextViewText(
                R.id.snapshotNow,
                now.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT))
            )

            if (primary != null) {
                views.setViewVisibility(R.id.snapshotPrimaryCard, View.VISIBLE)
                views.setTextViewText(R.id.snapshotPrimarySubject, primary.lesson.subject)
                views.setTextViewText(R.id.snapshotPrimaryRoom, primary.lesson.room.ifBlank { "-" })
                views.setTextViewText(R.id.snapshotPrimaryTime, primary.shownTime)

                bindUpcomingRow(
                    views = views,
                    rowContainerId = R.id.snapshotRow1,
                    rowLeftId = R.id.snapshotNext1Left,
                    rowTimeId = R.id.snapshotNext1Time,
                    lesson = upcoming.getOrNull(0)
                )
                bindUpcomingRow(
                    views = views,
                    rowContainerId = R.id.snapshotRow2,
                    rowLeftId = R.id.snapshotNext2Left,
                    rowTimeId = R.id.snapshotNext2Time,
                    lesson = upcoming.getOrNull(1)
                )
            } else {
                views.setViewVisibility(R.id.snapshotPrimaryCard, View.GONE)

                views.setViewVisibility(R.id.snapshotRow1, View.VISIBLE)
                views.setTextViewText(R.id.snapshotNext1Left, context.getString(R.string.widget_no_lessons))
                views.setViewVisibility(R.id.snapshotNext1Time, View.GONE)

                views.setViewVisibility(R.id.snapshotRow2, View.GONE)
            }

            views.setImageViewResource(R.id.snapshotLogo, R.drawable.logo_emblem)

            val openAppPending = buildOpenAppPendingIntent(context, 2000 + appWidgetId)
            views.setOnClickPendingIntent(R.id.snapshotRoot, openAppPending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun bindUpcomingRow(
            views: RemoteViews,
            rowContainerId: Int,
            rowLeftId: Int,
            rowTimeId: Int,
            lesson: PositionedLesson?
        ) {
            if (lesson == null) {
                views.setViewVisibility(rowContainerId, View.GONE)
                return
            }

            views.setViewVisibility(rowContainerId, View.VISIBLE)
            views.setViewVisibility(rowTimeId, View.VISIBLE)

            val left = "| ${lesson.lesson.subject}  ${lesson.lesson.room.ifBlank { "-" }}"
            views.setTextViewText(rowLeftId, left)
            views.setTextViewText(rowTimeId, minutesToShortTime(lesson.startMinutes))
        }

        private fun minutesToShortTime(totalMinutes: Int): String {
            val h = totalMinutes / 60
            val m = totalMinutes % 60
            return String.format(Locale.ROOT, "%d:%02d", h, m)
        }
    }
}


