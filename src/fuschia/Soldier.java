package fuschia;

import java.util.ArrayList;
import java.util.List;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;


public class Soldier extends Unit {
    enum SoldierState {
        REFILL,
        COMBAT,
        TOWER_BUILD,
        TAINT,
        SRP_BUILD,
        EXPLORE
    }

    private final List<Direction> moveHistory = new ArrayList<>(); // to move back to
    private Direction moveDirection;

    private RobotInfo[] nearbyRobots = new RobotInfo[0];
    private MapLocation knownPaintTower;

    private boolean refillMode;
    private boolean returnMode;
    private int returnIndex = -1;

    private Objective objective;
    private SoldierState state = SoldierState.EXPLORE;
    private boolean objectiveFromFreshMark;

    public Soldier(RobotController rc) {
        super(rc);
        moveDirection = Direction.values()[rng.nextInt(8)];
    }

    @Override
    protected void determineState() throws GameActionException {
        nearbyRobots = rc.senseNearbyRobots();
        mapData.update(rc, nearbyRobots);

        refreshKnownPaintTower();

        if (!refillMode && rc.getPaint() < 40 && knownPaintTower != null) {
            refillMode = true;
            returnMode = false;
            returnIndex = moveHistory.size() - 1;
        }

        if (refillMode && rc.getPaint() > 180) {
            refillMode = false;
            returnMode = objective != null && returnIndex >= 0;
        }

        if (isObjectiveCompleted()) {
            clearObjective();
        }
    }

    @Override
    protected void executeState() throws GameActionException {
        if (refillMode) {
            state = SoldierState.REFILL;
            rc.setIndicatorString(state.toString());
            if (doRefill()) {
                markProductiveAction();
            }
            return;
        }

        if (returnMode) {
            state = SoldierState.EXPLORE;
            rc.setIndicatorString(state.toString());
            if (doReturnToFront()) {
                markProductiveAction();
            }
            return;
        }

        state = SoldierState.EXPLORE;

        boolean productive = false;
        if (tryCompleteNearbyPatterns()) {
            productive = true;
        }

        if (tryAttackEnemyTower()) {
            state = SoldierState.COMBAT;
            productive = true;
        }

        if (objective == null) {
            selectObjective();
        }

        if (objective != null && executeObjective()) {
            productive = true;
        }

        if (roamAndPaint()) {
            productive = true;
        }

        rc.setIndicatorString(state.toString());

        if (productive) {
            markProductiveAction();
        }
    }

    private boolean doRefill() throws GameActionException {
        refreshKnownPaintTower();
        if (knownPaintTower == null) {
            refillMode = false;
            moveHistory.clear();
            return false;
        }

        boolean productive = false;
        if (rc.getLocation().distanceSquaredTo(knownPaintTower) > 2) {
            productive |= moveToward(knownPaintTower, false);
        } else if (rc.canTransferPaint(knownPaintTower, -100)) {
            rc.transferPaint(knownPaintTower, -100);
            productive = true;
        }

        for (RobotInfo ally : rc.senseNearbyRobots(-1, rc.getTeam())) {
            if (rc.canUpgradeTower(ally.location)) {
                rc.upgradeTower(ally.location);
                productive = true;
            }
        }

        if (rc.getPaint() > 180) {
            refillMode = false;
            returnMode = objective != null && returnIndex >= 0;
            if (!returnMode) {
                moveHistory.clear();
            }
        }

        return productive;
    }

    private boolean doReturnToFront() throws GameActionException {
        if (returnIndex < 0 || returnIndex >= moveHistory.size()) {
            returnMode = false;
            return true;
        }

        Direction dir = moveHistory.get(returnIndex);
        if (moveIfPossible(dir, false)) {
            returnIndex += 1;
            if (returnIndex >= moveHistory.size()) {
                returnMode = false;
            }
            return true;
        }

        returnMode = false;
        return false;
    }

