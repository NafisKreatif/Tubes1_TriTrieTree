package fuschia;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public abstract class Tower extends Robot {

    protected int spawnCounter;
    protected boolean productiveActionTaken;

    protected Tower(RobotController rc) {
        super(rc);
    }

    @Override
    public void run() throws GameActionException {
        productiveActionTaken = false;
        turnStart();

        RobotInfo[] nearbyRobots = rc.senseNearbyRobots();

        RobotInfo lowestHealthEnemyUnit = getLowestHealthEnemyUnitInAttackRange(nearbyRobots);
        if (lowestHealthEnemyUnit != null && rc.canAttack(lowestHealthEnemyUnit.location)) {
            rc.attack(lowestHealthEnemyUnit.location);
            markProductiveAction();
        }

        UnitType spawnType;
        if (hasVisibleEnemySoldier(nearbyRobots)) {
            spawnType = UnitType.MOPPER;
        } else {
            int mod = spawnCounter % 4;
            spawnType = switch (mod) {
                case 0, 1 ->
                    UnitType.SOLDIER;
                case 2 ->
                    UnitType.MOPPER;
                default ->
                    UnitType.SPLASHER;
            };
        }

        if (trySpawn(spawnType)) {
            spawnCounter += 1;
            markProductiveAction();
        }

        if (productiveActionTaken) {
            mapData.resetIdleTimer();
        } else {
            mapData.incrementIdleTimer();
        }
        turnEnd();
    }

    protected void determineState() throws GameActionException {
    }

    protected void executeState() throws GameActionException {
    }

    protected final void markProductiveAction() {
        productiveActionTaken = true;
    }

    private RobotInfo getLowestHealthEnemyUnitInAttackRange(RobotInfo[] nearbyRobots) {
        RobotInfo best = null;
        int bestHealth = Integer.MAX_VALUE;
        for (RobotInfo robot : nearbyRobots) {
            if (robot.team != rc.getTeam().opponent() || isTowerType(robot.type)) {
                continue;
            }
            if (!rc.canAttack(robot.location)) {
                continue;
            }
            if (robot.health < bestHealth) {
                bestHealth = robot.health;
                best = robot;
            }
        }
        return best;
    }

    private boolean hasVisibleEnemySoldier(RobotInfo[] nearbyRobots) {
        for (RobotInfo robot : nearbyRobots) {
            if (robot.team == rc.getTeam().opponent() && robot.type == UnitType.SOLDIER) {
                return true;
            }
        }
        return false;
    }

    private boolean trySpawn(UnitType type) throws GameActionException {
        Direction[] dirs = Direction.allDirections();
        int start = rng.nextInt(dirs.length);
        for (int i = 0; i < dirs.length; i++) {
            Direction dir = dirs[(start + i) % dirs.length];
            if (dir == Direction.CENTER) {
                continue;
            }
            MapLocation loc = rc.getLocation().add(dir);
            if (rc.canBuildRobot(type, loc)) {
                rc.buildRobot(type, loc);
                return true;
            }
        }
        return false;
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
}
