package antem;

import java.util.ArrayList;
import java.util.Stack;

import battlecode.common.*;

public class Mopper {

    public static enum MopperState {
        Roaming,
        Refill
    }

    public static Stack<Direction> moveStack = new Stack<>();
    public static Direction moveDirection = IndexToDirection(RobotPlayer.rng.nextInt(8));
    public static MopperState state = MopperState.Roaming;
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
        RobotInfo[] allyInfos = rc.senseNearbyRobots(1, rc.getTeam());
        for (RobotInfo allyInfo : allyInfos) {
            if (allyInfo.getType() == UnitType.LEVEL_ONE_PAINT_TOWER
                    || allyInfo.getType() == UnitType.LEVEL_TWO_PAINT_TOWER
                    || allyInfo.getType() == UnitType.LEVEL_THREE_PAINT_TOWER) {
                if (state != MopperState.Refill) {
                    paintTowerLocation = allyInfo.getLocation();
                    moveStack.clear();
                }
                if (rc.canTransferPaint(allyInfo.getLocation(), -50)) {
                    rc.transferPaint(allyInfo.getLocation(), -50);
                }
            } else if (allyInfo.getType() == UnitType.SOLDIER || allyInfo.getType() == UnitType.SPLASHER) {
                if (rc.canTransferPaint(allyInfo.getLocation(), 20) && rc.getPaint() > 40) {
                    rc.transferPaint(allyInfo.getLocation(), 20);
                }
            } else {
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
        }
        rc.setIndicatorString(indicatorString);
    }

