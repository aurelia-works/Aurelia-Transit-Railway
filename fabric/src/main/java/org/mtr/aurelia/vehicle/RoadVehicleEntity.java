package org.mtr.aurelia.vehicle;

import net.minecraft.entity.Dismounting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * A free-driving road vehicle with real steering: the heading turns by {@code speed * tan(steer) / wheelbase}, like a
 * car's front wheels, not on the spot like a boat. The server runs the physics from the driver's keys; clients only
 * follow it. Saved with the world: vanilla entity data plus the {@code AtrRoad*} keys. A hired bike also keeps the rack
 * it came from ({@link BikeShare}).
 */
public class RoadVehicleEntity extends Entity {

	private static final TrackedData<String> KIND = DataTracker.registerData(RoadVehicleEntity.class, TrackedDataHandlerRegistry.STRING);
	/** Metres per tick, negative when reversing. */
	private static final TrackedData<Float> SPEED = DataTracker.registerData(RoadVehicleEntity.class, TrackedDataHandlerRegistry.FLOAT);
	/** Front wheel angle in radians, positive to the right. */
	private static final TrackedData<Float> STEER = DataTracker.registerData(RoadVehicleEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final String KEY_KIND = "AtrRoadKind";
	private static final String KEY_SPEED = "AtrRoadSpeed";
	private static final String KEY_STEER = "AtrRoadSteer";
	private static final String KEY_HOME = "AtrRoadHome";
	/** Keys count for this many ticks after the last input packet, so a lost connection does not leave the throttle on. */
	private static final int INPUT_TICKS = 10;
	private static final double GRAVITY = 0.08;
	private static final double STEER_RATE = Math.toRadians(90) / 20;
	/** Rolling and air drag while coasting, in m/s² and per (m/s)². */
	private static final double COAST = 0.6;
	private static final double AIR = 0.0012;

	/** The rack a hired bike came from; null for a car or a bike that was not hired. */
	@Nullable
	private BlockPos home;
	private int input;
	private int inputTicks;
	private double lastScale;
	private float yawStep;
	private int lerpTicks;
	private double lerpX;
	private double lerpY;
	private double lerpZ;
	private float lerpYaw;
	/** Client only: how far the wheels have rolled, in radians. */
	public float wheelSpin;
	public float prevWheelSpin;

	public RoadVehicleEntity(EntityType<?> type, World world) {
		super(type, world);
		setStepHeight(1);
		intersectionChecked = true;
	}

	@Override
	protected void initDataTracker() {
		dataTracker.startTracking(KIND, RoadVehicleKind.CAR.id);
		dataTracker.startTracking(SPEED, 0F);
		dataTracker.startTracking(STEER, 0F);
	}

	public RoadVehicleKind kind() {
		return RoadVehicleKind.byId(dataTracker.get(KIND));
	}

	public void setKind(RoadVehicleKind kind) {
		dataTracker.set(KIND, kind.id);
	}

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		super.onTrackedDataSet(data);
		// A bike and a car differ in size: refit the hitbox when the kind is set, loaded or synced to the client.
		if (KIND.equals(data)) {
			calculateDimensions();
		}
	}

	public float speed() {
		return dataTracker.get(SPEED);
	}

	public float steer() {
		return dataTracker.get(STEER);
	}

	public double scale() {
		return RoadVehicles.settings(getWorld().isClient()).scale();
	}

	@Nullable
	public PlayerEntity driver() {
		return getFirstPassenger() instanceof PlayerEntity player ? player : null;
	}

	/** Marks a bike as hired from {@code rack}: it goes back to a rack instead of dropping as an item. */
	public void hireFrom(BlockPos rack) {
		home = rack.toImmutable();
	}

	public boolean hired() {
		return home != null;
	}

	public void setInput(int flags) {
		input = flags;
		inputTicks = INPUT_TICKS;
	}

	@Override
	public EntityDimensions getDimensions(EntityPose pose) {
		final RoadVehicleKind kind = kind();
		return EntityDimensions.changing((float) (kind.width * scale()), (float) (kind.height * scale()));
	}

	@Override
	public void tick() {
		super.tick();
		final double scale = scale();
		if (scale != lastScale) {
			lastScale = scale;
			calculateDimensions();
		}
		if (getWorld().isClient()) {
			followServer();
			prevWheelSpin = wheelSpin;
			wheelSpin += (float) (speed() / (kind().wheelRadius * scale));
		} else {
			drive(scale);
		}
	}

