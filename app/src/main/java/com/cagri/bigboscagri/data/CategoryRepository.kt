package com.cagri.bigboscagri.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class CategoryColorOverride(
    val start: Int,
    val end: Int? = null
)

class CategoryRepository(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE))

    private var cachedRaw: String? = null
    private var cachedItems: List<CustomCategory> = emptyList()

    fun load(): List<CustomCategory> = synchronized(mutationLock) {
        val raw = preferences.getString(KEY_CATEGORIES, null) ?: return@synchronized emptyList()
        if (raw == cachedRaw) return@synchronized cachedItems
        val decoded = run {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        CustomCategory(
                            id = item.getLong("id"),
                            name = item.getString("name"),
                            iconKey = item.optString("iconKey", "WORK"),
                            accentStart = item.optInt("accentStart", 0),
                            accentEnd = if (!item.has("accentEnd") || item.isNull("accentEnd")) null else item.optInt("accentEnd"),
                            createdAt = item.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }
        }
        cachedRaw = raw
        cachedItems = decoded
        decoded
    }

    fun add(
        name: String,
        iconKey: String,
        accentStart: Int,
        accentEnd: Int? = null
    ): List<CustomCategory> = synchronized(mutationLock) {
        require(name.isNotBlank()) { "Kategori adı boş olamaz" }
        val now = System.currentTimeMillis()
        val previous = load()
        val id = maxOf(now, (previous.maxOfOrNull { it.id } ?: 0L) + 1L)
        val newCategory = CustomCategory(
            id = id,
            name = name.trim(),
            iconKey = iconKey,
            accentStart = accentStart,
            accentEnd = accentEnd,
            createdAt = now
        )
        val updated = previous + newCategory
        save(updated)
        updated
    }

    fun update(
        id: Long,
        name: String,
        iconKey: String,
        accentStart: Int,
        accentEnd: Int? = null
    ): List<CustomCategory> = synchronized(mutationLock) {
        require(name.isNotBlank()) { "Kategori adı boş olamaz" }
        val previous = load()
        val updated = previous.map { item ->
            if (item.id == id) {
                item.copy(
                    name = name.trim(),
                    iconKey = iconKey,
                    accentStart = accentStart,
                    accentEnd = accentEnd
                )
            } else {
                item
            }
        }
        save(updated)
        updated
    }

    fun delete(id: Long): List<CustomCategory> = synchronized(mutationLock) {
        load().filterNot { it.id == id }.also(::save)
    }

    fun deleteAll(categoryIds: Set<Long>): List<CustomCategory> = synchronized(mutationLock) {
        if (categoryIds.isEmpty()) return@synchronized load()
        load().filterNot { it.id in categoryIds }.also(::save)
    }

    fun restore(items: List<CustomCategory>): List<CustomCategory> = synchronized(mutationLock) {
        save(items)
        items
    }

    private fun save(items: List<CustomCategory>) {
        val array = JSONArray()
        items.forEach { category ->
            array.put(
                JSONObject()
                    .put("id", category.id)
                    .put("name", category.name)
                    .put("iconKey", category.iconKey)
                    .put("accentStart", category.accentStart)
                    .put("accentEnd", category.accentEnd ?: JSONObject.NULL)
                    .put("createdAt", category.createdAt)
            )
        }
        val raw = array.toString()
        check(preferences.edit().putString(KEY_CATEGORIES, raw).commit()) { "Kategori kaydedilemedi" }
        cachedRaw = raw
        cachedItems = items
    }

    fun getBuiltInCategoryColor(category: ReminderCategory): CategoryColorOverride? = synchronized(mutationLock) {
        val raw = preferences.getString(KEY_BUILTIN_CATEGORY_COLORS, null) ?: return@synchronized null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return@synchronized null
        if (!json.has(category.name) || json.isNull(category.name)) return@synchronized null
        val value = json.get(category.name)
        when (value) {
            is Int -> CategoryColorOverride(start = value, end = null)
            is Number -> CategoryColorOverride(start = value.toInt(), end = null)
            is JSONObject -> {
                val start = value.optInt("start", 0)
                val end = if (value.has("end") && !value.isNull("end")) value.getInt("end") else null
                CategoryColorOverride(start = start, end = end)
            }
            else -> null
        }
    }

    fun setBuiltInCategoryColor(category: ReminderCategory, override: CategoryColorOverride?): Map<ReminderCategory, CategoryColorOverride> = synchronized(mutationLock) {
        val raw = preferences.getString(KEY_BUILTIN_CATEGORY_COLORS, null)
        val json = if (raw != null) runCatching { JSONObject(raw) }.getOrNull() ?: JSONObject() else JSONObject()
        if (override != null) {
            val obj = JSONObject().put("start", override.start)
            if (override.end != null) {
                obj.put("end", override.end)
            }
            json.put(category.name, obj)
        } else {
            json.remove(category.name)
        }
        val newRaw = json.toString()
        check(preferences.edit().putString(KEY_BUILTIN_CATEGORY_COLORS, newRaw).commit()) { "Kategori rengi kaydedilemedi" }
        getBuiltInCategoryColors()
    }

    fun setBuiltInCategoryColor(category: ReminderCategory, colorIndex: Int): Map<ReminderCategory, CategoryColorOverride> =
        setBuiltInCategoryColor(category, CategoryColorOverride(colorIndex, null))

    fun getBuiltInCategoryColors(): Map<ReminderCategory, CategoryColorOverride> = synchronized(mutationLock) {
        val raw = preferences.getString(KEY_BUILTIN_CATEGORY_COLORS, null) ?: return@synchronized emptyMap()
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return@synchronized emptyMap()
        buildMap {
            for (cat in ReminderCategory.entries) {
                if (json.has(cat.name) && !json.isNull(cat.name)) {
                    val value = json.get(cat.name)
                    when (value) {
                        is Int -> put(cat, CategoryColorOverride(start = value, end = null))
                        is Number -> put(cat, CategoryColorOverride(start = value.toInt(), end = null))
                        is JSONObject -> {
                            val start = value.optInt("start", 0)
                            val end = if (value.has("end") && !value.isNull("end")) value.getInt("end") else null
                            put(cat, CategoryColorOverride(start = start, end = end))
                        }
                    }
                }
            }
        }
    }

    companion object {
        val mutationLock = Any()
        private const val PREFERENCES_NAME = "big_bos_cagri_categories"
        private const val KEY_CATEGORIES = "categories_json"
        private const val KEY_BUILTIN_CATEGORY_COLORS = "builtin_category_colors"
    }
}

