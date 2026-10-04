package io.github.jan1a234.goblinforest.upgrade;

/**
 * Kaufbare Verbesserungen (DESIGN.md Abschnitte 5.3 und 9). Die Kosten und Wirkungen stehen in balance.json unter
 * {@code upgrades.<id>}.
 */
public enum UpgradeType {
	/** Pro Einheitentyp: weniger erlittener Schaden ({@code valuePerLevel} = Anteil pro Stufe). */
	ARMOR("armor", Scope.UNIT),
	/** Pro Einheitentyp: mehr Schaden ({@code valuePerLevel} = Anteil pro Stufe). */
	ATTACK("attack", Scope.UNIT),
	/** Nur Fernkämpfer: mehr Reichweite ({@code valuePerLevel} = Blöcke pro Stufe). */
	RANGE("range", Scope.RANGED_UNIT),
	/** Kaserne: neue Einheiten starten mit {@code valuePerLevel} Veteranen-Leveln mehr pro Stufe. */
	TRAINING("training", Scope.ARMY),
	/** Kaserne: alle Einheiten laufen schneller ({@code valuePerLevel} = Anteil pro Stufe). */
	ENDURANCE("endurance", Scope.ARMY),
	/** Festungsturm: mehr Schaden ({@code valuePerLevel}) und Reichweite ({@code stronghold.towerRangePerLevel}). */
	TOWER("tower", Scope.STRONGHOLD),
	/** Mauern: mehr Lebenspunkte für den Festungskern ({@code valuePerLevel} = HP pro Stufe). */
	WALLS("walls", Scope.STRONGHOLD),
	/** Goblinhütten: höheres Bevölkerungslimit ({@code valuePerLevel} = Plätze pro Stufe). */
	HUTS("huts", Scope.STRONGHOLD),
	/** Kanone auf der Mauer: Stufe 1 baut sie, jede weitere bringt {@code valuePerLevel} Schaden und schnelleres Nachladen. */
	CANNON("cannon", Scope.STRONGHOLD),
	/** Goldmine im Festungshof: {@code valuePerLevel} Gold pro Sekunde und Stufe. */
	GOLDMINE("goldmine", Scope.STRONGHOLD);

	public enum Scope {
		/** Pro Einheitentyp. */
		UNIT,
		/** Pro Einheitentyp, aber nur für Fernkämpfer. */
		RANGED_UNIT,
		/** Gilt für die ganze Armee. */
		ARMY,
		/** Ausbau der Festung. */
		STRONGHOLD
	}

	private final String id;
	private final Scope scope;

	UpgradeType(String id, Scope scope) {
		this.id = id;
		this.scope = scope;
	}

	public String id() {
		return id;
	}

	public Scope scope() {
		return scope;
	}

	public boolean perUnitType() {
		return scope == Scope.UNIT || scope == Scope.RANGED_UNIT;
	}

	public String translationKey() {
		return "upgrade.goblinforest." + id;
	}

	public static UpgradeType byId(String id) {
		for (UpgradeType type : values()) {
			if (type.id.equals(id)) {
				return type;
			}
		}
		return null;
	}
}
