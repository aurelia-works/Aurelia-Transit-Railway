package org.mtr.mod.data;

import org.mtr.aurelia.sound.ProfileSounds;
import org.mtr.aurelia.sound.SoundProfile;
import org.mtr.aurelia.sound.SoundProfileRegistry;
import org.mtr.aurelia.sound.SoundProfileStore;
import org.mtr.core.data.Data;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.data.Vehicle;
import org.mtr.core.data.VehicleCar;
import org.mtr.core.data.VehicleExtraData;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.client.Oscillation;
import org.mtr.mod.client.ScrollingText;
import org.mtr.mod.resource.DoorAnimationType;
import org.mtr.mod.resource.Interpolation;
import org.mtr.mod.resource.VehicleResource;
import org.mtr.mod.sound.VehicleSoundBase;

import javax.annotation.Nullable;
import java.util.function.Supplier;

public final class PersistentVehicleData {

	private double smoothedRailProgress;
	private double railProgressSmoothingAdjustment;
	private double doorValue;
	private double oldDoorValue;
	private double nextAnnouncementRailProgress;
	private int doorCooldown;
	private int overrideDoorMultiplier;

	public final boolean[] rayTracing;
	public final double[] longestDimensions;
	private final TransportMode transportMode;
	private final ObjectArrayList<VehicleSoundBase> vehicleSoundBaseList = new ObjectArrayList<>();
	private SoundProfileStore.Settings appliedSoundSettings = SoundProfileStore.Settings.DEFAULT;
	private int appliedProfileVersion = -1;
	private String appliedDefaultProfileId = "";
	private boolean appliedUseDefaults = true;
	@Nullable
	private ProfileSounds appliedProfileSounds;
	private final ObjectArrayList<ObjectArrayList<ScrollingText>> scrollingTexts = new ObjectArrayList<>();
	private final ObjectArrayList<Oscillation> oscillations = new ObjectArrayList<>();
	private final ObjectArrayList<VehicleSoundBase.RunSoundInfo> runSoundStates = new ObjectArrayList<>();
	private double runSoundRailProgress;
	@Nullable
	private VehicleExtraData runSoundVehicleExtraData;
	private boolean runSoundStatesStale;
	private final Object2ObjectOpenHashMap<String, DoorMovementInterpolation> doorMovementInterpolations = new Object2ObjectOpenHashMap<>();

	public PersistentVehicleData(ObjectImmutableList<VehicleCar> immutableVehicleCars, TransportMode transportMode) {
		rayTracing = new boolean[immutableVehicleCars.size()];
		longestDimensions = new double[immutableVehicleCars.size()];
		for (int i = 0; i < immutableVehicleCars.size(); i++) {
			longestDimensions[i] = Math.max(immutableVehicleCars.get(i).getLength(), immutableVehicleCars.get(i).getWidth());
		}
		this.transportMode = transportMode;
	}

	/**
	 * Captures the rail progress difference of an incoming vehicle update. This will be used for smoothing out animations.
	 *
	 * @param newRailProgress    the rail progress coming from the server
	 * @param totalVehicleLength the total length of this vehicle
	 */
	public void update(double newRailProgress, double totalVehicleLength) {
		railProgressSmoothingAdjustment = newRailProgress - smoothedRailProgress;
		if (Math.abs(railProgressSmoothingAdjustment) > Math.max(totalVehicleLength - 1, 10)) {
			railProgressSmoothingAdjustment = 0;
		}
	}

	/**
	 * MTR 4.0.5 shape, kept for add-ons: smooths without the 4.0.6 clamp at the stopping point.
	 */
	public double getSmoothedRailProgress(double railProgress, double adjustmentAmount) {
		return getSmoothedRailProgress(railProgress, adjustmentAmount, null);
	}

	public double getSmoothedRailProgress(double railProgress, double adjustmentAmount, @Nullable Double maxClamp) {
		if (railProgressSmoothingAdjustment > 0) {
			railProgressSmoothingAdjustment = Math.max(railProgressSmoothingAdjustment - adjustmentAmount, 0);
		} else if (railProgressSmoothingAdjustment < 0) {
			railProgressSmoothingAdjustment = Math.min(railProgressSmoothingAdjustment + adjustmentAmount, 0);
		}

		smoothedRailProgress = railProgress - railProgressSmoothingAdjustment;

		if (maxClamp != null && smoothedRailProgress > maxClamp) {
			smoothedRailProgress = maxClamp;
			railProgressSmoothingAdjustment = 0;
		}

		return smoothedRailProgress;
	}

	public ObjectArrayList<ScrollingText> getScrollingText(int carNumber) {
		return getElement(scrollingTexts, carNumber, ObjectArrayList::new);
	}

