package fuschia;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

enum SplasherState {
    REFILL,
    SPLASH,
    EXPLORE
}

public class Splasher extends Unit {
    private SplasherState state = SplasherState.EXPLORE;

    private int targetX;
    private int targetY;
    private int targetTimer;
    private int idleTimer;

    private MapLocation nearestEnemyPaint;
    private AttackChoice bestSplashChoice;
    private MapLocation exploreDestination;
    private boolean splashActionReadyBranch;

    public Splasher(RobotController rc) {
        super(rc);
        MapLocation startTarget = getOppositeCorner(rc.getLocation());
        targetX = startTarget.x;
        targetY = startTarget.y;
        targetTimer = 50;
        idleTimer = 0;
    }

    @Override
    protected void determineState() throws GameActionException {
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots();
        mapData.update(rc, nearbyRobots);

        refreshTargetIfNeeded();

        nearestEnemyPaint = findNearestEnemyPaint(rc.senseNearbyMapInfos());
        bestSplashChoice = null;
        exploreDestination = null;
        splashActionReadyBranch = false;

        if (rc.getPaint() < 50) {
            state = SplasherState.REFILL;
            return;
        }

        if (rc.isActionReady()) {
            state = SplasherState.SPLASH;
            splashActionReadyBranch = true;
            MapInfo[] splashTiles = rc.senseNearbyMapInfos(18);
            bestSplashChoice = findBestSplashAttack(splashTiles);
            if (nearestEnemyPaint == null) {
                nearestEnemyPaint = findNearestEnemyPaint(splashTiles);
            }
            return;
        }

        if (nearestEnemyPaint != null) {
            state = SplasherState.SPLASH;
            return;
        }

        state = SplasherState.EXPLORE;
        if (rc.getRoundNum() < 200) {
            exploreDestination = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
        } else {
            exploreDestination = getTargetLocation();
        }
    }

    @Override
    protected void executeState() throws GameActionException {
        switch (state) {
            case REFILL -> doRefill();
            case SPLASH -> doSplash();
            case EXPLORE -> doExplore();
        }

        targetTimer -= 1;
        // rc.setIndicatorString(state.toString() + );
    }

    private void refreshTargetIfNeeded() {
        if (targetTimer > 0) {
            return;
        }
        setTarget(getOppositeCorner(rc.getLocation()));
    }

    private void setTarget(MapLocation target) {
        if (target == null) {
            return;
        }
        targetX = target.x;
        targetY = target.y;
        targetTimer = 50;
    }

    private MapLocation getTargetLocation() {
        return new MapLocation(targetX, targetY);
    }

    private boolean moveToward(MapLocation target) throws GameActionException {
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
            if (dir != Direction.CENTER && rc.canMove(dir)) {
                rc.move(dir);
                return true;
            }
        }
        return false;
    }

    private void doRefill() throws GameActionException {
        MapLocation nearestTower = mapData.getNearestTower(rc.getLocation());
        if (moveToward(nearestTower)) {
            markProductiveAction();
        }
    }

    private void doSplash() throws GameActionException {
        rc.setIndicatorString("" + splashActionReadyBranch + " " + (bestSplashChoice != null ? bestSplashChoice.score : "null") + " " + nearestEnemyPaint);
        if (splashActionReadyBranch) {
            if (bestSplashChoice != null && bestSplashChoice.score >= 3 && rc.canAttack(bestSplashChoice.location)) {
                rc.attack(bestSplashChoice.location);
                idleTimer = 0;
                markProductiveAction();
                return;
            }

            MapLocation self = rc.getLocation();
            if (isEnemyPaint(self) && rc.canAttack(self)) {
                rc.attack(self);
                idleTimer = 0;
                markProductiveAction();
                return;
            }

            if (nearestEnemyPaint != null && rc.canAttack(nearestEnemyPaint) && isEnemyPaint(nearestEnemyPaint)) {
                rc.attack(nearestEnemyPaint);
                idleTimer = 0;
                markProductiveAction();
                return;
            }

            MapLocation fallback = nearestEnemyPaint;
            if (fallback == null) {
                fallback = rc.getRoundNum() < 200
                    ? new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2)
                    : getTargetLocation();
            }

            if (moveToward(fallback)) {
                markProductiveAction();
            }
            return;
        }

        if (nearestEnemyPaint != null && moveToward(nearestEnemyPaint)) {
            markProductiveAction();
        }
    }

    private void doExplore() throws GameActionException {
        if (moveToward(exploreDestination)) {
            markProductiveAction();
        }
        idleTimer += 1;
        if (idleTimer >= 12) {
            setTarget(getOppositeCorner(rc.getLocation()));
            idleTimer = 0;
        }
    }

    private MapLocation findNearestEnemyPaint(MapInfo[] nearbyTiles) {
        MapLocation me = rc.getLocation();
        MapLocation best = null;
        int bestDistance = Integer.MAX_VALUE;

        for (MapInfo tile : nearbyTiles) {
            if (!tile.getPaint().isEnemy()) {
                continue;
            }
            MapLocation loc = tile.getMapLocation();
            if (loc.equals(me)) {
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

    private AttackChoice findBestSplashAttack(MapInfo[] nearbyTiles) throws GameActionException {
        AttackChoice best = null;
        MapLocation me = rc.getLocation();
        for (MapInfo tile : nearbyTiles) {
            MapLocation attackLoc = tile.getMapLocation();
            if (attackLoc.equals(me)) {
                continue;
            }
            if (!rc.canAttack(attackLoc)) {
                continue;
            }

            int score = countEnemyPaintIn3(attackLoc);
            if (best == null || score > best.score) {
                best = new AttackChoice(attackLoc, score);
            }
        }
        return best;
    }

    private int countEnemyPaintIn3(MapLocation center) throws GameActionException {
        int score = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                MapLocation loc = center.translate(dx, dy);
                if (!rc.canSenseLocation(loc)) {
                    continue;
                }

                PaintType paint = rc.senseMapInfo(loc).getPaint();
                if (paint.isEnemy()) {
                    score += 1;
                }
            }
        }
        return score;
    }

    private boolean isEnemyPaint(MapLocation loc) throws GameActionException {
        return rc.canSenseLocation(loc) && rc.senseMapInfo(loc).getPaint().isEnemy();
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

    private static final class AttackChoice {
        private final MapLocation location;
        private final int score;

        private AttackChoice(MapLocation location, int score) {
            this.location = location;
            this.score = score;
        }
    }
}
