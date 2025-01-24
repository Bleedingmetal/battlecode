package manojBot;

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

    // Tower logic remains unchanged except for paint relay
    public static void runTower(RobotController rc) throws GameActionException {
        RobotInfo[] nearbyAllies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : nearbyAllies) {
            if (ally.paintAmount <= ally.getType().paintCapacity / 2 &&
                    rc.canTransferPaint(ally.location, ally.getType().paintCapacity / 4)) {
                rc.transferPaint(ally.location, ally.getType().paintCapacity / 4);
            }
        }
    }

    // Soldier logic
    public static void runSoldier(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        MapLocation currentLocation = rc.getLocation();

        // Paint refill logic
        if (rc.getPaint() < rc.getType().paintCapacity / 2) {
            moveToNearestTower(rc);
            return;
        }

        // Check for enemies and call reinforcements
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (nearbyEnemies.length > 0) {
            callReinforcements(rc, currentLocation);
            moveToLocation(rc, nearbyEnemies[0].location);
            return;
        }

        // Check for ruins to build towers
        for (MapInfo tile : nearbyTiles) {
            if (tile.hasRuin()) {
                if (attemptTowerBuild(rc, tile.getMapLocation())) return;
            }
        }

        // Default behavior: Move and paint
        MapLocation target = findUnpaintedTile(rc, nearbyTiles);
        if (target != null) {
            moveToLocation(rc, target);
        }
    }

    // Mopper logic
    public static void runMopper(RobotController rc) throws GameActionException {
        if (rc.getPaint() < 20) {
            moveToNearestTower(rc);
            return;
        }

        // Transfer paint to allies
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (ally.paintAmount < ally.getType().paintCapacity &&
                    rc.canTransferPaint(ally.location, 10)) {
                rc.transferPaint(ally.location, 10);
                return;
            }
        }

        // Mop enemy paint
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemies) {
            if (rc.canMopSwing(enemy.location.directionTo(rc.getLocation()))) {
                rc.mopSwing(enemy.location.directionTo(rc.getLocation()));
                return;
            }
        }

        // Default movement
        moveToLocation(rc, findUnpaintedTile(rc, rc.senseNearbyMapInfos()));
    }

    // Splasher logic
    public static void runSplasher(RobotController rc) throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemies) {
            if (rc.canAttack(enemy.location)) {
                rc.attack(enemy.location);
                return;
            }
        }

        moveToLocation(rc, findUnpaintedTile(rc, rc.senseNearbyMapInfos()));
    }

    // A* Pathfinding implementation
    private static void moveToLocation(RobotController rc, MapLocation target) throws GameActionException {
        if (target == null || !rc.isMovementReady()) return;

        MapLocation current = rc.getLocation();
        PriorityQueue<PathNode> openSet = new PriorityQueue<>(Comparator.comparingInt(node -> node.fCost));
        Set<MapLocation> closedSet = new HashSet<>();

        openSet.add(new PathNode(current, null, 0, calculateHeuristic(current, target)));

        while (!openSet.isEmpty()) {
            PathNode currentNode = openSet.poll();
            if (currentNode.location.equals(target)) {
                moveAlongPath(rc, reconstructPath(currentNode));
                return;
            }

            closedSet.add(currentNode.location);

            for (Direction dir : directions) {
                MapLocation neighbor = currentNode.location.add(dir);
                if (closedSet.contains(neighbor) || !rc.canSenseLocation(neighbor) || rc.senseRobotAtLocation(neighbor) != null) continue;

                int gCost = currentNode.gCost + 1;
                int hCost = calculateHeuristic(neighbor, target);
                openSet.add(new PathNode(neighbor, currentNode, gCost, gCost + hCost));
            }
        }
    }

    private static int calculateHeuristic(MapLocation a, MapLocation b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y); // Manhattan distance
    }

    private static void moveAlongPath(RobotController rc, List<MapLocation> path) throws GameActionException {
        if (path.size() > 1) {
            Direction dir = rc.getLocation().directionTo(path.get(1));
            if (rc.canMove(dir)) {
                rc.move(dir);
            }
        }
    }

    private static List<MapLocation> reconstructPath(PathNode node) {
        List<MapLocation> path = new ArrayList<>();
        while (node != null) {
            path.add(0, node.location);
            node = node.parent;
        }
        return path;
    }

    // Helper classes
    static class PathNode {
        MapLocation location;
        PathNode parent;
        int gCost;
        int fCost;

        PathNode(MapLocation location, PathNode parent, int gCost, int fCost) {
            this.location = location;
            this.parent = parent;
            this.gCost = gCost;
            this.fCost = fCost;
        }
    }

    // Find nearest unpainted tile
    private static MapLocation findUnpaintedTile(RobotController rc, MapInfo[] tiles) {
        for (MapInfo tile : tiles) {
            if (!tile.getPaint().isAlly()) {
                return tile.getMapLocation();
            }
        }
        return null;
    }

    // Tower building attempt
    private static boolean attemptTowerBuild(RobotController rc, MapLocation ruin) throws GameActionException {
        if (rc.canMarkTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruin)) {
            rc.markTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruin);
            System.out.println("Marking tower pattern at " + ruin);
            return true;
        }
        return false;
    }

    // Paint refill logic
    private static void moveToNearestTower(RobotController rc) throws GameActionException {
        RobotInfo[] nearbyTowers = rc.senseNearbyRobots(-1, rc.getTeam());
        MapLocation nearestTower = null;
        int minDistance = Integer.MAX_VALUE;

        for (RobotInfo tower : nearbyTowers) {
            if (tower.getType().toString().contains("TOWER")) {
                int distance = rc.getLocation().distanceSquaredTo(tower.location);
                if (distance < minDistance) {
                    minDistance = distance;
                    nearestTower = tower.location;
                }
            }
        }

        if (nearestTower != null) {
            moveToLocation(rc, nearestTower);
        }
    }

    // Reinforcements
    private static void callReinforcements(RobotController rc, MapLocation location) throws GameActionException {
        rc.sendMessage(rc.getLocation(), location.hashCode()); // Example signal (can be expanded for specific behavior)
    }
}
