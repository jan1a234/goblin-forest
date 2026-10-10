package io.github.jan1a234.goblinforest.unit;

import io.github.jan1a234.goblinforest.arena.ArenaLayout;
import io.github.jan1a234.goblinforest.config.Balance;
import io.github.jan1a234.goblinforest.game.Match;
import io.github.jan1a234.goblinforest.game.MatchManager;
import io.github.jan1a234.goblinforest.game.Stance;
import io.github.jan1a234.goblinforest.game.Structure;
import io.github.jan1a234.goblinforest.game.TeamColor;
import io.github.jan1a234.goblinforest.game.TeamState;
import io.github.jan1a234.goblinforest.upgrade.UpgradeType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ein Goblin-Soldat oder der Häuptling. Läuft die Lane ab, greift das nächste feindliche Ziel an (Einheiten, Häuptling,
 * Turm, Festungskern) und sammelt dabei selbst Erfahrung (DESIGN.md Abschnitt 5).
 *
 * <p>Der {@link UnitType#CHIEFTAIN Häuptling} ist dieselbe Einheit mit eigenen Regeln: Leben und Schaden kommen aus dem
 * Heldenlevel des Clans, er schlägt mit Flächenschaden zu, und solange der Spieler ihn nicht aufs Schlachtfeld schickt,
 * bewacht er seinen Posten in der Festung (DESIGN.md Abschnitt 6).
 *
 * <p>Die ganze KI steckt in {@link #customServerAiStep}: bewusst ohne Vanilla-Goals, weil Haltung, Lane und
 * Gebäudeangriffe sich damit nur umständlich ausdrücken lassen. Die Spielwerte kommen aus {@link UnitCombat}.
 */
public class GoblinUnit extends PathfinderMob {
	private static final EntityDataAccessor<Byte> DATA_UNIT = SynchedEntityData.defineId(GoblinUnit.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> DATA_TEAM = SynchedEntityData.defineId(GoblinUnit.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> DATA_LEVEL = SynchedEntityData.defineId(GoblinUnit.class, EntityDataSerializers.BYTE);

	/** Wie weit sich haltende Einheiten von ihrem Platz weglocken lassen. */
	private static final double HOLD_LEASH = 12.0;

	private UUID matchId;
	private double xp;
	private int attackCooldown;
	private int stealthTicks;
	private int bloodlustTicks;
	private double bloodlustBonus;
	private int rootedTicks;
	private int healTimer;
	private int ticksSinceAttack;
	private boolean chargeReady = true;
	private int retargetTimer;
	private int repathTimer;
	private int nameTimer;
	private float laneOffset;
	private Vec3 holdAnchor;
	private Stance lastStance;
	/** Häuptling, der (noch) nicht losgeschickt wurde: bewacht seinen Posten in der Festung. */
	private boolean guarding;
	private boolean lastGuarding;
	private LivingEntity enemy;
	private Structure structureTarget;
	private TeamColor pendingAttackerTeam;
	private TeamColor lastAttackerTeam;
	private Entity lastAttacker;
	private boolean deathHandled;
	private float lastShownHealth = -1;

	public GoblinUnit(EntityType<? extends GoblinUnit> type, Level level) {
		super(type, level);
		this.xpReward = 0;
		setPersistenceRequired();
		setCanPickUpLoot(false);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 40)
				.add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.ATTACK_DAMAGE, 4)
				.add(Attributes.FOLLOW_RANGE, 48)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_UNIT, (byte) 0);
		builder.define(DATA_TEAM, (byte) 0);
		builder.define(DATA_LEVEL, (byte) 1);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
	}

	// ---------------------------------------------------------------- Eigenschaften

	public UnitType unitType() {
		return UnitType.values()[Math.floorMod(entityData.get(DATA_UNIT), UnitType.values().length)];
	}

	public TeamColor team() {
		return TeamColor.values()[Math.floorMod(entityData.get(DATA_TEAM), TeamColor.values().length)];
	}

	public int unitLevel() {
		return Math.max(1, entityData.get(DATA_LEVEL));
	}

	public double xp() {
		return xp;
	}

	public UUID matchId() {
		return matchId;
	}

	public boolean isStealthed() {
		return stealthTicks > 0;
	}

	public boolean hasBloodlust() {
		return bloodlustTicks > 0;
	}

	/** Blutrausch des Häuptlings: {@code bonus} = zusätzliches Angriffstempo (0.3 = +30 %). */
	public void applyBloodlust(int ticks, double bonus) {
		if (ticks >= bloodlustTicks || bonus > bloodlustBonus) {
			bloodlustBonus = Math.max(bonus, bloodlustTicks > 0 ? bloodlustBonus : 0);
		}
		bloodlustTicks = Math.max(bloodlustTicks, ticks);
	}

	private double activeBloodlustBonus() {
		return bloodlustTicks > 0 ? bloodlustBonus : 0;
	}

	public boolean isRooted() {
		return rootedTicks > 0;
	}

	/** Wurzelfessel: die Einheit kann sich eine Zeit lang nicht bewegen, aber weiter zuschlagen. */
	public void root(int ticks) {
		rootedTicks = Math.max(rootedTicks, ticks);
		getNavigation().stop();
	}

	public boolean isChampion() {
		Match match = MatchManager.match();
		return match != null && !isChieftain() && match.combat().isChampion(unitLevel());
	}

	/** Ist diese Einheit ein Häuptling? */
	public boolean isChieftain() {
		return unitType() == UnitType.CHIEFTAIN;
	}

	/**
	 * Bereitet eine frisch erzeugte Einheit vor, bevor sie in die Welt kommt.
	 */
	public void setup(UnitType type, TeamColor team, Match match) {
		this.matchId = match.id();
		entityData.set(DATA_UNIT, (byte) type.ordinal());
		entityData.set(DATA_TEAM, (byte) team.ordinal());
		entityData.set(DATA_LEVEL, (byte) 1);
		this.laneOffset = (float) ((getRandom().nextDouble() - 0.5) * (ArenaLayout.LANE_HALF_WIDTH * 2 - 2.5));
		this.retargetTimer = getRandom().nextInt(8);
		Balance balance = match.balance();
		if (type == UnitType.ASSASSIN) {
			stealthTicks = balance.traits().assassinStealthSeconds() * 20;
			setInvisible(true);
		}
		getAttribute(Attributes.SCALE).setBaseValue(balance.unit(type).scale());
		if (type == UnitType.TROLL) {
			getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.9);
		} else if (type == UnitType.CATAPULT) {
			getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		} else if (type == UnitType.CHIEFTAIN) {
			getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.7);
			entityData.set(DATA_LEVEL, (byte) match.team(team).heroLevel());
			guarding = !match.chieftainDeployed(team);
		}
		int startLevel = type == UnitType.CHIEFTAIN ? 1 : match.team(team).startingUnitLevel(match.combat().leveling().maxLevel());
		if (startLevel > 1) {
			xp = match.combat().leveling().step(startLevel).xpRequired();
			entityData.set(DATA_LEVEL, (byte) startLevel);
		}
		applyStats(match, true);
		refreshEquipment();
		refreshName();
	}

	/** Überträgt Level-Boni auf Lebenspunkte und Tempo (beim Häuptling: Heldenlevel und Raserei). */
	private void applyStats(Match match, boolean fullHeal) {
		UnitCombat combat = match.combat();
		double maxHealth;
		double speed;
		if (isChieftain()) {
			maxHealth = match.chieftainMaxHealth(team());
			speed = match.balance().unit(UnitType.CHIEFTAIN).speed() * match.chieftainSpeedMultiplier(team());
		} else {
			maxHealth = combat.maxHealth(unitType(), unitLevel()) * match.team(team()).armyHealthMultiplier();
			speed = combat.speed(unitType(), unitLevel(), match.team(team()).armySpeedMultiplier());
		}
		double ratio = getMaxHealth() > 0 ? getHealth() / getMaxHealth() : 1.0;
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
		getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speed);
		setHealth((float) (fullHeal ? maxHealth : Math.max(1, maxHealth * ratio)));
	}

	/**
	 * Häuptling nach einem Heldenlevel-Aufstieg oder Beginn/Ende der Raserei neu einstellen. Bei einem Aufstieg
	 * ({@code levelUp}) kommt der Lebenszuwachs sofort dazu.
	 */
	public void refreshChieftain(boolean levelUp) {
		Match match = MatchManager.match();
		if (match == null || !match.owns(this) || !isChieftain()) {
			return;
		}
		float missing = getMaxHealth() - getHealth();
		entityData.set(DATA_LEVEL, (byte) match.team(team()).heroLevel());
		applyStats(match, false);
		if (levelUp) {
			setHealth(Math.max(1, getMaxHealth() - missing));
		}
		refreshName();
	}

	/** Der Spieler hat den Häuptling losgeschickt oder zurückgerufen: Ziele vergessen und neu orientieren. */
	public void onOrdersChanged() {
		enemy = null;
		structureTarget = null;
		retargetTimer = 0;
		repathTimer = 0;
		lastStance = null;
		getNavigation().stop();
	}

	/** Neu berechnen, nachdem ein Upgrade gekauft wurde, das lebende Einheiten betrifft (z. B. Ausdauer). */
	public void refreshStats() {
		Match match = MatchManager.match();
		if (match != null && match.owns(this)) {
			applyStats(match, false);
		}
	}

	private void refreshEquipment() {
		UnitType type = unitType();
		ItemStack weapon = switch (type) {
			case SLAVE -> new ItemStack(Items.WOODEN_AXE);
			case WARRIOR -> new ItemStack(Items.IRON_AXE);
			case ARCHER -> new ItemStack(Items.BOW);
			case ASSASSIN -> new ItemStack(Items.IRON_SWORD);
			case SHAMAN -> new ItemStack(Items.BLAZE_ROD);
			case WOLF_RIDER -> new ItemStack(Items.IRON_SPEAR);
			case TROLL -> new ItemStack(Items.MACE);
			case CATAPULT -> new ItemStack(Items.STICK);
			case CHIEFTAIN -> new ItemStack(Items.GOLDEN_AXE);
		};
		setItemSlot(EquipmentSlot.MAINHAND, cosmetic(weapon));
		setItemSlot(EquipmentSlot.OFFHAND, type == UnitType.WARRIOR ? cosmetic(new ItemStack(Items.SHIELD)) : ItemStack.EMPTY);
		int level = unitLevel();
		int color = team().rgb();
		if (type == UnitType.CHIEFTAIN) {
			setItemSlot(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE.getDefaultInstance(), color));
			setItemSlot(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS.getDefaultInstance(), color));
			setItemSlot(EquipmentSlot.HEAD, cosmetic(new ItemStack(Items.GOLDEN_HELMET)));
			// Immer leuchtend, damit man ihn in der Draufsicht im Getümmel findet.
			setGlowingTag(true);
			return;
		}
		setItemSlot(EquipmentSlot.CHEST, level >= 3 ? dyed(Items.LEATHER_CHESTPLATE.getDefaultInstance(), color) : ItemStack.EMPTY);
		setItemSlot(EquipmentSlot.LEGS, level >= 3 ? dyed(Items.LEATHER_LEGGINGS.getDefaultInstance(), color) : ItemStack.EMPTY);
		ItemStack helmet = ItemStack.EMPTY;
		if (type == UnitType.SHAMAN) {
			helmet = cosmetic(new ItemStack(Items.SKELETON_SKULL));
		} else if (level >= 5) {
			helmet = cosmetic(new ItemStack(Items.GOLDEN_HELMET));
		} else if (level >= 4) {
			helmet = cosmetic(new ItemStack(Items.IRON_HELMET));
		}
		setItemSlot(EquipmentSlot.HEAD, helmet);
		setGlowingTag(level >= 5);
	}

	/** Ausrüstung ist nur Optik: Rüstungswerte kommen ausschließlich aus den Clan-Upgrades. */
	private static ItemStack cosmetic(ItemStack stack) {
		stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		return stack;
	}

	private static ItemStack dyed(ItemStack stack, int rgb) {
		stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
		return cosmetic(stack);
	}

	private void refreshName() {
		int level = unitLevel();
		MutableComponent name = Component.empty();
		if (isChieftain()) {
			name.append(Component.literal("♛ ").withStyle(ChatFormatting.GOLD));
			name.append(Component.translatable("unit.goblinforest.chieftain.level", level).withStyle(ChatFormatting.GOLD));
			name.append(" ");
		} else if (level > 1) {
			name.append(Component.literal("★".repeat(level - 1)).withStyle(level >= 5 ? ChatFormatting.GOLD : ChatFormatting.YELLOW));
			name.append(" ");
		}
		name.append(Component.translatable(unitType().translationKey()).withColor(team().rgb()));
		name.append(" ");
		float fraction = getMaxHealth() > 0 ? getHealth() / getMaxHealth() : 0;
		int segments = 8;
		int filled = Math.max(getHealth() > 0 ? 1 : 0, Math.round(fraction * segments));
		ChatFormatting barColor = fraction > 0.6f ? ChatFormatting.GREEN : fraction > 0.3f ? ChatFormatting.YELLOW : ChatFormatting.RED;
		name.append(Component.literal("|".repeat(filled)).withStyle(barColor));
		name.append(Component.literal("|".repeat(segments - filled)).withStyle(ChatFormatting.DARK_GRAY));
		setCustomName(name);
		setCustomNameVisible(!isStealthed());
		lastShownHealth = getHealth();
	}

	// ---------------------------------------------------------------- Erfahrung

	public void addXp(double amount) {
		// Der Häuptling steigt über das Heldenlevel des Clans auf, nicht über Veteranenstufen.
		if (amount <= 0 || isDeadOrDying() || isChieftain()) {
			return;
		}
		Match match = MatchManager.match();
		if (match == null || !match.owns(this)) {
			return;
		}
		xp += amount;
		int newLevel = match.combat().leveling().levelForXp(xp);
		if (newLevel > unitLevel()) {
			entityData.set(DATA_LEVEL, (byte) newLevel);
			applyStats(match, true);
			refreshEquipment();
			refreshName();
			if (level() instanceof ServerLevel serverLevel) {
				serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, getX(), getY() + 1.2, getZ(), 20, 0.3, 0.6, 0.3, 0.25);
				serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.6f, 1.4f);
			}
			match.onUnitLevelUp(this);
		}
	}

	// ---------------------------------------------------------------- KI

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		Match match = MatchManager.match();
		if (match == null || !match.owns(this)) {
			discard();
			return;
		}
		tickTimers(level, match);
		if (!match.isBattle()) {
			getNavigation().stop();
			setAggressive(false);
			return;
		}
		TeamState own = match.team(team());
		guarding = isChieftain() && !match.chieftainDeployed(team());
		Stance stance = guarding ? Stance.HOLD : own.stance();
		if (stance != lastStance || guarding != lastGuarding) {
			onStanceChanged(stance);
			lastStance = stance;
			lastGuarding = guarding;
		}
		if (unitType() == UnitType.SHAMAN && --healTimer <= 0) {
			healTimer = match.balance().traits().shamanHealIntervalTicks();
			healAllies(level, match, own);
		}
		if (isRooted()) {
			getNavigation().stop();
			Vec3 motion = getDeltaMovement();
			setDeltaMovement(0, Math.min(0, motion.y), 0);
		}

		if (--retargetTimer <= 0) {
			retargetTimer = 8 + getRandom().nextInt(5);
			if (stance == Stance.RETREAT) {
				enemy = null;
				structureTarget = null;
			} else {
				acquireTarget(level, match, stance);
			}
		}
		if (enemy != null && (!isValidEnemy(enemy, match) || (stance == Stance.HOLD && leashBroken()))) {
			enemy = null;
			retargetTimer = 0;
		}

		if (enemy != null) {
			engage(level, match, own, enemy);
			return;
		}
		if (structureTarget != null) {
			if (match.structureAlive(team().opponent(), structureTarget)) {
				engageStructure(level, match, own, structureTarget);
				return;
			}
			structureTarget = null;
		}
		setAggressive(false);
		switch (stance) {
			case ADVANCE -> followLane(match);
			case HOLD -> holdPosition(match);
			case RETREAT -> retreat();
		}
	}

	private void tickTimers(ServerLevel level, Match match) {
		if (attackCooldown > 0) {
			attackCooldown--;
		}
		if (repathTimer > 0) {
			repathTimer--;
		}
		ticksSinceAttack++;
		if (unitType() == UnitType.WOLF_RIDER && !chargeReady
				&& ticksSinceAttack >= match.balance().traits().wolfChargeRechargeSeconds() * 20) {
			chargeReady = true;
			level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.3, getZ(), 4, 0.3, 0.1, 0.3, 0.02);
		}
		if (bloodlustTicks > 0) {
			bloodlustTicks--;
			if (tickCount % 6 == 0) {
				level.sendParticles(new DustParticleOptions(0xD0201A, 1.2f), getX(), getY() + 1.0, getZ(), 2, 0.25, 0.4, 0.25, 0);
			}
		}
		if (rootedTicks > 0) {
			rootedTicks--;
			if (tickCount % 8 == 0) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState()),
						getX(), getY() + 0.2, getZ(), 4, 0.3, 0.1, 0.3, 0.05);
			}
		}
		if (stealthTicks > 0 && --stealthTicks == 0) {
			reveal(level);
		}
		if (tickCount % 20 == 0) {
			ArenaLayout.Box zone = ArenaLayout.healZone(team());
			if (zone.contains(blockPosition().getX(), blockPosition().getY(), blockPosition().getZ()) && getHealth() < getMaxHealth()) {
				// Der Häuptling erholt sich in der Festung doppelt so schnell.
				double share = match.balance().traits().retreatHealPercentPerSecond() * (isChieftain() ? 2 : 1);
				heal((float) (getMaxHealth() * share));
				if (tickCount % 60 == 0) {
					level.sendParticles(ParticleTypes.HEART, getX(), getY() + 2.1, getZ(), 1, 0.2, 0.1, 0.2, 0);
				}
			}
			if (unitType() == UnitType.TROLL && isChampion() && getHealth() < getMaxHealth()) {
				heal((float) (getMaxHealth() * match.balance().unitLeveling().champion().trollRegenPercentPerSecond()));
			}
		}
		if (--nameTimer <= 0) {
			nameTimer = 10;
			if (getHealth() != lastShownHealth) {
				refreshName();
			}
		}
	}

	private void onStanceChanged(Stance stance) {
		holdAnchor = stance == Stance.HOLD && !guarding ? position() : null;
		if (stance == Stance.RETREAT) {
			enemy = null;
			structureTarget = null;
		}
		repathTimer = 0;
	}

	private boolean leashBroken() {
		Vec3 anchor = currentHoldAnchor(MatchManager.match());
		return anchor != null && position().distanceTo(anchor) > HOLD_LEASH;
	}

	private double attackReach(Match match, TeamState own) {
		if (ranged(match)) {
			return match.combat().attackRange(unitType(), own.unitUpgrade(UpgradeType.RANGE, unitType()));
		}
		return match.balance().traits().meleeReach();
	}

	private boolean ranged(Match match) {
		return match.balance().unit(unitType()).ranged();
	}

	private void acquireTarget(ServerLevel level, Match match, Stance stance) {
		TeamState own = match.team(team());
		double range = Math.max(match.balance().unit(unitType()).aggroRange(), ranged(match) ? attackReach(match, own) + 2 : 0);
		if (unitType().siegeOnly()) {
			enemy = null;
			structureTarget = findStructure(match, attackReach(match, own) + 2);
			return;
		}
		AABB box = getBoundingBox().inflate(range, 6, range);
		List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box, e -> e != this && isValidEnemy(e, match));
		Vec3 anchor = stance == Stance.HOLD ? currentHoldAnchor(match) : null;
		LivingEntity best = null;
		double bestScore = Double.MAX_VALUE;
		for (LivingEntity candidate : candidates) {
			double distance = distanceTo(candidate);
			if (distance > range) {
				continue;
			}
			if (anchor != null && candidate.position().distanceTo(anchor) > HOLD_LEASH + range * 0.5) {
				continue;
			}
			double score = distance;
			boolean isHero = candidate instanceof GoblinUnit unit && unit.isChieftain();
			if (unitType() == UnitType.ASSASSIN) {
				if (isHero) {
					score *= 0.4;
				} else if (candidate instanceof GoblinUnit unit && match.balance().unit(unit.unitType()).ranged()) {
					score *= 0.5;
				}
			} else if (unitType() == UnitType.WOLF_RIDER && candidate instanceof GoblinUnit unit && match.balance().unit(unit.unitType()).ranged()) {
				// Kavallerie reitet bevorzugt in die hinteren Reihen.
				score *= 0.6;
			} else if (isHero) {
				score *= 1.25;
			}
			if (!hasLineOfSight(candidate)) {
				score += 8;
			}
			if (score < bestScore) {
				bestScore = score;
				best = candidate;
			}
		}
		enemy = best;
		structureTarget = best == null ? findStructure(match, range) : null;
	}

	private Structure findStructure(Match match, double range) {
		TeamColor enemyTeam = team().opponent();
		if (match.structureAlive(enemyTeam, Structure.TOWER)
				&& ArenaLayout.towerBox(enemyTeam).distance(getX(), getY() + 1, getZ()) <= range + 2) {
			return Structure.TOWER;
		}
		if (match.structureAlive(enemyTeam, Structure.CORE)
				&& ArenaLayout.coreBox(enemyTeam).distance(getX(), getY() + 1, getZ()) <= range + 2) {
			return Structure.CORE;
		}
		return null;
	}

	public boolean isValidEnemy(Entity entity, Match match) {
		if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
			return false;
		}
		return entity instanceof GoblinUnit unit && unit.team() != team() && !unit.isStealthed() && match.owns(unit);
	}

	private void engage(ServerLevel level, Match match, TeamState own, LivingEntity target) {
		getLookControl().setLookAt(target, 30f, 30f);
		setAggressive(true);
		double reach = attackReach(match, own);
		double gap = distanceTo(target) - (getBbWidth() + target.getBbWidth()) / 2.0;
		boolean sees = hasLineOfSight(target);
		if (ranged(match)) {
			if (gap > reach - 1 || !sees) {
				moveTowards(target.position());
			} else {
				getNavigation().stop();
			}
			if (gap <= reach && sees && attackCooldown <= 0) {
				if (unitType() == UnitType.SHAMAN) {
					castBolt(level, match, own, target);
				} else {
					shootAt(level, match, own, target);
				}
			}
		} else {
			if (gap > reach * 0.7) {
				moveTowards(target.position());
			} else {
				getNavigation().stop();
			}
			if (gap <= reach && attackCooldown <= 0) {
				meleeAttack(level, match, own, target);
			}
		}
	}

	private double currentDamage(Match match, TeamState own) {
		if (isChieftain()) {
			return match.chieftainDamage(team());
		}
		return match.combat().damage(unitType(), unitLevel(), own.unitUpgrade(UpgradeType.ATTACK, unitType()));
	}

	private void startAttackCooldown(Match match) {
		attackCooldown = match.combat().attackCooldownTicks(unitType(), activeBloodlustBonus());
		ticksSinceAttack = 0;
	}

	private void meleeAttack(ServerLevel level, Match match, TeamState own, LivingEntity target) {
		swing(InteractionHand.MAIN_HAND);
		double damage = currentDamage(match, own);
		Balance.Traits traits = match.balance().traits();
		if (isStealthed()) {
			damage *= match.combat().assassinOpeningMultiplier();
			level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.2, target.getZ(), 15, 0.3, 0.4, 0.3, 0.3);
			reveal(level);
		}
		boolean charge = unitType() == UnitType.WOLF_RIDER && chargeReady;
		if (charge) {
			damage *= traits.wolfChargeMultiplier();
			chargeReady = false;
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 1.0, target.getZ(), 1, 0, 0, 0, 0);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.HOGLIN_ATTACK, SoundSource.HOSTILE, 0.9f, 1.2f);
		}
		startAttackCooldown(match);
		dealDamage(level, match, target, damage);
		if (charge) {
			Knockback.away(target, getX(), getZ(), traits.wolfChargeKnockback());
		}
		if (unitType() == UnitType.CHIEFTAIN) {
			cleave(level, match, target, damage * traits.chieftainCleaveShare(), traits.chieftainCleaveRadius());
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 0.8f, 0.8f + getRandom().nextFloat() * 0.2f);
			return;
		}
		if (unitType() == UnitType.TROLL) {
			Knockback.away(target, getX(), getZ(), traits.trollKnockback());
			cleave(level, match, target, damage * traits.trollCleaveShare(), traits.trollCleaveRadius());
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 0.8f, 0.9f + getRandom().nextFloat() * 0.2f);
			return;
		}
		level.playSound(null, getX(), getY(), getZ(), unitType() == UnitType.WARRIOR ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_SWEEP,
				SoundSource.HOSTILE, 0.5f, 1.1f + getRandom().nextFloat() * 0.3f);
	}

	/** Troll- und Häuptlingsschlag: trifft auch Gegner direkt neben dem Ziel. */
	private void cleave(ServerLevel level, Match match, LivingEntity primary, double damage, double radius) {
		AABB box = primary.getBoundingBox().inflate(radius, 1, radius);
		for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != primary && e != this && isValidEnemy(e, match))) {
			if (other.distanceTo(primary) <= radius) {
				dealDamage(level, match, other, damage);
				if (unitType() == UnitType.TROLL) {
					Knockback.away(other, getX(), getZ(), match.balance().traits().trollKnockback() * 0.6);
				}
			}
		}
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, primary.getX(), primary.getY() + 0.8, primary.getZ(), 2, 0.6, 0.2, 0.6, 0);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
				primary.getX(), primary.getY() + 0.1, primary.getZ(), 12, 0.6, 0.05, 0.6, 0.1);
	}

	private void shootAt(ServerLevel level, Match match, TeamState own, LivingEntity target) {
		swing(InteractionHand.MAIN_HAND);
		startAttackCooldown(match);
		double damage = currentDamage(match, own);
		fireArrow(level, target, damage);
		if (isChampion()) {
			// Champion-Bogenschützen schießen zusätzlich auf weitere Gegner in Reichweite.
			int extra = match.balance().unitLeveling().champion().archerMultishotTargets() - 1;
			double reach = attackReach(match, own);
			AABB box = getBoundingBox().inflate(reach, 4, reach);
			List<LivingEntity> others = level.getEntitiesOfClass(LivingEntity.class, box,
					e -> e != target && e != this && isValidEnemy(e, match) && distanceTo(e) <= reach && hasLineOfSight(e));
			others.sort(Comparator.comparingDouble(this::distanceTo));
			for (int i = 0; i < Math.min(extra, others.size()); i++) {
				fireArrow(level, others.get(i), damage);
			}
		}
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 0.6f, 1.0f / (getRandom().nextFloat() * 0.4f + 0.8f));
	}

	private void fireArrow(ServerLevel level, LivingEntity target, double damage) {
		GoblinArrow arrow = new GoblinArrow(level, this, team(), damage);
		double dx = target.getX() - getX();
		double dy = target.getY(0.4) - arrow.getY();
		double dz = target.getZ() - getZ();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		arrow.shoot(dx, dy + horizontal * 0.12, dz, 1.9f, 3.0f);
		level.addFreshEntity(arrow);
	}

	/** Geisterblitz des Schamanen: trifft sofort, sichtbar als Funkenspur. */
	private void castBolt(ServerLevel level, Match match, TeamState own, LivingEntity target) {
		swing(InteractionHand.MAIN_HAND);
		startAttackCooldown(match);
		Vec3 from = new Vec3(getX(), getEyeY() - 0.1, getZ());
		Vec3 to = target.getEyePosition().add(0, -0.4, 0);
		beam(level, from, to, ParticleTypes.WITCH);
		dealDamage(level, match, target, currentDamage(match, own));
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 0.35f, 1.6f);
	}

	private static void beam(ServerLevel level, Vec3 from, Vec3 to, net.minecraft.core.particles.SimpleParticleType particle) {
		Vec3 step = to.subtract(from);
		int points = (int) Math.max(2, step.length() / 0.5);
		for (int i = 0; i <= points; i++) {
			Vec3 p = from.add(step.scale(i / (double) points));
			level.sendParticles(particle, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
		}
	}

	/** Heilpuls des Schamanen: heilt die am stärksten verletzten Verbündeten (auch den Häuptling) im Umkreis. */
	private void healAllies(ServerLevel level, Match match, TeamState own) {
		double radius = match.combat().shamanHealRadius(unitLevel());
		AABB box = getBoundingBox().inflate(radius, 3, radius);
		List<LivingEntity> wounded = new ArrayList<>();
		for (LivingEntity ally : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e.getHealth() < e.getMaxHealth())) {
			if (ally.distanceTo(this) > radius) {
				continue;
			}
			if (ally instanceof GoblinUnit unit && match.owns(unit) && unit.team() == team()) {
				wounded.add(ally);
			}
		}
		if (wounded.isEmpty()) {
			return;
		}
		wounded.sort(Comparator.comparingDouble(e -> e.getHealth() / e.getMaxHealth()));
		double amount = match.combat().shamanHeal(unitLevel(), own.unitUpgrade(UpgradeType.ATTACK, unitType()));
		double healed = 0;
		int targets = Math.min(wounded.size(), match.balance().traits().shamanHealTargets());
		for (int i = 0; i < targets; i++) {
			LivingEntity ally = wounded.get(i);
			float before = ally.getHealth();
			ally.heal((float) amount);
			healed += ally.getHealth() - before;
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, ally.getX(), ally.getY() + 1.0, ally.getZ(), 4, 0.3, 0.5, 0.3, 0);
			beam(level, new Vec3(getX(), getEyeY(), getZ()), ally.position().add(0, 1.0, 0), ParticleTypes.HAPPY_VILLAGER);
		}
		swing(InteractionHand.MAIN_HAND);
		level.sendParticles(ParticleTypes.WITCH, getX(), getY() + 2.2, getZ(), 8, 0.3, 0.2, 0.3, 0.02);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 0.5f, 1.4f);
		addXp(match.combat().leveling().xpForDamage(healed));
	}

	/** Fügt einem Ziel Schaden zu (ohne Unverwundbarkeits-Pause); die Erfahrung schreibt {@link #hurtServer} des Ziels gut. */
	public void dealDamage(ServerLevel level, Match match, LivingEntity target, double damage) {
		target.invulnerableTime = 0;
		target.hurtServer(level, damageSources().mobAttack(this), (float) damage);
	}

	private void engageStructure(ServerLevel level, Match match, TeamState own, Structure structure) {
		TeamColor enemyTeam = team().opponent();
		ArenaLayout.Box box = structure == Structure.CORE ? ArenaLayout.coreBox(enemyTeam) : ArenaLayout.towerBox(enemyTeam);
		ArenaLayout.Point center = box.center();
		double distance = box.distance(getX(), getY() + 1, getZ());
		double reach = ranged(match) ? attackReach(match, own) : match.balance().stronghold().structureAttackReach();
		getLookControl().setLookAt(center.x(), getEyeY(), center.z());
		setAggressive(true);
		if (distance > reach - 0.5) {
			// Zum nächsten Punkt vor der Wand laufen, nicht in die Mitte des Gebäudes.
			double px = Math.clamp(getX(), box.minX(), box.maxX() + 1.0);
			double pz = Math.clamp(getZ(), box.minZ(), box.maxZ() + 1.0);
			Vec3 out = new Vec3(getX() - px, 0, getZ() - pz);
			Vec3 dir = out.lengthSqr() < 1.0E-4 ? new Vec3(-ArenaLayout.side(enemyTeam), 0, 0) : out.normalize();
			double stand = ranged(match) ? Math.min(reach - 1.5, out.length()) : 1.6;
			moveTowards(new Vec3(px + dir.x * stand, ArenaLayout.GROUND_Y + 1, pz + dir.z * stand));
			return;
		}
		getNavigation().stop();
		if (attackCooldown > 0) {
			return;
		}
		startAttackCooldown(match);
		swing(InteractionHand.MAIN_HAND);
		double damage = currentDamage(match, own) * match.combat().structureMultiplier(unitType());
		if (isStealthed()) {
			reveal(level);
		}
		switch (unitType()) {
			case CATAPULT -> {
				int shots = isChampion() ? match.balance().unitLeveling().champion().catapultShots() : 1;
				for (int i = 0; i < shots; i++) {
					int delay = i * 8;
					match.schedule(Math.max(1, delay), () -> launchBoulder(level, match, enemyTeam, structure, box, damage));
				}
				return;
			}
			case SHAMAN -> {
				Vec3 hit = new Vec3(Math.clamp(getX(), box.minX(), box.maxX() + 1.0), getEyeY(), Math.clamp(getZ(), box.minZ(), box.maxZ() + 1.0));
				beam(level, new Vec3(getX(), getEyeY() - 0.1, getZ()), hit, ParticleTypes.WITCH);
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 0.35f, 1.6f);
			}
			case ARCHER -> {
				GoblinArrow arrow = new GoblinArrow(level, this, team(), 0);
				arrow.shoot(center.x() - getX(), center.y() - arrow.getY(), center.z() - getZ(), 1.9f, 2.0f);
				level.addFreshEntity(arrow);
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 0.5f, 1.0f);
			}
			case TROLL -> level.playSound(null, getX(), getY(), getZ(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 0.8f, 0.7f);
			default -> level.playSound(null, getX(), getY(), getZ(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 0.35f, 1.3f);
		}
		match.damageStructure(enemyTeam, structure, damage, this, team());
		addXp(match.combat().leveling().xpForBuildingHit());
	}

	/**
	 * Katapultgeschoss: fliegt in einem Bogen zum Gebäude und schlägt dort mit Flächenschaden ein.
	 * Der Flug ist reine Partikeloptik, der Treffer ist sicher (Gebäude weichen nicht aus).
	 */
	private void launchBoulder(ServerLevel level, Match match, TeamColor enemyTeam, Structure structure, ArenaLayout.Box box, double damage) {
		if (!isAlive() || match.finished() || !match.isBattle()) {
			return;
		}
		swing(InteractionHand.MAIN_HAND);
		Vec3 from = new Vec3(getX(), getY() + 2.0, getZ());
		Vec3 target = new Vec3(Math.clamp(getX(), box.minX(), box.maxX() + 1.0) + (getRandom().nextDouble() - 0.5),
				ArenaLayout.GROUND_Y + 2.0 + getRandom().nextDouble() * 3,
				Math.clamp(getZ(), box.minZ(), box.maxZ() + 1.0) + (getRandom().nextDouble() - 0.5));
		int flight = (int) Math.clamp(from.distanceTo(target) / 1.1, 8, 26);
		double arc = 4 + from.distanceTo(target) * 0.25;
		BlockParticleOption rock = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBBLESTONE.defaultBlockState());
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.DISPENSER_LAUNCH, SoundSource.HOSTILE, 1.0f, 0.5f);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 0.8f, 0.6f);
		for (int i = 1; i <= flight; i++) {
			int step = i;
			match.schedule(step, () -> {
				double t = step / (double) flight;
				Vec3 p = from.lerp(target, t).add(0, Math.sin(Math.PI * t) * arc, 0);
				level.sendParticles(rock, p.x, p.y, p.z, 5, 0.15, 0.15, 0.15, 0);
				level.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
				if (step == flight) {
					level.sendParticles(ParticleTypes.EXPLOSION, target.x, target.y, target.z, 2, 0.5, 0.5, 0.5, 0);
					level.sendParticles(rock, target.x, target.y, target.z, 40, 1.0, 0.6, 1.0, 0.2);
					level.playSound(null, target.x, target.y, target.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2f, 1.2f);
					level.playSound(null, target.x, target.y, target.z, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 1.5f, 0.6f);
					match.damageStructure(enemyTeam, structure, damage, this, team());
					match.areaDamage(team(), target, match.balance().traits().catapultSplashRadius(), match.combat().catapultSplashDamage(damage),
							damageSources().mobAttack(this), 0.5);
					addXp(match.combat().leveling().xpForBuildingHit());
				}
			});
		}
	}

	private void moveTowards(Vec3 destination) {
		if (isRooted() || repathTimer > 0 && getNavigation().isInProgress()) {
			return;
		}
		repathTimer = 10;
		getNavigation().moveTo(destination.x, destination.y, destination.z, 1.0);
	}

	private void navigate(double x, double y, double z, double speed) {
		if (isRooted()) {
			return;
		}
		getNavigation().moveTo(x, y, z, speed);
	}

	private void followLane(Match match) {
		int dir = ArenaLayout.attackDirection(team());
		List<ArenaLayout.Point> waypoints = match.waypoints(team());
		ArenaLayout.Point next = waypoints.getLast();
		for (ArenaLayout.Point point : waypoints) {
			if ((point.x() - getX()) * dir > 2.0) {
				next = point;
				break;
			}
		}
		Vec3 destination = new Vec3(next.x(), next.y(), next.z() + laneOffset);
		if (repathTimer <= 0 || getNavigation().isDone()) {
			repathTimer = 20;
			navigate(destination.x, destination.y, destination.z, 1.0);
		}
	}

	private Vec3 currentHoldAnchor(Match match) {
		if (guarding) {
			ArenaLayout.Point post = ArenaLayout.heroSpawn(team());
			return new Vec3(post.x(), post.y(), post.z());
		}
		Vec3 rally = match == null ? null : match.rallyPoint(team());
		if (rally != null) {
			return rally.add(laneOffset * 0.6, 0, laneOffset);
		}
		return holdAnchor;
	}

	private void holdPosition(Match match) {
		Vec3 anchor = currentHoldAnchor(match);
		if (anchor == null) {
			holdAnchor = anchor = position();
		}
		if (position().distanceTo(anchor) > 1.5) {
			if (repathTimer <= 0 || getNavigation().isDone()) {
				repathTimer = 20;
				if (guarding) {
					// Der zurückgerufene Häuptling eilt in die Festung.
					walkHome(match, anchor, 1.15);
				} else {
					navigate(anchor.x, anchor.y, anchor.z, 1.0);
				}
			}
		} else {
			getNavigation().stop();
		}
	}

	/**
	 * Läuft zu einem Ziel in der eigenen Festung. Von weit draußen geht es über die Wegpunkte der Lane, weil die
	 * Wegfindung nur ein Stück weit vorausplant.
	 */
	private void walkHome(Match match, Vec3 destination, double speed) {
		double homeward = ArenaLayout.side(team());
		if (Math.abs(destination.x - getX()) > 28 && match != null) {
			double wantedX = getX() + homeward * 20;
			ArenaLayout.Point best = null;
			for (ArenaLayout.Point point : match.waypoints(team())) {
				if ((point.x() - getX()) * homeward > 4 && (best == null || Math.abs(point.x() - wantedX) < Math.abs(best.x() - wantedX))) {
					best = point;
				}
			}
			if (best != null) {
				navigate(best.x(), best.y(), best.z() + laneOffset * 0.5, speed);
				return;
			}
		}
		navigate(destination.x, destination.y, destination.z, speed);
	}

	private void retreat() {
		ArenaLayout.Point barracks = ArenaLayout.barracks(team());
		double tx = barracks.x() + ArenaLayout.side(team()) * (2 + Math.abs(laneOffset));
		double tz = barracks.z() + laneOffset;
		if (Math.abs(getX() - tx) + Math.abs(getZ() - tz) > 2.0) {
			if (repathTimer <= 0 || getNavigation().isDone()) {
				repathTimer = 20;
				walkHome(MatchManager.match(), new Vec3(tx, barracks.y(), tz), 1.15);
			}
		} else {
			getNavigation().stop();
		}
	}

	private void reveal(ServerLevel level) {
		stealthTicks = 0;
		setInvisible(false);
		level.sendParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 12, 0.3, 0.5, 0.3, 0.02);
		refreshName();
	}

	/** Diese Einheit hat einen Gegner (Einheit oder Häuptling) getötet: Champion-Fähigkeiten auslösen. */
	public void onKilledEnemy(ServerLevel level, Match match) {
		if (!isAlive() || !isChampion()) {
			return;
		}
		if (unitType() == UnitType.ASSASSIN) {
			stealthTicks = Math.max(stealthTicks, match.balance().unitLeveling().champion().assassinRestealthSeconds() * 20);
			setInvisible(true);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 12, 0.3, 0.6, 0.3, 0.02);
			refreshName();
		} else if (unitType() == UnitType.WOLF_RIDER) {
			chargeReady = true;
		}
	}

	// ---------------------------------------------------------------- Schaden und Tod

	/** Schaden durch Turm, Zauber oder Fähigkeiten, die keinem Entity zugeordnet sind, aber einem Clan. */
	public boolean hurtByTeam(ServerLevel level, DamageSource source, float amount, TeamColor attackerTeam) {
		pendingAttackerTeam = attackerTeam;
		try {
			return hurtServer(level, source, amount);
		} finally {
			pendingAttackerTeam = null;
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		Match match = MatchManager.match();
		Entity attacker = source.getEntity();
		TeamColor attackerTeam = pendingAttackerTeam;
		if (attackerTeam == null && match != null) {
			attackerTeam = match.teamOf(attacker);
		}
		if (attackerTeam == team()) {
			return false;
		}
		float damage = amount;
		if (match != null && match.owns(this)) {
			TeamState own = match.team(team());
			damage *= (float) match.combat().armorMultiplier(own.unitUpgrade(UpgradeType.ARMOR, unitType()));
			if (unitType() == UnitType.WARRIOR && attacker != null) {
				damage *= (float) match.combat().warriorBlockMultiplier(angleTo(attacker));
			}
			if (match.championWarriorNear(this)) {
				damage *= (float) (1.0 - match.balance().unitLeveling().champion().warriorAuraArmor());
			}
		}
		if (isStealthed() && attackerTeam != null) {
			reveal(level);
		}
		float before = getHealth();
		invulnerableTime = 0;
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt) {
			if (attackerTeam != null) {
				lastAttackerTeam = attackerTeam;
				lastAttacker = attacker;
			}
			float dealt = Math.max(0, before - Math.max(0, getHealth()));
			if (attacker instanceof GoblinUnit unit && dealt > 0 && match != null) {
				unit.addXp(match.combat().leveling().xpForDamage(dealt));
				if (unit.isChieftain() && match.owns(this)) {
					match.onChieftainDealtDamage(unit, dealt);
				}
			}
			if (isChieftain() && dealt > 0 && match != null && match.owns(this)) {
				match.onChieftainDamaged(this, dealt);
			}
			nameTimer = 0;
		}
		return hurt;
	}

	/** Winkel zwischen Blickrichtung und Angreifer in Grad (0 = genau von vorne). */
	private double angleTo(Entity attacker) {
		double yaw = Math.toRadians(yBodyRot);
		Vec3 facing = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		Vec3 toAttacker = new Vec3(attacker.getX() - getX(), 0, attacker.getZ() - getZ());
		if (toAttacker.lengthSqr() < 1.0E-4) {
			return 0;
		}
		double cos = facing.dot(toAttacker.normalize());
		return Math.toDegrees(Math.acos(Math.clamp(cos, -1.0, 1.0)));
	}

	@Override
	public void die(DamageSource source) {
		boolean first = !deathHandled;
		super.die(source);
		if (first && isDeadOrDying() && level() instanceof ServerLevel) {
			deathHandled = true;
			Match match = MatchManager.match();
			if (match != null && match.owns(this)) {
				match.onUnitKilled(this, lastAttacker, lastAttackerTeam);
				if (unitType() == UnitType.SLAVE && match.combat().isChampion(unitLevel())) {
					explodeOnDeath(match);
				}
			}
		}
	}

	/** Champion-Sklave: reißt beim Tod die Gegner in seiner Nähe mit. */
	private void explodeOnDeath(Match match) {
		Balance.Champion champion = match.balance().unitLeveling().champion();
		Vec3 center = position().add(0, 0.5, 0);
		TeamColor own = team();
		DamageSource source = damageSources().mobAttack(this);
		match.schedule(1, () -> {
			ServerLevel level = match.arena();
			level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 2, 0.4, 0.3, 0.4, 0);
			level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 20, 0.8, 0.4, 0.8, 0.05);
			level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.9f, 1.4f);
			match.areaDamage(own, center, champion.slaveExplosionRadius(), champion.slaveExplosionDamage(), source, 0.6);
		});
	}

	@Override
	protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
		// Einheiten lassen nichts fallen: keine Ausrüstung, keine Erfahrungskugeln.
	}

	@Override
	protected void dropExperience(ServerLevel level, Entity killer) {
	}

	@Override
	public boolean shouldDropExperience() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSquared) {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return switch (unitType()) {
			case TROLL, CHIEFTAIN -> SoundEvents.PIGLIN_BRUTE_AMBIENT;
			case SHAMAN -> SoundEvents.WITCH_AMBIENT;
			case CATAPULT -> null;
			default -> SoundEvents.PIGLIN_AMBIENT;
		};
	}

	@Override
	public int getAmbientSoundInterval() {
		return 600;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return unitType() == UnitType.TROLL || isChieftain() ? SoundEvents.PIGLIN_BRUTE_HURT : SoundEvents.PIGLIN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return unitType() == UnitType.TROLL || isChieftain() ? SoundEvents.PIGLIN_BRUTE_DEATH : SoundEvents.PIGLIN_DEATH;
	}
}
