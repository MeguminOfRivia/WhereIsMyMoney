# 我钱呢？！ / WhereIsMyMoney

> 一个只在**本地**记账的 Android 应用：听微信/支付宝的支付通知自动记账，也能导入账单，
> 带账户余额、分类预算、统计图表，还有一个会贴边、会眨眼、会吐槽你乱花钱的桌宠 Wally。

---

## ⚠️ 先说三件最重要的事

**1. 这个仓库的代码，全部由 AI 写成。**

从数据结构、数据库迁移、通知解析、Compose 界面，到单元测试和这份 README，
都是 AI（DeepSeek 驱动的编码代理）在人类给出需求、验收和报 bug 的循环里写的。
人类负责「要什么」「哪里不对」，AI 负责「怎么写」。**没有一个函数是逐行手敲的。**

**2. 目前处于「第一个正式版 1.0.0，但仍在持续修改」的阶段。**

- 从 1.0.0 起发布**正式签名**的安装包（见 [Releases](../../releases)），但**升级不保证数据不丢**；
- 数据库结构仍可能变（当前 v10），升级前请自己留一份账目截图或导出 CSV；
- 交互和界面随时可能改；
- 已知有粗糙的地方，见下面的「现状与已知问题」。

**3. 用之前请自己备份重要数据，出了问题作者不负责。**

完整免责声明见 [DISCLAIMER.md](DISCLAIMER.md)，隐私说明见 [PRIVACY_POLICY.md](PRIVACY_POLICY.md)。

---

## 下载与安装

- 最新安装包：到 **[Releases](../../releases)** 下载 `wimm-1.0.2.apk`（正式签名）。
- **从之前的 debug 包升级要先卸载**：debug 包和正式包的签名不同，系统不允许覆盖安装。
  卸载会连同账目数据一起清掉，所以升级前请先在「统计 → 右上角导出 CSV」把数据导出来。
- 安装后要给它三个权限才灵：**通知使用权**（读微信/支付宝支付通知）、
  **无障碍服务**（补齐通知里没写的商户名）、**悬浮窗**（Wally 桌宠）。

---

## 这个项目从哪来

