@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.llamatik.sdk.agent.action

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.app.NotificationCompat
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

// All Android action IDs intentionally match the BuiltInToolDefinitions tool IDs
// so AgentExecutionEngine can resolve them via ActionRegistry.get(step.toolId).

class AndroidCalendarAction(private val context: Context) : Action {
    override val id = "calendar.create_event"
    override fun isSupported() = true
    override fun requiredPermissions() = setOf("android.permission.WRITE_CALENDAR", "android.permission.READ_CALENDAR")

    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["title"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: title")
        if (context.arguments["date"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: date")
        return ActionValidationResult(true)
    }

    override suspend fun execute(context: ActionContext): ActionResult {
        val title = context.arguments["title"] ?: return ActionResult.Failure("Missing title")
        val dateStr = context.arguments["date"] ?: return ActionResult.Failure("Missing date")
        val timeStr = context.arguments["time"] ?: "09:00"
        val durationMinutes = context.arguments["duration_minutes"]?.toLongOrNull() ?: 60L
        val notes = context.arguments["notes"] ?: ""

        return runCatching {
            val localDate = LocalDate.parse(dateStr)
            val timeParts = timeStr.split(":").map { it.toInt() }
            val hour = timeParts.getOrElse(0) { 9 }
            val minute = timeParts.getOrElse(1) { 0 }
            val localDateTime = LocalDateTime(localDate.year, localDate.month, localDate.dayOfMonth, hour, minute)
            val startMs = localDateTime.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
            val endMs = startMs + durationMinutes * 60_000L

            val calendarId = findPrimaryCalendarId() ?: 1L

            val cv = ContentValues().apply {
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DTSTART, startMs)
                put(CalendarContract.Events.DTEND, endMs)
                put(CalendarContract.Events.DESCRIPTION, notes)
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.currentSystemDefault().id)
            }

            val uri = this.context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, cv)
                ?: return ActionResult.Failure("Calendar insertion returned null — check WRITE_CALENDAR permission")

            ActionResult.Success(
                summary = "Calendar event '$title' created on $dateStr at $timeStr",
                data = mapOf("event_uri" to uri.toString())
            )
        }.getOrElse { e ->
            // Fallback: open calendar editor if direct insert fails
            runCatching {
                val startMs = runCatching {
                    val d = LocalDate.parse(dateStr)
                    val parts = timeStr.split(":").map { it.toInt() }
                    LocalDateTime(d.year, d.month, d.dayOfMonth, parts.getOrElse(0){9}, parts.getOrElse(1){0})
                        .toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
                }.getOrElse { System.currentTimeMillis() }

                val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
                    putExtra(CalendarContract.Events.TITLE, title)
                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMs)
                    putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startMs + durationMinutes * 60_000L)
                    putExtra(CalendarContract.Events.DESCRIPTION, notes)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                this.context.startActivity(intent)
                ActionResult.Success("Calendar editor opened for '$title' — please save the event manually")
            }.getOrElse {
                ActionResult.Failure("Failed to create calendar event: ${e.message}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun findPrimaryCalendarId(): Long? {
        return runCatching {
            val cursor = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY),
                "${CalendarContract.Calendars.VISIBLE} = 1",
                null,
                "${CalendarContract.Calendars.IS_PRIMARY} DESC"
            )
            cursor?.use {
                if (it.moveToFirst()) it.getLong(0) else null
            }
        }.getOrNull()
    }
}

class AndroidReminderAction(private val context: Context) : Action {
    override val id = "reminder.create"
    override fun isSupported() = true
    override fun requiredPermissions() = setOf("android.permission.SET_ALARM")

    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["title"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: title")
        return ActionValidationResult(true)
    }

