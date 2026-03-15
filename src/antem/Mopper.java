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
    public static boolean knowPaintTower = false;

    public static void run(RobotController rc) throws GameActionException {
        roam(rc);
    }

    public static void roam(RobotController rc) throws GameActionException {
        RobotInfo[] allyInfos = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo allyInfo : allyInfos) {
            if (allyInfo.getType() == UnitType.LEVEL_ONE_PAINT_TOWER
                    || allyInfo.getType() == UnitType.LEVEL_TWO_PAINT_TOWER
                    || allyInfo.getType() == UnitType.LEVEL_THREE_PAINT_TOWER) {
                if (rc.canTransferPaint(allyInfo.getLocation(), -20)) {
                    rc.canTransferPaint(allyInfo.getLocation(), -20);
                    knowPaintTower = true;
                    moveStack.clear();
                }
            } else if (allyInfo.getType() == UnitType.SOLDIER || allyInfo.getType() == UnitType.SPLASHER) {
                if (rc.canTransferPaint(allyInfo.getLocation(), 20) && rc.getPaint() > 40) {
                    rc.transferPaint(allyInfo.getLocation(), 20);
                }
            } else {
                if (rc.canUpgradeTower(allyInfo.getLocation())) {
                    rc.upgradeTower(allyInfo.getLocation());
                }
            }
        }

        ArrayList<MapInfo> targetTiles = new ArrayList<>();
        RobotInfo[] enemyInfos = rc.senseNearbyRobots(1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemyInfos) {
            if (enemy.getType() == UnitType.SOLDIER
                    || enemy.getType() == UnitType.MOPPER
                    || enemy.getType() == UnitType.SPLASHER) {
                targetTiles.add(rc.senseMapInfo(enemy.getLocation()));
            }
        }
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(1);
        for (MapInfo tile : nearbyTiles) {
            if (tile.getPaint().isEnemy()) {
                targetTiles.add(tile);
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
                if (knowPaintTower)
                    moveStack.add(dir);
            }
        }

        // Move straight or do a random direction
        if (rc.canMove(moveDirection) && !rc.senseMapInfo(rc.getLocation().add(moveDirection)).getPaint().isEnemy()) {
            rc.move(moveDirection);
            if (knowPaintTower)
                moveStack.add(moveDirection);
        } else {
            if (rc.isMovementReady() && !isAttacking && rc.isActionReady()) {
                moveDirection = IndexToDirection(RobotPlayer.rng.nextInt(8));
            }
        }

        // Refill time
        if (rc.getPaint() < 20 && knowPaintTower) {
            state = MopperState.Refill;
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

        if (rc.getPaint() > 90) {
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

    public static Direction IndexToDirection(int index) {
        return RobotPlayer.directions[(index % 8 + 8) % 8];
    }
}