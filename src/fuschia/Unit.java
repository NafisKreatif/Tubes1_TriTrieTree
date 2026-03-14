package fuschia;

import battlecode.common.GameActionException;
import battlecode.common.RobotController;

public abstract class Unit extends Robot {
    protected boolean productiveActionTaken;

    protected Unit(RobotController rc) {
        super(rc);
    }

    @Override
    public void run() throws GameActionException {
        productiveActionTaken = false;
        turnStart();
        determineState();
        executeState();
        if (productiveActionTaken) {
            mapData.resetIdleTimer();
        } else {
            mapData.incrementIdleTimer();
        }
        turnEnd();
    }

    protected abstract void determineState() throws GameActionException;

    protected abstract void executeState() throws GameActionException;

    protected final void markProductiveAction() {
        productiveActionTaken = true;
    }
}
