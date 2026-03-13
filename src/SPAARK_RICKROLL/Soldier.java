package SPAARK_RICKROLL;

import battlecode.common.*;

public class Soldier {
    public static MapLocation ruinLocation = null; // BUILD_TOWER mode
    public static int buildTowerType = 0;
    public static UnitType towerType = null; // ATTACK mode
    public static MapLocation towerLocation = null; // ATTACK mode
    public static MapLocation resourceLocation = null; // BUILD_RESOURCE mode

    // allowed marker locations
    // fills entire vision range + buffer for checking when adjacent
    // we can set everything outside of 7x7 to true but may be bad for
    // checkerboarding (misaligned checkerboard blocks future SRPs)
    public static final boolean[][] allowedSrpMarkerLocations = new boolean[][] {
            { false, false, false, false, false, false, false, false, false, false, false },
            { false, false, false, true, false, true, false, true, false, false, false },
            { false, false, true, false, true, false, true, false, true, false, false },
            { false, true, false, true, false, true, false, true, false, true, false },
            { false, false, true, false, false, false, false, false, true, false, false },
            { false, true, false, true, false, false, false, true, false, true, false },
            { false, false, true, false, false, false, false, false, true, false, false },
            { false, true, false, true, false, true, false, true, false, true, false },
            { false, false, true, false, true, false, true, false, true, false, false },
            { false, false, false, true, false, true, false, true, false, false, false },
            { false, false, false, false, false, false, false, false, false, false, false },
    };

    // queue of next locations to check for expanding SRP
    // used in explore mode to mark initial build since needs centered for markers
    // (goes into expand mode, reaches the target location, and starts building)
    public static MapLocation[] srpCheckLocations = new MapLocation[] {};
    public static int srpCheckIndex = 0;

    public static final int EXPLORE = 0;
    public static final int BUILD_TOWER = 1;
    public static final int BUILD_RESOURCE = 2;
    public static final int EXPAND_RESOURCE = 3;
    public static final int ATTACK = 4;
    public static final int RETREAT = 5;
    public static int mode = EXPLORE;

    // ratio of paint necessary to exit retreat mode
    public static final float RETREAT_PAINT_RATIO = 0.75f;
    // exploration weight multiplier
    public static final int EXPLORE_OPP_WEIGHT = 5;
    // controls rounds between visiting ruins (G.lastVisited)
    public static final int VISIT_TIMEOUT = 40;
    // don't build SRP for first few rounds, prioritize towers
    public static final int MIN_SRP_ROUND = 5;
    // controls rounds between repairing/expanding SRP
    public static final int SRP_VISIT_TIMEOUT = 20;
    // have at most TOWER_CEIL for the first TOWER_CEIL rounds, if map small
    public static final int TOWER_CEIL = 3;
    public static final int TOWER_CEIL_MAP_AREA = 1600;
    public static final int TOWER_CEIL_ROUND = 75;
    // encourages building SRPs if waiting for chips on large maps initially
    public static final int INITIAL_SRP_ALT_MAP_AREA = 1600;
    public static final int INITIAL_SRP_ALT_TOWER_CAP = 6;
    public static final int INITIAL_SRP_ALT_CHIPS = 300;
    // ignore being near ruins for SRPs for some rounds, sometimes necessary
    public static final int INITIAL_SRP_RUIN_IGNORE = 50;
    // don't expand SRP if low on paint, since very slow
    public static final int EXPAND_SRP_MIN_PAINT = 75;

    public static MapLocation[] nearbyRuins;
    // map nearby map infos into 2d array in (y, x) form
    // used for tower building, SRP detection, expansion, building
    public static MapInfo[][] mapInfos = new MapInfo[9][9];

    // TEMP
    // TEMP
    // TEMP
    // TEMP
    // TEMP
    // TEMP
    // TODO: WILL REMOVE, HERE FOR COMPATIBILITY
    public static final boolean[][] seenSrpMarkers = new boolean[9][9];

