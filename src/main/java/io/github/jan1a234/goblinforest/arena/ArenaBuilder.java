package io.github.jan1a234.goblinforest.arena;

import io.github.jan1a234.goblinforest.arena.ArenaBlueprint.Material;
import io.github.jan1a234.goblinforest.game.TeamColor;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import static io.github.jan1a234.goblinforest.arena.ArenaLayout.HALF_LENGTH;
import static io.github.jan1a234.goblinforest.arena.ArenaLayout.HALF_WIDTH;

/**
 * Setzt den {@link ArenaBlueprint} in der Arena-Dimension Block für Block um, verteilt über mehrere Ticks,
 * damit der Server dabei nicht hängt. Danach werden Zäune, Mauern und Gitter miteinander verbunden.
 */
public final class ArenaBuilder {
	/** Block-Updates: an Clients senden, aber keine Nachbar-Reaktionen (kein fließendes Wasser, kein Laubzerfall). */
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
	private static final int SLICES_PER_TICK = 8;

	private static ArenaBlueprint blueprint;

	private final ServerLevel level;
	private final List<BlockPos> connectables = new ArrayList<>();
	private int nextX = -HALF_LENGTH;
	private int connectIndex;

	public ArenaBuilder(ServerLevel level) {
		this.level = level;
	}

	public static synchronized ArenaBlueprint blueprint() {
		if (blueprint == null) {
			blueprint = ArenaBlueprint.create();
		}
		return blueprint;
	}

	public boolean done() {
		return nextX > HALF_LENGTH && connectIndex >= connectables.size();
	}

	/** Fortschritt von 0 bis 1 für die Anzeige. */
	public double progress() {
		double slices = (double) (nextX + HALF_LENGTH) / (2 * HALF_LENGTH + 1);
		return Math.min(1.0, slices * 0.95 + (connectables.isEmpty() ? 0 : 0.05 * connectIndex / connectables.size()));
	}

