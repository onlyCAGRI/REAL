package com.cagri.bigboscagri.ui.components

import com.cagri.bigboscagri.data.CustomCategory
import com.cagri.bigboscagri.data.ReminderCategory

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
