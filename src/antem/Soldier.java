package antem;

import java.util.ArrayList;
import java.util.Stack;

import battlecode.common.*;

public class Soldier {
    public static enum SoldierState {
        Roaming,
        Painting,
        Refill
    }

    public static Stack<Direction> moveStack = new Stack<>();
    public static Direction moveDirection = IndexToDirection(RobotPlayer.rng.nextInt(8));
    public static SoldierState state = SoldierState.Roaming;
    public static MapLocation paintLocation;
    public static boolean knowPaintTower = false;

    public static void run(RobotController rc) throws GameActionException {
        rc.setIndicatorString(state.name());
        RobotInfo[] allyInfos = rc.senseNearbyRobots();
        for (RobotInfo allyInfo : allyInfos) {
            if (allyInfo.getType() == UnitType.LEVEL_ONE_PAINT_TOWER
                    || allyInfo.getType() == UnitType.LEVEL_TWO_PAINT_TOWER
                    || allyInfo.getType() == UnitType.LEVEL_THREE_PAINT_TOWER) {
                if (rc.canTransferPaint(allyInfo.getLocation(), -10)) {
                    knowPaintTower = true;
                    moveStack.clear();
                }
            }
            if (rc.canUpgradeTower(allyInfo.getLocation())) {
                rc.upgradeTower(allyInfo.getLocation());
            }
        }
        switch (state) {
            case Roaming:
                roam(rc);
                break;
            case Painting:
                paint(rc);
                break;
            case Refill:
                goBack(rc);
                break;

            default:
                break;
        }
    }

    public static void roam(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

        // Check for ruin and resource pattern
        MapInfo ruinTile = null;
        for (MapInfo tile : nearbyTiles) {
            if (tile.hasRuin()) {
                ruinTile = tile;
            }
            if (tile.getMark() != PaintType.EMPTY) {
                if (rc.canCompleteResourcePattern(tile.getMapLocation())) {
                    rc.completeResourcePattern(tile.getMapLocation());
                }
            }
        }
        if (ruinTile != null) {
            MapLocation ruinLocation = ruinTile.getMapLocation();
            tryCompleteTower(rc, ruinLocation);
            if (tryMarkTower(rc, ruinLocation)) {
                paintLocation = ruinLocation;
                state = SoldierState.Painting;
            }
        }

        // Attack enemy tower
        RobotInfo[] enemyInfos = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemyInfos) {
            if (rc.canAttack(enemy.getLocation())) {
                Direction dir = rc.getLocation().directionTo(enemy.getLocation());
                rc.attack(enemy.getLocation());
                if (rc.canMove(dir)) {
                    rc.move(dir);
                    moveDirection = dir;
                    if (knowPaintTower)
                        moveStack.add(dir);
                }
            }
        }

        // Attack tile randomly
        ArrayList<MapInfo> targetTiles = new ArrayList<>();
        for (MapInfo tile : nearbyTiles) {
            if (tile.getPaint() == PaintType.EMPTY || tile.getMark() != tile.getPaint() && tile.getPaint().isAlly()) {
                targetTiles.add(tile);
            }
        }
        nearbyTiles = rc.senseNearbyMapInfos(1);
        for (MapInfo tile : nearbyTiles) {
            if (tryMarkResource(rc, tile.getMapLocation())) {
                paintLocation = tile.getMapLocation();
                state = SoldierState.Painting;
            }
        }
        if (!targetTiles.isEmpty()) {
            MapInfo targetTile = targetTiles.get(RobotPlayer.rng.nextInt(targetTiles.size()));
            Direction dir = rc.getLocation().directionTo(targetTile.getMapLocation());
            boolean useSecondaryColor = targetTile.getMark() == PaintType.ALLY_SECONDARY;
            if (rc.canAttack(targetTile.getMapLocation())) {
                rc.attack(targetTile.getMapLocation(), useSecondaryColor);
                if (rc.canMove(dir)) {
                    rc.move(dir);
                    moveDirection = dir;
                    if (knowPaintTower)
                        moveStack.add(dir);
                }
            }
        }

        // Move straight or do a random direction
        if (rc.canMove(moveDirection)) {
            rc.move(moveDirection);
            if (knowPaintTower)
                moveStack.add(moveDirection);
        } else {
            if (rc.isMovementReady()) {
                moveDirection = IndexToDirection(RobotPlayer.rng.nextInt(8));
            }
        }

        // Refill time
        if (rc.getPaint() < 40) {
            state = SoldierState.Refill;
        }
    }

    public static void paint(RobotController rc) throws GameActionException {
        boolean patternCompleted = true;
        for (MapInfo patternTile : rc.senseNearbyMapInfos(paintLocation, 8)) {
            if (patternTile.getMark() != patternTile.getPaint() && patternTile.getMark() != PaintType.EMPTY) {
                patternCompleted = false;
                boolean useSecondaryColor = patternTile.getMark() == PaintType.ALLY_SECONDARY;
                if (rc.canAttack(patternTile.getMapLocation())) {
                    rc.attack(patternTile.getMapLocation(), useSecondaryColor);
                    Direction dir = rc.getLocation().directionTo(patternTile.getMapLocation());
                    if (rc.canMove(dir)) {
                        rc.move(dir);
                        moveDirection = dir;
                        if (knowPaintTower)
                            moveStack.add(dir);
                    }
                }
            }
        }
        // Go to paintLocation, takutnya malah kabur
        Direction dir = rc.getLocation().directionTo(paintLocation);
        if (rc.canMove(dir)) {
            rc.move(dir);
            moveDirection = dir;
            if (knowPaintTower)
                moveStack.add(dir);
        }
        if (tryCompleteTower(rc, paintLocation) || tryCompleteResource(rc, paintLocation)) {
            state = SoldierState.Roaming;
        } else if (patternCompleted || rc.getPaint() < 40) {
            state = SoldierState.Refill;
        }
    }

    public static void goBack(RobotController rc) throws GameActionException {
        if (!moveStack.isEmpty()) {
            Direction dir = moveStack.peek();
            Direction opposite = rc.getLocation().directionTo(rc.getLocation().subtract(dir));

            if (rc.canMove(opposite)) {
                rc.move(opposite);
                moveStack.pop();
            }
        }

        if (rc.getPaint() > 180) {
            state = SoldierState.Roaming;
        }
    }

    public static UnitType getRandomTowerType() {
        int index = RobotPlayer.rng.nextInt(4) - 2;
        if (index <= 0) {
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
            for (int i = -3; i <= 3; i++) {
                for (int j = -3; j <= 3; j++) {
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
}
