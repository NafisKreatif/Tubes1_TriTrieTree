package SPAARK_RICKROLL;

import battlecode.common.*;

public class Motion {
    public static final boolean ENABLE_EXPLORE_INDICATORS = false;

    public static final int TOWARDS = 0;
    public static final int AWAY = 1;
    public static final int AROUND = 2;
    public static final int NONE = 0;
    public static final int CLOCKWISE = 1;
    public static final int COUNTER_CLOCKWISE = -1;

    public static final int DEFAULT_RETREAT_HP = 999;

    public static Direction lastDir = Direction.CENTER;
    public static Direction optimalDir = Direction.CENTER;
    public static int rotation = NONE;
    public static int circleDirection = CLOCKWISE;

    public static Direction lastRandomDir = Direction.CENTER;
    public static MapLocation lastRandomSpread;

    // common distance stuff
    public static int getManhattanDistance(MapLocation a, MapLocation b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }

    public static int getChebyshevDistance(MapLocation a, MapLocation b) {
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.y - b.y));
    }

    public static MapLocation getClosest(MapLocation[] a) throws Exception {
        return getClosest(a, G.rc.getLocation());
    }

    public static MapLocation getClosest(MapLocation[] a, MapLocation me) throws Exception {
        /* Get closest MapLocation to me (Euclidean) */
        MapLocation closest = a[0];
        int distance = me.distanceSquaredTo(a[0]);
        for (int i = a.length; --i >= 0;) {
            MapLocation loc = a[i];
            if (me.distanceSquaredTo(loc) < distance) {
                closest = loc;
                distance = me.distanceSquaredTo(loc);
            }
        }
        return closest;
    }

    public static MapLocation getClosestPair(MapLocation[] a, MapLocation[] b) throws Exception {
        /* Get closest pair (Euclidean) */
        MapLocation closest = a[0];
        int distance = b[0].distanceSquaredTo(a[0]);
        for (int i = a.length; --i >= 0;) {
            MapLocation loc = a[i];
            for (int j = b.length; --j >= 0;) {
                MapLocation loc2 = b[i];
                if (loc2.distanceSquaredTo(loc) < distance) {
                    closest = loc;
                    distance = loc2.distanceSquaredTo(loc);
                }
            }
        }
        return closest;
    }

    public static MapLocation getFarthest(MapLocation[] a) throws Exception {
        /* Get farthest MapLocation to this robot (Euclidean) */
        return getFarthest(a, G.rc.getLocation());
    }

    public static MapLocation getFarthest(MapLocation[] a, MapLocation me) throws Exception {
        /* Get farthest MapLocation to me (Euclidean) */
        MapLocation closest = a[0];
        int distance = me.distanceSquaredTo(a[0]);
        for (int i = a.length; --i >= 0;) {
            MapLocation loc = a[i];
            if (me.distanceSquaredTo(loc) > distance) {
                closest = loc;
                distance = me.distanceSquaredTo(loc);
            }
        }
        return closest;
    }

    // basic random movement
    public static void moveRandomly() throws Exception {
        if (G.rc.isMovementReady()) {
            boolean stuck = true;
            for (int i = G.DIRECTIONS.length; --i >= 0;) {
                if (G.rc.canMove(G.DIRECTIONS[i])) {
                    stuck = false;
                }
            }
            if (stuck) {
                return;
            }
            // move in a random direction but minimize making useless moves back to where
            // you came from
            Direction direction = G.DIRECTIONS[G.rng.nextInt(G.DIRECTIONS.length)];
            if (direction == lastRandomDir.opposite() && G.rc.canMove(direction.opposite())) {
                direction = direction.opposite();
            }
            if (move(direction)) {
                lastRandomDir = direction;
            }
        }
    }

    public static void spreadRandomly() throws Exception {
        boolean stuck = true;
        for (int i = G.DIRECTIONS.length; --i >= 0;) {
            if (canMove(G.DIRECTIONS[i])) {
                stuck = false;
            }
        }
        if (stuck) {
            return;
        }
        if (G.rc.isMovementReady()) {
            MapLocation target = G.me;
            for (int i = G.allyRobots.length; --i >= 0;) {
                // ignore towers
                if (!G.allyRobots[i].type.isRobotType())
                    target = target.subtract(G.me.directionTo(G.allyRobots[i].getLocation()));
            }
            for (int i = 8; --i >= 0;) {
                if (!G.rc.canMove(G.DIRECTIONS[i])) {
                    target = target.subtract(G.DIRECTIONS[i]);
                }
            }
            if (target.equals(G.me)) {
                // just keep moving in the same direction as before if there's no robots nearby
                if (G.round % 3 == 0 || lastRandomSpread == null) {
                    moveRandomly(); // occasionally move randomly to avoid getting stuck
                } else if (G.rng.nextInt(20) == 1) {
                    // don't get stuck in corners
                    lastRandomSpread = G.me.add(G.DIRECTIONS[G.rng.nextInt(G.DIRECTIONS.length)]);
                    moveRandomly();
                } else {
                    // Direction direction = bug2Helper(me, lastRandomSpread, TOWARDS, 0, 0);
                    Direction direction = G.me.directionTo(target);
                    if (move(direction)) {
                        lastRandomSpread = lastRandomSpread.add(direction);
                        lastRandomDir = direction;
                    } else {
                        moveRandomly();
                    }
                }
                lastDir = Direction.CENTER;
                optimalDir = Direction.CENTER;
            } else {
                if (lastDir == G.me.directionTo(target)) {
                    lastDir = Direction.CENTER;
                }
                Direction direction = bug2Helper(G.me, target, TOWARDS, 0, 0);
                if (move(direction)) {
                    lastRandomSpread = target;
                    lastRandomDir = direction;
                }
            }
        }
    }

    public static MapLocation exploreLoc;

    public static void exploreRandomly() throws Exception {
        if (G.rc.isMovementReady()) {
            if (exploreLoc != null) {
                if (G.rc.canSenseLocation(exploreLoc)) {
                    exploreLoc = null;
                }
                if (G.rng.nextInt(25) == 0) {
                    exploreLoc = null;
                }
            }
            // don't explore in direction of a lot of allied bots
            MapLocation otherBots = G.me;
            for (int i = G.allyRobots.length; --i >= 0;) {
                otherBots = otherBots.add(G.me.directionTo(G.allyRobots[i].getLocation()));
            }
            if (!G.me.isWithinDistanceSquared(otherBots, 36)
                    && Math.abs(G.me.directionTo(otherBots).compareTo(G.me.directionTo(otherBots))) <= 1) {
                exploreLoc = null;
            }
            if (exploreLoc == null) {
                //pick a random location that we haven't seen before
                int sum = G.rc.getMapHeight()*G.rc.getMapWidth();
                for (int i = G.rc.getMapHeight(); --i >= 0;) {
                    sum -= Long.bitCount(POI.explored[i]);
                }
                int rand = G.rng.nextInt(sum);
                int cur = 0;
                for (int i = G.rc.getMapHeight(); --i >= 0;) {
                    cur += G.rc.getMapWidth() - Long.bitCount(POI.explored[i]);
                    if (cur > rand) {
                        rand -= cur - (G.rc.getMapWidth() - Long.bitCount(POI.explored[i]));
                        int cur2 = 0;
                        for (int b = G.rc.getMapWidth(); --b >= 0;) {
                            if (((POI.explored[i] >> b) & 1) == 0) {
                                if (++cur2 > rand) {
                                    exploreLoc = new MapLocation(b, i);
                                    break;
                                }
                            }
                        }
                        break;
                    }
                }
            }
            bugnavTowards(exploreLoc);
            if (ENABLE_EXPLORE_INDICATORS)
                G.rc.setIndicatorLine(G.me, exploreLoc, 0, 200, 0);
        }
    }

    // bugnav helpers

    public static MapLocation bugnavTarget;
    public static int bugnavMode = -1;

    public static int minDistanceToTarget;
    public static int maxDistanceFromTarget;
    public static int minCircleDistance;
    public static int maxCircleDistance;
    public static boolean obstacleOnRight;
    public static MapLocation currentObstacle;
    public static StringBuilder visitedList = new StringBuilder();

    // public static Direction bug2Helper(MapLocation target, int mode, int minCircleDistance1, int maxCircleDistance1)
    //         throws Exception {
    //     boolean stuck = true;
    //     for (int i = 8; --i >= 0;) {
    //         if (G.rc.canMove(G.DIRECTIONS[i])) {
    //             stuck = false;
    //             break;
    //         }
    //     }

    //     if (stuck) {
    //         return Direction.CENTER;
    //     }

    //     if (bugnavTarget == null || !bugnavTarget.equals(target) || bugnavMode != mode) {
    //         reset();
    //     }
    //     bugnavTarget = target;
    //     bugnavMode = mode;
    //     minCircleDistance = minCircleDistance1;
    //     maxCircleDistance = maxCircleDistance1;

    //     int distanceToTarget = getChebyshevDistance(G.me, target);
    //     switch (bugnavMode) {
    //         case TOWARDS:
    //             if (distanceToTarget < minDistanceToTarget) {
    //                 reset();
    //                 minDistanceToTarget = distanceToTarget;
    //             }
    //             break;
    //         case AWAY:
    //             if (distanceToTarget > maxDistanceFromTarget) {
    //                 reset();
    //                 maxDistanceFromTarget = distanceToTarget;
    //             }
    //             break;
    //         case AROUND:
    //             // kind of approximation
    //             // probably wont circle around something with very large radius?
    //             int dist = G.me.distanceSquaredTo(bugnavTarget);
    //             if (dist < minCircleDistance) {
    //                 if (distanceToTarget > maxDistanceFromTarget) {
    //                     reset();
    //                     maxDistanceFromTarget = distanceToTarget;
    //                 }
    //             } else if (dist > maxCircleDistance) {
    //                 if (distanceToTarget < minDistanceToTarget) {
    //                     reset();
    //                     minDistanceToTarget = distanceToTarget;
    //                 }
    //             }
    //             break;
    //     }

    //     if (currentObstacle != null && G.rc.canSenseLocation(currentObstacle)
    //             && G.rc.sensePassability(currentObstacle)) {
    //         reset();
    //     }

    //     if (visitedList.indexOf("" + getState()) != -1) {
    //         reset();
    //     }
    //     visitedList.append("" + getState());

    //     Direction targetDirection = getTargetDirection();

    //     if (currentObstacle == null) {
    //         if (G.rc.canMove(targetDirection)) {
    //             return targetDirection;
    //         }

    //         setInitialDirection(targetDirection);
    //     }

    //     return followWall(true);
    // }

    // public static void reset() {
    //     minDistanceToTarget = Integer.MAX_VALUE;
    //     maxDistanceFromTarget = 0;
    //     obstacleOnRight = true;
    //     currentObstacle = null;
    //     visitedList = new StringBuilder();
    // }

    // public static Direction getTargetDirection() throws Exception {
    //     if (G.me.equals(bugnavTarget)) {
    //         if (bugnavMode == AROUND) {
    //             return Direction.EAST;
    //         } else {
    //             return Direction.CENTER;
    //         }
    //     }
    //     Direction direction = G.me.directionTo(bugnavTarget);
    //     switch (bugnavMode) {
    //         case AWAY:
    //             direction = direction.opposite();
    //             break;
    //         case AROUND:
    //             int dist = G.me.distanceSquaredTo(bugnavTarget);
    //             if (dist < minCircleDistance) {
    //                 direction = direction.opposite();
    //             } else if (dist <= maxCircleDistance) {
    //                 direction = direction.rotateLeft().rotateLeft();
    //                 if (circleDirection == COUNTER_CLOCKWISE) {
    //                     direction = direction.opposite();
    //                 }

    //                 if (!G.rc.canMove(direction)) {
    //                     direction = direction.opposite();
    //                     circleDirection *= -1;
    //                 }
    //             }
    //             break;
    //     }
    //     return direction;
    // }

    // public static void setInitialDirection(Direction forward) throws Exception {
    //     Direction left = forward.rotateLeft();
    //     for (int i = 8; --i >= 0;) {
    //         MapLocation location = G.rc.adjacentLocation(left);
    //         if (G.rc.onTheMap(location) && G.rc.sensePassability(location)) {
    //             break;
    //         }

    //         left = left.rotateLeft();
    //     }

    //     Direction right = forward.rotateRight();
    //     for (int i = 8; --i >= 0;) {
    //         MapLocation location = G.rc.adjacentLocation(right);
    //         if (G.rc.onTheMap(location) && G.rc.sensePassability(location)) {
    //             break;
    //         }

    //         right = right.rotateRight();
    //     }

    //     // TODO: add paint weightings

    //     MapLocation leftLocation = G.rc.adjacentLocation(left);
    //     MapLocation rightLocation = G.rc.adjacentLocation(right);

    //     int leftDistance = getChebyshevDistance(leftLocation, bugnavTarget);
    //     int rightDistance = getChebyshevDistance(rightLocation, bugnavTarget);

    //     if (leftDistance < rightDistance) {
    //         obstacleOnRight = true;
    //     } else if (rightDistance < leftDistance) {
    //         obstacleOnRight = false;
    //     } else {
    //         obstacleOnRight = G.me.distanceSquaredTo(leftLocation) < G.me.distanceSquaredTo(rightLocation);
    //     }

    //     if (obstacleOnRight) {
    //         currentObstacle = G.rc.adjacentLocation(left.rotateRight());
    //     } else {
    //         currentObstacle = G.rc.adjacentLocation(right.rotateLeft());
    //     }
    // }

    // public static Direction followWall(boolean canRotate) throws Exception {
    //     Direction direction = G.rc.getLocation().directionTo(currentObstacle);

    //     for (int i = 8; --i >= 0;) {
    //         direction = obstacleOnRight ? direction.rotateLeft() : direction.rotateRight();
    //         if (G.rc.canMove(direction)) {
    //             return direction;
    //         }

    //         MapLocation location = G.rc.adjacentLocation(direction);
    //         if (canRotate && !G.rc.onTheMap(location)) {
    //             obstacleOnRight = !obstacleOnRight;
    //             return followWall(false);
    //         }

    //         if (G.rc.onTheMap(location) && !G.rc.sensePassability(location)) {
    //             currentObstacle = location;
    //         }
    //     }
    //     return Direction.CENTER;
    // }

    // public static char getState() {
    //     Direction direction = G.me.directionTo(currentObstacle != null ? currentObstacle : bugnavTarget);
    //     int rotation = obstacleOnRight ? 1 : 0;

    //     return (char) ((((G.me.x << 6) | G.me.y) << 4) | (direction.ordinal() << 1) | rotation);
    // }

    public static int[] simulateMovement(MapLocation me, MapLocation dest) throws Exception {
        MapLocation clockwiseLoc = G.rc.getLocation();
        Direction clockwiseLastDir = lastDir;
        int clockwiseStuck = 0;
        MapLocation counterClockwiseLoc = G.rc.getLocation();
        Direction counterClockwiseLastDir = lastDir;
        int counterClockwiseStuck = 0;
        search: for (int t = 0; t < 10; t++) {
            // search: for (int t = 0; t < 2; t++) {
            if (clockwiseLoc.equals(dest)) {
                break;
            }
            if (counterClockwiseLoc.equals(dest)) {
                break;
            }
            Direction clockwiseDir = clockwiseLoc.directionTo(dest);
            {
                for (int i = 9; --i >= 0;) {
                    MapLocation loc = clockwiseLoc.add(clockwiseDir);
                    if (G.rc.onTheMap(loc)) {
                        if (!G.rc.canSenseLocation(loc)) {
                            break search;
                        }
                        if (clockwiseDir != clockwiseLastDir.opposite() && G.rc.senseMapInfo(loc).isPassable()
                                && G.rc.senseRobotAtLocation(loc) == null) {
                            clockwiseLastDir = clockwiseDir;
                            break;
                        }
                    }
                    clockwiseDir = clockwiseDir.rotateRight();
                    if (i == 7) {
                        clockwiseStuck = 1;
                        break search;
                    }
                }
            }
            Direction counterClockwiseDir = counterClockwiseLoc.directionTo(dest);
            {
                for (int i = 9; --i >= 0;) {
                    MapLocation loc = counterClockwiseLoc.add(counterClockwiseDir);
                    if (G.rc.onTheMap(loc)) {
                        if (!G.rc.canSenseLocation(loc)) {
                            break search;
                        }
                        if (counterClockwiseDir != counterClockwiseLastDir.opposite()
                                && G.rc.senseMapInfo(loc).isPassable() && G.rc.senseRobotAtLocation(loc) == null) {
                            counterClockwiseLastDir = counterClockwiseDir;
                            break;
                        }
                    }
                    counterClockwiseDir = counterClockwiseDir.rotateLeft();
                    if (i == 7) {
                        counterClockwiseStuck = 1;
                        break search;
                    }
                }
            }
            clockwiseLoc = clockwiseLoc.add(clockwiseDir);
            counterClockwiseLoc = counterClockwiseLoc.add(counterClockwiseDir);
        }

        int clockwiseDist = clockwiseLoc.distanceSquaredTo(dest);
        int counterClockwiseDist = counterClockwiseLoc.distanceSquaredTo(dest);

        return new int[] { clockwiseDist, clockwiseStuck, counterClockwiseDist, counterClockwiseStuck };
    }

    public static Direction bug2Helper(MapLocation me, MapLocation dest, int mode, int minRadiusSquared,
            int maxRadiusSquared) throws Exception {
        Direction direction = me.directionTo(dest);
        if (me.equals(dest)) {
            if (mode == AROUND) {
                direction = Direction.EAST;
            } else {
                return Direction.CENTER;
            }
        }
        if (mode == AWAY) {
            direction = direction.opposite();
        } else if (mode == AROUND) {
            if (me.distanceSquaredTo(dest) < minRadiusSquared) {
                direction = direction.opposite();
            } else if (me.distanceSquaredTo(dest) <= maxRadiusSquared) {
                direction = direction.rotateLeft().rotateLeft();
                if (circleDirection == COUNTER_CLOCKWISE) {
                    direction = direction.opposite();
                }
            }
            lastDir = Direction.CENTER;
        }

        boolean stuck = true;
        for (int i = 4; --i >= 0;) {
            String m = me + " " + i + " ";
            if (!visitedList.toString().contains(m)) {
                visitedList.append(m);
                stuck = false;
                break;
            }
        }
        if (stuck) {
            moveRandomly();
            visitedList = new StringBuilder();
            return Direction.CENTER;
        }

        // G.indicatorString.append("DIR=" + direction + " ");
        if (optimalDir != Direction.CENTER && mode != AROUND) {
            if (G.rc.canMove(optimalDir) && lastDir != optimalDir.opposite()) {
                optimalDir = Direction.CENTER;
                rotation = NONE;
                visitedList = new StringBuilder();
            } else {
                direction = optimalDir;
            }
        }
        // G.indicatorString.append("OPTIMAL=" + optimalDir + " ");

        // G.indicatorString.append("CIRCLE: " + circleDirection + " ");
        // G.indicatorString.append("DIR: " + direction + " ");
        // G.indicatorString.append("OFF: " + G.rc.onTheMap(me.add(direction)) + " ");

        if (lastDir != direction.opposite()) {
            if (G.rc.canMove(direction)) {
                // if (!lastBlocked) {
                // rotation = NONE;
                // }
                // lastBlocked = false;
                // boolean touchingTheWallBefore = false;
                // for (int i = DIRECTIONS.length; --i>=0;) {
                // MapLocation translatedMapLocation = me.add(d);
                // if (G.rc.onTheMap(translatedMapLocation)) {
                // if (!G.rc.senseMapInfo(translatedMapLocation).isPassable()) {
                // touchingTheWallBefore = true;
                // break;
                // }
                // }
                // }
                // if (touchingTheWallBefore) {
                // rotation = NONE;
                // }
                return direction;
            }
        } else if (G.rc.canMove(direction)) {
            Direction dir;
            if (rotation == CLOCKWISE) {
                dir = direction.rotateRight();
            } else {
                dir = direction.rotateLeft();
            }
            if (!G.rc.onTheMap(me.add(dir))) {
                // boolean touchingTheWallBefore = false;
                // for (int i = DIRECTIONS.length; --i>=0;) {
                // MapLocation translatedMapLocation = me.add(d);
                // if (G.rc.onTheMap(translatedMapLocation)) {
                // if (!G.rc.senseMapInfo(translatedMapLocation).isPassable()) {
                // touchingTheWallBefore = true;
                // break;
                // }
                // }
                // }
                // if (touchingTheWallBefore) {
                // rotation = NONE;
                // }
                rotation *= -1;
                return direction;
            }
        }
        if (!G.rc.onTheMap(me.add(direction))) {
            if (mode == AROUND) {
                circleDirection *= -1;
                direction = direction.opposite();
                // G.indicatorString.append("FLIPPED ");
            } else {
                direction = me.directionTo(dest);
            }
            if (G.rc.canMove(direction)) {
                return direction;
            }
        }

        if (optimalDir == Direction.CENTER) {
            optimalDir = direction;
        }

        // G.indicatorString.append("ROTATION=" + rotation + " ");
        if (rotation == NONE) {
            // if (G.rng.nextInt(2) == 0) {
            // rotation = CLOCKWISE;
            // } else {
            // rotation = COUNTER_CLOCKWISE;
            // }
            int[] simulated = simulateMovement(me, dest);

            int clockwiseDist = simulated[0];
            int counterClockwiseDist = simulated[2];
            boolean clockwiseStuck = simulated[1] == 1;
            boolean counterClockwiseStuck = simulated[3] == 1;

            // G.indicatorString.append("DIST=" + clockwiseDist + " " +
            // counterClockwiseDist
            // + " ");
            int tempMode = mode;
            if (mode == AROUND) {
                if (clockwiseDist < minRadiusSquared) {
                    if (counterClockwiseDist < minRadiusSquared) {
                        tempMode = AWAY;
                    } else {
                        tempMode = AWAY;
                    }
                } else {
                    if (counterClockwiseDist < minRadiusSquared) {
                        tempMode = AWAY;
                    } else {
                        tempMode = TOWARDS;
                    }
                }
            }
            if (clockwiseStuck) {
                rotation = COUNTER_CLOCKWISE;
            } else if (counterClockwiseStuck) {
                rotation = CLOCKWISE;
            } else if (tempMode == TOWARDS) {
                if (clockwiseDist < counterClockwiseDist) {
                    rotation = CLOCKWISE;
                } else {
                    rotation = COUNTER_CLOCKWISE;
                }
            } else if (tempMode == AWAY) {
                if (clockwiseDist < counterClockwiseDist) {
                    rotation = COUNTER_CLOCKWISE;
                } else {
                    rotation = CLOCKWISE;
                }
            }
        }

        boolean flip = false;
        for (int i = 8; --i >= 0;) {
            if (rotation == CLOCKWISE) {
                direction = direction.rotateRight();
            } else {
                direction = direction.rotateLeft();
            }
            if (!G.rc.onTheMap(me.add(direction))) {
                flip = true;
            }
            // if (G.rc.onTheMap(me.add(direction)) &&
            // G.rc.senseMapInfo(me.add(direction)).isPassable() && lastDir !=
            // direction.opposite()) {
            // if (G.rc.canMove(direction)) {
            // return direction;
            // }
            // return Direction.CENTER;
            // }
            if (G.rc.canMove(direction) && lastDir != direction.opposite()) {
                if (flip) {
                    rotation *= -1;
                }
                if (G.rc.canMove(direction)) {
                    return direction;
                }
                return Direction.CENTER;
            }
        }
        if (flip) {
            rotation *= -1;
        }
        if (G.rc.canMove(lastDir.opposite())) {
            return lastDir.opposite();
        }
        return Direction.CENTER;
    }

    // bugnav

    static int total = 0;
    static int turns = 0;

    // IMPORTANT: bugnav takes around 1100 bytecode

    public static void bugnavTowards(MapLocation dest) throws Exception {
        // int a = Clock.getBytecodeNum();
        bugnavTowards(dest, defaultMicro);
        // total += Clock.getBytecodeNum() - a;
        turns++;
        // G.indicatorString.append("BUG-BT" + (total / turns) + " ");
        // G.indicatorString.append("BUG-BT" + (Clock.getBytecodeNum() - a) + " ");
    }

    public static void bugnavTowards(MapLocation dest, Micro m) throws Exception {
        if (G.rc.isMovementReady()) {
            Direction d = bug2Helper(G.rc.getLocation(), dest, TOWARDS, 0, 0);
            // Direction d = bug2Helper(dest, TOWARDS, 0, 0);
            // what is purpose of this v
            if (d == Direction.CENTER) {
                d = G.rc.getLocation().directionTo(dest);
            }
            int[] weights = m.micro(d, dest);
            int best = 8;
            for (int i = 9; --i >= 0;) {
                if (weights[i] > weights[best])
                    best = i;
            }
            if (best != 8)
                move(G.ALL_DIRECTIONS[best]);
        }
    }

    public static void bugnavAway(MapLocation dest) throws Exception {
        bugnavAway(dest, defaultMicro);
    }

    public static void bugnavAway(MapLocation dest, Micro m) throws Exception {
        if (G.rc.isMovementReady()) {
            Direction d = bug2Helper(G.me, dest, AWAY, 0, 0);
            if (d == Direction.CENTER) {
                d = G.rc.getLocation().directionTo(dest);
            }
            int[] weights = m.micro(d, dest);
            int best = 8;
            for (int i = 9; --i >= 0;) {
                if (weights[i] > weights[best])
                    best = i;
            }
            if (best != 8)
                move(G.ALL_DIRECTIONS[best]);
        }
    }

    public static void bugnavAround(MapLocation dest, int minRadiusSquared, int maxRadiusSquared) throws Exception {
        bugnavAround(dest, minRadiusSquared, maxRadiusSquared, defaultMicro);
    }

    public static void bugnavAround(MapLocation dest, int minRadiusSquared, int maxRadiusSquared, Micro m)
            throws Exception {
        if (G.rc.isMovementReady()) {
            Direction d = bug2Helper(G.rc.getLocation(), dest, AROUND, minRadiusSquared, maxRadiusSquared);
            // Direction d = bug2Helper(dest, AROUND, minRadiusSquared, maxRadiusSquared);
            if (d == Direction.CENTER) {
                d = G.rc.getLocation().directionTo(dest);
            }
            int[] weights = m.micro(d, dest);
            int best = 8;
            for (int i = 9; --i >= 0;) {
                if (weights[i] > weights[best])
                    best = i;
            }
            if (best != 8)
                move(G.ALL_DIRECTIONS[best]);
        }
    }

    public static MapLocation bfsDest;
    public static long[] bfsMap;
    public static long[] bfsDist;
    public static long[] bfsCurr;
    public static long bitmask;
    // public static StringBuilder bfsQueue = new StringBuilder();
    public static final int MAX_PATH_LENGTH = 100;

    public static void bfsInit() {
        width = G.rc.getMapWidth();
        height = G.rc.getMapHeight();
        bfsMap = new long[height + 2];
        bfsCurr = new long[height + 2];
        bfsDist = new long[(height + 2) * MAX_PATH_LENGTH];
        bitmask = (long1 << width) - 1;
    }

    public static int step = 1;
    public static int stepOffset;
    public static int width;
    public static int height;
    public static long long1 = 1;
    public static int recalculationNeeded = MAX_PATH_LENGTH;

    public static void updateBfsMap() throws Exception {
        MapInfo[] map = G.rc.senseNearbyMapInfos();
        for (int i = map.length; --i >= 0;) {
            MapInfo m = map[i];
            if (m.isWall()) {
                int loc = m.getMapLocation().y + 1;
                int subloc = m.getMapLocation().x;
                if (((bfsMap[loc] >> subloc) & 1) == 0) {
                    bfsMap[loc] |= (long1 << subloc);
                    G.rc.setIndicatorDot(m.getMapLocation(), 255, 255, 255);
                    for (int j = step - 1; j >= 0; j--) {
                        if (((bfsDist[j * (height + 2) + loc] >> subloc) & 1) != 1) {
                            recalculationNeeded = Math.min(j, recalculationNeeded);
                            break;
                        }
                    }
                }
            }
        }
    }

    public static void bfs() throws Exception {

        if (recalculationNeeded != MAX_PATH_LENGTH && recalculationNeeded < step) {
            step = recalculationNeeded;
            for (int i = 1; i <= height; i++) {
                // bfsDist[i] = 0;
                // bfsCurr[i] = 0;
                bfsCurr[i] = bfsDist[step * (height + 2) + i];
            }
            step += 1;
            G.indicatorString.append("BFS-RECALC ");
        }
        recalculationNeeded = MAX_PATH_LENGTH;

        while (step < MAX_PATH_LENGTH && Clock.getBytecodesLeft() > 5000) {
            stepOffset = step * (height + 2);
            switch (height) {
                case 20:
                    MotionCodeGen.bfs20();
                    break;
                case 21:
                    MotionCodeGen.bfs21();
                    break;
                case 22:
                    MotionCodeGen.bfs22();
                    break;
                case 23:
                    MotionCodeGen.bfs23();
                    break;
                case 24:
                    MotionCodeGen.bfs24();
                    break;
                case 25:
                    MotionCodeGen.bfs25();
                    break;
                case 26:
                    MotionCodeGen.bfs26();
                    break;
                case 27:
                    MotionCodeGen.bfs27();
                    break;
                case 28:
                    MotionCodeGen.bfs28();
                    break;
                case 29:
                    MotionCodeGen.bfs29();
                    break;
                case 30:
                    MotionCodeGen.bfs30();
                    break;
                case 31:
                    MotionCodeGen.bfs31();
                    break;
                case 32:
                    MotionCodeGen.bfs32();
                    break;
                case 33:
                    MotionCodeGen.bfs33();
                    break;
                case 34:
                    MotionCodeGen.bfs34();
                    break;
                case 35:
                    MotionCodeGen.bfs35();
                    break;
                case 36:
                    MotionCodeGen.bfs36();
                    break;
                case 37:
                    MotionCodeGen.bfs37();
                    break;
                case 38:
                    MotionCodeGen.bfs38();
                    break;
                case 39:
                    MotionCodeGen.bfs39();
                    break;
                case 40:
                    MotionCodeGen.bfs40();
                    break;
                case 41:
                    MotionCodeGen.bfs41();
                    break;
                case 42:
                    MotionCodeGen.bfs42();
                    break;
                case 43:
                    MotionCodeGen.bfs43();
                    break;
                case 44:
                    MotionCodeGen.bfs44();
                    break;
                case 45:
                    MotionCodeGen.bfs45();
                    break;
                case 46:
                    MotionCodeGen.bfs46();
                    break;
                case 47:
                    MotionCodeGen.bfs47();
                    break;
                case 48:
                    MotionCodeGen.bfs48();
                    break;
                case 49:
                    MotionCodeGen.bfs49();
                    break;
                case 50:
                    MotionCodeGen.bfs50();
                    break;
                case 51:
                    MotionCodeGen.bfs51();
                    break;
                case 52:
                    MotionCodeGen.bfs52();
                    break;
                case 53:
                    MotionCodeGen.bfs53();
                    break;
                case 54:
                    MotionCodeGen.bfs54();
                    break;
                case 55:
                    MotionCodeGen.bfs55();
                    break;
                case 56:
                    MotionCodeGen.bfs56();
                    break;
                case 57:
                    MotionCodeGen.bfs57();
                    break;
                case 58:
                    MotionCodeGen.bfs58();
                    break;
                case 59:
                    MotionCodeGen.bfs59();
                    break;
                case 60:
                    MotionCodeGen.bfs60();
                    break;
            }
            // var cod = "";
            // for (var i = 30; i <= 60; i++) {
            // cod += "public static void bfs" + i + "() {\n";
            // for (var j = 1; j <= i; j++) {
            // cod += "Motion.bfsCurr[z] = Motion.bfsCurr[z] | (Motion.bfsCurr[z] >> 1) |
            // (Motion.bfsCurr[z] << 1);\n".replaceAll("z", j);
            // }
            // for (var j = 1; j <= i; j++) {
            // cod += "Motion.bfsDist[Motion.stepOffset + z] = (Motion.bfsCurr[z] |
            // Motion.bfsCurr[y] | Motion.bfsCurr[x]) & (Motion.bitmask ^
            // Motion.bfsMap[z]);\n".replaceAll("z", j).replaceAll("y", j -
            // 1).replaceAll("x", j + 1);
            // }
            // for (var j = 1; j <= i; j++) {
            // //cod += "Motion.bfsDist[Motion.stepOffset + z] &= Motion.bitmask ^
            // Motion.bfsMap[z];\n".replaceAll("z", j);
            // }
            // for (var j = 1; j <= i; j++) {
            // cod += "Motion.bfsCurr[z] = Motion.bfsDist[Motion.stepOffset +
            // z];\n".replaceAll("z", j);
            // }
            // cod += "}\n";
            // }
            // console.log(cod);
            step += 1;
        }

        // int b = G.round % width;
        // if (G.round == 201) {
        // for (int i = 0; i < width; i++) {
        // b = i;
        // for (int j = 0; j < height; j++) {
        // // if (((bfsDist[(G.round % 100) * (height + 2) + j + 1] >> i) &
        // 1) == 0) {
        // if (((bfsDist[(G.round % 100) * (height + 2) + j + 1] >> b) & 1)
        // == 0) {
        // if (((bfsMap[j + 1] >> b) & 1) == 0) {
        // G.rc.setIndicatorDot(new MapLocation(b, j), 255, 0, 0);
        // }
        // else {
        // G.rc.setIndicatorDot(new MapLocation(b, j), 0, 0, 0);
        // }
        // }
        // else {
        // if (((bfsMap[j + 1] >> b) & 1) == 0) {
        // G.rc.setIndicatorDot(new MapLocation(b, j), 255, 255, 255);
        // }
        // else {
        // G.rc.setIndicatorDot(new MapLocation(b, j), 0, 255, 0);
        // }
        // }
        // }
        // }
        // }
        G.indicatorString.append("BFS-STP=" + step + " ");
    }

    public static Direction getBfsDirection(MapLocation dest) throws Exception {
        boolean[] directions = new boolean[9];
        for (int i = 1; i < step; i++) {
            if (((bfsDist[i * (height + 2) + 1 + G.me.y] >> G.me.x) & 1) == 1) {
                if (((bfsDist[(i - 1) * (height + 2) + 1 + G.me.y - 1] >> G.me.x) & 1) == 1) {
                    directions[7] = true;
                }
                if (((bfsDist[(i - 1) * (height + 2) + 1 + G.me.y + 1] >> G.me.x) & 1) == 1) {
                    directions[3] = true;
                }
                if (G.me.x > 0) {
                    if (((bfsDist[(i - 1) * (height + 2) + 1 + G.me.y] >> (G.me.x - 1)) & 1) == 1) {
                        directions[1] = true;
                    }
                    if (((bfsDist[(i - 1) * (height + 2) + 1 + G.me.y - 1] >> (G.me.x - 1)) & 1) == 1) {
                        directions[8] = true;
                    }
                    if (((bfsDist[(i - 1) * (height + 2) + 1 + G.me.y + 1] >> (G.me.x - 1)) & 1) == 1) {
                        directions[2] = true;
                    }
                }
                if (G.me.x < width - 1) {
                    if (((bfsDist[(i - 1) * (height + 2) + 1 + G.me.y] >> (G.me.x + 1)) & 1) == 1) {
                        directions[5] = true;
                    }
                    if (((bfsDist[(i - 1) * (height + 2) + 1 + G.me.y - 1] >> (G.me.x + 1)) & 1) == 1) {
                        directions[6] = true;
                    }
                    if (((bfsDist[(i - 1) * (height + 2) + 1 + G.me.y + 1] >> (G.me.x + 1)) & 1) == 1) {
                        directions[4] = true;
                    }
                }
                break;
            }
        }
        Direction optimalDirection = Direction.CENTER;
        int minDist = Integer.MAX_VALUE;
        // int optimalIndex = 0;
        for (int i = 9; --i >= 0;) {
            if (directions[i]) {
                Direction dir = Direction.DIRECTION_ORDER[i];
                if (G.rc.canMove(dir)) {
                    if (G.me.add(dir).distanceSquaredTo(dest) < minDist) {
                        optimalDirection = dir;
                        minDist = G.me.add(dir).distanceSquaredTo(dest);
                    }
                }
            }
        }
        if (optimalDirection != Direction.CENTER) {
            return optimalDirection;
        }
        if (optimalDirection == Direction.CENTER) {
            optimalDirection = bug2Helper(G.me, dest, TOWARDS, 0, 0);
            G.indicatorString.append("BFS-BUG ");

            if (canMove(optimalDirection)) {
                return optimalDirection;
            }
        }
        if (canMove(optimalDirection)) {
            return optimalDirection;
        }
        return Direction.CENTER;
    }

    public static void bfsnav(MapLocation dest) throws Exception {
        bfsnav(dest, defaultMicro);
    }

    public static void bfsnav(MapLocation dest, Micro m) throws Exception {
        int a = Clock.getBytecodesLeft();
        updateBfsTarget(dest);
        if (!G.rc.getLocation().equals(dest) && G.rc.isMovementReady()) {
            Direction d = getBfsDirection(dest);
            if (d == Direction.CENTER) {
                d = G.rc.getLocation().directionTo(dest);
            }
            int[] weights = m.micro(d, dest);
            int best = 8;
            for (int i = 8; --i >= 0;) {
                if (weights[i] > weights[best])
                    best = i;
            }
            if (best != 8)
                move(G.ALL_DIRECTIONS[best]);
        }
        bfs();
        G.indicatorString.append("BFS-BT: " + (Clock.getBytecodesLeft() - a) + "-");
    }

    public static void updateBfsTarget(MapLocation dest) throws Exception {
        if (!dest.equals(bfsDest)) {
            bfsDest = dest;
            for (int i = 1; i <= height; i++) {
                bfsDist[i] = 0;
                bfsCurr[i] = 0;
            }
            bfsDist[dest.y + 1] = long1 << (dest.x);
            bfsCurr[dest.y + 1] = long1 << (dest.x);
            step = 1;
        }
    }

    public static Micro defaultMicro = (Direction d, MapLocation dest) -> {
        int[] scores = new int[9];
        for (int i = 8; --i >= 0;) {
            if (!G.rc.canMove(G.DIRECTIONS[i])) {
                scores[i] = 0;
                continue;
            }
            int score = 0;
            MapLocation nxt = G.me.add(G.DIRECTIONS[i]);
            MapInfo info = G.rc.senseMapInfo(nxt);
            if (info.getPaint().isEnemy())
                score -= 10;
            else if (info.getPaint() == PaintType.EMPTY)
                score -= 5;
            if (G.DIRECTIONS[i] == d) {
                score += 20;
            } else if (G.DIRECTIONS[i].rotateLeft() == d || G.DIRECTIONS[i].rotateRight() == d) {
                score += 16;
            }
            scores[i] = score;
        }
        return scores;
    };

    // false if it didn't move
    public static boolean move(Direction dir) throws Exception {
        if (G.rc.canMove(dir)) {
            G.rc.move(dir);
            lastDir = dir;
            RobotPlayer.updateInfo();
            return true;
        }
        return false;
    }

    public static boolean canMove(Direction dir) throws Exception {
        return G.rc.canMove(dir);
    }
}