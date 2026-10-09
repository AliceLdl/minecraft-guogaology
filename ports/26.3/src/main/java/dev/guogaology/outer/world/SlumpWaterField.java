package dev.guogaology.outer.world;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.synth.Noise;

/**
 * 震惊瘫坐之地（{@code guogaology:shocked_slump_land}）的「局部海平面/盆地注水」
 * 噪声场 + 逐列判定（纯逻辑，不触碰任何注册表）。26.3 端口 {@code Noise} 版。
 *
 * <h2>方案（已由用户批准的「局部海平面」）</h2>
 * 该群系表面高度为 {@code h} 的列，只要 {@code h < }{@link #FLOOD_LEVEL}，
 * 就把 {@code (h, }{@link #FLOOD_LEVEL}{@code ]} 注成水（原版
 * {@code minecraft:water}，level 0 源态），并把海床顶面换成沙/沙砾。
 * 一切都是<b>逐列纯函数</b>：只依赖坐标 + 种子，没有任何跨区块可变状态
 * ⇒ 前后区块怎么生成顺序都得到逐位相同的结果，不会出现接缝。
 *
 * <h2>水位 L 为什么取 38</h2>
 * 由只读普查（真实存档 14500 区块 / 740,999 目标群系列）选定：
 * {@code L = 38} 时被淹列 ≈ 6.47%、新增水方块 ≈ 153k、
 * 最大连通片 XZ 跨度 ≈ 431 格。
 *
 * <h2>边界防漏（逐列 8 邻域守门）</h2>
 * 注水列的每个 8 邻域邻居都必须满足其一：本群系邻列 / 邻列高 ≥ 水位 /
 * 邻列读不到时按水位 quart 取样仍是本群系；否则放弃（宁可少淹，不许接缝）。
 *
 * <h2>海床换沙/沙砾</h2>
 * 复用 {@link LhoIslandField#surfaceKind} 的同一份斑块判据
 * （同阈值 {@link LhoIslandField#SAND_THRESHOLD} / {@link LhoIslandField#GRAVEL_THRESHOLD}），
 * 换噪声场（不同盐）不换判据；只换海床<b>顶面 1 格</b>。
 */
public final class SlumpWaterField {
	private SlumpWaterField() {
	}

	/** {@code minecraft:water} 的材质码（M_ 编码延续段），由 blockStateOf 映射。 */
	public static final int M_WATER = 9;

	/** 本群系的局部海平面（含）。列高 {@code h ≥ 38} 的列一个方块都不动。 */
	public static final int FLOOD_LEVEL = 38;

	/** 海床斑块噪声的盐（ASCII {@code "slp_bed1"}，与 {@link LhoIslandField} 各盐错开）。 */
	private static final long BED_SEED_SALT = 0x736C_705F_6265_6431L;
	/** 海床斑块噪声：基准八度 -5、两层八度 → "成片"而不是零散单格。 */
	private static final int BED_BASE_OCTAVE = -5;
	private static final int BED_OCTAVE_COUNT = 2;
	private static final double BED_BASE_AMPLITUDE = 1.0;

	/** 海床斑块噪声场：同一世界种子永远同一噪声场（确定性 ✓）。 */
	public static Noise createBedNoise(long worldSeed) {
		return NormalNoise.builder()
				.setBaseOctave(BED_BASE_OCTAVE)
				.setOctaveCount(BED_OCTAVE_COUNT)
				.setBaseAmplitude(BED_BASE_AMPLITUDE)
				.setNormalize(true)
				.build()
				.create(RandomSource.create(worldSeed ^ BED_SEED_SALT));
	}

	/**
	 * 这一列的海床材质码：{@link LhoIslandField#M_SAND} / {@link LhoIslandField#M_GRAVEL}。
	 * 斑块判据直接复用 {@link LhoIslandField#surfaceKind}，沙以外的档归沙。
	 */
	public static int bedMaterial(Noise bedNoise, int x, int z) {
		int kind = LhoIslandField.surfaceKind(bedNoise, x, z);
		return kind == LhoIslandField.SURFACE_GRAVEL
				? LhoIslandField.M_GRAVEL : LhoIslandField.M_SAND;
	}

	/** 真实群系解析出口：必须走世界 quart 群系解析，不能用 ChunkAccess#getNoiseBiome。 */
	@FunctionalInterface
	public interface SlumpBiomeProbe {
		boolean isShocked(int x, int y, int z);
	}

