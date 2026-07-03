package zero.ramjikvarosai.hirebazzar

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateUtils
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Date

object GoalioMatchNotifier {
    private const val CHANNEL_ID = "goalio_live_match_alerts"
    private const val UPCOMING_NOTIFICATION_ID = 90210

    fun scheduleUpcoming(context: Context, matches: List<ScheduleMatch>) {
        val now = System.currentTimeMillis()
        val upcoming = matches.asSequence().filter { it.state == "pre" }
            .mapNotNull { match -> match.kickoffEpochMillis()?.takeIf { it > now }?.let { match to it } }
            .sortedBy { it.second }.take(20).toList()
        if (upcoming.isEmpty()) {
            NotificationManagerCompat.from(context).cancel(UPCOMING_NOTIFICATION_ID)
            return
        }
        showUpcomingCountdown(context, upcoming.first().first, upcoming.first().second)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        upcoming.forEach { (match, kickoff) ->
            listOf(
                24 * 60 * 60 * 1000L to R.string.notification_match_in_24_hours,
                60 * 60 * 1000L to R.string.notification_match_in_1_hour,
                0L to R.string.notification_kickoff_now
            ).forEach { (before, titleRes) ->
                    val trigger = kickoff - before
                    if (trigger <= now) return@forEach
                    val intent = Intent(context, MatchReminderReceiver::class.java).apply {
                        putExtra("title", context.getString(titleRes))
                        putExtra("message", match.compactNotificationName())
                        putExtra("kickoff", kickoff)
                    }
                    val requestCode = "${match.league}:${match.matchId}:$before".hashCode()
                    val pending = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
                }
        }
    }

    fun notifyBackgroundEvents(context: Context, events: List<MatchNotificationEvent>) {
        if (events.isEmpty() || GoalioAppVisibility.isForeground) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        val appContext = context.applicationContext
        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val manager = NotificationManagerCompat.from(appContext)
        events.forEach { event ->
            val contentText = event.kickoffEpochMillis?.let { kickoff ->
                "${matchClockText(kickoff)} • ${event.message}"
            } ?: event.message
            val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_goalio)
                .setColor(ContextCompat.getColor(appContext, R.color.goalio_tertiary))
                .setContentTitle(event.title)
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
            val notification = builder.build()
            manager.notify(event.id.hashCode(), notification)
        }
    }

    fun showScheduled(context: Context, title: String, message: String, kickoff: Long) {
        if (!canNotify(context)) return
        ensureChannel(context)
        NotificationManagerCompat.from(context).notify(
            "$title:$message".hashCode(),
            baseBuilder(context, title, message).setWhen(kickoff).setShowWhen(true).build()
        )
    }

    private fun showUpcomingCountdown(context: Context, match: ScheduleMatch, kickoff: Long) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val now = System.currentTimeMillis()
        val relativeKickoff = DateUtils.getRelativeTimeSpanString(
            kickoff,
            now,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        )
        val kickoffTime = DateFormat.getTimeFormat(context).format(Date(kickoff))
        val kickoffLine = "${context.getString(R.string.notification_kickoff)} $relativeKickoff • $kickoffTime"
        val venue = match.venue?.name?.takeIf(String::isNotBlank)
        val expandedText = listOfNotNull(kickoffLine, venue).joinToString("\n")
        val launch = contentIntent(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_goalio)
            .setColor(ContextCompat.getColor(context, R.color.goalio_tertiary))
            .setContentTitle(match.compactNotificationName())
            .setContentText(kickoffLine)
            .setSubText(context.getString(R.string.notification_next_match))
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setTimeoutAfter((kickoff - now).coerceAtLeast(0L) + DateUtils.HOUR_IN_MILLIS)
            .setContentIntent(launch)
            .addAction(0, context.getString(R.string.open), launch)
            .setOngoing(false)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
        NotificationManagerCompat.from(context).notify(UPCOMING_NOTIFICATION_ID, builder.build())
    }

    private fun baseBuilder(context: Context, title: String, text: String): NotificationCompat.Builder {
        val launch = contentIntent(context)
        return NotificationCompat.Builder(context, CHANNEL_ID).setSmallIcon(R.drawable.ic_stat_goalio)
            .setColor(ContextCompat.getColor(context, R.color.goalio_tertiary))
            .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true).setOnlyAlertOnce(true).setContentIntent(launch)
    }

    private fun contentIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun canNotify(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_description)
            }
        )
    }

    private fun matchClockText(kickoffEpochMillis: Long): String {
        val diffSeconds = ((kickoffEpochMillis - System.currentTimeMillis()) / 1000L)
        val prefix = if (diffSeconds >= 0) "Starts in" else "Live"
        val total = kotlin.math.abs(diffSeconds)
        val hours = total / 3600L
        val minutes = (total % 3600L) / 60L
        val seconds = total % 60L
        return "$prefix %02d:%02d:%02d".format(hours, minutes, seconds)
    }
}

class MatchReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        GoalioMatchNotifier.showScheduled(
            context,
            intent.getStringExtra("title") ?: context.getString(R.string.notification_match_reminder),
            intent.getStringExtra("message") ?: context.getString(R.string.notification_upcoming_match),
            intent.getLongExtra("kickoff", System.currentTimeMillis())
        )
    }
}
