package SPAARK_RICKROLL;

import battlecode.common.*;

public class Robot {
    public static boolean[][] resourcePattern;
    public static boolean[][][] towerPatterns;
    public static UnitType[] towers = new UnitType[] {
            UnitType.LEVEL_ONE_DEFENSE_TOWER,
            UnitType.LEVEL_ONE_MONEY_TOWER,
            UnitType.LEVEL_ONE_PAINT_TOWER
    };

    public static void init() throws Exception {
        resourcePattern = G.rc.getResourcePattern();
        towerPatterns = new boolean[][][] {
                G.rc.getTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER),
                G.rc.getTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER),
                G.rc.getTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER)
        };
    }

    public static void run() throws Exception {
        paintLost += Math.max(lastPaint - G.rc.getPaint(), 0);
        switch (G.rc.getType()) {
            case MOPPER -> Mopper.run();
            case SOLDIER -> Soldier.run();
            case SPLASHER -> Splasher.run();
            default -> throw new Exception("Challenge Complete! How Did We Get Here?");
        }
        lastPaint = G.rc.getPaint();
        G.indicatorString.append("SYM="
                + (POI.symmetry[0] ? "1" : "0") + (POI.symmetry[1] ? "1" : "0") + (POI.symmetry[2] ? "1 " : "0 "));
        POI.drawIndicators();
    }

    // lastPaint stores how much paint has been lost to neutral/opponent territory
    // used to determine how much paint until retreating
    public static int lastPaint = 0;
    public static int paintLost = 0;

    // retreat calculations
    public static final int RETREAT_PAINT_OFFSET = 10;
    public static final int RETREAT_PAINT_RATIO = 5;

    public static int retreatTower = -1;
    public static StringBuilder triedRetreatTowers = new StringBuilder();

    public static int getRetreatPaint() throws Exception {
        // return Math.max(paintLost + RETREAT_PAINT_OFFSET, G.rc.getType().paintCapacity / RETREAT_PAINT_RATIO);
        return Math.max(paintLost + 20, G.rc.getType().paintCapacity / 4);
    }

    public static void retreat() throws Exception {
        // retreats to an ally tower
        // depends on which information needs to be transmitted and if tower has paint
        // if no paint towers found it should go to chip tower to update POI and find
        // paint tower to retreat to
        paintLost = 0;
        if (retreatTower >= 0) {
            // oopsies tower was replaced
            if (POI.parseTowerTeam(POI.towers[retreatTower]) != G.team) {
                retreatTower = -1;
            }
        }
        if (retreatTower >= 0) {
            // don't retreat to tower with lots of bots surrounding it
            MapLocation loc = POI.parseLocation(POI.towers[retreatTower]);
            if (G.rc.canSenseRobotAtLocation(loc)) {
                G.indicatorString.append("R: " + G.rc.senseNearbyRobots(loc, 2, G.team).length + " ");
                if (G.me.distanceSquaredTo(loc) > 2 && G.rc.senseNearbyRobots(loc, 2, G.team).length > 4) {
                    retreatTower = -1;
                } else {
                    RobotInfo robotInfo = G.rc.senseRobotAtLocation(loc);
                    if (robotInfo.getType().getBaseType() != UnitType.LEVEL_ONE_PAINT_TOWER
                            && robotInfo.getPaintAmount() == 0) {
                        retreatTower = -1;
                    }
                }
            }
        }
        if (retreatTower == -1) {
            int best = -1;
            while (best == -1) {
                int bestDistance = 0;
                boolean bestPaint = false;
                boolean bestCritical = false;
                for (int i = 144; --i >= 0;) {
                    if (POI.towers[i] == -1)
                        break;
                    if (POI.parseTowerTeam(POI.towers[i]) != G.team)
                        continue;
                    // this needs to change
                    boolean paint = POI.parseTowerType(POI.towers[i]) == UnitType.LEVEL_ONE_PAINT_TOWER;
                    if (!paint) {
                        // This is dumb but borks code for some reason
                        continue;
                    }
                    if (triedRetreatTowers.indexOf("" + (char) i) != -1) {
                        continue;
                    }
                    int distance = Motion.getChebyshevDistance(G.me, POI.parseLocation(POI.towers[i]));
                    if (best == -1) {
                        best = i;
                        bestDistance = distance;
                        bestCritical = POI.critical[i];
                        bestPaint = paint;
                    } else if (bestCritical && !POI.critical[i]) {
                        best = i;
                        bestDistance = distance;
                        bestCritical = POI.critical[i];
                        bestPaint = paint;
                    } else if (paint && !bestPaint) {
                        best = i;
                        bestDistance = distance;
                        bestCritical = POI.critical[i];
                        bestPaint = paint;
                    } else if (distance < bestDistance) {
                        best = i;
                        bestDistance = distance;
                        bestCritical = POI.critical[i];
                        bestPaint = paint;
                    }
                }
                if (best == -1) {
                    if (triedRetreatTowers.length() == 0) {
                        retreatTower = -2;
                        break;
                    }
                    triedRetreatTowers = new StringBuilder();
                    continue;
                }
                retreatTower = best;
                triedRetreatTowers.append((char) best);
                break;
            }
        }
        if (retreatTower == -2) {
            // oof no tower
            Motion.exploreRandomly();
            retreatTower = -1;
        } else if (retreatTower != -1) {
            MapLocation loc = POI.parseLocation(POI.towers[retreatTower]);
            Motion.bugnavTowards(loc);
            G.rc.setIndicatorLine(G.me, loc, 200, 0, 200);
            if (G.rc.canSenseRobotAtLocation(loc)) {
                int amt = -Math.min(G.rc.getType().paintCapacity - G.rc.getPaint(),
                        G.rc.senseRobotAtLocation(loc).getPaintAmount());
                if (G.rc.canTransferPaint(loc, amt)) {
                    G.rc.transferPaint(loc, amt);
                }
            }
        }
        G.rc.setIndicatorDot(G.me, 255, 0, 255);
    }
}