	/** Baut den nächsten Abschnitt. Gibt true zurück, sobald alles fertig ist. */
	public boolean tick() {
		ArenaBlueprint plan = blueprint();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int slice = 0; slice < SLICES_PER_TICK && nextX <= HALF_LENGTH; slice++, nextX++) {
			int x = nextX;
			for (int z = -HALF_WIDTH; z <= HALF_WIDTH; z++) {
				for (int y = ArenaBlueprint.MIN_Y; y <= ArenaBlueprint.MAX_Y; y++) {
					Material material = plan.get(x, y, z);
					pos.set(x, y, z);
					BlockState state = state(material, ArenaBlueprint.sideOf(x));
					if (level.getBlockState(pos) != state) {
						level.setBlock(pos, state, FLAGS);
					}
					if (isConnectable(material)) {
						connectables.add(pos.immutable());
					}
				}
			}
		}
		if (nextX > HALF_LENGTH) {
			int end = Math.min(connectables.size(), connectIndex + 2000);
			for (; connectIndex < end; connectIndex++) {
				connect(connectables.get(connectIndex));
			}
		}
		return done();
	}

	/** Setzt einzelne Blöcke sofort (Turmruine, Risse im Festungskern). */
	public static void apply(ServerLevel level, List<ArenaBlueprint.Placement> placements) {
		List<BlockPos> toConnect = new ArrayList<>();
		for (ArenaBlueprint.Placement placement : placements) {
			BlockPos pos = new BlockPos(placement.x(), placement.y(), placement.z());
			BlockState state = state(placement.material(), ArenaBlueprint.sideOf(placement.x()));
			if (level.getBlockState(pos) != state) {
				level.setBlock(pos, state, FLAGS);
			}
			if (isConnectable(placement.material())) {
				toConnect.add(pos);
			}
		}
		ArenaBuilder helper = new ArenaBuilder(level);
		toConnect.forEach(helper::connect);
	}

	private void connect(BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		BlockState connected = Block.updateFromNeighbourShapes(state, level, pos);
		if (connected != state) {
			level.setBlock(pos, connected, FLAGS);
		}
	}

	private static boolean isConnectable(Material material) {
		return switch (material) {
			case SPRUCE_FENCE, DARK_OAK_FENCE, COBBLESTONE_WALL, IRON_BARS -> true;
			default -> false;
		};
	}

	private static final Map<Material, BlockState> RED = new EnumMap<>(Material.class);
	private static final Map<Material, BlockState> GREEN = new EnumMap<>(Material.class);

	/** Blockzustand für ein Material; Clanfarben hängen von der Seite ab. */
	public static BlockState state(Material material, TeamColor side) {
		Map<Material, BlockState> cache = side == TeamColor.RED ? RED : GREEN;
		BlockState state = cache.get(material);
		if (state == null) {
			state = resolve(material, side);
			cache.put(material, state);
		}
		return state;
	}

	private static BlockState leaves(Block block) {
		return block.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
	}

	private static BlockState resolve(Material material, TeamColor side) {
		return switch (material) {
			case AIR -> Blocks.AIR.defaultBlockState();
			case BARRIER -> Blocks.BARRIER.defaultBlockState();
			case BEDROCK -> Blocks.BEDROCK.defaultBlockState();
			case STONE -> Blocks.STONE.defaultBlockState();
			case DIRT -> Blocks.DIRT.defaultBlockState();
			case GRASS -> Blocks.GRASS_BLOCK.defaultBlockState();
			case MOSS -> Blocks.MOSS_BLOCK.defaultBlockState();
			case PODZOL -> Blocks.PODZOL.defaultBlockState();
			case COARSE_DIRT -> Blocks.COARSE_DIRT.defaultBlockState();
			case ROOTED_DIRT -> Blocks.ROOTED_DIRT.defaultBlockState();
			case DIRT_PATH -> Blocks.DIRT_PATH.defaultBlockState();
			case GRAVEL -> Blocks.GRAVEL.defaultBlockState();
			case MUD -> Blocks.MUD.defaultBlockState();
			case WATER -> Blocks.WATER.defaultBlockState();
			case SPRUCE_PLANKS -> Blocks.SPRUCE_PLANKS.defaultBlockState();
			case SPRUCE_LOG -> Blocks.SPRUCE_LOG.defaultBlockState();
			case SPRUCE_FENCE -> Blocks.SPRUCE_FENCE.defaultBlockState();
			case SPRUCE_SLAB -> Blocks.SPRUCE_SLAB.defaultBlockState();
			case DARK_OAK_LOG -> Blocks.DARK_OAK_LOG.defaultBlockState();
			case DARK_OAK_FENCE -> Blocks.DARK_OAK_FENCE.defaultBlockState();
			case DARK_OAK_LEAVES -> leaves(Blocks.DARK_OAK_LEAVES);
			case OAK_LEAVES -> leaves(Blocks.OAK_LEAVES);
			case SPRUCE_LEAVES -> leaves(Blocks.SPRUCE_LEAVES);
			case AZALEA_LEAVES -> leaves(Blocks.AZALEA_LEAVES);
			case STONE_BRICKS -> Blocks.STONE_BRICKS.defaultBlockState();
			case MOSSY_STONE_BRICKS -> Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
			case CRACKED_STONE_BRICKS -> Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
			case CHISELED_STONE_BRICKS -> Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
			case COBBLESTONE -> Blocks.COBBLESTONE.defaultBlockState();
			case MOSSY_COBBLESTONE -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
			case COBBLESTONE_WALL -> Blocks.COBBLESTONE_WALL.defaultBlockState();
			case POLISHED_BLACKSTONE_BRICKS -> Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
			case CRACKED_POLISHED_BLACKSTONE_BRICKS -> Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
			case CHISELED_POLISHED_BLACKSTONE -> Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
			case GILDED_BLACKSTONE -> Blocks.GILDED_BLACKSTONE.defaultBlockState();
			case MUD_BRICKS -> Blocks.MUD_BRICKS.defaultBlockState();
			case PACKED_MUD -> Blocks.PACKED_MUD.defaultBlockState();
			case CRACKED_DEEPSLATE_BRICKS -> Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
			case MAGMA_BLOCK -> Blocks.MAGMA_BLOCK.defaultBlockState();
			case SHROOMLIGHT -> Blocks.SHROOMLIGHT.defaultBlockState();
			case LANTERN -> Blocks.LANTERN.defaultBlockState();
			case HAY_BLOCK -> Blocks.HAY_BLOCK.defaultBlockState();
			case IRON_BARS -> Blocks.IRON_BARS.defaultBlockState();
			case SHORT_GRASS -> Blocks.SHORT_GRASS.defaultBlockState();
			case FERN -> Blocks.FERN.defaultBlockState();
			case RED_MUSHROOM -> Blocks.RED_MUSHROOM.defaultBlockState();
			case BROWN_MUSHROOM -> Blocks.BROWN_MUSHROOM.defaultBlockState();
			case LILY_PAD -> Blocks.LILY_PAD.defaultBlockState();
			case MOSS_CARPET -> Blocks.MOSS_CARPET.defaultBlockState();
			case RED_MUSHROOM_BLOCK -> Blocks.RED_MUSHROOM_BLOCK.defaultBlockState();
			case MUSHROOM_STEM -> Blocks.MUSHROOM_STEM.defaultBlockState();
			case TEAM_ACCENT -> Blocks.WOOL.get(side == TeamColor.RED ? DyeColor.RED : DyeColor.GREEN).defaultBlockState();
		};
	}
}