    private void selectObjective() throws GameActionException {
        MapLocation incompleteTower = findIncompleteAllyPatternCenter();
        if (incompleteTower != null) {
            UnitType towerType = countKnownPaintTowers() == 0
                ? UnitType.LEVEL_ONE_PAINT_TOWER
                : UnitType.LEVEL_ONE_MONEY_TOWER;
            objective = Objective.tower(incompleteTower, towerType);
            objectiveFromFreshMark = false;
            return;
        }

        PatternPlan plan = findAndMarkNewRuinPattern();
        if (plan != null) {
            objective = Objective.tower(plan.location, plan.towerType);
            objectiveFromFreshMark = true;
            return;
        }

        MapLocation resourceCenter = findResourceObjective();
        if (resourceCenter != null) {
            objective = Objective.resource(resourceCenter);
            objectiveFromFreshMark = false;
        }
    }

    private boolean executeObjective() throws GameActionException {
        if (objective == null) {
            return false;
        }

        return objective.isResource ? executeResourceObjective() : executeTowerObjective();
    }

    private boolean executeTowerObjective() throws GameActionException {
        if (objective == null) {
            return false;
        }

        if (rc.getPaint() < 40) {
            refillMode = true;
            returnMode = false;
            returnIndex = moveHistory.size() - 1;
            return false;
        }

        state = objectiveFromFreshMark ? SoldierState.TAINT : SoldierState.TOWER_BUILD;
        MapLocation objectiveCenter = objective.location;
        boolean productive = false;
        if (objective.towerType != null && rc.canMarkTowerPattern(objective.towerType, objectiveCenter)) {
            rc.markTowerPattern(objective.towerType, objectiveCenter);
            productive = true;
        }

        MapInfo[] patternTiles = rc.senseNearbyMapInfos(objectiveCenter, 8);
        MapLocation unpainted = findNearestUnpaintedPatternTile(objectiveCenter, patternTiles);
        if (unpainted != null) {
            if (!tryPaintTile(unpainted)) {
                productive |= moveToward(unpainted, true);
                productive |= tryPaintTile(unpainted);
            } else {
                productive = true;
            }
        } else {
            productive |= moveToward(objectiveCenter, true);
        }

        UnitType completionType = findCompletableTowerType(objectiveCenter);
        if (completionType != null) {
            rc.completeTowerPattern(completionType, objectiveCenter);
            mapData.markRuin(objectiveCenter, MapData.RUIN_ALLY_OWNED);
            clearObjective();
            productive = true;
        } else {
            objectiveFromFreshMark = false;
        }

        return productive;
    }

    private boolean executeResourceObjective() throws GameActionException {
        if (objective == null) {
            return false;
        }

        state = SoldierState.SRP_BUILD;
        MapLocation objectiveCenter = objective.location;
        boolean productive = false;
        if (shouldMarkResourcePattern(objectiveCenter) && rc.canMarkResourcePattern(objectiveCenter)) {
            rc.markResourcePattern(objectiveCenter);
            productive = true;
        }

        MapInfo[] tiles = rc.senseNearbyMapInfos(objectiveCenter, 8);
        MapLocation unpainted = findNearestUnpaintedResourceTile(objectiveCenter, tiles);
        if (unpainted != null) {
            if (!tryPaintTile(unpainted)) {
                productive |= moveToward(unpainted, true);
                productive |= tryPaintTile(unpainted);
            } else {
                productive = true;
            }
        } else {
            productive |= moveToward(objectiveCenter, true);
        }

        if (rc.canCompleteResourcePattern(objectiveCenter)) {
            rc.completeResourcePattern(objectiveCenter);
            clearObjective();
            productive = true;
        }

        return productive;
    }

    private boolean roamAndPaint() throws GameActionException {
        boolean productive = false;

        MapLocation paintTarget = findBestPaintTarget();
        if (paintTarget != null && tryPaintTile(paintTarget)) {
            productive = true;
            Direction attackDir = rc.getLocation().directionTo(paintTarget);
            if (attackDir != Direction.CENTER) {
                moveIfPossible(attackDir, true);
            }
        }

        MapLocation chaseTarget = findNearestEnemyPaintTile();
        if (chaseTarget == null) {
            chaseTarget = getOppositeCorner(rc.getLocation());
        }

        if (rc.isMovementReady() && chaseTarget != null) {
            productive |= moveToward(chaseTarget, true);
        }

        if (rc.isMovementReady() && !moveIfPossible(moveDirection, true)) {
            Direction alt = Direction.values()[rng.nextInt(8)];
            moveIfPossible(alt, true);
        }

        return productive;
    }

