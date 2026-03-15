package besi;

import battlecode.common.*;

public abstract class Unit extends Robot {

    protected enum UnitState {
        EXPLORE,            // eksplorasi dan cat map
        REFILLING,          // kembali ke tower untuk isi paint
        QUICK_REFILL,       // isi paint setelah selesai bangun tower
        COMBAT,             // serang tower musuh / sweep musuh
        BUILD,              // selesaikan pola ruin lalu bangun tower
        BUILD_SRP,          // cat Special Resource Pattern
        MOPPING,            // (Mopper) bersihkan cat musuh
        CONNECTING_TO_TOWER // (awal spawn) connect ke spawn tower via paint
    }

    protected UnitState state = UnitState.EXPLORE;
    protected UnitState prevState = null;

    //  Info lingkungan
    protected MapInfo[] nearbyTiles = new MapInfo[0];
    protected RobotInfo[] allies = new RobotInfo[0];
    protected RobotInfo[] enemies = new RobotInfo[0];
    protected MapLocation[] allRuins = new MapLocation[0];

    // Tower musuh terdekat yang terlihat
    protected RobotInfo closestEnemyTower = null;

    // Ruin terdekat yang belum ada towernya
    protected MapLocation closestEmptyRuin = null;

    // Lokasi untuk kembali setelah refill 
    protected MapLocation returnLoc = null;

    // Lokasi spawn tower
    protected final MapLocation spawnTowerLoc;

    // Sudah pernah connect ke spawn tower?
    protected boolean connected = false;


    private static final int PAINT_REFILL_THRESHOLD = 40;
    private static final int PAINT_REFILL_TARGET_PCT = 75;

    private MapLocation bugNavTarget = null;
    private Direction bugWallDir = null;
    private boolean bugHuggingWall = false;
    private MapLocation bugStartLoc = null;
    private int bugStartDist = 0;

    //  Constructor
    public Unit(RobotController rc) throws GameActionException {
        super(rc);
        // Cari spawn tower
        MapLocation found = null;
        int minDist = Integer.MAX_VALUE;
        for (RobotInfo r : rc.senseNearbyRobots(GameConstants.BUILD_ROBOT_RADIUS_SQUARED, rc.getTeam())) {
            if (r.type.isTowerType()) {
                int d = distTo(r.location);
                if (d < minDist) {
                    minDist = d;
                    found = r.location;
                }
            }
        }
        spawnTowerLoc = found;
    }

    /**
     * Scan semua info lingkungan dan update field.
     * Urutan: mapInfos → allies → enemies → ruins.
     * Dipanggil sebelum determineState().
     */
    protected void senseNearby() throws GameActionException {
        nearbyTiles = rc.senseNearbyMapInfos();
        allies = rc.senseNearbyRobots(-1, rc.getTeam());
        enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        allRuins = rc.senseNearbyRuins(-1);

        // Tower musuh terdekat
        closestEnemyTower = null;
        int closestDist = Integer.MAX_VALUE;
        for (RobotInfo e : enemies) {
            if (e.type.isTowerType()) {
                int d = distTo(e.location);
                if (d < closestDist) {
                    closestDist = d;
                    closestEnemyTower = e;
                }
            }
        }

        // Ruin terdekat tanpa tower
        closestEmptyRuin = null;
        closestDist = Integer.MAX_VALUE;
        for (MapLocation ruin : allRuins) {
            if (rc.senseRobotAtLocation(ruin) == null) {
                int d = distTo(ruin);
                if (d < closestDist) {
                    closestDist = d;
                    closestEmptyRuin = ruin;
                }
            }
        }

        // Cek koneksi ke spawn tower
        if (!connected && spawnTowerLoc != null
                && rc.canSenseRobotAtLocation(spawnTowerLoc)
                && rc.canSendMessage(spawnTowerLoc)) {
            connected = true;
        }
    }

    //  PAINTING
    //  Kasus 1: tryPaintTile() → coverage biasa saat explore
    //  Kasus 2: paintTowerPattern() → ada di Soldier.java
    //  Kasus 3: tryPaintSRPTile() → SRP pattern, baca getMark() dari engine
 
    /**
     * KASUS 1
     * Aturan:
     *   - Hanya cat tile KOSONG
     *   - Selalu pakai PRIMARY color
     *   - Skip tile musuh, untuk Soldier
     *
     * Dipakai oleh: paintSpiral(), tryPaintUnderSelf()
     */
    public boolean tryPaintTile(MapLocation loc) throws GameActionException {
        return tryPaintTile(loc, false);
    }
 