fun deleteCategoryWithRollback(
    categoryId: Long,
    reminderRepository: ReminderRepository,
    categoryRepository: CategoryRepository,
    deleteCategoryStep: (Long) -> Unit = { categoryRepository.delete(it) }
): Pair<List<ReminderItem>, List<CustomCategory>> = synchronized(ReminderRepository.mutationLock) {
    synchronized(CategoryRepository.mutationLock) {
        val reminderSnapshot = reminderRepository.load()
        val categorySnapshot = categoryRepository.load()
        try {
            val updatedReminders = reminderRepository.unassignCategory(categoryId)
            deleteCategoryStep(categoryId)
            val updatedCategories = categoryRepository.load()
            updatedReminders to updatedCategories
        } catch (e: Exception) {
            try {
                reminderRepository.restore(reminderSnapshot)
                categoryRepository.restore(categorySnapshot)
            } catch (rollbackError: Exception) {
                try {
                    android.util.Log.e("Real", "Category rollback failed", rollbackError)
                } catch (_: Throwable) { }
            }
            throw e
        }
    }
}

fun deleteCategoriesWithRollback(
    categoryIds: Set<Long>,
    reminderRepository: ReminderRepository,
    categoryRepository: CategoryRepository
): Pair<List<ReminderItem>, List<CustomCategory>> = synchronized(ReminderRepository.mutationLock) {
    synchronized(CategoryRepository.mutationLock) {
        val reminderSnapshot = reminderRepository.load()
        val categorySnapshot = categoryRepository.load()
        try {
            val updatedReminders = reminderRepository.unassignCategories(categoryIds)
            val updatedCategories = categoryRepository.deleteAll(categoryIds)
            updatedReminders to updatedCategories
        } catch (e: Exception) {
            try {
                reminderRepository.restore(reminderSnapshot)
                categoryRepository.restore(categorySnapshot)
            } catch (rollbackError: Exception) {
                try {
                    android.util.Log.e("Real", "Categories rollback failed", rollbackError)
                } catch (_: Throwable) { }
            }
            throw e
        }
    }
}


