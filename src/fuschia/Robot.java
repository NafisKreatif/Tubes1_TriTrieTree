package fuschia;

import java.util.Random;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public abstract class Robot {

    protected final RobotController rc;
    protected final MapData mapData;

    protected static final Random rng = new Random(13524095);

    protected Robot(RobotController rc) {
        this.rc = rc;
        this.mapData = new MapData(rc);
    }

    public abstract void run() throws GameActionException;

    protected void turnStart() throws GameActionException {
        RobotInfo[] sensedRobots = rc.senseNearbyRobots();
        mapData.update(rc, sensedRobots);
        Comms.readAndApply(rc, mapData);
    }

    protected void turnEnd() throws GameActionException {
        Comms.broadcastTowerIntel(rc, mapData);
    }

    protected MapLocation getCurrentLocation() {
        return rc.getLocation();
    }

    protected int getCurrentPaint() {
        return rc.getPaint();
    }

    protected int getCurrentRound() {
        return rc.getRoundNum();
    }

    protected static boolean isTowerType(UnitType type) {
        return type == UnitType.LEVEL_ONE_PAINT_TOWER
                || type == UnitType.LEVEL_TWO_PAINT_TOWER
                || type == UnitType.LEVEL_THREE_PAINT_TOWER
                || type == UnitType.LEVEL_ONE_MONEY_TOWER
                || type == UnitType.LEVEL_TWO_MONEY_TOWER
                || type == UnitType.LEVEL_THREE_MONEY_TOWER
                || type == UnitType.LEVEL_ONE_DEFENSE_TOWER
                || type == UnitType.LEVEL_TWO_DEFENSE_TOWER
                || type == UnitType.LEVEL_THREE_DEFENSE_TOWER;
    }

    protected static boolean isPaintTower(UnitType type) {
        return type == UnitType.LEVEL_ONE_PAINT_TOWER
                || type == UnitType.LEVEL_TWO_PAINT_TOWER
                || type == UnitType.LEVEL_THREE_PAINT_TOWER;
    }
}