本项目**完全基于** [FridayKoi/WhereIsMyMoney](https://github.com/FridayKoi/WhereIsMyMoney)（Apache License 2.0）。

- 上游提供了：Android 工程骨架、通知监听 + 无障碍服务的基本思路、账单文件解析的雏形、
  Wally 桌宠的初始形象与动画。
- 本仓库（fork）在保留这些基础的前提下，把**记账模型整个重做了一遍**，并在这个基础上继续加功能。

上游的著作权、许可证、原始说明依然有效，本源码同样以 Apache-2.0 发布，详见 [LICENSE](LICENSE)。
如果上游作者认为本 fork 的描述有不妥之处，欢迎开 issue 指出。

---

## 为什么要改

上游版本用「卡片」当钱包：你得先建一张卡片，再把流水塞进去。
但真实花钱不是这样的——

- 微信零钱、支付宝余额、花呗、某张银行卡，是**四种不同的东西**；
- 花呗是负资产（欠钱），银行卡是正资产，混在一张「卡片」里，余额永远是错的；
- 微信支付的通知里不会告诉你「这是哪张卡」，得靠来源判断；
- 预算、统计、分类这些事，上游基本没有或者很粗。

于是有了这次重做：**账户取代卡片，分类取代手工标签，预算和统计建立在同一套查询口径上。**

---

## 相对上游，具体改了什么

### 1. 记账模型：账户 + 初始余额

- **账户**（微信零钱 / 支付宝 / 银行卡 / 现金 / 信用 / 其他），每个账户有「初始余额」和「起始时间」。
  余额 = 初始余额 + 起始时间之后的收入 − 支出。初始余额可以是负数（花呗欠款）。
- **自动归属账户**：通知识别到微信支付就记到微信类账户，支付宝同理；导入账单时按账单来源自动挂账户。
- **收款方 + 大类**：内置 13 个支出大类、5 个收入大类，可新增、改名、改色、停用。
  自动分类优先级：单笔手动指定 > 收款方规则（完全相同 > 关键词越长越优先）> 未分类。
- **单笔可编辑**：点开任意一条流水，能改金额、收支方向、收款方、大类、账户、备注。
  改大类时会问一句「只改这笔 / 以后都改」，选后者自动建规则并批量修正历史流水。

### 2. 预算：按分类、可结转、两种日限额

- 每个大类一个月度预算，**总预算 = 各分类之和**（不单独存一个总数，避免两套数字打架）。
- **动态日额度**：本月剩余 ÷ 剩余天数。
- **固定日额度**：本月可用 ÷ 当月天数，自动计算（不用手填）；除不尽的零头加到当天额度里，
  每天没用完的额度自动继承到第二天。
- **跨月结转**：本月没花完的滚到下个月，超支也照样滚（负数），最多往回追 6 个月。
- 支持手动调整结转、直接清零。
- 记账日以**凌晨 4 点**为界（熬夜党的 01:00 算前一天）。

### 3. 统计与导出

- 时间范围：本月 / 上月 / 近 3 个月 / 今年 / 全部，可叠加账户、大类、收款方筛选。
- 支出饼图 + 分类占比、收款方排行、按天（长期按月）柱状图、与上一周期环比。
- 一键导出 CSV（走系统文件选择器，不申请任何存储权限）。

### 4. 账单导入

- 支持微信导出的 `.xlsx` 和支付宝导出的 `.csv`。
- CSV 解析重写：GBK / UTF-8 自动识别、带引号的字段、按表头找列、
  跳过「不计收支」和交易关闭的行、单号重复自动加后缀（两笔真交易不会被去重吃掉）。
- 导入时自动挂账户、自动分类，并和之前通知记下的同一笔**合并**（补商户名和单号，不重复插入）。

### 5. Wally 桌宠

- **双击** → 弹出「今天」预算面板（今天还能花多少、今天花最多的是哪类）；
- **长按** → 弹出「本月」预算面板（本月还剩多少、可用多少、花钱最多的三个大类）；
- 单击 → 说一句和记账有关的小话（「今天吃了吗？」「别冲动消费啊」），**不报具体数字**；
- 自定义照片 / GIF，两种对话动画（果冻 Q 弹 / 张口差分），可选**闭眼差分**定时眨眼；
- **贴边吸附**：拖动到屏幕边缘附近会自动贴边，贴边时图片最外侧的像素与屏幕边框对齐，
  换边时按设置自动左右镜像；
- 大小 10% ~ 150% 可调；
- 设置改动**即时生效**（跨进程用广播传值，不依赖 SharedPreferences 的进程内缓存）。

### 6. 隐私与工程

- **没有任何联网权限**：最终 APK 只声明了开机自启、通知、悬浮窗、前台服务四项；
  没有 `INTERNET`，没有 WebView，没有统计 SDK，没有动态加载代码。
- 关闭云备份（`allowBackup="false"` + 排除全部备份域）；release 包不打日志，日志里也从不记录金额和商户名。
- 稳定的交易指纹 + 数据库唯一索引做去重；跨通道（通知 / 无障碍 / 导入）合并只在金额、方向、时间都对得上时才进行。
- 99 个单元测试（解析器、转账状态机、预算算法、金额解析、CSV 解析、桌宠台词）。

---

## 现在能做什么（用户视角）

1. 装好后给通知使用权 + 无障碍 + 悬浮窗权限；
2. 在「账户」里建好你的微信、支付宝、银行卡，填上现在的余额；
3. 正常花钱，微信/支付宝付完款，流水自动记下来；
4. 在「预算」里给吃饭、交通这些大类定月度额度；
5. 首页看今天和本月花了多少，点任意一条可以改分类和备注；
6. 统计页看钱花在哪、和上个月比多了还是少了，需要的话导出 CSV；
7. Wally 在桌面上陪着你，戳它会吐槽，长按它会告诉你今天还剩多少额度。

---

## 现状与已知问题

这是一个**还在改**的项目，下面这些是已知的：

- **只发 debug 包**，没有正式签名；升级可能因为数据库版本变化而需要重装；
- 上游旧数据（卡片模型时期的流水）**没有做迁移适配**，账户一律显示为「未归属」；
- 手动记一笔（现金、离线消费）还没有入口，暂时只能靠导入账单补；
- 通知/无障碍识别的覆盖面取决于各家 App 的界面文案，冷门渠道可能识别不到；
- 桌面悬浮窗在不同厂商 ROM 上表现不一（部分系统会限制后台悬浮窗）；
- 界面细节、文案、动画仍在打磨。

发现问题欢迎开 issue，附上「哪个页面、点了什么、期望是什么」，最好带上系统版本和 App 版本。

---

## 隐私

**所有数据只存在这台手机上。** 应用没有联网权限，不上传、不备份、不统计、不收集设备信息。
通知内容只用来识别金额和商户，识别完就写进本地数据库，不外发。
详见 [PRIVACY_POLICY.md](PRIVACY_POLICY.md)。

---

## 免责声明（摘要）

- 本软件按「现状」提供，不附带任何明示或暗示的担保；
- 自动记账**可能漏记、错记、重复记录**，金额和分类请自行核对，不能作为财务、税务、法律依据；
- 因使用本软件导致的任何数据丢失、账目错误、设备异常或其他损失，作者与贡献者不承担责任；
- 请自行备份数据；卸载或清除数据会同时删掉全部账目；
- 本项目与腾讯（微信支付）、蚂蚁集团（支付宝）无任何关联，相关商标归各自所有者。

完整条款见 [DISCLAIMER.md](DISCLAIMER.md)。

---

## 自己编译

需要 JDK 21 和 Android SDK（compileSdk 36）。

```bash
./gradlew :app:assembleDebug        # 产出 app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease      # 正式包（需要签名配置，见下）
./gradlew :app:testDebugUnitTest    # 跑单元测试
```

`local.properties` 里把 `sdk.dir` 指向你的 Android SDK 即可。
界面全部是 Jetpack Compose，没有引入任何第三方 UI 库；图表是自己用 Canvas 画的。

**正式版签名**：密钥和密码放在仓库外的 `.toolchain/keystore/keystore.properties`（已 gitignore），
`app/build.gradle.kts` 里读到它才会给 release 包签名，读不到就产出未签名包。
**这个文件丢了就再也无法给已安装的用户推升级**，换机器时请务必一起备份。

### 代码结构

```
app/src/main/java/com/friday/wimm/
├─ data/
│  ├─ database/     SQLiteOpenHelper、账户/分类/规则/预算 DAO、流水查询
│  ├─ model/        Account / Category / MerchantRule / MonthlyBudget / Transaction ...
│  ├─ budget/       预算计算（结转、日额度）
│  ├─ transfer/     转账「待确认」状态机
│  └─ repository/
├─ service/         通知监听、无障碍、解析器、静默复核通知
├─ ui/
│  ├─ ledger/       首页 + 单笔编辑
│  ├─ accounts/     账户页
│  ├─ stats/        统计页
│  ├─ budget/       预算页
│  ├─ category/     大类与收款方规则
│  ├─ mascot/       Wally 桌宠（悬浮窗服务、形象、设置）
│  └─ settings/     设置与各项入口
└─ util/            金额、预算算术、商户名归一化等纯函数
```

---

## 致谢

- 上游项目：[FridayKoi/WhereIsMyMoney](https://github.com/FridayKoi/WhereIsMyMoney)（Apache-2.0）
- 默认的像素小猫形象来自上游仓库
- 所有代码由 AI 编写，人类负责需求、验收和挨个试 bug

## License

[Apache License 2.0](LICENSE) —— 与上游一致。

---

## English Summary

**WhereIsMyMoney** is a fully local Android expense tracker. It listens to WeChat Pay / Alipay
payment notifications and bills files to record transactions automatically, then gives you
accounts with real balances, per-category monthly budgets with carry-over, and statistics.

- **Forked from** [FridayKoi/WhereIsMyMoney](https://github.com/FridayKoi/WhereIsMyMoney) (Apache-2.0).
- **The entire codebase, including this README, is written by an AI agent**; a human set the
  requirements, reviewed results and filed bugs.
- **Still in debug stage**: debug-signed builds only, schema still moving, no data migration from
  the upstream card-based model, features still being added.
- **No network permission at all** — no INTERNET, no WebView, no analytics, no cloud backup.

Use at your own risk. See [DISCLAIMER.md](DISCLAIMER.md) and [PRIVACY_POLICY.md](PRIVACY_POLICY.md).
