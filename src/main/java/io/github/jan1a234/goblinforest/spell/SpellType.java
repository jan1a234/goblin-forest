package io.github.jan1a234.goblinforest.spell;

/** Zauber des Häuptlings (DESIGN.md Abschnitt 8). Werte stehen in balance.json unter {@code spells}. */
public enum SpellType {
	/** Flächenschaden an der anvisierten Stelle. */
	FIREBALL("fireball"),
	/** Heilt eigene Einheiten (und den Häuptling) an der anvisierten Stelle. */
	HEALING("healing");

	private final String id;

	SpellType(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public String translationKey() {
		return "spell.goblinforest." + id;
	}

	public static SpellType byId(String id) {
		for (SpellType type : values()) {
			if (type.id.equals(id)) {
				return type;
			}
		}
		return null;
	}
}
