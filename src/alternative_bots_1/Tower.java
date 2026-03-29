package alternative_bots_1;

import battlecode.common.*;

/**
 * Base class tower
 * Extends Robot, menambahkan:
 *   - Spawning unit (trySummon, trySummonToward)
 *   - Upgrade tower
 *   - Attack musuh (prioritas Soldier HP rendah)
 *   - Emergency mopper saat diserang
 *   - Komunikasi awal (broadcast posisi tower)
 *   - Pemilihan tipe tower saat bangun baru (determineTowerType)
 */
public abstract class Tower extends Robot {
    protected final boolean isStartingTower;   // Tower awal
    protected final Direction enemyDirection;  // Arah menuju musuh (estimasi dari posisi tower)
    protected final MapLocation[] spawnTiles;  // Tile yang bisa dipakai untuk spawn robot
    protected int spawnCounter = 0;
    protected int lastEmergencyMopperTurn = -1000;
    protected static final int EMERGENCY_MOPPER_COOLDOWN = 25;

    // Constructor
    public Tower(RobotController rc) throws GameActionException {
        super(rc);
        isStartingTower = rc.getRoundNum() == 1;

        // Estimasi arah musuh, rotasi 180° dari posisi tower
        MapLocation center = new MapLocation(mapWidth/2, mapHeight/2);
        enemyDirection = rc.getLocation().directionTo(center).opposite();

        spawnTiles = rc.getAllLocationsWithinRadiusSquared(rc.getLocation(), GameConstants.BUILD_ROBOT_RADIUS_SQUARED);
    }

    // Turn tower

    @Override
    public void turn() throws GameActionException {
        RobotInfo[] alliedRobots = rc.senseNearbyRobots(-1, rc.getTeam());
        RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        handleUpgrade(alliedRobots);
        handleEmergencyMopper(enemyRobots, alliedRobots);
        handleSpawning(alliedRobots);
        handleAttack(enemyRobots);
    }

    // Upgrade

    private void handleUpgrade(RobotInfo[] allies) throws GameActionException {
        if (!rc.canUpgradeTower(rc.getLocation())) return;
        int chips = rc.getChips();
        int nearbyAllies = allies.length;

        // Lv1 → Lv2 butuh 2500, Lv2 → Lv3 butuh 5000
        UnitType currentType = rc.getType();
        boolean isLv1 = currentType == UnitType.LEVEL_ONE_PAINT_TOWER
                    || currentType == UnitType.LEVEL_ONE_MONEY_TOWER
                    || currentType == UnitType.LEVEL_ONE_DEFENSE_TOWER;

        int upgradeCost = isLv1 ? 2500 : 5000;
        int chipsThreshold = upgradeCost + 500;  
        int richThreshold = upgradeCost + 1500; 
        //  di sini                 johjijhih
        // if (isPaintTower(currentType)) {
        //     // Paint tower
            if (chips > chipsThreshold && (nearbyAllies >= 3 || chips > richThreshold)) {
                rc.upgradeTower(rc.getLocation());
            }
        // } else {
        //     // Tower lain
        //     if (chips > chipsThreshold + 500 && (nearbyAllies > 3 || chips > richThreshold + 500)) {
        //         rc.upgradeTower(rc.getLocation());
        //     }
        // }
    }

    //  Emergency mopper saat diserang

    private void handleEmergencyMopper(RobotInfo[] enemies, RobotInfo[] allies) throws GameActionException {
        // Hitung soldier musuh yang bisa menyerang tower ini
        int enemySoldiers = 0;
        for (RobotInfo e : enemies) {
            if (e.type == UnitType.SOLDIER) enemySoldiers++;
        }
        if (enemySoldiers == 0) return;

        // Hitung mopper friendly yang sudah ada
        int friendlyMoppers = 0;
        for (RobotInfo a : allies) {
            if (a.type == UnitType.MOPPER) friendlyMoppers++;
        }

        // Spawn emergency mopper jika kurang dan cooldown sudah lewat
        boolean cooldownOk = rc.getRoundNum() - lastEmergencyMopperTurn > EMERGENCY_MOPPER_COOLDOWN;
        if (friendlyMoppers < enemySoldiers && cooldownOk) {
            if (trySummonToward(UnitType.MOPPER, dirTo(enemies[0].location))) {
                lastEmergencyMopperTurn = rc.getRoundNum();
            }
        }
    }