    /**
     * Always:
     * If low on paint, retreat
     * Default to explore mode
     * 
     * Explore:
     * Run around randomly while painting below self, maybe check sus POI stuff
     * If seeing opponent tower or ruin, go to attack/tower build mode
     * - tower build mode then checks if actually needed, may switch back to explore
     * - exception for tower ceiling, to place SRPs to help build towers later
     * If sees SRP, queue location of SRP and enter SRP expand mode
     * - Queue once, SRP build will repair if needed, otherwise switch to expand
     * Else, if can build SRP adjacent, queue location and enter SRP expand mode
     * - SRPs won't be built overlapping with tower 5x5 squares
     * 
     * Build tower:
     * If pattern is complete but tower not completed, leave lowest ID to complete
     * Place stuff
     * If can't complete tower, low coins, few towers, large map, return to explore,
     * other bot will finish tower later (see if SRP is possible)
     * Complete tower, return to explore
     * 
     * Build SRP:
     * Place SRP
     * Complete SRP, queue 8 optimal expansion locations and enter SRP expand mode
     * - 4 expansions for 2 chiralities (flipped pattern too)
     * 
     * Expand SRP:
     * Go to queued locations of expansion and see if can build (fix race condition)
     * If can build
     * - Place 4 secondary markers in box around center and primary marker at center
     * - Enter SRP build mode
     * CALLS exploreCheckMode() IF CAN'T BUILD, MAY RUN OUT OF BYTECODE?
     * 
     * Attack:
     * Attack tower until ded lmao
     * Attempt to repair SRPs if not in range to attack tower
     */
    public static void run() throws Exception {
        if (G.rc.getPaint() < Robot.getRetreatPaint()) {
            mode = RETREAT;
        } else if (mode == RETREAT && G.rc.getPaint() > G.rc.getType().paintCapacity * RETREAT_PAINT_RATIO) {
            mode = EXPLORE;
        }
        nearbyRuins = G.rc.senseNearbyRuins(-1);
        // map
        int miDx = 4 - G.me.x, miDy = 4 - G.me.y;
        for (int i = G.nearbyMapInfos.length; --i >= 0;) {
            MapLocation loc = G.nearbyMapInfos[i].getMapLocation().translate(miDx, miDy);
            mapInfos[loc.y][loc.x] = G.nearbyMapInfos[i];
        }
        int a = Clock.getBytecodeNum();
        switch (mode) {
            case EXPLORE -> exploreCheckMode();
            case BUILD_TOWER -> buildTowerCheckMode();
            case BUILD_RESOURCE -> buildResourceCheckMode();
            case EXPAND_RESOURCE -> expandResourceCheckMode();
            case ATTACK -> attackCheckMode();
        }
        int b = Clock.getBytecodeNum();
        G.indicatorString.append((b - a) + " ");
        switch (mode) {
            case EXPLORE -> explore();
            case BUILD_TOWER -> buildTower();
            case BUILD_RESOURCE -> buildResource();
            case EXPAND_RESOURCE -> expandResource();
            case ATTACK -> attack();
            case RETREAT -> {
                G.indicatorString.append("RETREAT ");
                Robot.retreat();
            }
        }
        G.indicatorString.append((Clock.getBytecodeNum() - b) + " ");
    }

