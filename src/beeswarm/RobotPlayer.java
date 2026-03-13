package beeswarm;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * RobotPlayer is the class that describes your main robot strategy.
 * The run() method inside this class is like your main function: this is what
 * we'll call once your robot
 * is created!
 */
public class RobotPlayer {
    static int turnCount = 0;
    static int leaderID = -1;
    static int robotTypeToBuild = 0;

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
        System.out.println("I'VE BEECOME SOMETHING");
        rc.setIndicatorString("Hacthling!");

        while (true) {
            turnCount += 1; // We have now been alive for one more turn!

            try {
                switch (rc.getType()) {
                    case SOLDIER:
                        runSoldier(rc);
                        break;
                    case MOPPER:
                        runMopper(rc);
                        break;
                    case SPLASHER:
                        break; // Consider upgrading examplefuncsplayer to use splashers!
                    default:
                        runTower(rc);
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

    /**
     * Run a single turn for towers.
     * This code is wrapped inside the infinite loop in run(), so it is called once
     * per turn.
     */
    public static void runTower(RobotController rc) throws GameActionException {
        // Pick a direction to build in.
        Direction dir = directions[rng.nextInt(directions.length)];
        MapLocation nextLoc = rc.getLocation().add(dir);
        if (rc.getRoundNum() % 10 == 0) {
        // Pick a random robot type to build.
            if (robotTypeToBuild == 0 && rc.canBuildRobot(UnitType.SOLDIER, nextLoc)) {
                robotTypeToBuild = (robotTypeToBuild + 1) % 2;
                rc.buildRobot(UnitType.SOLDIER, nextLoc);
                System.out.println("SPAWNING A BEE TROOPER");
            } else if (robotTypeToBuild == 1 && rc.canBuildRobot(UnitType.MOPPER, nextLoc)) {
                robotTypeToBuild = (robotTypeToBuild + 1) % 2;
                rc.buildRobot(UnitType.MOPPER, nextLoc);
                System.out.println("SPAWNING A BEE SWEEPER");
            }
        }
        // } else if (robotType <= 3 && rc.canBuildRobot(UnitType.SPLASHER, nextLoc)) {
        // // rc.buildRobot(UnitType.SPLASHER, nextLoc);
        // System.out.println("SPANING A BEE BOOMER");
        // }

        // Read incoming messages
        // Message[] messages = rc.readMessages(-1);
        // for (Message m : messages) {
        // System.out.println("Tower received message: '#" + m.getSenderID() + " " +
        // m.getBytes());
        // }

        MapLocation attackSpot = calculateBestAoESpot(rc);
        if (attackSpot != null && rc.canAttack(attackSpot)) {
            rc.attack(attackSpot);
            System.out.println("Tower attack!!!");
        }
    }

    /**
     * Run a single turn for a Soldier.
     * This code is wrapped inside the infinite loop in run(), so it is called once
     * per turn.
     */
    public static void runSoldier(RobotController rc) throws GameActionException {
        RobotInfo leaderInfo = senseLeader(rc);
        if (leaderInfo != null) {
            leaderID = leaderInfo.ID;
        } else {
            leaderID = -1;
        }
        rc.setIndicatorString(Integer.toString(leaderID));

        // Attack enemy if there's any
        MapLocation enemyLocation = getEnemyLocationNearby(rc);
        if (enemyLocation != null) {
            if (rc.canAttack(enemyLocation)) {
                rc.attack(enemyLocation);
            }
        }

        // Sense information about all visible nearby tiles.
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(2);
        MapInfo ruinTile = null;
        ArrayList<MapInfo> targetTiles = new ArrayList<>();
        for (MapInfo tile : nearbyTiles) {
            if (tile.hasRuin()) {
                ruinTile = tile;
            }
            if (tile.getPaint().isAlly() || tile.getPaint() == PaintType.EMPTY || tile.getMark() != tile.getPaint()) {
                targetTiles.add(tile);
            }
        }
        if (ruinTile != null) {
            tryMarkTower(rc, ruinTile.getMapLocation());
        } else {
            // tryMarkResource(rc, rc.getLocation());
        }

        // Fill in any spots in the pattern with the appropriate paint.
        if (!targetTiles.isEmpty()) {
            MapInfo targetTile = targetTiles.get(rng.nextInt(targetTiles.size()));
            boolean useSecondaryColor = targetTile.getMark() == PaintType.ALLY_SECONDARY;
            if (rc.canAttack(targetTile.getMapLocation())) {
                rc.attack(targetTile.getMapLocation(), useSecondaryColor);
            }
        }

        // Complete tower and resource pattern
        if (ruinTile != null) {
            tryCompleteTower(rc, ruinTile.getMapLocation());
        }
        // for (MapInfo tile : nearbyTiles) {
        //     tryCompleteResource(rc, tile.getMapLocation());
        // }

        // If a leader
        if (leaderID == -1) {
            // ArrayList<Direction> safeDirections = new ArrayList<>();
            // for (Direction direction : directions) {
            //     MapInfo tile = rc.senseMapInfo(rc.getLocation().add(direction));
            //     if (rc.canMove(direction) && tile.getPaint().isAlly()) {
            //         safeDirections.add(direction);
            //     }
            // }
            // if (!safeDirections.isEmpty()) {
            //     rc.move(safeDirections.get(rng.nextInt(safeDirections.size())));
            // }
                rc.move(directions[rng.nextInt(directions.length)]);

        } else {
            Direction dir = rc.getLocation().directionTo(leaderInfo.getLocation());
            MapInfo tile = rc.senseMapInfo(rc.getLocation().add(dir));

            // Move if safe
            // if (rc.canMove(dir) && tile.getPaint().isAlly()) {
            //     rc.move(dir);
            // }
            // Move if safe and not adjacent to the leader (avoid wasting paint)
            if (rc.canMove(dir) && tile.getPaint().isAlly() &&
            !tile.getMapLocation().isAdjacentTo(leaderInfo.getLocation())) {
            rc.move(dir);
            }
        }
    }

    /**
     * Run a single turn for a Mopper.
     * This code is wrapped inside the infinite loop in run(), so it is called once
     * per turn.
     */
    public static void runMopper(RobotController rc) throws GameActionException {
        RobotInfo leaderInfo = senseLeader(rc);
        if (leaderInfo != null) {
            leaderID = leaderInfo.ID;
        } else {
            leaderID = -1;
        }
        rc.setIndicatorString(Integer.toString(leaderID));

        // Attack enemy if there's any
        MapLocation enemyLocation = getEnemyLocationNearby(rc);
        if (enemyLocation != null) {
            if (rc.canAttack(enemyLocation)) {
                rc.attack(enemyLocation);
            }
        }

        // Sense information about all visible nearby tiles.
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        ArrayList<MapInfo> targetTiles = new ArrayList<>();
        ArrayList<MapInfo> mopSwingTiles = new ArrayList<>();
        for (MapInfo tile : nearbyTiles) {
            if (tile.getPaint().isEnemy()) {
                targetTiles.add(tile);
            }
            if (!tile.getPaint().isAlly()) {
                Direction dir = rc.getLocation().directionTo(tile.getMapLocation());
                if (dir == Direction.EAST
                        || dir == Direction.NORTH
                        || dir == Direction.WEST
                        || dir == Direction.SOUTH) {
                    mopSwingTiles.add(tile);
                }
            }
        }

        if (!mopSwingTiles.isEmpty()) {
            // Fill in any spots in the pattern with the appropriate paint.
            MapInfo mopSwingTile = mopSwingTiles.get(rng.nextInt(targetTiles.size()));
            boolean useSecondaryColor = mopSwingTile.getMark() == PaintType.ALLY_SECONDARY;
            if (rc.canAttack(mopSwingTile.getMapLocation())) {
                rc.attack(mopSwingTile.getMapLocation(), useSecondaryColor);
                Direction dir = rc.getLocation().directionTo(mopSwingTile.getMapLocation());
                if (rc.canMove(dir)) {
                    rc.move(dir);
                }
            }
        }

        // Fill in any spots in the pattern with the appropriate paint.
        if (!targetTiles.isEmpty()) {
            MapInfo targetTile = targetTiles.get(rng.nextInt(targetTiles.size()));
            boolean useSecondaryColor = targetTile.getMark() == PaintType.ALLY_SECONDARY;
            if (rc.canAttack(targetTile.getMapLocation())) {
                rc.attack(targetTile.getMapLocation(), useSecondaryColor);
                Direction dir = rc.getLocation().directionTo(targetTile.getMapLocation());
                if (rc.canMove(dir)) {
                    rc.move(dir);
                }
            }
        }

        // If a leader
        if (leaderID == -1) {
            // ArrayList<Direction> safeDirections = new ArrayList<>();
            // for (Direction direction : directions) {
            //     MapInfo tile = rc.senseMapInfo(rc.getLocation().add(direction));
            //     if (rc.canMove(direction) && tile.getPaint().isAlly()) {
            //         safeDirections.add(direction);
            //     }
            // }
                rc.move(directions[rng.nextInt(directions.length)]);

            // if (!safeDirections.isEmpty()) {
            //     rc.move(safeDirections.get(rng.nextInt(safeDirections.size())));
            // } else {
            // }
        } else {
            Direction dir = rc.getLocation().directionTo(leaderInfo.getLocation());
            MapInfo tile = rc.senseMapInfo(rc.getLocation().add(dir));

            // Move if safe
            // if (rc.canMove(dir) && tile.getPaint().isAlly()) {
            // rc.move(dir);
            // }
            // Move if safe and not adjacent to the leader (avoid wasting paint)
            if (rc.canMove(dir) && tile.getPaint().isAlly() &&
                    !tile.getMapLocation().isAdjacentTo(leaderInfo.getLocation())) {
                rc.move(dir);
            }
        }
    }

    public static void updateEnemyRobots(RobotController rc) throws GameActionException {
        // Sensing methods can be passed in a radius of -1 to automatically
        // use the largest possible value.
        RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemyRobots.length != 0) {
            rc.setIndicatorString("There are nearby enemy robots! Scary!");
            // Save an array of locations with enemy robots in them for possible future use.
            MapLocation[] enemyLocations = new MapLocation[enemyRobots.length];
            for (int i = 0; i < enemyRobots.length; i++) {
                enemyLocations[i] = enemyRobots[i].getLocation();
            }
            RobotInfo[] allyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
            // Occasionally try to tell nearby allies how many enemy robots we see.
            if (rc.getRoundNum() % 20 == 0) {
                for (RobotInfo ally : allyRobots) {
                    if (rc.canSendMessage(ally.location, enemyRobots.length)) {
                        rc.sendMessage(ally.location, enemyRobots.length);
                    }
                }
            }
        }
    }

    public static RobotInfo senseLeader(RobotController rc) throws GameActionException {
        RobotInfo[] allyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
        int minID = rc.getID();
        RobotInfo leader = null;
        for (RobotInfo ally : allyRobots) {
            if (ally.ID < 10000)
                continue;
            if (minID == -1 || minID > ally.ID) {
                minID = ally.ID;
                leader = ally;
            }
        }
        return leader;
    }

    public static MapLocation calculateBestAoESpot(RobotController rc) throws GameActionException {
        if (!rc.canSenseRobot(-1)) {
            return null;
        }

        RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemyRobots.length == 0) {
            return null;
        } else {
            int bestNearbyEnemy = 1;
            MapLocation enemyLocation = enemyRobots[0].getLocation();
            for (int i = 0; i < enemyRobots.length; i++) {
                int nearbyEnemy = 1;
                MapLocation enemyLocation1 = enemyRobots[i].getLocation();
                for (int j = 0; j < enemyRobots.length; j++) {
                    MapLocation enemyLocation2 = enemyRobots[j].getLocation();
                    if (enemyLocation1.distanceSquaredTo(enemyLocation2) <= 4) {
                        nearbyEnemy++;
                    }
                }
                if (nearbyEnemy > bestNearbyEnemy) {
                    enemyLocation = enemyLocation1;
                }
            }
            return enemyLocation;
        }
    }

    public static MapLocation getEnemyLocationNearby(RobotController rc) throws GameActionException {
        if (!rc.canSenseRobot(-1)) {
            return null;
        } else {
            RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
            if (enemyRobots.length == 0) {
                return null;
            } else {
                return enemyRobots[0].getLocation();
            }
        }
    }

    public static MapLocation getRuinLocationNearby(RobotController rc) throws GameActionException {
        MapInfo[] mapInfos = rc.senseNearbyMapInfos();
        for (MapInfo mapInfo : mapInfos) {
            if (mapInfo.getMark() == PaintType.EMPTY) {
                return mapInfo.getMapLocation();
            }
        }
        return null;
    }

    public static void tryMarkTower(RobotController rc, MapLocation ruinLocation) throws GameActionException {
        UnitType towerType = getRandomTowerType();
        if (rc.senseMapInfo(ruinLocation.subtract(directions[rng.nextInt(8)])).getMark() == PaintType.EMPTY
                && rc.canMarkTowerPattern(towerType, ruinLocation)) {
            rc.markTowerPattern(towerType, ruinLocation);
        }
    }

    public static void tryMarkResource(RobotController rc, MapLocation tileLocation) throws GameActionException {
        if (rc.canMarkResourcePattern(tileLocation)) {
            rc.markResourcePattern(tileLocation);
        }
    }

    public static void tryCompleteTower(RobotController rc, MapLocation ruinLocation) throws GameActionException {
        if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinLocation)) {
            rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinLocation);
        } else if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, ruinLocation)) {
            rc.completeTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, ruinLocation);
        } else if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, ruinLocation)) {
            rc.completeTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, ruinLocation);
        }
    }

    public static void tryCompleteResource(RobotController rc, MapLocation tileLocation) throws GameActionException {
        if (rc.canCompleteResourcePattern(tileLocation)) {
            rc.completeResourcePattern(tileLocation);
        }
    }

    public static UnitType getRandomTowerType() {
        int index = rng.nextInt(3);
        if (index == 0) {
            return UnitType.LEVEL_ONE_PAINT_TOWER;
        } else if (index == 1) {
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        } else {
            return UnitType.LEVEL_ONE_DEFENSE_TOWER;
        }
    }
}
