package com.friday.wimm.data.model

data class Card(
    val id: Long = 0,
    val name: String,
    val startTime: Long, // 起始时间
    val endTime: Long = 0, // 终止时间，0表示无终止时间（全局卡片）
    val isGlobal: Boolean = false, // 是否是全局卡片
    val createdAt: Long = System.currentTimeMillis()
)
