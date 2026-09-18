package com.cagri.bigboscagri.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

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

    companion object {
        val mutationLock = Any()
        private const val PREFERENCES_NAME = "big_bos_cagri_categories"
        private const val KEY_CATEGORIES = "categories_json"
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