	public Oscillation getOscillation(int carNumber) {
		return getElement(oscillations, carNumber, () -> new Oscillation(transportMode));
	}

	public void tick(double railProgress, long millisElapsed, VehicleExtraData vehicleExtraData) {
		oldDoorValue = doorValue;
		doorValue = Utilities.clamp(doorValue + (double) (millisElapsed * getAdjustedDoorMultiplier(vehicleExtraData)) / Vehicle.DOOR_MOVE_TIME, 0, 1);
		if (checkCanOpenDoors()) {
			doorCooldown--;
		} else {
			overrideDoorMultiplier = 0;
		}
		if (doorValue > 0) {
			doorCooldown = 2;
			nextAnnouncementRailProgress = railProgress + vehicleExtraData.getTotalVehicleLength() * 1.5;
		}
		oscillations.forEach(oscillation -> oscillation.tick(millisElapsed));

		// Which rail each car is on is worked out only when a car plays its motor sound (see playMotorSound), so trains
		// that are out of earshot or have no sound cost nothing here
		runSoundRailProgress = railProgress;
		runSoundVehicleExtraData = vehicleExtraData;
		runSoundStatesStale = true;
	}

	/** Which rail each car is on, so its run sound can change with the rail under it. */
	private void updateRunSoundStates() {
		runSoundStatesStale = false;
		runSoundStates.clear();
		final VehicleExtraData vehicleExtraData = runSoundVehicleExtraData;
		if (vehicleExtraData != null && !vehicleExtraData.immutablePath.isEmpty()) {
			double headRailProgress = runSoundRailProgress;
			for (int i = 0; i < vehicleExtraData.immutableVehicleCars.size(); i++) {
				final VehicleCar vehicleCar = vehicleExtraData.immutableVehicleCars.get(i);
				final double carLength = vehicleCar.getLength();
				final double tailRailProgress = headRailProgress - carLength;

				// getElement wraps a negative index to the last rail, so a progress before the path must not stay negative
				final int thisPathIndex = Math.max(0, Utilities.getIndexFromConditionalList(vehicleExtraData.immutablePath, headRailProgress));
				final int lastPathIndex = Math.max(0, Utilities.getIndexFromConditionalList(vehicleExtraData.immutablePath, tailRailProgress));
				final PathData thisPathData = Utilities.getElement(vehicleExtraData.immutablePath, thisPathIndex);
				final PathData lastPathData = thisPathIndex == lastPathIndex ? thisPathData : Utilities.getElement(vehicleExtraData.immutablePath, lastPathIndex);
				final float pathDelta = thisPathIndex == lastPathIndex ? 1 : (float) Math.min(1, (headRailProgress - lastPathData.getEndDistance()) / carLength);
				runSoundStates.add(VehicleSoundBase.RunSoundInfo.create(styledRail(lastPathData), styledRail(thisPathData), pathDelta));
				headRailProgress -= carLength;
			}
		}
	}

	/**
	 * The rail a path step runs on, with its styles. On the client a vehicle's path is cached against empty data (see
	 * {@code PacketUpdateVehiclesLifts}), so the path's own rail is a bare stand-in with no styles; the client's rail
	 * between the same two nodes has them.
	 */
	private static Rail styledRail(PathData pathData) {
		final Rail rail = Data.tryGet(MinecraftClientData.getInstance().positionsToRail, pathData.getOrderedPosition1(), pathData.getOrderedPosition2());
		return rail == null ? pathData.getRail() : rail;
	}

	public double getDoorValue() {
		return doorValue;
	}

	public float getInterpolatedDoorValue(DoorAnimationType doorAnimationType, double doorZMultiplier, boolean flipped, double doorOverrideValue, boolean opening) {
		final String key = doorZMultiplier + "_" + doorAnimationType + "_" + flipped;
		final DoorMovementInterpolation doorMovementInterpolation = doorMovementInterpolations.get(key);
		final double value = doorAnimationType.getDoorAnimationZ(doorZMultiplier, flipped, doorValue, opening);
		final float interpolatedDoorValue;
		if (doorMovementInterpolation == null) {
			final DoorMovementInterpolation newDoorMovementInterpolation = new DoorMovementInterpolation();
			doorMovementInterpolations.put(key, newDoorMovementInterpolation);
			interpolatedDoorValue = newDoorMovementInterpolation.setAndGet(value, opening);
		} else {
			interpolatedDoorValue = doorMovementInterpolation.setAndGet(value, opening);
		}

		final float newDoorOverrideValue = (float) (doorOverrideValue * doorZMultiplier) * (flipped ? -1 : 1);
		return Math.abs(newDoorOverrideValue) > Math.abs(interpolatedDoorValue) ? newDoorOverrideValue : interpolatedDoorValue;
	}