    public static void exploreCheckMode() throws Exception {
        G.indicatorString.append("CHK_E ");
        // switch modes if seeing towers/ruins
        for (int i = nearbyRuins.length; --i >= 0;) {
            MapLocation loc = nearbyRuins[i];
            if (G.rc.canSenseRobotAtLocation(loc)) {
                RobotInfo bot = G.rc.senseRobotAtLocation(loc);
                if (bot.team == G.opponentTeam
                        && bot.type.actionRadiusSquared <= G.rc.getType().actionRadiusSquared) {
                    towerLocation = loc;
                    towerType = bot.type;
                    mode = ATTACK;
                    return;
                }
            } else if (G.rc.getNumberTowers() < 25
                    && (G.round > TOWER_CEIL_ROUND || G.rc.getNumberTowers() <= TOWER_CEIL)) {
                // TOWER_CEIL encourages building SRPs to help build more towers
                ruinLocation = loc;
                mode = BUILD_TOWER;
                // if the tower doesn't need more help it'll return to explore mode
                // need to do this because of lowest ID check - bot may leave if other bot with
                // lower ID comes, but may not swap roles to build the tower
                buildTowerCheckMode();
                return;
            }
        }
        // has bytecode checks because uses lots of bytecode
        if (Clock.getBytecodesLeft() < 12000) {
            G.indicatorString.append("!CHK-SRP1-BTCODE ");
        } else if (G.rc.getPaint() > EXPAND_SRP_MIN_PAINT) {
            // search for SRP markers for building/repairing/expanding
            // big mapping very buh lots of bytecode
            int meX = G.me.x, meY = G.me.y;
            // TODO: YEET ALL OF THIS
            // TODO: YEET ALL OF THIS
            // TODO: YEET ALL OF THIS
            // TODO: YEET ALL OF THIS
            // TODO: YEET ALL OF THIS
            // TODO: YEET ALL OF THIS
            // TODO: YEET ALL OF THIS
            // TODO: YEET ALL OF THIS
            // TODO: YEET ALL OF THIS
            // TODO: YEET ALL OF THIS
            // TODO: YEET ALL OF THIS
            // detect SRP expansion only within a certain range of soldiers, otherwise
            // soldiers too eager to build SRPs

            // this block takes roughly 2.5k bytecde TEMP TEMP TEMP TEMP TEMP TEMP
            for (int i = G.nearbyMapInfos.length; --i >= 0;) {
                MapLocation loc = G.nearbyMapInfos[i].getMapLocation();
                // stupid vscode formatting
                seenSrpMarkers[loc.y - meY + 4][loc.x - meX + 4] = G.nearbyMapInfos[i]
                        .getMark() == PaintType.ALLY_SECONDARY;
            }
            // don't check for SRP on edges of vision (from 1-7)
            searchExpansion: for (int i = 0; i++ < 7;) {
                // this block takes roughly 3k bytecde TEMP TEMP TEMP TEMP TEMP TEMP
                for (int j = 1; j++ < 7;) {
                    // middle + 4 box corners
                    if (seenSrpMarkers[j][i] && seenSrpMarkers[j - 1][i - 1] && seenSrpMarkers[j - 1][i + 1]
                            && seenSrpMarkers[j + 1][i + 1] && seenSrpMarkers[j + 1][i - 1]) {
                        MapLocation loc = G.me.translate(i - 4, j - 4);
                        if (G.rc.onTheMap(loc) && G.getLastVisited(loc.x, loc.y) + SRP_VISIT_TIMEOUT < G.round) {
                            mode = EXPAND_RESOURCE;
                            // SRP expand will enter SRP build, which may repair if needed before expanding
                            srpCheckLocations = new MapLocation[] { loc };
                            srpCheckIndex = 0;
                            // we have to do this or may have bugs
                            expandResourceCheckMode();
                            break searchExpansion;
                        }
                    }
                }
                if (Clock.getBytecodesLeft() < 8000) {
                    G.indicatorString.append("!CHK-SRP2-BTCODE ");
                    return;
                }
            }
            // only if can't expand SRP build nearby
            if (Clock.getBytecodesLeft() < 8000) {
                G.indicatorString.append("!CHK-SRP3-BTCODE ");
                return;
            }
        }
        // TODO: FIND WAY TO RUN THIS WITHOUT UNDERMINING TOWER BUILD LOGIC
        // TODO: maybe dont build SRP if near explore target?
        if (G.round > MIN_SRP_ROUND) {
            // see if SRP is possible nearby
            // TODO: THIS MAKES rickroll OP????????????
            for (int i = 9; --i >= 8;) { // CHANGE THIS BACK TO 9-0!!
                MapLocation loc = G.me.add(G.ALL_DIRECTIONS[i]);
                if (canBuildSRPHere(loc, false)) {
                    // TODO: prioritize lining up checkerboards
                    srpCheckLocations = new MapLocation[] { loc };
                    srpCheckIndex = 0;
                    mode = EXPAND_RESOURCE;
                    expandResourceCheckMode();
                    break;
                }
            }
        }
    }

