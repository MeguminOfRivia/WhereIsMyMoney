package com.friday.wimm.util

/**
 * 商户名的「占位 / 具体」判断。
 *
 * 「通知优先、屏幕补充」的合并全靠它：只有占位名才允许被屏幕识别出的具体商户覆盖，
 * 用户手改过的名字永远不会被冲掉。
 */
object MerchantNames {

    /** 所有"占位"商户名，屏幕补充时只有这些可以被覆盖 */
    val GENERIC = listOf(
        "未知支出", "未知收入", "未知转账",
        "微信支付", "微信收款", "支付宝", "支付宝收款",
        "快捷支付", "快捷收款", "微信"
    )

    fun isGeneric(name: String?): Boolean {
        if (name.isNullOrBlank()) return true
        return GENERIC.any { name == it || name.contains(it) }
    }

    fun compatible(a: String?, b: String?): Boolean =
        a == b || isGeneric(a) || isGeneric(b)

    /** 两个候选商户名里挑更具体的那一个 */
    fun better(a: String?, b: String?): String {
        val left = a.orEmpty()
        val right = b.orEmpty()
        return when {
            left.isBlank() -> right
            right.isBlank() -> left
            isGeneric(left) && !isGeneric(right) -> right
            else -> left
        }
    }
}
