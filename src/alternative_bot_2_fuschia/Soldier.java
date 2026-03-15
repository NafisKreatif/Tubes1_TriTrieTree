package alternative_bot_2_fuschia;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class Soldier extends Unit {
    private static final int REFILL_PAINT = 50;
    private static final int BUILD_PAINT = 70;
    private static final int FREE_PAINT = 110;
    private static final int TARGET_TIMEOUT = 40;

    private enum SoldierState { REFILL, COMBAT, BUILD_TOWER, BUILD_RESOURCE, EXPLORE }
    private enum ObjectiveType { TOWER, RESOURCE }

    private static final class Objective {
        private final ObjectiveType type;
        private final MapLocation location;
        private Objective(ObjectiveType type, MapLocation location) {
            this.type = type;
            this.location = location;
        }
    }

    private SoldierState state = SoldierState.EXPLORE;
    private RobotInfo[] nearbyRobots = new RobotInfo[0];
    private MapInfo[] nearbyTiles = new MapInfo[0];
    private MapLocation paintTowerLocation;
    private Objective objective;
    private UnitType towerToBuild = UnitType.LEVEL_ONE_PAINT_TOWER;
    private Direction roamDirection;

    public Soldier(RobotController rc) {
        super(rc);
        int seed = (rc.getLocation().x * 73 + rc.getLocation().y * 97 + rc.getRoundNum() * 31) & 7;
        roamDirection = Direction.values()[seed];
        if (roamDirection == Direction.CENTER) roamDirection = Direction.NORTH;
        targetTimer = TARGET_TIMEOUT;
    }

    @Override
    protected void determineState() throws GameActionException {
        nearbyRobots = rc.senseNearbyRobots();
        nearbyTiles = rc.senseNearbyMapInfos();
        readTowerDirective();
        refreshPaintTowerLocation();

        if (objective != null && isObjectiveDone()) objective = null;
        if (rc.getPaint() < REFILL_PAINT && paintTowerLocation != null) {
            state = SoldierState.REFILL;
            return;
        }
        if (findCombatRobot() != null) {
            state = SoldierState.COMBAT;
            return;
        }
        if (objective == null) objective = findObjective();
        if (objective != null) {
            state = objective.type == ObjectiveType.TOWER ? SoldierState.BUILD_TOWER : SoldierState.BUILD_RESOURCE;
            return;
        }
        refreshTargetIfNeeded();
        state = SoldierState.EXPLORE;
    }

    @Override
    protected void executeState() throws GameActionException {
        boolean productive = tryCompleteNearbyPatterns() | upgradeNearbyTowers();
        productive |= switch (state) {
            case REFILL -> doRefill();
            case COMBAT -> doCombat();
            case BUILD_TOWER -> doTowerObjective();
            case BUILD_RESOURCE -> doResourceObjective();
            case EXPLORE -> doExplore();
        };
        if (!productive && state != SoldierState.EXPLORE) productive |= doExplore();
        if (productive) markProductiveAction();
        rc.setIndicatorString(objective == null ? state.toString() : state + " " + objective.location + " " + towerToBuild.name());
        targetTimer -= 1;
    }

    private boolean doRefill() throws GameActionException {
        if (paintTowerLocation == null) return false;
        if (rc.getLocation().distanceSquaredTo(paintTowerLocation) > 2) return stepToward(paintTowerLocation);
        if (!rc.canTransferPaint(paintTowerLocation, -100)) return false;
        rc.transferPaint(paintTowerLocation, -100);
        idleTimer = 0;
        return true;
    }

    private boolean doCombat() throws GameActionException {
        RobotInfo target = findCombatRobot();
        if (target == null) return false;
        boolean productive = false;
        if (!rc.canAttack(target.location)) productive |= stepToward(target.location);
        if (rc.canAttack(target.location)) {
            rc.attack(target.location);
            productive = true;
        }
        if (rc.isMovementReady() && isTowerType(target.type)) productive |= stepToward(target.location);
        return productive;
    }

    private boolean doTowerObjective() throws GameActionException {
        if (objective == null) return false;
        MapLocation center = objective.location;
        if (!isRuinAvailable(center)) {
            objective = null;
            return false;
        }
        if (rc.getPaint() < BUILD_PAINT && paintTowerLocation != null) return doRefill();
        if (!rc.canSenseLocation(center)) return stepToward(center);

        boolean productive = false;
        if (!hasAllyPatternMark(center, 8) && rc.canMarkTowerPattern(towerToBuild, center)) {
            rc.markTowerPattern(towerToBuild, center);
            mapData.markRuin(center, MapData.RUIN_TAINTED);
            productive = true;
        }

        MapLocation workTile = findMarkedMismatch(center, 8);
        if (workTile != null) {
            if (!tryPaintTile(workTile)) {
                productive |= stepToward(workTile);
                productive |= tryPaintTile(workTile);
            } else {
                productive = true;
            }
        }

        UnitType completionType = findCompletableTowerType(center);
        if (completionType != null) {
            rc.completeTowerPattern(completionType, center);
            mapData.markRuin(center, MapData.RUIN_ALLY_OWNED);
            objective = null;
            return true;
        }
        return productive || stepToward(center);
    }

    private boolean doResourceObjective() throws GameActionException {
        if (objective == null) return false;
        MapLocation center = objective.location;
        if (rc.getPaint() < BUILD_PAINT && paintTowerLocation != null) return doRefill();
        if (!rc.canSenseLocation(center)) return stepToward(center);

        boolean productive = false;
        if (shouldMarkResourcePattern(center)) {
            rc.markResourcePattern(center);
            productive = true;
        }

        MapLocation workTile = findMarkedMismatch(center, 4);
        if (workTile != null) {
            if (!tryPaintTile(workTile)) {
                productive |= stepToward(workTile);
                productive |= tryPaintTile(workTile);
            } else {
                productive = true;
            }
        }

        if (rc.canCompleteResourcePattern(center)) {
            rc.completeResourcePattern(center);
            objective = null;
            return true;
        }
        return productive || stepToward(center);
    }

    private boolean doExplore() throws GameActionException {
        boolean productive = false;
        if (rc.getPaint() >= FREE_PAINT) {
            MapLocation paintTarget = findBestPaintTarget();
            if (paintTarget != null && tryPaintTile(paintTarget)) productive = true;
        }

        MapLocation moveTarget = pickExploreTarget();
        productive |= moveTarget != null ? stepToward(moveTarget) : moveInRoamDirection();
        if (productive) {
            idleTimer = 0;
            return true;
        }

        idleTimer += 1;
        if (idleTimer >= 10) {
            setTarget(getOppositeCorner(rc.getLocation()));
            targetTimer = TARGET_TIMEOUT;
            idleTimer = 0;
        }
        return moveInRoamDirection();
    }

    private boolean tryCompleteNearbyPatterns() throws GameActionException {
        boolean completed = false;
        for (MapInfo tile : nearbyTiles) {
            MapLocation loc = tile.getMapLocation();
            if (tile.hasRuin() && !hasTowerAt(loc)) {
                UnitType completionType = findCompletableTowerType(loc);
                if (completionType != null) {
                    rc.completeTowerPattern(completionType, loc);
                    mapData.markRuin(loc, MapData.RUIN_ALLY_OWNED);
                    if (objective != null && objective.location.equals(loc)) objective = null;
                    completed = true;
                }
            }
            if (tile.getMark() != PaintType.EMPTY && rc.canCompleteResourcePattern(loc)) {
                rc.completeResourcePattern(loc);
                if (objective != null && objective.type == ObjectiveType.RESOURCE && objective.location.equals(loc)) {
                    objective = null;
                }
                completed = true;
            }
        }
        return completed;
    }

    private boolean upgradeNearbyTowers() throws GameActionException {
        boolean upgraded = false;
        for (RobotInfo ally : rc.senseNearbyRobots(2, rc.getTeam())) {
            if (ally.type.isTowerType() && rc.canUpgradeTower(ally.location)) {
                rc.upgradeTower(ally.location);
                upgraded = true;
            }
        }
        return upgraded;
    }

    private Objective findObjective() throws GameActionException {
        Objective towerObjective = findTowerObjective();
        return towerObjective != null ? towerObjective : findResourceObjective();
    }

    private Objective findTowerObjective() throws GameActionException {
        if (rc.getNumberTowers() >= GameConstants.MAX_NUMBER_OF_TOWERS) return null;
        MapLocation me = rc.getLocation();
        MapLocation best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (MapInfo tile : nearbyTiles) {
            if (!tile.hasRuin()) continue;
            MapLocation center = tile.getMapLocation();
            if (!isRuinAvailable(center)) continue;
            int distance = me.distanceSquaredTo(center);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = center;
            }
        }
        return best == null ? null : new Objective(ObjectiveType.TOWER, best);
    }

    private Objective findResourceObjective() throws GameActionException {
        MapLocation me = rc.getLocation();
        if (isValidSrpCenter(me) && (shouldMarkResourcePattern(me) || hasIncompleteResourcePattern(me))) {
            return new Objective(ObjectiveType.RESOURCE, me);
        }
        for (MapInfo tile : rc.senseNearbyMapInfos(1)) {
            MapLocation candidate = tile.getMapLocation();
            if (isValidSrpCenter(candidate) && (shouldMarkResourcePattern(candidate) || hasIncompleteResourcePattern(candidate))) {
                return new Objective(ObjectiveType.RESOURCE, candidate);
            }
        }
        return null;
    }

    private RobotInfo findCombatRobot() {
        RobotInfo best = null;
        int bestScore = Integer.MIN_VALUE;
        MapLocation me = rc.getLocation();
        for (RobotInfo robot : nearbyRobots) {
            if (robot.team != rc.getTeam().opponent()) continue;
            int score = isTowerType(robot.type) ? 100 : 40;
            if (rc.canAttack(robot.location)) score += 25;
            score -= me.distanceSquaredTo(robot.location);
            if (score > bestScore) {
                bestScore = score;
                best = robot;
            }
        }
        return best;
    }

    private MapLocation pickExploreTarget() {
        MapLocation me = rc.getLocation();
        MapLocation enemyPaint = findNearestEnemyPaint();
        if (enemyPaint != null) return enemyPaint;
        MapLocation ruin = mapData.getNearestUnclaimed(me);
        if (ruin != null) return ruin;
        MapLocation target = getTargetLocation();
        if (me.distanceSquaredTo(target) <= 4 || targetTimer <= 0) {
            setTarget(getOppositeCorner(me));
            targetTimer = TARGET_TIMEOUT;
            target = getTargetLocation();
        }
        return target;
    }

    private void readTowerDirective() throws GameActionException {
        Message[] messages = rc.readMessages(-1);
        for (Message message : messages) {
            int data = message.getBytes();
            if (data < 0 || data > 3) continue;
            if ((data & 2) == 2) towerToBuild = UnitType.LEVEL_ONE_PAINT_TOWER;
            else if ((data & 1) == 1) towerToBuild = UnitType.LEVEL_ONE_MONEY_TOWER;
            else towerToBuild = UnitType.LEVEL_ONE_DEFENSE_TOWER;
            return;
        }
    }

    private void refreshPaintTowerLocation() {
        MapLocation me = rc.getLocation();
        MapLocation best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (RobotInfo ally : nearbyRobots) {
            if (ally.team != rc.getTeam() || !isPaintTower(ally.type)) continue;
            int distance = me.distanceSquaredTo(ally.location);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = ally.location;
            }
        }
        if (best == null) {
            for (int i = 0; i < mapData.towerCount; i++) {
                UnitType type = UnitType.values()[mapData.towerTypes[i]];
                if (!isPaintTower(type)) continue;
                int distance = me.distanceSquaredTo(mapData.knownTowers[i]);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = mapData.knownTowers[i];
                }
            }
        }
        paintTowerLocation = best;
    }

    private boolean isObjectiveDone() throws GameActionException {
        if (objective == null || !rc.canSenseLocation(objective.location)) return false;
        if (objective.type == ObjectiveType.RESOURCE) {
            return !shouldMarkResourcePattern(objective.location)
                    && !hasIncompleteResourcePattern(objective.location)
                    && !rc.canCompleteResourcePattern(objective.location);
        }
        return hasTowerAt(objective.location) || !isRuinAvailable(objective.location);
    }

    private boolean isRuinAvailable(MapLocation center) throws GameActionException {
        if (center == null || rc.getNumberTowers() >= GameConstants.MAX_NUMBER_OF_TOWERS) return false;
        return !rc.canSenseLocation(center) || !hasTowerAt(center);
    }

    private boolean hasTowerAt(MapLocation location) throws GameActionException {
        if (!rc.canSenseLocation(location) || !rc.canSenseRobotAtLocation(location)) return false;
        RobotInfo robot = rc.senseRobotAtLocation(location);
        return robot != null && robot.type.isTowerType();
    }

    private MapLocation findMarkedMismatch(MapLocation center, int maxDistanceSquared) throws GameActionException {
        MapLocation me = rc.getLocation();
        MapLocation best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (MapInfo tile : rc.senseNearbyMapInfos(center, 8)) {
            MapLocation loc = tile.getMapLocation();
            if (loc.distanceSquaredTo(center) > maxDistanceSquared || tile.isWall() || tile.hasRuin()) continue;
            PaintType mark = tile.getMark();
            if (mark != PaintType.ALLY_PRIMARY && mark != PaintType.ALLY_SECONDARY) continue;
            PaintType paint = tile.getPaint();
            boolean matched = (mark == PaintType.ALLY_PRIMARY && paint == PaintType.ALLY_PRIMARY)
                    || (mark == PaintType.ALLY_SECONDARY && paint == PaintType.ALLY_SECONDARY);
            if (matched) continue;
            int distance = me.distanceSquaredTo(loc);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = loc;
            }
        }
        return best;
    }

    private MapLocation findBestPaintTarget() {
        MapLocation me = rc.getLocation();
        MapLocation best = null;
        int bestScore = Integer.MIN_VALUE;
        for (MapInfo tile : nearbyTiles) {
            MapLocation loc = tile.getMapLocation();
            if (tile.isWall() || tile.hasRuin() || !rc.canAttack(loc)) continue;
            int score = 0;
            PaintType paint = tile.getPaint();
            PaintType mark = tile.getMark();
            if (paint.isEnemy()) score += 45;
            else if (paint == PaintType.EMPTY) score += 10;
            if (mark == PaintType.ALLY_PRIMARY || mark == PaintType.ALLY_SECONDARY) {
                boolean matched = (mark == PaintType.ALLY_PRIMARY && paint == PaintType.ALLY_PRIMARY)
                        || (mark == PaintType.ALLY_SECONDARY && paint == PaintType.ALLY_SECONDARY);
                if (!matched) score += 20;
            }
            score -= me.distanceSquaredTo(loc);
            if (score > bestScore) {
                bestScore = score;
                best = loc;
            }
        }
        return best;
    }

    private MapLocation findNearestEnemyPaint() {
        MapLocation me = rc.getLocation();
        MapLocation best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (MapInfo tile : nearbyTiles) {
            if (!tile.getPaint().isEnemy()) continue;
            int distance = me.distanceSquaredTo(tile.getMapLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = tile.getMapLocation();
            }
        }
        return best;
    }

    private boolean stepToward(MapLocation target) throws GameActionException {
        if (target == null || !rc.isMovementReady()) return false;
        Direction direct = rc.getLocation().directionTo(target);
        Direction[] options = {
            direct, direct.rotateLeft(), direct.rotateRight(),
            direct.rotateLeft().rotateLeft(), direct.rotateRight().rotateRight(), direct.opposite()
        };
        for (Direction option : options) if (tryMove(option)) return true;
        return false;
    }

    private boolean moveInRoamDirection() throws GameActionException {
        if (!rc.isMovementReady()) return false;
        if (tryMove(roamDirection)) return true;
        Direction left = roamDirection;
        Direction right = roamDirection;
        for (int i = 0; i < 3; i++) {
            left = left.rotateLeft();
            if (tryMove(left)) return true;
            right = right.rotateRight();
            if (tryMove(right)) return true;
        }
        roamDirection = Direction.values()[rng.nextInt(8)];
        if (roamDirection == Direction.CENTER) roamDirection = Direction.NORTH;
        return false;
    }

    private boolean tryMove(Direction direction) throws GameActionException {
        if (direction == null || direction == Direction.CENTER || !rc.canMove(direction)) return false;
        rc.move(direction);
        roamDirection = direction;
        return true;
    }

    private boolean tryPaintTile(MapLocation tile) throws GameActionException {
        if (tile == null || !rc.canAttack(tile)) return false;
        PaintType mark = rc.senseMapInfo(tile).getMark();
        if (mark == PaintType.ALLY_PRIMARY || mark == PaintType.ALLY_SECONDARY) rc.attack(tile, mark == PaintType.ALLY_SECONDARY);
        else rc.attack(tile);
        return true;
    }

    private UnitType findCompletableTowerType(MapLocation center) throws GameActionException {
        if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, center)) return UnitType.LEVEL_ONE_PAINT_TOWER;
        if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, center)) return UnitType.LEVEL_ONE_MONEY_TOWER;
        if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, center)) return UnitType.LEVEL_ONE_DEFENSE_TOWER;
        return null;
    }

    private boolean hasAllyPatternMark(MapLocation center, int maxDistanceSquared) throws GameActionException {
        for (MapInfo tile : rc.senseNearbyMapInfos(center, 8)) {
            if (tile.getMapLocation().distanceSquaredTo(center) > maxDistanceSquared) continue;
            PaintType mark = tile.getMark();
            if (mark == PaintType.ALLY_PRIMARY || mark == PaintType.ALLY_SECONDARY) return true;
        }
        return false;
    }

    private boolean isValidSrpCenter(MapLocation center) {
        return center.x % 4 == 2 && center.y % 4 == 2 && !hasRuinWithin3(center);
    }

    private boolean shouldMarkResourcePattern(MapLocation center) throws GameActionException {
        if (!rc.canMarkResourcePattern(center)) return false;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                MapLocation loc = center.translate(dx, dy);
                if (!rc.canSenseLocation(loc)) return false;
                MapInfo tile = rc.senseMapInfo(loc);
                if (tile.hasRuin() || tile.isWall() || tile.getMark() != PaintType.EMPTY || !tile.getPaint().isAlly()) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean hasIncompleteResourcePattern(MapLocation center) throws GameActionException {
        return findMarkedMismatch(center, 4) != null;
    }

    private boolean hasRuinWithin3(MapLocation center) {
        for (MapInfo tile : nearbyTiles) if (tile.hasRuin() && center.distanceSquaredTo(tile.getMapLocation()) <= 3) return true;
        return false;
    }
}
