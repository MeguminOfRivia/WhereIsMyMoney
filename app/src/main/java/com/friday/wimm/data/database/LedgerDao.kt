package com.friday.wimm.data.database

import android.content.ContentValues
import com.friday.wimm.data.model.CategoryTotal
import com.friday.wimm.data.model.LedgerEntry
import com.friday.wimm.data.model.LedgerSums
import com.friday.wimm.data.model.MerchantTotalRow
import com.friday.wimm.data.model.TrendPoint
import com.friday.wimm.util.BudgetMath
import com.friday.wimm.util.Money
import java.time.LocalDate

/**
 * 流水的查询 / 修改。
 *
 * 筛选（账户、大类、收款方、时间、收支）统一走 [LedgerFilter]，
 * 这样首页、统计页、预算页、CSV 导出用的是同一套口径，
 * 不会出现「首页看 1000，统计页看 980」这种对不上的情况。
 */
class LedgerDao(private val helper: DatabaseHelper) {

    // ── 查询 ────────────────────────────────────────────────

    /** 筛选条件。时间区间是左闭右开 [fromTs, toTs)，避免月末那一毫秒的重复计算 */
    data class LedgerFilter(
        val fromTs: Long = 0L,
        val toTs: Long = Long.MAX_VALUE,
        val accountId: Long? = null,
        val categoryId: Long? = null,
        val merchantKeyword: String? = null,
        /** "expense" / "income" / null=全部 */
        val type: String? = null
    )

    private fun buildWhere(filter: LedgerFilter): Pair<String, Array<String>> {
        val clauses = mutableListOf("t.timestamp >= ?", "t.timestamp < ?")
        val args = mutableListOf(filter.fromTs.toString(), filter.toTs.toString())

        filter.accountId?.let {
            clauses.add("t.accountId = ?")
            args.add(it.toString())
        }
        filter.categoryId?.let {
            // 允许筛「未分类」：categoryId 传 -1 时匹配 NULL
            if (it == UNCATEGORIZED_FILTER) {
                clauses.add("t.categoryId IS NULL")
            } else {
                clauses.add("t.categoryId = ?")
                args.add(it.toString())
            }
        }
        filter.type?.let {
            clauses.add("t.type = ?")
            args.add(it)
        }
        filter.merchantKeyword?.takeIf { it.isNotBlank() }?.let { keyword ->
            clauses.add("t.merchant LIKE ? ESCAPE '\\'")
            args.add("%" + escapeLike(keyword.trim()) + "%")
        }
        return clauses.joinToString(" AND ") to args.toTypedArray()
    }

    private fun escapeLike(raw: String): String =
        raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    fun query(filter: LedgerFilter, limit: Int = 1000, offset: Int = 0): List<LedgerEntry> {
        val (where, args) = buildWhere(filter)
        val sql = """
            SELECT t.*, c.name AS categoryName, c.color AS categoryColor, a.name AS accountName
            FROM transactions t
            LEFT JOIN categories c ON c.id = t.categoryId
            LEFT JOIN accounts a ON a.id = t.accountId
            WHERE $where
            ORDER BY t.timestamp DESC, t.id DESC
            LIMIT ? OFFSET ?
        """.trimIndent()
        val entries = mutableListOf<LedgerEntry>()
        helper.readableDatabase.rawQuery(sql, args + arrayOf(limit.toString(), offset.toString())).use { cursor ->
            while (cursor.moveToNext()) entries.add(cursor.toLedgerEntry())
        }
        return entries
    }

    fun sums(filter: LedgerFilter): LedgerSums {
        val (where, args) = buildWhere(filter)
        val sql = """
            SELECT
              COALESCE(SUM(CASE WHEN t.type = 'expense' THEN ROUND(t.amount * 100) ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN t.type = 'income'  THEN ROUND(t.amount * 100) ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN t.type = 'expense' THEN 1 ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN t.type = 'income'  THEN 1 ELSE 0 END), 0)
            FROM transactions t WHERE $where
        """.trimIndent()
        helper.readableDatabase.rawQuery(sql, args).use { cursor ->
            if (cursor.moveToFirst()) {
                return LedgerSums(cursor.getLong(0), cursor.getLong(1), cursor.getInt(2), cursor.getInt(3))
            }
        }
        return LedgerSums(0, 0, 0, 0)
    }

