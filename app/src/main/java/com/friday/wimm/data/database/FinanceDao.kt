package com.friday.wimm.data.database

import android.content.ContentValues
import com.friday.wimm.data.model.Account
import com.friday.wimm.data.model.BudgetAdjustment
import com.friday.wimm.data.model.Category
import com.friday.wimm.data.model.MerchantRule
import com.friday.wimm.data.model.MonthlyBudget

/**
 * 账户 / 大类 / 收款方规则的读写。
 *
 * 单独成类，避免 [DatabaseHelper] 继续膨胀。
 */
class FinanceDao(private val helper: DatabaseHelper) {

    // ── 账户 ────────────────────────────────────────────────

    fun getAllAccounts(includeArchived: Boolean = false): List<Account> {
        val selection = if (includeArchived) null else "archived = 0"
        val cursor = helper.readableDatabase.query(
            "accounts", null, selection, null, null, null, "sortOrder ASC, id ASC"
        )
        val list = mutableListOf<Account>()
        while (cursor.moveToNext()) list.add(cursor.toAccount())
        cursor.close()
        return list
    }

    fun getAccount(id: Long): Account? {
        val cursor = helper.readableDatabase.query(
            "accounts", null, "id = ?", arrayOf(id.toString()), null, null, null
        )
        var account: Account? = null
        if (cursor.moveToFirst()) account = cursor.toAccount()
        cursor.close()
        return account
    }

    fun insertAccount(account: Account): Long {
        val values = ContentValues().apply {
            put("name", account.name)
            put("type", account.type)
            put("initialBalanceCents", account.initialBalanceCents)
            put("balanceStartAt", account.balanceStartAt)
            put("archived", if (account.archived) 1 else 0)
            put("createdAt", account.createdAt)
            put("sortOrder", account.sortOrder)
        }
        return helper.writableDatabase.insert("accounts", null, values)
    }

    fun updateAccount(account: Account) {
        val values = ContentValues().apply {
            put("name", account.name)
            put("type", account.type)
            put("initialBalanceCents", account.initialBalanceCents)
            put("balanceStartAt", account.balanceStartAt)
            put("archived", if (account.archived) 1 else 0)
            put("sortOrder", account.sortOrder)
        }
        helper.writableDatabase.update("accounts", values, "id = ?", arrayOf(account.id.toString()))
    }

    fun setAccountArchived(id: Long, archived: Boolean) {
        val values = ContentValues().apply { put("archived", if (archived) 1 else 0) }
        helper.writableDatabase.update("accounts", values, "id = ?", arrayOf(id.toString()))
    }

