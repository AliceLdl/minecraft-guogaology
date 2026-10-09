package dev.guogaology.outer.world;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * 震惊瘫坐之地（{@code guogaology:shocked_slump_land}）的「局部海平面/盆地注水」
 * 噪声场 + 逐列判定（纯逻辑，不触碰任何注册表）。
 *
 * <h2>为什么单独一个类</h2>
 * 按群系改写地形必须走「自定义生成器后处理」（{@code noise_settings} 是每维度一份，
 * 无法按群系区分地形 —— 见 {@link LhoChunkGenerator}）。结构与 {@link LhoIslandField}
 * 成例相同：只依赖原版噪声数学（{@link NormalNoise}），<b>不引用 {@code ModBlocks}、
 * 不引用任何注册表</b>，所以可以用一个小 Java 程序对着真实 jar 直接编译运行
 * 做离线自检（工程侧 {@code scratch/SlumpWaterSelfCheck.java}）。
 *
 * <h2>方案（已由用户批准的「局部海平面」）</h2>
 * 该群系表面高度为 {@code h} 的列，只要 {@code h < }{@link #FLOOD_LEVEL}，
 * 就把 {@code (h, }{@link #FLOOD_LEVEL}{@code ]} 注成水（原版
 * {@code minecraft:water}，level 0 源态），并把海床顶面换成沙/沙砾。
 * 一切都是<b>逐列纯函数</b>：只依赖坐标 + 种子，没有任何跨区块可变状态
 * ⇒ 前后区块怎么生成顺序都得到逐位相同的结果，不会出现接缝。
 *
 * <h2>水位 L 为什么取 38</h2>
 * 由只读普查（在真实存档 14500 区块 / 740,999 目标群系列上推演）选定：
 * {@code L = 38} 时被淹列 ≈ 6.47%、新增水方块 ≈ 153k、
 * 最大连通片 XZ 跨度 ≈ 431 格 —— "有可观的湖面、但群系不会被淹成泽国"。
 *
 * <h2>边界防漏（逐列 8 邻域守门）</h2>
 * 注水列的每个 8 邻域邻居都必须满足其一：
 * <ol>
 *   <li>邻列也是本群系（自身会被同一套规则处理：要么注到同一水面、要么也守门失败）；</li>
 *   <li>邻列的列高 {@code ≥ }{@link #FLOOD_LEVEL}（本群系岸上 / 外族岸上的干地）；</li>
 *   <li>⚠️ <b>列高读不到（邻列在区块外）→</b> 若该邻列的 quart 群系<b>仍是本群系</b>
 *       （按水位 Y 的 quart 取样），它由邻区块自己的注水遍以<b>同一水位</b>处理、
 *       两边水面必然同 Y ⇒ 放行；若是<b>外族</b> ⇒ 无法核验 {@code h ≥ L} ⇒
 *       <b>放弃这一列</b>：多区块后处理的期间相邻区块多半还没生成地形，硬读必然
 *       读到假高度、造成区块间接缝 —— 宁可少淹，不许接缝。</li>
 * </ol>
 * 邻列若是虚空（整列空气，{@code top == Integer.MIN_VALUE}）也算"低于水位的外族"
 * ⇒ 不注（水贴虚空会变成贴免费的瀑布墙）。
 *
 * <h2>海床换沙/沙砾</h2>
 * 复用 {@link LhoIslandField#surfaceKind} 的<b>同一份斑块判据</b>
 * （同阈值 {@link LhoIslandField#SAND_THRESHOLD} / {@link LhoIslandField#GRAVEL_THRESHOLD}）
 * —— 换噪声场（不同盐）不换判据：{@code > 0.30 → 沙、< -0.28 → 沙砾、其余 → 沙}；
 * 只换海床<b>顶面 1 格</b>（最小侵入，"disk half_height ≤ 2" 的量级）。
 */
public final class SlumpWaterField {
	private SlumpWaterField() {
	}

	/**
	 * {@code minecraft:water}（原版水方块）的材质码：与 {@link LhoIslandField}
	 * 的 M_ 常量同一编码表的延续段（已有 0~8），由
	 * {@code LhoChunkGenerator#blockStateOf} 映射成 {@code Blocks.WATER.defaultBlockState()}。
	 */
	public static final int M_WATER = 9;

	/**
	 * 本群系的局部海平面（含）。列高 {@code h ≥ 38} 的列一个方块都不动。
	 */
	public static final int FLOOD_LEVEL = 38;

	/** 海床斑块噪声的盐（ASCII {@code "slp_bed1"}，与 {@link LhoIslandField} 各盐错开）。 */
	private static final long BED_SEED_SALT = 0x736C_705F_6265_6431L;
	/** 海床斑块噪声：基准八度 -5、两层八度 → "成片"而不是零散单格（与岛顶斑块同参数习惯）。 */
	private static final int BED_BASE_OCTAVE = -5;
	private static final int BED_OCTAVE_COUNT = 2;
	private static final double BED_BASE_AMPLITUDE = 1.0;

	/** 海床斑块噪声场：同一世界种子永远同一噪声场（确定性 ✓）。 */
	public static NormalNoise createBedNoise(long worldSeed) {
		return NormalNoise.create(RandomSource.create(worldSeed ^ BED_SEED_SALT),
				BED_BASE_OCTAVE, BED_BASE_AMPLITUDE, BED_BASE_AMPLITUDE);
	}

	/**
	 * 这一列的海床材质码：{@link LhoIslandField#M_SAND} / {@link LhoIslandField#M_GRAVEL}。
	 * 斑块判据直接复用 {@link LhoIslandField#surfaceKind}（同阈值），沙以外的档归沙。
	 */
	public static int bedMaterial(NormalNoise bedNoise, int x, int z) {
		int kind = LhoIslandField.surfaceKind(bedNoise, x, z);
		return kind == LhoIslandField.SURFACE_GRAVEL
				? LhoIslandField.M_GRAVEL : LhoIslandField.M_SAND;
	}

	/**
	 * 真实群系解析出口：实现必须走世界 quart 群系解析
	 * （生产代码由 {@code LhoChunkGenerator.WorldBiomeGrid#isShockedSlump} 提供），
	 * <b>不能</b>用 {@code ChunkAccess#getNoiseBiome}（越出本区块会绕回自己）。
	 */
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
	 * 这是"其它群系逐位一致"的结构性保证；把 {@code floodLevel} 传
	 * {@link Integer#MIN_VALUE} 即为"整遍关闭"（阴证：与改动前逐位一致）。
	 *
	 * @return 注了水的列数（0 表示本区块一列都没被注）
	 */
	public static <T> long apply(ChunkPos pos, int minY, int maxY, NormalNoise bedNoise,
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
