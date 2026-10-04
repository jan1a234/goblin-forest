package io.github.jan1a234.goblinforest.client;

import io.github.jan1a234.goblinforest.game.MatchPhase;
import io.github.jan1a234.goblinforest.game.Stance;
import io.github.jan1a234.goblinforest.game.TeamColor;
import io.github.jan1a234.goblinforest.net.MatchStatePayload;
import io.github.jan1a234.goblinforest.unit.UnitType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Match-HUD: Ressourcen oben links, Festungen und Türme oben in der Mitte, Abklingzeiten unten rechts
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

	private static void drawStructures(GuiGraphicsExtractor g, Font font, MatchStatePayload s) {
		int cx = g.guiWidth() / 2;
		int barWidth = 90;
		int y = 4;
		panel(g, cx - barWidth - 30, y - 2, barWidth * 2 + 60, 30);
		String time = String.format("%d:%02d", s.matchSeconds() / 60, s.matchSeconds() % 60);
		g.centeredText(font, time, cx, y + 9, TEXT);
		for (TeamColor team : TeamColor.values()) {
			int i = team.ordinal();
			boolean left = team == TeamColor.RED;
			int x = left ? cx - barWidth - 24 : cx + 24;
			int color = 0xFF000000 | team.rgb();
			Component label = Component.translatable(team.translationKey());
			if (i == s.team()) {
				label = Component.translatable("hud.goblinforest.you", label);
			}
			int labelX = left ? x : x + barWidth - font.width(label);
			g.text(font, label, labelX, y, color, true);
			float core = s.coreMaxHealth()[i] <= 0 ? 0 : s.coreHealth()[i] / s.coreMaxHealth()[i];
			bar(g, x, y + 10, barWidth, 6, core, color);
			String coreText = (int) Math.ceil(s.coreHealth()[i]) + "";
			g.text(font, coreText, left ? x + barWidth + 3 : x - 3 - font.width(coreText), y + 9, TEXT, true);
			float tower = s.towerMaxHealth()[i] <= 0 ? 0 : s.towerHealth()[i] / s.towerMaxHealth()[i];
			bar(g, x, y + 19, barWidth, 3, tower, tower > 0 ? 0xFFB0B0B0 : 0xFF505050);
		}
	}

	private static void drawResources(GuiGraphicsExtractor g, Font font, MatchStatePayload s) {
		int x = 6;
		int y = 6;
		int w = 150;
		panel(g, x - 4, y - 4, w + 8, 98);
		Component gold = Component.translatable("hud.goblinforest.gold", s.gold());
		g.text(font, gold, x, y, GOLD, true);
		int gain = ClientMatchState.recentGoldGain();
		if (gain > 0) {
			g.text(font, "+" + gain, x + font.width(gold) + 4, y, 0xFFFFF59A, true);
		}
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
			army.append(Component.translatable(type.translationKey()).getString(), 0, 1).append(count).append("  ");
		}
		g.text(font, army.toString().trim(), x, y, MUTED, true);
		y += 12;
		g.text(font, Component.translatable("hud.goblinforest.enemy", s.enemyUnits(), s.enemyHeroLevel(), s.enemyReputation()), x, y, 0xFFE09080, true);
		y += 12;
		g.text(font, Component.translatable("hud.goblinforest.menu_hint", ModKeys.MENU.getTranslatedKeyMessage()), x, y, MUTED, true);
	}

	private static void drawCooldowns(GuiGraphicsExtractor g, Font font, MatchStatePayload s) {
		String[][] entries = {
				{"fireball", "cast:fireball", "spell.goblinforest.fireball"},
				{"healing", "cast:healing", "spell.goblinforest.healing"},
				{"bloodlust", "ability:bloodlust", "ability.goblinforest.bloodlust"},
				{"battleSlam", "ability:battleSlam", "ability.goblinforest.battleSlam"}};
		int w = 128;
		int x = g.guiWidth() - w - 6;
		int y = g.guiHeight() - 6 - entries.length * 12;
		panel(g, x - 4, y - 4, w + 8, entries.length * 12 + 6);
		for (String[] entry : entries) {
			MatchStatePayload.Cooldown cooldown = s.cooldown(entry[0]);
			MatchStatePayload.ShopEntry cast = s.shopEntry(entry[1]);
			boolean locked = cast != null && cast.status() == io.github.jan1a234.goblinforest.game.PurchaseResult.REPUTATION_TOO_LOW.ordinal();
			String key = ModKeys.label(entry[1]);
			g.text(font, "[" + key + "] ", x, y, MUTED, true);
			int nameX = x + font.width("[" + key + "] ");
			int remaining = cooldown == null ? 0 : cooldown.remaining();
			int color = locked ? 0xFF707070 : remaining > 0 ? MUTED : TEXT;
			g.text(font, Component.translatable(entry[2]), nameX, y, color, true);
			String right;
			int rightColor;
			if (locked) {
				right = Component.translatable("hud.goblinforest.locked", cast.reputation()).getString();
				rightColor = 0xFF707070;
			} else if (remaining > 0) {
				right = ((remaining + 19) / 20) + "s";
				rightColor = 0xFFFFB060;
				float progress = cooldown.total() <= 0 ? 0 : 1f - remaining / (float) cooldown.total();
				g.fill(nameX, y + 9, nameX + (int) ((x + w - nameX) * progress), y + 10, 0xFFFFB060);
			} else {
				right = cast != null ? cast.cost() + "g" : "✔";
				rightColor = cast != null && cast.status() == io.github.jan1a234.goblinforest.game.PurchaseResult.NOT_ENOUGH_GOLD.ordinal() ? 0xFFFF6B5B : 0xFF8BD449;
			}
			g.text(font, right, x + w - font.width(right), y, rightColor, true);
			y += 12;
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
