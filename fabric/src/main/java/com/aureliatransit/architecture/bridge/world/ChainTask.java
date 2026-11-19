package com.aureliatransit.architecture.bridge.world;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Runs tasks one after another as one task: a restyle takes the old bridge down, then builds the new one. */
final class ChainTask implements BridgeTask {

	private final Deque<BridgeTask> tasks;

	ChainTask(List<BridgeTask> tasks) {
		this.tasks = new ArrayDeque<>(tasks);
	}

	@Override
	public boolean step(long deadline) {
		while (!tasks.isEmpty()) {
			final BridgeTask task = tasks.peekFirst();
			if (!task.step(deadline)) {
				return false;
			}
			task.finish();
			tasks.removeFirst();
			if (System.nanoTime() >= deadline) {
				return tasks.isEmpty();
			}
		}
		return true;
	}

	@Override
	public void finish() {
		while (!tasks.isEmpty()) {
			tasks.removeFirst().finish();
		}
	}
}
