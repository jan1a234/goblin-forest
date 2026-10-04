package io.github.jan1a234.goblinforest.hero;

/** Fähigkeiten des Häuptlings (DESIGN.md Abschnitt 6). Werte stehen in balance.json unter {@code abilities}. */
public enum AbilityType {
	/** Eigene Einheiten in der Nähe greifen eine Zeit lang schneller an. */
	BLOODLUST("bloodlust"),
	/** Flächenschlag um den Häuptling, schleudert Gegner zurück. */
	BATTLE_SLAM("battleSlam");

	private final String id;

	AbilityType(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public String translationKey() {
		return "ability.goblinforest." + id;
	}

	public static AbilityType byId(String id) {
		for (AbilityType type : values()) {
			if (type.id.equals(id)) {
				return type;
			}
		}
		return null;
	}
}
