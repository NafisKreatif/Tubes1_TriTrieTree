package fuschia;

import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public abstract class Robot {
    protected final RobotController rc;
    protected final MapData mapData;

    protected Robot(RobotController rc) {
        this.rc = rc;
        this.mapData = new MapData(rc);
    }

    public abstract void run() throws GameActionException;

    protected void turnStart() throws GameActionException {
        RobotInfo[] sensedRobots = rc.senseNearbyRobots();
        MapInfo[] sensedMapInfos = rc.senseNearbyMapInfos();
        if (sensedRobots.length >= 0 && sensedMapInfos.length >= 0) {
            mapData.update(rc);
        }
    }

    protected void turnEnd() throws GameActionException {
        
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
}