    /** 彻底删除账户及其名下流水，只应在「已归档」后由用户二次确认时调用 */
    fun deleteAccountPermanently(id: Long) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.delete("transactions", "accountId = ?", arrayOf(id.toString()))
            db.delete("accounts", "id = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * 账户余额（分）= 初始余额 + 起始时间之后的收入 − 支出。
     *
     * 起始时间之前的流水只进统计、不改余额——导入历史账单时不会把当前余额算乱。
     */
    fun getAccountBalanceCents(accountId: Long): Long {
        val account = getAccount(accountId) ?: return 0
        val cursor = helper.readableDatabase.rawQuery(
            "SELECT " +
                "COALESCE(SUM(CASE WHEN type = 'income' THEN ROUND(amount * 100) ELSE 0 END), 0), " +
                "COALESCE(SUM(CASE WHEN type = 'expense' THEN ROUND(amount * 100) ELSE 0 END), 0) " +
                "FROM transactions WHERE accountId = ? AND timestamp >= ?",
            arrayOf(accountId.toString(), account.balanceStartAt.toString())
        )
        var income = 0L
        var expense = 0L
        if (cursor.moveToFirst()) {
            income = cursor.getLong(0)
            expense = cursor.getLong(1)
        }
        cursor.close()
        return account.initialBalanceCents + income - expense
    }

    fun getTotalBalanceCents(): Long =
        getAllAccounts().sumOf { getAccountBalanceCents(it.id) }

    /**
     * 按流水来源找默认账户：微信 → 微信类账户，支付宝 → 支付宝类账户。
     * 页面里明确读到银行卡时，调用方应优先用读到的那张卡。
     */
    fun getDefaultAccountIdForSource(source: String): Long {
        val type = when (source) {
            "wechat" -> Account.TYPE_WECHAT
            "alipay" -> Account.TYPE_ALIPAY
            else -> return 0L
        }
        val cursor = helper.readableDatabase.query(
            "accounts", arrayOf("id"), "type = ? AND archived = 0",
            arrayOf(type), null, null, "sortOrder ASC, id ASC"
        )
        var id = 0L
        if (cursor.moveToFirst()) id = cursor.getLong(0)
        cursor.close()
        return id
    }

    /** 兜底大类：支出用「未分类」，收入用「其他收入」 */
    fun getFallbackCategoryId(kind: String): Long? {
        val name = if (kind == Category.KIND_INCOME) "其他收入" else "未分类"
        return getCategoryByName(name, kind)?.id
    }

    // ── 大类 ────────────────────────────────────────────────

    fun getCategories(kind: String? = null, includeArchived: Boolean = false): List<Category> {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (kind != null) {
            conditions.add("kind = ?")
            args.add(kind)
        }
        if (!includeArchived) conditions.add("archived = 0")
        val selection = if (conditions.isEmpty()) null else conditions.joinToString(" AND ")

        val cursor = helper.readableDatabase.query(
            "categories", null, selection, args.toTypedArray(), null, null, "sortOrder ASC, id ASC"
        )
        val list = mutableListOf<Category>()
        while (cursor.moveToNext()) list.add(cursor.toCategory())
        cursor.close()
        return list
    }

    fun insertCategory(category: Category): Long {
        val values = ContentValues().apply {
            put("name", category.name)
            put("color", category.color)
            put("kind", category.kind)
            put("sortOrder", category.sortOrder)
            put("archived", if (category.archived) 1 else 0)
            put("isBuiltin", if (category.isBuiltin) 1 else 0)
        }
        return helper.writableDatabase.insert("categories", null, values)
    }

    fun updateCategory(category: Category) {
        val values = ContentValues().apply {
            put("name", category.name)
            put("color", category.color)
            put("kind", category.kind)
            put("sortOrder", category.sortOrder)
            put("archived", if (category.archived) 1 else 0)
        }
        helper.writableDatabase.update("categories", values, "id = ?", arrayOf(category.id.toString()))
    }

    /** 停用而不是删除：历史流水仍然指着它，删了会变孤儿 */
    fun setCategoryArchived(id: Long, archived: Boolean) {
        val values = ContentValues().apply { put("archived", if (archived) 1 else 0) }
        helper.writableDatabase.update("categories", values, "id = ?", arrayOf(id.toString()))
    }

    fun getCategory(id: Long): Category? {
        val cursor = helper.readableDatabase.query(
            "categories", null, "id = ?", arrayOf(id.toString()), null, null, null
        )
        var category: Category? = null
        if (cursor.moveToFirst()) category = cursor.toCategory()
        cursor.close()
        return category
    }

    fun getCategoryByName(name: String, kind: String): Category? {
        val cursor = helper.readableDatabase.query(
            "categories", null, "name = ? AND kind = ?", arrayOf(name, kind), null, null, null
        )
        var category: Category? = null
        if (cursor.moveToFirst()) category = cursor.toCategory()
        cursor.close()
        return category
    }

    // ── 收款方规则 ──────────────────────────────────────────

    fun getMerchantRules(): List<MerchantRule> {
        val cursor = helper.readableDatabase.query(
            "merchant_rules", null, null, null, null, null, "createdAt DESC"
        )
        val list = mutableListOf<MerchantRule>()
        while (cursor.moveToNext()) {
            list.add(
                MerchantRule(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    pattern = cursor.getString(cursor.getColumnIndexOrThrow("pattern")),
                    matchType = cursor.getString(cursor.getColumnIndexOrThrow("matchType")),
                    categoryId = cursor.getLong(cursor.getColumnIndexOrThrow("categoryId")),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("createdAt"))
                )
            )
        }
        cursor.close()
        return list
    }

    /** 同一个 pattern + matchType 只保留一条 */
    fun upsertMerchantRule(rule: MerchantRule): Long {
        val db = helper.writableDatabase
        db.delete("merchant_rules", "pattern = ? AND matchType = ?", arrayOf(rule.pattern, rule.matchType))
        val values = ContentValues().apply {
            put("pattern", rule.pattern)
            put("matchType", rule.matchType)
            put("categoryId", rule.categoryId)
            put("createdAt", rule.createdAt)
        }
        return db.insert("merchant_rules", null, values)
    }

