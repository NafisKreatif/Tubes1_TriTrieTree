package fuschia;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.Team;
import battlecode.common.UnitType;

public class MapData {
    public enum RuinStatus {
        UNCLAIMED,
        TAINTED,
        ENEMY_OWNED,
        ALLY_OWNED,
        BLOCKED
    }

    public enum Symmetry {
        ROTATIONAL,
        HORIZONTAL,
        VERTICAL
    }

    private static final class RuinRecord {
        private RuinStatus status;
        private UnitType allyTowerType;

        private RuinRecord(RuinStatus status, UnitType allyTowerType) {
            this.status = status;
            this.allyTowerType = allyTowerType;
        }
    }

    private final RobotController rc;

    // Ruin tracking
    private final Map<MapLocation, RuinRecord> ruins = new HashMap<>();

    // Tower tracking
    private final Map<MapLocation, UnitType> allyTowers = new HashMap<>();
    private final Set<MapLocation> flickerTowers = new HashSet<>();
    private int estimatedPaintTowerCount;

    // Symmetry state
    public boolean rotationalPossible = true;
    public boolean horizontalPossible = true;
    public boolean verticalPossible = true;
    private final Set<Long> checkedTilePairs = new HashSet<>();

    // Exploration state
    public MapLocation exploreTarget;
    public MapLocation returnLocation;
    public int idleTimer;
    public battlecode.common.Direction baseExploreDirection;

    // Communication state
    private final List<MapLocation> mopperBackupRequestsThisTurn = new ArrayList<>();
    private int initialTowerLocationDownloadRound = -1;

    public MapData(RobotController rc) {
        this.rc = rc;
    }

    public void update(RobotController ignored) {
        mopperBackupRequestsThisTurn.clear();

        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        Team allyTeam = rc.getTeam();

        for (MapInfo tile : nearbyTiles) {
            MapLocation loc = tile.getMapLocation();

            if (tile.hasRuin()) {
                RuinStatus status = inferRuinStatus(loc, tile);
                markRuin(loc, status);
            }

            updateSymmetryFromTile(loc, tile);
        }

        try {
            RobotInfo[] nearbyAllies = rc.senseNearbyRobots(-1, allyTeam);
            for (RobotInfo ally : nearbyAllies) {
                if (!isTowerType(ally.type)) {
                    continue;
                }
                allyTowers.put(ally.location, ally.type);
            }
        } catch (GameActionException e) {
            
        }

        refreshEstimatedPaintTowerCount();
    }

    public void markRuin(MapLocation loc, RuinStatus status) {
        RuinRecord existing = ruins.get(loc);
        if (existing == null) {
            ruins.put(loc, new RuinRecord(status, null));
            return;
        }
        existing.status = status;
        if (status != RuinStatus.ALLY_OWNED) {
            existing.allyTowerType = null;
        }
    }

    public MapLocation getNearestRuin(RuinStatus status) {
        MapLocation current = rc.getLocation();
        MapLocation best = null;
        int bestDistance = Integer.MAX_VALUE;

        for (Map.Entry<MapLocation, RuinRecord> entry : ruins.entrySet()) {
            if (entry.getValue().status != status) {
                continue;
            }

            int distance = current.distanceSquaredTo(entry.getKey());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entry.getKey();
            }
        }

