package io.github.jan1a234.goblinforest.client;

import io.github.jan1a234.goblinforest.game.MatchPhase;
import io.github.jan1a234.goblinforest.game.PurchaseResult;
import io.github.jan1a234.goblinforest.game.Stance;
import io.github.jan1a234.goblinforest.game.TeamColor;
import io.github.jan1a234.goblinforest.hero.AbilityType;
import io.github.jan1a234.goblinforest.net.MatchStatePayload;
import io.github.jan1a234.goblinforest.spell.SpellType;
import io.github.jan1a234.goblinforest.unit.UnitType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Match-HUD für die Draufsicht (DESIGN.md Abschnitt 10):
 * <ul>
 * <li>oben in der Mitte Festungen und Türme, darunter Runden, Sudden Death (nur wenn eingeschaltet) und Fähigkeitspunkte,</li>
 * <li>oben links Gold, Ruf, Armeegröße und der Gegner,</li>
 * <li>unten links die Befehlsleiste: oben die Einheiten mit Haltung und Sammelbanner, unten die Zauber (Wunder),
 * der Häuptling, seine Fähigkeiten und das Kriegsmenü,</li>
 * <li>unten rechts der Zustand des Häuptlings.</li>
 * </ul>
 * Jedes Feld der Leiste ist anklickbar; {@link CommandScreen} fragt dafür {@link #hotspotAt} ab. Alles ist so
 * bemessen, dass es auch bei der kleinsten GUI (320 × 240) vollständig ins Bild passt.
 */
public final class MatchHud {
	private static final int PANEL = 0xA0101010;
	private static final int BORDER = 0xFF3A2A12;
	private static final int GOLD = 0xFFF2C744;
	private static final int TEXT = 0xFFE8E0C8;
	private static final int MUTED = 0xFFA09A88;
	private static final int REPUTATION = 0xFF5FD3E0;
	private static final int XP = 0xFF8BD449;
	private static final int BAD = 0xFFFF6B5B;
	private static final int RAGE = 0xFFD0301A;

	/** Größe eines Felds der Befehlsleiste. */
	static final int SLOT_W = 24;
	static final int SLOT_H = 27;
	private static final int SLOT_GAP = 2;
	private static final int GROUP_GAP = 6;
	private static final int PAD = 3;
	/** Gesamthöhe der Befehlsleiste samt Rand (zwei Reihen). */
	public static final int BAR_HEIGHT = PAD * 2 + SLOT_H * 2 + SLOT_GAP + 2;
	/** Höhe der Festungsleiste oben; alles andere beginnt darunter, damit sich bei kleiner GUI nichts überlappt. */
	private static final int TOP_BAR_HEIGHT = 32;
	private static final int CHIEF_PANEL_W = 128;
	private static final int CHIEF_PANEL_H = 46;

	/** Anklickbare Fläche der Leiste mit Aktion und Tooltip. */
	public record Hotspot(int x, int y, int w, int h, String action, List<Component> tooltip) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private static final List<Hotspot> HOTSPOTS = new ArrayList<>();
	/** Rechte Kante der Befehlsleiste im letzten Frame (für Tests und die Anordnung daneben). */
	private static int barRight;
	private static int barTop;

	private MatchHud() {
	}

	private static Minecraft mc() {
		return Minecraft.getInstance();
	}

	/** Anklickbares Feld unter dem Mauszeiger oder null. */
	public static Hotspot hotspotAt(double mx, double my) {
		for (Hotspot spot : HOTSPOTS) {
			if (spot.contains(mx, my)) {
				return spot;
			}
		}
		return null;
	}

	/** Feld mit dieser Aktion (für Tests), oder null. */
	public static Hotspot hotspot(String action) {
		for (Hotspot spot : HOTSPOTS) {
			if (spot.action().equals(action)) {
				return spot;
			}
		}
		return null;
	}

	/** Liegt der Punkt auf einem HUD-Element, in das nicht ins Feld geklickt werden soll? */
	public static boolean blocksClick(double mx, double my) {
		return hotspotAt(mx, my) != null || mx < barRight && my >= barTop;
	}

	public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		HOTSPOTS.clear();
		barRight = 0;
		barTop = Integer.MAX_VALUE;
		MatchStatePayload s = ClientMatchState.get();
		if (!s.inMatch() || mc().player == null) {
			return;
		}
		Font font = mc().font;
		MatchPhase phase = MatchPhase.values()[Mth.clamp(s.phase(), 0, MatchPhase.values().length - 1)];
		if (phase == MatchPhase.SETUP) {
			drawBuilding(g, font, s);
			return;
		}
		drawStructures(g, font, s);
		drawNotices(g, font, s);
		drawResources(g, font, s);
		drawCommandBar(g, font, s);
		drawChieftainPanel(g, font, s);
		if (!s.chieftainSent() && (phase == MatchPhase.COUNTDOWN || phase == MatchPhase.BATTLE)) {
			drawStartHint(g, font);
		}
	}

	private static void drawBuilding(GuiGraphicsExtractor g, Font font, MatchStatePayload s) {
		int w = 180;
		int x = (g.guiWidth() - w) / 2;
		int y = g.guiHeight() / 2 - 40;
		panel(g, x - 6, y - 6, w + 12, 32);
		g.centeredText(font, Component.translatable("hud.goblinforest.building"), g.guiWidth() / 2, y, GOLD);
		bar(g, x, y + 13, w, 6, s.phaseSeconds() / 100f, XP);
	}

	private static void drawStructures(GuiGraphicsExtractor g, Font font, MatchStatePayload s) {
		int cx = g.guiWidth() / 2;
		int panelWidth = Math.min(g.guiWidth() - 8, 330);
		int gap = 22;
		int barWidth = (panelWidth - 12 - 2 * gap) / 2;
		int y = 4;
		panel(g, cx - panelWidth / 2, y - 2, panelWidth, 28);
		String time = String.format("%d:%02d", s.matchSeconds() / 60, s.matchSeconds() % 60);
		g.centeredText(font, time, cx, y + 9, TEXT);
		for (TeamColor team : TeamColor.values()) {
			int i = team.ordinal();
			boolean left = team == TeamColor.RED;
			int x = left ? cx - gap - barWidth : cx + gap;
			int color = 0xFF000000 | team.rgb();
			Component label = Component.translatable(team.translationKey());
			if (i == s.team()) {
				label = Component.translatable("hud.goblinforest.you", label);
			}
			String coreText = String.valueOf((int) Math.ceil(s.coreHealth()[i]));
			String labelText = fit(font, label.getString(), barWidth - font.width(coreText) - 4);
			// Name außen, Lebenspunkte des Kerns innen (zur Uhr hin).
			g.text(font, labelText, left ? x : x + barWidth - font.width(labelText), y, color, true);
			g.text(font, coreText, left ? x + barWidth - font.width(coreText) : x, y, TEXT, true);
			float core = s.coreMaxHealth()[i] <= 0 ? 0 : s.coreHealth()[i] / s.coreMaxHealth()[i];
			bar(g, x, y + 10, barWidth, 6, core, color);
			float tower = s.towerMaxHealth()[i] <= 0 ? 0 : s.towerHealth()[i] / s.towerMaxHealth()[i];
			bar(g, x, y + 18, barWidth, 3, tower, tower > 0 ? 0xFFB0B0B0 : 0xFF505050);
		}
	}

	/** Hinweise unter der Festungsleiste: Best-of-Stand, Sudden Death (nur wenn eingeschaltet), freie Fähigkeitspunkte. */
	private static void drawNotices(GuiGraphicsExtractor g, Font font, MatchStatePayload s) {
		int cx = g.guiWidth() / 2;
		int y = TOP_BAR_HEIGHT + 4;
		if (s.bestOf() > 1) {
			g.centeredText(font, Component.translatable("hud.goblinforest.rounds", s.roundWins()[0], s.roundWins()[1], s.bestOf()), cx, y, TEXT);
			y += 11;
		}
		if (s.suddenDeath()) {
			boolean blink = (ClientMatchState.ticks() / 10) % 2 == 0;
			g.centeredText(font, Component.translatable("hud.goblinforest.sudden_death"), cx, y, blink ? 0xFFFF3020 : 0xFFB02010);
			y += 11;
		} else if (s.suddenDeathSeconds() > 0 && s.suddenDeathSeconds() <= 180) {
			String time = String.format("%d:%02d", s.suddenDeathSeconds() / 60, s.suddenDeathSeconds() % 60);
			g.centeredText(font, Component.translatable("hud.goblinforest.sudden_death_in", time), cx, y, 0xFFFF8060);
			y += 11;
		}
		if (s.abilityPoints() > 0) {
			g.centeredText(font, Component.translatable("hud.goblinforest.ability_points", s.abilityPoints(),
					ModKeys.MENU.getTranslatedKeyMessage()), cx, y, GOLD);
		}
	}

	/** Gold, Ruf, Armee und Gegner oben links. */
	private static void drawResources(GuiGraphicsExtractor g, Font font, MatchStatePayload s) {
		int x = 6;
		int y = TOP_BAR_HEIGHT + 6;
		// Schmal halten, damit die Hinweise in der Mitte auch bei kleiner GUI frei bleiben.
		int w = Math.min(112, g.guiWidth() / 2 - 60);
		panel(g, x - 4, y - 4, w + 8, 64);
		Component gold = Component.translatable("hud.goblinforest.gold", s.gold());
		g.text(font, gold, x, y, GOLD, true);
		int gain = ClientMatchState.recentGoldGain();
		String income = String.format("+%.1f/s", s.income());
		if (gain > 0) {
			g.text(font, "+" + gain, x + font.width(gold) + 4, y, 0xFFFFF59A, true);
		} else if (font.width(gold) + font.width(income) + 4 <= w) {
			g.text(font, income, x + w - font.width(income), y, 0xFFB8A050, true);
		}
		y += 12;
		Component reputation = Component.translatable("hud.goblinforest.reputation", s.reputation());
		g.text(font, reputation, x, y, REPUTATION, true);
		int repBar = font.width(reputation) + 4;
		bar(g, x + repBar, y + 2, w - repBar, 4, s.reputationProgress(), REPUTATION);
		y += 12;
		boolean full = s.population() >= s.populationLimit();
		g.text(font, Component.translatable("hud.goblinforest.population", s.population(), s.populationLimit()), x, y, full ? 0xFFFF8060 : TEXT, true);
		y += 12;
		Stance stance = Stance.values()[Mth.clamp(s.stance(), 0, Stance.values().length - 1)];
		Component stanceText = Component.translatable(stance.translationKey());
		if (s.rallySet()) {
			stanceText = Component.translatable("hud.goblinforest.at_rally", stanceText);
		}
		g.text(font, fit(font, Component.translatable("hud.goblinforest.stance", stanceText).getString(), w), x, y, TEXT, true);
		y += 12;
		String enemy = fit(font, Component.translatable("hud.goblinforest.enemy", s.enemyUnits(), s.enemyHeroLevel(), s.enemyReputation()).getString(), w);
		g.text(font, enemy, x, y, 0xFFE09080, true);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Befehlsleiste unten links
	// ---------------------------------------------------------------------------------------------------------------

	private static void drawCommandBar(GuiGraphicsExtractor g, Font font, MatchStatePayload s) {
		int row2 = g.guiHeight() - PAD - SLOT_H - 3;
		int row1 = row2 - SLOT_GAP - SLOT_H;
		int left = 2 + PAD;
		// Breite vorab berechnen, damit der Hintergrund unter den Feldern liegt.
		int width1 = 10 * (SLOT_W + SLOT_GAP) - SLOT_GAP + GROUP_GAP;
		int width2 = 10 * (SLOT_W + SLOT_GAP) - SLOT_GAP + 3 * GROUP_GAP;
		int width = Math.max(width1, width2);
		barTop = row1 - PAD;
		barRight = left + width + PAD;
		panel(g, 2, barTop, width + 2 * PAD, g.guiHeight() - 2 - barTop);

		int x = left;
		for (UnitType type : UnitType.soldiers()) {
			unitSlot(g, font, s, type, x, row1);
			x += SLOT_W + SLOT_GAP;
		}
		x += GROUP_GAP;
		stanceSlot(g, font, s, x, row1);
		x += SLOT_W + SLOT_GAP;
		rallySlot(g, font, s, x, row1);

		x = left;
		for (SpellType spell : SpellType.values()) {
			spellSlot(g, font, s, spell, x, row2);
			x += SLOT_W + SLOT_GAP;
		}
		x += GROUP_GAP;
		chieftainSlot(g, font, s, x, row2);
		x += SLOT_W + SLOT_GAP + GROUP_GAP;
		for (AbilityType ability : AbilityType.values()) {
			abilitySlot(g, font, s, ability, x, row2);
			x += SLOT_W + SLOT_GAP;
		}
		x += GROUP_GAP;
		menuSlot(g, font, s, x, row2);
	}

	private static Item unitIcon(UnitType type) {
		return switch (type) {
			case SLAVE -> Items.WOODEN_AXE;
			case WARRIOR -> Items.IRON_AXE;
			case ARCHER -> Items.BOW;
			case ASSASSIN -> Items.IRON_SWORD;
			case SHAMAN -> Items.BLAZE_ROD;
			case WOLF_RIDER -> Items.IRON_SPEAR;
			case TROLL -> Items.MACE;
			case CATAPULT -> Items.DISPENSER;
			case CHIEFTAIN -> Items.GOLDEN_HELMET;
		};
	}

	private static Item spellIcon(SpellType spell) {
		return switch (spell) {
			case FIREBALL -> Items.FIRE_CHARGE;
			case HEALING -> Items.RED_MUSHROOM;
			case ROOTS -> Items.HANGING_ROOTS;
			case LIGHTNING -> Items.TRIDENT;
			case METEOR -> Items.MAGMA_BLOCK;
		};
	}

	private static Item abilityIcon(AbilityType ability) {
		return switch (ability) {
			case BLOODLUST -> Items.REDSTONE;
			case BATTLE_SLAM -> Items.ANVIL;
			case RAGE -> Items.BLAZE_POWDER;
		};
	}

	private static PurchaseResult status(MatchStatePayload.ShopEntry entry) {
		return entry == null ? PurchaseResult.NOT_AVAILABLE
				: PurchaseResult.values()[Mth.clamp(entry.status(), 0, PurchaseResult.values().length - 1)];
	}

	private static void unitSlot(GuiGraphicsExtractor g, Font font, MatchStatePayload s, UnitType type, int x, int y) {
		String action = "recruit:" + type.id();
		MatchStatePayload.ShopEntry entry = s.shopEntry(action);
		PurchaseResult status = status(entry);
		boolean locked = status == PurchaseResult.REPUTATION_TOO_LOW;
		int count = s.unitCounts().length > type.ordinal() ? s.unitCounts()[type.ordinal()] : 0;
		String caption;
		int captionColor;
		if (locked) {
			caption = "R" + entry.reputation();
			captionColor = REPUTATION;
		} else {
			caption = entry == null ? "" : String.valueOf(entry.cost());
			captionColor = status == PurchaseResult.NOT_ENOUGH_GOLD ? BAD : status.ok() ? GOLD : MUTED;
		}
		List<Component> tip = new ArrayList<>();
		tip.add(Component.translatable(type.translationKey()).withStyle(ChatFormatting.GOLD));
		tip.add(Component.translatable("menu.goblinforest.unit." + type.id() + ".desc").withStyle(ChatFormatting.GRAY));
		if (entry != null) {
			tip.add(Component.translatable("hud.goblinforest.slot.recruit", entry.cost()));
		}
		addReason(tip, status, entry);
		addKey(tip, action);
		slot(g, font, x, y, new ItemStack(unitIcon(type)), caption, captionColor, ModKeys.label(action),
				count > 0 ? String.valueOf(count) : "", !status.ok(), 0f, false, action, tip);
	}

	private static void stanceSlot(GuiGraphicsExtractor g, Font font, MatchStatePayload s, int x, int y) {
		Stance stance = Stance.values()[Mth.clamp(s.stance(), 0, Stance.values().length - 1)];
		Item icon = switch (stance) {
			case ADVANCE -> Items.GOAT_HORN;
			case HOLD -> Items.SHIELD;
			case RETREAT -> Items.COMPASS;
		};
		Component name = Component.translatable(stance.translationKey());
		List<Component> tip = new ArrayList<>();
		tip.add(Component.translatable("hud.goblinforest.slot.stance", name).withStyle(ChatFormatting.GOLD));
		tip.add(Component.translatable("menu.goblinforest.stance." + stance.id() + ".desc").withStyle(ChatFormatting.GRAY));
		tip.add(Component.translatable("hud.goblinforest.slot.stance.click", Component.translatable(stance.next().translationKey())));
		addKey(tip, "stance:next");
		slot(g, font, x, y, new ItemStack(icon), "", TEXT, ModKeys.label("stance:next"), "", false, 0f, false, "stance:next", tip);
	}

	private static void rallySlot(GuiGraphicsExtractor g, Font font, MatchStatePayload s, int x, int y) {
		List<Component> tip = new ArrayList<>();
		tip.add(Component.translatable("hud.goblinforest.slot.rally").withStyle(ChatFormatting.GOLD));
		tip.add(Component.translatable("hud.goblinforest.slot.rally.desc").withStyle(ChatFormatting.GRAY));
		addKey(tip, "rally");
		slot(g, font, x, y, new ItemStack(Items.TARGET), s.rallySet() ? "⚑" : "", XP, ModKeys.label("rally"), "",
				false, 0f, "rally".equals(CommandScreen.armedAction()), "rally", tip);
	}

	private static void spellSlot(GuiGraphicsExtractor g, Font font, MatchStatePayload s, SpellType spell, int x, int y) {
		String action = "cast:" + spell.id();
		MatchStatePayload.ShopEntry entry = s.shopEntry(action);
		PurchaseResult status = status(entry);
		MatchStatePayload.Cooldown cooldown = s.cooldown(spell.id());
		int remaining = cooldown == null ? 0 : cooldown.remaining();
		float cooling = remaining > 0 && cooldown.total() > 0 ? remaining / (float) cooldown.total() : 0f;
		String caption;
		int color;
		if (status == PurchaseResult.REPUTATION_TOO_LOW) {
			caption = "R" + entry.reputation();
			color = REPUTATION;
		} else if (remaining > 0) {
			caption = ((remaining + 19) / 20) + "s";
			color = 0xFFFFB060;
		} else {
			caption = entry == null ? "" : String.valueOf(entry.cost());
			color = status == PurchaseResult.NOT_ENOUGH_GOLD ? BAD : status.ok() ? GOLD : MUTED;
		}
		List<Component> tip = new ArrayList<>();
		MutableComponent title = Component.translatable(spell.translationKey()).withStyle(ChatFormatting.GOLD);
		if (entry != null && entry.level() > 0) {
			title.append(Component.literal(" +" + entry.level()).withStyle(ChatFormatting.YELLOW));
		}
		tip.add(title);
		tip.add(Component.translatable("menu.goblinforest.spell." + spell.id() + ".desc").withStyle(ChatFormatting.GRAY));
		if (entry != null) {
			tip.add(Component.translatable("hud.goblinforest.slot.cast", entry.cost()));
		}
		addReason(tip, status, entry);
		addKey(tip, action);
		boolean armed = action.equals(CommandScreen.armedAction());
		slot(g, font, x, y, new ItemStack(spellIcon(spell)), caption, color, ModKeys.label(action), "", !status.ok() && !armed,
				cooling, armed, action, tip);
	}

	private static void chieftainSlot(GuiGraphicsExtractor g, Font font, MatchStatePayload s, int x, int y) {
		String caption;
		int color;
		List<Component> tip = new ArrayList<>();
		tip.add(Component.translatable("unit.goblinforest.chieftain.level", s.heroLevel()).withStyle(ChatFormatting.GOLD));
		if (s.chieftainDead()) {
			caption = s.chieftainRespawnSeconds() + "s";
			color = BAD;
			tip.add(Component.translatable("hud.goblinforest.slot.chieftain.dead", s.chieftainRespawnSeconds()).withStyle(ChatFormatting.RED));
			tip.add(Component.translatable(s.chieftainSent() ? "hud.goblinforest.slot.chieftain.dead_marching" : "hud.goblinforest.slot.chieftain.dead_home")
					.withStyle(ChatFormatting.GRAY));
		} else if (s.chieftainOnField()) {
			caption = Component.translatable("hud.goblinforest.caption.recall").getString();
			color = TEXT;
			tip.add(Component.translatable("hud.goblinforest.slot.chieftain.field").withStyle(ChatFormatting.GRAY));
			tip.add(Component.translatable("hud.goblinforest.slot.chieftain.recall"));
		} else {
			caption = Component.translatable("hud.goblinforest.caption.send").getString();
			color = XP;
			tip.add(Component.translatable("hud.goblinforest.slot.chieftain.home").withStyle(ChatFormatting.GRAY));
			tip.add(Component.translatable("hud.goblinforest.slot.chieftain.send"));
		}
		addKey(tip, "chieftain:toggle");
		tip.add(Component.translatable("hud.goblinforest.slot.chieftain.center", ModKeys.CENTER_CHIEFTAIN.getTranslatedKeyMessage())
				.withStyle(ChatFormatting.DARK_GRAY));
		// Blinkt, bis der Häuptling zum ersten Mal losgeschickt wurde.
		boolean attention = !s.chieftainSent() && !s.chieftainDead() && (ClientMatchState.ticks() / 10) % 2 == 0;
		slot(g, font, x, y, new ItemStack(Items.GOLDEN_HELMET), caption, color, ModKeys.label("chieftain:toggle"),
				String.valueOf(s.heroLevel()), false, 0f, attention, "chieftain:toggle", tip);
	}

	private static void abilitySlot(GuiGraphicsExtractor g, Font font, MatchStatePayload s, AbilityType ability, int x, int y) {
		String action = "ability:" + ability.id();
		MatchStatePayload.ShopEntry entry = s.shopEntry(action);
		PurchaseResult status = status(entry);
		MatchStatePayload.Cooldown cooldown = s.cooldown(ability.id());
		int remaining = cooldown == null ? 0 : cooldown.remaining();
		float cooling = remaining > 0 && cooldown.total() > 0 ? remaining / (float) cooldown.total() : 0f;
		String caption;
		int color;
		boolean highlight = false;
		if (ability == AbilityType.RAGE) {
			if (s.rageSeconds() > 0) {
				caption = s.rageSeconds() + "s";
				color = 0xFFFF4020;
				highlight = true;
			} else {
				caption = Math.round(s.rageCharge() * 100) + "%";
				color = s.rageCharge() >= 1f ? 0xFFFF4020 : MUTED;
				cooling = 1f - Mth.clamp(s.rageCharge(), 0f, 1f);
				highlight = s.rageCharge() >= 1f && !s.chieftainDead() && (ClientMatchState.ticks() / 5) % 2 == 0;
			}
		} else if (remaining > 0) {
			caption = ((remaining + 19) / 20) + "s";
			color = 0xFFFFB060;
		} else {
			caption = status.ok() ? "✔" : "";
			color = XP;
		}
		List<Component> tip = new ArrayList<>();
		MutableComponent title = Component.translatable(ability.translationKey()).withStyle(ChatFormatting.GOLD);
		if (entry != null) {
			title.append(Component.literal("  " + entry.level() + "/" + entry.maxLevel()).withStyle(ChatFormatting.YELLOW));
		}
		tip.add(title);
		tip.add(Component.translatable("menu.goblinforest.ability." + ability.id() + ".desc").withStyle(ChatFormatting.GRAY));
		tip.add(Component.translatable("hud.goblinforest.slot.ability"));
		addReason(tip, status, entry);
		addKey(tip, action);
		slot(g, font, x, y, new ItemStack(abilityIcon(ability)), caption, color, ModKeys.label(action), "",
				!status.ok() && !highlight, cooling, highlight, action, tip);
	}

	private static void menuSlot(GuiGraphicsExtractor g, Font font, MatchStatePayload s, int x, int y) {
		List<Component> tip = new ArrayList<>();
		tip.add(Component.translatable("hud.goblinforest.slot.menu").withStyle(ChatFormatting.GOLD));
		tip.add(Component.translatable("hud.goblinforest.slot.menu.desc").withStyle(ChatFormatting.GRAY));
		tip.add(Component.translatable("hud.goblinforest.slot.key", ModKeys.MENU.getTranslatedKeyMessage()).withStyle(ChatFormatting.DARK_GRAY));
		boolean points = s.abilityPoints() > 0 && (ClientMatchState.ticks() / 10) % 2 == 0;
		slot(g, font, x, y, new ItemStack(Items.WRITABLE_BOOK), Component.translatable("hud.goblinforest.caption.menu").getString(), TEXT,
				ModKeys.MENU.getTranslatedKeyMessage().getString(), s.abilityPoints() > 0 ? "+" + s.abilityPoints() : "", false, 0f, points,
				"menu", tip);
	}

	private static void addReason(List<Component> tip, PurchaseResult status, MatchStatePayload.ShopEntry entry) {
		if (status == PurchaseResult.REPUTATION_TOO_LOW && entry != null) {
			tip.add(Component.translatable("menu.goblinforest.needs_reputation", entry.reputation()).withStyle(ChatFormatting.RED));
		} else if (!status.ok()) {
			tip.add(Component.translatable(status.translationKey()).withStyle(ChatFormatting.RED));
		}
	}

	private static void addKey(List<Component> tip, String action) {
		String key = ModKeys.label(action);
		if (!key.isEmpty()) {
			tip.add(Component.translatable("hud.goblinforest.slot.key", key).withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	/**
	 * Ein Feld der Leiste: Symbol, Beschriftung darunter, Taste oben links, Zahl oben rechts.
	 * {@code cooling} (0–1) dunkelt das Symbol von oben her ab, {@code highlight} rahmt es golden ein.
	 */
	private static void slot(GuiGraphicsExtractor g, Font font, int x, int y, ItemStack icon, String caption, int captionColor,
			String key, String corner, boolean disabled, float cooling, boolean highlight, String action, List<Component> tooltip) {
		boolean hover = CommandScreen.isHovered(x, y, SLOT_W, SLOT_H);
		g.fill(x, y, x + SLOT_W, y + SLOT_H, hover ? 0xE0302618 : 0xD0181410);
		g.item(icon, x + (SLOT_W - 16) / 2, y + 1);
		if (cooling > 0) {
			int h = Math.round(17 * Mth.clamp(cooling, 0f, 1f));
			g.fill(x + 1, y + 1, x + SLOT_W - 1, y + 1 + h, 0xA0000000);
		}
		if (disabled) {
			g.fill(x + 1, y + 1, x + SLOT_W - 1, y + 18, 0x90101010);
		}
		int border = highlight ? GOLD : hover ? 0xFFE8D8A0 : 0xFF5A4424;
		g.outline(x, y, SLOT_W, SLOT_H, border);
		if (highlight) {
			g.outline(x - 1, y - 1, SLOT_W + 2, SLOT_H + 2, GOLD);
		}
		if (!key.isEmpty()) {
			small(g, font, fit(font, key, 28), x + 1, y + 1, 0xFFFFFFFF, false);
		}
		if (!corner.isEmpty()) {
			small(g, font, corner, x + SLOT_W - 1, y + 1, 0xFFFFF59A, true);
		}
		if (!caption.isEmpty()) {
			int fullWidth = font.width(caption);
			if (fullWidth <= SLOT_W) {
				g.text(font, caption, x + (SLOT_W - fullWidth) / 2 + 1, y + 18, captionColor, true);
			} else {
				// Längere Wörter (Haltung, „Zurück“) in kleinerer Schrift, notfalls gekürzt.
				String text = fit(font, caption, (int) (SLOT_W / 0.75f));
				float w = font.width(text) * 0.75f;
				g.pose().pushMatrix();
				g.pose().translate(x + (SLOT_W - w) / 2f, y + 19.5f);
				g.pose().scale(0.75f, 0.75f);
				g.text(font, text, 0, 0, captionColor, true);
				g.pose().popMatrix();
			}
		}
		HOTSPOTS.add(new Hotspot(x, y, SLOT_W, SLOT_H, action, tooltip));
	}

	/** Kleine Schrift (¾) für Tastenkürzel und Zähler; {@code alignRight} richtet sie an {@code x} rechts aus. */
	private static void small(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color, boolean alignRight) {
		float scale = 0.75f;
		float w = font.width(text) * scale;
		g.pose().pushMatrix();
		g.pose().translate(alignRight ? x - w : x, y);
		g.pose().scale(scale, scale);
		g.text(font, text, 0, 0, color, true);
		g.pose().popMatrix();
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Häuptling unten rechts
	// ---------------------------------------------------------------------------------------------------------------

	private static void drawChieftainPanel(GuiGraphicsExtractor g, Font font, MatchStatePayload s) {
		int w = CHIEF_PANEL_W;
		int h = CHIEF_PANEL_H;
		int x = g.guiWidth() - w - 4;
		int y = g.guiHeight() - h - 4;
		if (x < barRight + 4) {
			// Zu schmal für beides nebeneinander: über die Leiste, rechts.
			y = barTop - h - 4;
		}
		panel(g, x, y, w, h);
		int tx = x + 4;
		int ty = y + 4;
		int inner = w - 8;
		g.text(font, fit(font, "♛ " + Component.translatable("unit.goblinforest.chieftain.level", s.heroLevel()).getString(), inner), tx, ty, GOLD, true);
		ty += 10;
		Component state;
		int stateColor;
		if (s.chieftainDead()) {
			state = Component.translatable("hud.goblinforest.chieftain.dead", s.chieftainRespawnSeconds());
			stateColor = BAD;
		} else if (s.chieftainOnField()) {
			state = Component.translatable("hud.goblinforest.chieftain.field");
			stateColor = XP;
		} else {
			state = Component.translatable("hud.goblinforest.chieftain.home");
			stateColor = MUTED;
		}
		g.text(font, fit(font, state.getString(), inner), tx, ty, stateColor, true);
		ty += 11;
		float health = s.chieftainMaxHealth() <= 0 ? 0 : s.chieftainHealth() / s.chieftainMaxHealth();
		bar(g, tx, ty, inner, 5, s.chieftainDead() ? 0 : health, 0xFFC0392B);
		ty += 7;
		bar(g, tx, ty, inner, 3, s.heroProgress(), XP);
		ty += 5;
		bar(g, tx, ty, inner, 3, s.rageSeconds() > 0 ? 1f : s.rageCharge(), s.rageSeconds() > 0 || s.rageCharge() >= 1f ? 0xFFFF4020 : RAGE);
		List<Component> tip = new ArrayList<>();
		tip.add(Component.translatable("unit.goblinforest.chieftain.level", s.heroLevel()).withStyle(ChatFormatting.GOLD));
		if (!s.chieftainDead()) {
			tip.add(Component.translatable("hud.goblinforest.chieftain.health", (int) Math.ceil(s.chieftainHealth()), (int) s.chieftainMaxHealth()));
		}
		tip.add(Component.translatable("hud.goblinforest.chieftain.xp", Math.round(s.heroProgress() * 100)).withStyle(ChatFormatting.GREEN));
		tip.add(Component.translatable("hud.goblinforest.chieftain.rage", Math.round(s.rageCharge() * 100)).withStyle(ChatFormatting.RED));
		tip.add(Component.translatable("hud.goblinforest.slot.chieftain.center", ModKeys.CENTER_CHIEFTAIN.getTranslatedKeyMessage())
				.withStyle(ChatFormatting.DARK_GRAY));
		HOTSPOTS.add(new Hotspot(x, y, w, h, "center", tip));
	}

	/** Hinweis zu Rundenbeginn, bis der Häuptling zum ersten Mal losgeschickt wurde. */
	private static void drawStartHint(GuiGraphicsExtractor g, Font font) {
		Component title = Component.translatable("hud.goblinforest.start_hint.title");
		Component text = Component.translatable("hud.goblinforest.start_hint", ModKeys.label("chieftain:toggle"));
		Component camera = Component.translatable("hud.goblinforest.start_hint.camera");
		int cx = g.guiWidth() / 2;
		int maxW = g.guiWidth() - 16;
		int w = Math.min(maxW, Math.max(font.width(title), Math.max(font.width(text), font.width(camera))) + 12);
		int y = g.guiHeight() / 2 - 20;
		panel(g, cx - w / 2, y - 4, w, 37);
		g.centeredText(font, title, cx, y, GOLD);
		g.centeredText(font, fit(font, text.getString(), w - 8), cx, y + 11, TEXT);
		g.centeredText(font, fit(font, camera.getString(), w - 8), cx, y + 22, MUTED);
	}

	private static String fit(Font font, String text, int width) {
		if (font.width(text) <= width) {
			return text;
		}
		return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
	}

	private static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, PANEL);
		g.outline(x, y, w, h, BORDER);
	}

	private static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float progress, int color) {
		g.fill(x, y, x + w, y + h, 0xFF202020);
		int filled = (int) (w * Mth.clamp(progress, 0f, 1f));
		if (filled > 0) {
			g.fill(x, y, x + filled, y + h, color);
		}
	}
}
