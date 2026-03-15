package fuschia;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

// Communication
public final class Comms {
    // Structure to save bytecode:
    // [3 bits: type] [6 bits: x] [6 bits: y] [2 bits: tower type]
    private static final int TYPE_MASK = 0b111;
    private static final int COORD_MASK = 0b111111;
    private static final int TOWER_TYPE_MASK = 0b11;

    private Comms() {
    }

    public enum MessageType {
        TOWER_LOCATION(0),
        MOPPER_REQUEST(1);

        private final int value;

        MessageType(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }

        public static MessageType fromValue(int value) {
            for (MessageType type : values()) {
                if (type.value == value) {
                    return type;
                }
            }
            return TOWER_LOCATION;
        }
    }

    public static final class DecodedMessage {
        public final MessageType type;
        public final MapLocation location;
        public final int towerType;

        public DecodedMessage(MessageType type, MapLocation location, int towerType) {
            this.type = type;
            this.location = location;
            this.towerType = towerType;
        }
    }

    public static void readAndApply(RobotController rc, MapData mapData) throws GameActionException {
        Message[] messages = rc.readMessages(-1);
        for (Message msg : messages) {
            DecodedMessage decoded = decodeMessage(msg.getBytes());
            if (decoded.type != MessageType.TOWER_LOCATION) {
                continue;
            }

            UnitType towerType = decodeTowerType(decoded.towerType);
            mapData.upsertTower(decoded.location, towerType);
        }
    }

    public static void broadcastTowerIntel(RobotController rc, MapData mapData) throws GameActionException {
        MapLocation towerLocation = null;
        int towerTypeCode = 0;
        UnitType selfType = rc.getType();
        if (isTowerType(selfType)) {
            towerLocation = rc.getLocation();
            towerTypeCode = encodeTowerType(selfType);
        } else {
            MapLocation nearest = mapData.getNearestTower(rc.getLocation());
            if (nearest != null) {
                int idx = findTowerIndex(mapData, nearest);
                if (idx != -1) {
                    towerLocation = nearest;
                    towerTypeCode = encodeTowerType(UnitType.values()[mapData.towerTypes[idx]]);
                }
            }
        }

        if (towerLocation == null) {
            return;
        }

        int payload = encodeMessage(MessageType.TOWER_LOCATION, towerLocation, towerTypeCode);
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (ally.location.equals(rc.getLocation())) {
                continue;
            }
            if (rc.canSendMessage(ally.location, payload)) {
                rc.sendMessage(ally.location, payload);
            }
        }
    }

    public static int encodeMessage(MessageType type, MapLocation loc, int towerType) {
        int message = 0;
        message |= (type.value() & TYPE_MASK);
        message |= (loc.x & COORD_MASK) << 3;
        message |= (loc.y & COORD_MASK) << 9;
        message |= (towerType & TOWER_TYPE_MASK) << 15;
        return message;
    }

    public static DecodedMessage decodeMessage(int message) {
        int typeValue = message & TYPE_MASK;
        int x = (message >>> 3) & COORD_MASK;
        int y = (message >>> 9) & COORD_MASK;
        int towerType = (message >>> 15) & TOWER_TYPE_MASK;
        return new DecodedMessage(MessageType.fromValue(typeValue), new MapLocation(x, y), towerType);
    }

    private static int findTowerIndex(MapData mapData, MapLocation loc) {
        for (int i = 0; i < mapData.towerCount; i++) {
            if (mapData.knownTowers[i].equals(loc)) {
                return i;
            }
        }
        return -1;
    }

    private static int encodeTowerType(UnitType towerType) {
        if (towerType == UnitType.LEVEL_ONE_MONEY_TOWER
            || towerType == UnitType.LEVEL_TWO_MONEY_TOWER
            || towerType == UnitType.LEVEL_THREE_MONEY_TOWER) {
            return 1;
        }
        if (towerType == UnitType.LEVEL_ONE_DEFENSE_TOWER
            || towerType == UnitType.LEVEL_TWO_DEFENSE_TOWER
            || towerType == UnitType.LEVEL_THREE_DEFENSE_TOWER) {
            return 2;
        }
        return 0;
    }

    private static UnitType decodeTowerType(int encodedType) {
        if (encodedType == 1) {
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        }
        if (encodedType == 2) {
            return UnitType.LEVEL_ONE_DEFENSE_TOWER;
        }
        return UnitType.LEVEL_ONE_PAINT_TOWER;
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
