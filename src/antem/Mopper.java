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
        RobotInfo[] allyInfos = rc.senseNearbyRobots();
        for (RobotInfo allyInfo : allyInfos) {
            if (allyInfo.getType() == UnitType.LEVEL_ONE_PAINT_TOWER
                    || allyInfo.getType() == UnitType.LEVEL_TWO_PAINT_TOWER
                    || allyInfo.getType() == UnitType.LEVEL_THREE_PAINT_TOWER) {
                if (rc.canTransferPaint(allyInfo.getLocation(), -10)) {
                    knowPaintTower = true;
                    moveStack.clear();
                }
            } else if (allyInfo.getType() == UnitType.SOLDIER || allyInfo.getType() == UnitType.SPLASHER) {
                // if (rc.canTransferPaint(allyInfo.getLocation(), 20) && rc.getPaint() > 30) {
                //     rc.transferPaint(allyInfo.getLocation(), 20);
                // }
            } else {
                if (rc.canUpgradeTower(allyInfo.getLocation())) {
                    rc.upgradeTower(allyInfo.getLocation());
                }
            }
        }

        // Paint
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        ArrayList<MapInfo> targetTiles = new ArrayList<>();
        for (MapInfo tile : nearbyTiles) {
            if (tile.getPaint().isEnemy() || tile.getPaint().isAlly() && tile.getPaint() != tile.getMark()) {
                targetTiles.add(tile);
            }
        }
        if (!targetTiles.isEmpty()) {
            MapInfo targetTile = targetTiles.get(RobotPlayer.rng.nextInt(targetTiles.size()));
            Direction dir = rc.getLocation().directionTo(targetTile.getMapLocation());
            boolean useSecondaryColor = targetTile.getMark() == PaintType.ALLY_SECONDARY;
            if (rc.canMopSwing(dir) && targetTile.getMapLocation().isAdjacentTo(rc.getLocation())) {
                rc.mopSwing(dir);
            }
            if (rc.canAttack(targetTile.getMapLocation())) {
                rc.attack(targetTile.getMapLocation(), useSecondaryColor);
                if (rc.canMove(dir) && rc.senseMapInfo(rc.getLocation().add(dir)).getPaint().isAlly()) {
                    rc.move(dir);
                    moveDirection = dir;
                    if (knowPaintTower)
                        moveStack.add(dir);
                }
            }
        }

        // Move straight or do a random direction
        if (rc.canMove(moveDirection) && rc.senseMapInfo(rc.getLocation().add(moveDirection)).getPaint().isAlly()) {
            rc.move(moveDirection);
            if (knowPaintTower)
                moveStack.add(moveDirection);
        } else {
            if (rc.isMovementReady()) {
                moveDirection = IndexToDirection(RobotPlayer.rng.nextInt(8));
            }
        }

        // Refill time
        if (rc.getPaint() < 20) {
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

        if (rc.getPaint() == 100) {
            state = MopperState.Roaming;
        }
    }

    public static Direction calculateBestMopSweepDirection() {
        int best = 0;
        for (int i = 1; i <= 2; i++) {
            for (int j = -1; j <= 1; j++) {

            }
        }
        return Direction.NORTH;
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
