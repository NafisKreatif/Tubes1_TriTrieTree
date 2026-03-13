package SPAARK_RICKROLL;

import battlecode.common.*;

public class Mopper {
    public static MapLocation ruinLocation = null; // BUILD mode
    public static final int EXPLORE = 0;
    public static final int BUILD = 1;
    public static final int RETREAT = 2;
    public static int mode = EXPLORE;
    public static int BUILD_TIMEOUT = 10;
    public static int lastBuild = -BUILD_TIMEOUT;

    static int[] microWeights = new int[8];

    /**
     * Always:
     * If low on paint, retreat
     * Default to explore mode
     * 
     * Explore:
     * Run around randomly deleting enemy paint if it sees it
     * If near a ruin, go to BUILD mode
     * If mop swing works, do it
     * 
     * Build:
     * Help soldiers mop enemy paint around ruins
     */
    public static void run() throws Exception {
        if (G.rc.getPaint() < Robot.getRetreatPaint()) {
            mode = RETREAT;
        } else if (G.rc.getPaint() > G.rc.getType().paintCapacity * 3 / 4 && mode == RETREAT) {
            mode = EXPLORE;
        }
        int a = Clock.getBytecodeNum();
        switch (mode) {
            case EXPLORE -> exploreCheckMode();
            case BUILD -> buildCheckMode();
        }
        int b = Clock.getBytecodeNum();
        G.indicatorString.append((b - a) + " ");
        // grab directions for micro
        for (int i = 8; --i >= 0;) {
            MapLocation m = G.me.add(G.DIRECTIONS[i]);
            if (G.rc.onTheMap(m))
                microWeights[i] = G.rc.senseMapInfo(m).getPaint().isEnemy() ? -10 : 0;
        }
        switch (mode) {
            case EXPLORE -> explore();
            case BUILD -> build();
            case RETREAT -> {
                G.indicatorString.append("RETREAT ");
                Robot.retreat();
            }
        }
        G.indicatorString.append((Clock.getBytecodeNum() - b) + " ");
    }

    public static void exploreCheckMode() throws Exception {
        G.indicatorString.append("CHK_E ");
        // make sure not stuck between exploring and building
        if (lastBuild + BUILD_TIMEOUT < G.round && G.rc.getNumberTowers() < 25) {
            MapLocation[] locs = G.rc.senseNearbyRuins(-1);
            for (MapLocation loc : locs) {
                if (G.rc.canSenseRobotAtLocation(loc)) {
                    continue; // tower already there
                }
                ruinLocation = loc;
                mode = BUILD;
                break;
            }
        }
    }

    public static void buildCheckMode() throws Exception {
        G.indicatorString.append("CHK_B ");
        if (!G.rc.canSenseLocation(ruinLocation) || G.rc.canSenseRobotAtLocation(ruinLocation)
                || G.rc.getNumberTowers() == 25) {
            mode = EXPLORE;
            ruinLocation = null;
        }
    }

    public static void explore() throws Exception {
        G.indicatorString.append("EXPLORE ");
        mopSwingWithMicro();
        MapInfo[] mapInfos = G.rc.senseNearbyMapInfos();
        // will try to unpaint squares under opponent bots
        // but if no opponents, just move to paint and attack
        // store both the best empty tile and the best opponent bot tile
        MapLocation bestBot = null;
        MapLocation bestEmpty = null;
        double bestPaint = -1;
        int bestDist = -1;
        MapLocation microDir = G.me;
        for (int i = mapInfos.length; --i >= 0;) {
            MapInfo info = mapInfos[i];
            MapLocation loc = info.getMapLocation();
            PaintType p = info.getPaint();
            if (p.isEnemy()) {
                microDir = microDir.add(G.me.directionTo(loc));
                if (G.rc.canSenseRobotAtLocation(loc)) {
                    RobotInfo r = G.rc.senseRobotAtLocation(loc);
                    if (r.team == G.opponentTeam) {
                        double paint = r.paintAmount / (double) r.type.paintCapacity;
                        if (paint > bestPaint) {
                            bestPaint = paint;
                            bestBot = loc;
                        }
                    }
                } else {
                    int dist = G.me.distanceSquaredTo(loc);
                    if (bestEmpty == null || G.rc.canAttack(loc) && dist < bestDist) {
                        bestEmpty = loc;
                        bestDist = dist;
                    }
                }
            }
        }
        if (bestEmpty == null && bestBot == null) {
            if (G.me.distanceSquaredTo(microDir) >= 2) {
                Motion.bugnavTowards(microDir, avoidPaintMicro);
            } else {
                G.indicatorString.append("RAND ");
                Motion.exploreRandomly();
            }
        } else {
            if (bestBot != null)
                bestEmpty = bestBot;
            if (G.rc.canAttack(bestEmpty))
                G.rc.attack(bestEmpty);
            Motion.bugnavAround(bestEmpty, 1, 1, avoidPaintMicro);
            G.rc.setIndicatorLine(G.me, bestEmpty, 0, 0, 255);
        }
        if (G.rc.onTheMap(microDir))
            G.rc.setIndicatorLine(G.me, microDir, 0, 200, 255);
        G.rc.setIndicatorDot(G.me, 0, 255, 0);
    }