	public boolean checkCanOpenDoors() {
		return doorCooldown > 0;
	}

	/**
	 * Get the actual door value, including any overridden value, for example when debugging a train from the Resource Pack Creator.
	 */
	public int getAdjustedDoorMultiplier(VehicleExtraData vehicleExtraData) {
		return overrideDoorMultiplier != 0 ? overrideDoorMultiplier : vehicleExtraData.getDoorMultiplier();
	}

	/**
	 * Override the door value, for example when debugging a train from the Resource Pack Creator. This must be called every tick.
	 *
	 * @param overrideDoorMultiplier {@code 1} for open and {@code -1} for close
	 */
	public void overrideDoorMultiplier(int overrideDoorMultiplier) {
		this.overrideDoorMultiplier = overrideDoorMultiplier;
	}

	public boolean canAnnounce(double oldRailProgress, double railProgress) {
		return oldRailProgress < nextAnnouncementRailProgress && railProgress >= nextAnnouncementRailProgress;
	}

	public void playMotorSound(VehicleResource vehicleResource, int carNumber, BlockPos bogiePosition, float speed, float speedChange, float acceleration, boolean isOnRoute) {
		if (runSoundStatesStale) {
			updateRunSoundStates();
		}
		final VehicleSoundBase.RunSoundInfo runSound = carNumber >= 0 && carNumber < runSoundStates.size() ? runSoundStates.get(carNumber) : null;
		getVehicleSoundBase(vehicleResource, carNumber).playVehicleSound(new VehicleSoundBase.VehicleSoundParameters(runSound, bogiePosition, speed, speedChange, acceleration, isOnRoute));
	}

	public void playDoorSound(VehicleResource vehicleResource, int carNumber, BlockPos vehiclePosition) {
		getVehicleSoundBase(vehicleResource, carNumber).playDoorSound(vehiclePosition, doorValue, oldDoorValue);
	}

	private VehicleSoundBase getVehicleSoundBase(VehicleResource vehicleResource, int carNumber) {
		final VehicleSoundBase soundBase = getElement(vehicleSoundBaseList, carNumber, vehicleResource.createVehicleSoundBase);
		soundBase.setSoundProfile(appliedProfileSounds);
		return soundBase;
	}

	public void dispose() {
		for (VehicleSoundBase sounds : vehicleSoundBaseList) {
			sounds.dispose();
		}
	}

	private static <T> T getElement(ObjectArrayList<T> list, int index, Supplier<T> supplier) {
		while (list.size() <= index) {
			list.add(supplier.get());
		}
		return list.get(index);
	}

	private static class DoorMovementInterpolation {

		private boolean opening;
		private final Interpolation interpolation;

		private DoorMovementInterpolation() {
			this.interpolation = new Interpolation(500);
		}

		private float setAndGet(double value, boolean opening) {
			interpolation.setValue(value, this.opening != opening);
			this.opening = opening;
			return (float) interpolation.getValue();
		}
	}

	/**
	 * Applies this train's sound profile (chosen on the Aurelia panel, or the pack's default tag) to every car. Only
	 * runs when the choice, the toggle, the tag or a volume slider changed; a train with no profile is never touched.
	 */
	public void updateSoundProfile(long vehicleId) {
		updateSoundProfile(vehicleId, "");
	}

	/**
	 * New overload for the pack default: add-ons calling the old one keep the previous behaviour (no default).
	 * Last in the class, like the rest of ATR's additions, so MTR's methods keep their shape.
	 */
	public void updateSoundProfile(long vehicleId, @Nullable String defaultProfileId) {
		final SoundProfileStore.Settings settings = SoundProfileStore.settings(vehicleId);
		final int version = SoundProfileRegistry.version();
		final boolean useDefaults = SoundProfileStore.useTrainDefaults();
		final String def = defaultProfileId == null ? "" : defaultProfileId;
		if (settings.equals(appliedSoundSettings) && version == appliedProfileVersion && def.equals(appliedDefaultProfileId) && useDefaults == appliedUseDefaults) {
			return;
		}
		appliedSoundSettings = settings;
		appliedProfileVersion = version;
		appliedDefaultProfileId = def;
		appliedUseDefaults = useDefaults;
		final SoundProfile profile = SoundProfileStore.resolveWithDefault(settings, useDefaults ? def : "");
		appliedProfileSounds = profile == null ? null : ProfileSounds.resolve(profile, settings);
		for (final VehicleSoundBase soundBase : vehicleSoundBaseList) {
			soundBase.setSoundProfile(appliedProfileSounds);
		}
	}
}
