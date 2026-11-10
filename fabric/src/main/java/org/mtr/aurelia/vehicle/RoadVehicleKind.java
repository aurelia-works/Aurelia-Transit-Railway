package org.mtr.aurelia.vehicle;

/**
 * What a road vehicle is: its size, how it accelerates and steers, where people sit and which Blockbench model draws it.
 * Lengths are in metres (1 block = 1 m) at size 100%. Only the car exists now; a bicycle is meant to become a second kind.
 */
public enum RoadVehicleKind {

	CAR("car", "hatchback", 4.05, 1.8, 1.5, 2.55, 0.3125, 3.0, 8.0, 15, 35, new double[][]{{0.36, -0.22, 0.22}, {-0.36, -0.22, 0.22}});

	public final String id;
	/** {@code assets/mtr/models/road_vehicle/<model>.bbmodel} and {@code textures/entity/road_vehicle/<model>.png}. */
	public final String model;
	public final double length;
	public final double width;
	public final double height;
	public final double wheelbase;
	public final double wheelRadius;
	/** m/s². */
	public final double acceleration;
	/** m/s². */
	public final double braking;
	public final double reverseKmh;
	public final double maxSteerDegrees;
	/** Seat offsets {left, up, forward} from the vehicle's ground centre; the first seat drives. */
	public final double[][] seats;

	RoadVehicleKind(String id, String model, double length, double width, double height, double wheelbase, double wheelRadius, double acceleration, double braking, double reverseKmh, double maxSteerDegrees, double[][] seats) {
		this.id = id;
		this.model = model;
		this.length = length;
		this.width = width;
		this.height = height;
		this.wheelbase = wheelbase;
		this.wheelRadius = wheelRadius;
		this.acceleration = acceleration;
		this.braking = braking;
		this.reverseKmh = reverseKmh;
		this.maxSteerDegrees = maxSteerDegrees;
		this.seats = seats;
	}

	public static RoadVehicleKind byId(String id) {
		for (final RoadVehicleKind kind : values()) {
			if (kind.id.equals(id)) {
				return kind;
			}
		}
		return CAR;
	}
}
