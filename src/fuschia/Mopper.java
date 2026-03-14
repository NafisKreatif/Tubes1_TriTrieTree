package fuschia;

import battlecode.common.GameActionException;
import battlecode.common.RobotController;

public class Mopper extends Unit {

    enum MopperState {
        REFILL,
        RUIN_DEFENSE,
        TOWER_DEFENSE,
        MOP_PATROL,
        EXPLORE
    }

    private MopperState state = MopperState.EXPLORE;

    public Mopper(RobotController rc) {
        super(rc);
    }

    @Override
    protected void determineState() throws GameActionException {
        
    }

    @Override
    protected void executeState() throws GameActionException {
        switch (state) {
            case REFILL:
                doRefill();
                break;
            case RUIN_DEFENSE:
                doRuinDefense();
                break;
            case TOWER_DEFENSE:
                doTowerDefense();
                break;
            case MOP_PATROL:
                doMopPatrol();
                break;
            case EXPLORE:
                doExplore();
                break;
            default:
                break;
        }
    }

    private void doRefill() throws GameActionException {
    }

    private void doRuinDefense() throws GameActionException {
    }

    private void doTowerDefense() throws GameActionException {
    }

    private void doMopPatrol() throws GameActionException {
    }

    private void doExplore() throws GameActionException {
    }
}
