package io.github.jan1a234.goblinforest.game;

import java.util.Locale;

/**
 * Schwierigkeit des KI-Clans. Je nach Stufe bekommt die KI mehr oder weniger passives Gold und entscheidet
 * schneller und klüger (auch beim Einsatz ihres Häuptlings).
 */
public enum AiDifficulty {
	EASY("leicht", 60, 0.75, 0, 0.0, false),
	NORMAL("normal", 30, 1.05, 0, 0.6, true),
	HARD("schwer", 16, 1.3, 60, 1.0, true);

	private final String id;
	private final int decisionTicks;
	private final double incomeMultiplier;
	private final int bonusStartGold;
	private final double spellChance;
	private final boolean counters;

	AiDifficulty(String id, int decisionTicks, double incomeMultiplier, int bonusStartGold, double spellChance, boolean counters) {
		this.id = id;
		this.decisionTicks = decisionTicks;
		this.incomeMultiplier = incomeMultiplier;
		this.bonusStartGold = bonusStartGold;
		this.spellChance = spellChance;
		this.counters = counters;
	}

	public String id() {
		return id;
	}

	public String translationKey() {
		return "difficulty.goblinforest." + id;
	}

	/** Ticks zwischen zwei Entscheidungen (etwas Zufall kommt dazu). */
	public int decisionTicks() {
		return decisionTicks;
	}

	/** Faktor auf das passive Einkommen (über 1 = Vorteil für die KI). */
	public double incomeMultiplier() {
		return incomeMultiplier;
	}

	public int bonusStartGold() {
		return bonusStartGold;
	}

	/** Wahrscheinlichkeit, eine gute Gelegenheit für einen Zauber auch zu nutzen (0 = nie). */
	public double spellChance() {
		return spellChance;
	}

	/** Stellt die KI ihre Armee gezielt gegen die gegnerische auf und kauft Upgrades? */
	public boolean counters() {
		return counters;
	}

	/** Liest „leicht“, „easy“, „schwer“ usw.; null, wenn unbekannt. */
	public static AiDifficulty parse(String text) {
		return switch (text.toLowerCase(Locale.ROOT)) {
			case "leicht", "easy" -> EASY;
			case "normal", "mittel", "medium" -> NORMAL;
			case "schwer", "hard" -> HARD;
			default -> null;
		};
	}
}
