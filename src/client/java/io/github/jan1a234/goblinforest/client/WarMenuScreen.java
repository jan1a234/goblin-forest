package io.github.jan1a234.goblinforest.client;

import io.github.jan1a234.goblinforest.game.PurchaseResult;
import io.github.jan1a234.goblinforest.hero.AbilityType;
import io.github.jan1a234.goblinforest.spell.SpellType;
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
 * Das Kriegsmenü (Taste B): Einheiten rekrutieren, Upgrades und Festung ausbauen, Zauber verbessern,
 * Fähigkeitspunkte des Häuptlings verteilen und die Haltung der Armee wählen. Das Spiel läuft weiter, während es offen ist.
 */
public class WarMenuScreen extends Screen {
	private static final int WIDTH = 420;
	private static final int HEIGHT = 236;
	private static final int GOLD = 0xFFF2C744;
	private static final int TEXT = 0xFFE8E0C8;
	private static final int MUTED = 0xFFA09A88;
	private static final int RAGE = 0xFFD0301A;

	private enum Tab {
		UNITS, UPGRADES, STRONGHOLD, MAGIC, CHIEFTAIN
	}

	private static Tab lastTab = Tab.UNITS;

	/** Text, der im Menü gezeichnet wird (Beschriftungen neben den Knöpfen); {@code maxWidth} 0 = unbegrenzt. */
	private record Label(Component text, int x, int y, int color, int maxWidth) {
		Label(Component text, int x, int y, int color) {
			this(text, x, y, color, 0);
		}
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
			Component name = Component.translatable("menu.goblinforest.tab." + candidate.name().toLowerCase());
			Button button = Button.builder(name, b -> {
				tab = candidate;
				lastTab = candidate;
				rebuildWidgets();
			}).bounds(left + 8 + candidate.ordinal() * tabWidth, top + 22, tabWidth - 2, 18).build();
			button.active = candidate != tab;
			if (candidate == Tab.CHIEFTAIN) {
				refreshers.add(() -> {
					int points = ClientMatchState.get().abilityPoints();
					button.setMessage(points > 0 ? Component.empty().append(name).append(Component.literal(" (" + points + ")").withStyle(ChatFormatting.YELLOW)) : name);
				});
			}
			addRenderableWidget(button);
		}
		int y = top + 48;
		switch (tab) {
			case UNITS -> initUnits(y);
			case UPGRADES -> initUpgrades(y);
			case STRONGHOLD -> initStronghold(y);
			case MAGIC -> initMagic(y);
			case CHIEFTAIN -> initChieftain(y);
		}
		refresh();
	}

	private void initUnits(int y) {
		int columnWidth = (WIDTH - 24) / 2;
		UnitType[] types = UnitType.values();
		for (int i = 0; i < types.length; i++) {
			UnitType type = types[i];
			int x = left + 12 + (i % 2) * (columnWidth + 4);
			int rowY = y + (i / 2) * 32;
			String id = "recruit:" + type.id();
			int index = type.ordinal();
			labels.add(new Label(Component.translatable(type.translationKey()).withStyle(ChatFormatting.BOLD), x, rowY + 1, TEXT));
			labels.add(new Label(Component.translatable("menu.goblinforest.unit." + type.id() + ".desc"), x, rowY + 13, MUTED, columnWidth - 6));
			shopButton(id, x + columnWidth - 92, rowY - 3, 88, entry -> {
				int alive = ClientMatchState.get().unitCounts().length > index ? ClientMatchState.get().unitCounts()[index] : 0;
				String key = ModKeys.label(id);
				return Component.translatable("menu.goblinforest.recruit", entry.cost(), key.isEmpty() ? "" : " [" + key + "]")
						.append(Component.literal(" ×" + alive).withStyle(ChatFormatting.GRAY));
			}, Component.translatable("menu.goblinforest.unit." + type.id() + ".tip"));
		}
		y += 4 * 32 + 4;
		labels.add(new Label(Component.translatable("menu.goblinforest.stance"), left + 12, y + 6, GOLD));
		int x = left + 80;
		for (Stance stance : Stance.values()) {
			Button button = Button.builder(Component.translatable(stance.translationKey()), b -> send("stance:" + stance.id()))
					.bounds(x, y + 1, 100, 18)
					.tooltip(Tooltip.create(Component.translatable("menu.goblinforest.stance." + stance.id() + ".desc")))
					.build();
			addRenderableWidget(button);
			refreshers.add(() -> button.active = ClientMatchState.get().stance() != stance.ordinal());
			x += 104;
		}
		String rally = ModKeys.label("rally");
		labels.add(new Label(Component.translatable("menu.goblinforest.rally_hint", rally), left + 12, y + 26, MUTED, WIDTH - 24));
	}

	private void initUpgrades(int y) {
		UpgradeType[] columns = {UpgradeType.ARMOR, UpgradeType.ATTACK, UpgradeType.RANGE};
		int columnX = left + 120;
		int columnWidth = 92;
		for (int c = 0; c < columns.length; c++) {
			labels.add(new Label(Component.translatable(columns[c].translationKey()), columnX + c * (columnWidth + 4) + 4, y, GOLD));
		}
		y += 12;
		for (UnitType type : UnitType.values()) {
			labels.add(new Label(Component.translatable(type.translationKey()), left + 12, y + 5, TEXT));
			for (int c = 0; c < columns.length; c++) {
				String id = "upgrade:" + columns[c].id() + ":" + type.id();
				if (ClientMatchState.get().shopEntry(id) != null) {
					shopButton(id, columnX + c * (columnWidth + 4), y, columnWidth, MenuText::upgradeLabel,
							Component.translatable("menu.goblinforest.upgrade." + columns[c].id() + ".desc"));
				}
			}
			y += 19;
		}
		labels.add(new Label(Component.translatable("menu.goblinforest.upgrades.hint"), left + 12, y + 5, MUTED, WIDTH - 24));
	}

	private void initStronghold(int y) {
		for (UpgradeType type : new UpgradeType[] {UpgradeType.TOWER, UpgradeType.WALLS, UpgradeType.HUTS, UpgradeType.CANNON,
				UpgradeType.GOLDMINE, UpgradeType.TRAINING, UpgradeType.ENDURANCE}) {
			labels.add(new Label(Component.translatable(type.translationKey()).withStyle(ChatFormatting.BOLD), left + 12, y + 1, TEXT));
			labels.add(new Label(Component.translatable("menu.goblinforest.upgrade." + type.id() + ".desc"), left + 12, y + 11, MUTED, WIDTH - 150));
			shopButton("upgrade:" + type.id(), left + WIDTH - 124, y + 2, 112, MenuText::upgradeLabel, null);
			y += 25;
		}
	}

	private void initMagic(int y) {
		for (SpellType spell : SpellType.values()) {
			labels.add(new Label(Component.translatable(spell.translationKey()).withStyle(ChatFormatting.BOLD), left + 12, y + 1, TEXT));
			labels.add(new Label(Component.translatable("menu.goblinforest.spell." + spell.id() + ".desc"), left + 12, y + 11, MUTED, WIDTH - 260));
			String castId = "cast:" + spell.id();
			shopButton(castId, left + WIDTH - 240, y + 2, 112, entry -> {
				String key = ModKeys.label(castId);
				return Component.translatable("menu.goblinforest.cast", entry.cost(), key.isEmpty() ? "" : " [" + key + "]");
			}, null);
			shopButton("spell_upgrade:" + spell.id(), left + WIDTH - 124, y + 2, 112, MenuText::upgradeLabel,
					Component.translatable("menu.goblinforest.spell_upgrade.desc"));
			y += 28;
		}
		labels.add(new Label(Component.translatable("menu.goblinforest.magic.hint"), left + 12, y + 2, MUTED, WIDTH - 24));
	}

	private void initChieftain(int y) {
		y += 14;
		for (AbilityType ability : AbilityType.values()) {
			String key = ModKeys.label("ability:" + ability.id());
			labels.add(new Label(Component.translatable(ability.translationKey()).withStyle(ChatFormatting.BOLD)
					.append(Component.literal(key.isEmpty() ? "" : "  [" + key + "]").withStyle(ChatFormatting.GRAY)), left + 12, y + 1, TEXT));
			labels.add(new Label(Component.translatable("menu.goblinforest.ability." + ability.id() + ".desc"), left + 12, y + 11, MUTED, WIDTH - 150));
			shopButton("ability_rank:" + ability.id(), left + WIDTH - 124, y + 2, 112, entry -> entry.level() >= entry.maxLevel()
							? Component.translatable("menu.goblinforest.rank_max", entry.level(), entry.maxLevel())
							: Component.translatable("menu.goblinforest.rank_up", entry.level(), entry.maxLevel()),
					Component.translatable("menu.goblinforest.ability." + ability.id() + ".rank"));
			y += 30;
		}
		labels.add(new Label(Component.translatable("menu.goblinforest.chieftain.hint"), left + 12, y + 18, MUTED, WIDTH - 24));
	}

	/** Knopf, der einen Kauf beim Server anfragt und sich nach dem Match-Zustand färbt und sperrt. */
	private void shopButton(String id, int x, int y, int w, java.util.function.Function<MatchStatePayload.ShopEntry, Component> text, Component info) {
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
			MutableComponent label = status == PurchaseResult.REPUTATION_TOO_LOW && entry.level() == 0 && !id.startsWith("upgrade:")
					? Component.translatable("menu.goblinforest.locked", entry.reputation())
					: Component.empty().append(text.apply(entry));
			if (!status.ok()) {
				label.withStyle(ChatFormatting.GRAY);
			}
			button.setMessage(label);
			button.active = status.ok();
			Component reason = null;
			if (status == PurchaseResult.REPUTATION_TOO_LOW) {
				reason = Component.translatable("menu.goblinforest.needs_reputation", entry.reputation()).withStyle(ChatFormatting.RED);
			} else if (!status.ok()) {
				reason = Component.translatable(status.translationKey()).withStyle(ChatFormatting.RED);
			}
			Component tip = info == null ? reason : reason == null ? info : Component.empty().append(info).append("\n").append(reason);
			if (!java.util.Objects.equals(tip, MenuText.lastTooltip(button))) {
				button.setTooltip(tip == null ? null : Tooltip.create(tip));
				MenuText.rememberTooltip(button, tip);
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
		Component resources = Component.translatable("menu.goblinforest.resources", s.gold(), String.format("%.1f", s.income()),
				s.reputation(), s.population(), s.populationLimit());
		g.text(font, resources, left + WIDTH - 10 - font.width(resources), top + 8, TEXT, true);
		for (Label label : labels) {
			if (label.maxWidth() > 0 && font.width(label.text()) > label.maxWidth()) {
				String cut = font.plainSubstrByWidth(label.text().getString(), label.maxWidth() - font.width("…")) + "…";
				g.text(font, cut, label.x(), label.y(), label.color(), true);
			} else {
				g.text(font, label.text(), label.x(), label.y(), label.color(), true);
			}
		}
		if (tab == Tab.CHIEFTAIN) {
			drawChieftainHeader(g, s);
		}
	}

	private void drawChieftainHeader(GuiGraphicsExtractor g, MatchStatePayload s) {
		int y = top + 48;
		Component level = Component.translatable("menu.goblinforest.chieftain.level", s.heroLevel());
		g.text(font, level, left + 12, y, 0xFF8BD449, true);
		Component points = Component.translatable("menu.goblinforest.chieftain.points", s.abilityPoints());
		g.text(font, points, left + WIDTH - 12 - font.width(points), y, s.abilityPoints() > 0 ? GOLD : MUTED, true);
		int barY = top + 48 + 14 + 3 * 30 + 2;
		Component rage = s.rageSeconds() > 0
				? Component.translatable("menu.goblinforest.rage_active", s.rageSeconds())
				: Component.translatable("menu.goblinforest.rage_charge", Math.round(s.rageCharge() * 100));
		g.text(font, rage, left + 12, barY, RAGE, true);
		int barX = left + 20 + font.width(rage);
		int barW = left + WIDTH - 12 - barX;
		g.fill(barX, barY + 2, barX + barW, barY + 7, 0xFF202020);
		float fill = s.rageSeconds() > 0 ? 1f : s.rageCharge();
		g.fill(barX, barY + 2, barX + (int) (barW * Math.clamp(fill, 0f, 1f)), barY + 7, s.rageCharge() >= 1f || s.rageSeconds() > 0 ? 0xFFFF4020 : RAGE);
	}

	/** Beschriftungen, die mehrere Reiter teilen. */
	private static final class MenuText {
		private static final java.util.Map<Button, Component> TOOLTIPS = new java.util.WeakHashMap<>();

		private MenuText() {
		}

		static Component lastTooltip(Button button) {
			return TOOLTIPS.get(button);
		}

		static void rememberTooltip(Button button, Component tip) {
			TOOLTIPS.put(button, tip);
		}

		static Component upgradeLabel(MatchStatePayload.ShopEntry entry) {
			if (entry.level() >= entry.maxLevel()) {
				return Component.translatable("menu.goblinforest.max_level", entry.level(), entry.maxLevel());
			}
			return Component.translatable("menu.goblinforest.upgrade_cost", entry.level(), entry.maxLevel(), entry.cost());
		}
	}
}
