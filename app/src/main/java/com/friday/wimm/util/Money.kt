package com.friday.wimm.util

import java.util.Locale

/**
 * 金额显示工具。
 *
 * 统一用 [Locale.US] 格式化，保证小数点永远是 `.` 而不是某些语言环境下的 `,`，
 * 避免「¥12,50」这种歧义。
 *
 * 注意：这里只是**显示**用的格式化。金额的存储与累加在后续版本会改成整数「分」，
 * 详见 `Transaction.amountCents`。
 */
object Money {

    fun format(amount: Double): String = String.format(Locale.US, "%.2f", amount)

    fun formatWithSymbol(amount: Double): String = "¥" + format(amount)

    /** 元 → 整数分（四舍五入），所有金额累加都应该先转成这个再相加 */
    fun toCents(amount: Double): Long = Math.round(amount * 100.0)

    /** 整数分 → 元（仅用于显示） */
    fun fromCents(cents: Long): Double = cents / 100.0

    fun formatCents(cents: Long): String = format(fromCents(cents))
}
