package alternative_bot_2_fuschia;

import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.Team;
import battlecode.common.UnitType;

public class MapData {
    public static final byte RUIN_UNCLAIMED = 0;
    public static final byte RUIN_TAINTED = 1;
    public static final byte RUIN_ALLY_OWNED = 2;
    public static final byte RUIN_ENEMY_OWNED = 3;

    public final MapLocation[] knownTowers = new MapLocation[10];
    public final byte[] towerTypes = new byte[10];
    public int towerCount;

    public final MapLocation[] knownRuins = new MapLocation[20];
    public final byte[] ruinStatus = new byte[20];
    public int ruinCount;

    public int idleTimer;
    private final Team allyTeam;

    public MapData(RobotController rc) {
        allyTeam = rc.getTeam();
    }

    public void update(RobotController rc, RobotInfo[] nearbyRobots) throws GameActionException {
        for (RobotInfo robot : nearbyRobots) {
            MapLocation loc = robot.location;

            if (robot.team == allyTeam && isTowerType(robot.type)) {
                upsertTower(loc, robot.type);
            }
        }

        for (MapInfo tile : rc.senseNearbyMapInfos()) {
            if (!tile.hasRuin()) {
                continue;
            }

            MapLocation ruin = tile.getMapLocation();
            int idx = findRuinIndex(ruin);
            if (idx == -1) {
                if (ruinCount >= knownRuins.length) {
                    continue;
                }
                idx = ruinCount;
                knownRuins[ruinCount] = ruin;
                ruinStatus[ruinCount] = RUIN_UNCLAIMED;
                ruinCount += 1;
            }

            if (rc.canSenseRobotAtLocation(ruin)) {
                RobotInfo atRuin = rc.senseRobotAtLocation(ruin);
                if (atRuin != null && isTowerType(atRuin.type)) {
                    ruinStatus[idx] = atRuin.team == allyTeam ? RUIN_ALLY_OWNED : RUIN_ENEMY_OWNED;
                }
            }
        }
    }

    public MapLocation getNearestPaintTower(MapLocation from) {
        MapLocation best = null;
        int bestDistance = Integer.MAX_VALUE;

        for (int i = 0; i < towerCount; i++) {
            if ((towerTypes[i] & 0b111) != UnitType.LEVEL_ONE_PAINT_TOWER.ordinal() % 8
                && (towerTypes[i] & 0b111) != UnitType.LEVEL_TWO_PAINT_TOWER.ordinal() % 8
                && (towerTypes[i] & 0b111) != UnitType.LEVEL_THREE_PAINT_TOWER.ordinal() % 8) {
                continue;
            }
            int distance = from.distanceSquaredTo(knownTowers[i]);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = knownTowers[i];
            }
        }
        return best;
    }

    public MapLocation getNearestMoneyTower(MapLocation from) {
        MapLocation best = null;
        int bestDistance = Integer.MAX_VALUE;

        for (int i = 0; i < towerCount; i++) {
            UnitType type = UnitType.values()[towerTypes[i]];
            if (type != UnitType.LEVEL_ONE_MONEY_TOWER
                && type != UnitType.LEVEL_TWO_MONEY_TOWER
                && type != UnitType.LEVEL_THREE_MONEY_TOWER) {
                continue;
            }

            int distance = from.distanceSquaredTo(knownTowers[i]);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = knownTowers[i];
            }
        }
        return best;
    }

    public MapLocation getNearestTower(MapLocation from) {
        MapLocation best = null;
        int bestDistance = Integer.MAX_VALUE;

        for (int i = 0; i < towerCount; i++) {
            int distance = from.distanceSquaredTo(knownTowers[i]);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = knownTowers[i];
            }
        }
        return best;
    }

    public MapLocation getNearestUnclaimed(MapLocation from) {
        MapLocation best = null;
        int bestDistance = Integer.MAX_VALUE;

        for (int i = 0; i < ruinCount; i++) {
            if (ruinStatus[i] != RUIN_UNCLAIMED) {
                continue;
            }

            int distance = from.distanceSquaredTo(knownRuins[i]);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = knownRuins[i];
            }
        }
        return best;
    }

    public void markRuin(MapLocation loc, byte status) {
        int ruinIndex = findRuinIndex(loc);
        if (ruinIndex != -1) {
            ruinStatus[ruinIndex] = status;
        }
    }

    public void upsertTower(MapLocation loc, UnitType type) {
        if (loc == null || type == null) {
            return;
        }

        int idx = findTowerIndex(loc);
        if (idx != -1) {
            towerTypes[idx] = (byte) type.ordinal();
            return;
        }

        if (towerCount >= knownTowers.length) {
            return;
        }

        knownTowers[towerCount] = loc;
        towerTypes[towerCount] = (byte) type.ordinal();
        towerCount += 1;
    }

    public void resetIdleTimer() {
        idleTimer = 0;
    }

    public void incrementIdleTimer() {
        idleTimer += 1;
    }

    private int findTowerIndex(MapLocation loc) {
        for (int i = 0; i < towerCount; i++) {
            if (knownTowers[i].equals(loc)) {
                return i;
            }
        }
        return -1;
    }

    private int findRuinIndex(MapLocation loc) {
        for (int i = 0; i < ruinCount; i++) {
            if (knownRuins[i].equals(loc)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isTowerType(UnitType type) {
        return type == UnitType.LEVEL_ONE_PAINT_TOWER
            || type == UnitType.LEVEL_TWO_PAINT_TOWER
            || type == UnitType.LEVEL_THREE_PAINT_TOWER
            || type == UnitType.LEVEL_ONE_MONEY_TOWER
            || type == UnitType.LEVEL_TWO_MONEY_TOWER
            || type == UnitType.LEVEL_THREE_MONEY_TOWER
            || type == UnitType.LEVEL_ONE_DEFENSE_TOWER
            || type == UnitType.LEVEL_TWO_DEFENSE_TOWER
            || type == UnitType.LEVEL_THREE_DEFENSE_TOWER;
    }
}
