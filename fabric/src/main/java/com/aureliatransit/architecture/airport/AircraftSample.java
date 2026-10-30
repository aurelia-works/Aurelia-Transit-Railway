package com.aureliatransit.architecture.airport;

/**
 * One airplane, as the jet bridge sees it. Positions are world blocks. {@code doorValue} is MTR's platform-door
 * signal: it rises only while the doorway is beside a platform block, which at a stand is the apron platform.
 */
public record AircraftSample(double x, double y, double z, boolean moving, double doorValue, boolean airplane) {
}
