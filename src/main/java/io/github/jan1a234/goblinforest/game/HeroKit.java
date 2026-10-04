package io.github.jan1a234.goblinforest.game;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;

/**
 * Die Match-Ausrüstung des Häuptlings: Axt, Zauber und Fähigkeiten als Gegenstände in der Hotbar.
 * Rechtsklick wirkt sie, die Vanilla-Abklingzeit-Anzeige der Hotbar zeigt die Abklingzeit.
 * Alle Gegenstände tragen eine Markierung in {@code custom_data}, damit der Server sie erkennt.
 */
public final class HeroKit {
	public static final String TAG = "goblinforest_kit";

	public enum Slot {
		AXE("axe", 0, Items.IRON_AXE),
		FIREBALL("fireball", 1, Items.FIRE_CHARGE),
		HEALING("healing", 2, Items.RED_MUSHROOM),
		BLOODLUST("bloodlust", 3, Items.BLAZE_POWDER),
		BATTLE_SLAM("battleSlam", 4, Items.HEAVY_CORE),
		RALLY("rally", 5, Items.GOAT_HORN),
		MENU("menu", 8, Items.COMPASS);

		private final String id;
		private final int hotbarSlot;
		private final Item item;

		Slot(String id, int hotbarSlot, Item item) {
			this.id = id;
			this.hotbarSlot = hotbarSlot;
			this.item = item;
		}

		public String id() {
			return id;
		}

		public int hotbarSlot() {
			return hotbarSlot;
		}

		public Item item() {
			return item;
		}

		public static Slot byId(String id) {
			for (Slot slot : values()) {
				if (slot.id.equals(id)) {
					return slot;
				}
			}
			return null;
		}
	}

	private HeroKit() {
	}

	public static ItemStack create(Slot slot) {
		ItemStack stack = new ItemStack(slot.item());
		CompoundTag tag = new CompoundTag();
		tag.putString(TAG, slot.id());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_NAME, Component.translatable("item.goblinforest.kit." + slot.id()).withStyle(ChatFormatting.GOLD));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
				Component.translatable("item.goblinforest.kit." + slot.id() + ".desc").withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY)))));
		stack.set(DataComponents.MAX_STACK_SIZE, 1);
		if (slot == Slot.AXE) {
			// Schaden kommt aus den Häuptlingswerten, nicht aus der Axt selbst.
			stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
			stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
		}
		return stack;
	}

	private static ItemStack armor(Item item, TeamColor team) {
		ItemStack stack = new ItemStack(item);
		CompoundTag tag = new CompoundTag();
		tag.putString(TAG, "armor");
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.DYED_COLOR, new DyedItemColor(team.rgb()));
		stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
		return stack;
	}

	/** Kennung eines Ausrüstungsgegenstands oder null, wenn er nicht zur Match-Ausrüstung gehört. */
	public static String kitId(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		String id = data.copyTag().getStringOr(TAG, "");
		return id.isEmpty() ? null : id;
	}

	/** Ersetzt das komplette Inventar durch die Match-Ausrüstung. */
	public static void apply(ServerPlayer player, TeamColor team) {
		player.getInventory().clearContent();
		enforce(player, team);
		player.getInventory().setSelectedSlot(0);
	}

	/**
	 * Stellt die Ausrüstung wieder her, falls der Spieler Gegenstände verschoben oder weggeworfen hat,
	 * und entfernt alles Fremde. Gibt true zurück, wenn etwas korrigiert werden musste.
	 */
	public static boolean enforce(ServerPlayer player, TeamColor team) {
		Inventory inventory = player.getInventory();
		boolean changed = false;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack expected = expectedAt(i, inventory);
			ItemStack current = inventory.getItem(i);
			String expectedId = expected == null ? null : kitId(expected);
			String currentId = kitId(current);
			if (expectedId == null) {
				if (!current.isEmpty() && (currentId == null || !isArmorSlot(i, inventory))) {
					inventory.setItem(i, ItemStack.EMPTY);
					changed = true;
				}
			} else if (!expectedId.equals(currentId) || current.getItem() != expected.getItem()) {
				inventory.setItem(i, expected);
				changed = true;
			}
		}
		changed |= equip(player, EquipmentSlot.HEAD, armor(Items.LEATHER_HELMET, team));
		changed |= equip(player, EquipmentSlot.CHEST, armor(Items.LEATHER_CHESTPLATE, team));
		changed |= equip(player, EquipmentSlot.LEGS, armor(Items.LEATHER_LEGGINGS, team));
		changed |= equip(player, EquipmentSlot.FEET, armor(Items.LEATHER_BOOTS, team));
		if (!player.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty()) {
			player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
			changed = true;
		}
		return changed;
	}

	private static boolean isArmorSlot(int index, Inventory inventory) {
		return index >= Inventory.INVENTORY_SIZE;
	}

	private static ItemStack expectedAt(int index, Inventory inventory) {
		if (index >= Inventory.INVENTORY_SIZE) {
			return null;
		}
		for (Slot slot : Slot.values()) {
			if (slot.hotbarSlot() == index) {
				return create(slot);
			}
		}
		return null;
	}

	private static boolean equip(ServerPlayer player, EquipmentSlot slot, ItemStack stack) {
		ItemStack current = player.getItemBySlot(slot);
		if ("armor".equals(kitId(current)) && current.getItem() == stack.getItem()) {
			return false;
		}
		player.setItemSlot(slot, stack);
		return true;
	}
}
