package besi2;

import battlecode.common.*;

import java.util.*;

/**
 * Base class robot dan tower
 * Isi: rc, utilitas umum, run loop.
 * Pola: run() → loop → turn() → Clock.yield()
 */
abstract public class Robot {

    static public final Direction[] DIRECTIONS = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };

    static public final UnitType[] TOWER_TYPES = {
        UnitType.LEVEL_ONE_PAINT_TOWER,
        UnitType.LEVEL_ONE_MONEY_TOWER,
        UnitType.LEVEL_ONE_DEFENSE_TOWER,
    };

    public RobotController rc;
    public int mapWidth;
    public int mapHeight;
    public MapLocation spawnLoc;
    public int spawnTurn;
    
    /**
     * RNG diseed dari robot ID
     * RIGHT = true jika ID genap
     * Dipakai fuzzyDirs() supaya setengah robot rotasi ke kanan dulu,
     * sisanya ke kiri untuk mencgah deadlock
     */
    public Random rng;
    public boolean RIGHT;

    public static boolean[][] blueprintPaint;
    public static boolean[][] blueprintMoney;
    public static boolean[][] blueprintDefense;

    // Constructor
    public Robot(RobotController rc) throws GameActionException {
        this.rc = rc;
        this.mapWidth = rc.getMapWidth();
        this.mapHeight = rc.getMapHeight();
        this.spawnLoc = rc.getLocation();
        this.spawnTurn = rc.getRoundNum();
        this.rng = new Random(rc.getID());
        this.RIGHT = rc.getID()%2 == 0;

        if (blueprintPaint == null) {
            blueprintPaint = rc.getTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER);
            blueprintMoney = rc.getTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER);
            blueprintDefense = rc.getTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER);
        }

        // Soldier muncul langsung cat
        if (rc.getType() == UnitType.SOLDIER
                && rc.senseMapInfo(rc.getLocation()).getPaint() == PaintType.EMPTY
                && rc.canAttack(rc.getLocation())) {
            rc.attack(rc.getLocation());
        }
    }

    // Game Loop
    public void run() {
        while (true) {
            try {
                turn();
            } catch (GameActionException e) {
                System.out.println("[GAME] " + rc.getType() + " ID=" + rc.getID() + ": " + e.getMessage());
            } catch (Exception e) {
                System.out.println("[ERR ] " + rc.getType() + " ID=" + rc.getID() + ": " + e.getMessage());
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    abstract public void turn() throws Exception;

    // Utilitas Posisi
    public int distTo(MapLocation loc) {
        return rc.getLocation().distanceSquaredTo(loc);
    }

    public Direction dirTo(MapLocation loc) {
        return rc.getLocation().directionTo(loc);
    }

    public boolean touching(MapLocation loc) {
        return rc.getLocation().isAdjacentTo(loc) || rc.getLocation().equals(loc);
    }
    
    // Utilitas Arah

    /**
     * Return array 7 arah
     * RIGHT falg untuk menentukan arah rotasi
     */
    public Direction[] fuzzyDirs(Direction dir) {
        if (RIGHT) {
            return new Direction[] {
                dir,
                dir.rotateRight(),
                dir.rotateLeft(),
                dir.rotateRight().rotateRight(),
                dir.rotateLeft().rotateLeft(),
                dir.rotateRight().rotateRight().rotateRight(),
                dir.rotateLeft().rotateLeft().rotateLeft(),
            };
        } else {
            return new Direction[] {
                dir,
                dir.rotateLeft(),
                dir.rotateRight(),
                dir.rotateLeft().rotateLeft(),
                dir.rotateRight().rotateRight(),
                dir.rotateLeft().rotateLeft().rotateLeft(),
                dir.rotateRight().rotateRight().rotateRight(),
            };
        }
    }

    public Direction randomDirection() {
        return DIRECTIONS[rng.nextInt(DIRECTIONS.length)];
    }

    // Target awal untuk perluas lokasi ke tepi map
    public MapLocation extendToEdge(MapLocation loc, Direction dir) {
        MapLocation cur = loc;
        while (rc.onTheMap(cur.add(dir))) {
            cur = cur.add(dir);
        }
        return cur;
    }

    // Utilitas Pencarian

    // MapLocation terdekat dari array
    public MapLocation getClosest(MapLocation[] locs) {
        if (locs == null || locs.length == 0) return null;
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        for (MapLocation loc : locs) {
            if (loc == null) continue;
            int d = distTo(loc);
            if (d < bestDist) {
                bestDist = d;
                best = loc;
            }
        }
        return best;
    }

    // RobotInfo terdekat dari array
    public RobotInfo getClosest(RobotInfo[] robots) {
        if (robots == null || robots.length == 0) return null;
        RobotInfo best = null;
        int bestDist = Integer.MAX_VALUE;
        for (RobotInfo r : robots) {
            int d = distTo(r.location);
            if (d < bestDist) {
                bestDist = d;
                best = r;
            }
        }
        return best;
    }

    // Utilitas Aksi

    // Attack lokasi
    public boolean tryAttack(MapLocation loc) throws GameActionException {
        if (loc != null && rc.canAttack(loc)) {
            rc.attack(loc);
            return true;
        }
        return false;
    }

    // Gerak ke arah dir
    public boolean tryMove(Direction dir) throws GameActionException {
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }
        return false;
    }

    // Fuzzy move ke arah dir, coba rotasi kanan/kiri bergantian
    public boolean fuzzyMove(Direction dir) throws GameActionException {
        for (Direction d : fuzzyDirs(dir)) {
            if (rc.canMove(d)) {
                rc.move(d);
                return true;
            }
        }
        return false;
    }

    // Helper

    public static boolean isPaintTower(UnitType t) {
        return t == UnitType.LEVEL_ONE_PAINT_TOWER
            || t == UnitType.LEVEL_TWO_PAINT_TOWER
            || t == UnitType.LEVEL_THREE_PAINT_TOWER;
    }

    public static boolean isMoneyTower(UnitType t) {
        return t == UnitType.LEVEL_ONE_MONEY_TOWER
            || t == UnitType.LEVEL_TWO_MONEY_TOWER
            || t == UnitType.LEVEL_THREE_MONEY_TOWER;
    }

    public static boolean isDefenseTower(UnitType t) {
        return t == UnitType.LEVEL_ONE_DEFENSE_TOWER
            || t == UnitType.LEVEL_TWO_DEFENSE_TOWER
            || t == UnitType.LEVEL_THREE_DEFENSE_TOWER;
    }

    public int encodeLocation(MapLocation loc) {
        return loc.x | (loc.y << 8);
    }

    public MapLocation decodeLocation(int encoded) {
        return new MapLocation(encoded & 0xFF, (encoded >> 8) & 0xFF);
    }
}
