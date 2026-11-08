package com.friday.wimm.ui.mascot

import androidx.compose.runtime.*
import kotlin.random.Random

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
     * 点一下 Wally 时说的话。
     *
     * 刻意**不带任何数字、金额和分类名**——气泡只有两行，报数字会挤成一坨，
     * 而且看数字有双击/长按的预算面板。这里只说和花钱有关的小话。
     * 唯一参考的状态是「今天有没有超」，用来在两种语气之间挑一句。
     */
    fun smallTalk(hasBudget: Boolean, overBudgetToday: Boolean): String {
        if (!hasBudget) return noBudgetTalk.random()
        if (overBudgetToday) return overBudgetTalk.random()
        return if (Random.nextFloat() < 0.55f) underBudgetTalk.random() else dailyTalk.random()
    }

    private val dailyTalk = listOf(
        "今天吃了吗？",
        "控制饮食，就是控制钱包",
        "记账了吗？",
        "还有什么要买的吗？",
        "别冲动消费啊",
        "钱要花在刀刃上",
        "买之前先想想",
        "今天也要好好记账哦",
        "省下来的才是自己的",
        "钱包在看着你哦",
        "有没有乱花钱呀？",
        "今天过得怎么样？"
    )

    private val overBudgetTalk = listOf(
        "今天又超支了噢…",
        "今天好像花多了",
        "钱包有点疼…",
        "明天要省着点啦",
        "悠着点，钱不经花"
    )

    private val underBudgetTalk = listOf(
        "今天还有盈余，不错嘛",
        "今天花得挺克制！",
        "钱包今天很开心",
        "继续保持哦~"
    )

    private val noBudgetTalk = listOf(
        "还没设预算，去「预算」页设一个？",
        "定个预算吧，我帮你盯着",
        "没有预算，我可管不住你的手"
    )

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

    // ── 眨眼 ────────────────────────────────────────────
    const val KEY_BLINK_URI = "mascot_blink_uri"
    const val KEY_BLINK_ENABLED = "mascot_blink_enabled"

    /** 可调节的大小范围：10% ~ 150% */
    const val MIN_SCALE = 0.1f
    const val MAX_SCALE = 1.5f

    fun clampScale(value: Float): Float = value.coerceIn(MIN_SCALE, MAX_SCALE)

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
        KEY_BLINK_ENABLED to true,
    )
}
