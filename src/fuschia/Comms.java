package fuschia;

import battlecode.common.MapLocation;

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
}