    public static void roam(RobotController rc) throws GameActionException {
        ArrayList<MapInfo> targetTiles = new ArrayList<>();
        ArrayList<MapInfo> potentialTiles = new ArrayList<>();
        RobotInfo[] enemyInfos = rc.senseNearbyRobots(1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemyInfos) {
            if (enemy.getType() == UnitType.SOLDIER
                    || enemy.getType() == UnitType.MOPPER
                    || enemy.getType() == UnitType.SPLASHER) {
                targetTiles.add(rc.senseMapInfo(enemy.getLocation()));
            }
        }

        // Mark resource
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                tryMarkResource(rc, rc.getLocation().translate(i, j));
            }
        }

        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        for (MapInfo tile : nearbyTiles) {
            if (tile.getPaint().isEnemy()) {
                if (rc.canAttack(tile.getMapLocation())) {
                    targetTiles.add(tile);
                } else {
                    potentialTiles.add(tile);
                }
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
        Direction mopDirection = calculateBestMopSweepDirection(rc);
        // Mop swing
        if (mopDirection != null) {
            if (rc.canMopSwing(mopDirection)) {
                rc.mopSwing(mopDirection);
                isAttacking = true;
            }
        }
        // Attack something
        if (!targetTiles.isEmpty()) {
            MapInfo targetTile = targetTiles.get(RobotPlayer.rng.nextInt(targetTiles.size()));
            Direction dir = rc.getLocation().directionTo(targetTile.getMapLocation());
            boolean useSecondaryColor = targetTile.getMark() == PaintType.ALLY_SECONDARY;
            if (rc.canAttack(targetTile.getMapLocation())) {
                isAttacking = true;
                rc.attack(targetTile.getMapLocation(), useSecondaryColor);
            }
            if (rc.canMove(dir) && !rc.senseMapInfo(rc.getLocation().add(dir)).getPaint().isEnemy()) {
                rc.move(dir);
                moveDirection = dir;
                if (paintTowerLocation != null)
                    moveStack.add(dir);
            }
        } else if (!potentialTiles.isEmpty()) {
            MapInfo potentialTile = potentialTiles.get(RobotPlayer.rng.nextInt(potentialTiles.size()));
            Direction dir = rc.getLocation().directionTo(potentialTile.getMapLocation());
            if (rc.canMove(dir) && !rc.senseMapInfo(rc.getLocation().add(dir)).getPaint().isEnemy()) {
                rc.move(dir);
                moveDirection = dir;
                if (paintTowerLocation != null)
                    moveStack.add(dir);
            }
        }

        // Move straight or do a random direction
        if (rc.canMove(moveDirection) && !rc.senseMapInfo(rc.getLocation().add(moveDirection)).getPaint().isEnemy()) {
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
        if (rc.getPaint() < 20 && paintTowerLocation != null) {
            state = MopperState.Refill;
        }
    }

    public static void goBack(RobotController rc) throws GameActionException {
        indicatorString += paintTowerLocation.toString();
        if (rc.canSenseLocation(paintTowerLocation)) {
            indicatorString += ">> I see the tower";
            moveStack.clear();
            Direction dir = rc.getLocation().directionTo(paintTowerLocation);
            if (rc.canMove(dir)) {
                rc.move(dir);
            }
        } else if (!moveStack.isEmpty()) {
            Direction dir = moveStack.peek();
            Direction opposite = rc.getLocation().directionTo(rc.getLocation().subtract(dir));

            if (rc.canMove(opposite)) {
                rc.move(opposite);
                moveStack.pop();
            }
        }

        if (rc.getPaint() > 70) {
            state = MopperState.Roaming;
        }
    }

    public static Direction calculateBestMopSweepDirection(RobotController rc) throws GameActionException {
        int bestTotalEnemy = 0;
        Direction bestDirection = null;
        int currentTotal = 0;
        for (int i = 1; i <= 2; i++) {
            for (int j = -1; j <= 1; j++) {
                MapLocation checkLocation = rc.getLocation().translate(i, j);
                if (rc.canSenseRobotAtLocation(checkLocation)) {
                    if (rc.senseRobotAtLocation(checkLocation).getTeam() == rc.getTeam().opponent()) {
                        currentTotal++;
                    }
                }
            }
        }
        if (currentTotal >= 2 && currentTotal > bestTotalEnemy) {
            bestDirection = Direction.EAST;
            bestTotalEnemy = currentTotal;
        }

        currentTotal = 0;
        for (int i = 1; i <= 2; i++) {
            for (int j = -1; j <= 1; j++) {
                MapLocation checkLocation = rc.getLocation().translate(-i, j);
                if (rc.canSenseRobotAtLocation(checkLocation)) {
                    if (rc.senseRobotAtLocation(checkLocation).getTeam() == rc.getTeam().opponent()) {
                        currentTotal++;
                    }
                }
            }
        }
        if (currentTotal >= 2 && currentTotal > bestTotalEnemy) {
            bestDirection = Direction.WEST;
            bestTotalEnemy = currentTotal;
        }

        currentTotal = 0;
        for (int i = 1; i <= 2; i++) {
            for (int j = -1; j <= 1; j++) {
                MapLocation checkLocation = rc.getLocation().translate(j, i);
                if (rc.canSenseRobotAtLocation(checkLocation)) {
                    if (rc.senseRobotAtLocation(checkLocation).getTeam() == rc.getTeam().opponent()) {
                        currentTotal++;
                    }
                }
            }
        }
        if (currentTotal >= 2 && currentTotal > bestTotalEnemy) {
            bestDirection = Direction.NORTH;
            bestTotalEnemy = currentTotal;
        }

        currentTotal = 0;
        for (int i = 1; i <= 2; i++) {
            for (int j = -1; j <= 1; j++) {
                MapLocation checkLocation = rc.getLocation().translate(j, -i);
                if (rc.canSenseRobotAtLocation(checkLocation)) {
                    if (rc.senseRobotAtLocation(checkLocation).getTeam() == rc.getTeam().opponent()) {
                        currentTotal++;
                    }
                }
            }
        }
        if (currentTotal >= 2 && currentTotal > bestTotalEnemy) {
            bestDirection = Direction.SOUTH;
            bestTotalEnemy = currentTotal;
        }

        return bestDirection;
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

    public static boolean tryMarkResource(RobotController rc, MapLocation tileLocation) throws GameActionException {
        if (rc.canMarkResourcePattern(tileLocation)) {
            boolean shouldMark = true;
            for (int i = -2; i <= 2; i++) {
                for (int j = -2; j <= 2; j++) {
                    if (rc.canSenseLocation(tileLocation.translate(i, j))) {
                        MapInfo tile = rc.senseMapInfo(tileLocation.translate(i, j));
                        if (tile.getMark() != PaintType.EMPTY || !tile.getPaint().isAlly()) {
                            shouldMark = false;
                        }
                    } else {
                        shouldMark = false;
                    }
                }
            }
            if (shouldMark) {
                rc.markResourcePattern(tileLocation);
                return true;
            }
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

    public static Direction IndexToDirection(int index) {
        return RobotPlayer.directions[(index % 8 + 8) % 8];
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
