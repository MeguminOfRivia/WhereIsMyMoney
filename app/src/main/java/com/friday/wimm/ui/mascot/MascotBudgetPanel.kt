package com.friday.wimm.ui.mascot

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.friday.wimm.data.budget.BudgetPlanner
import com.friday.wimm.data.database.FinanceDao
import com.friday.wimm.data.database.LedgerDao
import com.friday.wimm.data.database.LedgerDao.LedgerFilter
import com.friday.wimm.util.BudgetMath
import com.friday.wimm.util.Money
import java.time.LocalDate

/** 双击看今天，长按看本月 */
enum class BudgetPanelMode { TODAY, MONTH }

data class CategoryLine(val name: String, val cents: Long)

/**
 * Wally 弹出来的预算速览。
 *
 * 注意：Wally 跑在 `:mascot` 进程里，这里读的是同一个 SQLite 文件。
 * 只读不写，不会和主进程抢锁。
 */
data class BudgetPanelData(
    val period: String,
    val hasBudget: Boolean,
    /** 预算内已花（和 App 预算页同口径） */
    val todaySpentCents: Long,
    /** 今天所有支出（含没有预算的大类），只用于额外提示 */
    val todayAllSpentCents: Long,
    val todayLimitCents: Long,
    val monthSpentCents: Long,
    val monthAvailableCents: Long,
    val monthRemainingCents: Long,
    val carryInCents: Long,
    val adjustCents: Long,
    val unbudgetedCents: Long,
    val topToday: CategoryLine?,
    val topMonth: List<CategoryLine>
) {
    val todayRemainingCents: Long get() = todayLimitCents - todaySpentCents

    /** 今日预算外支出（不在任何分类预算里的钱） */
    val todayUnbudgetedCents: Long get() = (todayAllSpentCents - todaySpentCents).coerceAtLeast(0L)
    val todayProgress: Float
        get() = if (todayLimitCents <= 0L) {
            if (todaySpentCents > 0L) 1f else 0f
        } else {
            (todaySpentCents.toDouble() / todayLimitCents).coerceIn(0.0, 1.0).toFloat()
        }
    val monthProgress: Float
        get() = if (monthAvailableCents <= 0L) {
            if (monthSpentCents > 0L) 1f else 0f
        } else {
            (monthSpentCents.toDouble() / monthAvailableCents).coerceIn(0.0, 1.0).toFloat()
        }
}

/** 在 IO 线程调用 */
fun loadBudgetPanelData(financeDao: FinanceDao, ledgerDao: LedgerDao): BudgetPanelData {
    val today = LocalDate.now()
    val period = BudgetMath.periodOf(today)
    val snapshot = BudgetPlanner.snapshot(financeDao, period, today)

    val (todayFrom, todayTo) = LedgerDao.dayRange(today)
    val (monthFrom, monthTo) = LedgerDao.monthRange(period)
    val todayBreakdown = ledgerDao.categoryBreakdown(LedgerFilter(fromTs = todayFrom, toTs = todayTo))
    val monthBreakdown = ledgerDao.categoryBreakdown(LedgerFilter(fromTs = monthFrom, toTs = monthTo))

    return BudgetPanelData(
        period = period,
        hasBudget = snapshot.hasBudget,
        todaySpentCents = snapshot.totalTodaySpentCents,
        todayAllSpentCents = todayBreakdown.sumOf { it.cents },
        todayLimitCents = snapshot.totalDailyLimitCents,
        monthSpentCents = snapshot.totalSpentCents,
        monthAvailableCents = snapshot.totalAvailableCents,
        monthRemainingCents = snapshot.totalRemainingCents,
        carryInCents = snapshot.totalCarryInCents,
        adjustCents = snapshot.totalAdjustCents,
        unbudgetedCents = snapshot.totalUnbudgetedCents,
        topToday = todayBreakdown.firstOrNull()?.let { CategoryLine(it.name, it.cents) },
        topMonth = monthBreakdown.take(3).map { CategoryLine(it.name, it.cents) }
    )
}

private val PanelText = Color(0xFF333333)
private val PanelSub = Color(0xFF888888)
private val PanelError = Color(0xFFE53935)
private val PanelOk = Color(0xFF43A047)

