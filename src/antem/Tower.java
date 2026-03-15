package antem;

import battlecode.common.*;

public class Tower {
    static int robotTypeToBuild = 0;
    static int broadcastType = 0;
    static int indexTower = 0;
    static final int TARGET_EXPIRED_TIME = 50;

    public static void run(RobotController rc) throws GameActionException {
        buildRobot(rc);
        transferPaint(rc);
        if (broadcastType == 0) {
            broadcastTowerList(rc);
        } else {
            broadcastTargetList(rc);
        }
        if (rc.getRoundNum() % 2 == 0) {
            broadcastType++;
            broadcastType %= 2;
        }
    }

    public static void buildRobot(RobotController rc) throws GameActionException {
        if (rc.getChips() < 1500)
            return;
        Direction dir = RobotPlayer.directions[RobotPlayer.rng.nextInt(RobotPlayer.directions.length)];
        MapLocation nextLoc = rc.getLocation().add(dir);
        if (robotTypeToBuild == 0) {
            if (rc.canBuildRobot(UnitType.SOLDIER, nextLoc)) {
                robotTypeToBuild = (robotTypeToBuild + 1) % 3;
                rc.buildRobot(UnitType.SOLDIER, nextLoc);
            }
        } else if (robotTypeToBuild == 1) {
            if (rc.canBuildRobot(UnitType.MOPPER, nextLoc)) {
                robotTypeToBuild = (robotTypeToBuild + 1) % 3;
                rc.buildRobot(UnitType.MOPPER, nextLoc);
            }
        } else {
            if (rc.canBuildRobot(UnitType.SPLASHER, nextLoc)) {
                robotTypeToBuild = (robotTypeToBuild + 1) % 3;
                rc.buildRobot(UnitType.SPLASHER, nextLoc);
            }
        }
    }

    public static void transferPaint(RobotController rc) throws GameActionException {
        // RobotInfo[] allyInfos = rc.senseNearbyRobots();
        // for (RobotInfo robotInfo : allyInfos) {
        // if (rc.canTransferPaint(robotInfo.getLocation(), 20)) {
        // rc.transferPaint(robotInfo.getLocation(), 20);
        // }
        // }
    }

    public static void broadcastTowerList(RobotController rc) throws GameActionException {

    }

    public static void broadcastTargetList(RobotController rc) throws GameActionException {

    }
}