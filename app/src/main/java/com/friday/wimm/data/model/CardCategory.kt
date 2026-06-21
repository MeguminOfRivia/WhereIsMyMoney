package com.friday.wimm.data.model

data class CardCategory(
    val id: Long = 0,
    val cardId: Long, // 关联的卡片ID
    val name: String, // 分类名称
    val color: String = "#E57373" // 分类颜色
)
