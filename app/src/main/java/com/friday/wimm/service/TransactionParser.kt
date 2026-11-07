package com.friday.wimm.service

import java.security.MessageDigest

/**
 * 纯 Kotlin 的支付消息解析器。
 *
 * 设计目标（与旧的 `PaymentNotificationListener` 内联逻辑相比）：
 *  1. **不依赖 Android**：可以直接在 JVM 单元测试里跑，不必装手机。
 *  2. **宁缺毋滥**：识别不出金额就丢弃，绝不写一条 0 元的垃圾记录。
 *  3. **不猜日期/版本号**：旧实现用 `(\d+\.\d{1,2})` 兜底，会把 "2026.11" 当成 2026.11 元、
 *     把 "9.8.5" 当成 9.80 元。现在金额必须带 ¥/￥/元 或明确的「金额」「实付」字样。
 *  4. **方向靠语序判断**：「张三向你转账」是收入，「你向张三转账」是支出。
 *  5. **指纹稳定**：同一笔交易无论被重复解析多少次，都会得到同一个 fingerprint，
 *     交给数据库唯一索引去重；两笔真实交易一定是不同的指纹。
 */
object TransactionParser {

    const val WECHAT_PACKAGE = "com.tencent.mm"
    const val ALIPAY_PACKAGE = "com.eg.android.AlipayGphone"

    const val GENERIC_EXPENSE = "未知支出"
    const val GENERIC_INCOME = "未知收入"

    /** 金额上限：1000 万元，超过基本可以断定是识别错误 */
    private const val MAX_YUAN = 10_000_000L

    enum class Kind(val value: String) {
        EXPENSE("expense"),
        INCOME("income");

        companion object {
            fun fromValue(value: String?): Kind = if (value == "income") INCOME else EXPENSE
        }
    }

    enum class Channel { NOTIFICATION, ACCESSIBILITY }

    /**
     * @param title    通知标题 / 页面首行
     * @param body     通知正文的各个候选字段（text / bigText / subText ...）
     * @param identity 稳定身份：通知用 `StatusBarNotification.key`；
     *                 无障碍用 `会话随机串#windowId#页面世代`
     */
    data class Message(
        val packageName: String,
        val title: String,
        val body: List<String>,
        val identity: String,
        val channel: Channel
    )

    data class Draft(
        val amountCents: Long,
        val merchant: String,
        val kind: Kind,
        val note: String,
        val source: String,
        val fingerprint: String,
        /** 金额尚未识别出来（0 元），需要用户补录 */
        val needsAmount: Boolean
    )

    sealed interface Outcome {
        data class Parsed(val draft: Draft) : Outcome
        data class Ignored(val reason: String) : Outcome
    }

    // ─────────────────────────────────────────────────────────────
    // 规则表
    // ─────────────────────────────────────────────────────────────

    /** 命中即判定「不是一笔已完成的交易」 */
    private val IGNORE_PHRASES = listOf(
        "支付失败", "付款失败", "转账失败", "收款失败", "退款失败",
        "已取消", "取消支付", "已撤销", "已撤回", "交易关闭",
        "待支付", "请支付", "请尽快", "请及时",
        "验证码", "已过期", "领取失败", "退款申请", "正在退款",
        "申请已提交", "已提交申请",
        "版本", "已更新", "优惠券", "积分", "红包封面",
        "账单", "还款", "逾期", "提现", "收益", "余额为",
        "拍了拍"
    )

    /** 退款按「收入」记录，并在备注里标记，方便用户识别 */
    private val REFUND_PHRASE = "退款"

    /** 只有这类强特征才允许「金额缺失、等用户补录」 */
    private val STRONG_WITHOUT_AMOUNT = listOf(
        "转账", "收款", "红包", "付款"
    )