    private boolean tryAttackEnemyTower() throws GameActionException {
        RobotInfo best = null;
        int bestDist = Integer.MAX_VALUE;
        for (RobotInfo robot : nearbyRobots) {
            if (robot.team != rc.getTeam().opponent() || !isTowerType(robot.type)) {
                continue;
            }
            int dist = rc.getLocation().distanceSquaredTo(robot.location);
            if (dist < bestDist) {
                bestDist = dist;
                best = robot;
            }
        }

        if (best == null) {
            return false;
        }

        boolean productive = false;
        if (rc.canAttack(best.location)) {
            rc.attack(best.location);
            productive = true;
        }

        if (rc.isMovementReady()) {
            productive |= moveToward(best.location, true);
        }

        return productive;
    }

    private boolean tryCompleteNearbyPatterns() throws GameActionException {
        boolean completed = false;
        for (MapInfo tile : rc.senseNearbyMapInfos()) {
            MapLocation loc = tile.getMapLocation();
            if (tile.hasRuin()) {
                UnitType completionType = findCompletableTowerType(loc);
                if (completionType != null) {
                    rc.completeTowerPattern(completionType, loc);
                    mapData.markRuin(loc, MapData.RUIN_ALLY_OWNED);
                    completed = true;
                }
            }
            if (tile.getMark() != PaintType.EMPTY && rc.canCompleteResourcePattern(loc)) {
                rc.completeResourcePattern(loc);
                completed = true;
            }
        }
        return completed;
    }

    private PatternPlan findAndMarkNewRuinPattern() throws GameActionException {
        for (MapInfo visible : rc.senseNearbyMapInfos()) {
            if (!visible.hasRuin()) {
                continue;
            }

            MapLocation ruinCenter = visible.getMapLocation();
            if (!isRuinUnclaimed(ruinCenter)) {
                continue;
            }

            if (hasAllyPatternMark(ruinCenter)) {
                UnitType existing = findPatternTowerType(ruinCenter);
                if (existing != null) {
                    return new PatternPlan(ruinCenter, existing);
                }
                continue;
            }

            UnitType towerType = chooseTowerType();
            if (rc.canMarkTowerPattern(towerType, ruinCenter)) {
                rc.markTowerPattern(towerType, ruinCenter);
                mapData.markRuin(ruinCenter, MapData.RUIN_TAINTED);
                return new PatternPlan(ruinCenter, towerType);
            }
        }

        return null;
    }

    private UnitType chooseTowerType() {
        if (countKnownPaintTowers() == 0) {
            return UnitType.LEVEL_ONE_PAINT_TOWER;
        }
        return rng.nextInt(2) == 0 ? UnitType.LEVEL_ONE_PAINT_TOWER : UnitType.LEVEL_ONE_MONEY_TOWER;
    }

    private UnitType findPatternTowerType(MapLocation center) throws GameActionException {
        UnitType[] candidates = {
            UnitType.LEVEL_ONE_PAINT_TOWER,
            UnitType.LEVEL_ONE_MONEY_TOWER,
            UnitType.LEVEL_ONE_DEFENSE_TOWER
        };

        for (UnitType candidate : candidates) {
            if (rc.canMarkTowerPattern(candidate, center) || rc.canCompleteTowerPattern(candidate, center)) {
                return candidate;
            }
        }
        return null;
    }

    private MapLocation findResourceObjective() throws GameActionException {
        MapLocation me = rc.getLocation();
        if (isValidSrpCenter(me) && (shouldMarkResourcePattern(me) || hasIncompleteResourcePattern(me))) {
            return me;
        }

        for (MapInfo tile : rc.senseNearbyMapInfos(1)) {
            MapLocation candidate = tile.getMapLocation();
            if (isValidSrpCenter(candidate)
                && (shouldMarkResourcePattern(candidate) || hasIncompleteResourcePattern(candidate))) {
                return candidate;
            }
        }

        return null;
    }