    public static void buildTowerCheckMode() throws Exception {
        G.indicatorString.append("CHK_BTW ");
        G.setLastVisited(ruinLocation.x, ruinLocation.y, G.round);
        buildTowerType = predictTowerType(ruinLocation);
        // if tower already built leave tower build mode
        if (!G.rc.canSenseLocation(ruinLocation) || G.rc.canSenseRobotAtLocation(ruinLocation)
                || G.rc.getNumberTowers() == 25) {
            mode = EXPLORE;
            ruinLocation = null;
            return;
        }
        // if pattern complete leave lowest bot ID to complete
        boolean isPatternComplete = true;
        // do this instead of iterating through nearby map infos
        checkPattern: for (int dx = -1; dx++ < 4;) {
            for (int dy = -1; dy++ < 4;) {
                if (dx == 2 && dy == 2)
                    continue;
                MapLocation loc = ruinLocation.translate(dx - 2, dy - 2);
                // location guaranteed to be on the map, unless ruinLocation isn't a ruin
                // also guaranteed can be sensed if can action there
                // TODO: REPLACE WITH mapInfos
                // TODO: REPLACE WITH mapInfos
                // TODO: REPLACE WITH mapInfos
                // TODO: REPLACE WITH mapInfos
                // TODO: REPLACE WITH mapInfos
                // TODO: REPLACE WITH mapInfos
                if (G.rc.canSenseLocation(loc)
                        && (Robot.towerPatterns[buildTowerType][dx][dy] ? PaintType.ALLY_SECONDARY
                                : PaintType.ALLY_PRIMARY) != G.rc.senseMapInfo(loc).getPaint()) {
                    isPatternComplete = false;
                    break checkPattern;
                }
            }
        }
        if (isPatternComplete) {
            for (int i = G.allyRobots.length; --i >= 0;) {
                if (G.allyRobots[i].getLocation().isWithinDistanceSquared(ruinLocation, 8)) {
                    if (G.allyRobots[i].ID < G.rc.getID()) {
                        // not lowest ID, leave
                        mode = EXPLORE;
                        ruinLocation = null;
                        return;
                    }
                }
            }
        }
        // is lowest id, maybe try later if chips low, towers few, and map large
        if (G.rc.getChips() <= INITIAL_SRP_ALT_CHIPS && G.rc.getNumberTowers() <= INITIAL_SRP_ALT_TOWER_CAP
                && G.mapArea >= INITIAL_SRP_ALT_MAP_AREA) {
            mode = EXPLORE;
            ruinLocation = null;
        }
    }

    public static void buildResourceCheckMode() throws Exception {
        G.indicatorString.append("CHK_BRP ");
        // POSSIBLY HAVE TO REMOVE MARKERS IF INTERFERING?
        // shouldn't happen though?
    }

    public static void expandResourceCheckMode() throws Exception {
        G.indicatorString.append("CHK_ERP ");
        MapLocation target = srpCheckLocations[srpCheckIndex];
        // shouldn't interfere with towers, since SRP adjacent to ruin impossible
        G.setLastVisited(target.x, target.y, G.round);
        while (!G.rc.onTheMap(target)) {
            srpCheckIndex++;
            if (srpCheckIndex >= srpCheckLocations.length) {
                mode = EXPLORE;
                if (Clock.getBytecodesLeft() > 10000)
                    exploreCheckMode();
                return;
            }
            target = srpCheckLocations[srpCheckIndex];
        }
        // tiny optimization, saves like 1 turn
        if (G.me.isWithinDistanceSquared(target, 1)) {
            // have to be within 1 tile lmao
            if (!canBuildSRPHere(target, true)) {
                srpCheckIndex++;
                if (srpCheckIndex >= srpCheckLocations.length) {
                    mode = EXPLORE;
                    if (Clock.getBytecodesLeft() > 10000)
                        exploreCheckMode();
                    return;
                }
            }
        }
        if (G.me.equals(target) && canBuildSRPHere(G.me, true)) {
            resourceLocation = G.me;
            // markers
            G.rc.mark(G.me.add(Direction.NORTHWEST), true);
            G.rc.mark(G.me.add(Direction.NORTHEAST), true);
            G.rc.mark(G.me.add(Direction.SOUTHWEST), true);
            G.rc.mark(G.me.add(Direction.SOUTHEAST), true);
            G.rc.mark(G.me, true);
            G.indicatorString.append("MK_SRP ");
            mode = BUILD_RESOURCE;
        }
    }

