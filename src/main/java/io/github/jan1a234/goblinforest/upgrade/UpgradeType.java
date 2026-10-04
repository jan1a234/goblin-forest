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
	/** Festungsturm: mehr Schaden ({@code valuePerLevel}) und Reichweite ({@code stronghold.towerRangePerLevel}). */
	TOWER("tower", Scope.STRONGHOLD),
	/** Mauern: mehr Lebenspunkte für den Festungskern ({@code valuePerLevel} = HP pro Stufe). */
	WALLS("walls", Scope.STRONGHOLD),
	/** Goblinhütten: höheres Bevölkerungslimit ({@code valuePerLevel} = Plätze pro Stufe). */
	HUTS("huts", Scope.STRONGHOLD);

	public enum Scope {
		UNIT,
		RANGED_UNIT,
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
		return scope != Scope.STRONGHOLD;
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
