package dev.guogaology.block;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * 康威链岩（{@code guogaology:conway_link}）：四个水平朝向的箭头方块。
 *
 * <p>贴图上的箭头始终指向 {@link #FACING}（手工放置时朝向玩家，与箱子/熔炉同一手感）。
 * 「无穷降链」的螺旋楼梯用它铺台阶，生成器会把 FACING 设为
 * <b>下一级台阶（低一格）所在的方向</b> ⇒ 箭头永远指着往下的路。
 *
 * <p>朝向映射（blockstate 的 {@code y} 旋转以<b>顶面</b>贴图为准：
 * {@code y=0} 时箭头指向东）：
 * <pre>
 *   facing=east  → y=0     facing=south → y=90
 *   facing=west  → y=180   facing=north → y=270
 * </pre>
 */
public class ConwayLinkBlock extends Block {
   public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

   public ConwayLinkBlock(BlockBehaviour.Properties var1) {
      super(var1);
      // 默认 = 贴图原样（箭头朝东），物品栏图标因此与贴图一致
      this.registerDefaultState((BlockState)this.defaultBlockState().setValue(FACING, Direction.EAST));
   }

   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> var1) {
      var1.add(new Property[]{FACING});
   }

   public BlockState getStateForPlacement(BlockPlaceContext var1) {
      return (BlockState)this.defaultBlockState().setValue(FACING, var1.getHorizontalDirection().getOpposite());
   }
}
