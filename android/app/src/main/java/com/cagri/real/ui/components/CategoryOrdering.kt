package com.cagri.real.ui.components

import com.cagri.real.data.CustomCategory
import com.cagri.real.data.ReminderCategory

sealed interface OrderedCategoryItem {
    val orderKey: String

    data class BuiltIn(val category: ReminderCategory) : OrderedCategoryItem {
        override val orderKey: String = categoryOrderKey(category)
    }

    data class Custom(val category: CustomCategory) : OrderedCategoryItem {
        override val orderKey: String = categoryOrderKey(category)
    }
}

fun categoryOrderKey(category: ReminderCategory): String = "builtin:${category.name}"
fun categoryOrderKey(category: CustomCategory): String = "custom:${category.id}"

/**
 * Resolves a stored category order against the categories that currently exist.
 * Missing/deleted keys are ignored and newly-created categories are appended using
 * REAL's normal default order, so older preference values remain forward compatible.
 */
fun resolveCategoryOrder(
    storedKeys: List<String>,
    customCategories: List<CustomCategory>
): List<OrderedCategoryItem> {
    val defaults = buildList<OrderedCategoryItem> {
        ReminderCategory.entries.forEach { add(OrderedCategoryItem.BuiltIn(it)) }
        customCategories.forEach { add(OrderedCategoryItem.Custom(it)) }
    }
    if (storedKeys.isEmpty()) return defaults

    val byKey = defaults.associateBy { it.orderKey }
    val used = HashSet<String>(defaults.size)
    return buildList {
        storedKeys.forEach { key ->
            val item = byKey[key]
            if (item != null && used.add(key)) add(item)
        }
        defaults.forEach { item ->
            if (used.add(item.orderKey)) add(item)
        }
    }
}

fun resolveCategoryOrderKeys(
    storedKeys: List<String>,
    customCategories: List<CustomCategory>
): List<String> = resolveCategoryOrder(storedKeys, customCategories).map { it.orderKey }

fun parseCategoryOrderPreference(raw: String?): List<String> = raw
    ?.split('|')
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    .orEmpty()

fun encodeCategoryOrderPreference(keys: List<String>): String = keys.joinToString("|")

// Visibility travels with the existing persisted/synced order preference. Old order
// entries stay valid; the resolver ignores metadata when resolving category IDs.
const val CATEGORY_SHOW_ALL_KEY = "action:show_all"
private const val CATEGORY_HIDDEN_PREFIX = "hidden:"

fun hiddenCategoryKeys(storedKeys: List<String>): Set<String> = storedKeys
    .filter { it.startsWith(CATEGORY_HIDDEN_PREFIX) }
    .map { it.removePrefix(CATEGORY_HIDDEN_PREFIX) }.toSet()

fun categoryOrderWithVisibility(order: List<String>, hidden: Set<String>): List<String> =
    order.filterNot { it.startsWith(CATEGORY_HIDDEN_PREFIX) || it == CATEGORY_SHOW_ALL_KEY }
        .distinct() + hidden.sorted().map { CATEGORY_HIDDEN_PREFIX + it }

fun resolveHomeCategoryOrder(storedKeys: List<String>, customCategories: List<CustomCategory>): List<OrderedCategoryItem> {
    val hidden = hiddenCategoryKeys(storedKeys)
    return resolveCategoryOrder(storedKeys, customCategories).filterNot {
        it.orderKey in hidden || (it is OrderedCategoryItem.BuiltIn &&
            (it.category == ReminderCategory.Today || it.category == ReminderCategory.JournalEntries))
    }
}

/** The slot belongs to the unchanged list while dragging; commit only on release. */
fun moveCategoryToSlot(keys: List<String>, key: String, slot: Int): List<String> {
    val from = keys.indexOf(key)
    if (from < 0) return keys
    val result = keys.toMutableList()
    result.removeAt(from)
    result.add((slot - if (from < slot) 1 else 0).coerceIn(0, result.size), key)
    return result
}
