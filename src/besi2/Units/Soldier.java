package besi2.Units;

import battlecode.common.*;
import besi2.Unit;

/**
 * Soldier: unit painter utama + penyerang tower musuh.
 * States: REFILLING → COMBAT → BUILD → EXPLORE
 * Pola turn: senseNearby() → determineState() → execute state → stateInvariantActions()
 */
public class Soldier extends Unit {
    private MapLocation exploreTarget;
    private MapLocation srpCenter = null;

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
            case BUILD_SRP           -> buildSrpState();
            case EXPLORE             -> exploreState();
        }

        stateInvariantActions();
    }

    //  State machine

    private UnitState determineState() throws GameActionException {
        if (prevState != UnitState.REFILLING && shouldRefill()) {
            if (prevState != UnitState.EXPLORE) returnLoc = rc.getLocation();
            return UnitState.REFILLING;
        }
        if (prevState == UnitState.REFILLING && !refillComplete()) return UnitState.REFILLING;

        if (closestEnemyTower != null && rc.getHealth() > ATTACK_HP_THRESHOLD && !isDefenseTower(closestEnemyTower.type)) {
            return UnitState.COMBAT;
        }

        // Fokus ke SRP
        boolean underTowerCeil = (rc.getRoundNum() <= 75 && rc.getNumberTowers() >= 3);

        if (closestEmptyRuin != null && rc.getChips() >= 1000 && !underTowerCeil) {
            return UnitState.BUILD;
        }

        if (srpCenter == null && rc.getRoundNum() > 5 && rc.getPaint() >= 75) {
            if (canBuildSrpHere(rc.getLocation())) {
                srpCenter = rc.getLocation();
                
                if (rc.canMarkResourcePattern(srpCenter)) {
                    rc.markResourcePattern(srpCenter);
                }
                
                return UnitState.BUILD_SRP;
            }
        } else if (srpCenter != null) {
            return UnitState.BUILD_SRP;
        }

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

    /**
     * Bangun tower di ruin terdekat.
     * Alur Baru (Tanpa Mark):
     * 1. Dekati ruin
     * 2. Langsung baca pola dari rc.getTowerPattern()
     * 3. Cat sesuai pola (hemat 25 paint!)
     * 4. completeTowerPattern()
     */
    private void buildState() throws GameActionException {
        if (closestEmptyRuin == null) return;
        MapLocation ruin = closestEmptyRuin;

        // Dekati ruin 
        if (distTo(ruin) > 8) {
            bugNav(ruin);
            return;
        }

        UnitType targetType = UnitType.LEVEL_ONE_PAINT_TOWER;

        for (UnitType type : TOWER_TYPES) {
            if (rc.canCompleteTowerPattern(type, ruin)) {
                rc.completeTowerPattern(type, ruin);
                rc.setTimelineMarker("Tower built!", 0, 255, 0);
                state = UnitState.QUICK_REFILL;
                return;
            }
        }

        if (rc.isActionReady()) {
            paintTowerPattern(ruin, targetType);
        }

        if (rc.isMovementReady()) {
            MapLocation target = findUnpaintedTowerTile(ruin, targetType);
            if (target != null) {
                if (distTo(target) > 2) {
                    fuzzyMove(dirTo(target));
                }
            } else {
                if (distTo(ruin) > 4) fuzzyMove(dirTo(ruin));
            }
        }
    }

    /**
     * State untuk membangun Special Resource Pattern (SRP)
     */
    private void buildSrpState() throws GameActionException {
        if (srpCenter == null) return;

        if (rc.canSenseLocation(srpCenter)) {
            RobotInfo r = rc.senseRobotAtLocation(srpCenter);
            if (r != null && r.type.isTowerType()) { srpCenter = null; return; }
        }

        // Jika tergeser, kembali ke titik tengah SRP
        if (distTo(srpCenter) > 0) {
            bugNav(srpCenter);
            return;
        }

        if (rc.canCompleteResourcePattern(srpCenter)) {
            rc.completeResourcePattern(srpCenter);
            rc.setTimelineMarker("SRP Farm Expanded!", 0, 255, 255);
            
            exploreTarget = srpCenter.translate(3, 1);
            srpCenter = null;
            state = UnitState.QUICK_REFILL;
            return;
        }

        if (rc.isActionReady()) paintSrpPattern(srpCenter);

        if (rc.isMovementReady()) {
            MapLocation target = findUnpaintedSrpTile(srpCenter);
            if (target != null && distTo(target) > 2) {
                fuzzyMove(dirTo(target));
            }
        }
    }
    

    // Mengecat pattern SRP tanpa memanggil markResourcePattern

    private void paintSrpPattern(MapLocation center) throws GameActionException {
        boolean[][] pattern = rc.getResourcePattern(); 
        
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                MapLocation loc = center.translate(dx, dy);
                if (!rc.canSenseLocation(loc)) continue;
                if (!rc.isActionReady()) return;

                MapInfo info = rc.senseMapInfo(loc);
                
                // Baca pola dari API
                boolean wantSecondary = pattern[dx + 2][dy + 2];
                PaintType expectedPaint = wantSecondary ? PaintType.ALLY_SECONDARY : PaintType.ALLY_PRIMARY;

                if (info.getPaint() != expectedPaint) {
                    if (rc.canAttack(loc)) {
                        rc.attack(loc, wantSecondary);
                        return; 
                    }
                }
            }
        }
    }

    // Cari tile 5x5 area SRP yang perlu dicat
    private MapLocation findUnpaintedSrpTile(MapLocation center) throws GameActionException {
        boolean[][] pattern = rc.getResourcePattern();
        MapLocation closest = null;
        int closestDist = Integer.MAX_VALUE;
        
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                MapLocation loc = center.translate(dx, dy);
                if (!rc.canSenseLocation(loc)) continue;
                
                MapInfo info = rc.senseMapInfo(loc);
                boolean wantSecondary = pattern[dx + 2][dy + 2];
                PaintType expectedPaint = wantSecondary ? PaintType.ALLY_SECONDARY : PaintType.ALLY_PRIMARY;
                
                if (info.getPaint() != expectedPaint) {
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

    //Cat tile di area 5x5 ruin sesuai dengan pattern dari API rc.getTowerPattern().
    private void paintTowerPattern(MapLocation ruin, UnitType type) throws GameActionException {
        boolean[][] pattern = rc.getTowerPattern(type);
        
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (dx == 0 && dy == 0) continue; 
                
                MapLocation loc = ruin.translate(dx, dy);
                if (!rc.canSenseLocation(loc)) continue;
                if (!rc.isActionReady()) return;

                MapInfo info = rc.senseMapInfo(loc);
                if (info.hasRuin() && (dx != 0 || dy != 0)) continue;

                boolean wantSecondary = pattern[dx + 2][dy + 2];
                PaintType expectedPaint = wantSecondary ? PaintType.ALLY_SECONDARY : PaintType.ALLY_PRIMARY;

                if (info.getPaint() != expectedPaint) {
                    if (rc.canAttack(loc)) {
                        rc.attack(loc, wantSecondary);
                        return; 
                    }
                }
            }
        }
    }

    // Cari tile di area 5x5 ruin yang belum dicat sesuai pola API.
     
    private MapLocation findUnpaintedTowerTile(MapLocation ruin, UnitType type) throws GameActionException {
        boolean[][] pattern = rc.getTowerPattern(type);
        MapLocation closest = null;
        int closestDist = Integer.MAX_VALUE;
        
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (dx == 0 && dy == 0) continue;
                
                MapLocation loc = ruin.translate(dx, dy);
                if (!rc.canSenseLocation(loc)) continue;
                
                MapInfo info = rc.senseMapInfo(loc);
                if (info.hasRuin() && (dx != 0 || dy != 0)) continue;
                
                boolean wantSecondary = pattern[dx + 2][dy + 2];
                PaintType expectedPaint = wantSecondary ? PaintType.ALLY_SECONDARY : PaintType.ALLY_PRIMARY;
                
                if (info.getPaint() != expectedPaint) {
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

    // Eksplor map:
    private void exploreState() throws GameActionException {
        if (returnLoc != null) {
            if (distTo(returnLoc) <= 4) {
                returnLoc = null;
            } else {
                bugNav(returnLoc);
                return;
            }
        }

        if (distTo(exploreTarget) <= 8) {
            exploreTarget = extendToEdge(rc.getLocation(), randomDirection());
        }

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

    // Matriks untuk jarak marker SRP yang diizinkan
    private static final boolean[][] allowedSrpMarkerLocations = new boolean[][] {
            { false, false, false, false, false, false, false, false, false, false, false },
            { false, false, false, true, false, true, false, true, false, false, false },
            { false, false, true, false, true, false, true, false, true, false, false },
            { false, true, false, true, false, true, false, true, false, true, false },
            { false, false, true, false, false, false, false, false, true, false, false },
            { false, true, false, true, false, false, false, true, false, true, false },
            { false, false, true, false, false, false, false, false, true, false, false },
            { false, true, false, true, false, true, false, true, false, true, false },
            { false, false, true, false, true, false, true, false, true, false, false },
            { false, false, false, true, false, true, false, true, false, false, false },
            { false, false, false, false, false, false, false, false, false, false, false },
    };

    private boolean canBuildSrpHere(MapLocation center) throws GameActionException {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                MapLocation loc = center.translate(dx, dy);
                if (!rc.onTheMap(loc)) return false;
                MapInfo info = rc.senseMapInfo(loc);
                
                if (info.isWall() || info.hasRuin()) return false;
                if (info.getMark() != PaintType.EMPTY) return false; 
                
                RobotInfo r = rc.senseRobotAtLocation(loc);
                if (r != null && r.type.isTowerType()) return false;
            }
        }

        if (rc.getRoundNum() > 50) {
            for (MapLocation ruin : allRuins) {
                if (Math.abs(center.x - ruin.x) <= 4 && Math.abs(center.y - ruin.y) <= 4) {
                    if (rc.senseRobotAtLocation(ruin) == null) return false;
                }
            }
        }

        return true; 
    }
}