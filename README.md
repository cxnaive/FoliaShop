# FoliaShop - 高版本Folia/Paper系统商店与扭蛋插件

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Folia](https://img.shields.io/badge/Folia-1.21+-blue.svg)](https://github.com/PaperMC/Folia)

一个专为 [Folia/Paper](https://github.com/PaperMC/Folia) 服务端设计的系统商店和扭蛋插件，支持原版物品和 [CraftEngine](https://github.com/Momirealms/CraftEngine) 自定义物品。

## ✨ 功能特性

### 系统商店
- 🛒 支持购买和出售物品
- 📂 商品分类管理（支持子分类）
- 📦 库存系统（支持无限库存）
- 🔧 支持 CraftEngine 自定义物品（CE物品）
- 📜 交易记录（玩家可查询最近20次）
- ⏰ **每日购买限额**（每个物品独立配置）
- 🔒 **玩家终身限购**（每个物品独立配置）
- 💰 **分类每日出售限额**（按分类限制每日出售获得的金币总额）
- 🏷️ **出售专用分类**（配置 `sell-only: true`，不在商店显示但可出售）
- 💎 **PlayerPoints 点券支付**（支持金币+点券混合支付）
- 🏷️ **NBT 组件支持**（附魔、自定义名称、Lore、自定义数据）
- ⚡ **纯命令商品**（给予权限、执行命令，可不给予物品）
- 🎲 **随机价格**（每日每玩家固定随机定价，`"100~200"` 格式）
- ⚡ **异步出售流程**（不阻塞游戏线程，Folia 友好）

### 扭蛋系统
- 🎰 多扭蛋机支持
- 🎯 概率配置
- 🔧 支持 CraftEngine 自定义物品（CE物品）
- ✨ 抽奖动画（单抽 + 10连抽）
- 🛡️ **软保底机制**（线性概率增长）
- 📢 稀有奖品广播（MiniMessage格式）
- 👁️ 奖品预览（按概率排序）
- 📊 抽奖记录
- 🧊 **方块绑定**（将扭蛋机绑定到方块，右键交互）
- 🎨 **展示实体**（悬浮物品动画、粒子效果）
- 🎯 **累抽自选**（累计抽奖次数可兑换自选奖品）
- 🏆 **收集兑换**（收集指定奖品组合可兑换额外奖励）

### 全球商店（玩家间交易市场）
- 🏪 玩家上架物品自定义价格出售
- 🔍 浏览和搜索在售物品
- 💰 购买其他玩家上架的物品
- 📦 物品退还与收益领取
- 📋 上架管理（查看、取消上架）

### 其他功能
- 💱 **点券兑换金币**（PlayerPoints 点券按汇率兑换金币）
- 💾 **数据库备份/恢复**（支持 H2 和 MySQL 互导）
- 🔀 支持 H2（本地）和 MySQL（跨服）

## 📋 依赖要求

### 必需插件（硬依赖）
| 插件 | 版本 | 用途 |
|------|------|------|
| Folia | 1.21+ | 服务端核心 |
| XConomy | 2.25+ | 经济系统 |
| CraftEngine | 0.0.67+ | 自定义物品系统 |

### 可选插件（软依赖）
| 插件 | 版本 | 用途 |
|------|------|------|
| PlayerPoints | 3.2+ | 点券系统 |

## 🚀 安装

1. 下载最新版本的 `folia_shop-1.2.1.jar`
2. 将 JAR 文件放入服务器的 `plugins` 文件夹
3. 重启服务器或加载插件
4. 编辑 `plugins/FoliaShop/config.yml` 配置数据库连接
5. 编辑 `plugins/FoliaShop/shop.yml` 配置商品
6. 编辑 `plugins/FoliaShop/gacha.yml` 配置扭蛋机
7. 执行 `/foliashop reload` 重载配置

## 📖 命令

### 玩家命令
| 命令 | 描述 | 权限 |
|------|------|------|
| `/foliashop` | 打开主菜单 | `foliashop.use` |
| `/foliashop shop` | 打开系统商店 | `foliashop.shop.use` |
| `/foliashop sell` | 打开出售界面 | `foliashop.shop.sell` |
| `/foliashop gacha` | 打开扭蛋界面 | `foliashop.gacha.use` |
| `/foliashop globalshop` | 打开全球商店 | `foliashop.globalshop.use` |
| `/foliashop globalshop sell` | 上架物品到全球商店 | `foliashop.globalshop.sell` |
| `/foliashop globalshop manage` | 查看我的上架 | `foliashop.globalshop.sell` |
| `/foliashop globalshop returns` | 查看待领取物品 | `foliashop.globalshop.use` |
| `/foliashop pick <扭蛋机ID>` | 使用累抽自选 | `foliashop.gacha.use` |
| `/foliashop collect` | 打开收集兑换 | `foliashop.gacha.use` |
| `/foliashop collect claim <id>` | 领取收集奖励 | `foliashop.gacha.use` |
| `/shop [分类]` | 直接打开商店（可指定分类） | `foliashop.shop.use` |
| `/gacha [扭蛋机ID]` | 直接打开扭蛋（可指定机器） | `foliashop.gacha.use` |

### 管理员命令
| 命令 | 描述 | 权限 |
|------|------|------|
| `/foliashop reload` | 重载配置文件 | `foliashop.admin` |
| `/foliashop admin` | 打开商店管理界面 | `foliashop.admin` |
| `/foliashop reset` | 清空数据库并从配置重新加载 | `foliashop.admin` |
| `/foliashop clean <天数>` | 清理旧数据（5/10/30天） | `foliashop.admin` |
| `/foliashop bindblock <machineId>` | 将看向的方块绑定到扭蛋机 | `foliashop.admin` |
| `/foliashop unbindblock` | 解绑看向的方块 | `foliashop.admin` |
| `/foliashop listblocks [machineId]` | 列出方块绑定 | `foliashop.admin` |
| `/foliashop export [full\|config\|state]` | 导出数据库备份 | `foliashop.admin` |
| `/foliashop import <文件名> [replace\|merge]` | 从备份恢复数据库 | `foliashop.admin` |
| `/foliashop stats [-\|玩家名] <machineId> <rewardId>` | 查询奖品统计 | `foliashop.admin` |
| `/foliashop exportshop` | 导出商店数据到 YAML | `foliashop.admin` |

## 🔐 权限节点

### 玩家权限
```yaml
foliashop.use:              # 使用基础命令（打开主菜单）
  default: true

foliashop.shop.use:         # 使用商店功能
  default: true

foliashop.shop.sell:        # 出售物品给系统
  default: op

foliashop.gacha.use:        # 使用扭蛋功能
  default: op

foliashop.globalshop.use:   # 使用全球商店（浏览和购买）
  default: true

foliashop.globalshop.sell:  # 在全球商店上架物品
  default: true
```

### 管理员权限
```yaml
foliashop.admin:        # 管理员权限（编辑商店、重载配置等）
  default: op
  children:
    foliashop.shop.admin: true   # 商店管理
    foliashop.gacha.admin: true  # 扭蛋管理
```

### 权限说明
- `foliashop.use` - 基础命令权限，默认所有玩家拥有
- `foliashop.shop.use` - 商店使用权限，默认所有玩家拥有
- `foliashop.shop.sell` - 出售物品权限，默认仅 OP（可在配置中启用）
- `foliashop.gacha.use` - 扭蛋使用权限，默认仅 OP（可在配置中启用）
- `foliashop.globalshop.use` - 全球商店浏览和购买权限，默认所有玩家拥有
- `foliashop.globalshop.sell` - 全球商店上架权限，默认所有玩家拥有
- `foliashop.admin` - 管理员权限，包含商店和扭蛋管理子权限

## 👑 管理员功能

### 商店物品管理
管理员可以通过 `/foliashop admin` 命令打开商店管理界面，对单个物品进行以下操作：

| 功能 | 说明 |
|------|------|
| 📦 库存调整 | +1, +10, +64, -1, -10, 设为无限 |
| 🗑️ **清空库存** | 将库存设为 0 |
| 🔄 **从配置文件重置** | 从 `shop.yml` 重新加载该物品的所有配置 |
| ❌ **删除物品** | 从数据库中永久删除该商店物品（带确认对话框） |

### 数据清理
管理员可以使用 `/foliashop clean <天数>` 命令清理旧数据：

```bash
/foliashop clean 5    # 清理5天以前的数据
/foliashop clean 10   # 清理10天以前的数据
/foliashop clean 30   # 清理30天以前的数据
```

**清理的数据类型：**
- 交易记录
- 抽奖记录
- 过期购买计数
- 过期分类出售额度

### 数据库备份/恢复
管理员可以使用备份命令导出和恢复数据库：

```bash
# 导出备份
/foliashop export           # 导出配置+状态（推荐）
/foliashop export config    # 只导出配置（商品、方块绑定）
/foliashop export state     # 导出配置+玩家状态（限购、保底）
/foliashop export full      # 导出所有数据（包含日志）

# 恢复备份
/foliashop import backup_20250215_143022      # 清空现有数据后导入
/foliashop import backup_20250215_143022 merge # 合并导入，跳过冲突

# 查看可用备份
/foliashop import  # 不填文件名会列出所有备份
```

**备份文件位置：** `plugins/FoliaShop/backups/`

**跨数据库迁移：** 支持从 H2 导出，导入到 MySQL（或反过来）

### 扭蛋方块绑定
管理员可以将扭蛋机绑定到方块，玩家右键点击方块即可打开扭蛋界面：

```bash
/foliashop bindblock normal      # 将看向的方块绑定到 normal 扭蛋机
/foliashop unbindblock           # 解绑看向的方块
/foliashop listblocks            # 列出所有方块绑定
/foliashop listblocks normal     # 列出 normal 扭蛋机的方块绑定
```

绑定方块后会自动生成展示实体（悬浮的物品图标）。

## ⚙️ 配置说明

### 配置文件结构

插件使用分离的配置文件结构，便于管理：

```
plugins/FoliaShop/
├── config.yml            # 主配置：数据库、经济、GUI、消息、全球商店
├── shop.yml              # 商店配置：商品、分类、回收设置
├── shop_*.yml            # 商店拆分配置（可选，自动加载合并）
├── gacha.yml             # 扭蛋配置：扭蛋机、奖品、保底、展示实体
├── gacha_*.yml           # 扭蛋拆分配置（可选，自动加载合并）
└── backups/              # 自动创建的备份目录
```

### config.yml — 主配置

包含数据库连接、经济系统、GUI界面、消息文本、全球商店等全局设置。

```yaml
# 数据库配置
database:
  type: h2  # 可选: h2, mysql
  mysql:
    host: localhost
    port: 3306
    database: foliashop
    username: root
    password: password
    pool-size: 10
  h2:
    filename: foliashop

# 经济系统设置
economy:
  enabled: true
  currency-name: "金币"
  currency-format: "{amount} {currency}"
  # 点券兑换金币（需要 PlayerPoints 插件）
  exchange:
    enabled: true
    rate: 2.0        # 1 点券 = 2 金币

# 全球商店设置（默认关闭）
globalshop:
  enabled: false
  tax-rate: 0.05                    # 交易税率 5%
  rental-period-days: 7             # 上架租期（天）
  listing-fee: 100.0                # 上架费用
  max-listings-per-player: 10       # 每人最大上架数
  expired-retain-days: 30           # 过期保留天数

# GUI界面标题
gui:
  titles:
    main-menu: "<dark_gray>主菜单"
    shop: "<green>系统商店"
    gacha: "<gold>扭蛋中心"
    globalshop: "<dark_purple>全球商店"
    globalshop-submit: "<blue>上架物品"
    globalshop-returns: "<gold>待领取物品"
    globalshop-manage: "<green>我的上架"
  # 装饰物品（边框、按钮等）
  decoration:
    border: { material: "minecraft:black_stained_glass_pane", name: " " }
    close: { material: "minecraft:barrier", name: "<red>关闭" }
    back: { material: "minecraft:arrow", name: "<yellow>返回" }
    confirm: { material: "minecraft:lime_wool", name: "<green>✔ 确认" }
    cancel: { material: "minecraft:red_wool", name: "<red>✘ 取消" }

# 消息（支持 MiniMessage 格式，可用变量: {item}, {amount}, {cost}, {player} 等）
messages:
  prefix: "<gold>[系统商店] <reset>"
  purchase-success: "<green>✔ 成功购买 <white>{item} <yellow>x{amount}"
  gacha-broadcast: "<gold><bold>🎉 恭喜 {player} 从 {machine} 抽中了 {item}！"
  # ... 更多消息配置见 config.yml
```

### shop.yml — 商店配置

包含商店开关、系统回收、商品分类和商品列表。

```yaml
# 商店开关
enabled: true
title: "系统商店"
allow-sell: true
sell-discount: 0.7            # 出售价格 = 购买价格 × 0.7
log-transactions: true        # 记录交易日志
refresh-interval: 0           # 自动刷新间隔（分钟，0=不刷新）
daily-buy-limit: 0            # 全局每日购买限制（0=无限制）

# 系统回收设置
sell-system:
  enabled: true
  add-stock-on-sell: false    # 回收物品是否增加商店库存

# 商品分类
categories:
  building:
    name: "建筑材料"
    icon: "minecraft:bricks"
    slot: 10
    enabled: true         # 可选，设为 false 则不显示
    sell-only: false      # 可选，设为 true 则仅在出售界面可用
    daily-sell-limit: 0   # 每日出售金币限额（0=无限制）
    # 支持子分类:
    # subcategories:
    #   blocks:
    #     name: "建筑方块"
    #     icon: "minecraft:stone"
    #     slot: 11

# 商品列表
items:
  diamond:
    item: "minecraft:diamond"
    buy-price: 100.0          # 购买价格（金币）
    sell-price: 50.0          # 出售价格（金币）
    stock: -1                 # -1=无限库存
    category: "minerals"
    slot: 10
    daily-limit: 10           # 每日购买限额（0=无限制）
    # player-limit: 1         # 终身限购（0=无限制）
    # buy-points: 100         # 点券价格（需 PlayerPoints）
    # components:             # NBT组件
    #   - "minecraft:enchantments+{'minecraft:sharpness':5}"
    # give-item: false        # 不给予物品，只执行命令
    # commands:
    #   - "lp user {player} parent addtemp vip 30d"
    # conditions:
    #   - "!permission:group.vip"
    # enabled: true           # 是否启用

  # 随机价格（"最低~最高"，每日每玩家固定）
  emerald:
    item: "minecraft:emerald"
    buy-price: "50~200"
    sell-price: "25~100"
    stock: -1
    category: "minerals"
    slot: 11
```

**分类字段说明：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | string | 分类显示名称 |
| `icon` | string | 分类图标物品ID |
| `slot` | int | GUI中的位置 |
| `enabled` | bool | 是否启用（默认true） |
| `sell-only` | bool | 出售专用，不在商店显示（默认false） |
| `daily-sell-limit` | double | 每日出售金币限额，0=无限制（默认0） |
| `subcategories` | section | 子分类配置（可选） |

> **注意**：`daily-sell-limit` 定义在父分类级别，子分类的物品共享父分类的限额。
> 例如 `building` 分类限额 5000，则 `building:blocks` 和 `building:decor` 下的物品出售总额共受 5000 限制。

**商品字段说明：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `item` | string | 物品ID，支持 `minecraft:` 和 `craftengine:` |
| `buy-price` | double/string | 购买价格，支持随机范围 `"50~200"` |
| `sell-price` | double/string | 出售价格，0=不可出售，支持随机范围 |
| `buy-points` | int | 点券价格，可与金币同时设置 |
| `stock` | int | 库存，-1=无限 |
| `category` | string | 分类ID，支持子分类 `"parent:child"` |
| `slot` | int | GUI中的位置 |
| `daily-limit` | int | 每日购买限额，0=无限制 |
| `player-limit` | int | 终身限购，0=无限制 |
| `components` | list | NBT组件列表 |
| `give-item` | bool | 是否给予物品（默认true） |
| `commands` | list | 购买后执行的命令（`{player}` 替换为玩家名） |
| `conditions` | list | 购买条件（如 `!permission:group.vip`） |
| `enabled` | bool | 是否启用（默认true） |

**拆分配置：** 可将商品拆分到 `shop_*.yml` 文件（如 `shop_weapons.yml`），所有 `shop_*.yml` 会自动加载合并。

### gacha.yml — 扭蛋配置

包含扭蛋开关、展示实体全局设置、扭蛋机列表和收集兑换。

```yaml
enabled: true

# 展示实体全局设置（每个扭蛋机可单独覆盖）
display-entity:
  enabled: true
  scale: 0.8              # 缩放比例
  rotation-y: 45          # Y轴旋转角度
  face-player: false      # 是否朝向玩家
  floating-animation: true
  float-amplitude: 0.1    # 悬浮幅度
  float-speed: 1.0        # 悬浮速度
  height-offset: 1.5      # 高度偏移
  view-range: 32          # 视图范围
  glowing: false          # 是否发光
  glow-color: null        # 发光颜色 ("RRGGBB")
  particle-effect:
    # 类型: NONE, STAR_RING, MAGIC_RUNE, RAINBOW_HALO, FLAME_AURA, FROST_CRYSTAL, LOVE_BUBBLE
    type: NONE
    density: 3
    radius: 1.2
    speed: 1.0

# 扭蛋机列表
machines:
  normal:
    name: "<green>普通扭蛋机"
    description:
      - "<gray>花费 <yellow>100金币 <gray>抽取一次"
    icon: "minecraft:chest"
    # icon-components:     # 图标NBT组件（可选）
    #   - "minecraft:enchantment_glint_override+true"
    cost: 100.0
    animation-duration: 3      # 单抽动画（秒）
    animation-duration-ten: 8  # 10连抽动画（秒）
    broadcast-rare: true
    broadcast-threshold: 0.05  # 概率<5%时广播

    # 软保底（可选）
    pity:
      enabled: true
      start: 70              # 70抽后开始增长概率
      max: 90                # 90抽必出
      target-max-probability: 0.05

    # 累抽自选（可选）
    milepost-pick:
      interval: 100          # 每100抽获得1次自选
      max-picks: 10          # 最多10次（0=无限制）

    # 展示实体覆盖（可选，未设置的项继承全局配置）
    # display-entity:
    #   scale: 1.2
    #   glowing: true
    #   glow-color: "#FFD700"
    #   particle-effect:
    #     type: STAR_RING

    # 奖品列表
    rewards:
      - id: "common_coal"
        item: "minecraft:coal"
        amount: 16
        probability: 0.15
        display-name: "<white>煤炭"
      - id: "epic_sword"
        item: "minecraft:diamond_sword"
        amount: 1
        probability: 0.01
        display-name: "<gold>传说之剑"
        broadcast: true       # 强制广播（覆盖阈值判断）
        components:
          - "minecraft:enchantments+{'minecraft:sharpness':5}"
          - "minecraft:custom_name+\"§6传说之剑\""

# 收集兑换（可选，在 gacha.yml 末尾配置）
# collections:
#   warrior_set:
#     name: "<gold>战士套装收集"
#     icon: "minecraft:iron_sword"
#     description:
#       - "<gold>收集齐4种战士装备"
#     requires:
#       - machine: "normal"
#         reward: "uncommon_iron"
#       - "premium:p_diamond"    # 简写格式
#     reward:
#       item: "minecraft:diamond_sword"
#       amount: 1
#       components:
#         - "minecraft:enchantments+{'minecraft:sharpness':10}"
#       commands:
#         - "say {player} 完成了收集任务！"
#       give-item: true
#     repeatable: false
```

**奖品字段说明：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | string | 奖品唯一ID |
| `item` | string | 物品ID |
| `amount` | int | 数量 |
| `probability` | double | 概率（所有奖品概率之和应为1.0） |
| `display-name` | string | 显示名称（MiniMessage格式） |
| `broadcast` | bool | 是否强制广播（可选） |
| `components` | list | NBT组件列表（可选） |

**拆分配置：** 可将扭蛋机拆分到 `gacha_*.yml` 文件（如 `gacha_event.yml`），所有 `gacha_*.yml` 会自动加载合并。

## 🛠️ 构建

```bash
./gradlew shadowJar
```

构建后的 JAR 文件位于 `build/libs/folia_shop-1.2.1.jar`

## 🏗️ 项目结构

```
folia_shop/
├── build.gradle.kts              # Gradle构建配置
├── settings.gradle.kts
├── gradlew
├── gradle/wrapper/
├── src/
│   └── main/
│       ├── java/dev/user/shop/
│       │   ├── FoliaShopPlugin.java
│       │   ├── command/          # 命令处理
│       │   ├── config/           # 配置管理
│       │   ├── database/         # 数据库
│       │   ├── economy/          # 经济系统 (XConomy API, 点券兑换)
│       │   ├── gacha/            # 扭蛋系统
│       │   ├── globalshop/       # 全球商店（玩家间交易）
│       │   ├── gui/              # GUI界面
│       │   ├── listener/         # 事件监听
│       │   ├── shop/             # 商店系统
│       │   └── util/             # 工具类 (CraftEngine API, 随机价格)
│       └── resources/
│           ├── plugin.yml
│           └── config.yml
└── README.md
```

## 📦 依赖

| 依赖 | 版本 | 来源 |
|------|------|------|
| Folia-API | 1.21.11-R0.1-SNAPSHOT | PaperMC |
| XConomyAPI | 2.25.1 | JitPack |
| CraftEngine | 0.0.67 | Momirealms |
| PlayerPoints | 3.2+ | GitHub |
| HikariCP | 6.2.1 | Maven Central |
| H2 | 2.3.232 | Maven Central |
| MySQL Connector | 9.2.0 | Maven Central |

## 📄 许可证

MIT License

---

**注意**：本插件专为 Folia/Paper 服务端设计。