    /** 金额：¥12.34 / ￥12.34 / 12.34元 / 金额12.34 */
    private val AMOUNT_SYMBOL = Regex("""[¥￥]\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
    private val AMOUNT_YUAN = Regex("""([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*元""")
    private val AMOUNT_KEYWORD = Regex("""(?:金额|实付|合计)[：:]?\s*[¥￥]?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")

    /** 语序优先：先判断「钱进我口袋」，再判断「钱从我口袋出去」 */
    private val INCOME_TO_ME = Regex("""(?:向你|给我|给你|转给你|向我)(?:转账|付款|付钱|发了一个红包)""")
    private val INCOME_WORDS = listOf(
        "到账", "入账", "收款成功", "已收钱", "已收款", "收款到账",
        "红包已存入", "已存入零钱", "收到一笔", "收到转账", "收到红包",
        "领取了", "已领取", "已接收", "收款通知"
    )

    private val EXPENSE_FROM_ME = Regex("""向.{1,20}?(?:付款|转账|付钱|发红包)""")
    private val EXPENSE_WORDS = listOf(
        "支付成功", "付款成功", "已支付", "已付款", "扫码付款",
        "发出了红包", "发了一个红包", "转账给", "付款给",
        "消费", "扣款", "扣费", "缴费", "充值",
        // 兜底：带金额的支付类通知，标题基本都含「支付/付款」
        "支付", "付款"
    )

    private val MERCHANT_PATTERNS = listOf(
        Regex("""(?:商户|收款方|商家|付款给|转账给)[：:]?\s*(.{1,20}?)(?:[，,。;；、\s]|$)"""),
        Regex("""向(.{1,20}?)(?:付款|转账|付钱)"""),
        Regex("""(.{1,20}?)(?:向你|给你)(?:转账|付款)"""),
        Regex("""来自(.{1,20}?)(?:[，,。;；、\s]|$)"""),
        Regex("""在(.{1,20}?)消费""")
    )

    private val GENERIC_MERCHANTS = listOf(
        "微信支付", "微信收款", "支付宝", "支付宝收款",
        "快捷支付", "快捷收款", "未知支出", "未知收入", "微信"
    )

    private val TITLE_AS_MERCHANT_HINT = listOf("转账", "红包", "收款")

    // ─────────────────────────────────────────────────────────────
    // 对外入口
    // ─────────────────────────────────────────────────────────────

    fun parse(message: Message): Outcome {
        val title = normalize(message.title)
        val body = message.body
            .map(::normalize)
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(" ")
        val full = listOf(title, body).filter { it.isNotEmpty() }.joinToString(" ")
        if (full.isEmpty()) return Outcome.Ignored("空消息")

        IGNORE_PHRASES.firstOrNull { full.contains(it) }?.let {
            return Outcome.Ignored("非交易消息($it)")
        }

        val amountInBody = findAmount(body)
        val amountInTitle = findAmount(title)
        if (amountInBody.invalid || amountInTitle.invalid) {
            return Outcome.Ignored("金额超出合理范围")
        }
        val amountCents = amountInBody.cents ?: amountInTitle.cents
        val kindResult = detectKind(full) ?: return Outcome.Ignored("没有交易特征")

        val needsAmount = amountCents == null
        if (needsAmount && STRONG_WITHOUT_AMOUNT.none { full.contains(it) }) {
            return Outcome.Ignored("没有可识别的金额")
        }

        val merchant = findMerchant(title, body, full, kindResult.kind, message.packageName)
        val cents = amountCents ?: 0L
        val fingerprint = fingerprintOf(
            channel = message.channel,
            packageName = message.packageName,
            identity = message.identity,
            amountCents = cents,
            kind = kindResult.kind,
            textHash = sha1(full).take(12)
        )

        return Outcome.Parsed(
            Draft(
                amountCents = cents,
                merchant = merchant,
                kind = kindResult.kind,
                note = kindResult.note,
                source = sourceOf(message.packageName),
                fingerprint = fingerprint,
                needsAmount = needsAmount
            )
        )
    }

    /**
     * 生成去重指纹。
     *
     * 关键点：**两笔真实交易一定是不同指纹**。
     *  - 通知通道用 `StatusBarNotification.key`（每笔支付都是独立通知）+ 金额 + 方向；
     *  - 无障碍通道用「会话随机串#windowId#页面世代」+ 金额 + 方向；
     *    页面内容刷新不会改变世代号，所以不会重复；重新进入支付页世代号 +1，所以两笔都能记上。
     */
    fun fingerprintOf(
        channel: Channel,
        packageName: String,
        identity: String,
        amountCents: Long,
        kind: Kind,
        textHash: String
    ): String = sha1(
        listOf(channel.name, packageName, identity, amountCents.toString(), kind.value, textHash)
            .joinToString("|")
    )

    fun sourceOf(packageName: String): String = when (packageName) {
        WECHAT_PACKAGE -> "wechat"
        ALIPAY_PACKAGE -> "alipay"
        else -> "other"
    }

    fun isSupportedPackage(packageName: String?): Boolean =
        packageName == WECHAT_PACKAGE || packageName == ALIPAY_PACKAGE

    /** 供转账事件分类器复用：从一段文字里取金额（分），取不到返回 null */
    fun parseAmountCents(text: String): Long? = findAmount(text).cents

    /** 供转账事件分类器复用：清洗一个可能的收款人名 */
    fun merchantCandidate(raw: String): String? = cleanMerchant(raw)

    // ─────────────────────────────────────────────────────────────
    // 内部实现
    // ─────────────────────────────────────────────────────────────

    private val COUNT_PREFIX = Regex("""[\[【]?\s*\d+\s*条\s*[\]】]?""")
    private val WHITESPACE = Regex("""\s+""")

    private fun normalize(raw: String): String =
        raw.replace(COUNT_PREFIX, " ").replace(WHITESPACE, " ").trim()

    private data class AmountMatch(val cents: Long?, val invalid: Boolean)

    private fun findAmount(text: String): AmountMatch {
        if (text.isEmpty()) return AmountMatch(null, false)
        for (pattern in listOf(AMOUNT_SYMBOL, AMOUNT_YUAN, AMOUNT_KEYWORD)) {
            val match = pattern.find(text) ?: continue
            val cents = toCents(match.groupValues[1])
                ?: return AmountMatch(null, true) // 有明显金额形态但越界，判定为不可信
            if (cents > 0) return AmountMatch(cents, false)
        }
        return AmountMatch(null, false)
    }

    /** "1,234.5" → 123450 分；"12" → 1200 分；非法/越界返回 null */
    internal fun toCents(raw: String): Long? {
        val cleaned = raw.replace(",", "").trim()
        if (cleaned.isEmpty()) return null
        val parts = cleaned.split(".")
        val yuan = parts[0].toLongOrNull() ?: return null
        if (yuan < 0 || yuan > MAX_YUAN) return null
        val fraction = when (parts.size) {
            1 -> 0L
            2 -> parts[1].padEnd(2, '0').take(2).toLongOrNull() ?: return null
            else -> return null
        }
        return yuan * 100 + fraction
    }

    private data class KindResult(val kind: Kind, val note: String = "")

    private fun detectKind(full: String): KindResult? {
        if (full.contains(REFUND_PHRASE)) return KindResult(Kind.INCOME, "退款")
        if (INCOME_TO_ME.containsMatchIn(full)) return KindResult(Kind.INCOME)
        if (INCOME_WORDS.any { full.contains(it) }) return KindResult(Kind.INCOME)
        if (EXPENSE_FROM_ME.containsMatchIn(full)) return KindResult(Kind.EXPENSE)
        if (EXPENSE_WORDS.any { full.contains(it) }) return KindResult(Kind.EXPENSE)
        return null
    }

    private fun findMerchant(
        title: String,
        body: String,
        full: String,
        kind: Kind,
        packageName: String
    ): String {
        for (pattern in MERCHANT_PATTERNS) {
            val match = pattern.find(body) ?: pattern.find(title)
            val candidate = match?.groupValues?.getOrNull(1)?.let(::cleanMerchant)
            if (candidate != null) return candidate
        }
        // 微信/支付宝的转账、红包通知：标题通常就是对方昵称
        if (TITLE_AS_MERCHANT_HINT.any { full.contains(it) }) {
            val candidate = cleanMerchant(title)
            if (candidate != null) return candidate
        }
        // 实在认不出商户时，用来源名兜底，避免所有未知支出挤成一个分类
        return when {
            packageName == WECHAT_PACKAGE -> "微信支付"
            packageName == ALIPAY_PACKAGE -> "支付宝"
            kind == Kind.INCOME -> GENERIC_INCOME
            else -> GENERIC_EXPENSE
        }
    }

    private val PURE_NUMBER = Regex("""^[\d\s.,:：¥￥+\-]+$""")

    private fun cleanMerchant(raw: String): String? {
        val value = raw.trim().trim('：', ':', '，', ',', '。', '.', ' ', '、')
        if (value.isEmpty() || value.length > 20) return null
        // 纯数字/日期/金额（如 "2026.11.08"、"3268.50"）不是商户
        if (PURE_NUMBER.matches(value)) return null
        if (value.contains("¥") || value.contains("￥")) return null
        if (GENERIC_MERCHANTS.any { value == it || value.contains(it) }) return null
        return value
    }

    private fun sha1(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(raw.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (byte in digest) {
            val value = byte.toInt() and 0xFF
            if (value < 0x10) sb.append('0')
            sb.append(Integer.toHexString(value))
        }
        return sb.toString()
    }
}
