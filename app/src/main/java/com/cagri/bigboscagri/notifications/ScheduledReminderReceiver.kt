package com.cagri.bigboscagri.notifications

/**
 * Compatibility receiver for project folders that still contain the former
 * scheduled-reminder manifest entry. The current implementation reconciles
 * every due reminder from persistent storage instead of trusting a stale ID.
 */
@Deprecated("Use ReminderAlarmReceiver")
class ScheduledReminderReceiver : ReminderAlarmReceiver()
