package fuschia;

import battlecode.common.GameActionException;
import battlecode.common.RobotController;

enum SoldierState {
    REFILL,
    COMBAT,
    TOWER_BUILD,
    TAINT,
    SRP_BUILD,
    EXPLORE
}

public class Soldier extends Unit {
    private SoldierState state = SoldierState.EXPLORE;

    public Soldier(RobotController rc) {
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
            case COMBAT:
                doCombat();
                break;
            case TOWER_BUILD:
                doTowerBuild();
                break;
            case TAINT:
                doTaint();
                break;
            case SRP_BUILD:
                doSrpBuild();
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

    private void doCombat() throws GameActionException {
    }

    private void doTowerBuild() throws GameActionException {
    }

    private void doTaint() throws GameActionException {
    }

    private void doSrpBuild() throws GameActionException {
    }

    private void doExplore() throws GameActionException {
    }
}
