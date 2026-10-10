# 交接说明（给新对话）· 2026-10-09

> 旧对话因上下文过长导致 API 400，改在新对话继续。**本文件是断点续传的唯一依据。**

## 一、当前进度总览

| 版本 | 内容 | 状态 |
|---|---|---|
| **0.5.4** | 震惊瘫坐之地「局部海平面/盆地注水」（SlumpWaterField，L=38） | ✅ 已提交、已推送分支 `codex/0.5.4-slump-water`、已部署 |
| **0.5.5** | 大数表界「无穷降链」3×3 竖井 + 序石螺旋楼梯；葛立恒藤加入 `minecraft:climbable` 标签 | ✅ 已提交、已推送 `codex/0.5.5-surface-shaft-and-vine`、已部署 |
| **0.5.6** | 修 26.3 数据格式漂移（loot `block_state_property`→`match_block`；advancement `recipe`→`recipes`）——**首次让 26.3 能进世界** | ✅ 已提交、已推送 `codex/0.5.6-26.3-data-fixups`、已部署 |
| **0.5.7** | ①竖井外圈 16 格石壳（防联通洞穴）②楼梯改用「康威链岩」+ 四方向箭头 | ⚠️ **未提交**：①已完成并构建通过；②**做到一半**（见下） |

- 仓库：`C:\Users\HUAWEI\Documents\deepseek-harness\default-workspace\guogaology`（fork：github.com/AliceLdl/minecraft-guogaology，deploy key 推送已通）
- 用户实际游玩的实例：`…\PCL+正式版+2.13.1.1\.minecraft\versions\26.3-Fabric 0.19.5\mods\`
  （另一个实例 `fabric-loader-0.19.5-26.3\mods` 也同步；`Downloads\` 放测试副本）

## 二、0.5.7 未完成的部分（新对话从这里接着做）

需求：**螺旋楼梯的台阶统一用「康威链岩」`guogaology:conway_link`，且该方块支持四个水平朝向，箭头始终指向下一级（低一格）台阶。**

已做（在工作区里，未提交）：
- `ports/common/main/dev/guogaology/block/ConwayLinkBlock.java` —— 新增，带 `FACING = BlockStateProperties.HORIZONTAL_FACING`、`getStateForPlacement`（朝向玩家）、默认 `EAST`
- `ports/common/main/dev/guogaology/GuogaologyBlocks.java` —— 已把 `CONWAY_LINK` 从 `landscape(...)` 换成新工厂 `conwayLink(...)`（新类）
- `ports/common/main/dev/guogaology/outer/world/SurfaceShaftField.java` —— 只加了 import（`Direction`/`BuiltInRegistries`/`Block`/`BlockState`/`Property`/`GuogaologyMod`），**StepPalette 与 apply 签名尚未改**
- 1.21.1 基座（`src/main/java`，**yarn 映射**）已还原干净，不放新方块类（该目标优雅退化为无朝向的普通康威链岩）

待做：
1. `SurfaceShaftField`：加 `StepPalette<T>{ T step(int facing); }`（0=北 1=东 2=南 3=西）；`apply(...)` 增一个 `StepPalette<T> steps` 参数；楼梯循环改为
   `slot=(top-1-y)&7; next=(slot+1)&7; facing 由 RING[slot]→RING[next] 的位移决定; sink.set(..., steps.step(facing));`
2. 该文件加静态辅助（**映射无关**：按 id 查方块 + 按属性名找 facing，避免引用宿主类导致 1.21.1 桥接编译失败）：
   ```java
   public static BlockState conwayStep(int facing) {
      Direction d = switch (facing) { case 1 -> Direction.EAST; case 2 -> Direction.SOUTH; case 3 -> Direction.WEST; default -> Direction.NORTH; };
      Block block = BuiltInRegistries.BLOCK.getValue(GuogaologyMod.id("conway_link"));
      BlockState state = block.defaultBlockState();
      for (Property<?> p : state.getProperties()) if ("facing".equals(p.getName())) return state.setValue((Property)p, d);
      return state;
   }
   ```
   ⚠️ 26.3 用 `Registry.getValue(Identifier)`；1.21.1 用 `get(...)` ⇒ 需在 `tools/prepare_outer_legacy.py` 的替换字典里加一条 `'BuiltInRegistries.BLOCK.getValue(': 'BuiltInRegistries.BLOCK.get('`
3. 两份生成器接线（`ports/common/main/dev/guogaology/outer/world/LhoChunkGenerator.java` 与 `ports/26.3/src/main/java/.../LhoChunkGenerator.java`）：把 `SurfaceShaftField.apply(...)` 调用补上最后参数 `SurfaceShaftField::conwayStep`
4. **blockstate**：`src/main/resources/assets/guogaology/blockstates/conway_link.json` 需 4 个变体
   `facing=east→y=0, south→y=90, west→y=180, north→y=270`（顶面贴图 y=0 时箭头朝东；贴图本身是右向箭头）
   ⚠️ 1.21.1 目标直接吃源资源、而它的方块没有 facing 属性 ⇒ **不要改源文件**，应在 `tools/prepare_port_resources.py` 里给现代目标（1.21.11/26.2/26.3）覆写这份 blockstate
5. 构建（见下）→ 三个位置部署 → 提交 + 推送

## 三、构建与部署（本沙箱专用，已全部打通）

```powershell
$py="C:\Users\HUAWEI\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe"
Set-Location "C:\Users\HUAWEI\Documents\deepseek-harness\default-workspace\guogaology"
& $py -X utf8 tools\prepare_port_sources.py 26.3          # 生成端口源码
& $py -X utf8 tools\prepare_port_resources.py 26.3 0.5.7  # 生成端口资源（含 26.3 数据修复）
& $py "C:\Users\HUAWEI\Documents\deepseek-harness\default-workspace\.toolchain\guogaology-offloom-263.py"  # off-loom 构建（javac+打包）
& $py -X utf8 tools\audit_resources.py --check ; & $py -X utf8 tools\audit_localization.py --check ; & $py -X utf8 tools\audit_outer_content.py
```
- 为什么不用 gradle/loom：本沙箱 **jdk zipfs 只读**，loom 的 jar 拆分/重映射必失败；off-loom 管线绕开它（脚本在 `.toolchain/`，**不在仓库里**，不污染仓库）
- 版本号只认 `gradle.properties` 的 `mod_version`（off-loom 脚本会自动读）

**部署三处**（需要 `danger-full-access` 批准；写前确认无 java 进程）：
1. `…\versions\26.3-Fabric 0.19.5\mods\`（用户实际在玩）
2. `…\versions\fabric-loader-0.19.5-26.3\mods\`
3. `C:\Users\HUAWEI\Downloads\`
备份目录：`.toolchain\deploy-backup-26.3\`

## 四、找「无穷降链」的工具（已建好）

- `.toolchain/shaftverify/ShaftScan.java` —— **用真实编译产物扫描某世界种子的全部孔位**（权威）
- `scratch/shaft_tools.py scan|verify <seed> <chunkX,chunkZ>` —— 运行上面两个探针
- `scratch/shaft_locate.py` —— 我复刻的 Python 版（**注意：与 Java 并非逐位一致，勿再使用**）
- 世界种子读法（26.3）：`saves/<世界>/data/minecraft/world_gen_settings.dat` 的 `data.seed`
  （level.dat 里已经没有种子了）
- 扫描结果参考：±640 格内 35 个孔位（≈21 个/km²）⇒ 不稀有，只是 3×3 的孔在地表不显眼
- 游戏内传送：`/execute in guogaology:outer run tp @s <中心X> ~ <中心Z>`（**别用中心正上方落体**，200 格深的井会摔进虚空；偏移 6~8 格走过去）

## 五、纪律提醒（见仓库 AGENTS.md，务必遵守）

- 四目标（1.21.1 / 1.21.11 / 26.2 / 26.3）都要能编译；本次改动只落在 ports/common（现代）+ 生成器，1.21.x 的 loom 构建在本沙箱**跑不了**（zipfs 限制），如实标注未验证
- 26.3 与现代目标的映射是 **mojmap**，1.21.1 主源码集是 **yarn**，1.21.1 桥接（ports/outer-1.21.1）是 **mojmap 且只编译 outer/**，其跨引用由 `tools/prepare_outer_legacy.py` 文本改写
- 生成物不许手改；改 `tools/` 里的生成器/转换器再重新生成
- 报告要短、要如实（未验证项必须写明）；收尾只跑一次闸门
