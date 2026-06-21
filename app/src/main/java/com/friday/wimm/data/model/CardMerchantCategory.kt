package com.friday.wimm.data.model

data class CardMerchantCategory(
    val id: Long = 0,
    val cardId: Long, // 关联的卡片ID
    val merchant: String, // 收款方名称
    val categoryId: Long // 关联的分类ID
)
