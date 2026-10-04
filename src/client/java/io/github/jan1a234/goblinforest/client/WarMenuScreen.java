package io.github.jan1a234.goblinforest.client;

import io.github.jan1a234.goblinforest.game.PurchaseResult;
import io.github.jan1a234.goblinforest.game.Stance;
import io.github.jan1a234.goblinforest.net.ActionPayload;
import io.github.jan1a234.goblinforest.net.MatchStatePayload;
import io.github.jan1a234.goblinforest.unit.UnitType;
import io.github.jan1a234.goblinforest.upgrade.UpgradeType;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Das Kriegsmenü (Taste B oder Kompass): Einheiten rekrutieren, Upgrades und Festung ausbauen, Zauber verbessern
 * und die Haltung der Armee wählen. Das Spiel läuft weiter, während es offen ist.
 */
public class WarMenuScreen extends Screen {
	private static final int WIDTH = 344;
	private static final int HEIGHT = 216;
	private static final int GOLD = 0xFFF2C744;
	private static final int TEXT = 0xFFE8E0C8;
	private static final int MUTED = 0xFFA09A88;

	private enum Tab {
		UNITS, UPGRADES, STRONGHOLD, MAGIC
	}

	private static Tab lastTab = Tab.UNITS;

	/** Text, der im Menü gezeichnet wird (Beschriftungen neben den Knöpfen). */
	private record Label(Component text, int x, int y, int color) {
	}

	private final List<Runnable> refreshers = new ArrayList<>();
	private final List<Label> labels = new ArrayList<>();
	private Tab tab = lastTab;
	private int left;
	private int top;