	/**
	 * 列高读取出口：与 {@link LhoIslandField.ColumnTop#top} 同语义，外加
	 * {@link #hasData}（这一列高度<b>可不可信</b>：区块内真、区块外恒假）。
	 */
	public interface SlumpColumnProbe extends LhoIslandField.ColumnTop {
		boolean hasData(int x, int z);
	}

	/** 注水遍的运行时统计（每区块一个；日志/回放对账用）。 */
	public static final class SlumpStats {
		/** 判为本群系且 {@code h < L} 的候选列数（防漏前）。 */
		public long candidates;
		/** 真正注了水的列数（{@code candidates - boundaryRefused}）。 */
		public long floodedColumns;
		/** 注进的水方块总数（Σ(L − h)）。 */
		public long waterCells;
		/** 海床换沙的格数。 */
		public long sandBedCells;
		/** 海床换沙砾的格数。 */
		public long gravelBedCells;
		/** 因边界防漏被放弃的候选列数。 */
		public long boundaryRefused;
	}

	/**
	 * 对一个区块内全部 16×16 列做「局部海平面注水」。
	 *
	 * <p><b>非本群系的列一个方块都不写</b>（群系预筛在每列第一件事）——
	 * 把 {@code floodLevel} 传 {@link Integer#MIN_VALUE} 即为"整遍关闭"（阴证）。
	 *
	 * @return 注了水的列数（0 表示本区块一列都没被注）
	 */
	public static <T> long apply(ChunkPos pos, int minY, int maxY, Noise bedNoise,
			SlumpBiomeProbe biome, SlumpColumnProbe columns, LhoIslandField.ColumnSink<T> sink,
			LhoIslandField.MaterialPalette<T> palette, int floodLevel, SlumpStats stats) {
		if (minY > maxY) {
			return 0;
		}
		int minX = pos.getMinBlockX();
		int minZ = pos.getMinBlockZ();
		int topFill = Math.min(floodLevel, maxY);
		long flooded = 0;
		long waterCells = 0;
		long sandBeds = 0;
		long gravelBeds = 0;
		for (int x = minX; x < minX + 16; x++) {
			for (int z = minZ; z < minZ + 16; z++) {
				if (!columns.hasData(x, z)) {
					continue; // 列高读不到：宁可少淹，不许猜
				}
				int h = columns.top(x, z);
				if (h == Integer.MIN_VALUE || h < minY || h >= topFill) {
					continue; // 虚空列 / h ≥ L：不处理
				}
				// 群系预筛：按海床顶面那一格的真实群系判。
				if (!biome.isShocked(x, h, z)) {
					continue;
				}
				if (stats != null) {
					stats.candidates++;
				}
				// ---- 边界防漏：8 邻域守门（与注水状态无关 ⇒ 纯函数 ✓）----
				boolean gate = true;
				for (int dx = -1; dx <= 1 && gate; dx++) {
					for (int dz = -1; dz <= 1 && gate; dz++) {
						if (dx == 0 && dz == 0) {
							continue;
						}
						int nx = x + dx;
						int nz = z + dz;
						if (!columns.hasData(nx, nz)) {
							// 邻列高度读不到：本群系（按水位 quart 取样）⇒ 放行；
							// 外族 ⇒ 无法核验 "h ≥ L" ⇒ 放弃本列。
							if (biome.isShocked(nx, topFill, nz)) {
								continue;
							}
							gate = false;
							break;
						}
						int nh = columns.top(nx, nz);
						if (biome.isShocked(nx, nh == Integer.MIN_VALUE ? minY : nh, nz)) {
							continue; // 本群系邻列：由同一套规则自行处理
						}
						if (nh == Integer.MIN_VALUE || nh < topFill) {
							// 非本群系邻列且低于局部海平面 ⇒ 放弃
							gate = false;
							break;
						}
					}
				}
				if (!gate) {
					if (stats != null) {
						stats.boundaryRefused++;
					}
					continue;
				}
				// ---- 注水：海床顶面换沙/沙砾，再把 (h, topFill] 注成水 ----
				int bed = bedMaterial(bedNoise, x, z);
				sink.set(x, h, z, palette.of(bed));
				if (bed == LhoIslandField.M_SAND) {
					sandBeds++;
				} else {
					gravelBeds++;
				}
				for (int y = h + 1; y <= topFill; y++) {
					sink.set(x, y, z, palette.of(M_WATER));
				}
				waterCells += topFill - h;
				flooded++;
			}
		}
		if (stats != null) {
			stats.floodedColumns = flooded;
			stats.waterCells = waterCells;
			stats.sandBedCells = sandBeds;
			stats.gravelBedCells = gravelBeds;
		}
		return flooded;
	}
}
