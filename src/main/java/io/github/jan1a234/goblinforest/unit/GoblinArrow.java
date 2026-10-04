package io.github.jan1a234.goblinforest.unit;

import io.github.jan1a234.goblinforest.game.Match;
import io.github.jan1a234.goblinforest.game.MatchManager;
import io.github.jan1a234.goblinforest.game.TeamColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Pfeil eines Goblin-Bogenschützen. Fliegt durch Verbündete hindurch, trifft mit genau dem Schaden des Schützen
 * (statt geschwindigkeitsabhängig wie Vanilla) und verschwindet kurz nach dem Aufprall.
 */
public class GoblinArrow extends Arrow {
	private final TeamColor team;
	private final double damage;

	public GoblinArrow(Level level, LivingEntity owner, TeamColor team, double damage) {
		super(level, owner, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
		this.team = team;
		this.damage = damage;
		this.pickup = AbstractArrow.Pickup.DISALLOWED;
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		if (!super.canHitEntity(entity)) {
			return false;
		}
		Match match = MatchManager.match();
		return match == null || match.teamOf(entity) != team;
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		Entity target = hit.getEntity();
		if (damage > 0 && target instanceof LivingEntity living && level() instanceof ServerLevel serverLevel) {
			Match match = MatchManager.match();
			if (getOwner() instanceof GoblinUnit shooter && match != null && match.owns(shooter)) {
				shooter.dealDamage(serverLevel, match, living, damage);
			} else {
				living.invulnerableTime = 0;
				living.hurtServer(serverLevel, damageSources().arrow(this, getOwner()), (float) damage);
			}
			serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.ARROW_HIT, SoundSource.NEUTRAL, 0.6f, 1.2f);
		}
		discard();
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide() && (isInGround() && inGroundTime > 30 || tickCount > 200)) {
			discard();
		}
	}
}
