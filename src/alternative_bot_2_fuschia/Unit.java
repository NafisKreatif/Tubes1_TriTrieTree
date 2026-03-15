package alternative_bot_2_fuschia;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

public abstract class Unit extends Robot {
    protected boolean productiveActionTaken;

    protected int targetX;
    protected int targetY;
    protected int targetTimer;
    protected int idleTimer;

    protected Unit(RobotController rc) {
        super(rc);
        MapLocation startTarget = getOppositeCorner(rc.getLocation());
        targetX = startTarget.x;
        targetY = startTarget.y;
        targetTimer = 50;
        idleTimer = 0;
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

    protected final void refreshTargetIfNeeded() {
        if (targetTimer > 0) {
            return;
        }
        setTarget(getOppositeCorner(rc.getLocation()));
    }

    protected final void setTarget(MapLocation target) {
        if (target == null) {
            return;
        }
        targetX = target.x;
        targetY = target.y;
        targetTimer = 50;
    }

    protected final MapLocation getTargetLocation() {
        return new MapLocation(targetX, targetY);
    }
}
