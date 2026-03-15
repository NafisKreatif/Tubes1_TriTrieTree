package besi2.Units;

import besi2.Unit;
import battlecode.common.*;

/**
 * Soldier: unit painter utama + penyerang tower musuh.
 * States: REFILLING → COMBAT → BUILD → EXPLORE
 * Pola turn: senseNearby() → determineState() → execute state → stateInvariantActions()
 */
public class Soldier extends Unit {
    private MapLocation exploreTarget;

    private static final int ATTACK_HP_THRESHOLD = 40;

    public Soldier(RobotController rc) throws GameActionException {
        super(rc);
        state = UnitState.EXPLORE;

        // Arah eksplorasi awal: ikuti arah dari spawn tower ke posisi kita menjauh
        Direction dir = (spawnTowerLoc != null)
            ? spawnTowerLoc.directionTo(rc.getLocation())
            : randomDirection();
        exploreTarget = extendToEdge(rc.getLocation(), dir);
    }

    //  Turn

    @Override
    public void turn() throws GameActionException {
        senseNearby();

        prevState = state;
        state     = determineState();

        switch (state) {
            case REFILLING           -> refillingState();
            case COMBAT              -> combatState();
            case BUILD               -> buildState();
            case EXPLORE             -> exploreState();
        }

        stateInvariantActions();
    }

    //  State machine

    private UnitState determineState() throws GameActionException {
        // Refilling
        if (prevState != UnitState.REFILLING && shouldRefill()) {
            if (prevState != UnitState.EXPLORE) returnLoc = rc.getLocation();
            return UnitState.REFILLING;
        }
        if (prevState == UnitState.REFILLING && !refillComplete()) {
            return UnitState.REFILLING;
        }

        // Combat: ada enemy tower yang reachable dan HP masih oke
        if (closestEnemyTower != null
                && rc.getHealth() > ATTACK_HP_THRESHOLD
                && !isDefenseTower(closestEnemyTower.type)) {
            return UnitState.COMBAT;
        }

        // Build
        if (closestEmptyRuin != null && rc.getChips() >= 700) {
            return UnitState.BUILD;
        }

        // Default: explore
        return UnitState.EXPLORE;
    }

    //  State implementations

    // Kembali ke paint tower untuk isi ulang paint
    private void refillingState() throws GameActionException {
        if (goRefill()) {
            returnLoc = null;
        }
    }

    /**
     * Serang tower musuh terdekat dengan kiting mechanic.
     *
     * Kiting: gunakan sinkronisasi round genap/ganjil.
     *   Round genap → masuk range + attack
     *   Round ganjil → keluar range (hindari serangan balik)
     */
    private void combatState() throws GameActionException {
        if (closestEnemyTower == null) return;
        MapLocation tower = closestEnemyTower.location;
        int towerRange    = closestEnemyTower.type.actionRadiusSquared;
        int myRange       = rc.getType().actionRadiusSquared;

        boolean evenRound = (rc.getRoundNum() % 2 == 0);

        if (evenRound) {
            // Attack turn
            if (distTo(tower) > myRange) {
                safeFuzzyMove(dirTo(tower));
            }
            tryAttack(tower);
        } else {
            // Move turn
            if (distTo(tower) <= towerRange) {
                safeFuzzyMove(dirTo(tower).opposite());
            }
        }
    }

    // Bangun tower di ruin terdekat
    private void buildState() throws GameActionException {
        if (closestEmptyRuin == null) return;
        MapLocation ruin = closestEmptyRuin;

        if (distTo(ruin) > 8) {
            bugNav(ruin);
            return;
        }
        
        UnitType targetType = UnitType.LEVEL_ONE_PAINT_TOWER;
        boolean[][] blueprint = blueprintPaint; 

        if (rc.canCompleteTowerPattern(targetType, ruin)) {
            rc.completeTowerPattern(targetType, ruin);
            rc.setIndicatorString("Tower built!");
            state = UnitState.QUICK_REFILL;
            return;
        }

        if (rc.isActionReady()) {
            paintFromBlueprint(ruin, blueprint);
        }

        if (rc.isMovementReady()) {
            MapLocation target = findUnpaintedBlueprintTile(ruin, blueprint);
            if (target != null) {
                if (distTo(target) > 2) {
                    fuzzyMove(dirTo(target));
                }
            } else {
                if (distTo(ruin) > 4) fuzzyMove(dirTo(ruin));
            }
        }
    }

