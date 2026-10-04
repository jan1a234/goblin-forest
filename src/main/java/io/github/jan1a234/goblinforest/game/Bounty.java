package io.github.jan1a234.goblinforest.game;

import io.github.jan1a234.goblinforest.config.Balance;

/**
 * Kopfgeld und Clan-Erfahrung für Kills (DESIGN.md Abschnitt 7). Reine Rechenlogik.
 *
 * <p>Der Fortschritt {@code progress} beschreibt, wo ein Kill passiert ist, aus Sicht des Clans, der belohnt wird:
 * 0 = an der eigenen Festung, 1 = direkt an der feindlichen Festung.
 */
public final class Bounty {
	private final Balance.Economy economy;

	public Bounty(Balance.Economy economy) {
		this.economy = economy;
	}

	/** Frontfaktor: 1,0 in der eigenen Hälfte, steigt in der feindlichen Hälfte linear bis 2,0 an der Festung. */
	public double frontFactor(double progress) {
		double p = Math.clamp(progress, 0.0, 1.0);
		if (p <= 0.5) {
			return economy.frontFactorMin();
		}
		double t = (p - 0.5) / 0.5;
		return economy.frontFactorMin() + (economy.frontFactorMax() - economy.frontFactorMin()) * t;
	}

	/** Grundwert vor dem Frontfaktor: Anteil der Kosten, plus Veteranenbonus pro Level über 1. */
	public double baseValue(Balance.UnitStats stats, int unitLevel) {
		double veteran = 1.0 + economy.veteranBountyBonusPerLevel() * (Math.max(1, unitLevel) - 1);
		return stats.costPerUnit() * economy.bountyShareOfCost() * veteran;
	}

	/** Gold für den Clan, der die Einheit getötet hat. */
	public int killBounty(Balance.UnitStats stats, int unitLevel, double killerProgress) {
		return (int) Math.round(baseValue(stats, unitLevel) * frontFactor(killerProgress));
	}

	/**
	 * Gold für den Clan, der die Einheit verloren hat: wie im Original gibt es auch für eigene Verluste etwas,
	 * aber nur, wenn sie in der feindlichen Hälfte gefallen sind.
	 */
	public int ownLossRefund(Balance.UnitStats stats, int unitLevel, double ownerProgress) {
		if (ownerProgress <= 0.5) {
			return 0;
		}
		return (int) Math.round(baseValue(stats, unitLevel) * frontFactor(ownerProgress) * economy.ownLossBountyShare());
	}

	/** Clan-Erfahrung für einen Einheiten-Kill (ohne Frontfaktor). */
	public double clanXpForKill(Balance.UnitStats stats, int unitLevel) {
		double veteran = 1.0 + economy.veteranBountyBonusPerLevel() * (Math.max(1, unitLevel) - 1);
		return stats.costPerUnit() * economy.clanXpShareOfCost() * veteran;
	}

	public int heroKillBounty() {
		return economy.heroKillBounty();
	}

	public double heroKillClanXp() {
		return economy.heroKillClanXp();
	}
}
