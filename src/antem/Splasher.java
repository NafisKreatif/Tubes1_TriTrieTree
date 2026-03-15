package antem;

import java.lang.reflect.Array;
import java.util.ArrayList;

import battlecode.common.*;

public class Splasher {
    public static enum SplasherState {
        Roaming,
        Refill,
        BackToFront
    }

    public static ArrayList<Direction> moveStack = new ArrayList<>();
    public static int goBackIndex = -1;
    public static Direction moveDirection = IndexToDirection(RobotPlayer.rng.nextInt(4) * 2 + 1);

    public static SplasherState state = SplasherState.Roaming;

    public static MapLocation paintLocation = null;
    public static MapLocation paintTowerLocation = null;

    public static String indicatorString = "";

    public static void run(RobotController rc) throws GameActionException {
        indicatorString = state.name();
        if (paintTowerLocation == null) {
            paintTowerLocation = rc.getLocation();
        }
        switch (state) {
            case Roaming:
                roam(rc);
                break;
            case Refill:
                goBack(rc);
                break;

            default:
                break;
        }
        RobotInfo[] allyInfos = rc.senseNearbyRobots(2, rc.getTeam());
        for (RobotInfo allyInfo : allyInfos) {
            if (allyInfo.getType() == UnitType.LEVEL_ONE_PAINT_TOWER
                    || allyInfo.getType() == UnitType.LEVEL_TWO_PAINT_TOWER
                    || allyInfo.getType() == UnitType.LEVEL_THREE_PAINT_TOWER) {
                if (state != SplasherState.Refill
                        && state != SplasherState.BackToFront) {
                    paintTowerLocation = allyInfo.getLocation();
                    moveStack.clear();
                }
                if (rc.canTransferPaint(allyInfo.getLocation(), -50)) {
                    rc.transferPaint(allyInfo.getLocation(), -50);
                }
            }
            if (rc.canUpgradeTower(allyInfo.getLocation())) {
                if (allyInfo.type == UnitType.LEVEL_ONE_PAINT_TOWER
                        || allyInfo.type == UnitType.LEVEL_ONE_MONEY_TOWER
                        || allyInfo.type == UnitType.LEVEL_ONE_DEFENSE_TOWER) {
                    if (rc.getChips() > 5000) {
                        rc.upgradeTower(allyInfo.getLocation());
                    }
                } else if (allyInfo.type == UnitType.LEVEL_TWO_PAINT_TOWER
                        || allyInfo.type == UnitType.LEVEL_TWO_MONEY_TOWER
                        || allyInfo.type == UnitType.LEVEL_TWO_DEFENSE_TOWER) {
                    if (rc.getChips() > 7500) {
                        rc.upgradeTower(allyInfo.getLocation());
                    }
                }
            }
        }
        rc.setIndicatorString(indicatorString);
    }

    public static void roam(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

        // Attack enemy tower
        RobotInfo[] enemyInfos = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemyInfos) {
            if (rc.canAttack(enemy.getLocation())
                    && enemy.getType() != UnitType.SOLDIER
                    && enemy.getType() != UnitType.MOPPER
                    && enemy.getType() != UnitType.SPLASHER) {
                Direction dir = rc.getLocation().directionTo(enemy.getLocation());
                rc.attack(enemy.getLocation());
                if (rc.canMove(dir)) {
                    rc.move(dir);
                    moveDirection = dir;
                    if (paintTowerLocation != null)
                        moveStack.add(dir);
                }
            }
        }
        