	private void drive(double scale) {
		final RoadVehicleKind kind = kind();
		final RoadVehicleSettings settings = RoadVehicles.settings(false);
		final int keys = inputTicks > 0 && settings.enabled() && driver() != null ? input : 0;
		if (inputTicks > 0) {
			inputTicks--;
		}
		final boolean throttle = (keys & RoadVehicles.THROTTLE) != 0;
		final boolean brake = (keys & RoadVehicles.BRAKE) != 0;
		final int steerKeys = ((keys & RoadVehicles.RIGHT) != 0 ? 1 : 0) - ((keys & RoadVehicles.LEFT) != 0 ? 1 : 0);

		// Speed in m/tick; an acceleration in m/s² adds a/400 m/tick every tick.
		double speed = speed();
		final double top = Math.min(settings.maxSpeedKmh(), kind.maxKmh) / 72D;
		final double reverse = kind.reverseKmh / 72D;
		final double accelerate = kind.acceleration / 400;
		final double brakeStep = kind.braking / 400;
		if (throttle && !brake) {
			speed = speed < 0 ? Math.min(0, speed + brakeStep) : Math.min(top, speed + accelerate);
		} else if (brake && !throttle) {
			speed = speed > 0 ? Math.max(0, speed - brakeStep) : Math.max(-reverse, speed - accelerate / 2);
		} else {
			final double metresPerSecond = speed * 20;
			final double drag = (COAST + AIR * metresPerSecond * metresPerSecond) / 400;
			speed = Math.signum(speed) * Math.max(0, Math.abs(speed) - drag);
		}
		if (Math.abs(speed) > top) {
			speed = Math.signum(speed) * Math.max(top, Math.abs(speed) - brakeStep);
		}

		// Less lock at speed, as a driver gives: full lock when parking, a few degrees on a fast road.
		final double metresPerSecond = Math.abs(speed) * 20;
		final double lock = Math.toRadians(kind.maxSteerDegrees) / (1 + metresPerSecond * metresPerSecond / 100);
		final double target = steerKeys * lock;
		double steer = steer();
		steer += MathHelper.clamp(target - steer, -STEER_RATE, STEER_RATE);

		// Bicycle model: yaw rate = v * tan(steer) / wheelbase. Minecraft yaw grows clockwise seen from above, so a right turn adds.
		yawStep = (float) Math.toDegrees(speed * Math.tan(steer) / (kind.wheelbase * scale));
		setYaw(getYaw() + yawStep);
		final double yaw = Math.toRadians(getYaw());
		final double forwardX = -Math.sin(yaw);
		final double forwardZ = Math.cos(yaw);

		final double fall = isOnGround() ? -GRAVITY : (getVelocity().y - GRAVITY) * 0.98;
		Vec3d motion = new Vec3d(forwardX * speed, fall, forwardZ * speed);
		if (speed != 0 && blockedAhead(motion, forwardX * Math.signum(speed), forwardZ * Math.signum(speed), kind, scale)) {
			speed = 0;
			motion = new Vec3d(0, fall, 0);
		}
		move(MovementType.SELF, motion);
		if (horizontalCollision) {
			speed = 0;
		}
		setVelocity(motion.x, isOnGround() ? 0 : fall, motion.z);
		dataTracker.set(SPEED, (float) speed);
		dataTracker.set(STEER, (float) steer);
		pushOthers();
	}

	/**
	 * The hitbox is square, as Minecraft's are, and only as wide as the car. This checks the nose (or the tail when
	 * reversing): a box the same size moved to the end of the car, which may climb one block like the car does.
	 */
	private boolean blockedAhead(Vec3d motion, double directionX, double directionZ, RoadVehicleKind kind, double scale) {
		final double reach = (kind.length - kind.width) / 2 * scale;
		final Box end = getBoundingBox().offset(directionX * reach + motion.x, 0.05, directionZ * reach + motion.z);
		return !getWorld().isSpaceEmpty(this, end) && !getWorld().isSpaceEmpty(this, end.offset(0, getStepHeight(), 0));
	}

	private void pushOthers() {
		for (final Entity other : getWorld().getOtherEntities(this, getBoundingBox().expand(0.2, 0, 0.2), entity -> entity.isPushable() && !entity.hasVehicle())) {
			other.pushAwayFrom(this);
		}
	}