    /**
     * Untuk Splasher
     * Splasher bisa menimpa cat musuh di tile di bawahnya
     */
    public boolean tryPaintTile(MapLocation loc, boolean overwriteEnemy) throws GameActionException {
        if (!rc.canAttack(loc)) return false;
        MapInfo info = rc.senseMapInfo(loc);
        if (info.hasRuin()) return false;
 
        if (info.getPaint().isEnemy() && !overwriteEnemy) return false;
 
        if (info.getPaint() == PaintType.EMPTY ||
                (overwriteEnemy && info.getPaint().isEnemy())) {
            rc.attack(loc, false); // false = primary color
            return true;
        }
        return false;
    }
 
    /**
     * KASUS 3
     * Aturan:
     *   1. Panggil rc.markResourcePattern(center)
     *   2. Baca getMark() tiap tile → cat dengan warna sesuai marker
     */
    public boolean tryPaintSRPTile(MapLocation center) throws GameActionException {
        if (!rc.isActionReady()) return false;
 
        // Mark
        if (rc.canMarkResourcePattern(center)) {
            rc.markResourcePattern(center);
        }
 
        // Scan, cat sesuai marker
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (!rc.isActionReady()) return false;
 
                MapLocation loc = center.translate(dx, dy);
                if (!rc.canSenseLocation(loc)) continue;
 
                MapInfo info = rc.senseMapInfo(loc);
                if (info.hasRuin()) continue;
 
                // Baca marker
                PaintType marker = info.getMark();
                if (marker == PaintType.EMPTY) continue;
 
                // Cat jika kosong atau warna salah
                boolean wantSecondary = (marker == PaintType.ALLY_SECONDARY);
                if (info.getPaint() != marker && !info.getPaint().isEnemy()
                        && rc.canAttack(loc)) {
                    rc.attack(loc, wantSecondary);
                    return true; 
                }
            }
        }
        return false;
    }
 
    /**
     * Cari tile SRP yang belum dicat sesuai pola di area 5x5 sekitar center.
     * Dipakai untuk tahu ke mana harus bergerak saat BUILD_SRP.
     */
    public MapLocation findUnpaintedSRPTile(MapLocation center) throws GameActionException {
        MapLocation closest = null;
        int closestDist = Integer.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                MapLocation loc = center.translate(dx, dy);
                if (!rc.canSenseLocation(loc)) continue;
                MapInfo info = rc.senseMapInfo(loc);
                if (info.hasRuin()) continue;
                PaintType marker = info.getMark();
                if (marker == PaintType.EMPTY) continue;
                if (info.getPaint() != marker && !info.getPaint().isEnemy()) {
                    int d = distTo(loc);
                    if (d < closestDist) { closestDist = d; closest = loc; }
                }
            }
        }
        return closest;
    }
 
    // Cat tile di bawah diri sendiri.
    public boolean tryPaintUnderSelf() throws GameActionException {
        return tryPaintTile(rc.getLocation());
    }
 
    // Cat tile dalam spiral keluar dari center
    public void paintSpiral(MapLocation center, int radius) throws GameActionException {
        if (!rc.isActionReady()) return;
        for (MapLocation loc : spiralLocs(center, radius)) {
            if (tryPaintTile(loc)) return;
        }
    }

    /**
     * Kembalikan lokasi dalam urutan spiral keluar dari center, radius 2.
     * Urutan: tengah → ring 1 → ring 2.
     */
    public MapLocation[] spiralLocs(MapLocation c, int r) {
        if (r == 1) {
            return new MapLocation[]{
                c,
                c.translate(0,1), c.translate(1,0), c.translate(0,-1), c.translate(-1,0),
                c.translate(1,1), c.translate(1,-1), c.translate(-1,-1), c.translate(-1,1),
            };
        }
        // radius 2
        return new MapLocation[]{
            c,
            c.translate(0,1), c.translate(1,0), c.translate(0,-1), c.translate(-1,0),
            c.translate(1,1), c.translate(1,-1), c.translate(-1,-1), c.translate(-1,1),
            c.translate(0,2), c.translate(2,0), c.translate(0,-2), c.translate(-2,0),
            c.translate(1,2), c.translate(2,1), c.translate(2,-1), c.translate(1,-2),
            c.translate(-1,-2), c.translate(-2,-1), c.translate(-2,1), c.translate(-1,2),
            c.translate(2,2), c.translate(2,-2), c.translate(-2,-2), c.translate(-2,2),
        };
    }

    //  Deteksi situasi

    // Apakah paint cukup rendah untuk perlu refill?
    protected boolean shouldRefill() {
        return rc.getPaint() < PAINT_REFILL_THRESHOLD;
    }

    // Apakah paint sudah cukup penuh setelah refill?
    protected boolean refillComplete() {
        return rc.getPaint() >= rc.getType().paintCapacity * PAINT_REFILL_TARGET_PCT / 100;
    }

    // Cari lokasi centroid cat musuh di sekitar robot.
    protected MapLocation getEnemyPaintCentroid() {
        int sx = 0, sy = 0, count = 0;
        for (MapInfo info : nearbyTiles) {
            if (info.getPaint().isEnemy()) {
                sx += info.getMapLocation().x;
                sy += info.getMapLocation().y;
                count++;
            }
        }
        if (count < 2) return null;
        return new MapLocation(sx/count, sy/count);
    }

    // Apakah lokasi loc terlalu dekat dengan tower musuh?
    protected boolean inEnemyTowerRange(MapLocation loc) {
        for (RobotInfo e : enemies) {
            if (e.type.isTowerType()
                    && loc.distanceSquaredTo(e.location) <= e.type.actionRadiusSquared) {
                return true;
            }
        }
        return false;
    }

    //  Refilling helper

    /**
     * Pergi ke paint tower terdekat yang diketahui, lalu withdraw paint.
     * Return true jika sudah di dekat tower dan berhasil refill.
     */
    protected boolean goRefill() throws GameActionException {
        // Cari paint tower terdekat dari allies yang terlihat
        MapLocation nearestPaintTower = null;
        int minDist = Integer.MAX_VALUE;
        for (RobotInfo ally : allies) {
            if (isPaintTower(ally.type)) {
                int d = distTo(ally.location);
                if (d < minDist) {
                    minDist = d;
                    nearestPaintTower = ally.location;
                }
            }
        }

        if (nearestPaintTower == null) {
            fuzzyMove(randomDirection());
            return false;
        }

        if (distTo(nearestPaintTower) <= 2) {
            if (rc.canTransferPaint(nearestPaintTower, -(rc.getType().paintCapacity - rc.getPaint()))) {
                rc.transferPaint(nearestPaintTower, -(rc.getType().paintCapacity - rc.getPaint()));
            }
            if (refillComplete()) return true;
        } else {
            bugNav(nearestPaintTower);
        }
        return false;
    }

    //  Movement BugNav

    /**
     * BugNav: pathfinding sederhana yang bisa memutar obstacle.
     * Cara kerja:
     *   1. Jika jalur lurus bebas → gerak langsung ke target
     *   2. Jika ada halangan → mulai ikuti dinding sampai bisa kembali bergerak langsung ke target
     */
    protected void bugNav(MapLocation target) throws GameActionException {
        if (!rc.isMovementReady()) return;
        if (rc.getLocation().equals(target)) return;

        Direction dir = dirTo(target);

        if (!bugHuggingWall) {
            if (rc.canMove(dir)) {
                rc.move(dir);
                return;
            }
            bugHuggingWall = true;
            bugStartLoc    = rc.getLocation();
            bugStartDist   = distTo(target);
            bugWallDir     = dir;
        }

        // Wall hugging mode
        if (bugHuggingWall) {
            if (!rc.getLocation().equals(bugStartLoc) && distTo(target) < bugStartDist && rc.canMove(dir)) {
                // Bisa gerak ke target lagi
                bugHuggingWall = false;
                rc.move(dir);
                return;
            }

            // Ikuti dinding
            for (int i = 0; i < 8; i++) {
                if (rc.canMove(bugWallDir)) {
                    rc.move(bugWallDir);
                    bugWallDir = bugWallDir.rotateLeft();
                    return;
                }
                bugWallDir = bugWallDir.rotateRight();
            }

            // Stuck total
            bugHuggingWall = false;
        }
    }

    // safeFuzzyMove: fuzzyMove tapi hindari masuk range tower musuh.
    protected void safeFuzzyMove(Direction dir) throws GameActionException {
        if (!rc.isMovementReady()) return;
        for (Direction d : fuzzyDirs(dir)) {
            MapLocation next = rc.getLocation().add(d);
            if (rc.canMove(d) && !inEnemyTowerRange(next)) {
                rc.move(d);
                return;
            }
        }
        // Jika semua arah berbahaya, tetap gerak
        fuzzyMove(dir);
    }

    // Coba gerak masuk ke dalam radius tertentu dari target.
    protected void moveIntoRange(MapLocation target, int radiusSq) throws GameActionException {
        if (!rc.isMovementReady()) return;
        if (distTo(target) <= radiusSq) return;
        bugNav(target);
    }
}