        // Mark resource
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                tryMarkResource(rc, rc.getLocation().translate(i, j));
            }
        }
        
        // Attack tile randomly
        ArrayList<MapInfo> targetTiles = new ArrayList<>();
        for (MapInfo tile : nearbyTiles) {
            if (tile.getPaint() == PaintType.EMPTY
                    || (tile.getMark() != tile.getPaint() && tile.getMark().isAlly() && tile.getPaint().isAlly())) {
                targetTiles.add(tile);
            }
            if (tile.hasRuin()) {
                tryCompleteTower(rc, tile.getMapLocation());
            }
            if (tile.getMark() != PaintType.EMPTY) {
                if (rc.canCompleteResourcePattern(tile.getMapLocation())) {
                    rc.completeResourcePattern(tile.getMapLocation());
                }
            }
        }

        boolean isAttacking = false;
        MapLocation attackLocation = calculateBestAoESpot(rc);
        if (attackLocation != null) {
            Direction dir = rc.getLocation().directionTo(attackLocation);
            if (rc.canAttack(attackLocation)) {
                rc.attack(attackLocation);
                isAttacking = true;
            }
            if (rc.canMove(dir)) {
                rc.move(dir);
                moveDirection = dir;
                if (paintTowerLocation != null)
                    moveStack.add(dir);
            }
        }


        // Move straight or do a random direction
        if (rc.canMove(moveDirection)) {
            rc.move(moveDirection);
            if (paintTowerLocation != null)
                moveStack.add(moveDirection);
        } else {
            if (rc.isMovementReady() && !isAttacking && rc.isActionReady()) {
                indicatorString += "[New Direction]";
                moveDirection = getNewDirection(rc, moveDirection);
            }
        }

        // Refill time
        if (rc.getPaint() < 70 && paintTowerLocation != null) {
            state = SplasherState.Refill;
            goBackIndex = moveStack.size() - 1;
        }
    }

    public static void goBack(RobotController rc) throws GameActionException {
        indicatorString += paintTowerLocation.toString();
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

        // Check for ruin and resource pattern
        for (MapInfo tile : nearbyTiles) {
            if (tile.hasRuin()) {
                tryCompleteTower(rc, tile.getMapLocation());
            }
            if (tile.getMark() != PaintType.EMPTY) {
                if (rc.canCompleteResourcePattern(tile.getMapLocation())) {
                    rc.completeResourcePattern(tile.getMapLocation());
                }
            }
        }

        if (rc.canSenseLocation(paintTowerLocation)) {
            indicatorString += ">> I see the tower";
            moveStack.clear();
            Direction dir = rc.getLocation().directionTo(paintTowerLocation);
            if (rc.canMove(dir)) {
                rc.move(dir);
            }
        } else if (goBackIndex > 0) {
            Direction dir = moveStack.get(goBackIndex);
            Direction opposite = rc.getLocation().directionTo(rc.getLocation().subtract(dir));

            if (rc.canMove(opposite)) {
                rc.move(opposite);
                goBackIndex--;
            }
        }

        if (rc.getPaint() > 200) {
            if (paintLocation == null) {
                state = SplasherState.Roaming;
                moveStack.clear();
            } else {
                state = SplasherState.BackToFront;
            }
        }
    }

    public static UnitType getRandomTowerType() {
        int index = RobotPlayer.rng.nextInt(2);
        if (index == 0) {
            return UnitType.LEVEL_ONE_PAINT_TOWER;
        } else if (index == 1) {
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        } else {
            return UnitType.LEVEL_ONE_DEFENSE_TOWER;
        }
    }

    public static boolean tryMarkTower(RobotController rc, MapLocation ruinLocation) throws GameActionException {
        UnitType towerType = getRandomTowerType();
        Direction dir = RobotPlayer.directions[RobotPlayer.rng.nextInt(8)];
        if (rc.canSenseLocation(ruinLocation.subtract(dir))) {
            if (rc.senseMapInfo(ruinLocation.subtract(dir))
                    .getMark() == PaintType.EMPTY
                    && rc.canMarkTowerPattern(towerType, ruinLocation)
                    && rc.senseRobotAtLocation(ruinLocation) == null) {
                rc.markTowerPattern(towerType, ruinLocation);
                return true;
            }
        }
        return false;
    }

    public static boolean tryMarkResource(RobotController rc, MapLocation tileLocation) throws GameActionException {
        if (rc.canMarkResourcePattern(tileLocation)) {
            boolean hasNoMark = true;
            for (int i = -2; i <= 2; i++) {
                for (int j = -2; j <= 2; j++) {
                    if (rc.canSenseLocation(tileLocation.translate(i, j))) {
                        MapInfo tile = rc.senseMapInfo(tileLocation.translate(i, j));
                        if (tile.getMark() != PaintType.EMPTY) {
                            hasNoMark = false;
                        }
                    } else {
                        hasNoMark = false;
                    }
                }
            }
            if (hasNoMark) {
                rc.markResourcePattern(tileLocation);
                return true;
            }
        }
        return false;
    }

    public static boolean tryCompleteTower(RobotController rc, MapLocation ruinLocation) throws GameActionException {
        if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinLocation)) {
            rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinLocation);
            return true;
        } else if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, ruinLocation)) {
            rc.completeTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, ruinLocation);
            return true;
        } else if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, ruinLocation)) {
            rc.completeTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, ruinLocation);
            return true;
        }
        return false;
    }

    public static boolean tryCompleteResource(RobotController rc, MapLocation tileLocation) throws GameActionException {
        if (rc.canCompleteResourcePattern(tileLocation)) {
            rc.completeResourcePattern(tileLocation);
            return true;
        }
        return false;
    }

    public static int DirectionToIndex(Direction direction) {
        switch (direction) {
            case NORTH:
                return 0;
            case NORTHEAST:
                return 1;
            case EAST:
                return 2;
            case SOUTHEAST:
                return 3;
            case SOUTH:
                return 4;
            case SOUTHWEST:
                return 5;
            case WEST:
                return 6;
            case NORTHWEST:
                return 7;
            default:
                return -1;
        }
    }

    public static Direction IndexToDirection(int index) {
        return RobotPlayer.directions[(index % 8 + 8) % 8];
    }

    public static boolean isInBound(RobotController rc, MapLocation tileLocation) throws GameActionException {
        return tileLocation.x >= 0 && tileLocation.y >= 0
                && tileLocation.x < rc.getMapWidth() && tileLocation.y < rc.getMapHeight();
    }

    public static MapLocation calculateBestAoESpot(RobotController rc) throws GameActionException {
        int bestTotalAttacked = 0;
        MapLocation bestAttackLocation = null;
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                if (i * i + j * j > 4)
                    continue;
                int totalAttacked = 0;
                MapLocation attackLocation = rc.getLocation().translate(i, j);
                MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(attackLocation, 2);
                for (MapInfo tile : nearbyTiles) {
                    if (!tile.getPaint().isAlly() && !tile.isWall() && !tile.hasRuin()) {
                        totalAttacked++;
                    }
                }
                if (totalAttacked >= 2 && totalAttacked > bestTotalAttacked) {
                    bestAttackLocation = attackLocation;
                    bestTotalAttacked = totalAttacked;
                }
            }
        }
        return bestAttackLocation;
    }

    public static Direction getNewDirection(RobotController rc, Direction initialDirection) throws GameActionException {
        // Ganti gerakan dengan mengutamakan gerakan diagonal
        int[] p;
        if (DirectionToIndex(initialDirection) % 2 == 0) {
            p = new int[] { 1, 2, 3, 4 };
        } else {
            p = new int[] { 2, 1, 3, 4 };
        }
        for (int i = 0; i <= 3; i++) {
            for (int j = -1; j <= 1; j += 2) {
                Direction newDirection = IndexToDirection(DirectionToIndex(initialDirection) + p[i] * j);
                if (rc.canMove(newDirection)) {
                    return newDirection;
                }
            }
        }
        return initialDirection;
    }
}