package fuschia;

import java.util.Random;

import battlecode.common.Direction;
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

    protected final boolean moveToward(MapLocation target) throws GameActionException {
        if (target == null || !rc.isMovementReady()) {
            return false;
        }

        Direction direct = rc.getLocation().directionTo(target);
        Direction[] prefs = {
            direct,
            direct.rotateLeft(),
            direct.rotateRight(),
            direct.rotateLeft().rotateLeft(),
            direct.rotateRight().rotateRight(),
            direct.opposite()
        };

        for (Direction dir : prefs) {
            if (dir != Direction.CENTER && rc.canMove(dir)) {
                rc.move(dir);
                return true;
            }
        }
        return false;
    }

    protected final MapLocation getOppositeCorner(MapLocation from) {
        int maxX = rc.getMapWidth() - 1;
        int maxY = rc.getMapHeight() - 1;
        MapLocation[] corners = new MapLocation[] {
            new MapLocation(0, 0),
            new MapLocation(maxX, 0),
            new MapLocation(0, maxY),
            new MapLocation(maxX, maxY)
        };

        MapLocation best = corners[0];
        int bestDist = from.distanceSquaredTo(best);
        for (int i = 1; i < corners.length; i++) {
            int dist = from.distanceSquaredTo(corners[i]);
            if (dist > bestDist) {
                bestDist = dist;
                best = corners[i];
            }
        }
        return best;
    }
}
