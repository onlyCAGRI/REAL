package com.cagri.real.data

enum class BeverageType(
    val persistKey: String,
    val label: String,
    val quickAmounts: List<Int>,
    val visualGoalMl: Int
) {
    WATER("water", "Su", listOf(150, 250), 2_000),
    COFFEE("coffee", "Coffee", listOf(100, 200), 2_000),
    BEER("beer", "Beer", listOf(330, 500), 2_000);

    companion object {
        fun fromKey(key: String): BeverageType = entries.firstOrNull { it.persistKey == key }
            ?: error("Bilinmeyen içecek türü")
    }
}