    public static void attackCheckMode() throws Exception {
        G.indicatorString.append("CHK_ATK ");
        // nothing for now
    }

    public static void explore() throws Exception {
        G.indicatorString.append("EXPLORE ");
        // find towers from POI to attack/build out of vision
        MapLocation bestLoc = null;
        // TODO: EXPLORE BLANK AREAS AWAY FROM TOWERS AND POI
        // TODO: we leave too much map empty
        // TODO: try using random explore more
        // TODO: maybe even edit random explore to avoid known POI
        // TODO: since POI will be checked on without random
        // TOWER_CEIL encourages building SRPs to help build more towers
        if (G.round > TOWER_CEIL_ROUND || G.rc.getNumberTowers() <= TOWER_CEIL) {
            int bestDistanceSquared = 10000;
            for (int i = 144; --i >= 0;) {
                if (POI.towers[i] == -1) {
                    break;
                }
                if (POI.parseTowerTeam(POI.towers[i]) == G.opponentTeam) {
                    // attack these
                    MapLocation pos = POI.parseLocation(POI.towers[i]);
                    if (G.me.isWithinDistanceSquared(pos, bestDistanceSquared)
                            && (G.round <= VISIT_TIMEOUT || G.getLastVisited(pos.x, pos.y) + VISIT_TIMEOUT < G.round)) {
                        bestDistanceSquared = G.me.distanceSquaredTo(pos);
                        bestLoc = pos;
                    }
                } else if (POI.parseTowerTeam(POI.towers[i]) == Team.NEUTRAL && G.rc.getNumberTowers() < 25) {
                    // having 25 towers otherwise just softlocks the bots
                    MapLocation pos = POI.parseLocation(POI.towers[i]);
                    // prioritize opponent towers more than ruins, so it has to be REALLY close
                    if (G.me.isWithinDistanceSquared(pos, bestDistanceSquared / EXPLORE_OPP_WEIGHT)
                            && (G.round <= VISIT_TIMEOUT || G.getLastVisited(pos.x, pos.y) + VISIT_TIMEOUT < G.round)) {
                        bestDistanceSquared = G.me.distanceSquaredTo(pos) * EXPLORE_OPP_WEIGHT; // lol
                        bestLoc = pos;
                    }
                }
            }
        }
        if (bestLoc == null) {
            Motion.exploreRandomly();
        } else {
            Motion.bugnavTowards(bestLoc);
            G.rc.setIndicatorLine(G.me, bestLoc, 255, 255, 0);
        }
        MapInfo me = G.rc.senseMapInfo(G.me);
        // place paint under self to avoid passive paint drain if possible
        if (me.getPaint() == PaintType.EMPTY && G.rc.canAttack(G.me)) {
            // determine which checkerboard pattern to copy
            int[] cnt = new int[] { 0, 0 };
            for (int i = G.nearbyMapInfos.length; --i >= 0;) {
                if (G.nearbyMapInfos[i].getPaint() == PaintType.ALLY_SECONDARY) {
                    cnt[(G.nearbyMapInfos[i].getMapLocation().x + G.nearbyMapInfos[i].getMapLocation().y) & 1]++;
                }
            }
            G.rc.attack(G.me, cnt[(G.me.x + G.me.y) & 1] > cnt[(1 + G.me.x + G.me.y) & 1]);
        }
        G.rc.setIndicatorDot(G.me, 0, 255, 0);
    }

