package alternative_bots_1.Units;

import alternative_bots_1.Unit;
import battlecode.common.*;

/**
 * Splasher: AoE painter
 * States: REFILLING → EXPLORE
 */
public class Splasher extends Unit {

    private MapLocation exploreTarget;

    public Splasher(RobotController rc) throws GameActionException {
        super(rc);
        state = UnitState.EXPLORE;

        Direction dir = (spawnTowerLoc != null)
            ? spawnTowerLoc.directionTo(rc.getLocation())
            : randomDirection();
        exploreTarget = extendToEdge(rc.getLocation(), dir);
    }

    @Override
    public void turn() throws GameActionException {
        senseNearby();
        prevState = state;
        state     = determineState();

        switch (state) {
            case REFILLING -> refillingState();
            case EXPLORE   -> exploreState();
        }
        stateInvariantActions();
    }

    private void stateInvariantActions() throws GameActionException {
        // Selalu cat tile di bawah diri sendiri
        tryPaintUnderSelf();
    }

    @Override
    public boolean tryPaintUnderSelf() throws GameActionException {
        return tryPaintTile(rc.getLocation(), true);
    }

    private UnitState determineState() {
        if (prevState != UnitState.REFILLING && rc.getPaint() < 75) return UnitState.REFILLING;
        if (prevState == UnitState.REFILLING && rc.getPaint() < rc.getType().paintCapacity * 3 / 4) return UnitState.REFILLING;
        return UnitState.EXPLORE;
    }

    private void refillingState() throws GameActionException { goRefill(); }

    /**
     * Explore + splash.
     * Scoring: untuk setiap tile cat musuh, tambahkan +1 ke semua 9 tile adjacentnya.
     * Enemy tower di area → +5. Pilih tile dengan skor tertinggi.
     */
    private void exploreState() throws GameActionException {
        // Hitung skor tiap tile
        MapLocation bestSplash = null;
        if (rc.isActionReady()) {
            int[][] score = new int[11][11];
            int totalEnemyTiles = 0;

            for (MapInfo tile : nearbyTiles) {
                if (Clock.getBytecodesLeft() < 1500) break; 
                if (tile.getPaint().isEnemy()) {
                    totalEnemyTiles++;
                    int ox = tile.getMapLocation().x - rc.getLocation().x + 5;
                    int oy = tile.getMapLocation().y - rc.getLocation().y + 5;
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            int nx = ox + dx, ny = oy + dy;
                            if (nx >= 0 && nx < 11 && ny >= 0 && ny < 11) score[nx][ny]++;
                        }
                    }
                }
            }
            for (RobotInfo e : enemies) {
                if (!e.type.isTowerType()) continue;
                int ox = e.location.x - rc.getLocation().x + 5;
                int oy = e.location.y - rc.getLocation().y + 5;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        int nx = ox + dx, ny = oy + dy;
                        if (nx >= 0 && nx < 11 && ny >= 0 && ny < 11) score[nx][ny] += 5;
                    }
                }
            }

            // Cari tile terbaik dalam action radius atau 1 langkah
            int maxScore = 0;
            for (MapLocation loc : rc.getAllLocationsWithinRadiusSquared(rc.getLocation(), 10)) {
                int ox = loc.x - rc.getLocation().x + 5;
                int oy = loc.y - rc.getLocation().y + 5;
                if (ox < 0 || ox >= 11 || oy < 0 || oy >= 11) continue;
                
                if (score[ox][oy] > maxScore && isSafeToSplash(loc)) { 
                    maxScore = score[ox][oy]; 
                    bestSplash = loc; 
                }
            }

            // Splash jika skor cukup atau semua enemy tiles bisa dicover
            boolean worthSplashing = maxScore >= 4 || (maxScore > 0 && maxScore == totalEnemyTiles);
            if (bestSplash != null && worthSplashing) {
                if (distTo(bestSplash) > 4) {
                    safeFuzzyMove(dirTo(bestSplash));
                }
                if (rc.canAttack(bestSplash)) {
                    rc.attack(bestSplash);
                }
            }
        }

        // Hindari range tower musuh
        if (closestEnemyTower != null
                && distTo(closestEnemyTower.location) <= closestEnemyTower.type.actionRadiusSquared) {
            safeFuzzyMove(dirTo(closestEnemyTower.location).opposite());
            return;
        }

        // Gerak ke centroid cat musuh jika ada
        MapLocation enemyCentroid = getEnemyPaintCentroid();
        if (enemyCentroid != null && dirTo(enemyCentroid) != Direction.CENTER) {
            if (!rc.senseMapInfo(rc.getLocation()).getPaint().isEnemy()) {
                safeFuzzyMove(dirTo(enemyCentroid));
            } else {
                safeFuzzyMove(dirTo(enemyCentroid).opposite());
            }
            return;
        }

        // Explore normal
        if (distTo(exploreTarget) <= 8) {
            exploreTarget = extendToEdge(rc.getLocation(), randomDirection());
        }
        bugNav(exploreTarget);
    }
}
