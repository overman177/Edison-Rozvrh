package cz.tal0052.edisonrozvrh.widget

import cz.tal0052.edisonrozvrh.R
import cz.tal0052.edisonrozvrh.app.MainActivity

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class ScheduleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            runCatching {
                updateWidget(context, appWidgetManager, appWidgetId)
            }.onFailure { throwable ->
                Log.e(TAG, "Failed to update widget id=$appWidgetId", throwable)
            }
        }
    }

    override fun onEnabled(context: Context) {
        refreshAll(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        runCatching {
            updateWidget(context, appWidgetManager, appWidgetId)
            appWidgetManager.notifyAppWidgetViewDataChanged(intArrayOf(appWidgetId), R.id.widgetList)
        }.onFailure { throwable ->
            Log.e(TAG, "Failed to update widget options for id=$appWidgetId", throwable)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_TICK -> {
                runCatching { refreshAll(context) }
                    .onFailure { throwable -> Log.e(TAG, "Failed to refresh schedule widget", throwable) }
                runCatching { ScheduleWidgetSnapshotProvider.refreshAll(context) }
                    .onFailure { throwable -> Log.e(TAG, "Failed to refresh snapshot widget", throwable) }
            }
        }
    }

    companion object {
        private const val TAG = "ScheduleWidgetProvider"

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ScheduleWidgetProvider::class.java))
            if (ids.isEmpty()) return

            ids.forEach { appWidgetId ->
                runCatching {
                    updateWidget(context, manager, appWidgetId)
                }.onFailure { throwable ->
                    Log.e(TAG, "Failed to refresh widget id=$appWidgetId", throwable)
                }
            }
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widgetList)
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_schedule)

            val today = LocalDate.now()
            views.setTextViewText(R.id.widgetTitle, today.toCzechShortDay().uppercase(Locale.ROOT))
            views.setTextViewText(
                R.id.widgetDate,
                today.format(DateTimeFormatter.ofPattern("d.M.", Locale.ROOT))
            )
            views.setTextViewText(
                R.id.widgetNow,
                LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT))
            )

            val openAppPending = buildOpenAppPendingIntent(context, 1000 + appWidgetId)
            views.setOnClickPendingIntent(R.id.widgetTitle, openAppPending)
            views.setOnClickPendingIntent(R.id.widgetNow, openAppPending)
            views.setOnClickPendingIntent(R.id.widgetHeader, openAppPending)

            runCatching {
                val serviceIntent = Intent(context, ScheduleWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                }
                views.setRemoteAdapter(R.id.widgetList, serviceIntent)
                views.setEmptyView(R.id.widgetList, R.id.widgetEmpty)
            }.onFailure { throwable ->
                Log.e(TAG, "Failed to bind RemoteViewsService for widget id=$appWidgetId", throwable)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

internal fun buildOpenAppPendingIntent(context: Context, requestCode: Int): PendingIntent {
    val launchIntent = Intent(context, MainActivity::class.java)
    return PendingIntent.getActivity(
        context,
        requestCode,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

internal fun LocalDate.toUiDay(): String {
    return when (dayOfWeek) {
        DayOfWeek.MONDAY -> "Pondeli"
        DayOfWeek.TUESDAY -> "Utery"
        DayOfWeek.WEDNESDAY -> "Streda"
        DayOfWeek.THURSDAY -> "Ctvrtek"
        DayOfWeek.FRIDAY -> "Patek"
        DayOfWeek.SATURDAY -> "Sobota"
        DayOfWeek.SUNDAY -> "Nedele"
    }
}

internal fun LocalDate.toCzechShortDay(): String {
    return when (dayOfWeek) {
        DayOfWeek.MONDAY -> "Po"
        DayOfWeek.TUESDAY -> "\u00dat"
        DayOfWeek.WEDNESDAY -> "St"
        DayOfWeek.THURSDAY -> "\u010ct"
        DayOfWeek.FRIDAY -> "P\u00e1"
        DayOfWeek.SATURDAY -> "So"
        DayOfWeek.SUNDAY -> "Ne"
    }
}

internal fun LocalDate.toCzechLongDay(): String {
    return when (dayOfWeek) {
        DayOfWeek.MONDAY -> "Pond\u011bl\u00ed"
        DayOfWeek.TUESDAY -> "\u00dater\u00fd"
        DayOfWeek.WEDNESDAY -> "St\u0159eda"
        DayOfWeek.THURSDAY -> "\u010ctvrtek"
        DayOfWeek.FRIDAY -> "P\u00e1tek"
        DayOfWeek.SATURDAY -> "Sobota"
        DayOfWeek.SUNDAY -> "Ned\u011ble"
    }
}
