package main_bots_antem;

import java.util.ArrayList;

import battlecode.common.*;

public class Soldier {
    public static enum SoldierState {
        Roaming,
        Painting,
        Refill,
        BackToFront
    }

    public static ArrayList<Direction> moveStack = new ArrayList<>();
    public static int goBackIndex = -1;
    public static Direction moveDirection = IndexToDirection(RobotPlayer.rng.nextInt(8));

    public static SoldierState state = SoldierState.Roaming;

    public static MapLocation paintLocation = null;
    public static MapLocation paintTowerLocation = null;
    public static UnitType towerToBuild = UnitType.LEVEL_ONE_PAINT_TOWER;

    public static String indicatorString = "";

    public static void run(RobotController rc) throws GameActionException {
        indicatorString = state.name() + "\n" + towerToBuild.name();
        readMessagesFromTower(rc);
        switch (state) {
            case Roaming:
                roam(rc);
                break;
            case Painting:
                paint(rc);
                break;
            case Refill:
                refill(rc);
                break;
            case BackToFront:
                goBackToFront(rc);
                break;

            default:
                break;
        }
        RobotInfo[] allyInfos = rc.senseNearbyRobots(3, rc.getTeam());
        for (RobotInfo allyInfo : allyInfos) {
            if (allyInfo.getType() == UnitType.LEVEL_ONE_PAINT_TOWER
                    || allyInfo.getType() == UnitType.LEVEL_TWO_PAINT_TOWER
                    || allyInfo.getType() == UnitType.LEVEL_THREE_PAINT_TOWER) {
                if (state != SoldierState.BackToFront
                        && state != SoldierState.Refill
                        && !rc.senseMapInfo(
                                rc.getLocation().add(rc.getLocation().directionTo(allyInfo.getLocation())))
                                .isWall()) {
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
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        for (MapInfo tile : nearbyTiles) {
            if (tile.hasRuin()) {
                tryCompleteTower(rc, tile.getMapLocation());
            }
            if (tile.getMark() != PaintType.EMPTY) {
                tryCompleteResource(rc, tile.getMapLocation());
            }
        }
        rc.setIndicatorString(indicatorString);
    }

    public static void roam(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

        // Check for ruin
        MapInfo ruinTile = null;
        for (MapInfo tile : nearbyTiles) {
            if (tile.hasRuin()) {
                ruinTile = tile;
            }
        }
        if (ruinTile != null && rc.senseRobotAtLocation(ruinTile.getMapLocation()) == null) {
            MapLocation ruinLocation = ruinTile.getMapLocation();
            tryMarkTower(rc, ruinLocation);
            boolean hasEnemyPaint = false;
            for (int i = -2; i <= 2; i++) {
                for (int j = -2; j <= 2; j++) {
                    if (rc.canSenseLocation(ruinLocation.translate(i, j))) {
                        MapInfo tile = rc.senseMapInfo(ruinLocation.translate(i, j));
                        if (tile.getPaint().isEnemy()) {
                            hasEnemyPaint = true;
                        }
                    }
                }
            }
            Direction dir = rc.getLocation().directionTo(ruinLocation);
            if (!hasEnemyPaint
                    && !rc.senseMapInfo(rc.getLocation().add(dir)).isWall()
                    && rc.getNumberTowers() < GameConstants.MAX_NUMBER_OF_TOWERS) {
                paintLocation = ruinLocation;
                state = SoldierState.Painting;
            }
        }

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

        // Attack tile randomly
        ArrayList<MapInfo> targetTiles = new ArrayList<>();
        ArrayList<MapInfo> potentialTiles = new ArrayList<>();
        for (MapInfo tile : nearbyTiles) {
            boolean hasTower = false;
            if (rc.canSenseRobotAtLocation(tile.getMapLocation())) {
                if (rc.senseRobotAtLocation(tile.getMapLocation()).getType().isTowerType()) {
                    hasTower = true;
                }
            }
            if (!tile.hasRuin() && !hasTower && tile.getPaint() == PaintType.EMPTY && !tile.isWall()
                    || (tile.getMark() != tile.getPaint() && tile.getMark().isAlly() && tile.getPaint().isAlly())) {
                if (rc.canAttack(tile.getMapLocation())) {
                    targetTiles.add(tile);
                } else {
                    potentialTiles.add(tile);
                }
            }
        }

        // Mark resource
        if (tryMarkResource(rc, rc.getLocation())) {
            paintLocation = rc.getLocation();
            state = SoldierState.Painting;
        } else {
            nearbyTiles = rc.senseNearbyMapInfos(1);
            for (MapInfo tile : nearbyTiles) {
                if (tryMarkResource(rc, tile.getMapLocation())) {
                    paintLocation = tile.getMapLocation();
                    state = SoldierState.Painting;
                }
            }
        }

        boolean isAttacking = false;
        if (!targetTiles.isEmpty()) {
            MapInfo targetTile = targetTiles.get(RobotPlayer.rng.nextInt(targetTiles.size()));
            Direction dir = rc.getLocation().directionTo(targetTile.getMapLocation());
            boolean useSecondaryColor = targetTile.getMark() == PaintType.ALLY_SECONDARY;
            if (rc.canAttack(targetTile.getMapLocation())) {
                indicatorString += "\nTargetting : " + targetTile.getMapLocation().toString();
                isAttacking = true;
                rc.attack(targetTile.getMapLocation(), useSecondaryColor);
                if (rc.canMove(dir)) {
                    indicatorString += "\nMoving to : " + targetTile.getMapLocation().toString();
                    rc.move(dir);
                    moveDirection = dir;
                    if (paintTowerLocation != null)
                        moveStack.add(dir);
                }
            }
        } else if (!potentialTiles.isEmpty()) {
            MapInfo potentialTile = potentialTiles.get(RobotPlayer.rng.nextInt(potentialTiles.size()));
            Direction dir = rc.getLocation().directionTo(potentialTile.getMapLocation());
            if (rc.canMove(dir)) {
                indicatorString += "\nMoving to : " + potentialTile.getMapLocation().toString();
                rc.move(dir);
                moveDirection = dir;
                if (paintTowerLocation != null)
                    moveStack.add(dir);
            }
        }

        // Move straight or do a random direction
        if (rc.canMove(moveDirection)) {
            indicatorString += "\nMoving to direction : " + moveDirection.name();
            rc.move(moveDirection);
            if (paintTowerLocation != null)
                moveStack.add(moveDirection);
        } else {
            if (rc.isMovementReady() && !isAttacking && rc.isActionReady()) {
                indicatorString += "\nNew Direction : " + moveDirection.name();
                moveDirection = getNewDirection(rc, moveDirection);
            }
        }

        // Refill time
        if (rc.getPaint() < 30 && paintTowerLocation != null) {
            state = SoldierState.Refill;
            goBackIndex = moveStack.size() - 1;
        }
    }

    public static void paint(RobotController rc) throws GameActionException {
        indicatorString += paintLocation.toString();
        tryMarkTower(rc, paintLocation);
        boolean patternCompleted = true;
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                if (rc.canSenseLocation(paintLocation.translate(i, j))) {
                    MapInfo patternTile = rc.senseMapInfo(paintLocation.translate(i, j));
                    if (patternTile.getPaint().isEnemy()) {
                        state = SoldierState.Roaming;
                    } else if (patternTile.getMark() != patternTile.getPaint()
                            && patternTile.getMark() != PaintType.EMPTY) {
                        patternCompleted = false;
                        boolean useSecondaryColor = patternTile.getMark() == PaintType.ALLY_SECONDARY;
                        if (rc.canAttack(patternTile.getMapLocation())) {
                            rc.attack(patternTile.getMapLocation(), useSecondaryColor);
                        }
                        Direction dir = rc.getLocation().directionTo(patternTile.getMapLocation());
                        if (rc.canMove(dir)) {
                            rc.move(dir);
                            moveDirection = dir;
                            if (paintTowerLocation != null)
                                moveStack.add(dir);
                        }
                    }
                } else {
                    patternCompleted = false;
                }
            }
        }

        // Go to paintLocation, takutnya malah kabur
        Direction dir = rc.getLocation().directionTo(paintLocation);
        if (rc.canMove(dir)) {
            rc.move(dir);
            moveDirection = dir;
            if (paintTowerLocation != null)
                moveStack.add(dir);
        } else if (rc.senseMapInfo(rc.getLocation().add(dir)).isWall()) {
            state = SoldierState.Roaming;
        }

        if (tryCompleteTower(rc, paintLocation) || tryCompleteResource(rc, paintLocation)
                || rc.getNumberTowers() == GameConstants.MAX_NUMBER_OF_TOWERS) {
            state = SoldierState.Roaming;
            paintLocation = null;
        }

        // Refill time
        if (rc.getPaint() < 30 && !patternCompleted) {
            if (paintTowerLocation != null) {
                state = SoldierState.Refill;
                goBackIndex = moveStack.size() - 1;
            } else {
                state = SoldierState.Roaming;
            }
        }
    }

    public static void refill(RobotController rc) throws GameActionException {
        indicatorString += paintTowerLocation.toString();
        if (rc.canSenseLocation(paintTowerLocation)
                && !rc.senseMapInfo(rc.getLocation().add(rc.getLocation().directionTo(paintTowerLocation))).isWall()) {
            indicatorString += ">> I see the tower";
            Direction dir = rc.getLocation().directionTo(paintTowerLocation);
            if (rc.canMove(dir)) {
                rc.move(dir);
                moveStack.add(goBackIndex + 1, IndexToDirection((DirectionToIndex(dir) + 4) % 8));
            }
        } else if (goBackIndex >= 0) {
            Direction dir = moveStack.get(goBackIndex);
            Direction opposite = rc.getLocation().directionTo(rc.getLocation().subtract(dir));

            if (rc.canMove(opposite)) {
                rc.move(opposite);
                goBackIndex--;
            }
        }

        if (rc.getPaint() > 150) {
            goBackIndex++;
            state = SoldierState.BackToFront;
        }
    }

    public static void goBackToFront(RobotController rc) throws GameActionException {
        if (paintLocation != null && rc.canSenseLocation(paintLocation)) {
            while (moveStack.size() - 1 > goBackIndex) {
                moveStack.removeLast();
            }
            state = SoldierState.Painting;
        } else if (goBackIndex < moveStack.size()) {
            Direction dir = moveStack.get(goBackIndex);

            if (rc.canMove(dir)) {
                rc.move(dir);
                goBackIndex++;
            }

            // Refill time
            if (rc.getPaint() < 40) {
                if (paintTowerLocation != null) {
                    state = SoldierState.Refill;
                    goBackIndex = moveStack.size() - 1;
                } else {
                    state = SoldierState.Roaming;
                }
            }
        } else {
            state = SoldierState.Roaming;
        }
    }

    public static UnitType getRandomTowerType(RobotController rc) throws GameActionException {
        int index = RobotPlayer.rng.nextInt(2);
        if (index == 0) {
            return UnitType.LEVEL_ONE_PAINT_TOWER;
        } else if (index == 1) {
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        } else {
            return UnitType.LEVEL_ONE_DEFENSE_TOWER;
        }
    }

    public static void readMessagesFromTower(RobotController rc) throws GameActionException {
        Message[] messages = rc.readMessages(-1);
        if (messages.length > 0) {
            int data = messages[0].getBytes();
            if ((data & 2) == 2) {
                towerToBuild = UnitType.LEVEL_ONE_PAINT_TOWER;
            } else if ((data & 1) == 1) {
                towerToBuild = UnitType.LEVEL_ONE_MONEY_TOWER;
            } else {
                towerToBuild = UnitType.LEVEL_ONE_DEFENSE_TOWER;
            }
            System.out.println("Alright, time to build " + towerToBuild.name());
        }
    }

    public static boolean tryMarkTower(RobotController rc, MapLocation ruinLocation) throws GameActionException {
        UnitType towerType = towerToBuild;
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

    public static boolean tryCompleteTower(RobotController rc, MapLocation ruinLocation) throws GameActionException {
        System.out.println("Trying to complete ruin at " + ruinLocation.toString());
        if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinLocation)) {
            rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinLocation);
            return true;
        } else if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, ruinLocation)) {
            rc.completeTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, ruinLocation);
            return true;
        } else if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, ruinLocation)) {
            rc.completeTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, ruinLocation);
            return true;
        } else if (rc.canSenseRobotAtLocation(ruinLocation)) {
            if (rc.senseRobotAtLocation(ruinLocation).getType().isTowerType()) {
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

    public static Direction getNewDirection(RobotController rc, Direction initialDirection) throws GameActionException {
        // Ganti gerakan dengan mengutamakan gerakan diagonal
        int[] p;
        if (DirectionToIndex(initialDirection) % 2 == 0) {
            p = new int[] { 1, 2, 3, 4 };
        } else {
            p = new int[] { 2, 1, 3, 4 };
        }
        int reverse = RobotPlayer.rng.nextBoolean() ? 1 : -1;
        for (int i = 0; i <= 3; i++) {
            for (int j = -1; j <= 1; j += 2) {
                Direction newDirection = IndexToDirection(DirectionToIndex(initialDirection) + p[i] * j * reverse);
                if (rc.canMove(newDirection)) {
                    return newDirection;
                }
            }
        }
        return initialDirection;
    }
}
