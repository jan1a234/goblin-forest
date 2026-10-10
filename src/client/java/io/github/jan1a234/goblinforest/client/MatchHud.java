package io.github.jan1a234.goblinforest.client;

import io.github.jan1a234.goblinforest.game.MatchPhase;
import io.github.jan1a234.goblinforest.game.PurchaseResult;
import io.github.jan1a234.goblinforest.hero.AbilityType;
import io.github.jan1a234.goblinforest.spell.SpellType;
import java.util.ArrayList;
import java.util.List;
import io.github.jan1a234.goblinforest.game.Stance;
import io.github.jan1a234.goblinforest.game.TeamColor;
import io.github.jan1a234.goblinforest.net.MatchStatePayload;
import io.github.jan1a234.goblinforest.unit.UnitType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Match-HUD: Ressourcen oben links, Festungen und Türme oben in der Mitte (darunter Sudden Death und Runden),
 * Zauber, Fähigkeiten und Raserei unten rechts
 * und eine eigene Lebensleiste des Häuptlings anstelle der Herzen.
 */
public final class MatchHud {
	private static final int PANEL = 0xA0101010;
	private static final int BORDER = 0xFF3A2A12;
	private static final int GOLD = 0xFFF2C744;
	private static final int TEXT = 0xFFE8E0C8;
	private static final int MUTED = 0xFFA09A88;
	private static final int REPUTATION = 0xFF5FD3E0;
	private static final int XP = 0xFF8BD449;

	private MatchHud() {
	}

	private static Minecraft mc() {
		return Minecraft.getInstance();
	}

