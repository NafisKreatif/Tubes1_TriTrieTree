package fuschia;

import battlecode.common.GameActionException;
import battlecode.common.RobotController;

public class PaintTower extends Tower {
    private TowerState state = TowerState.SPAWN_UNITS;

    public PaintTower(RobotController rc) {
        super(rc);
    }

    @Override
    protected void determineState() throws GameActionException {
        
    }

    @Override
    protected void executeState() throws GameActionException {
        switch (state) {
            case SPAWN_UNITS:
                doSpawnUnits();
                break;
            case DEFEND:
                doDefend();
                break;
            case UPGRADE:
                doUpgrade();
                break;
            case FLICKER:
                doFlicker();
                break;
            default:
                break;
        }
    }

    private void doSpawnUnits() throws GameActionException {
    }

    private void doDefend() throws GameActionException {
    }

    private void doUpgrade() throws GameActionException {
    }

    private void doFlicker() throws GameActionException {
    }
}
