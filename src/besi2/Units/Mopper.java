package besi2.Units;

import battlecode.common.*;
import besi2.Unit;

/**
 * Mopper: bersihkan cat musuh + refill paint allied robots
 * States: REFILLING → COMBAT → MOPPING → EXPLORE
 */
public class Mopper extends Unit {

    private MapLocation enemyPaintTarget = null;

    public Mopper(RobotController rc) throws GameActionException {
        super(rc);
        state = UnitState.EXPLORE;
    }

    @Override
    public void turn() throws GameActionException {
        senseNearby();
        prevState = state;
        state     = determineState();

        switch (state) {
            case REFILLING -> refillingState();
            case COMBAT    -> combatState();
            case MOPPING   -> moppingState();
            case EXPLORE   -> exploreState();
        }

        stateInvariantActions();
    }

    private UnitState determineState() throws GameActionException {
        // Refill
        if (prevState != UnitState.REFILLING && shouldRefill()) return UnitState.REFILLING;
        if (prevState == UnitState.REFILLING && !refillComplete()) return UnitState.REFILLING;

        // Combat
        if (enemies.length > 0 && hasNonTowerEnemy()) return UnitState.COMBAT;

        // Mopping
        enemyPaintTarget = findEnemyPaintTarget();
        if (enemyPaintTarget != null) return UnitState.MOPPING;

        return UnitState.EXPLORE;
    }

    private void refillingState() throws GameActionException { goRefill(); }

    private void combatState() throws GameActionException {
        // Cari target: robot dengan paint paling sedikit
        RobotInfo target = null;
        int minPaint = Integer.MAX_VALUE;
        for (RobotInfo e : enemies) {
            if (!e.type.isTowerType() && e.paintAmount < minPaint) {
                minPaint = e.paintAmount;
                target   = e;
            }
        }
        if (target == null) return;

        // Coba mop swing dulu jika bisa hit >=2 musuh
        if (!tryMopSwing()) {
            // Mop normal ke target
            if (rc.canAttack(target.location)) {
                rc.attack(target.location);
            } else {
                moveIntoRange(target.location, rc.getType().actionRadiusSquared);
                if (rc.canAttack(target.location)) rc.attack(target.location);
            }
        }
    }

    private void moppingState() throws GameActionException {
        if (enemyPaintTarget == null) return;
        rc.setIndicatorDot(enemyPaintTarget, 255, 255, 0);

        if (rc.isActionReady()) {
            moveIntoRange(enemyPaintTarget, rc.getType().actionRadiusSquared);
            tryAttack(enemyPaintTarget);
        } else {
            // Action belum siap, gerak dulu ke target
            if (distTo(enemyPaintTarget) > 2) bugNav(enemyPaintTarget);
        }
    }

    private void exploreState() throws GameActionException {
        fuzzyMove(randomDirection());
    }

    //  State invariant

    private void stateInvariantActions() throws GameActionException {
        // Selalu transfer paint ke ally nonmopper yang membutuhkan
        refillAllies();

        // Complete tower pattern jika bisa
        if (closestEmptyRuin != null) {
            for (UnitType type : TOWER_TYPES) {
                if (rc.canCompleteTowerPattern(type, closestEmptyRuin)) {
                    rc.completeTowerPattern(type, closestEmptyRuin);
                }
            }
        }
    }

    //  Helpers

    private boolean hasNonTowerEnemy() {
        for (RobotInfo e : enemies) {
            if (!e.type.isTowerType()) return true;
        }
        return false;
    }

    // Cari cat musuh terdekat, prioritaskan yang dekat ruin
    private MapLocation findEnemyPaintTarget() {
        MapLocation best = null;
        boolean bestNearRuin = false;
        int bestDist = Integer.MAX_VALUE;

        for (MapInfo tile : nearbyTiles) {
            if (!tile.getPaint().isEnemy()) continue;
            MapLocation loc = tile.getMapLocation();

            // Cek apakah dekat ruin
            boolean nearRuin = false;
            for (MapLocation ruin : allRuins) {
                if (loc.distanceSquaredTo(ruin) <= 8) {
                    nearRuin = true;
                    break;
                }
            }

            int dist = distTo(loc);
            if (nearRuin && !bestNearRuin) {
                best = loc;
                bestNearRuin = true;
                bestDist = dist;
            } else if (nearRuin == bestNearRuin && dist < bestDist) {
                best = loc;
                bestDist = dist;
            }
        }
        return best;
    }

    /**
     * Transfer paint ke ally nonmopper yang kekurangan.
     * Sisakan minimum 30 paint untuk diri sendiri.
     */
    private void refillAllies() throws GameActionException {
        for (RobotInfo ally : allies) {
            if (ally.type.isTowerType() || ally.type == UnitType.MOPPER) continue;
            int canGive = rc.getPaint() - 30;
            int allyNeeds = ally.type.paintCapacity - ally.paintAmount;
            int amount = Math.min(canGive, allyNeeds);
            if (amount > 0 && rc.canTransferPaint(ally.location, amount)) {
                rc.transferPaint(ally.location, amount);
            }
        }
    }

    /**
     * Coba mop swing ke arah yang hit paling banyak musuh.
     * Return true jika swing berhasil.
     */
    private boolean tryMopSwing() throws GameActionException {
        if (!rc.isActionReady()) return false;
        int[] hits = new int[4]; // N S E W
        Direction[] dirs = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

        for (RobotInfo e : enemies) {
            if (e.type.isTowerType()) continue;
            int dx = e.location.x - rc.getLocation().x;
            int dy = e.location.y - rc.getLocation().y;
            if (Math.abs(dx) <= 1 && dy > 0 && dy <= 2) hits[0]++;
            if (Math.abs(dx) <= 1 && dy < 0 && dy >= -2) hits[1]++;
            if (Math.abs(dy) <= 1 && dx > 0 && dx <= 2) hits[2]++;
            if (Math.abs(dy) <= 1 && dx < 0 && dx >= -2) hits[3]++;
        }

        int maxHits = 0;
        int bestDir = -1;
        for (int i = 0; i < 4; i++) {
            if (hits[i] > maxHits) { maxHits = hits[i]; bestDir = i; }
        }

        if (maxHits >= 2 && bestDir >= 0 && rc.canMopSwing(dirs[bestDir])) {
            rc.mopSwing(dirs[bestDir]);
            return true;
        }
        return false;
    }
}
