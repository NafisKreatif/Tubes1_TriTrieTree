package fuschia;

import battlecode.common.GameActionException;
import battlecode.common.RobotController;

enum SplasherState {
    REFILL,
    SPLASH,
    EXPLORE
}

public class Splasher extends Unit {
    private SplasherState state = SplasherState.EXPLORE;

    public Splasher(RobotController rc) {
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
            case SPLASH:
                doSplash();
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

    private void doSplash() throws GameActionException {
    }

    private void doExplore() throws GameActionException {
    }
}
