package com.cagri.bigboscagri.data

data class CustomCategory(
    val id: Long,
    val name: String,
    val iconKey: String,
    val accentStart: Int,
    val accentEnd: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
)
