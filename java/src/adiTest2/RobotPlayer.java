package adiTest2;

import battlecode.common.*;
import java.util.*;

public class RobotPlayer {
    static int turnCount = 0;
    static final Random rng = new Random(6147);
    static final Direction[] directions = Direction.values();

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
                } else if (round < 1000) {
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
        Direction moveDir = getVShapeDirection(rc);

        if (!rc.canMove(moveDir)) {
            moveDir = getWallFollowDirection(rc, moveDir);
        }

        if (rc.canMove(moveDir)) {
            rc.move(moveDir);
        }

        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        // Search for a nearby ruin to complete ONLY IF EARLY ROUNDS
        if (rc.getRoundNum() < 2000) {
            MapInfo curRuin = null;
            for (MapInfo tile : nearbyTiles){
                if (tile.hasRuin()){
                    curRuin = tile;
                }
            }
            if (curRuin != null){
                MapLocation targetLoc = curRuin.getMapLocation();
                Direction dir = rc.getLocation().directionTo(targetLoc);
                if (rc.canMove(dir))
                    rc.move(dir);
                // Mark the pattern we need to draw to build a tower here if we haven't already.
                MapLocation shouldBeMarked = curRuin.getMapLocation().subtract(dir);
                //TODO reviiew need for painttype empty and get rid of it if not needed
                if (rc.senseMapInfo(shouldBeMarked).getMark() == PaintType.EMPTY && rc.canMarkTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc)){
                    rc.markTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc);
                    System.out.println("Trying to build a tower at " + targetLoc);
                }
                // Fill in any spots in the pattern with the appropriate paint.
                for (MapInfo patternTile : rc.senseNearbyMapInfos(targetLoc, 8)){
                    if (patternTile.getMark() != patternTile.getPaint() && patternTile.getMark() != PaintType.EMPTY){
                        boolean useSecondaryColor = patternTile.getMark() == PaintType.ALLY_SECONDARY;
                        if (rc.canAttack(patternTile.getMapLocation()))
                            rc.attack(patternTile.getMapLocation(), useSecondaryColor);
                    }
                }
                // Complete the ruin if we can.
                if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc)){
                    rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc);
                    rc.setTimelineMarker("Tower built", 0, 255, 0);
                    System.out.println("Built a tower at " + targetLoc + "!");
                }
            }
        }
        MapInfo currentTile = rc.senseMapInfo(rc.getLocation());
        if (!currentTile.getPaint().isAlly() && rc.canAttack(rc.getLocation())) {
            rc.attack(rc.getLocation());
        }
    }

    public static void runMopper(RobotController rc) throws GameActionException {
        Direction moveDir = getVShapeDirection(rc);
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

        if (!rc.canMove(moveDir)) {
            moveDir = getWallFollowDirection(rc, moveDir);
        }

        if (rc.canMove(moveDir)) {
            rc.move(moveDir);
        }

        // Mopping logic

        // TODO decide which tower logic is better

        if (rc.getRoundNum() < 2000) {
            MapInfo curRuin = null;
            for (MapInfo tile : nearbyTiles){
                if (tile.hasRuin()){
                    curRuin = tile;
                }
            }
            if (curRuin != null){
                MapLocation targetLoc = curRuin.getMapLocation();
                Direction dir = rc.getLocation().directionTo(targetLoc);
                if (rc.canMove(dir))
                    rc.move(dir);
                // Mark the pattern we need to draw to build a tower here if we haven't already.
                MapLocation shouldBeMarked = curRuin.getMapLocation().subtract(dir);
                //TODO reviiew need for painttype empty and get rid of it if not needed
                if (rc.senseMapInfo(shouldBeMarked).getMark() == PaintType.EMPTY && rc.canMarkTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, targetLoc)){
                    rc.markTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, targetLoc);
                    System.out.println("Trying to build a tower at " + targetLoc);
                }
                // Fill in any spots in the pattern with the appropriate paint.
                for (MapInfo patternTile : rc.senseNearbyMapInfos(targetLoc, 8)){
                    if (patternTile.getMark() != patternTile.getPaint() && patternTile.getMark() != PaintType.EMPTY){
                        boolean useSecondaryColor = patternTile.getMark() == PaintType.ALLY_SECONDARY;
                        if (rc.canAttack(patternTile.getMapLocation()))
                            rc.attack(patternTile.getMapLocation(), useSecondaryColor);
                    }
                }
                // Complete the ruin if we can.
                if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, targetLoc)){
                    rc.completeTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, targetLoc);
                    rc.setTimelineMarker("Tower built", 0, 255, 0);
                    System.out.println("Built a tower at " + targetLoc + "!");
                }
            }
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
            if (rc.getPaint() < 20) {
                moveToNearestTower(rc);
            }
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
    private static boolean isTower(UnitType type) {
        return switch (type) {
            case LEVEL_ONE_PAINT_TOWER, LEVEL_TWO_PAINT_TOWER, LEVEL_THREE_PAINT_TOWER,
                 LEVEL_ONE_MONEY_TOWER, LEVEL_TWO_MONEY_TOWER, LEVEL_THREE_MONEY_TOWER,
                 LEVEL_ONE_DEFENSE_TOWER, LEVEL_TWO_DEFENSE_TOWER, LEVEL_THREE_DEFENSE_TOWER -> true;
            default -> false;
        };
    }

    public static void runSplasher(RobotController rc) throws GameActionException {
        Direction moveDir = getVShapeDirection(rc);

        if (!rc.canMove(moveDir)) {
            moveDir = getWallFollowDirection(rc, moveDir);
        }
        if (turnCount % 6 == 0) {
            moveDir = getSweepDirection(rc);
        }

        if (rc.canMove(moveDir)) {
            rc.move(moveDir);
        }

        // Splash attacking
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        MapInfo currentTile = rc.senseMapInfo(rc.getLocation());
        if (!currentTile.getPaint().isAlly() && rc.canAttack(rc.getLocation())) {
            rc.attack(rc.getLocation());
        }
    }



    private static Direction getVShapeDirection(RobotController rc) {
        MapLocation myLocation = rc.getLocation();
        int mapWidth = rc.getMapWidth();
        int mapHeight = rc.getMapHeight();

        // Middle bottom: move top-left and top-right
        if (myLocation.x == mapWidth / 2 && myLocation.y == mapHeight - 1) {
            return rng.nextBoolean() ? Direction.NORTHWEST : Direction.NORTHEAST;
        }
        // Middle top: move bottom-left and bottom-right
        else if (myLocation.x == mapWidth / 2 && myLocation.y == 0) {
            return rng.nextBoolean() ? Direction.SOUTHWEST : Direction.SOUTHEAST;
        }
        // Middle left: move top-right and bottom-right
        else if (myLocation.x == 0 && myLocation.y == mapHeight / 2) {
            return rng.nextBoolean() ? Direction.NORTHEAST : Direction.SOUTHEAST;
        }
        // Middle right: move top-left and bottom-left
        else if (myLocation.x == mapWidth - 1 && myLocation.y == mapHeight / 2) {
            return rng.nextBoolean() ? Direction.NORTHWEST : Direction.SOUTHWEST;
        }

        // Fallback: Random direction
        return directions[rng.nextInt(directions.length)];
    }

    private static Direction getWallFollowDirection(RobotController rc, Direction initialDir) throws GameActionException {
        for (Direction dir : directions) {
            if (rc.canMove(dir)) {
                return dir;
            }
        }
        return initialDir; // If no alternative direction, return the initial
    }

    private static MapLocation findNearestUnexploredTile(RobotController rc) throws GameActionException {
        Queue<MapLocation> toExplore = new LinkedList<>();
        Set<MapLocation> visited = new HashSet<>();
        toExplore.add(rc.getLocation());

        while (!toExplore.isEmpty()) {
            MapLocation current = toExplore.poll();
            if (!visited.contains(current)) {
                visited.add(current);

                MapInfo mapInfo = rc.senseMapInfo(current);
                if (mapInfo.getPaint() == PaintType.EMPTY) {
                    return current;
                }

                for (Direction dir : directions) {
                    MapLocation neighbor = current.add(dir);
                    if (!visited.contains(neighbor)) {
                        toExplore.add(neighbor);
                    }
                }
            }
        }

        return null; // No unexplored tile found
    }

    private static Direction getSweepDirection(RobotController rc) {
        MapLocation myLocation = rc.getLocation();
        int mapWidth = rc.getMapWidth();
        int mapHeight = rc.getMapHeight();

        // Determine sweeping based on spawn area
        if (myLocation.x == mapWidth / 2) {
            return rng.nextBoolean() ? Direction.EAST : Direction.WEST;
        } else if (myLocation.y == mapHeight / 2) {
            return rng.nextBoolean() ? Direction.NORTH : Direction.SOUTH;
        }

        return directions[rng.nextInt(directions.length)]; // Random fallback
    }

}

