package alternative_bot_2_fuchsia;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;
import battlecode.common.UnitType;

public final class RobotPlayer {
    private RobotPlayer() {
    }

    public static void run(RobotController rc) {
        Robot robot = createRobot(rc);

        while (true) {
            try {
                robot.run();
            } catch (GameActionException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    private static Robot createRobot(RobotController rc) {
        UnitType type = rc.getType();
        switch (type) {
            case SOLDIER:
                return new Soldier(rc);
            case MOPPER:
                return new Mopper(rc);
            case SPLASHER:
                return new Splasher(rc);
            case LEVEL_ONE_PAINT_TOWER:
            case LEVEL_TWO_PAINT_TOWER:
            case LEVEL_THREE_PAINT_TOWER:
                return new PaintTower(rc);
            case LEVEL_ONE_MONEY_TOWER:
            case LEVEL_TWO_MONEY_TOWER:
            case LEVEL_THREE_MONEY_TOWER:
                return new MoneyTower(rc);
            case LEVEL_ONE_DEFENSE_TOWER:
            case LEVEL_TWO_DEFENSE_TOWER:
            case LEVEL_THREE_DEFENSE_TOWER:
                return new DefenseTower(rc);
            default:
                throw new IllegalStateException("Unsupported unit type: " + type);
        }
    }
}
