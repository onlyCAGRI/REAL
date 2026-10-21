package com.cagri.real.data

/**
 * A fully-resolved edit for one existing reminder, produced from a spoken follow-up such as
 * "onu 8'e al" or "bunu kisisel kategorisine tasi". Only the flagged parts are applied.
 */
data class ReminderEditPatch(
    val text: String? = null,
    val setAlert: Boolean = false,
    val alertAt: Long? = null,
    val setRepeat: Boolean = false,
    val repeatRule: RecurrenceRule = RecurrenceRule.NONE,
    val weekdaysMask: Int = 0,
    val untilEpochDay: Long? = null,
    val setLocation: Boolean = false,
    val location: ReminderLocation? = null,
    val setCategory: Boolean = false,
    val customCategoryId: Long? = null,
    /** Date-only move for a card that has no clock time ("yarına ertele" on a planned-date card). */
    val setPlannedDate: Boolean = false,
    val plannedEpochDay: Long? = null
) {
    val isEmpty: Boolean
        get() = text == null && !setAlert && !setPlannedDate && !setRepeat && !setLocation && !setCategory
}
