package com.friday.wimm.data.model

/**
 * 账户（微信零钱 / 支付宝花呗 / 某张银行卡 / 现金 ...）。
 *
 * 余额 = [initialBalanceCents] + 起始时间之后的所有收入 − 支出（转账按方向加减）。
 * 起始时间之前的流水只进统计，不改余额——这样导入历史账单不会把当前余额算乱。
 */
data class Account(
    val id: Long = 0,
    val name: String,
    val type: String = TYPE_OTHER,
    /** 单位：分，可以为负（花呗欠款） */
    val initialBalanceCents: Long = 0,
    /** 从这个时刻开始，流水才影响余额 */
    val balanceStartAt: Long = 0,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0
) {
    companion object {
        const val TYPE_WECHAT = "wechat"
        const val TYPE_ALIPAY = "alipay"
        const val TYPE_BANK = "bank"
        const val TYPE_CASH = "cash"
        const val TYPE_CREDIT = "credit"
        const val TYPE_OTHER = "other"

        val ALL_TYPES = listOf(TYPE_WECHAT, TYPE_ALIPAY, TYPE_BANK, TYPE_CASH, TYPE_CREDIT, TYPE_OTHER)

        fun typeLabel(type: String): String = when (type) {
            TYPE_WECHAT -> "微信"
            TYPE_ALIPAY -> "支付宝"
            TYPE_BANK -> "银行卡"
            TYPE_CASH -> "现金"
            TYPE_CREDIT -> "信用支付"
            else -> "其他"
        }
    }
}

/**
 * 「收款方 / 关键词 → 大类」规则。
 *
 * 优先级：单笔手动指定 > 本规则（越长的关键词越优先）> 内置词典 > 未分类。
 */
data class MerchantRule(
    val id: Long = 0,
    val pattern: String,
    /** exact：收款方完全相等；contains：收款方包含该关键词 */
    val matchType: String = MATCH_CONTAINS,
    val categoryId: Long,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val MATCH_EXACT = "exact"
        const val MATCH_CONTAINS = "contains"
    }
}

/**
 * 某个大类在某个自然月的预算（period 形如 "2026-11"）。
 *
 * 两套数字要分清：
 *  - [amountCents] 是**这个月实际能花的总数**。月中创建时，它就是「从今天到月底」的总预算，
 *    而不是整月的（例如 9 号建 1000，这 1000 只覆盖剩下的 22 天）；
 *  - [dailyRateCents] 是**每天多少钱**，它是「下个月自动续上」的依据：
 *    下个月的预算 = 每天的钱 × 下个月天数（30 天 3000、31 天 3100）。
 */
data class MonthlyBudget(
    val id: Long = 0,
    val categoryId: Long,
    val period: String,
    /** 本月可用总额（分） */
    val amountCents: Long,
    /** fixed：固定日限额；dynamic：按剩余天数动态计算 */
    val dailyMode: String = MODE_DYNAMIC,
    /** 旧字段，已不再使用（固定模式的每日额度改成由 [dailyRateCents] 推导） */
    val dailyAmountCents: Long = 0,
    /** 每天多少钱（分）：下个月自动续预算就靠它 */
    val dailyRateCents: Long = 0,
    /** 这个月的预算从哪天开始覆盖（记账日的 dayOfMonth；整月预算是 1） */
    val anchorDay: Int = 1,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val MODE_FIXED = "fixed"
        const val MODE_DYNAMIC = "dynamic"
    }
}

/**
 * 手动调整「顺延结余」的记录（可正可负）。
 *
 * 用途：攒多了清空、欠多了重置，或者凭空加减一笔，避免预算越滚越离谱。
 */
data class BudgetAdjustment(
    val id: Long = 0,
    val categoryId: Long,
    val period: String,
    val deltaCents: Long,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
