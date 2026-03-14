package fuschia;

import battlecode.common.GameActionException;
import battlecode.common.RobotController;

public abstract class Tower extends Robot {

    enum TowerState {
        SPAWN_UNITS,
        DEFEND,
        UPGRADE,
        FLICKER
    }

    protected boolean productiveActionTaken;

    protected Tower(RobotController rc) {
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