        return best;
    }

    public MapLocation getNearestAllyTower() {
        MapLocation current = rc.getLocation();
        MapLocation best = null;
        int bestDistance = Integer.MAX_VALUE;

        for (Map.Entry<MapLocation, UnitType> entry : allyTowers.entrySet()) {
            if (!isPaintTowerType(entry.getValue())) {
                continue;
            }

            int distance = current.distanceSquaredTo(entry.getKey());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entry.getKey();
            }
        }

        return best;
    }

    public MapLocation[] predictEnemyLocations() {
        Symmetry symmetry = getBestSymmetry();
        int width = rc.getMapWidth();
        int height = rc.getMapHeight();

        List<MapLocation> predictions = new ArrayList<>();
        Set<MapLocation> seen = new HashSet<>();
        for (MapLocation allyTower : allyTowers.keySet()) {
            MapLocation mirrored = applySymmetry(allyTower, symmetry, width, height);
            if (mirrored != null && seen.add(mirrored)) {
                predictions.add(mirrored);
            }
        }
        return predictions.toArray(new MapLocation[0]);
    }

    public void resetIdleTimer() {
        idleTimer = 0;
    }

    public void incrementIdleTimer() {
        idleTimer += 1;
    }

    public Symmetry getBestSymmetry() {
        if (rotationalPossible) {
            return Symmetry.ROTATIONAL;
        }
        if (horizontalPossible) {
            return Symmetry.HORIZONTAL;
        }
        if (verticalPossible) {
            return Symmetry.VERTICAL;
        }
        return Symmetry.ROTATIONAL;
    }

    public void markAllyOwnedRuin(MapLocation loc, UnitType towerType) {
        RuinRecord record = ruins.get(loc);
        if (record == null) {
            record = new RuinRecord(RuinStatus.ALLY_OWNED, towerType);
            ruins.put(loc, record);
        } else {
            record.status = RuinStatus.ALLY_OWNED;
            record.allyTowerType = towerType;
        }

        allyTowers.put(loc, towerType);
        refreshEstimatedPaintTowerCount();
    }

    public UnitType getAllyTowerTypeAtRuin(MapLocation loc) {
        RuinRecord record = ruins.get(loc);
        return record == null ? null : record.allyTowerType;
    }

    public MapLocation[] getKnownAllyTowerLocations() {
        return allyTowers.keySet().toArray(new MapLocation[0]);
    }

    public UnitType getKnownAllyTowerType(MapLocation loc) {
        return allyTowers.get(loc);
    }

    public void addFlickerTower(MapLocation loc) {
        flickerTowers.add(loc);
    }

    public MapLocation[] getFlickerTowerLocations() {
        return flickerTowers.toArray(new MapLocation[0]);
    }

    public int getEstimatedPaintTowerCount() {
        return estimatedPaintTowerCount;
    }

    public void addMopperBackupRequest(MapLocation loc) {
        mopperBackupRequestsThisTurn.add(loc);
    }

    public List<MapLocation> getMopperBackupRequestsThisTurn() {
        return new ArrayList<>(mopperBackupRequestsThisTurn);
    }

    public int getInitialTowerLocationDownloadRound() {
        return initialTowerLocationDownloadRound;
    }

    public void setInitialTowerLocationDownloadRound(int round) {
        this.initialTowerLocationDownloadRound = round;
    }

    private RuinStatus inferRuinStatus(MapLocation loc, MapInfo tile) {
        try {
            if (rc.canSenseRobotAtLocation(loc)) {
                RobotInfo robot = rc.senseRobotAtLocation(loc);
                if (robot != null && isTowerType(robot.type)) {
                    if (robot.team == rc.getTeam()) {
                        markAllyOwnedRuin(loc, robot.type);
                        return RuinStatus.ALLY_OWNED;
                    }
                    return RuinStatus.ENEMY_OWNED;
                }
            }
        } catch (GameActionException e) {
        }

        PaintType paint = tile.getPaint();
        if (paint.isEnemy()) {
            return RuinStatus.TAINTED;
        }
        return RuinStatus.UNCLAIMED;
    }

    private void updateSymmetryFromTile(MapLocation source, MapInfo sourceInfo) {
        int width = rc.getMapWidth();
        int height = rc.getMapHeight();

        if (rotationalPossible) {
            MapLocation mirror = applySymmetry(source, Symmetry.ROTATIONAL, width, height);
            rotationalPossible = compareTilePair(source, sourceInfo, mirror);
        }
        if (horizontalPossible) {
            MapLocation mirror = applySymmetry(source, Symmetry.HORIZONTAL, width, height);
            horizontalPossible = compareTilePair(source, sourceInfo, mirror);
        }
        if (verticalPossible) {
            MapLocation mirror = applySymmetry(source, Symmetry.VERTICAL, width, height);
            verticalPossible = compareTilePair(source, sourceInfo, mirror);
        }
    }

    private boolean compareTilePair(MapLocation a, MapInfo infoA, MapLocation b) {
        if (b == null || !rc.canSenseLocation(b)) {
            return true;
        }

        long key = encodePair(a, b);
        if (checkedTilePairs.contains(key)) {
            return true;
        }
        checkedTilePairs.add(key);

        try {
            MapInfo infoB = rc.senseMapInfo(b);
            return infoA.hasRuin() == infoB.hasRuin() && paintClass(infoA.getPaint()) == paintClass(infoB.getPaint());
        } catch (GameActionException e) {
            return true;
        }
    }

    private static int paintClass(PaintType paintType) {
        if (paintType.isAlly()) {
            return 1;
        }
        if (paintType.isEnemy()) {
            return -1;
        }
        return 0;
    }

    private static MapLocation applySymmetry(MapLocation loc, Symmetry symmetry, int width, int height) {
        switch (symmetry) {
            case ROTATIONAL:
                return new MapLocation(width - 1 - loc.x, height - 1 - loc.y);
            case HORIZONTAL:
                return new MapLocation(loc.x, height - 1 - loc.y);
            case VERTICAL:
                return new MapLocation(width - 1 - loc.x, loc.y);
            default:
                return null;
        }
    }

    // combine for hash
    private static long encodePair(MapLocation a, MapLocation b) {
        long first = (((long) a.x) << 48) | (((long) a.y) << 32);
        long second = (((long) b.x) << 16) | ((long) b.y);
        return first ^ second;
    }

    private void refreshEstimatedPaintTowerCount() {
        int count = 0;
        for (UnitType type : allyTowers.values()) {
            if (isPaintTowerType(type)) {
                count += 1;
            }
        }
        estimatedPaintTowerCount = count;
    }

    private static boolean isTowerType(UnitType type) {
        return isPaintTowerType(type)
            || type == UnitType.LEVEL_ONE_MONEY_TOWER
            || type == UnitType.LEVEL_TWO_MONEY_TOWER
            || type == UnitType.LEVEL_THREE_MONEY_TOWER
            || type == UnitType.LEVEL_ONE_DEFENSE_TOWER
            || type == UnitType.LEVEL_TWO_DEFENSE_TOWER
            || type == UnitType.LEVEL_THREE_DEFENSE_TOWER;
    }

    private static boolean isPaintTowerType(UnitType type) {
        return type == UnitType.LEVEL_ONE_PAINT_TOWER
            || type == UnitType.LEVEL_TWO_PAINT_TOWER
            || type == UnitType.LEVEL_THREE_PAINT_TOWER;
    }
}
