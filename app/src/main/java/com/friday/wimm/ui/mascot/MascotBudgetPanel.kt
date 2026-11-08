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

/**
 * 长按 / 双击 Wally 弹出来的「今天 + 这个月」预算速览。
 *
 * 注意：Wally 跑在 `:mascot` 进程里，这里读的是同一个 SQLite 文件。
 * 只读不写，不会和主进程抢锁。
 */
data class BudgetPanelData(
    val period: String,
    val hasBudget: Boolean,
    val todaySpentCents: Long,
    val todayLimitCents: Long,
    val monthSpentCents: Long,
    val monthAvailableCents: Long,
    val monthRemainingCents: Long,
    val topCategory: String?,
    val topCategoryCents: Long
) {
    val todayRemainingCents: Long get() = todayLimitCents - todaySpentCents
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
    val (from, to) = LedgerDao.dayRange(today)
    val breakdown = ledgerDao.categoryBreakdown(LedgerFilter(fromTs = from, toTs = to))
    val top = breakdown.firstOrNull()
    return BudgetPanelData(
        period = period,
        hasBudget = snapshot.hasBudget,
        todaySpentCents = breakdown.sumOf { it.cents },
        todayLimitCents = snapshot.totalDailyLimitCents,
        monthSpentCents = snapshot.totalSpentCents,
        monthAvailableCents = snapshot.totalAvailableCents,
        monthRemainingCents = snapshot.totalRemainingCents,
        topCategory = top?.name,
        topCategoryCents = top?.cents ?: 0L
    )
}

private val PanelText = Color(0xFF333333)
private val PanelSub = Color(0xFF888888)
private val PanelError = Color(0xFFE53935)
private val PanelOk = Color(0xFF43A047)

@Composable
fun BudgetPanelCard(data: BudgetPanelData, onClose: () -> Unit) {
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
            Text("预算速览", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PanelText)
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
                "还没设预算。\n去「预算」页给几个大类定个额度，\n我就能提醒你今天还能花多少了。",
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

        // 今日
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
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (data.todayRemainingCents >= 0) PanelOk else PanelError
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { data.todayProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            color = if (data.todayRemainingCents >= 0) PanelOk else PanelError,
            trackColor = Color(0xFFEDEDED)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            "已花 ¥${Money.formatCents(data.todaySpentCents)} / 额度 ¥${Money.formatCents(data.todayLimitCents)}",
            fontSize = 10.sp,
            color = PanelSub
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 6.dp),
            color = Color(0xFFEEEEEE)
        )

        // 本月
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
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (data.monthRemainingCents >= 0) PanelOk else PanelError
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { data.monthProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            color = if (data.monthRemainingCents >= 0) PanelOk else PanelError,
            trackColor = Color(0xFFEDEDED)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            "已花 ¥${Money.formatCents(data.monthSpentCents)} / 可用 ¥${Money.formatCents(data.monthAvailableCents)}",
            fontSize = 10.sp,
            color = PanelSub
        )

        if (data.topCategory != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "今天花最多：${data.topCategory} ¥${Money.formatCents(data.topCategoryCents)}",
                fontSize = 10.sp,
                color = PanelSub,
                maxLines = 1
            )
        }
    }
}
