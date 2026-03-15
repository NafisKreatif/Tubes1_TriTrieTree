package main_bots_antem;

import battlecode.common.*;

public class Tower {
    static int robotTypeToBuild = 0;
    static int broadcastType = 0;
    static int indexTower = 0;
    static final int TARGET_EXPIRED_TIME = 50;

    static int paintTowerCount = 0;
    static int moneyTowerCount = 0;

    public static void run(RobotController rc) throws GameActionException {
        buildRobot(rc);
        attackNearbyEnemy(rc);
        broadcastTowerType(rc);
        countTowerType(rc);
        tellToBuild(rc);
        if (rc.getRoundNum() % 2 == 0) {
            broadcastType++;
            broadcastType %= 2;
        }
        rc.setIndicatorString(paintTowerCount + ":" + moneyTowerCount);
    }

    public static void buildRobot(RobotController rc) throws GameActionException {
        if (rc.getChips() < 1100)
            return;
        Direction dir = RobotPlayer.directions[RobotPlayer.rng.nextInt(RobotPlayer.directions.length)];
        MapLocation nextLoc = rc.getLocation().add(dir);
        if (robotTypeToBuild <= 1) {
            if (rc.canBuildRobot(UnitType.SOLDIER, nextLoc)) {
                robotTypeToBuild = (robotTypeToBuild + 1) % 4;
                rc.buildRobot(UnitType.SOLDIER, nextLoc);
            }
        } else if (robotTypeToBuild <= 2) {
            if (rc.canBuildRobot(UnitType.MOPPER, nextLoc)) {
                robotTypeToBuild = (robotTypeToBuild + 1) % 4;
                rc.buildRobot(UnitType.MOPPER, nextLoc);
            }
        } else {
            if (rc.canBuildRobot(UnitType.SPLASHER, nextLoc)) {
                robotTypeToBuild = (robotTypeToBuild + 1) % 4;
                rc.buildRobot(UnitType.SPLASHER, nextLoc);
            }
        }
    }

    public static void attackNearbyEnemy(RobotController rc) throws GameActionException {
        RobotInfo[] enemyInfos = rc.senseNearbyRobots(3);
        if (enemyInfos.length > 0) {
            RobotInfo randomEnemy = enemyInfos[RobotPlayer.rng.nextInt(enemyInfos.length)];
            if (rc.canAttack(randomEnemy.getLocation())) {
                rc.attack(randomEnemy.getLocation());
            }
        }
        rc.attack(null); // AoE
    }

    public static void broadcastTowerType(RobotController rc) throws GameActionException {
        if (rc.canBroadcastMessage()) {
            if (rc.getType() == UnitType.LEVEL_ONE_PAINT_TOWER
                    || rc.getType() == UnitType.LEVEL_TWO_PAINT_TOWER
                    || rc.getType() == UnitType.LEVEL_THREE_PAINT_TOWER) {
                rc.broadcastMessage(1);
            } else if (rc.getType() == UnitType.LEVEL_ONE_MONEY_TOWER
                    || rc.getType() == UnitType.LEVEL_TWO_MONEY_TOWER
                    || rc.getType() == UnitType.LEVEL_THREE_MONEY_TOWER) {
                rc.broadcastMessage(2);
            }
        }
    }

    public static void countTowerType(RobotController rc) throws GameActionException {
        Message[] messages = rc.readMessages(rc.getRoundNum() - 1);
        paintTowerCount = 0;
        moneyTowerCount = 0;
        for (Message message : messages) {
            System.out.println(message.getBytes());
            if ((message.getBytes() & 1) == 1) {
                paintTowerCount++;
            }
            if ((message.getBytes() & 2) == 2) {
                moneyTowerCount++;
            }
        }
        if (rc.getType() == UnitType.LEVEL_ONE_PAINT_TOWER
                || rc.getType() == UnitType.LEVEL_TWO_PAINT_TOWER
                || rc.getType() == UnitType.LEVEL_THREE_PAINT_TOWER) {
            paintTowerCount++;
        } else if (rc.getType() == UnitType.LEVEL_ONE_MONEY_TOWER
                || rc.getType() == UnitType.LEVEL_TWO_MONEY_TOWER
                || rc.getType() == UnitType.LEVEL_THREE_MONEY_TOWER) {
            moneyTowerCount++;
        }
    }

    public static void tellToBuild(RobotController rc) throws GameActionException {
        int data = 0;
        if (paintTowerCount >= moneyTowerCount)
            data |= 1;
        if (moneyTowerCount >= paintTowerCount)
            data |= 2;
        RobotInfo[] allyInfos = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo allyInfo : allyInfos) {
            if (allyInfo.getType().isRobotType()) {
                MapLocation allyLocation = allyInfo.getLocation();
                if (rc.canSendMessage(allyLocation)) {
                    rc.sendMessage(allyLocation, data);
                }
            }
        }
    }
}
