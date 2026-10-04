package io.github.jan1a234.goblinforest.game;

/** Haltung aller eigenen Einheiten (DESIGN.md Abschnitt 5.4). */
public enum Stance {
	/** Die Lane bis zur feindlichen Festung ablaufen. */
	ADVANCE("advance"),
	/** Am Sammelbanner bzw. an der aktuellen Position stehen bleiben und verteidigen. */
	HOLD("hold"),
	/** Zurück in die eigene Festung, dort langsam heilen. */
	RETREAT("retreat");

	private final String id;

	Stance(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public Stance next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public String translationKey() {
		return "stance.goblinforest." + id;
	}
}