	public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
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
		drawResources(g, font, s);
		drawCooldowns(g, font, s);
		drawNotices(g, font, s);
		if (CommandView.active()) {
			drawCommandView(g, font);
		} else if (s.respawnSeconds() <= 0 && (phase == MatchPhase.COUNTDOWN || phase == MatchPhase.BATTLE)
				&& ClientMatchState.stillInFortress()) {
			drawLeaveFortressHint(g, font);
		}
		if (s.respawnSeconds() > 0) {
			Component text = Component.translatable("hud.goblinforest.respawn", s.respawnSeconds());
			g.centeredText(font, text, g.guiWidth() / 2, g.guiHeight() / 2 + 20, 0xFFFF6B5B);
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

	/** Höhe der Festungsleiste oben; alles andere beginnt darunter, damit sich bei kleiner GUI nichts überlappt. */
	private static final int TOP_BAR_HEIGHT = 32;

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
			int labelSpace = barWidth - font.width(coreText) - 4;
			String labelText = label.getString();
			if (font.width(labelText) > labelSpace) {
				labelText = font.plainSubstrByWidth(labelText, labelSpace - font.width("…")) + "…";
			}
			// Name außen, Lebenspunkte des Kerns innen (zur Uhr hin).
			g.text(font, labelText, left ? x : x + barWidth - font.width(labelText), y, color, true);
			g.text(font, coreText, left ? x + barWidth - font.width(coreText) : x, y, TEXT, true);
			float core = s.coreMaxHealth()[i] <= 0 ? 0 : s.coreHealth()[i] / s.coreMaxHealth()[i];
			bar(g, x, y + 10, barWidth, 6, core, color);
			float tower = s.towerMaxHealth()[i] <= 0 ? 0 : s.towerHealth()[i] / s.towerMaxHealth()[i];
			bar(g, x, y + 18, barWidth, 3, tower, tower > 0 ? 0xFFB0B0B0 : 0xFF505050);
		}
	}

	private static void drawResources(GuiGraphicsExtractor g, Font font, MatchStatePayload s) {
		int x = 6;
		int y = TOP_BAR_HEIGHT + 6;
		int w = 140;
		panel(g, x - 4, y - 4, w + 8, 98);
		Component gold = Component.translatable("hud.goblinforest.gold", s.gold());
		g.text(font, gold, x, y, GOLD, true);
		int gain = ClientMatchState.recentGoldGain();
		if (gain > 0) {
			g.text(font, "+" + gain, x + font.width(gold) + 4, y, 0xFFFFF59A, true);
		}
		String income = String.format("+%.1f/s", s.income());
		g.text(font, income, x + w - font.width(income), y, 0xFFB8A050, true);
		y += 12;
		g.text(font, Component.translatable("hud.goblinforest.reputation", s.reputation()), x, y, REPUTATION, true);
		bar(g, x + 60, y + 2, w - 60, 4, s.reputationProgress(), REPUTATION);
		y += 12;
		boolean full = s.population() >= s.populationLimit();
		g.text(font, Component.translatable("hud.goblinforest.population", s.population(), s.populationLimit()), x, y, full ? 0xFFFF8060 : TEXT, true);
		y += 12;
		Stance stance = Stance.values()[Mth.clamp(s.stance(), 0, Stance.values().length - 1)];
		Component stanceText = Component.translatable(stance.translationKey());
		if (s.rallySet()) {
			stanceText = Component.translatable("hud.goblinforest.at_rally", stanceText);
		}
		g.text(font, Component.translatable("hud.goblinforest.stance", stanceText, ModKeys.label("stance:next")), x, y, TEXT, true);
		y += 12;
		g.text(font, Component.translatable("hud.goblinforest.hero", s.heroLevel()), x, y, XP, true);
		bar(g, x + 60, y + 2, w - 60, 4, s.heroProgress(), XP);
		y += 12;
		StringBuilder army = new StringBuilder();
		for (UnitType type : UnitType.values()) {
			int count = s.unitCounts().length > type.ordinal() ? s.unitCounts()[type.ordinal()] : 0;
			if (count > 0) {
				army.append(Component.translatable(type.shortKey()).getString()).append(' ').append(count).append("  ");
			}
		}
		String armyText = army.isEmpty() ? Component.translatable("hud.goblinforest.no_army").getString() : army.toString().trim();
		if (font.width(armyText) > w) {
			armyText = font.plainSubstrByWidth(armyText, w - font.width("…")) + "…";
		}
		g.text(font, armyText, x, y, MUTED, true);
		y += 12;
		g.text(font, Component.translatable("hud.goblinforest.enemy", s.enemyUnits(), s.enemyHeroLevel(), s.enemyReputation()), x, y, 0xFFE09080, true);
		y += 12;
		g.text(font, Component.translatable("hud.goblinforest.menu_hint", ModKeys.MENU.getTranslatedKeyMessage()), x, y, MUTED, true);
	}

	private static void drawCooldowns(GuiGraphicsExtractor g, Font font, MatchStatePayload s) {
		List<String[]> entries = new ArrayList<>();
		for (SpellType spell : SpellType.values()) {
			entries.add(new String[] {spell.id(), "cast:" + spell.id(), spell.translationKey()});
		}
		for (AbilityType ability : AbilityType.values()) {
			entries.add(new String[] {ability.id(), "ability:" + ability.id(), ability.translationKey()});
		}
		int w = 112;
		int x = g.guiWidth() - w - 6;
		// Rechts unten neben der Hotbar; reicht der Platz dafür nicht, über Hotbar und Lebensleiste.
		int bottom = x - 4 >= g.guiWidth() / 2 + 95 ? g.guiHeight() - 6 : g.guiHeight() - 48;
		int y = bottom - entries.size() * 12;
		panel(g, x - 4, y - 4, w + 8, entries.size() * 12 + 6);
		for (String[] entry : entries) {
			boolean rage = AbilityType.RAGE.id().equals(entry[0]);
			MatchStatePayload.Cooldown cooldown = s.cooldown(entry[0]);
			MatchStatePayload.ShopEntry cast = s.shopEntry(entry[1]);
			boolean isSpell = entry[1].startsWith("cast:");
			boolean locked = isSpell && cast != null && cast.status() == PurchaseResult.REPUTATION_TOO_LOW.ordinal();
			String key = ModKeys.label(entry[1]);
			String keyText = key.isEmpty() ? "" : "[" + key + "] ";
			g.text(font, keyText, x, y, MUTED, true);
			int nameX = x + font.width(keyText);
			int remaining = cooldown == null ? 0 : cooldown.remaining();
			String right;
			int rightColor;
			int nameColor = TEXT;
			if (rage) {
				if (s.rageSeconds() > 0) {
					right = s.rageSeconds() + "s";
					rightColor = 0xFFFF4020;
					nameColor = 0xFFFF6040;
					bar(g, nameX, y + 9, x + w - nameX, 1, 1f, 0xFFFF4020);
				} else if (s.rageCharge() >= 1f) {
					right = "✔";
					rightColor = (ClientMatchState.ticks() / 5) % 2 == 0 ? 0xFFFF4020 : 0xFFFFB060;
				} else {
					right = Math.round(s.rageCharge() * 100) + "%";
					rightColor = MUTED;
					nameColor = MUTED;
					bar(g, nameX, y + 9, x + w - nameX, 1, s.rageCharge(), 0xFFD0301A);
				}
			} else if (locked) {
				right = Component.translatable("hud.goblinforest.locked", cast.reputation()).getString();
				rightColor = 0xFF707070;
				nameColor = 0xFF707070;
			} else if (remaining > 0) {
				right = ((remaining + 19) / 20) + "s";
				rightColor = 0xFFFFB060;
				nameColor = MUTED;
				float progress = cooldown.total() <= 0 ? 0 : 1f - remaining / (float) cooldown.total();
				g.fill(nameX, y + 9, nameX + (int) ((x + w - nameX) * progress), y + 10, 0xFFFFB060);
			} else {
				right = isSpell && cast != null ? cast.cost() + "g" : "✔";
				rightColor = isSpell && cast != null && cast.status() == PurchaseResult.NOT_ENOUGH_GOLD.ordinal() ? 0xFFFF6B5B : 0xFF8BD449;
			}
			Component name = Component.translatable(entry[2]);
			int maxName = x + w - font.width(right) - 4 - nameX;
			String nameText = name.getString();
			if (font.width(nameText) > maxName) {
				nameText = font.plainSubstrByWidth(nameText, maxName - font.width("…")) + "…";
			}
			g.text(font, nameText, nameX, y, nameColor, true);
			g.text(font, right, x + w - font.width(right), y, rightColor, true);
			y += 12;
		}
	}

	/**
	 * Wegweiser zu Beginn jeder Runde, bis der Häuptling die eigene Festung verlassen hat:
	 * Der Spieler ist selbst der Häuptling und läuft mit den Bewegungstasten durchs Tor.
	 */
	private static void drawLeaveFortressHint(GuiGraphicsExtractor g, Font font) {
		Options options = mc().options;
		String keys = String.join("/", options.keyUp.getTranslatedKeyMessage().getString(), options.keyLeft.getTranslatedKeyMessage().getString(),
				options.keyDown.getTranslatedKeyMessage().getString(), options.keyRight.getTranslatedKeyMessage().getString());
		Component title = Component.translatable("hud.goblinforest.leave_fortress.title");
		Component text = Component.translatable("hud.goblinforest.leave_fortress", keys);
		int cx = g.guiWidth() / 2;
		int y = g.guiHeight() / 2 + 22;
		int w = Math.min(g.guiWidth() - 8, Math.max(font.width(title), font.width(text)) + 12);
		panel(g, cx - w / 2, y - 4, w, 26);
		g.centeredText(font, title, cx, y, GOLD);
		g.centeredText(font, text, cx, y + 11, TEXT);
	}

	/** Fadenkreuz in der Bildmitte (dort landen Zauber und Sammelpunkt) und eine kurze Bedienhilfe. */
	private static void drawCommandView(GuiGraphicsExtractor g, Font font) {
		int cx = g.guiWidth() / 2;
		int cy = g.guiHeight() / 2;
		g.fill(cx - 6, cy, cx - 2, cy + 1, GOLD);
		g.fill(cx + 2, cy, cx + 6, cy + 1, GOLD);
		g.fill(cx, cy - 6, cx + 1, cy - 2, GOLD);
		g.fill(cx, cy + 2, cx + 1, cy + 6, GOLD);
		g.outline(cx - 1, cy - 1, 3, 3, GOLD);
		g.centeredText(font, Component.translatable("hud.goblinforest.command_view", ModKeys.COMMAND_VIEW.getTranslatedKeyMessage()), cx, cy + 12, GOLD);
	}

	/** Hinweise in der Bildschirmmitte oben: Sudden Death, Best-of-Stand, freie Fähigkeitspunkte. */
	private static void drawNotices(GuiGraphicsExtractor g, Font font, MatchStatePayload s) {
		int cx = g.guiWidth() / 2;
		int y = TOP_BAR_HEIGHT + 4;
		if (s.bestOf() > 1) {
			Component rounds = Component.translatable("hud.goblinforest.rounds", s.roundWins()[0], s.roundWins()[1], s.bestOf());
			g.centeredText(font, rounds, cx, y, TEXT);
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
		if (s.abilityPoints() > 0 && s.respawnSeconds() <= 0) {
			g.centeredText(font, Component.translatable("hud.goblinforest.ability_points", s.abilityPoints(),
					ModKeys.MENU.getTranslatedKeyMessage()), cx, y, GOLD);
		}
	}

	/** Lebensleiste des Häuptlings statt der Vanilla-Herzen (ein Häuptling hat bis zu 100 Leben). */
	public static void extractHealth(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (mc().player == null) {
			return;
		}
		Font font = mc().font;
		int x = g.guiWidth() / 2 - 91;
		int y = g.guiHeight() - 39;
		int w = 81;
		float health = mc().player.getHealth();
		float max = mc().player.getMaxHealth();
		g.fill(x - 1, y - 1, x + w + 1, y + 9, 0xFF000000);
		g.fill(x, y, x + w, y + 8, 0xFF3A0E0E);
		g.fill(x, y, x + (int) (w * Mth.clamp(health / max, 0, 1)), y + 8, 0xFFC0392B);
		g.fill(x, y, x + (int) (w * Mth.clamp(health / max, 0, 1)), y + 2, 0xFFE06050);
		String text = (int) Math.ceil(health) + " / " + (int) max;
		g.text(font, text, x + (w - font.width(text)) / 2, y, 0xFFFFFFFF, true);
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
