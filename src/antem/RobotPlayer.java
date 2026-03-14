package antem;

import battlecode.common.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Random;

/**
 * RobotPlayer is the class that describes your main robot strategy.
 * The run() method inside this class is like your main function: this is what
 * we'll call once your robot
 * is created!
 */
public class RobotPlayer {
    static public class Coords {
        public int x, y, round;
        public Coords(int x, int y, int round) {
            this.x = x;
            this.y = y;
            this.round = round;
        }
    }

    static int turnCount = 0;
    static ArrayList<Coords> towerList = new ArrayList<>();
    static ArrayDeque<Coords> targetList = new ArrayDeque<>();

    /**
     * A random number generator.
     * No, I want my NIM to be the seed
     * - Nafis
     */
    static final Random rng = new Random(13524018);

    /** Array containing all the possible movement directions. */
    static final Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
    };

    /**
     * run() is the method that is called when a robot is instantiated in the
     * Battlecode world.
     * It is like the main function for your robot. If this method returns, the
     * robot dies!
     *
     * @param rc The RobotController object. You use it to perform actions from this
     *           robot, and to get
     *           information on its current status. Essentially your portal to
     *           interacting with the world.
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        System.out.println("Ant deployed!");

        while (true) {
            turnCount += 1;
            try {
                switch (rc.getType()) {
                    case SOLDIER:
                        Soldier.run(rc);
                        break;
                    case MOPPER:
                        Mopper.run(rc);
                        break;
                    case SPLASHER:
                        Splasher.run(rc);
                        break; // Consider upgrading examplefuncsplayer to use splashers!
                    default:
                        Tower.run(rc);
                        break;
                }
            } catch (GameActionException e) {
                // Waddat!! Wadidaidu
                System.out.println("GameActionException: Wadidaidu?");
                e.printStackTrace();

            } catch (Exception e) {
                System.out.println("Exception: Notme");
                e.printStackTrace();

            } finally {
                // End turn
                Clock.yield();
            }
        }
    }
}