    public static void buildTower() throws Exception {
        G.indicatorString.append("BUILD_TW ");
        MapLocation paintLocation = null; // so indicator drawn to bot instead of previous position
        // do this instead of iterating through nearby map infos
        boolean[][] towerPattern = Robot.towerPatterns[buildTowerType];
        for (int dx = -1; dx++ < 4;) {
            for (int dy = -1; dy++ < 4;) {
                if (dx == 2 && dy == 2)
                    continue;
                MapLocation loc = ruinLocation.translate(dx - 2, dy - 2);
                // location guaranteed to be on the map, unless ruinLocation isn't a ruin
                // also guaranteed can be sensed if can action there
                if (G.rc.canAttack(loc)) {
                    boolean paint = towerPattern[dx][dy];
                    PaintType exist = G.rc.senseMapInfo(loc).getPaint();
                    // can't paint enemy paint
                    if (!exist.isEnemy() && (paint ? PaintType.ALLY_SECONDARY : PaintType.ALLY_PRIMARY) != exist) {
                        G.rc.attack(loc, paint);
                        paintLocation = loc;
                        break;
                    }
                }
            }
        }
        if (G.rc.canCompleteTowerPattern(Robot.towers[buildTowerType], ruinLocation) && G.rc.getPaint() > 50) {
            G.rc.completeTowerPattern(Robot.towers[buildTowerType], ruinLocation);
            POI.addTower(-1, POI.intifyTower(G.team, Robot.towers[buildTowerType]) | POI.intifyLocation(ruinLocation));
            mode = EXPLORE;
            Motion.exploreRandomly();
            // dot to signal building complete
            G.rc.setIndicatorDot(ruinLocation, 255, 200, 0);
            ruinLocation = null;
        } else {
            Motion.bugnavAround(ruinLocation, 1, 2);
            G.rc.setIndicatorLine(G.rc.getLocation(), ruinLocation, 255, 200, 0);
        }
        if (paintLocation != null)
            G.rc.setIndicatorLine(G.me, paintLocation, 200, 100, 0);
        G.rc.setIndicatorDot(G.me, 0, 0, 255);
    }

    public static void buildResource() throws Exception {
        G.indicatorString.append("BUILD_RP ");
        // MUCH IS IDENTICAL TO TOWER BUILD CODE
        MapLocation paintLocation = null; // so indicator drawn to bot instead of previous position
        // do this instead of iterating through nearby map infos
        for (int dx = -1; dx++ < 4;) {
            for (int dy = -1; dy++ < 4;) {
                MapLocation loc = resourceLocation.translate(dx - 2, dy - 2);
                // location guaranteed to be on the map by canBuildSrpHere calls
                // also guaranteed can be sensed if can action there
                if (G.rc.canAttack(loc)) {
                    boolean paint = Robot.resourcePattern[dx][dy];
                    PaintType exist = G.rc.senseMapInfo(loc).getPaint();
                    // can't paint enemy paint
                    if (!exist.isEnemy() && (paint ? PaintType.ALLY_SECONDARY : PaintType.ALLY_PRIMARY) != exist) {
                        G.rc.attack(loc, paint);
                        paintLocation = loc;
                        break;
                    }
                }
            }
        }
        if (G.rc.canCompleteResourcePattern(resourceLocation)) {
            G.rc.completeResourcePattern(resourceLocation);
            if (G.rc.getPaint() < EXPAND_SRP_MIN_PAINT) {
                // early retreat since painting more is very slow
                mode = RETREAT;
            } else {
                // put the 4 optimal locations of next pattern into a queue
                // that the bot then pathfinds to and checks if can build pattern
                mode = EXPAND_RESOURCE;
                // checks both chiralities
                srpCheckLocations = new MapLocation[] {
                        resourceLocation.translate(3, 1), resourceLocation.translate(3, -1),
                        resourceLocation.translate(1, -3), resourceLocation.translate(-1, -3),
                        resourceLocation.translate(-3, -1), resourceLocation.translate(-3, 1),
                        resourceLocation.translate(-1, 3), resourceLocation.translate(1, 3)
                };
                srpCheckIndex = 0;
            }
            Motion.exploreRandomly();
            // dot to signal building complete
            G.rc.setIndicatorDot(resourceLocation, 255, 200, 0);
            resourceLocation = null;
        } else {
            Motion.bugnavAround(resourceLocation, 0, 2);
            G.rc.setIndicatorLine(G.rc.getLocation(), resourceLocation, 255, 100, 0);
        }
        if (paintLocation != null)
            G.rc.setIndicatorLine(G.me, paintLocation, 200, 100, 0);
        G.rc.setIndicatorDot(G.me, 0, 200, 255);
    }