    fun deleteMerchantRule(id: Long) {
        helper.writableDatabase.delete("merchant_rules", "id = ?", arrayOf(id.toString()))
    }

    /**
     * 按「先精确、后包含（关键词越长越优先）」的顺序给收款方找大类。
     * 找不到返回 null，调用方再落到「未分类」。
     */
    fun resolveCategoryIdForMerchant(merchant: String): Long? {
        if (merchant.isBlank()) return null
        val db = helper.readableDatabase

        db.query(
            "merchant_rules", arrayOf("categoryId"),
            "matchType = ? AND pattern = ?", arrayOf(MerchantRule.MATCH_EXACT, merchant),
            null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }

        db.query(
            "merchant_rules", arrayOf("categoryId", "pattern"),
            "matchType = ?", arrayOf(MerchantRule.MATCH_CONTAINS),
            null, null, "LENGTH(pattern) DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                if (merchant.contains(cursor.getString(1))) return cursor.getLong(0)
            }
        }
        return null
    }

    // ── 预算 ────────────────────────────────────────────────

    fun upsertBudget(budget: MonthlyBudget): Long {
        val db = helper.writableDatabase
        val values = ContentValues().apply {
            put("categoryId", budget.categoryId)
            put("period", budget.period)
            put("amountCents", budget.amountCents)
            put("dailyMode", budget.dailyMode)
            put("dailyAmountCents", budget.dailyAmountCents)
            put("dailyRateCents", budget.dailyRateCents)
            put("anchorDay", budget.anchorDay)
            put("createdAt", budget.createdAt)
        }
        return db.insertWithOnConflict(
            "budgets", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getBudgets(period: String): List<MonthlyBudget> {
        val cursor = helper.readableDatabase.query(
            "budgets", null, "period = ?", arrayOf(period), null, null, "categoryId ASC"
        )
        val list = mutableListOf<MonthlyBudget>()
        while (cursor.moveToNext()) {
            list.add(
                MonthlyBudget(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    categoryId = cursor.getLong(cursor.getColumnIndexOrThrow("categoryId")),
                    period = cursor.getString(cursor.getColumnIndexOrThrow("period")),
                    amountCents = cursor.getLong(cursor.getColumnIndexOrThrow("amountCents")),
                    dailyMode = cursor.getString(cursor.getColumnIndexOrThrow("dailyMode"))
                        ?: MonthlyBudget.MODE_DYNAMIC,
                    dailyAmountCents = cursor.getLong(cursor.getColumnIndexOrThrow("dailyAmountCents")),
                    dailyRateCents = cursor.getLong(cursor.getColumnIndexOrThrow("dailyRateCents")),
                    anchorDay = cursor.getInt(cursor.getColumnIndexOrThrow("anchorDay")).let {
                        if (it <= 0) 1 else it
                    },
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("createdAt"))
                )
            )
        }
        cursor.close()
        return list
    }

    /**
     * 最近一个「有预算」的月份（比 [beforePeriod] 早）。
     *
     * 用来把上个月的预算按「每天多少钱」续到新的一个月。
     */
    fun latestBudgetPeriodBefore(beforePeriod: String): String? {
        helper.readableDatabase.rawQuery(
            "SELECT period FROM budgets WHERE period < ? ORDER BY period DESC LIMIT 1",
            arrayOf(beforePeriod)
        ).use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }

    /**
     * 如果 [period] 还没有任何预算，就把之前的预算按「每天金额 × 本月天数」续过来。
     *
     * 只往**比已有的最新月份更晚**的月份续，不会凭空给历史月份造预算。
     */
    fun ensureBudgetsForPeriod(period: String, daysInMonth: Int): Int {
        if (getBudgets(period).isNotEmpty()) return 0
        if (latestBudgetPeriodBefore(period) == null) return 0
        // 只在「比最新月份还新」的时候续，避免用户翻历史月份时凭空出现预算
        val newest = helper.readableDatabase.rawQuery(
            "SELECT period FROM budgets ORDER BY period DESC LIMIT 1", null
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        if (newest == null || period <= newest) return 0

        val source = getBudgets(latestBudgetPeriodBefore(period)!!)
        var inserted = 0
        source.forEach { budget ->
            val rate = if (budget.dailyRateCents > 0) {
                budget.dailyRateCents
            } else {
                // 老数据兜底：用原月份的天数反推
                val days = runCatching {
                    com.friday.wimm.util.BudgetMath.parsePeriod(budget.period).lengthOfMonth()
                }.getOrDefault(30)
                budget.amountCents / days.coerceAtLeast(1)
            }
            upsertBudget(
                MonthlyBudget(
                    categoryId = budget.categoryId,
                    period = period,
                    amountCents = rate * daysInMonth,
                    dailyMode = budget.dailyMode,
                    dailyRateCents = rate,
                    anchorDay = 1,
                    createdAt = System.currentTimeMillis()
                )
            )
            inserted++
        }
        return inserted
    }

    fun getBudget(categoryId: Long, period: String): MonthlyBudget? =
        getBudgets(period).firstOrNull { it.categoryId == categoryId }

    fun deleteBudget(categoryId: Long, period: String) {
        helper.writableDatabase.delete(
            "budgets", "categoryId = ? AND period = ?", arrayOf(categoryId.toString(), period)
        )
    }

    fun addBudgetAdjustment(adjustment: BudgetAdjustment): Long {
        val values = ContentValues().apply {
            put("categoryId", adjustment.categoryId)
            put("period", adjustment.period)
            put("deltaCents", adjustment.deltaCents)
            put("note", adjustment.note)
            put("createdAt", adjustment.createdAt)
        }
        return helper.writableDatabase.insert("budget_adjustments", null, values)
    }

    fun getAdjustments(period: String): List<BudgetAdjustment> {
        val cursor = helper.readableDatabase.query(
            "budget_adjustments", null, "period = ?", arrayOf(period), null, null, "createdAt ASC"
        )
        val list = mutableListOf<BudgetAdjustment>()
        while (cursor.moveToNext()) {
            list.add(
                BudgetAdjustment(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    categoryId = cursor.getLong(cursor.getColumnIndexOrThrow("categoryId")),
                    period = cursor.getString(cursor.getColumnIndexOrThrow("period")),
                    deltaCents = cursor.getLong(cursor.getColumnIndexOrThrow("deltaCents")),
                    note = cursor.getString(cursor.getColumnIndexOrThrow("note")) ?: "",
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("createdAt"))
                )
            )
        }
        cursor.close()
        return list
    }

    /**
     * 统计一段时间内的支出（只算 expense，不含收入/转账），单位分。
     * categoryId 为 null 时统计全部大类。
     */
    fun getSpentCents(fromTs: Long, toTs: Long, categoryId: Long? = null): Long {
        val categoryClause = if (categoryId != null) " AND categoryId = ?" else ""
        val args = buildList {
            add(fromTs.toString())
            add(toTs.toString())
            if (categoryId != null) add(categoryId.toString())
        }.toTypedArray()
        val cursor = helper.readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(ROUND(amount * 100)), 0) FROM transactions " +
                "WHERE type = 'expense' AND timestamp >= ? AND timestamp < ?$categoryClause",
            args
        )
        var cents = 0L
        if (cursor.moveToFirst()) cents = cursor.getLong(0)
        cursor.close()
        return cents
    }

    // ── 行映射 ──────────────────────────────────────────────

    private fun android.database.Cursor.toAccount(): Account = Account(
        id = getLong(getColumnIndexOrThrow("id")),
        name = getString(getColumnIndexOrThrow("name")),
        type = getString(getColumnIndexOrThrow("type")) ?: Account.TYPE_OTHER,
        initialBalanceCents = getLong(getColumnIndexOrThrow("initialBalanceCents")),
        balanceStartAt = getLong(getColumnIndexOrThrow("balanceStartAt")),
        archived = getInt(getColumnIndexOrThrow("archived")) == 1,
        createdAt = getLong(getColumnIndexOrThrow("createdAt")),
        sortOrder = getInt(getColumnIndexOrThrow("sortOrder"))
    )

    private fun android.database.Cursor.toCategory(): Category = Category(
        id = getLong(getColumnIndexOrThrow("id")),
        name = getString(getColumnIndexOrThrow("name")),
        color = getString(getColumnIndexOrThrow("color")) ?: "#9E9E9E",
        kind = getString(getColumnIndexOrThrow("kind")) ?: Category.KIND_EXPENSE,
        sortOrder = getInt(getColumnIndexOrThrow("sortOrder")),
        archived = getInt(getColumnIndexOrThrow("archived")) == 1,
        isBuiltin = getInt(getColumnIndexOrThrow("isBuiltin")) == 1
    )
}