    private MapLocation findBestPaintTarget() {
        MapLocation me = rc.getLocation();
        MapLocation best = null;
        int bestScore = Integer.MIN_VALUE;

        for (MapInfo tile : rc.senseNearbyMapInfos()) {
            MapLocation loc = tile.getMapLocation();
            if (tile.isWall() || tile.hasRuin() || !rc.canAttack(loc)) {
                continue;
            }

            PaintType paint = tile.getPaint();
            PaintType mark = tile.getMark();

            int score = 0;
            if (paint.isEnemy()) {
                score += 60;
            } else if (paint == PaintType.EMPTY) {
                score += 20;
            }

            if (mark == PaintType.ALLY_PRIMARY || mark == PaintType.ALLY_SECONDARY) {
                boolean paintedCorrectly =
                    (mark == PaintType.ALLY_PRIMARY && paint == PaintType.ALLY_PRIMARY)
                        || (mark == PaintType.ALLY_SECONDARY && paint == PaintType.ALLY_SECONDARY);
                if (!paintedCorrectly) {
                    score += 15;
                }
            }

            score -= me.distanceSquaredTo(loc);

            if (score > bestScore) {
                bestScore = score;
                best = loc;
            }
        }

        return best;
    }

    private MapLocation findNearestEnemyPaintTile() {
        MapLocation me = rc.getLocation();
        MapLocation best = null;
        int bestDistance = Integer.MAX_VALUE;

        for (MapInfo tile : rc.senseNearbyMapInfos()) {
            if (!tile.getPaint().isEnemy()) {
                continue;
            }

            int distance = me.distanceSquaredTo(tile.getMapLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = tile.getMapLocation();
            }
        }

        return best;
    }

    private boolean moveToward(MapLocation target, boolean recordHistory) throws GameActionException {
        if (target == null || !rc.isMovementReady()) {
            return false;
        }

        Direction direct = rc.getLocation().directionTo(target);
        Direction[] prefs = {
            direct,
            direct.rotateLeft(),
            direct.rotateRight(),
            direct.rotateLeft().rotateLeft(),
            direct.rotateRight().rotateRight(),
            direct.opposite()
        };

        for (Direction dir : prefs) {
            if (dir != Direction.CENTER && moveIfPossible(dir, recordHistory)) {
                return true;
            }
        }

        return false;
    }

    private boolean moveIfPossible(Direction dir, boolean recordHistory) throws GameActionException {
        if (dir == null || dir == Direction.CENTER || !rc.isMovementReady() || !rc.canMove(dir)) {
            return false;
        }

        rc.move(dir);
        moveDirection = dir;

        if (recordHistory && knownPaintTower != null && !refillMode && !returnMode) {
            moveHistory.add(dir);
            if (moveHistory.size() > 240) {
                moveHistory.remove(0);
            }
            returnIndex = moveHistory.size() - 1;
        }

        return true;
    }

    private boolean tryPaintTile(MapLocation tile) throws GameActionException {
        if (tile == null || !rc.canAttack(tile)) {
            return false;
        }

        PaintType mark = rc.senseMapInfo(tile).getMark();
        if (mark == PaintType.ALLY_PRIMARY || mark == PaintType.ALLY_SECONDARY) {
            rc.attack(tile, mark == PaintType.ALLY_SECONDARY);
        } else {
            rc.attack(tile);
        }
        return true;
    }

    private boolean isObjectiveCompleted() throws GameActionException {
        if (objective == null) {
            return false;
        }

        if (objective.isResource) {
            if (!rc.canSenseLocation(objective.location)) {
                return false;
            }
            return findIncompleteResourcePatternTile(objective.location, rc.senseNearbyMapInfos(objective.location, 8)) == null
                && !rc.canCompleteResourcePattern(objective.location);
        }

        if (!rc.canSenseLocation(objective.location)) {
            return false;
        }

        if (rc.canSenseRobotAtLocation(objective.location)) {
            RobotInfo at = rc.senseRobotAtLocation(objective.location);
            return at != null && isTowerType(at.type) && at.team == rc.getTeam();
        }

        return false;
    }

    private void clearObjective() {
        objective = null;
        objectiveFromFreshMark = false;
    }

    private void refreshKnownPaintTower() throws GameActionException {
        MapLocation me = rc.getLocation();
        int bestDistance = Integer.MAX_VALUE;
        MapLocation best = null;

        for (RobotInfo ally : rc.senseNearbyRobots(-1, rc.getTeam())) {
            if (!isPaintTower(ally.type)) {
                continue;
            }
            int distance = me.distanceSquaredTo(ally.location);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = ally.location;
            }
        }

