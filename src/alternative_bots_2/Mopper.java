package alternative_bots_2;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public class Mopper extends Unit {

    enum MopperState {
        REFILL,
        RUIN_DEFENSE,
        TOWER_DEFENSE,
        MOP_PATROL,
        EXPLORE
    }

    private MopperState state = MopperState.EXPLORE;

    private RobotInfo[] nearbyRobots = new RobotInfo[0];
    private MapInfo[] nearbyTiles = new MapInfo[0];
    private RobotInfo lowestHealthEnemyInRange;
    private Direction bestSwingDirection;
    private MapLocation ruinAdjacentEnemyPaint;
    private MapLocation nearestEnemyPaint;

    public Mopper(RobotController rc) {
        super(rc);
    }

    @Override
    protected void determineState() throws GameActionException {
        nearbyRobots = rc.senseNearbyRobots();
        mapData.update(rc, nearbyRobots);
        nearbyTiles = rc.senseNearbyMapInfos(8);
        updateVisibleRuinTaint();

        refreshTargetIfNeeded();

        lowestHealthEnemyInRange = findLowestHealthEnemyUnitInAttackRange();
        bestSwingDirection = findBestSwingDirection(2);
        ruinAdjacentEnemyPaint = findRuinAdjacentEnemyPaint();
        nearestEnemyPaint = findNearestEnemyPaint();

        if (rc.getPaint() < 40) {
            state = MopperState.REFILL;
            return;
        }

        if (lowestHealthEnemyInRange != null) {
            state = MopperState.TOWER_DEFENSE;
            return;
        }

        if (ruinAdjacentEnemyPaint != null) {
            state = MopperState.RUIN_DEFENSE;
            return;
        }

        if (nearestEnemyPaint != null) {
            state = MopperState.MOP_PATROL;
            return;
        }

        state = MopperState.EXPLORE;
    }

    @Override
    protected void executeState() throws GameActionException {
        rc.setIndicatorString(state.toString());
        switch (state) {
            case REFILL -> doRefill();
            case RUIN_DEFENSE -> doRuinDefense();
            case TOWER_DEFENSE -> doTowerDefense();
            case MOP_PATROL -> doMopPatrol();
            case EXPLORE -> doExplore();
        }

        targetTimer -= 1;
    }

    private void doRefill() throws GameActionException {
        MapLocation tower = mapData.getNearestTower(rc.getLocation());
        if (moveToward(tower)) {
            markProductiveAction();
        }
    }

    private void doRuinDefense() throws GameActionException {
        idleTimer = 0;
        if (ruinAdjacentEnemyPaint == null) {
            return;
        }

        boolean didMop = tryMopTile(ruinAdjacentEnemyPaint);
        boolean moved = false;
        if (!didMop) {
            moved = moveToward(ruinAdjacentEnemyPaint);
            didMop = tryMopTile(ruinAdjacentEnemyPaint);
        }

        if (didMop || moved) {
            markProductiveAction();
        }
    }

    private void doTowerDefense() throws GameActionException {
        idleTimer = 0;

        if (bestSwingDirection != null && rc.canMopSwing(bestSwingDirection)) {
            rc.mopSwing(bestSwingDirection);
            markProductiveAction();
            return;
        }

        if (lowestHealthEnemyInRange != null && rc.canAttack(lowestHealthEnemyInRange.location)) {
            rc.attack(lowestHealthEnemyInRange.location);
            markProductiveAction();
        }
    }

    private void doMopPatrol() throws GameActionException {
        idleTimer = 0;
        if (nearestEnemyPaint == null) {
            return;
        }

        boolean didMop = tryMopTile(nearestEnemyPaint);
        boolean moved = false;
        if (!didMop) {
            moved = moveToward(nearestEnemyPaint);
            didMop = tryMopTile(nearestEnemyPaint);
        }

        if (didMop || moved) {
            markProductiveAction();
        }
    }

    private void doExplore() throws GameActionException {
        if (moveToward(getTargetLocation())) {
            markProductiveAction();
        }
        idleTimer += 1;
        if (idleTimer >= 12) {
            setTarget(getOppositeCorner(rc.getLocation()));
            idleTimer = 0;
        }
    }

    private RobotInfo findLowestHealthEnemyUnitInAttackRange() {
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

    private Direction findBestSwingDirection(int minimumHits) throws GameActionException {
        Direction[] dirs = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        Direction bestDir = null;
        int bestHits = 0;

        for (Direction dir : dirs) {
            if (!rc.canMopSwing(dir)) {
                continue;
            }
            int hits = countSwingHits(dir);
            if (hits >= minimumHits && hits > bestHits) {
                bestHits = hits;
                bestDir = dir;
            }
        }

        return bestDir;
    }

    private int countSwingHits(Direction dir) throws GameActionException {
        int hits = 0;
        MapLocation origin = rc.getLocation();

        if (dir == Direction.EAST || dir == Direction.WEST) {
            int sign = dir == Direction.EAST ? 1 : -1;
            for (int i = 1; i <= 2; i++) {
                for (int j = -1; j <= 1; j++) {
                    MapLocation check = origin.translate(sign * i, j);
                    if (!rc.canSenseLocation(check) || !rc.canSenseRobotAtLocation(check)) {
                        continue;
                    }
                    RobotInfo robot = rc.senseRobotAtLocation(check);
                    if (robot != null && robot.team == rc.getTeam().opponent() && !isTowerType(robot.type)) {
                        hits += 1;
                    }
                }
            }
            return hits;
        }

        int sign = dir == Direction.NORTH ? 1 : -1;
        for (int i = 1; i <= 2; i++) {
            for (int j = -1; j <= 1; j++) {
                MapLocation check = origin.translate(j, sign * i);
                if (!rc.canSenseLocation(check) || !rc.canSenseRobotAtLocation(check)) {
                    continue;
                }
                RobotInfo robot = rc.senseRobotAtLocation(check);
                if (robot != null && robot.team == rc.getTeam().opponent() && !isTowerType(robot.type)) {
                    hits += 1;
                }
            }
        }
        return hits;
    }

    private MapLocation findRuinAdjacentEnemyPaint() {
        MapLocation me = rc.getLocation();
        MapLocation best = null;
        int bestDistance = Integer.MAX_VALUE;

        for (MapInfo tile : nearbyTiles) {
            MapLocation loc = tile.getMapLocation();
            if (!tile.getPaint().isEnemy() || !isAdjacentToKnownRuin(loc)) {
                continue;
            }

            int distance = me.distanceSquaredTo(loc);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = loc;
            }
        }

        return best;
    }

    private void updateVisibleRuinTaint() throws GameActionException {
        for (MapInfo tile : nearbyTiles) {
            if (!tile.hasRuin()) {
                continue;
            }

            MapLocation ruin = tile.getMapLocation();
            if (rc.canSenseRobotAtLocation(ruin)) {
                RobotInfo robot = rc.senseRobotAtLocation(ruin);
                if (robot != null && robot.type.isTowerType()) {
                    mapData.markRuin(ruin, robot.team == rc.getTeam() ? MapData.RUIN_ALLY_OWNED : MapData.RUIN_ENEMY_OWNED);
                    continue;
                }
            }

            mapData.markRuin(ruin, hasEnemyPaintNearRuin(ruin) ? MapData.RUIN_TAINTED : MapData.RUIN_UNCLAIMED);
        }
    }

    private boolean hasEnemyPaintNearRuin(MapLocation ruin) throws GameActionException {
        for (MapInfo tile : rc.senseNearbyMapInfos(ruin, 8)) {
            if (!tile.getMapLocation().equals(ruin) && tile.getPaint().isEnemy()) {
                return true;
            }
        }
        return false;
    }

    private MapLocation findNearestEnemyPaint() {
        MapLocation me = rc.getLocation();
        
        MapLocation markedTarget = findNearestEnemyMarkedTile();
        if (markedTarget != null) {
            return markedTarget;
        }
        
        MapLocation best = null;
        int bestDistance = Integer.MAX_VALUE;

        for (MapInfo tile : nearbyTiles) {
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

    private MapLocation findNearestEnemyMarkedTile() {
        MapLocation me = rc.getLocation();
        MapLocation best = null;
        int bestDistance = Integer.MAX_VALUE;

        for (MapInfo tile : nearbyTiles) {
            if (!tile.getMark().isEnemy()) {
                continue;
            }
            
            if (tile.hasRuin()) {
                continue;
            }

            int distance = me.distanceSquaredTo(tile.getMapLocation());
            if (distance > 30) {
                continue;
            }
            
            if (distance < bestDistance) {
                bestDistance = distance;
                best = tile.getMapLocation();
            }
        }

        return best;
    }

    private boolean isAdjacentToKnownRuin(MapLocation loc) {
        for (int i = 0; i < mapData.ruinCount; i++) {
            int distance = loc.distanceSquaredTo(mapData.knownRuins[i]);
            if (distance > 0 && distance <= 2) {
                return true;
            }
        }
        return false;
    }

    private boolean tryMopTile(MapLocation loc) throws GameActionException {
        if (loc == null || !rc.canAttack(loc)) {
            return false;
        }
        rc.attack(loc);
        return true;
    }

}