    override suspend fun execute(context: ActionContext): ActionResult {
        val title = context.arguments["title"] ?: return ActionResult.Failure("Missing title")
        val timeStr = context.arguments["time"]
        val dateStr = context.arguments["date"]

        return runCatching {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_MESSAGE, title)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                if (timeStr != null) {
                    val parts = timeStr.split(":").map { it.toInt() }
                    putExtra(AlarmClock.EXTRA_HOUR, parts.getOrElse(0) { 9 })
                    putExtra(AlarmClock.EXTRA_MINUTES, parts.getOrElse(1) { 0 })
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            this.context.startActivity(intent)
            val timeDisplay = if (timeStr != null) " at $timeStr" else ""
            val dateDisplay = if (dateStr != null) " on $dateStr" else ""
            ActionResult.Success("Alarm set: '$title'$dateDisplay$timeDisplay")
        }.getOrElse { e ->
            ActionResult.Failure("Failed to set reminder: ${e.message}")
        }
    }
}

class AndroidOpenAppAction(private val context: Context) : Action {
    override val id = "apps.open"
    override fun isSupported() = true
    override fun requiredPermissions() = emptySet<String>()

    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["app_name"].isNullOrBlank() && context.arguments["package_id"].isNullOrBlank())
            return ActionValidationResult(false, "Missing app_name or package_id")
        return ActionValidationResult(true)
    }

    override suspend fun execute(context: ActionContext): ActionResult {
        val packageId = context.arguments["package_id"]
        val appName = context.arguments["app_name"]
        val pm = this.context.packageManager

        // Try direct package ID
        if (!packageId.isNullOrBlank()) {
            val launchIntent = pm.getLaunchIntentForPackage(packageId)
            if (launchIntent != null) {
                this.context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return ActionResult.Success("Opened app: $packageId")
            }
        }

        // Search by label
        if (!appName.isNullOrBlank()) {
            val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(mainIntent, 0)

            val exact = apps.firstOrNull { it.loadLabel(pm).toString().equals(appName, ignoreCase = true) }
            val fuzzy = exact ?: apps.firstOrNull { it.loadLabel(pm).toString().contains(appName, ignoreCase = true) }

            if (fuzzy != null) {
                val pkg = fuzzy.activityInfo.packageName
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    this.context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return ActionResult.Success("Opened: ${fuzzy.loadLabel(pm)}")
                }
            }
            return ActionResult.Failure("App not found: $appName")
        }

        return ActionResult.Failure("No matching app found")
    }
}

class AndroidOpenUrlAction(private val context: Context) : Action {
    override val id = "browser.open_url"
    override fun isSupported() = true
    override fun requiredPermissions() = emptySet<String>()

    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["url"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: url")
        return ActionValidationResult(true)
    }

    override suspend fun execute(context: ActionContext): ActionResult {
        var url = context.arguments["url"] ?: return ActionResult.Failure("Missing url")
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            this.context.startActivity(intent)
            ActionResult.Success("Opened: $url")
        }.getOrElse { e ->
            ActionResult.Failure("Failed to open URL: ${e.message}")
        }
    }
}

class AndroidClipboardAction(private val context: Context) : Action {
    override val id = "clipboard.copy"
    override fun isSupported() = true
    override fun requiredPermissions() = emptySet<String>()

    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["text"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: text")
        return ActionValidationResult(true)
    }

    override suspend fun execute(context: ActionContext): ActionResult {
        val text = context.arguments["text"] ?: return ActionResult.Failure("Missing text")
        return runCatching {
            val cm = this.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("llamatik", text))
            ActionResult.Success("Copied to clipboard: \"${text.take(60)}${if (text.length > 60) "…" else ""}\"")
        }.getOrElse { e ->
            ActionResult.Failure("Clipboard write failed: ${e.message}")
        }
    }
}

class AndroidNotificationAction(private val context: Context) : Action {
    override val id = "notifications.post"
    override fun isSupported() = true
    override fun requiredPermissions() = setOf("android.permission.POST_NOTIFICATIONS")

    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["title"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: title")
        return ActionValidationResult(true)
    }

    override suspend fun execute(context: ActionContext): ActionResult {
        val title = context.arguments["title"] ?: return ActionResult.Failure("Missing title")
        val body = context.arguments["body"] ?: ""
        val channelId = context.arguments["channel_id"] ?: CHANNEL_ID
        return runCatching {
            val nm = this.context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(channelId, "Llamatik Agent", NotificationManager.IMPORTANCE_DEFAULT)
                nm.createNotificationChannel(ch)
            }
            val notification = NotificationCompat.Builder(this.context, channelId)
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .build()
            nm.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
            ActionResult.Success("Notification posted: '$title'")
        }.getOrElse { e ->
            ActionResult.Failure("Notification failed: ${e.message}")
        }
    }

    companion object {
        private const val CHANNEL_ID = "llamatik_agent"
    }
}

