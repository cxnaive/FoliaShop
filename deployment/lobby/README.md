# 大厅彩蛋兔兔与绯樱配置

2026-10-22 21:38:53，大厅另行更新了家具与全球商店配置：家具“奇境主题馆”补充排除 `elitecreatures:energy_vol_1_staff1`；全球商店每次上架费由 200 金币降为 50，成交税率维持 10%。已备份并通过 `foliashop reload` 生效，Java PID 保持。配置快照和差异在本地受限目录 `run/lobby-config-20261022-furniture-globalshop/`，详细恢复点见 `../thw_servers/reports/configuration/lobby/2026-10-22_foliashop-furniture-globalshop/analysis.md`。

主城已有 FancyNpcs NPC `globalshop`，通过 `ANY_CLICK` 的 `player_command: fshop globalshop` 打开玩家全球商店。如需新建，在目标位置执行 `/npc create global_market`，再执行 `/npc action global_market ANY_CLICK add player_command foliashop globalshop`。

2026-10-14 已部署到 `/home/mc/lobby/plugins/FoliaShop/` 并执行 `foliashop reload`。

- `FoliaShop/gacha_easterbunny.yml`：预置武装、骑士、冒险三台机器。只有 `easterbunny_arms` 为 `enabled: true`；当前只加载其七件武装收集任务，集齐可领 `easterbunny_v4:wing`。
- `FoliaShop/gacha_easterbunny_collections.yml.disabled`：骑士与冒险的收集奖励预备配置。FoliaShop 只扫描 `gacha_*.yml`，所以此文件当前不会加载。
- 三台机器奖池权重各为 1.0，单抽 300 金币、十连 3000 金币。武装七件各 0.08%，合计基础概率 0.56%，第 68 抽起软保底、第 100 抽硬保底（配置值为 `start: 67` / `max: 99`，代码计数从 0 开始）；各机每 300 抽可自选一次，最多 3 次。后两台机器不设软保底。
- 进度按抽奖记录的 `machine_id` 和 `reward_id` 计算，自选也计入收集；奖励不消耗已获得的物品。三条收集任务均可重复完成，重复领取需重新收集一轮。

后续开放骑士或冒险时，先把对应机器设为 `enabled: true`，并将要开放的收集任务复制到可加载的 `gacha_*.yml` 中，再执行 `foliashop reload`。不要直接将整个 `.disabled` 文件改名，除非两条路线同时开放。

CE 物品定义位于 `../ia_to_ce/ce/easterbunny_v4/`。2026-10-14 20:04～20:05，兔兔11件武器与工具的底材已在所有8个正式CE安装点升级为对应下界合金，7个在线服重载成功；物品ID及抽奖记录ID保持不变，旧抽奖与收集进度仍有效。没有批量转换玩家已持有的旧钻石版物品，需单独核验实际服务端材质。详情见 `../ia_to_ce/reports/easterbunny-v4/README.md`。翅膀和背包通过副手展示，会占用副手槽。

2026-10-15 11:55:27 已额外执行大厅 `foliashop reload`。FoliaShop 在加载奖池时缓存 CE ItemStack，实际发奖克隆缓存；此前只重载 CE 没刷新奖池，所以 10-14 20:04 到本次重载之间，兔兔武装抽出的斧/锄/镐/铲等仍可能是钻石版。重载后兔兔武装奖池按新 CE 定义重新构建；当时已打开的抽奖界面应关闭后重进。原抽奖记录与收集进度保持。

## 绯樱转入点券商城

2026-10-14 17:42:59 正式大厅已重载生效。

- `FoliaShop/gacha_pinksuite.yml`：原 `pinksuite` 机器与其展示实体设为 `enabled: false`，保留配置和历史抽奖数据。
- `FoliaShop/gacha.yml`：移除原 `pink_collection` 收集任务，其他全局设置保持原值。当前仅兔兔武装收集任务加载。
- `FoliaShop/shop_points_pink.yml`：点券商城新增 `points:pink_suite`，名称为“绯樱武装系列”，子分类位置16。剑、镰、锤、斧、锄、镐、铲各3500点券；翅膀4500点券；无限库存，金币价格及系统回收价均为0。

验证：远端五份文件与本地一致；日志确认1个收集兑换、新增8个商店商品、绯樱展示实体禁用；PlayerPoints已连接，Java PID2527235保持。没有实际扣点或发奖测试。

更新前恢复点：`/home/mc/ops/archive/20261014_174245-foliashop-easterbunny-pink/before.zip`，含原 `gacha.yml`、`gacha_pinksuite.yml` 和初版 `gacha_easterbunny.yml`。恢复时还需要移出本次新增的 `shop_points_pink.yml`；商品已由插件增量保存到数据库，完整回退商城需同时处理八个 `pink_pack_*` 商品，不能只移走YAML。

## 绯樱抽奖与收集任务恢复

2026-10-15 13:16:30，大厅将 `gacha_pinksuite.yml` 中原机及展示实体改回 `enabled: true`，并把原 `pink_collection` 收集任务加回 `gacha.yml`。原机器/奖品/收集 ID 和奖池权重保持不变。执行 `foliashop reload` 后日志确认10台机器、2条收集任务、1164件商店商品；绯樱的8件点券商品仍在。

恢复前只读核对数据库：`pinksuite` 有32575条抽奖记录、320名玩家的保底记录；`pink_collection` 有23名玩家的领取记录、累计领取24次。本轮没有修改或删除这些表。原绯樱机在数据库中没有方块绑定，玩家通过扭蛋菜单进入。恢复前配置备份：`/home/mc/ops/archive/20261015_131621-foliashop-pink-restore/before.zip`。

## 兔兔玩偶奖池

2026-10-15 17:41:58 将兔兔武装、骑士、冒险三台机器预置奖池的玩偶由单一粉帽兔 5%，改为 10 种各 0.5%，合计仍为 5%。武装继续启用，骑士与冒险继续禁用；全池权重仍各为 1.0，保底、装备概率、价格和收集条件未变。原粉帽兔 `rabbit_plushie` 奖品 ID 保留，历史记录无需迁移。

已同步大厅并执行 `foliashop reload`，日志确认兔兔配置加载、10台机器/2条收集任务、兔兔武装展示实体重建，原 Java PID 保持。更新前配置备份：`/home/mc/ops/archive/20261015_174144-foliashop-toy-pool/before.zip`。此前兔兔武装正常抽奖 5144 次中，粉帽兔出现 260 次（约 5.05%）；这是变更前的历史分布。
