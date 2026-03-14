package fuschia;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;

public class Pathfinding {
    private final RobotController rc;

    // Bug navigation state fields kept private for easy internal replacement later.
    private boolean wallFollowing;
    private Direction bugDirection;
    private int lastClosestDistance;

    public Pathfinding(RobotController rc) {
        this.rc = rc;
        this.wallFollowing = false;
        this.bugDirection = Direction.CENTER;
        this.lastClosestDistance = Integer.MAX_VALUE;
    }

    public boolean pathTo(MapLocation target) throws GameActionException {
        if (target == null || !rc.isMovementReady()) {
            return false;
        }

        Direction bestDirection = null;
        int bestScore = Integer.MAX_VALUE;

        for (Direction direction : Direction.allDirections()) {
            if (direction == Direction.CENTER || !rc.canMove(direction)) {
                continue;
            }

            MapLocation next = rc.getLocation().add(direction);
            int score = next.distanceSquaredTo(target) + getPaintPenalty(next);
            if (score < bestScore) {
                bestScore = score;
                bestDirection = direction;
            }
        }

        if (bestDirection == null) {
            return false;
        }

        rc.move(bestDirection);
        int newDistance = rc.getLocation().distanceSquaredTo(target);
        this.wallFollowing = false;
        this.bugDirection = bestDirection;
        this.lastClosestDistance = Math.min(lastClosestDistance, newDistance);
        return true;
    }

    private int getPaintPenalty(MapLocation location) throws GameActionException {
        if (!rc.canSenseLocation(location)) {
            return Constants.NEUTRAL_PAINT_MOVE_PENALTY;
        }

        MapInfo mapInfo = rc.senseMapInfo(location);
        PaintType paintType = mapInfo.getPaint();
        if (paintType.isAlly()) {
            return Constants.ALLY_PAINT_MOVE_PENALTY;
        }
        if (paintType == PaintType.EMPTY) {
            return Constants.NEUTRAL_PAINT_MOVE_PENALTY;
        }
        return Constants.ENEMY_PAINT_MOVE_PENALTY;
    }
}
