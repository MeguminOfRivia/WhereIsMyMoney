package com.friday.wimm.data.model

/**
 * 收支大类。
 *
 * @param kind "expense" 支出类 / "income" 收入类
 * @param sortOrder 排序（越小越靠前）
 * @param isBuiltin 内置大类（可以停用但不会被误删）
 */
data class Category(
    val id: Long = 0,
    val name: String,
    val color: String,
    val kind: String = KIND_EXPENSE,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    val isBuiltin: Boolean = false
) {
    companion object {
        const val KIND_EXPENSE = "expense"
        const val KIND_INCOME = "income"
    }
}