    //  Spawning normal

    /**
     * Paint tower dan starting tower yang spawn, tower lain backup.
     * Pola spawn:
     *   Early game (round < 300): 2 Soldier → 1 Splasher → ulang
     *   Mid-late game: 1 Soldier → 1 Mopper → 1 Splasher → ulang
     */
    protected void handleSpawning(RobotInfo[] allies) throws GameActionException {
        boolean shouldSpawn = isPaintTower(rc.getType()) || isStartingTower;
        if (!shouldSpawn) {
            // Spawn Soldier jika ada banyak chips
            if (rc.getChips() > 1500 && rc.getPaint() > UnitType.SOLDIER.paintCost) {
                trySummon(UnitType.SOLDIER);
            }
            return;
        }

        // Tidak spawn jika tidak ada perubahan chips atau jika paint tidak cukup
        if (rc.getPaint() < UnitType.MOPPER.paintCost) return;

        int round = rc.getRoundNum();

        if (round < 300) {
            // Early game: prioritas Soldier, sesekali Splasher ke arah musuh
            // Pattern: S S Sp S S Sp ...
            if (spawnCounter % 3 < 2) {
                // Soldier pertama diarahkan ke musuh untuk eksplorasi
                if (spawnCounter == 0) {
                    if (trySummonToward(UnitType.SOLDIER, enemyDirection)) spawnCounter++;
                } else {
                    if (trySummon(UnitType.SOLDIER)) spawnCounter++;
                }
            } else {
                if (trySummonToward(UnitType.SPLASHER, enemyDirection)) spawnCounter++;
            }
        } else {
            // Mid-late game: Soldier + Mopper + Splasher
            // Pattern: S M Sp S M Sp ...
            if (spawnCounter % 3 == 0) {
                if (trySummon(UnitType.SOLDIER)) spawnCounter++;
            } else if (spawnCounter % 3 == 1) {
                if (trySummon(UnitType.MOPPER)) spawnCounter++;
            } else {
                if (trySummonToward(UnitType.SPLASHER, enemyDirection)) spawnCounter++;
            }
        }
    }

    // Attack

    /**
     * Serang musuh terdekat
     * Prioritas: Soldier dengan HP paling rendah, lalu null attack (AoE) untuk damage area
     */
    protected void handleAttack(RobotInfo[] enemies) throws GameActionException {
        // AoE attack dulu
        if (rc.isActionReady()) rc.attack(null);

        // Pilih target single attack: soldier HP terendah → non-soldier HP terendah
        RobotInfo best = null;
        int bestHP = Integer.MAX_VALUE;
        boolean foundSoldier = false;

        for (RobotInfo e : enemies) {
            if (!rc.canAttack(e.location)) continue;
            boolean isSoldier = (e.type == UnitType.SOLDIER);
            if (!foundSoldier && isSoldier) {
                foundSoldier = true;
                best   = e;
                bestHP = e.health;
            } else if (foundSoldier && isSoldier && e.health < bestHP) {
                best   = e;
                bestHP = e.health;
            } else if (!foundSoldier && e.health < bestHP) {
                best   = e;
                bestHP = e.health;
            }
        }

        if (best != null) tryAttack(best.location);
    }

    //  Pemilihan tipe tower baru

