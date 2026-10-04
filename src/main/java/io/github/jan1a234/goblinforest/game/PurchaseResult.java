package io.github.jan1a234.goblinforest.game;

/** Ergebnis eines Kauf- oder Aktionswunsches; alles außer {@link #OK} wird dem Spieler als Hinweis angezeigt. */
public enum PurchaseResult {
	OK("ok"),
	NOT_ENOUGH_GOLD("not_enough_gold"),
	REPUTATION_TOO_LOW("reputation_too_low"),
	MAX_LEVEL("max_level"),
	POPULATION_FULL("population_full"),
	UNIT_CAP("unit_cap"),
	ON_COOLDOWN("on_cooldown"),
	NOT_AVAILABLE("not_available"),
	HERO_DEAD("hero_dead"),
	NO_ABILITY_POINTS("no_ability_points"),
	NOT_CHARGED("not_charged");

	private final String id;

	PurchaseResult(String id) {
		this.id = id;
	}

	public boolean ok() {
		return this == OK;
	}

	public String translationKey() {
		return "message.goblinforest.purchase." + id;
	}
}
