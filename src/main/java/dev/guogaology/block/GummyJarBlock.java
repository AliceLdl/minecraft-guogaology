package dev.guogaology.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.TransparentBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * 软糖罐（{@code guogaology:gummy_jar}）：以原版陶罐（decorated_pot）为骨架的玻璃罐，
 * 内部装满彩色软糖；挖掉后由掉落表给 1~3 个随机软糖。
 *
 * <p>形状沿用原版陶罐实测的碰撞盒 {@code box(1,0,1,15,16,15)}（14×16×14，居中）。
 */
public final class GummyJarBlock extends TransparentBlock {
    public static final MapCodec<GummyJarBlock> CODEC = createCodec(GummyJarBlock::new);
    private static final VoxelShape SHAPE = createCuboidShape(1, 0, 1, 15, 16, 15);

    public GummyJarBlock(Settings settings) {
        super(settings);
    }

    @Override
    public MapCodec<GummyJarBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    protected float getAmbientOcclusionLightLevel(BlockState state, BlockView world, BlockPos pos) {
        return 1.0f; // 玻璃罐：不吃环境光遮蔽
    }
}