class AndroidShareAction(private val context: Context) : Action {
    override val id = "share.content"
    override fun isSupported() = true
    override fun requiredPermissions() = emptySet<String>()

    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["text"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: text")
        return ActionValidationResult(true)
    }

    override suspend fun execute(context: ActionContext): ActionResult {
        val text = context.arguments["text"] ?: return ActionResult.Failure("Missing text")
        val title = context.arguments["title"]
        return runCatching {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                title?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(send, title ?: "Share").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            this.context.startActivity(chooser)
            ActionResult.Success("Share sheet opened")
        }.getOrElse { e ->
            ActionResult.Failure("Share failed: ${e.message}")
        }
    }
}

class AndroidContactsAction(private val context: Context) : Action {
    override val id = "contacts.search"
    override fun isSupported() = true
    override fun requiredPermissions() = setOf("android.permission.READ_CONTACTS")

    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["query"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: query")
        return ActionValidationResult(true)
    }

    override suspend fun execute(context: ActionContext): ActionResult {
        val query = context.arguments["query"] ?: return ActionResult.Failure("Missing query")
        val limit = context.arguments["limit"]?.toIntOrNull() ?: 5
        return runCatching {
            val results = mutableListOf<String>()
            val cursor = this.context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$query%"),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )
            cursor?.use {
                while (it.moveToNext() && results.size < limit) {
                    val name = it.getString(0) ?: continue
                    val number = it.getString(1) ?: ""
                    results.add("$name: $number")
                }
            }
            if (results.isEmpty()) {
                ActionResult.Failure("No contacts found for: '$query'")
            } else {
                ActionResult.Success(
                    summary = "Found ${results.size} contact(s) for '$query'",
                    data = mapOf("contacts" to results.joinToString("\n"))
                )
            }
        }.getOrElse { e ->
            ActionResult.Failure("Contacts query failed: ${e.message}")
        }
    }
}

class AndroidSettingsAction(private val context: Context) : Action {
    override val id = "settings.open"
    override fun isSupported() = true
    override fun requiredPermissions() = emptySet<String>()

    override fun validate(context: ActionContext) = ActionValidationResult(true)

    override suspend fun execute(context: ActionContext): ActionResult {
        val panel = context.arguments["panel"]?.lowercase() ?: "general"
        return runCatching {
            val action = when (panel) {
                "wifi" -> Settings.ACTION_WIFI_SETTINGS
                "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                "battery", "battery_saver" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
                "notifications" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS
                } else {
                    Settings.ACTION_APPLICATION_SETTINGS
                }
                "app_settings", "apps" -> Settings.ACTION_APPLICATION_SETTINGS
                "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
                "display" -> Settings.ACTION_DISPLAY_SETTINGS
                "sound" -> Settings.ACTION_SOUND_SETTINGS
                "network", "mobile_data" -> Settings.ACTION_WIRELESS_SETTINGS
                "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
                "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
                "date_time" -> Settings.ACTION_DATE_SETTINGS
                "language" -> Settings.ACTION_LOCALE_SETTINGS
                else -> Settings.ACTION_SETTINGS
            }
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            this.context.startActivity(intent)
            ActionResult.Success("Opened $panel settings")
        }.getOrElse { e ->
            ActionResult.Failure("Failed to open settings: ${e.message}")
        }
    }
}

/** Returns all Android platform actions for registration, requiring an application [Context]. */
fun androidPlatformActions(context: Context): List<Action> = listOf(
    AndroidCalendarAction(context),
    AndroidReminderAction(context),
    AndroidOpenAppAction(context),
    AndroidOpenUrlAction(context),
    AndroidClipboardAction(context),
    AndroidNotificationAction(context),
    AndroidShareAction(context),
    AndroidContactsAction(context),
    AndroidSettingsAction(context),
)
