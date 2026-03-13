package SPAARK_RICKROLL;

import battlecode.common.*;
import java.util.*;

public class Tower {
    public static int spawnedSoldiers = 0;
    public static int spawnedSplashers = 0;
    public static int spawnedMoppers = 0;
    public static int spawnedRobots = 0;

    public static MapLocation[] spawnLocs;
    public static int level;

    public static void init() throws Exception {
        spawnLocs = new MapLocation[] {
                G.me.add(Direction.NORTH),
                G.me.add(Direction.NORTH).add(Direction.NORTH),
                G.me.add(Direction.NORTHEAST),
                G.me.add(Direction.EAST),
                G.me.add(Direction.EAST).add(Direction.EAST),
                G.me.add(Direction.SOUTHEAST),
                G.me.add(Direction.SOUTH),
                G.me.add(Direction.SOUTH).add(Direction.SOUTH),
                G.me.add(Direction.SOUTHWEST),
                G.me.add(Direction.WEST),
                G.me.add(Direction.WEST).add(Direction.WEST),
                G.me.add(Direction.NORTHWEST)
        };
        Arrays.sort(spawnLocs,
                (MapLocation a, MapLocation b) -> a.distanceSquaredTo(G.mapCenter) - b.distanceSquaredTo(G.mapCenter));
    }

    public static void run() throws Exception {
        // general common code for all towers
        // spawning
        if (spawnedRobots == 0) {
            for (MapLocation loc : spawnLocs) {
                if (G.rc.canBuildRobot(UnitType.SOLDIER, loc)) {
                    G.rc.buildRobot(UnitType.SOLDIER, loc);
                    spawnedRobots++;
                    spawnedSoldiers++;
                    break;
                }
            }
        } else if (spawnedRobots == 1) {
            for (MapLocation loc : spawnLocs) {
                if (G.rc.canBuildRobot(UnitType.MOPPER, loc)) {
                    G.rc.buildRobot(UnitType.MOPPER, loc);
                    spawnedRobots++;
                    spawnedMoppers++;
                    break;
                }
            }
        } else if (G.rc.getNumberTowers() > 2 || G.round > 50) {
            // don't suffocate money until we built a tower
            switch (spawnedRobots % 5) {
                // make sure to subtract 2
                case 0:
                    for (MapLocation loc : spawnLocs) {
                        if (G.rc.canBuildRobot(UnitType.MOPPER, loc)) {
                            G.rc.buildRobot(UnitType.MOPPER, loc);
                            spawnedRobots++;
                            spawnedMoppers++;
                            break;
                        }
                    }
                    break;
                case 1:
                    for (MapLocation loc : spawnLocs) {
                        if (G.rc.canBuildRobot(UnitType.SPLASHER, loc)) {
                            G.rc.buildRobot(UnitType.SPLASHER, loc);
                            spawnedRobots++;
                            spawnedSplashers++;
                            break;
                        }
                    }
                    break;
                case 2:
                    for (MapLocation loc : spawnLocs) {
                        if (G.rc.canBuildRobot(UnitType.SOLDIER, loc)) {
                            G.rc.buildRobot(UnitType.SOLDIER, loc);
                            spawnedRobots++;
                            spawnedSoldiers++;
                            break;
                        }
                    }
                    break;
                case 3:
                    for (MapLocation loc : spawnLocs) {
                        if (G.rc.canBuildRobot(UnitType.SPLASHER, loc)) {
                            G.rc.buildRobot(UnitType.SPLASHER, loc);
                            spawnedRobots++;
                            spawnedSplashers++;
                            break;
                        }
                    }
                    break;
                default:
                    for (MapLocation loc : spawnLocs) {
                        if (G.rc.canBuildRobot(UnitType.MOPPER, loc)) {
                            G.rc.buildRobot(UnitType.MOPPER, loc);
                            spawnedRobots++;
                            spawnedMoppers++;
                            break;
                        }
                    }
                    break;
            }
        }
        // more specialized here
        switch (G.rc.getType()) {
            case LEVEL_ONE_DEFENSE_TOWER -> {
                level = 0;
                DefenseTower.run();
            }
            case LEVEL_TWO_DEFENSE_TOWER -> {
                level = 1;
                DefenseTower.run();
            }
            case LEVEL_THREE_DEFENSE_TOWER -> {
                level = 2;
                DefenseTower.run();
            }
            case LEVEL_ONE_MONEY_TOWER -> {
                level = 0;
                MoneyTower.run();
            }
            case LEVEL_TWO_MONEY_TOWER -> {
                level = 1;
                MoneyTower.run();
            }
            case LEVEL_THREE_MONEY_TOWER -> {
                level = 2;
                MoneyTower.run();
            }
            case LEVEL_ONE_PAINT_TOWER -> {
                level = 0;
                PaintTower.run();
            }
            case LEVEL_TWO_PAINT_TOWER -> {
                level = 1;
                PaintTower.run();
            }
            case LEVEL_THREE_PAINT_TOWER -> {
                level = 2;
                PaintTower.run();
            }
            default -> throw new Exception("Challenge Complete! How Did We Get Here?");
        }
        while (G.rc.canUpgradeTower(G.me) && G.rc.getMoney() - G.rc.getType().moneyCost >= 5000) {
            G.rc.upgradeTower(G.me);
        }
        // attack AFTER run (in case we get an upgrade)
        MapLocation bestEnemyLoc = null;
        int bestEnemyHp = 1000000;
        UnitType bestEnemyType = UnitType.MOPPER;
        // priority: soldier, splasher, mopper
        int numAttackableRobots = 0;
        for (int i = G.opponentRobots.length; --i >= 0;) {
            RobotInfo r = G.opponentRobots[i];
            if (G.rc.canAttack(r.location)) {
                numAttackableRobots++;
                switch (bestEnemyType) {
                    case UnitType.MOPPER:
                        if (r.type == UnitType.SOLDIER || r.type == UnitType.SPLASHER || r.health < bestEnemyHp) {
                            bestEnemyHp = r.health;
                            bestEnemyLoc = r.location;
                            bestEnemyType = r.type;
                        }
                        break;
                    case UnitType.SPLASHER:
                        if (r.type == UnitType.SOLDIER || (r.type == UnitType.SPLASHER && r.health < bestEnemyHp)) {
                            bestEnemyHp = r.health;
                            bestEnemyLoc = r.location;
                            bestEnemyType = r.type;
                        }
                        break;
                    case UnitType.SOLDIER:
                        if (r.type == UnitType.SOLDIER && r.health < bestEnemyHp) {
                            bestEnemyHp = r.health;
                            bestEnemyLoc = r.location;
                        }
                        break;
                    default:
                        break;
                }
            }
        }
        if (numAttackableRobots > 2 && G.rc.canAttack(null)) {
            G.rc.attack(null);
        } else if (G.rc.canAttack(bestEnemyLoc)) {
            G.rc.attack(bestEnemyLoc);
        }
        calcRickroll();
    }

