package com.friday.wimm.ui.mascot

import androidx.compose.runtime.*

enum class AnimationState {
    IDLE, SPINNING, BOUNCING, SLEEPING, EXCITED, DIZZY,
    WALK_SIDE, YAWN, STRETCH, LOOK_PHONE, SNEEZE,
    ACCOUNTING, LISTEN_MUSIC, COUNT_MONEY, DRINK_COFFEE
}

class MascotStateHolder {
    var eyeOffsetX by mutableFloatStateOf(0f)       // -1 ~ 1
    var eyeOffsetY by mutableFloatStateOf(0f)       // -1 ~ 1
    var isDizzy by mutableStateOf(false)
    var speechText by mutableStateOf<String?>(null)
    var animationState by mutableStateOf(AnimationState.IDLE)

    /** 每次点击 +1，自定义形象据此触发 Q 弹反馈 */
    var clickTick by mutableIntStateOf(0)
}

object MascotSpeech {
    private val idlePhrases = listOf(
        "好无聊...", "有人吗？", "钱钱钱...", "记账了吗？",
        "今天花了多少？", "我在看着你哦", "别乱花钱~",
        "又月光了？", "存点钱吧", "Wally 来监督你！",
        "嗯...在想钱", "钱呢？！", "要理性消费哦",
        "你的钱包在哭泣", "省着点花~", "发呆中...",
        "数数今天花了多少", "摸鱼中~", "无聊到长蘑菇了"
    )

    private val clickPhrases = listOf(
        "别戳我！", "干嘛啦~", "好痒！", "哼！",
        "不要碰我！", "你点我干嘛？", "嗷！", "嗯？",
        "有事吗？", "Wally 在呢！", "别闹~", "再点就不理你了！",
        "戳戳戳，戳什么戳！", "疼！", "呜呜~"
    )

    private val dizzyPhrases = listOf(
        "好晕...", "别转了！", "我要吐了...", "天旋地转...",
        "停下来！", "呜呜呜...", "我头晕...", "世界在转...",
        "救命...", "不行了..."
    )

    private val notificationPhrases = listOf(
        "又花钱了！", "有新交易！", "钱又少了...", "注意消费！",
        "钱包警报！", "又支出了...", "省着点啊！", "账单来了！"
    )

    private val incomePhrases = listOf(
        "有钱进账！", "收钱啦！", "好开心！", "又赚了！",
        "进账进账！", "钱来了！", "耶！加钱了！", "发财了！"
    )

    private val longPressPhrases = listOf(
        "要设置什么？", "来了来了~", "有什么事？"
    )

    // ── 时间问候语 ──────────────────────────────────────
    private val morningPhrases = listOf(
        "早上好！", "早安~新的一天！", "起床啦！",
        "早上好，今天也要好好记账哦", "Wally 陪你开始新的一天！"
    )
    private val forenoonPhrases = listOf(
        "上午好！", "上午好~工作加油！", "上午好，记得记账哦",
        "上午效率最高！", "上午好，Wally 提醒你别忘记账~"
    )
    private val noonPhrases = listOf(
        "中午好！", "午饭时间~", "中午好，吃饱了吗？",
        "午休一下~", "中午了，上午花了多少？"
    )
    private val afternoonPhrases = listOf(
        "下午好！", "下午好~继续加油！", "下午茶时间？",
        "下午好，别打瞌睡哦", "下午了，今天消费还好吗？"
    )
    private val eveningPhrases = listOf(
        "晚上好！", "晚上好~辛苦了！", "下班了吗？",
        "晚上好，今天花了多少？", "晚上好，Wally 提醒你记账哦~"
    )
    private val nightPhrases = listOf(
        "夜深了...", "还不睡吗？", "熬夜伤钱又伤身...",
        "这么晚了还在花钱？", "早点休息吧~", "夜猫子~"
    )

    // ── 动作专属语句 ────────────────────────────────────
    private val walkSidePhrases = listOf(
        "走走走~", "溜达溜达~", "散步中~", "出去逛逛~"
    )
    private val yawnPhrases = listOf(
        "哈欠~", "好困...", "困死了...", "想睡觉..."
    )
    private val stretchPhrases = listOf(
        "伸个懒腰~", "好舒服~", "活动活动~", "舒展一下~"
    )
    private val lookPhonePhrases = listOf(
        "看看手机...", "刷刷手机~", "有人找我吗？", "看看消息..."
    )
    private val sneezePhrases = listOf(
        "阿嚏！", "哈...阿嚏！", "是不是有人在想我？", "阿嚏~感冒了？"
    )
    private val accountingPhrases = listOf(
        "记一笔...", "今天花了多少？", "算算账~", "记账记账！",
        "让我算算...", "嗯...这笔是..."
    )
    private val listenMusicPhrases = listOf(
        "听歌~", "音乐时间~", "嗯~好听的~", "跟着节奏~",
        "啦啦啦~", "享受音乐~"
    )
    private val countMoneyPhrases = listOf(
        "数钱数钱~", "1、2、3...", "我的钱呢？", "让我数数...",
        "钱越数越少...", "数钱真开心~"
    )
    private val drinkCoffeePhrases = listOf(
        "喝杯咖啡~", "提提神！", "咖啡续命~", "来杯拿铁~",
        "精神了！", "咖啡时间~"
    )

