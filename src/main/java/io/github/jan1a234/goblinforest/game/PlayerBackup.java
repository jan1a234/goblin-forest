package io.github.jan1a234.goblinforest.game;

import io.github.jan1a234.goblinforest.GoblinForest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;

/**
 * Sichert Inventar, Erfahrung, Spielmodus und Position eines Spielers vor dem Match auf der Festplatte
 * und stellt alles danach wieder her. Weil die Sicherung als Datei liegt, übersteht sie auch einen Server-Absturz:
 * beim nächsten Einloggen wird sie automatisch zurückgespielt.
 */
public final class PlayerBackup {
	private PlayerBackup() {
	}

	private static Path file(MinecraftServer server, UUID uuid) {
		return server.getWorldPath(LevelResource.ROOT).resolve(GoblinForest.MOD_ID).resolve("backups").resolve(uuid + ".dat");
	}

	public static boolean exists(MinecraftServer server, UUID uuid) {
		return Files.exists(file(server, uuid));
	}

	public static void save(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, server.registryAccess());
		player.getInventory().save(out.list("Inventory", ItemStackWithSlot.CODEC));
		out.putInt("SelectedSlot", player.getInventory().getSelectedSlot());
		out.putInt("XpLevel", player.experienceLevel);
		out.putFloat("XpProgress", player.experienceProgress);
		out.putInt("XpTotal", player.totalExperience);
		out.putInt("GameMode", player.gameMode().getId());
		out.putFloat("Health", player.getHealth());
		out.putInt("Food", player.getFoodData().getFoodLevel());
		out.putFloat("Saturation", player.getFoodData().getSaturationLevel());
		out.putString("Dimension", player.level().dimension().toString());
		out.putDouble("X", player.getX());
		out.putDouble("Y", player.getY());
		out.putDouble("Z", player.getZ());
		out.putFloat("Yaw", player.getYRot());
		out.putFloat("Pitch", player.getXRot());
		CompoundTag tag = out.buildResult();
		Path path = file(server, player.getUUID());
		try {
			Files.createDirectories(path.getParent());
			NbtIo.write(tag, path);
		} catch (IOException e) {
			GoblinForest.LOGGER.error("Konnte Inventar von {} nicht sichern", player.getScoreboardName(), e);
		}
	}

	/** Schickt einen Spieler ohne Sicherung an den Ursprung der Oberwelt. */
	public static void sendHome(ServerPlayer player) {
		ServerLevel overworld = player.level().getServer().overworld();
		int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
		player.teleport(new TeleportTransition(overworld, new Vec3(0.5, y, 0.5), Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
	}

	/**
	 * Spielt die Sicherung zurück und löscht die Datei. Ohne Sicherung landet der Spieler am Weltspawn im Überlebensmodus.
	 */
	public static void restore(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Path path = file(server, player.getUUID());
		CompoundTag tag = null;
		if (Files.exists(path)) {
			try {
				tag = NbtIo.read(path);
			} catch (IOException e) {
				GoblinForest.LOGGER.error("Konnte Sicherung von {} nicht lesen", player.getScoreboardName(), e);
			}
		}
		player.getInventory().clearContent();
		if (tag == null) {
			player.setGameMode(GameType.SURVIVAL);
			sendHome(player);
			return;
		}
		ValueInput in = TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), tag);
		player.getInventory().load(in.listOrEmpty("Inventory", ItemStackWithSlot.CODEC));
		player.getInventory().setSelectedSlot(in.getIntOr("SelectedSlot", 0));
		player.setGameMode(GameType.byId(in.getIntOr("GameMode", GameType.SURVIVAL.getId())));
		player.experienceLevel = in.getIntOr("XpLevel", 0);
		player.experienceProgress = in.getFloatOr("XpProgress", 0);
		player.totalExperience = in.getIntOr("XpTotal", 0);
		player.getFoodData().setFoodLevel(in.getIntOr("Food", 20));
		player.getFoodData().setSaturation(in.getFloatOr("Saturation", 5));
		String dimension = in.getStringOr("Dimension", "");
		ServerLevel target = server.overworld();
		for (ServerLevel level : server.getAllLevels()) {
			if (level.dimension().toString().equals(dimension)) {
				target = level;
			}
		}
		Vec3 position = new Vec3(in.getDoubleOr("X", 0.5), in.getDoubleOr("Y", 100), in.getDoubleOr("Z", 0.5));
		player.teleport(new TeleportTransition(target, position, Vec3.ZERO, in.getFloatOr("Yaw", 0), in.getFloatOr("Pitch", 0), TeleportTransition.DO_NOTHING));
		player.setHealth(Math.max(1f, Math.min(player.getMaxHealth(), in.getFloatOr("Health", 20))));
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			GoblinForest.LOGGER.warn("Konnte Sicherung {} nicht löschen", path, e);
		}
	}
}
