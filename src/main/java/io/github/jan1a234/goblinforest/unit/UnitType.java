package io.github.jan1a234.goblinforest.unit;

/** Die rekrutierbaren Soldatentypen (DESIGN.md Abschnitt 5.1). Werte stehen in balance.json. */
public enum UnitType {
	SLAVE("slave"),
	WARRIOR("warrior"),
	ARCHER("archer"),
	ASSASSIN("assassin"),
	/** Heilt Verbündete in der Nähe und schießt Geisterblitze. */
	SHAMAN("shaman"),
	/** Sehr schnell, der erste Treffer nach einer Pause ist ein Ansturm mit Rückstoß. */
	WOLF_RIDER("wolfRider"),
	/** Langsamer Koloss: viel Leben, Flächenschlag mit Rückstoß, stark gegen Gebäude. */
	TROLL("troll"),
	/** Belagerungswaffe: greift nur Gebäude an, aus großer Entfernung und mit Flächenschaden. */
	CATAPULT("catapult");

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

	/** Kurzname für die Armee-Zeile im HUD. */
	public String shortKey() {
		return "unit.goblinforest." + id + ".short";
	}

	/** Greift diese Einheit ausschließlich Gebäude an? */
	public boolean siegeOnly() {
		return this == CATAPULT;
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