    /** 支出按大类汇总，金额从大到小 */
    fun categoryBreakdown(filter: LedgerFilter): List<CategoryTotal> {
        val (where, args) = buildWhere(filter.copy(type = "expense"))
        val sql = """
            SELECT t.categoryId AS categoryId,
                   COALESCE(c.name, '未分类') AS name,
                   COALESCE(c.color, '#9E9E9E') AS color,
                   COALESCE(SUM(ROUND(t.amount * 100)), 0) AS cents,
                   COUNT(*) AS cnt
            FROM transactions t
            LEFT JOIN categories c ON c.id = t.categoryId
            WHERE $where
            GROUP BY t.categoryId
            ORDER BY cents DESC
        """.trimIndent()
        val list = mutableListOf<CategoryTotal>()
        helper.readableDatabase.rawQuery(sql, args).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    CategoryTotal(
                        categoryId = if (cursor.isNull(0)) UNCATEGORIZED_FILTER else cursor.getLong(0),
                        name = cursor.getString(1) ?: "未分类",
                        color = cursor.getString(2) ?: "#9E9E9E",
                        cents = cursor.getLong(3),
                        count = cursor.getInt(4)
                    )
                )
            }
        }
        return list
    }

    /** 收入按大类汇总 */
    fun incomeBreakdown(filter: LedgerFilter): List<CategoryTotal> {
        val (where, args) = buildWhere(filter.copy(type = "income"))
        val sql = """
            SELECT t.categoryId, COALESCE(c.name, '其他收入'), COALESCE(c.color, '#4CAF50'),
                   COALESCE(SUM(ROUND(t.amount * 100)), 0), COUNT(*)
            FROM transactions t
            LEFT JOIN categories c ON c.id = t.categoryId
            WHERE $where
            GROUP BY t.categoryId
            ORDER BY 4 DESC
        """.trimIndent()
        val list = mutableListOf<CategoryTotal>()
        helper.readableDatabase.rawQuery(sql, args).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    CategoryTotal(
                        categoryId = if (cursor.isNull(0)) UNCATEGORIZED_FILTER else cursor.getLong(0),
                        name = cursor.getString(1) ?: "其他收入",
                        color = cursor.getString(2) ?: "#4CAF50",
                        cents = cursor.getLong(3),
                        count = cursor.getInt(4)
                    )
                )
            }
        }
        return list
    }

    /** 收款方排行（只统计支出） */
    fun merchantBreakdown(filter: LedgerFilter, limit: Int = 30): List<MerchantTotalRow> {
        val (where, args) = buildWhere(filter.copy(type = "expense"))
        val sql = """
            SELECT t.merchant, COALESCE(SUM(ROUND(t.amount * 100)), 0) AS cents, COUNT(*), MAX(t.timestamp)
            FROM transactions t
            WHERE $where
            GROUP BY t.merchant
            ORDER BY cents DESC
            LIMIT ?
        """.trimIndent()
        val list = mutableListOf<MerchantTotalRow>()
        helper.readableDatabase.rawQuery(sql, args + limit.toString()).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    MerchantTotalRow(
                        merchant = cursor.getString(0) ?: "未知",
                        cents = cursor.getLong(1),
                        count = cursor.getInt(2),
                        lastTimestamp = cursor.getLong(3)
                    )
                )
            }
        }
        return list
    }

    /**
     * 按「记账日」汇总的支出趋势。
     *
     * 记账日以凌晨 4 点为界，所以 SQL 里先把时间戳减 4 小时再取日期：
     * 03:30 的花费会落到前一天，和 BudgetMath.accountingDay 的口径一致。
     */
    fun dailyTrend(filter: LedgerFilter): List<TrendPoint> {
        val (where, args) = buildWhere(filter)
        val sql = """
            SELECT strftime('%Y-%m-%d', (t.timestamp / 1000) - ${BudgetMath.DAY_START_HOUR * 3600}, 'unixepoch', 'localtime') AS d,
                   COALESCE(SUM(CASE WHEN t.type = 'expense' THEN ROUND(t.amount * 100) ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN t.type = 'income'  THEN ROUND(t.amount * 100) ELSE 0 END), 0)
            FROM transactions t
            WHERE $where
            GROUP BY d
            ORDER BY d ASC
        """.trimIndent()
        return readTrend(sql, args)
    }

    fun monthlyTrend(filter: LedgerFilter): List<TrendPoint> {
        val (where, args) = buildWhere(filter)
        val sql = """
            SELECT strftime('%Y-%m', (t.timestamp / 1000) - ${BudgetMath.DAY_START_HOUR * 3600}, 'unixepoch', 'localtime') AS d,
                   COALESCE(SUM(CASE WHEN t.type = 'expense' THEN ROUND(t.amount * 100) ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN t.type = 'income'  THEN ROUND(t.amount * 100) ELSE 0 END), 0)
            FROM transactions t
            WHERE $where
            GROUP BY d
            ORDER BY d ASC
        """.trimIndent()
        return readTrend(sql, args)
    }

    private fun readTrend(sql: String, args: Array<String>): List<TrendPoint> {
        val list = mutableListOf<TrendPoint>()
        helper.readableDatabase.rawQuery(sql, args).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(TrendPoint(cursor.getString(0) ?: "", cursor.getLong(1), cursor.getLong(2)))
            }
        }
        return list
    }

    /** 出现过哪些收款方（给筛选和规则管理用） */
    fun distinctMerchants(keyword: String = "", limit: Int = 200): List<String> {
        val sql = buildString {
            append("SELECT merchant, COUNT(*) c FROM transactions ")
            if (keyword.isNotBlank()) append("WHERE merchant LIKE ? ESCAPE '\\' ")
            append("GROUP BY merchant ORDER BY c DESC LIMIT ?")
        }
        val args = if (keyword.isNotBlank()) {
            arrayOf("%" + escapeLike(keyword.trim()) + "%", limit.toString())
        } else {
            arrayOf(limit.toString())
        }
        val list = mutableListOf<String>()
        helper.readableDatabase.rawQuery(sql, args).use { cursor ->
            while (cursor.moveToNext()) cursor.getString(0)?.let { list.add(it) }
        }
        return list
    }

    fun earliestTimestamp(): Long {
        helper.readableDatabase.rawQuery("SELECT MIN(timestamp) FROM transactions", null).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getLong(0)
        }
        return 0L
    }

    /** 某个账户在某天的支出（分）——首页「今天花了多少」用 */
    fun expenseCentsBetween(fromTs: Long, toTs: Long, accountId: Long? = null): Long =
        sums(LedgerFilter(fromTs = fromTs, toTs = toTs, accountId = accountId, type = "expense")).expenseCents

    // ── 修改 ────────────────────────────────────────────────

    /**
     * 手动记一笔（首页右下角「+」用）。
     *
     * 和导入/通知的区别：不做去重、不分期，永远是一条新记录；
     * 时间取调用方给的当前时间，`source` 记成 manual，方便以后区分来源。
     */
    fun insertManual(
        amountCents: Long,
        merchant: String,
        note: String,
        accountId: Long,
        categoryId: Long?,
        type: String,
        timestamp: Long
    ): Long = helper.insertTransaction(
        com.friday.wimm.data.model.Transaction(
            amount = amountCents / 100.0,
            merchant = merchant,
            categoryId = categoryId,
            source = "manual",
            timestamp = timestamp,
            note = note,
            type = type,
            dataSource = "manual",
            accountId = accountId
        )
    )

    fun updateEntry(
        id: Long,
        amountCents: Long? = null,
        merchant: String? = null,
        note: String? = null,
        accountId: Long? = null,
        type: String? = null
    ) {
        val values = ContentValues()
        amountCents?.let { values.put("amount", Money.fromCents(it)) }
        merchant?.let { values.put("merchant", it) }
        note?.let { values.put("note", it) }
        accountId?.let { values.put("accountId", it) }
        type?.let { values.put("type", it) }
        if (values.size() == 0) return
        helper.writableDatabase.update("transactions", values, "id = ?", arrayOf(id.toString()))
    }

    /** 单独设置大类；categoryId 为 null 表示恢复「未分类」 */
    fun updateCategory(id: Long, categoryId: Long?) {
        val values = ContentValues().apply {
            if (categoryId == null) putNull("categoryId") else put("categoryId", categoryId)
        }
        helper.writableDatabase.update("transactions", values, "id = ?", arrayOf(id.toString()))
    }

    fun deleteEntry(id: Long) {
        helper.writableDatabase.delete("transactions", "id = ?", arrayOf(id.toString()))
    }

    // ── 导入合并 ────────────────────────────────────────────

    /**
     * 导入账单时先找一找：这笔是不是之前已经被通知/屏幕自动记下来了？
     *
     * 判断条件：来源是 notification、方向相同、金额差不超过 1 分、时间在 ±[windowMs] 内。
     * 要求金额分毫不差是故意的——宁可多记一条让用户删，也不能把两笔真实消费合成一笔。
     */
    fun findMergeCandidate(
        amountCents: Long,
        type: String,
        timestamp: Long,
        windowMs: Long = 3 * 60_000L
    ): com.friday.wimm.data.model.Transaction? {
        val sql = """
            SELECT * FROM transactions
            WHERE dataSource = 'notification' AND type = ?
              AND timestamp BETWEEN ? AND ?
              AND ABS(ROUND(amount * 100) - ?) <= 1
            ORDER BY ABS(timestamp - ?) ASC
            LIMIT 1
        """.trimIndent()
        helper.readableDatabase.rawQuery(
            sql,
            arrayOf(
                type,
                (timestamp - windowMs).toString(),
                (timestamp + windowMs).toString(),
                amountCents.toString(),
                timestamp.toString()
            )
        ).use { cursor ->
            if (cursor.moveToFirst()) return cursor.toTransaction()
        }
        return null
    }

    /** 把账单里的交易单号补到自动记下的那笔上；单号已被别的记录占用时返回 false */
    fun attachTransactionNo(id: Long, transactionNo: String?): Boolean {
        if (transactionNo.isNullOrBlank()) return false
        return try {
            val values = ContentValues().apply { put("transactionNo", transactionNo) }
            helper.writableDatabase.update("transactions", values, "id = ?", arrayOf(id.toString())) > 0
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            false
        }
    }

    /** 只在原来没归属账户时才写，避免覆盖用户手动指定的账户 */
    fun applyAccountIfUnset(id: Long, accountId: Long) {
        if (accountId <= 0L) return
        helper.writableDatabase.execSQL(
            "UPDATE transactions SET accountId = ? WHERE id = ? AND (accountId IS NULL OR accountId = 0)",
            arrayOf(accountId, id)
        )
    }

    /** 只在原来没有大类时才写 */
    fun applyCategoryIfUnset(id: Long, categoryId: Long?) {
        if (categoryId == null) return
        helper.writableDatabase.execSQL(
            "UPDATE transactions SET categoryId = ? WHERE id = ? AND categoryId IS NULL",
            arrayOf(categoryId, id)
        )
    }

    /**
     * 「以后都改」：把同一收款方的历史流水全部改到新大类。
     * 只动 categoryId，不动金额和时间。
     */
    fun bulkSetCategoryForMerchant(merchant: String, categoryId: Long, onlyUncategorized: Boolean = false): Int {
        val values = ContentValues().apply { put("categoryId", categoryId) }
        val where = if (onlyUncategorized) "merchant = ? AND categoryId IS NULL" else "merchant = ?"
        return helper.writableDatabase.update("transactions", values, where, arrayOf(merchant))
    }

    fun countByMerchant(merchant: String): Int {
        helper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM transactions WHERE merchant = ?", arrayOf(merchant)
        ).use { cursor ->
            if (cursor.moveToFirst()) return cursor.getInt(0)
        }
        return 0
    }

    // ── 行映射 ──────────────────────────────────────────────

    private fun android.database.Cursor.toTransaction(): com.friday.wimm.data.model.Transaction =
        com.friday.wimm.data.model.Transaction(
            id = getLong(getColumnIndexOrThrow("id")),
            amount = getDouble(getColumnIndexOrThrow("amount")),
            merchant = getString(getColumnIndexOrThrow("merchant")) ?: "",
            categoryId = getColumnIndexOrThrow("categoryId").let { if (isNull(it)) null else getLong(it) },
            source = getString(getColumnIndexOrThrow("source")) ?: "",
            timestamp = getLong(getColumnIndexOrThrow("timestamp")),
            note = getString(getColumnIndexOrThrow("note")) ?: "",
            type = getString(getColumnIndexOrThrow("type")) ?: "expense",
            transactionNo = getString(getColumnIndexOrThrow("transactionNo")),
            dataSource = getString(getColumnIndexOrThrow("dataSource")) ?: "file",
            fileId = getLong(getColumnIndexOrThrow("fileId")),
            accountId = getLong(getColumnIndexOrThrow("accountId"))
        )

    private fun android.database.Cursor.toLedgerEntry(): LedgerEntry {
        val tx = com.friday.wimm.data.model.Transaction(
            id = getLong(getColumnIndexOrThrow("id")),
            amount = getDouble(getColumnIndexOrThrow("amount")),
            merchant = getString(getColumnIndexOrThrow("merchant")) ?: "",
            categoryId = getColumnIndexOrThrow("categoryId").let { if (isNull(it)) null else getLong(it) },
            source = getString(getColumnIndexOrThrow("source")) ?: "",
            timestamp = getLong(getColumnIndexOrThrow("timestamp")),
            note = getString(getColumnIndexOrThrow("note")) ?: "",
            type = getString(getColumnIndexOrThrow("type")) ?: "expense",
            transactionNo = getString(getColumnIndexOrThrow("transactionNo")),
            dataSource = getString(getColumnIndexOrThrow("dataSource")) ?: "file",
            fileId = getLong(getColumnIndexOrThrow("fileId")),
            accountId = getLong(getColumnIndexOrThrow("accountId"))
        )
        return LedgerEntry(
            tx = tx,
            categoryName = getString(getColumnIndexOrThrow("categoryName")) ?: "未分类",
            categoryColor = getString(getColumnIndexOrThrow("categoryColor")) ?: "#9E9E9E",
            accountName = getString(getColumnIndexOrThrow("accountName")) ?: "未归属"
        )
    }

    companion object {
        /** 筛选「未分类」时用这个假 id，避免和真实大类 id 撞车 */
        const val UNCATEGORIZED_FILTER = -1L

        /** 本月（按记账日 4 点分界）的起止时间戳 */
        fun monthRange(period: String): Pair<Long, Long> {
            val month = BudgetMath.parsePeriod(period)
            val start = BudgetMath.dayStartMillis(month.atDay(1))
            val end = BudgetMath.dayStartMillis(month.plusMonths(1).atDay(1))
            return start to end
        }

        fun dayRange(day: LocalDate): Pair<Long, Long> {
            val start = BudgetMath.dayStartMillis(day)
            val end = BudgetMath.dayStartMillis(day.plusDays(1))
            return start to end
        }
    }
}