    fun randomIdle() = idlePhrases.random()
    fun randomClick() = clickPhrases.random()
    fun randomDizzy() = dizzyPhrases.random()
    fun randomNotification(isIncome: Boolean) =
        if (isIncome) incomePhrases.random() else notificationPhrases.random()
    fun randomLongPress() = longPressPhrases.random()

    /**
     * 点一下 Wally 时说的话——不是随便挑一句，而是「看着你的账本说话」。
     *
     * 依据：今天花了多少、今天还能花多少、这个月还剩多少、今天最大的一笔是哪类。
     * 气泡只有两行，所以每句都控制在 30 字以内。
     */
    fun budgetChatter(
        hasBudget: Boolean,
        todaySpentCents: Long,
        todayLimitCents: Long,
        monthRemainingCents: Long,
        topCategory: String?,
        topCategoryCents: Long
    ): String {
        fun yuan(cents: Long): String =
            String.format(java.util.Locale.US, "%.2f", cents / 100.0)

        if (!hasBudget) {
            return listOf(
                "还没设预算，去「预算」页设一个？",
                "没有预算，我只能看着你花钱了…",
                "不知道还能花多少，要不先定个预算？"
            ).random()
        }

        val todayRemaining = todayLimitCents - todaySpentCents
        val overMonth = monthRemainingCents < 0

        return when {
            todaySpentCents <= 0L -> listOf(
                "今天还没花钱，保持住！",
                "一分没花，今天很棒~"
            ).random()

            todayRemaining < 0L -> listOf(
                "今天超了 ¥${yuan(-todayRemaining)}，明天省点",
                "坏了，今天超标 ¥${yuan(-todayRemaining)}"
            ).random()

            todayLimitCents > 0L && todayRemaining * 5L < todayLimitCents -> listOf(
                "今天只剩 ¥${yuan(todayRemaining)} 了，悠着点",
                "额度快见底，还能花 ¥${yuan(todayRemaining)}"
            ).random()

            topCategory != null && topCategoryCents > 0L && topCategoryCents * 2L > todaySpentCents -> listOf(
                "「$topCategory」花了 ¥${yuan(topCategoryCents)}，今天还能花 ¥${yuan(todayRemaining)}",
                "大头在「$topCategory」¥${yuan(topCategoryCents)}，今天剩 ¥${yuan(todayRemaining)}"
            ).random()

            else -> listOf(
                "今天花了 ¥${yuan(todaySpentCents)}，还能花 ¥${yuan(todayRemaining)}",
                "今天花掉 ¥${yuan(todaySpentCents)}，还剩 ¥${yuan(todayRemaining)}"
            ).random()
        }.let { line ->
            when {
                overMonth -> "$line 本月也超支了…"
                monthRemainingCents > 0L && monthRemainingCents < todayLimitCents * 2L &&
                    line.length < 20 -> "$line 本月也快见底"
                else -> line
            }
        }
    }

    fun randomGreeting(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 6..9 -> morningPhrases.random()
            in 9..12 -> forenoonPhrases.random()
            in 12..14 -> noonPhrases.random()
            in 14..18 -> afternoonPhrases.random()
            in 18..22 -> eveningPhrases.random()
            else -> nightPhrases.random()
        }
    }

    fun randomForState(state: AnimationState): String? = when (state) {
        AnimationState.WALK_SIDE -> walkSidePhrases.random()
        AnimationState.YAWN -> yawnPhrases.random()
        AnimationState.STRETCH -> stretchPhrases.random()
        AnimationState.LOOK_PHONE -> lookPhonePhrases.random()
        AnimationState.SNEEZE -> sneezePhrases.random()
        AnimationState.ACCOUNTING -> accountingPhrases.random()
        AnimationState.LISTEN_MUSIC -> listenMusicPhrases.random()
        AnimationState.COUNT_MONEY -> countMoneyPhrases.random()
        AnimationState.DRINK_COFFEE -> drinkCoffeePhrases.random()
        else -> null
    }
}

object MascotPrefs {
    const val NAME = "mascot_prefs"
    const val KEY_ENABLED = "mascot_enabled"
    const val KEY_SCALE = "mascot_scale"
    const val KEY_RANDOM_MOVE = "mascot_random_move"
    const val KEY_SPEECH = "mascot_speech"
    const val KEY_EYE_TRACK = "mascot_eye_track"
    const val KEY_EYE_TRACK_PROB = "mascot_eye_track_prob"

    // ── 自定义形象 ──────────────────────────────────────
    const val KEY_STYLE = "mascot_style"
    const val KEY_IMAGE_URI = "mascot_image_uri"
    const val KEY_MOUTH_URI = "mascot_mouth_uri"
    const val KEY_ANIM_MODE = "mascot_anim_mode"
    const val KEY_CLICK_JELLY = "mascot_click_jelly"
    const val KEY_SNAP_ENABLED = "mascot_snap_enabled"
    const val KEY_SNAP_EDGE = "mascot_snap_edge"

    fun defaults() = mapOf(
        KEY_ENABLED to true,
        KEY_SCALE to 1.0f,
        KEY_RANDOM_MOVE to true,
        KEY_SPEECH to true,
        KEY_EYE_TRACK to true,
        KEY_EYE_TRACK_PROB to 1.0f,
        KEY_STYLE to MascotStyle.CUSTOM.value,
        KEY_ANIM_MODE to MascotAnimMode.JELLY.value,
        KEY_CLICK_JELLY to true,
        KEY_SNAP_ENABLED to true,
        KEY_SNAP_EDGE to MascotSnapEdge.LEFT.value,
    )
}
