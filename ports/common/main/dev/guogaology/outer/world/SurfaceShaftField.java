package dev.guogaology.outer.world;

import dev.guogaology.outer.GuogaologyMod;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * 大数表界地表群系的「无穷降链」——3×3 的孔，从地表一直通到虚空，
 * 内壁一圈一圈螺旋向下的楼梯（单块步阶，顺时针环绕 3×3 的 8 个边缘格）。
 *
 * <h2>用户要求</h2>
 * <ul>
 *   <li>仅在大数表界<b>地表群系</b>生成：候选孔位的地表群系
 *       <b>不得</b>是 {@code lho_void} / {@code lho_edge}（用户排除的两个）；
 *       其余地表群系（bms_mountains / christmas_forest / laver_table_plains /
 *       shocked_slump_land / underworld 等）都允许。</li>
 *   <li>形状：3×3 孔，从地表一直开到本区块的写入下界（含底面基岩凿穿 ⇒
 *       底部通向虚空），孔内壁一圈螺旋楼梯。</li>
 *   <li>楼梯：每层一整步、沿孔壁的 8 个边缘格顺时针走一格 —— 一步一格，
 *       既能下也能上（与普通玩家 1 格/步的攀爬判定吻合）。</li>
 *   <li>台阶材质：默认 {@link #conwayStep}（康威链岩），且每级台阶的朝向被设成
 *       <b>下一级（低一格）台阶所在的方向</b> ⇒ 贴图箭头永远指着往下的路
 *       （用户 0.5.7 要求；材质由调用方通过 {@link StepPalette} 注入）。</li>
 * </ul>
 *
 * <h2>生成节奏（确定性、无跨区块状态）</h2>
 * 每 {@link #CELL}（现为 160 格）×{@link #CELL} 的世界网格至多一根链：
 * 网格依据（世界种子、cellX/cellZ）哈希出本 cell 内的唯一落点（偏移限制在
 * {@link #MARGIN}~(CELL-{@link #MARGIN}) 区间内 ⇒ 不同 cell 相互离得足够远）；只有
 * <b>当孔位正好落进当前区块</b>（3×3 全在本区块内）时才写，不跨区块。
 *
 * <h2>形状写入</h2>
 * 顶面 anchor 的地表 Y 以下：3×3 全挖空（含基岩，⇒ 通向虚空）；井外一圈
 * <b>5×5 去掉 3×3 = 正好 16 格</b>全部填成本维度的石头（{@link LhoIslandField#M_ORDINAL_STONE}，
 * 与地表下的岩层同材质），从地表一直封到井底 —— 防止井与周边的洞穴/空腔联通
 * （用户要求）。井内壁每层一步压回序石螺旋楼梯。
 * 顶面 Y 以上不动（孔口即裸露地表）。
 *
 * <h2>可观测性</h2>
 * 由调用方 {@code LhoChunkGenerator} 打 {@code [SURFACE_SHAFT]} debug 日志。
 */
public final class SurfaceShaftField {
	private SurfaceShaftField() {
	}

	/** 世界网格尺寸：每 CELL×CELL 至多一根链（160 ⇒ 约 3.9/km²，比地表链密度低一档）。 */
	public static final int CELL = 160;
	/** 本 cell 内可能的落点离 cell 起点的最小/最大偏移（离 cell 边 ≥24 格 ⇒ 跨 cell 间距 ≥48 格）。 */
	public static final int MARGIN = 24;
	/** 顺时针 8 边缘格（3×3 的外围一圈，偏移相对孔右下角 (sx, sz)）。 */
	private static final int[][] RING = new int[][]{{0, 1}, {0, 0}, {1, 0}, {2, 0}, {2, 1}, {2, 2}, {1, 2}, {0, 2}};

	/**
	 * 表面探针：{@code hasData/top} 限定在<b>当前区块</b>内（越界 = 不写），
	 * {@code isVoidOrEdge} 直接读世界 quart 群系解析。三方法 ⇒ 不是函数式接口。
	 */
	public interface ShaftProbe {
		boolean hasData(int x, int z);

		int top(int x, int z);

		boolean isVoidOrEdge(int x, int y, int z);
	}

	/**
	 * 台阶材质工厂：给定朝向返回一个台阶方块状态。{@code facing} 取值
	 * {@code 0=北 1=东 2=南 3=西}（世界水平方向，见 {@link #stepFacing(int)}）。
	 */
	public interface StepPalette<T> {
		T step(int facing);
	}

	/** 顺时针 8 边缘格（3×3 的外围一圈，偏移相对孔右下角 (sx, sz)），供楼梯逐层取位。 */
	private static int ringIndex(int level) {
		return level & 7;
	}

	/**
	 * 第 {@code slot} 级台阶应该指向的朝向：由 {@link #RING} 里本级→下一级（低一格）的
	 * 实际位移推出，所以 RING 改了这里不会说错话。{@code 0=北(-Z) 1=东(+X) 2=南(+Z) 3=西(-X)}。
	 */
	private static int stepFacing(int slot) {
		int[] here = RING[slot & 7];
		int[] next = RING[(slot + 1) & 7];
		int dx = next[0] - here[0];
		int dz = next[1] - here[1];
		if (dz < 0) {
			return 0;
		}
		if (dx > 0) {
			return 1;
		}
		if (dz > 0) {
			return 2;
		}
		return 3;
	}

	/**
	 * 台阶的默认材质：康威链岩（{@code guogaology:conway_link}），箭头指向 {@code facing}
	 * （{@code 0=北 1=东 2=南 3=西}）。
	 *
	 * <p><b>刻意按 id + 属性名取方块，不引用宿主方块类</b>：本文件同时被 1.21.1 的
	 * 映射桥接编译，而 1.21.1 的康威链岩没有 {@code facing} 属性 ⇒ 找不到属性就返回默认
	 * 状态，该目标优雅退化成「无朝向的普通康威链岩」（不会为它引入跨映射的方块类引用）。
	 */
	public static BlockState conwayStep(int facing) {
		Direction direction = switch (facing) {
			case 1 -> Direction.EAST;
			case 2 -> Direction.SOUTH;
			case 3 -> Direction.WEST;
			default -> Direction.NORTH;
		};
		Block block = BuiltInRegistries.BLOCK.getValue(GuogaologyMod.id("conway_link"));
		BlockState state = block.defaultBlockState();
		for (Property<?> property : state.getProperties()) {
			if ("facing".equals(property.getName())) {
				return withFacing(state, property, direction);
			}
		}
		return state;
	}

	/** 定向写入统一收口在一处：raw {@code Property} 的泛型推断在四个目标上写法一致。 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static BlockState withFacing(BlockState state, Property<?> property, Direction direction) {
		return (BlockState)state.setValue((Property)property, direction);
	}

	private static long mix(long var0) {
		var0 += -2952168198444100760L;
		var0 = (var0 ^ var0 >>> 30) * 3691564373535L;
		var0 = (var0 ^ var0 >>> 27) * -5916209924272757118L;
		return var0 ^ var0 >>> 31;
	}

	/**
	 * 对当前区块做「无穷降链」写入。逐 cell 扫描（每 cell 随一个偏移的 3×3 孔），
	 * 只有孔位整体正好落入本区块时才写；返回写下的链数（0/1，区块较小则恒 ≤1）。
	 */
	public static <T> int apply(long seed, ChunkPos pos, int minY, int maxY, ShaftProbe probe,
			LhoIslandField.ColumnSink<T> sink, LhoIslandField.MaterialPalette<T> palette,
			StepPalette<T> steps) {
		if (minY > maxY) {
			return 0;
		}
		int minX = pos.getMinBlockX();
		int minZ = pos.getMinBlockZ();
		int built = 0;
		int cellX0 = Math.floorDiv(minX, CELL);
		int cellZ0 = Math.floorDiv(minZ, CELL);
		for (int cellX = cellX0; cellX <= cellX0 + 1; cellX++) {
			for (int cellZ = cellZ0; cellZ <= cellZ0 + 1; cellZ++) {
				long base = mix(seed ^ 0x736C_756D_7000_0001L);
				base = mix(base + cellX * 67277548617L);
				base = mix(base + cellZ * -4417276706812531889L);
				int offX = MARGIN + (int)(Math.floorMod(base, CELL - 2 * MARGIN));
				long base2 = mix(base + 0x51_5EB_5A_D1L);
				int offZ = MARGIN + (int)(Math.floorMod(base2, CELL - 2 * MARGIN));
				int sx = cellX * CELL + offX;
				int sz = cellZ * CELL + offZ;
				// 3×3 井 + 外面一圈 16 格石壳（共 5×5）必须整个落在本区块内
				// （否则不写；由"孔位属于谁谁写"，且避免越界写被 &15 折回对面）
				if (sx - 1 < minX || sx + 3 > minX + 15 || sz - 1 < minZ || sz + 3 > minZ + 15) {
					continue;
				}
				int cx = sx + 1;
				int cz = sz + 1;
				if (!probe.hasData(cx, cz)) {
					continue;
				}
				int top = probe.top(cx, cz);
				if (top == Integer.MIN_VALUE || top <= minY) {
					continue; // 地表就是虚空/无地形
				}
				if (probe.isVoidOrEdge(cx, top, cz)) {
					continue; // 用户排除的 lho_void / lho_edge
				}
				// ---- 石壳：3×3 洞周围一圈（5×5 去掉 3×3 = 正好 16 格）全填石，
				//      从地表一直封到井底，防止井与周边洞穴/空腔联通（用户要求）。----
				for (int y = top; y >= minY; y--) {
					for (int dx = -1; dx <= 3; dx++) {
						for (int dz = -1; dz <= 3; dz++) {
							if (dx >= 0 && dx <= 2 && dz >= 0 && dz <= 2) {
								continue; // 井本体留空
							}
							sink.set(sx + dx, y, sz + dz, palette.of(LhoIslandField.M_ORDINAL_STONE));
						}
					}
				}
				// ---- 挖：3×3 从地表往下直到本区块写入下界（基岩也挖 ⇒ 通向虚空）----
				for (int y = top; y >= minY; y--) {
					for (int dx = 0; dx < 3; dx++) {
						for (int dz = 0; dz < 3; dz++) {
							sink.set(sx + dx, y, sz + dz, palette.of(0));
						}
					}
				}
				// ---- 楼梯：顶面下一步开始，顺时针 8 边缘格每层一步；
				//      每级台阶的朝向 = 指向下一级（低一格）台阶的水平方向 ⇒ 箭头指路。----
				for (int y = top - 1; y > minY; y--) {
					int slot = (top - 1 - y) & 7;
					int ox = RING[slot][0];
					int oz = RING[slot][1];
					sink.set(sx + ox, y, sz + oz, steps.step(stepFacing(slot)));
				}
				built++;
			}
		}
		return built;
	}
}