	public WarMenuScreen() {
		super(Component.translatable("menu.goblinforest.title"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		refreshers.clear();
		labels.clear();
		left = (width - WIDTH) / 2;
		top = (height - HEIGHT) / 2;
		int tabWidth = (WIDTH - 16) / Tab.values().length;
		for (Tab candidate : Tab.values()) {
			Button button = Button.builder(Component.translatable("menu.goblinforest.tab." + candidate.name().toLowerCase()), b -> {
				tab = candidate;
				lastTab = candidate;
				rebuildWidgets();
			}).bounds(left + 8 + candidate.ordinal() * tabWidth, top + 22, tabWidth - 2, 18).build();
			button.active = candidate != tab;
			addRenderableWidget(button);
		}
		int y = top + 48;
		switch (tab) {
			case UNITS -> initUnits(y);
			case UPGRADES -> initUpgrades(y);
			case STRONGHOLD -> initStronghold(y);
			case MAGIC -> initMagic(y);
		}
		refresh();
	}

	private void initUnits(int y) {
		for (UnitType type : UnitType.values()) {
			String id = "recruit:" + type.id();
			labels.add(new Label(Component.translatable(type.translationKey()).withStyle(ChatFormatting.BOLD), left + 12, y + 1, TEXT));
			labels.add(new Label(Component.translatable("menu.goblinforest.unit." + type.id() + ".desc"), left + 12, y + 11, MUTED));
			int index = type.ordinal();
			shopButton(id, left + WIDTH - 124, y + 2, 112, entry -> {
				int alive = ClientMatchState.get().unitCounts().length > index ? ClientMatchState.get().unitCounts()[index] : 0;
				String key = ModKeys.label(id);
				return Component.translatable("menu.goblinforest.recruit", entry.cost(), key.isEmpty() ? "" : " [" + key + "]")
						.append(Component.literal("  ×" + alive).withStyle(ChatFormatting.GRAY));
			});
			y += 26;
		}
		labels.add(new Label(Component.translatable("menu.goblinforest.stance"), left + 12, y + 6, GOLD));
		int x = left + 80;
		for (Stance stance : Stance.values()) {
			Button button = Button.builder(Component.translatable(stance.translationKey()), b -> send("stance:" + stance.id()))
					.bounds(x, y + 1, 82, 18)
					.tooltip(Tooltip.create(Component.translatable("menu.goblinforest.stance." + stance.id() + ".desc")))
					.build();
			addRenderableWidget(button);
			refreshers.add(() -> button.active = ClientMatchState.get().stance() != stance.ordinal());
			x += 86;
		}
	}

	private void initUpgrades(int y) {
		UpgradeType[] columns = {UpgradeType.ARMOR, UpgradeType.ATTACK, UpgradeType.RANGE};
		int columnX = left + 100;
		int columnWidth = 78;
		for (int c = 0; c < columns.length; c++) {
			labels.add(new Label(Component.translatable(columns[c].translationKey()), columnX + c * (columnWidth + 4) + 4, y, GOLD));
		}
		y += 12;
		for (UnitType type : UnitType.values()) {
			labels.add(new Label(Component.translatable(type.translationKey()), left + 12, y + 6, TEXT));
			for (int c = 0; c < columns.length; c++) {
				String id = "upgrade:" + columns[c].id() + ":" + type.id();
				if (ClientMatchState.get().shopEntry(id) != null) {
					shopButton(id, columnX + c * (columnWidth + 4), y + 1, columnWidth, MenuText::upgradeLabel);
				}
			}
			y += 24;
		}
		labels.add(new Label(Component.translatable("menu.goblinforest.upgrades.hint"), left + 12, y + 6, MUTED));
	}

	private void initStronghold(int y) {
		for (UpgradeType type : new UpgradeType[] {UpgradeType.TOWER, UpgradeType.WALLS, UpgradeType.HUTS}) {
			labels.add(new Label(Component.translatable(type.translationKey()).withStyle(ChatFormatting.BOLD), left + 12, y + 1, TEXT));
			labels.add(new Label(Component.translatable("menu.goblinforest.upgrade." + type.id() + ".desc"), left + 12, y + 11, MUTED));
			shopButton("upgrade:" + type.id(), left + WIDTH - 124, y + 2, 112, MenuText::upgradeLabel);
			y += 30;
		}
	}

	private void initMagic(int y) {
		for (String spell : new String[] {"fireball", "healing"}) {
			labels.add(new Label(Component.translatable("spell.goblinforest." + spell).withStyle(ChatFormatting.BOLD), left + 12, y + 1, TEXT));
			labels.add(new Label(Component.translatable("menu.goblinforest.spell." + spell + ".desc"), left + 12, y + 11, MUTED));
			String castId = "cast:" + spell;
			shopButton(castId, left + WIDTH - 236, y + 2, 108, entry -> {
				String key = ModKeys.label(castId);
				return Component.translatable("menu.goblinforest.cast", entry.cost(), key.isEmpty() ? "" : " [" + key + "]");
			});
			shopButton("spell_upgrade:" + spell, left + WIDTH - 124, y + 2, 112, MenuText::upgradeLabel);
			y += 30;
		}
		for (String ability : new String[] {"bloodlust", "battleSlam"}) {
			String key = ModKeys.label("ability:" + ability);
			labels.add(new Label(Component.translatable("ability.goblinforest." + ability).withStyle(ChatFormatting.BOLD)
					.append(Component.literal(key.isEmpty() ? "" : "  [" + key + "]").withStyle(ChatFormatting.GRAY)), left + 12, y + 1, TEXT));
			labels.add(new Label(Component.translatable("menu.goblinforest.ability." + ability + ".desc"), left + 12, y + 11, MUTED));
			y += 26;
		}
	}

	/** Knopf, der einen Kauf beim Server anfragt und sich nach dem Match-Zustand färbt und sperrt. */
	private void shopButton(String id, int x, int y, int w, java.util.function.Function<MatchStatePayload.ShopEntry, Component> text) {
		Button button = Button.builder(Component.empty(), b -> send(id)).bounds(x, y, w, 18).build();
		addRenderableWidget(button);
		refreshers.add(() -> {
			MatchStatePayload.ShopEntry entry = ClientMatchState.get().shopEntry(id);
			if (entry == null) {
				button.visible = false;
				return;
			}
			button.visible = true;
			PurchaseResult status = PurchaseResult.values()[Math.clamp(entry.status(), 0, PurchaseResult.values().length - 1)];
			MutableComponent label = Component.empty().append(text.apply(entry));
			if (!status.ok()) {
				label.withStyle(ChatFormatting.GRAY);
			}
			button.setMessage(label);
			button.active = status.ok();
			if (status.ok()) {
				button.setTooltip(null);
			} else if (status == PurchaseResult.REPUTATION_TOO_LOW) {
				button.setTooltip(Tooltip.create(Component.translatable("menu.goblinforest.needs_reputation", entry.reputation())));
			} else {
				button.setTooltip(Tooltip.create(Component.translatable(status.translationKey())));
			}
		});
	}

	private void send(String action) {
		ClientPlayNetworking.send(new ActionPayload(action));
	}

	private void refresh() {
		refreshers.forEach(Runnable::run);
	}

	@Override
	public void tick() {
		if (!ClientMatchState.active()) {
			onClose();
			return;
		}
		refresh();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (ModKeys.MENU != null && ModKeys.MENU.matches(event)) {
			onClose();
			return true;
		}
		for (ModKeys.Binding binding : ModKeys.ACTIONS) {
			if (binding.key().matches(event)) {
				send(binding.action());
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(g, mouseX, mouseY, partialTick);
		g.fill(left, top, left + WIDTH, top + HEIGHT, 0xE01A140C);
		g.outline(left, top, WIDTH, HEIGHT, 0xFF7A5A2A);
		g.outline(left + 2, top + 2, WIDTH - 4, HEIGHT - 4, 0xFF3A2A12);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		MatchStatePayload s = ClientMatchState.get();
		g.text(font, title, left + 10, top + 8, GOLD, true);
		Component resources = Component.translatable("menu.goblinforest.resources", s.gold(), s.reputation(), s.population(), s.populationLimit());
		g.text(font, resources, left + WIDTH - 10 - font.width(resources), top + 8, TEXT, true);
		for (Label label : labels) {
			g.text(font, label.text(), label.x(), label.y(), label.color(), true);
		}
	}

	/** Beschriftungen, die mehrere Reiter teilen. */
	private static final class MenuText {
		private MenuText() {
		}

		static Component upgradeLabel(MatchStatePayload.ShopEntry entry) {
			if (entry.level() >= entry.maxLevel()) {
				return Component.translatable("menu.goblinforest.max_level", entry.level(), entry.maxLevel());
			}
			return Component.translatable("menu.goblinforest.upgrade_cost", entry.level(), entry.maxLevel(), entry.cost());
		}
	}
}
