package io.github.jan1a234.goblinforest.unit;

/** Die rekrutierbaren Soldatentypen (DESIGN.md Abschnitt 5.1). Werte stehen in balance.json. */
public enum UnitType {
	SLAVE("slave"),
	WARRIOR("warrior"),
	ARCHER("archer"),
	ASSASSIN("assassin");

	private final String id;

	UnitType(String id) {
		this.id = id;
	}

	/** Schlüssel in balance.json und Teil der Registry-IDs. */
	public String id() {
		return id;
	}

	public String translationKey() {
		return "unit.goblinforest." + id;
	}

	public static UnitType byId(String id) {
		for (UnitType type : values()) {
			if (type.id.equals(id)) {
				return type;
			}
		}
		return null;
	}
}