    public static void build() throws Exception {
        G.indicatorString.append("BUILD ");
        // clean enemy paint for ruin patterns
        // TODO: FIND AND MOP ENEMY PAINT OFF SRP
        // TODO: FIND AND MOP ENEMY PAINT OFF SRP
        // TODO: FIND AND MOP ENEMY PAINT OFF SRP
        // TODO: FIND AND MOP ENEMY PAINT OFF SRP
        // get 2 best locations to build stuff on
        // so if the first one is already there just go to the next one
        MapInfo[] infos = G.rc.senseNearbyMapInfos();
        MapLocation bestLoc = null;
        MapLocation bestLoc2 = null;
        int bestDistanceSquared = 10000;
        int bestDistanceSquared2 = 10001;
        for (MapInfo info : infos) {
            if (info.getMapLocation().distanceSquaredTo(ruinLocation) <= 8 && info.getPaint().isEnemy()) {
                int distanceSquared = info.getMapLocation().distanceSquaredTo(G.me);
                if (distanceSquared < bestDistanceSquared) {
                    bestDistanceSquared2 = bestDistanceSquared;
                    bestLoc2 = bestLoc;
                    bestDistanceSquared = distanceSquared;
                    bestLoc = info.getMapLocation();
                } else if (distanceSquared < bestDistanceSquared2) {
                    bestDistanceSquared2 = distanceSquared;
                    bestLoc2 = info.getMapLocation();
                }
            }
        }
        if (bestLoc != null) {
            if (G.rc.canAttack(bestLoc)) {
                G.rc.attack(bestLoc);
                if (bestLoc2 != null) {
                    Motion.bugnavTowards(bestLoc2, avoidPaintMicro);
                    G.rc.setIndicatorLine(G.me, bestLoc2, 0, 255, 200);
                } else if (G.me.distanceSquaredTo(ruinLocation) <= 4) {
                    mode = EXPLORE;
                    lastBuild = G.round;
                    Motion.exploreRandomly();
                    G.rc.setIndicatorLine(G.rc.getLocation(), ruinLocation, 0, 0, 0);
                    ruinLocation = null;
                } else {
                    Motion.bugnavTowards(ruinLocation, avoidPaintMicro);
                }
            } else {
                Motion.bugnavTowards(bestLoc, avoidPaintMicro);
            }
            G.rc.setIndicatorLine(G.me, bestLoc, 0, 255, 255);
        } else if (G.me.distanceSquaredTo(ruinLocation) <= 4) {
            mode = EXPLORE;
            lastBuild = G.round;
            Motion.exploreRandomly();
            G.rc.setIndicatorLine(G.rc.getLocation(), ruinLocation, 0, 0, 0);
            ruinLocation = null;
        } else {
            Motion.bugnavTowards(ruinLocation, avoidPaintMicro);
        }
        G.rc.setIndicatorDot(G.me, 0, 0, 255);
    }

    /**
     * Attempt mop swing with some microstrategy. Returns if a swing was executed.
     */
    public static boolean mopSwingWithMicro() throws Exception {
        // spaghetti copy paste
        int up = 0, down = 0, left = 0, right = 0;
        RobotInfo r;
        if (G.rc.onTheMap(G.me.add(Direction.NORTH))) {
            r = G.rc.senseRobotAtLocation(G.me.add(Direction.NORTH));
            if (r != null && r.team == G.opponentTeam)
                up++;
        }
        if (G.rc.onTheMap(G.me.add(Direction.NORTHEAST))) {
            r = G.rc.senseRobotAtLocation(G.me.add(Direction.NORTHEAST));
            if (r != null && r.team == G.opponentTeam) {
                up++;
                right++;
            }
        }
        if (G.rc.onTheMap(G.me.add(Direction.EAST))) {
            r = G.rc.senseRobotAtLocation(G.me.add(Direction.EAST));
            if (r != null && r.team == G.opponentTeam)
                right++;
        }
        if (G.rc.onTheMap(G.me.add(Direction.SOUTHEAST))) {
            r = G.rc.senseRobotAtLocation(G.me.add(Direction.SOUTHEAST));
            if (r != null && r.team == G.opponentTeam) {
                down++;
                right++;
            }
        }
        if (G.rc.onTheMap(G.me.add(Direction.SOUTH))) {
            r = G.rc.senseRobotAtLocation(G.me.add(Direction.SOUTH));
            if (r != null && r.team == G.opponentTeam)
                down++;
        }
        if (G.rc.onTheMap(G.me.add(Direction.SOUTHWEST))) {
            r = G.rc.senseRobotAtLocation(G.me.add(Direction.SOUTHWEST));
            if (r != null && r.team == G.opponentTeam) {
                down++;
                left++;
            }
        }
        if (G.rc.onTheMap(G.me.add(Direction.WEST))) {
            r = G.rc.senseRobotAtLocation(G.me.add(Direction.WEST));
            if (r != null && r.team == G.opponentTeam)
                left++;
        }
        if (G.rc.onTheMap(G.me.add(Direction.NORTHWEST))) {
            r = G.rc.senseRobotAtLocation(G.me.add(Direction.NORTHWEST));
            if (r != null && r.team == G.opponentTeam) {
                up++;
                left++;
            }
        }
        if (up >= 3)
            G.rc.mopSwing(Direction.NORTH);
        else if (down >= 3)
            G.rc.mopSwing(Direction.SOUTH);
        else if (left >= 3)
            G.rc.mopSwing(Direction.WEST);
        else if (right >= 3)
            G.rc.mopSwing(Direction.EAST);
        else
            return false;
        return true;
    }

    public static Micro avoidPaintMicro = new Micro() {
        @Override
        public int[] micro(Direction d, MapLocation dest) throws Exception {
            // REALLY avoid being on enemy paint
            int[] scores = Motion.defaultMicro.micro(d, dest);
            for (int i = 8; --i >= 0;) {
                scores[i] += microWeights[i];
            }
            return scores;
        }
    };
}