@Composable
fun BudgetPanelCard(
    data: BudgetPanelData,
    mode: BudgetPanelMode,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(16.dp))
            .background(Color(0xFFFDFDFD), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                when (mode) {
                    BudgetPanelMode.TODAY -> "今日预算"
                    BudgetPanelMode.MONTH -> "本月预算"
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = PanelText
            )
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEEEEEE))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("×", fontSize = 12.sp, color = Color(0xFF666666))
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (!data.hasBudget) {
            Text(
                "还没设预算。\n去「预算」页给几个大类定个额度，\n我就能提醒你还能花多少了。",
                fontSize = 11.sp,
                color = PanelSub,
                lineHeight = 15.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "今天已花 ¥${Money.formatCents(data.todaySpentCents)}",
                fontSize = 12.sp,
                color = PanelText
            )
            return@Column
        }

        when (mode) {
            BudgetPanelMode.TODAY -> TodaySection(data)
            BudgetPanelMode.MONTH -> MonthSection(data)
        }
    }
}

@Composable
private fun TodaySection(data: BudgetPanelData) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text("今天", fontSize = 12.sp, color = PanelSub)
        Text(
            if (data.todayRemainingCents >= 0) {
                "还能花 ¥${Money.formatCents(data.todayRemainingCents)}"
            } else {
                "超了 ¥${Money.formatCents(-data.todayRemainingCents)}"
            },
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = if (data.todayRemainingCents >= 0) PanelOk else PanelError
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
    LinearProgressIndicator(
        progress = { data.todayProgress },
        modifier = Modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(CircleShape),
        color = if (data.todayRemainingCents >= 0) PanelOk else PanelError,
        trackColor = Color(0xFFEDEDED)
    )
    Spacer(modifier = Modifier.height(3.dp))
    Text(
        "已花 ¥${Money.formatCents(data.todaySpentCents)} / 今日可用 ¥${Money.formatCents(data.todayLimitCents)}",
        fontSize = 10.sp,
        color = PanelSub
    )
    if (data.todayUnbudgetedCents > 0L) {
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            "另有 ¥${Money.formatCents(data.todayUnbudgetedCents)} 不在分类预算里",
            fontSize = 10.sp,
            color = PanelSub,
            maxLines = 1
        )
    }
    data.topToday?.let {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "今天花最多：${it.name} ¥${Money.formatCents(it.cents)}",
            fontSize = 10.sp,
            color = PanelSub,
            maxLines = 1
        )
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text("长按我，看本月", fontSize = 9.sp, color = Color(0xFFBBBBBB))
}

@Composable
private fun MonthSection(data: BudgetPanelData) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text("本月", fontSize = 12.sp, color = PanelSub)
        Text(
            if (data.monthRemainingCents >= 0) {
                "还剩 ¥${Money.formatCents(data.monthRemainingCents)}"
            } else {
                "超支 ¥${Money.formatCents(-data.monthRemainingCents)}"
            },
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = if (data.monthRemainingCents >= 0) PanelOk else PanelError
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
    LinearProgressIndicator(
        progress = { data.monthProgress },
        modifier = Modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(CircleShape),
        color = if (data.monthRemainingCents >= 0) PanelOk else PanelError,
        trackColor = Color(0xFFEDEDED)
    )
    Spacer(modifier = Modifier.height(3.dp))
    Text(
        "已花 ¥${Money.formatCents(data.monthSpentCents)} / 可用 ¥${Money.formatCents(data.monthAvailableCents)}",
        fontSize = 10.sp,
        color = PanelSub
    )

    if (data.carryInCents != 0L || data.adjustCents != 0L) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            "含结转 ¥${Money.formatCents(data.carryInCents)}，手动调整 ¥${Money.formatCents(data.adjustCents)}",
            fontSize = 9.sp,
            color = PanelSub,
            maxLines = 1
        )
    }
    if (data.unbudgetedCents > 0L) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            "预算外支出 ¥${Money.formatCents(data.unbudgetedCents)}",
            fontSize = 9.sp,
            color = PanelSub,
            maxLines = 1
        )
    }

    if (data.topMonth.isNotEmpty()) {
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 6.dp),
            color = Color(0xFFEEEEEE)
        )
        data.topMonth.forEach { line ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(line.name, fontSize = 10.sp, color = PanelSub, maxLines = 1)
                Text(
                    "¥${Money.formatCents(line.cents)}",
                    fontSize = 10.sp,
                    color = PanelText
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text("双击我，看今天", fontSize = 9.sp, color = Color(0xFFBBBBBB))
}
