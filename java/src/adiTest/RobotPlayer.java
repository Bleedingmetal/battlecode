package adiTest;

import battlecode.common.*;
import java.util.Random;

public class RobotPlayer {
    static int turnCount = 0;
    static final Random rng = new Random(6147);
    static final Direction[] directions = Direction.values();
    static final Direction[] generalDirections = {
            Direction.NORTH, Direction.NORTHEAST, Direction.NORTHWEST,
            Direction.EAST, Direction.SOUTHEAST, Direction.SOUTHWEST,
            Direction.SOUTH, Direction.WEST
    };

    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            turnCount++;
            try {
                switch (rc.getType()) {
                    case SOLDIER -> runSoldier(rc);
                    case MOPPER -> runMopper(rc);
                    case SPLASHER -> runSplasher(rc);
                    default -> runTower(rc);
                }
            } catch (Exception e) {
                System.err.println("Exception: " + e.getMessage());
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    public static void runTower(RobotController rc) throws GameActionException {
        if (rc.isActionReady()) {
            int round = rc.getRoundNum();
            Direction dir = directions[rng.nextInt(directions.length)];
            MapLocation nextLoc = rc.getLocation().add(dir);

            if (rc.canSenseLocation(nextLoc) && rc.senseRobotAtLocation(nextLoc) == null) {
                if (round < 500) {
                    if (rng.nextDouble() < 0.6 && rc.canBuildRobot(UnitType.SOLDIER, nextLoc)) {
                        rc.buildRobot(UnitType.SOLDIER, nextLoc);
                    } else if (rc.canBuildRobot(UnitType.SPLASHER, nextLoc)) {
                        rc.buildRobot(UnitType.SPLASHER, nextLoc);
                    }
                } else if (round < 1500) {
                    if (rng.nextDouble() < 0.6 && rc.canBuildRobot(UnitType.SPLASHER, nextLoc)) {
                        rc.buildRobot(UnitType.SPLASHER, nextLoc);
                    } else if (rc.canBuildRobot(UnitType.MOPPER, nextLoc)) {
                        rc.buildRobot(UnitType.MOPPER, nextLoc);
                    }
                } else if (rc.canBuildRobot(UnitType.SPLASHER, nextLoc)) {
                    rc.buildRobot(UnitType.SPLASHER, nextLoc);
                }
            }
        }

        RobotInfo[] nearbyAllies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : nearbyAllies) {
            if (ally.paintAmount <= ally.getType().paintCapacity / 2 &&
                    rc.canTransferPaint(ally.location, ally.getType().paintCapacity / 2)) {
                rc.transferPaint(ally.location, ally.getType().paintCapacity / 4);
            }
        }
    }

    public static void runSoldier(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        for (MapInfo tile : nearbyTiles) {
            if (tile.getPaint() != PaintType.ALLY_PRIMARY &&
                    rc.canAttack(tile.getMapLocation()) &&
                    rc.getLocation().distanceSquaredTo(tile.getMapLocation()) <= rc.getType().actionRadiusSquared) {
                rc.attack(tile.getMapLocation());
                return;
            }
        }

        Direction moveDir = getVShapeDirection(rc);
        if (rc.canMove(moveDir)) {
            rc.move(moveDir);
        }

        tryBuildOrUpgradeTower(rc);
    }

    public static void runMopper(RobotController rc) throws GameActionException {
        if (rc.getPaint() < 20) {
            moveToNearestTower(rc);
            return;
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemies) {
            if (rc.canMopSwing(enemy.location.directionTo(rc.getLocation()))) {
                rc.mopSwing(enemy.location.directionTo(rc.getLocation()));
                return;
            }
        }

        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (ally.paintAmount < ally.getType().paintCapacity &&
                    rc.canTransferPaint(ally.location, 10)) {
                rc.transferPaint(ally.location, 10);
                return;
            }
        }

        Direction moveDir = getVShapeDirection(rc);
        if (rc.canMove(moveDir)) {
            rc.move(moveDir);
        }

