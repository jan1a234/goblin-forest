package io.github.jan1a234.goblinforest.upgrade;

import io.github.jan1a234.goblinforest.unit.UnitType;
import java.util.ArrayList;
import java.util.List;

/**
 * Ein konkretes Upgrade: Typ plus Einheitentyp (nur bei Einheiten-Upgrades). Wird im Netzwerk als Text
 * {@code "armor:warrior"} bzw. {@code "walls"} übertragen.
 */
public record UpgradeKey(UpgradeType type, UnitType unit) {
	public UpgradeKey {
		if (type.perUnitType() != (unit != null)) {
			throw new IllegalArgumentException("Upgrade " + type.id() + " passt nicht zu Einheit " + unit);
		}
	}

	/** Upgrade ohne Einheitentyp (Festung oder ganze Armee). */
	public static UpgradeKey stronghold(UpgradeType type) {
		return new UpgradeKey(type, null);
	}

	public static UpgradeKey unit(UpgradeType type, UnitType unit) {
		return new UpgradeKey(type, unit);
	}

	public String serialize() {
		return unit == null ? type.id() : type.id() + ":" + unit.id();
	}

	/** Liest einen Schlüssel aus {@link #serialize()}; ungültige Eingaben (z. B. von manipulierten Clients) ergeben null. */
	public static UpgradeKey parse(String text) {
		if (text == null) {
			return null;
		}
		int colon = text.indexOf(':');
		UpgradeType type = UpgradeType.byId(colon < 0 ? text : text.substring(0, colon));
		if (type == null) {
			return null;
		}
		UnitType unit = colon < 0 ? null : UnitType.byId(text.substring(colon + 1));
		if (type.perUnitType() != (unit != null)) {
			return null;
		}
		return new UpgradeKey(type, unit);
	}

	/** Alle sinnvollen Upgrades; Reichweite nur für Fernkämpfer (siehe {@code isRanged}). */
	public static List<UpgradeKey> all(java.util.function.Predicate<UnitType> isRanged) {
		List<UpgradeKey> keys = new ArrayList<>();
		for (UnitType unit : UnitType.values()) {
			keys.add(unit(UpgradeType.ARMOR, unit));
			keys.add(unit(UpgradeType.ATTACK, unit));
			if (isRanged.test(unit)) {
				keys.add(unit(UpgradeType.RANGE, unit));
			}
		}
		for (UpgradeType type : UpgradeType.values()) {
			if (!type.perUnitType()) {
				keys.add(stronghold(type));
			}
		}
		return keys;
	}
}
