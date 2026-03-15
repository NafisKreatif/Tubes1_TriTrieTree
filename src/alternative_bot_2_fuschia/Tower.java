package alternative_bot_2_fuschia;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public abstract class Tower extends Robot {

    protected int spawnCounter;
    protected boolean productiveActionTaken;
    
    private static int paintTowerCount = 0;
    private static int moneyTowerCount = 0;

    protected Tower(RobotController rc) {
        super(rc);
    }

    @Override
    public void run() throws GameActionException {
        productiveActionTaken = false;
        turnStart();

        broadcastTowerType();
        countTowerTypes();
        tellSoldiersWhatToBuild();

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

    private void broadcastTowerType() throws GameActionException {
        if (rc.canBroadcastMessage()) {
            if (isPaintTower(rc.getType())) {
                rc.broadcastMessage(1);
            } else if (isMoneyTower(rc.getType())) {
                rc.broadcastMessage(2);
            }
        }
    }

    private void countTowerTypes() throws GameActionException {
        Message[] messages = rc.readMessages(rc.getRoundNum() - 1);
        paintTowerCount = 0;
        moneyTowerCount = 0;
        
        for (Message message : messages) {
            int data = message.getBytes();
            if ((data & 1) == 1) {
                paintTowerCount++;
            }
            if ((data & 2) == 2) {
                moneyTowerCount++;
            }
        }
        
        if (isPaintTower(rc.getType())) {
            paintTowerCount++;
        } else if (isMoneyTower(rc.getType())) {
            moneyTowerCount++;
        }
    }

    private void tellSoldiersWhatToBuild() throws GameActionException {
        int data = 0;
        if (paintTowerCount >= moneyTowerCount) {
            data |= 1;
        }
        if (moneyTowerCount >= paintTowerCount) {
            data |= 2;
        }

        RobotInfo[] nearbyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo robot : nearbyRobots) {
            if (robot.type == UnitType.SOLDIER) {
                if (rc.canSendMessage(robot.location)) {
                    rc.sendMessage(robot.location, data);
                }
            }
        }
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
