package test1;

import battlecode.common.*;

import java.util.Random;

/**
 * Ok so this is like the main place where the code kicks off, pretty much ur basecamp.
 * Every bot type runs this and then splits off to do its own thing. Modular vibes, right?
 */
public class RobotPlayer {
    static int turnCount = 0; // Keepin track of how long we've been in the game, like a diary but numbers.
    static final Random rng = new Random(6147); // Randomizer dude for spice. Predictable for testing tho.


    static final Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
    };

    // We're aiming to make a V-shaped spread, so these are like the whatver shit
    static final Direction[] generalDirections = {
            Direction.NORTH, Direction.NORTHEAST, Direction.NORTHWEST
    };

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            turnCount++; // Lol one more turn survived, gg.

            try {
                // Each bot type runs its own strat. Divide and conquer ftw.
                switch (rc.getType()) {
                    case SOLDIER: runSoldier(rc); break;
                    case MOPPER: runMopper(rc); break;
                    case SPLASHER: runSplasher(rc); break;
                    default: runTower(rc); break;
                }
            } catch (Exception e) {
                System.out.println("Exception"); // Yeah, something borked. Fix it maybe?
                e.printStackTrace();
            } finally {
                Clock.yield(); // Chill till the next turn starts.
            }
        }
    }

    public static void runTower(RobotController rc) throws GameActionException {

        if (rc.isActionReady()) {
            int round = rc.getRoundNum(); // What phase of the game r we in?
            Direction dir = directions[rng.nextInt(directions.length)]; // Random move idea for fun.
            MapLocation nextLoc = rc.getLocation().add(dir);

            // Early game: build basic chill dudes to claim land.
            if (round < 500) {
                if (rc.canBuildRobot(UnitType.SOLDIER, nextLoc)) {
                    rc.buildRobot(UnitType.SOLDIER, nextLoc);
                } else if (rc.canBuildRobot(UnitType.MOPPER, nextLoc)) {
                    rc.buildRobot(UnitType.MOPPER, nextLoc);
                }
            } else if (round < 1500) {

                if (rng.nextBoolean() && rc.canBuildRobot(UnitType.SPLASHER, nextLoc)) {
                    rc.buildRobot(UnitType.SPLASHER, nextLoc);
                } else if (rc.canBuildRobot(UnitType.MOPPER, nextLoc)) {
                    rc.buildRobot(UnitType.MOPPER, nextLoc);
                }
            } else {
                // Late game: full send splashers and moppers for chaos.
                if (rc.canBuildRobot(UnitType.SPLASHER, nextLoc)) {
                    rc.buildRobot(UnitType.SPLASHER, nextLoc);
                } else if (rc.canBuildRobot(UnitType.MOPPER, nextLoc)) {
                    rc.buildRobot(UnitType.MOPPER, nextLoc);
                }
            }
        }


        RobotInfo[] nearbyAllies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : nearbyAllies) {
            if (ally.getPaint() < ally.getType().getPaintCapacity() / 2 && rc.canTransferPaint(ally.location)) {
                rc.transferPaint(ally.location, ally.getType().getPaintCapacity() / 4);
            }
        }
    }

    public static void runSoldier(RobotController rc) throws GameActionException {

        if (rc.getPaint() < 10) {
            // Outta paint? Run back to mommy (aka tower).
            moveToNearestTower(rc);
            return;
        }


        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        for (MapInfo tile : nearbyTiles) {
            if (tile.getPaint() != PaintType.ALLY_PRIMARY && rc.canAttack(tile.getMapLocation())) {
                rc.attack(tile.getMapLocation()); // Boom, tile ours now.
                return;
            }
        }


        Direction generalDir = generalDirections[turnCount % generalDirections.length];
        Direction moveDir = directions[rng.nextInt(directions.length)];

        if (rng.nextDouble() < 0.7 && rc.canMove(generalDir)) {
            rc.move(generalDir);
        } else if (rc.canMove(moveDir)) {
            rc.move(moveDir);
        }
    }

    public static void runMopper(RobotController rc) throws GameActionException {
        // Moppers do all the dirty work: enemy wipeout + paint delivery.
        if (rc.getPaint() < 20) {
            .
            moveToNearestTower(rc);
            return;
        }

        // Priority uno: clean up enemy mess.
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemies) {
            if (rc.canMopSwing(enemy.location.directionTo(rc.getLocation()))) {
                rc.mopSwing(enemy.location.directionTo(rc.getLocation())); // Sweep!
                return;
            }
        }

        // If no enemies, do whatver u want ig
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (ally.getPaint() < ally.getType().getPaintCapacity() / 2 && rc.canTransferPaint(ally.location)) {
                rc.transferPaint(ally.location, 10);
                return;
            }
        }

        // V-move again or check back with the tower for instructions.
        Direction generalDir = generalDirections[turnCount % generalDirections.length];
        Direction moveDir = directions[rng.nextInt(directions.length)];

        if (rc.getRoundNum() % 5 == 0 && !isInCommunicationRange(rc)) {
            moveToNearestTower(rc);
        } else if (rng.nextDouble() < 0.7 && rc.canMove(generalDir)) {
            rc.move(generalDir);
        } else if (rc.canMove(moveDir)) {
            rc.move(moveDir);
        }
    }

    public static void runSplasher(RobotController rc) throws GameActionException {
        // Splashers just go ham on contested zones.
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        for (MapInfo tile : nearbyTiles) {
            if (tile.getPaint() == PaintType.ENEMY_PRIMARY && rc.canAttack(tile.getMapLocation())) {
                rc.attack(tile.getMapLocation()); // Splat
                return;
            }
        }

        // Stick to the V-move plan, go with the flow.
        Direction generalDir = generalDirections[turnCount % generalDirections.length];
        Direction moveDir = directions[rng.nextInt(directions.length)];

        if (rng.nextDouble() < 0.7 && rc.canMove(generalDir)) {
            rc.move(generalDir);
        } else if (rc.canMove(moveDir)) {
            rc.move(moveDir);
        }
    }

    private static void moveToNearestTower(RobotController rc) throws GameActionException {
        // Nearest tower? Go back home to chill or refuel. idrk this is kinda cooked idk wht a unittype tower is i mean i do bit its supposed to work bruh it works in the main but here it dies god help me I will kill this pc
        RobotInfo[] nearbyTowers = rc.senseNearbyRobots(-1, rc.getTeam());
        MapLocation nearestTower = null;
        int minDistance = Integer.MAX_VALUE;

        for (RobotInfo robot : nearbyTowers) {
            if (robot.getType() == UnitType.TOWER) {
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

    private static boolean isInCommunicationRange(RobotController rc) throws GameActionException {
        // Are we still in the squad chat zone or nah?
        RobotInfo[] nearbyTowers = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo robot : nearbyTowers) {
            if (robot.getType() == UnitType.TOWER && rc.getLocation().isWithinDistanceSquared(robot.location, 20)) {
                return true;
            }
        }
        return false;
    }

    // Function to determine the zone based on position its not round based coz idk how to check round without a counter(might do that lowk) but this is what its at rn and these are arbitrary numbers rn
    private static String getZone(MapLocation loc, int mapWidth, int mapHeight) {
        int x = loc.x;
        int y = loc.y;

        // Farthest zone is near the map center
        MapLocation center = new MapLocation(mapWidth / 2, mapHeight / 2);
        int distanceToCenter = loc.distanceSquaredTo(center);
        int distanceToSpawn = loc.distanceSquaredTo(new MapLocation(0, 0));

        if (distanceToSpawn < mapWidth * mapHeight / 10) {
            return "MONEY"; // Closest zone to spawn
        } else if (distanceToCenter < mapWidth * mapHeight / 6) {
            return "DEFENSE"; // Near center zone
        } else {
            return "PAINT"; // Middle zone between spawn and center
        }
    }
}