    public static void calcRickroll() throws Exception {
        int ox = G.rc.getMapWidth() / 2 - 10;
        int oy = G.rc.getMapHeight() / 2 - 10;
        MapLocation origin = new MapLocation(ox, oy);
        int p = Image.data.length - 1;
        int q = Image.data[p].length - 1;
        int[] px;
        int r, g, b;
        MapLocation loc = origin.translate(q, p);
        int[][] row = Image.data[Image.data.length - 1];
        G.rc.setIndicatorDot(loc, row[q][0], row[q][1], row[q][2]);
        for (int k = q; --k >= 0;) {
            px = row[k];
            r = px[0];
            g = px[1];
            b = px[2];
            loc = origin.translate(k, p);
            G.rc.setIndicatorDot(loc, row[k][0], row[k][1], row[k][2]);
            G.rc.setIndicatorLine(loc, loc.add(Direction.EAST), r, g, b);
        }
        for (int i = Image.data.length - 1; --i >= 0;) {
            row = Image.data[i];
            px = row[q];
            r = px[0];
            g = px[1];
            b = px[2];
            loc = origin.translate(q, i);
            G.rc.setIndicatorDot(loc, r, g, b);
            G.rc.setIndicatorLine(loc, loc.add(Direction.NORTH), r, g, b);
            for (int j = q; --j >= 0;) {
                px = row[j];
                r = px[0];
                g = px[1];
                b = px[2];
                loc = origin.translate(j, i);
                G.rc.setIndicatorDot(loc, r, g, b);
                G.rc.setIndicatorLine(loc, loc.add(Direction.NORTH), r, g, b);
                G.rc.setIndicatorLine(loc, loc.add(Direction.NORTHEAST), r, g, b);
                G.rc.setIndicatorLine(loc, loc.add(Direction.EAST), r, g, b);
                G.rc.setIndicatorLine(loc.add(Direction.NORTH), loc.add(Direction.EAST), r, g, b);
            }
        }
    }
}