    /**
     * Prioritas:
     *   1. Defense tower jika: kurang 2 defense tower + ruin dekat center + ada wall di 2 sisi
     *   2. Money tower jika rasio money:paint terlalu rendah
     *   3. Paint tower sebagai default
     *
     * @param ruinLoc lokasi ruin yang akan dibangun
     */
    public UnitType determineTowerType(MapLocation ruinLoc) throws GameActionException {
        // Hitung tower yang sudah ada
        int moneyTowers = 0;
        int paintTowers = 0;
        int defenseTowers = 0;
        for (RobotInfo ally : rc.senseNearbyRobots(-1, rc.getTeam())) {
            if (isMoneyTower(ally.type)) moneyTowers++;
            if (isPaintTower(ally.type)) paintTowers++;
            if (isDefenseTower(ally.type)) defenseTowers++;
        }

        // 1. Defense tower
        MapLocation center = new MapLocation(mapWidth/2, mapHeight/2);
        if (defenseTowers < 2 && ruinLoc.distanceSquaredTo(center) < 50) {
            if (hasWallOnOpposingSides(ruinLoc)) {
                return UnitType.LEVEL_ONE_DEFENSE_TOWER;
            }
        }

        // 2. Money tower
        double ratio = moneyTowers < 6 ? 2.5 : 1.5;
        if (moneyTowers < Math.max(1, paintTowers) * ratio && rc.getChips() < 4000) {
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        }

        // 3. Default: paint tower
        return UnitType.LEVEL_ONE_PAINT_TOWER;
    }

    // Cek apakah ada wall di sisi utara-selatan ATAU timur-barat dari ruin.
    private boolean hasWallOnOpposingSides(MapLocation ruin) throws GameActionException {
        boolean north = false, south = false, east = false, west = false;
        // Cek 4 tile ke utara dan selatan
        for (int i = 1; i <= 4; i++) {
            MapLocation n = ruin.translate(0, i);
            MapLocation s = ruin.translate(0, -i);
            MapLocation e = ruin.translate(i, 0);
            MapLocation w = ruin.translate(-i, 0);
            if (rc.onTheMap(n) && rc.senseMapInfo(n).isWall()) north = true;
            if (rc.onTheMap(s) && rc.senseMapInfo(s).isWall()) south = true;
            if (rc.onTheMap(e) && rc.senseMapInfo(e).isWall()) east = true;
            if (rc.onTheMap(w) && rc.senseMapInfo(w).isWall()) west = true;
        }
        return (north && south) || (east && west);
    }

    //  Spawning helpers

    /**
     * Spawn unit di tile terbaik (sedikit adjacent allies, prefer allied paint).
     */
    protected boolean trySummon(UnitType type) throws GameActionException {
        MapLocation best = null;
        int bestScore = Integer.MAX_VALUE;

        for (MapLocation loc : spawnTiles) {
            // Hindari spawn di tepi map (kemungkinan langsung mati di wall)
            if (loc.x < 3 || loc.x > mapWidth - 4 || loc.y < 3 || loc.y > mapHeight - 4) continue;
            if (!rc.canBuildRobot(type, loc)) continue;

            // Score: lebih sedikit adjacent allies lebih baik
            // Prefer allied paint (kurangi score)
            int score = rc.senseNearbyRobots(loc, 2, rc.getTeam()).length;
            MapInfo info = rc.senseMapInfo(loc);
            // Cek ini
            if (info.getPaint().isAlly()) score -= 2;

            if (score < bestScore) {
                bestScore = score;
                best = loc;
            }
        }

        if (best != null) {
            rc.buildRobot(type, best);
            return true;
        }
        return false;
    }

    //Spawn unit diarahkan ke targetDir (untuk first soldier menuju musuh)
    protected boolean trySummonToward(UnitType type, Direction targetDir) throws GameActionException {
        // Coba 2 langkah ke arah target, lalu 1 langkah
        MapLocation loc2 = rc.getLocation().add(targetDir).add(targetDir);
        MapLocation loc1 = rc.getLocation().add(targetDir);
        if (rc.canBuildRobot(type, loc2)) {
            rc.buildRobot(type, loc2);
            return true;
        }
        if (rc.canBuildRobot(type, loc1)) {
            rc.buildRobot(type, loc1);
            return true;
        }
        return trySummon(type);
    }
}

//Jangan lupa: cek ini
