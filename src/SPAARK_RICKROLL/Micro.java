package SPAARK_RICKROLL;

import battlecode.common.*;

@FunctionalInterface
public interface Micro {
    /**
     * Micro function - returns weights of moving each direction, where highest
     * weight is movement direction. Can be chained using the weights.
     * 
     * @param d    Pathfinding direction
     * @param dest Destination location
     * @return Length-9 array of weights for moving in each direction, mapped the
     *         same as G.ALL_DIRECTIONS
     * @throws Exception
     */
    public int[] micro(Direction d, MapLocation dest) throws Exception;
}