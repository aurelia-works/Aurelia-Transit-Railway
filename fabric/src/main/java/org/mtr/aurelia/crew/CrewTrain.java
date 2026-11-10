package org.mtr.aurelia.crew;

/**
 * What the driver of one train has heard from its crew since the doors last opened. A train no crew member has rung for
 * is driven alone and shows nothing. With a guard on board, the driver waits for the guard's ready to start (the guard
 * waits for the dispatcher's right away where there is one); with only a dispatcher, right away is enough. Pure logic.
 */
public final class CrewTrain {

	public enum Status {
		/** No crew works this train. */
		NONE,
		/** The crew have not given the train its signals yet. */
		WAIT,
		/** The dispatcher gave right away; the guard has not rung yet. */
		RIGHT_AWAY,
		/** The train may go. */
		READY
	}

	private boolean working;
	private boolean guard;
	private boolean ready;
	private boolean rightAway;

	public void bell(CrewBell bell) {
		working = true;
		if (bell == CrewBell.READY_TO_START) {
			guard = true;
			ready = true;
		} else {
			rightAway = true;
		}
	}

	/** The doors opened or the train left: the next departure needs new signals. */
	public void reset() {
		ready = false;
		rightAway = false;
	}

	public boolean working() {
		return working;
	}

	public boolean guard() {
		return guard;
	}

	public Status status() {
		if (!working) {
			return Status.NONE;
		}
		if (ready || rightAway && !guard) {
			return Status.READY;
		}
		return rightAway ? Status.RIGHT_AWAY : Status.WAIT;
	}
}
