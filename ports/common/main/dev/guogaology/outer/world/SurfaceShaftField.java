package dev.guogaology.outer.world;

import net.minecraft.world.level.ChunkPos;

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
 * </ul>
 *
 * <h2>生成节奏（确定性、无跨区块状态）</h2>
 * 每 {@link #CELL}（现为 160 格）×{@link #CELL} 的世界网格至多一根链：
 * 网格依据（世界种子、cellX/cellZ）哈希出本 cell 内的唯一落点（偏移限制在
 * {@link #MARGIN}~(CELL-{@link #MARGIN}) 区间内 ⇒ 不同 cell 相互离得足够远）；只有
 * <b>当孔位正好落进当前区块</b>（3×3 全在本区块内）时才写，不跨区块。
 *
 * <h2>形状写入</h2>
 * 顶面 anchor 的地表 Y 以下：3×3 全挖空（含基岩），把每层的"步阶"压回
 * {@link LhoIslandField#M_ORDINAL_STONE}（与 LHO 边缘坡体同一序石语义，不新加方块）。
 * 顶面 Y 以上一格不动（孔口即裸露地表，不另铺围栏 —— 用户未要求）。
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

	/** 顺时针 8 边缘格（3×3 的外围一圈，偏移相对孔右下角 (sx, sz)），供楼梯逐层取位。 */
	private static int ringIndex(int level) {
		return level & 7;
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
			LhoIslandField.ColumnSink<T> sink, LhoIslandField.MaterialPalette<T> palette) {
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
				// 3×3 必须整个落在本区块内（否则不写；由"孔位属于谁谁写"）
				if (sx - 1 < minX || sx + 1 > minX + 15 || sz - 1 < minZ || sz + 1 > minZ + 15) {
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
				// ---- 挖：3×3 从地表往下直到本区块写入下界（基岩也挖 ⇒ 通向虚空）----
				for (int y = top; y >= minY; y--) {
					for (int dx = 0; dx < 3; dx++) {
						for (int dz = 0; dz < 3; dz++) {
							sink.set(sx + dx, y, sz + dz, palette.of(0));
						}
					}
				}
				// ---- 楼梯：顶面下一步开始，顺时针 8 边缘格每层一步（序石）----
				for (int y = top - 1; y > minY; y--) {
					int slot = (top - 1 - y) & 7;
					int ox = RING[slot][0];
					int oz = RING[slot][1];
					sink.set(sx + ox, y, sz + oz, palette.of(LhoIslandField.M_ORDINAL_STONE));
				}
				built++;
			}
		}
		return built;
	}
}