        if (best == null) {
            for (int i = 0; i < mapData.towerCount; i++) {
                UnitType type = UnitType.values()[mapData.towerTypes[i]];
                if (!isPaintTower(type)) {
                    continue;
                }
                int distance = me.distanceSquaredTo(mapData.knownTowers[i]);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = mapData.knownTowers[i];
                }
            }
        }

        if (best != null) {
            knownPaintTower = best;
        }
    }

    private MapLocation findIncompleteAllyPatternCenter() throws GameActionException {
        for (MapInfo visible : rc.senseNearbyMapInfos()) {
            if (!visible.hasRuin()) {
                continue;
            }

            MapLocation ruinCenter = visible.getMapLocation();
            boolean hasAllyMark = false;
            boolean hasIncompleteMarkedTile = false;

            for (MapInfo tile : rc.senseNearbyMapInfos(ruinCenter, 8)) {
                PaintType mark = tile.getMark();
                if (mark != PaintType.ALLY_PRIMARY && mark != PaintType.ALLY_SECONDARY) {
                    continue;
                }

                hasAllyMark = true;
                PaintType paint = tile.getPaint();
                boolean paintedCorrectly =
                    (mark == PaintType.ALLY_PRIMARY && paint == PaintType.ALLY_PRIMARY)
                        || (mark == PaintType.ALLY_SECONDARY && paint == PaintType.ALLY_SECONDARY);
                if (!paintedCorrectly) {
                    hasIncompleteMarkedTile = true;
                    break;
                }
            }

            if (hasAllyMark && hasIncompleteMarkedTile) {
                return ruinCenter;
            }
        }
        return null;
    }

    private MapLocation findNearestUnpaintedPatternTile(MapLocation center, MapInfo[] nearbyTiles) {
        MapLocation me = rc.getLocation();
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;

        for (MapInfo tile : nearbyTiles) {
            MapLocation loc = tile.getMapLocation();
            if (loc.distanceSquaredTo(center) > 4 || tile.isWall() || tile.hasRuin()) {
                continue;
            }

            PaintType mark = tile.getMark();
            if (mark != PaintType.ALLY_PRIMARY && mark != PaintType.ALLY_SECONDARY) {
                continue;
            }

            PaintType paint = tile.getPaint();
            boolean paintedCorrectly =
                (mark == PaintType.ALLY_PRIMARY && paint == PaintType.ALLY_PRIMARY)
                    || (mark == PaintType.ALLY_SECONDARY && paint == PaintType.ALLY_SECONDARY);
            if (paintedCorrectly) {
                continue;
            }

            int dist = me.distanceSquaredTo(loc);
            if (dist < bestDist) {
                bestDist = dist;
                best = loc;
            }
        }

        return best;
    }

    private MapLocation findNearestUnpaintedResourceTile(MapLocation center, MapInfo[] nearbyTiles) {
        MapLocation me = rc.getLocation();
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;

        for (MapInfo tile : nearbyTiles) {
            MapLocation loc = tile.getMapLocation();
            if (loc.distanceSquaredTo(center) > 4 || tile.isWall() || tile.hasRuin()) {
                continue;
            }

            PaintType mark = tile.getMark();
            if (mark != PaintType.ALLY_PRIMARY && mark != PaintType.ALLY_SECONDARY) {
                continue;
            }

            PaintType paint = tile.getPaint();
            boolean paintedCorrectly =
                (mark == PaintType.ALLY_PRIMARY && paint == PaintType.ALLY_PRIMARY)
                    || (mark == PaintType.ALLY_SECONDARY && paint == PaintType.ALLY_SECONDARY);
            if (paintedCorrectly) {
                continue;
            }

            int dist = me.distanceSquaredTo(loc);
            if (dist < bestDist) {
                bestDist = dist;
                best = loc;
            }
        }

        return best;
    }

    private UnitType findCompletableTowerType(MapLocation center) throws GameActionException {
        UnitType[] candidates = {
            UnitType.LEVEL_ONE_PAINT_TOWER,
            UnitType.LEVEL_ONE_MONEY_TOWER,
            UnitType.LEVEL_ONE_DEFENSE_TOWER
        };

        for (UnitType candidate : candidates) {
            if (rc.canCompleteTowerPattern(candidate, center)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean hasAllyPatternMark(MapLocation center) throws GameActionException {
        for (MapInfo tile : rc.senseNearbyMapInfos(center, 8)) {
            PaintType mark = tile.getMark();
            if (mark == PaintType.ALLY_PRIMARY || mark == PaintType.ALLY_SECONDARY) {
                return true;
            }
        }
        return false;
    }

    private boolean isRuinUnclaimed(MapLocation ruin) {
        for (RobotInfo robot : nearbyRobots) {
            if (!robot.location.equals(ruin)) {
                continue;
            }
            if (isTowerType(robot.type)) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidSrpCenter(MapLocation center) throws GameActionException {
        return center.x % 4 == 2 && center.y % 4 == 2 && !hasRuinWithin3(center);
    }

    private boolean shouldMarkResourcePattern(MapLocation center) throws GameActionException {
        if (center == null || !rc.canMarkResourcePattern(center)) {
            return false;
        }

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                MapLocation loc = center.translate(dx, dy);
                if (!rc.canSenseLocation(loc)) {
                    return false;
                }

                MapInfo tile = rc.senseMapInfo(loc);
                if (tile.hasRuin() || tile.isWall() || tile.getMark() != PaintType.EMPTY || !tile.getPaint().isAlly()) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean hasIncompleteResourcePattern(MapLocation center) throws GameActionException {
        return findIncompleteResourcePatternTile(center, rc.senseNearbyMapInfos(center, 8)) != null;
    }

    private MapLocation findIncompleteResourcePatternTile(MapLocation center, MapInfo[] nearbyTiles) {
        for (MapInfo tile : nearbyTiles) {
            MapLocation loc = tile.getMapLocation();
            if (loc.distanceSquaredTo(center) > 4 || tile.isWall() || tile.hasRuin()) {
                continue;
            }

            PaintType mark = tile.getMark();
            if (mark != PaintType.ALLY_PRIMARY && mark != PaintType.ALLY_SECONDARY) {
                continue;
            }

            PaintType paint = tile.getPaint();
            boolean paintedCorrectly =
                (mark == PaintType.ALLY_PRIMARY && paint == PaintType.ALLY_PRIMARY)
                    || (mark == PaintType.ALLY_SECONDARY && paint == PaintType.ALLY_SECONDARY);
            if (!paintedCorrectly) {
                return loc;
            }
        }
        return null;
    }

    private boolean hasRuinWithin3(MapLocation center) throws GameActionException {
        for (MapInfo info : rc.senseNearbyMapInfos()) {
            if (info.hasRuin() && center.distanceSquaredTo(info.getMapLocation()) <= 3) {
                return true;
            }
        }
        return false;
    }

    private MapLocation getOppositeCorner(MapLocation from) {
        int maxX = rc.getMapWidth() - 1;
        int maxY = rc.getMapHeight() - 1;
        MapLocation[] corners = new MapLocation[] {
            new MapLocation(0, 0),
            new MapLocation(maxX, 0),
            new MapLocation(0, maxY),
            new MapLocation(maxX, maxY)
        };

        MapLocation best = corners[0];
        int bestDist = from.distanceSquaredTo(best);
        for (int i = 1; i < corners.length; i++) {
            int dist = from.distanceSquaredTo(corners[i]);
            if (dist > bestDist) {
                bestDist = dist;
                best = corners[i];
            }
        }
        return best;
    }

    private int countKnownPaintTowers() {
        int count = 0;
        for (int i = 0; i < mapData.towerCount; i++) {
            UnitType type = UnitType.values()[mapData.towerTypes[i]];
            if (isPaintTower(type)) {
                count += 1;
            }
        }
        return count;
    }

    private static final class PatternPlan {
        private final MapLocation location;
        private final UnitType towerType;

        private PatternPlan(MapLocation location, UnitType towerType) {
            this.location = location;
            this.towerType = towerType;
        }
    }

    private static final class Objective {
        private final MapLocation location;
        private final boolean isResource;
        private final UnitType towerType;

        private Objective(MapLocation location, boolean isResource, UnitType towerType) {
            this.location = location;
            this.isResource = isResource;
            this.towerType = towerType;
        }

        private static Objective tower(MapLocation location, UnitType towerType) {
            return new Objective(location, false, towerType);
        }

        private static Objective resource(MapLocation location) {
            return new Objective(location, true, null);
        }
    }
}