    public static void expandResource() throws Exception {
        G.indicatorString.append("EXPAND_RP ");
        Motion.bugnavTowards(srpCheckLocations[srpCheckIndex]);
        // show the queue and current target
        for (int i = srpCheckLocations.length; --i >= srpCheckIndex;) {
            // dots guaranteed to be on map because of expandResourceCheckMode
            G.rc.setIndicatorDot(srpCheckLocations[i], 200, 100, 150);
        }
        G.rc.setIndicatorLine(G.me, srpCheckLocations[srpCheckIndex], 255, 0, 150);
        G.rc.setIndicatorDot(G.me, 0, 200, 255);
    }

    public static void attack() throws Exception {
        G.indicatorString.append("ATTACK ");
        // attack micro moment
        if (towerLocation.isWithinDistanceSquared(G.me, towerType.actionRadiusSquared)) {
            if (G.rc.canAttack(towerLocation))
                G.rc.attack(towerLocation);
            Motion.bugnavAway(towerLocation, attackMicro);
        } else {
            if (G.rc.isActionReady()) {
                Motion.bugnavTowards(towerLocation, attackMicro);
                if (G.rc.canAttack(towerLocation))
                    G.rc.attack(towerLocation);
            } else {
                Motion.bugnavAround(towerLocation, towerType.actionRadiusSquared + 1,
                        towerType.actionRadiusSquared + 1);
            }
        }
        G.rc.setIndicatorDot(G.me, 255, 0, 0);
    }

    /**
     * Can build map at location - DOES NOT CHECK IF SENSE LOCATIONS ARE VALID!!!
     * Only call this with adjacent locations to bot!!! (any 8 cardinal directions)
     */
    public static boolean canBuildSRPHere(MapLocation me, boolean allowRepair) throws Exception {
        // make sure 5x5 square clear first
        for (int i = -3; ++i <= 2;) {
            for (int j = -3; ++j <= 2;) {
                MapLocation loc = me.translate(j, i);
                if (!G.rc.onTheMap(loc) || !G.rc.sensePassability(loc))
                    return false;
            }
        }
        // check for towers that could interfere
        // ignore at first for better SRPs, then only avoid ruins (built towers ok)
        if (G.round > INITIAL_SRP_RUIN_IGNORE) {
            for (int i = nearbyRuins.length; --i >= 0;) {
                if (Math.abs(G.me.x - nearbyRuins[i].x) <= 4 && Math.abs(G.me.y - nearbyRuins[i].y) <= 4
                        && !G.rc.canSenseRobotAtLocation(nearbyRuins[i]))
                    return false;
            }
        }
        // TODO: REPLACE WITH mapInfos
        // TODO: REPLACE WITH mapInfos
        // TODO: REPLACE WITH mapInfos
        // TODO: REPLACE WITH mapInfos
        // TODO: REPLACE WITH mapInfos
        // check meshing with nearby SRPs
        for (int i = G.nearbyMapInfos.length; --i >= 0;) {
            // can't have markers without spots but can have spots without markers
            if ((G.nearbyMapInfos[i].getMark() == PaintType.ALLY_SECONDARY)) {
                MapLocation loc = G.nearbyMapInfos[i].getMapLocation();
                if (!allowedSrpMarkerLocations[loc.y - me.y + 5][loc.x - me.x + 5])
                    return false;
            }
        }
        return true;
    }

    public static Micro attackMicro = new Micro() {
        @Override
        public int[] micro(Direction d, MapLocation dest) throws Exception {
            // try to stay out of range if on cd, otherwise try to get in range
            int[] scores = Motion.defaultMicro.micro(d, dest);
            if (G.rc.isActionReady()) {
                for (int i = 8; --i >= 0;) {
                    if (G.me.add(G.DIRECTIONS[i])
                            .isWithinDistanceSquared(towerLocation, G.rc.getType().actionRadiusSquared)) {
                        scores[i] += 40;
                    }
                }
            } else {
                for (int i = 8; --i >= 0;) {
                    if (!G.me.add(G.DIRECTIONS[i])
                            .isWithinDistanceSquared(towerLocation, towerType.actionRadiusSquared)) {
                        scores[i] += 40;
                    }
                }
            }
            return scores;
        }
    };

    public static int predictTowerType(MapLocation xy) {
        if (G.rc.getNumberTowers() % 2 == 1)
            return 2;
        return 1;
    }
}