    // Mengecat berdasarkan array 2D blueprint dari memori
    private void paintFromBlueprint(MapLocation ruin, boolean[][] blueprint) throws GameActionException {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (dx == 0 && dy == 0) continue;

                MapLocation loc = ruin.translate(dx, dy);
                if (!rc.canSenseLocation(loc)) continue;
                if (!rc.isActionReady()) return;

                MapInfo info = rc.senseMapInfo(loc);
                if (info.hasRuin()) continue;

                boolean wantSecondary = blueprint[dx + 2][dy + 2];
                PaintType requiredPaint = wantSecondary ? PaintType.ALLY_SECONDARY : PaintType.ALLY_PRIMARY;

                if (info.getPaint() != requiredPaint && rc.canAttack(loc)) {
                    rc.attack(loc, wantSecondary); 
                    return; 
                }
            }
        }
    }

    // Cari tile yang belum sesuai dengan blueprint
    private MapLocation findUnpaintedBlueprintTile(MapLocation ruin, boolean[][] blueprint) throws GameActionException {
        MapLocation closest = null;
        int closestDist = Integer.MAX_VALUE;
        
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (dx == 0 && dy == 0) continue;
                
                MapLocation loc = ruin.translate(dx, dy);
                if (!rc.canSenseLocation(loc)) continue;
                
                MapInfo info = rc.senseMapInfo(loc);
                if (info.hasRuin()) continue;
                
                boolean wantSecondary = blueprint[dx + 2][dy + 2];
                PaintType requiredPaint = wantSecondary ? PaintType.ALLY_SECONDARY : PaintType.ALLY_PRIMARY;

                if (info.getPaint() != requiredPaint) {
                    int d = distTo(loc);
                    if (d < closestDist) { 
                        closestDist = d; 
                        closest = loc; 
                    }
                }
            }
        }
        return closest;
    }

    // Eksplor map: gerak ke exploreTarget, cat sepanjang jalan 
    private void exploreState() throws GameActionException {
        // Jika ada returnLoc (habis refill), balik ke sana dulu
        if (returnLoc != null) {
            if (distTo(returnLoc) <= 4) {
                returnLoc = null;
            } else {
                bugNav(returnLoc);
                return;
            }
        }

        // Ganti target jika sudah sampai
        if (distTo(exploreTarget) <= 8) {
            exploreTarget = extendToEdge(rc.getLocation(), randomDirection());
        }

        // Jika ada enemy paint terdekat, dekati untuk dihandle Splasher/Mopper
        MapLocation enemyCentroid = getEnemyPaintCentroid();
        if (enemyCentroid != null) {
            // Jangan berdiri di atas cat musuh
            if (rc.senseMapInfo(rc.getLocation()).getPaint().isEnemy()) {
                safeFuzzyMove(dirTo(enemyCentroid).opposite());
            } else {
                safeFuzzyMove(dirTo(enemyCentroid));
            }
        } else {
            bugNav(exploreTarget);
        }
    }

    //  State invariant

    /**
     * Aksi yang selalu dilakukan:
     *   1. Cat tile di bawah diri sendiri
     *   2. Jika tidak sedang BUILD: cat area sekitar posisi diri dengan pola umum
     */
    private void stateInvariantActions() throws GameActionException {
        if (state == UnitState.REFILLING || state == UnitState.CONNECTING_TO_TOWER) return;
        if (rc.getPaint() < 10) return;

        // Prioritas 1: cat di bawah diri sendiri
        if (!tryPaintUnderSelf() && rc.isActionReady()) {

            // Prioritas 2: cat area sekitar diri sendiri
            boolean nearRuin = (closestEmptyRuin != null && distTo(closestEmptyRuin) <= 8);
            if (!nearRuin) {
                paintSpiral(rc.getLocation(), 2);
            }
        }
    }
}