        tryBuildOrUpgradeTower(rc);
    }

    public static void runSplasher(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        for (MapInfo tile : nearbyTiles) {
            if (tile.getPaint() != PaintType.ALLY_PRIMARY &&
                    rc.canAttack(tile.getMapLocation()) &&
                    rc.getLocation().distanceSquaredTo(tile.getMapLocation()) <= rc.getType().actionRadiusSquared) {
                rc.attack(tile.getMapLocation());
                return;
            }
        }

        Direction moveDir = getVShapeDirection(rc);
        if (rc.canMove(moveDir)) {
            rc.move(moveDir);
        }

        tryBuildOrUpgradeTower(rc);
    }

    private static void tryBuildOrUpgradeTower(RobotController rc) throws GameActionException {
        if (!rc.isActionReady()) return;

        MapLocation myLocation = rc.getLocation();
        UnitType[] towerTypes = {
                UnitType.LEVEL_ONE_PAINT_TOWER,
                UnitType.LEVEL_ONE_MONEY_TOWER,
                UnitType.LEVEL_ONE_DEFENSE_TOWER,
                UnitType.LEVEL_TWO_PAINT_TOWER,
                UnitType.LEVEL_TWO_MONEY_TOWER,
                UnitType.LEVEL_TWO_DEFENSE_TOWER,
                UnitType.LEVEL_THREE_PAINT_TOWER,
                UnitType.LEVEL_THREE_MONEY_TOWER,
                UnitType.LEVEL_THREE_DEFENSE_TOWER
        };

        for (UnitType towerType : towerTypes) {
            if (rc.canMarkTowerPattern(towerType, myLocation)) {
                rc.markTowerPattern(towerType, myLocation);
                System.out.println("Marking pattern for " + towerType + " at " + myLocation);
                return;
            }
        }

        if (rc.getPaint() >= 10) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    MapLocation loc = new MapLocation(myLocation.x + dx, myLocation.y + dy);
                    if (rc.canMarkResourcePattern(loc)) {
                        rc.markResourcePattern(loc);
                        System.out.println("Marked SRP at " + loc);
                        return;
                    }
                }
            }
        }
    }

    private static boolean isTower(UnitType type) {
        return switch (type) {
            case LEVEL_ONE_PAINT_TOWER, LEVEL_TWO_PAINT_TOWER, LEVEL_THREE_PAINT_TOWER,
                 LEVEL_ONE_MONEY_TOWER, LEVEL_TWO_MONEY_TOWER, LEVEL_THREE_MONEY_TOWER,
                 LEVEL_ONE_DEFENSE_TOWER, LEVEL_TWO_DEFENSE_TOWER, LEVEL_THREE_DEFENSE_TOWER -> true;
            default -> false;
        };
    }

    private static void moveToNearestTower(RobotController rc) throws GameActionException {
        RobotInfo[] nearbyTowers = rc.senseNearbyRobots(-1, rc.getTeam());
        MapLocation nearestTower = null;
        int minDistance = Integer.MAX_VALUE;

        for (RobotInfo robot : nearbyTowers) {
            if (isTower(robot.getType())) {
                int distance = rc.getLocation().distanceSquaredTo(robot.location);
                if (distance < minDistance) {
                    minDistance = distance;
                    nearestTower = robot.location;
                }
            }
        }

        if (nearestTower != null) {
            Direction dir = rc.getLocation().directionTo(nearestTower);
            if (rc.canMove(dir)) {
                rc.move(dir);
            }
        }
    }

    private static Direction getVShapeDirection(RobotController rc) {
        MapLocation myLocation = rc.getLocation();
        int mapWidth = rc.getMapWidth();
        int mapHeight = rc.getMapHeight();

        // Create a Random object for 50/50 decision
        Random rand = new Random();

        // Middle bottom: move top-left and top-right
        if (myLocation.x == mapWidth / 2 && myLocation.y == mapHeight - 1) {
            // Randomly choose between NORTHWEST and NORTHEAST
            return rand.nextBoolean() ? Direction.NORTHWEST : Direction.NORTHEAST;
        }
        // Middle top: move bottom-left and bottom-right
        else if (myLocation.x == mapWidth / 2 && myLocation.y == 0) {
            // Randomly choose between SOUTHWEST and SOUTHEAST
            return rand.nextBoolean() ? Direction.SOUTHWEST : Direction.SOUTHEAST;
        }
        // Middle left: move top-right and bottom-right
        else if (myLocation.x == 0 && myLocation.y == mapHeight / 2) {
            // Randomly choose between NORTHEAST and SOUTHEAST
            return rand.nextBoolean() ? Direction.NORTHEAST : Direction.SOUTHEAST;
        }
        // Middle right: move top-left and bottom-left
        else if (myLocation.x == mapWidth - 1 && myLocation.y == mapHeight / 2) {
            // Randomly choose between NORTHWEST and SOUTHWEST
            return rand.nextBoolean() ? Direction.NORTHWEST : Direction.SOUTHWEST;
        }

        Direction[] directions = Direction.values();
        return directions[rand.nextInt(directions.length)];
    }
}