	@Override
	public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int interpolationSteps, boolean interpolate) {
		lerpX = x;
		lerpY = y;
		lerpZ = z;
		lerpYaw = yaw;
		lerpTicks = 3;
	}

	private void followServer() {
		if (lerpTicks > 0) {
			final float oldYaw = getYaw();
			setPosition(getX() + (lerpX - getX()) / lerpTicks, getY() + (lerpY - getY()) / lerpTicks, getZ() + (lerpZ - getZ()) / lerpTicks);
			setYaw(getYaw() + MathHelper.wrapDegrees(lerpYaw - getYaw()) / lerpTicks);
			yawStep = getYaw() - oldYaw;
			lerpTicks--;
		} else {
			yawStep = 0;
		}
	}

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		if (player.shouldCancelInteraction()) {
			return ActionResult.PASS;
		}
		if (!getWorld().isClient()) {
			return player.startRiding(this) ? ActionResult.CONSUME : ActionResult.PASS;
		}
		return ActionResult.SUCCESS;
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		if (!getWorld().isClient() && hired() && !isRemoved() && !hasPassengers()) {
			BikeShare.dock(this, passenger);
		}
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengerList().size() < kind().seats.length;
	}

	@Override
	protected void updatePassengerPosition(Entity passenger, PositionUpdater positionUpdater) {
		if (!hasPassenger(passenger)) {
			return;
		}
		final double[] seat = kind().seats[Math.max(0, getPassengerList().indexOf(passenger))];
		final Vec3d offset = new Vec3d(seat[0], 0, seat[2]).multiply(scale()).rotateY((float) -Math.toRadians(getYaw()));
		positionUpdater.accept(passenger, getX() + offset.x, getY() + seat[1] * scale() + passenger.getHeightOffset(), getZ() + offset.z);
		passenger.setYaw(passenger.getYaw() + yawStep);
		passenger.setHeadYaw(passenger.getHeadYaw() + yawStep);
		clampPassengerYaw(passenger);
	}

	@Override
	public void onPassengerLookAround(Entity passenger) {
		clampPassengerYaw(passenger);
	}

	/** A seated person faces forward and can turn their head, not their body. */
	private void clampPassengerYaw(Entity passenger) {
		passenger.setBodyYaw(getYaw());
		final float relative = MathHelper.wrapDegrees(passenger.getYaw() - getYaw());
		final float clamped = MathHelper.clamp(relative, -105, 105);
		passenger.prevYaw += clamped - relative;
		passenger.setYaw(passenger.getYaw() + clamped - relative);
		passenger.setHeadYaw(passenger.getYaw());
	}

	/** Out of the nearer door: the left side first, then the right, then on the roof. */
	@Override
	public Vec3d updatePassengerForDismount(LivingEntity passenger) {
		final double yaw = Math.toRadians(getYaw());
		final double side = (kind().width * scale() + passenger.getWidth()) / 2 + 0.2;
		for (final int direction : new int[]{1, -1}) {
			final double x = getX() + Math.cos(yaw) * side * direction;
			final double z = getZ() + Math.sin(yaw) * side * direction;
			final BlockPos pos = BlockPos.ofFloored(x, getY(), z);
			for (final BlockPos tryPos : new BlockPos[]{pos, pos.up(), pos.down()}) {
				final double floor = getWorld().getDismountHeight(tryPos);
				if (Dismounting.canDismountInBlock(floor)) {
					final Vec3d at = new Vec3d(x, tryPos.getY() + floor, z);
					if (Dismounting.canPlaceEntityAt(getWorld(), passenger, passenger.getBoundingBox(EntityPose.STANDING).offset(at))) {
						return at;
					}
				}
			}
		}
		return new Vec3d(getX(), getBoundingBox().maxY, getZ());
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (getWorld().isClient() || isRemoved()) {
			return true;
		}
		if (isInvulnerableTo(source) || !(source.getAttacker() instanceof PlayerEntity player) || player.getVehicle() == this) {
			return false;
		}
		if (hired()) {
			BikeShare.recall(this, home);
			return true;
		}
		if (!player.getAbilities().creativeMode && getWorld().getGameRules().getBoolean(GameRules.DO_ENTITY_DROPS)) {
			dropStack(getPickBlockStack());
		}
		discard();
		return true;
	}

	@Override
	public ItemStack getPickBlockStack() {
		return kind() == RoadVehicleKind.CAR ? new ItemStack(RoadVehicles.CAR_ITEM) : ItemStack.EMPTY;
	}

	@Override
	protected Text getDefaultName() {
		return kind() == RoadVehicleKind.CAR ? super.getDefaultName() : Text.translatable("entity.mtr.road_vehicle." + kind().id);
	}

	@Override
	public boolean canHit() {
		return !isRemoved();
	}

	@Override
	public boolean isCollidable() {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		if (nbt.contains(KEY_KIND)) {
			setKind(RoadVehicleKind.byId(nbt.getString(KEY_KIND)));
		}
		dataTracker.set(SPEED, nbt.getFloat(KEY_SPEED));
		dataTracker.set(STEER, nbt.getFloat(KEY_STEER));
		home = nbt.contains(KEY_HOME) ? BlockPos.fromLong(nbt.getLong(KEY_HOME)) : null;
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putString(KEY_KIND, kind().id);
		nbt.putFloat(KEY_SPEED, speed());
		nbt.putFloat(KEY_STEER, steer());
		if (home != null) {
			nbt.putLong(KEY_HOME, home.asLong());
		}
	}
}
