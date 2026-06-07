package com.izimi.aiplayermod.brainstem.navigation;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

public class AStarPathfinder {
    private final World world;
    private static final int MAX_SEARCH_DISTANCE = 150;
    private static final int MAX_ITERATIONS = 10000;

    private static final Set<net.minecraft.block.Block> DANGEROUS_BLOCKS = Set.of(
            Blocks.LAVA, Blocks.FIRE, Blocks.CACTUS, Blocks.MAGMA_BLOCK,
            Blocks.SWEET_BERRY_BUSH, Blocks.COBWEB
    );

    public AStarPathfinder(World world) {
        this.world = world;
    }

    public List<BlockPos> findPath(BlockPos start, BlockPos end) {
        if (start.equals(end)) return List.of(start);

        double dist = start.getSquaredDistance(end);
        if (dist > MAX_SEARCH_DISTANCE * MAX_SEARCH_DISTANCE) {
            return findDirectPath(start, end);
        }

        PriorityQueue<Node> openSet = new PriorityQueue<>();
        Map<BlockPos, Node> allNodes = new HashMap<>();
        Set<BlockPos> closedSet = new HashSet<>();

        Node startNode = new Node(start, null, 0, heuristic(start, end));
        openSet.add(startNode);
        allNodes.put(start, startNode);

        int iterations = 0;

        while (!openSet.isEmpty() && iterations < MAX_ITERATIONS) {
            iterations++;
            Node current = openSet.poll();

            if (current.pos.equals(end) || current.pos.getSquaredDistance(end) <= 2.25) {
                return reconstructPath(current);
            }

            closedSet.add(current.pos);

            for (BlockPos neighbor : getNeighbors(current.pos)) {
                if (closedSet.contains(neighbor)) continue;
                if (!isWalkable(neighbor)) continue;

                double newG = current.g + getMovementCost(current.pos, neighbor);
                Node neighborNode = allNodes.get(neighbor);

                if (neighborNode == null || newG < neighborNode.g) {
                    double h = heuristic(neighbor, end);
                    Node newNode = new Node(neighbor, current, newG, h);
                    openSet.add(newNode);
                    allNodes.put(neighbor, newNode);
                }
            }
        }

        return findDirectPath(start, end);
    }

    private List<BlockPos> getNeighbors(BlockPos pos) {
        List<BlockPos> neighbors = new ArrayList<>();
        int[][] directions = {
                {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
                {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1}
        };

        for (int[] dir : directions) {
            BlockPos neighbor = pos.add(dir[0], dir[1], dir[2]);

            BlockPos ground = neighbor.down();
            if (!world.getBlockState(ground).isAir() && world.getBlockState(neighbor).isAir()
                    && world.getBlockState(neighbor.up()).isAir()) {
                neighbors.add(neighbor);
                continue;
            }

            if (dir[1] == 0) {
                BlockPos jumpUp = neighbor.up();
                if (world.getBlockState(neighbor).isAir()
                        && world.getBlockState(jumpUp).isAir()
                        && !world.getBlockState(neighbor.down()).isAir()
                        && world.getBlockState(neighbor.down().up()).isAir()) {
                    neighbors.add(neighbor);
                }
            }
        }

        for (int dy = -2; dy <= 2; dy++) {
            if (dy == 0) continue;
            BlockPos neighbor = pos.add(0, dy, 0);
            if (isWalkable(neighbor)) {
                neighbors.add(neighbor);
            }
        }

        return neighbors;
    }

    private boolean isWalkable(BlockPos pos) {
        if (pos.getY() < world.getBottomY() || pos.getY() >= world.getTopY()) return false;

        BlockPos feet = pos;
        BlockPos head = pos.up();

        if (!world.getBlockState(feet).isAir() || !world.getBlockState(head).isAir()) {
            return false;
        }

        if (DANGEROUS_BLOCKS.contains(world.getBlockState(feet).getBlock())
                || DANGEROUS_BLOCKS.contains(world.getBlockState(head).getBlock())) {
            return false;
        }

        BlockPos ground = pos.down();
        return !world.getBlockState(ground).isAir();
    }

    private double getMovementCost(BlockPos from, BlockPos to) {
        double baseCost = from.getSquaredDistance(to);

        if (to.getY() > from.getY()) {
            baseCost *= 1.5;
        }
        if (to.getY() < from.getY()) {
            baseCost *= 0.8;
        }

        BlockPos stepGround = to.down();
        if (world.getBlockState(stepGround).isOf(Blocks.SOUL_SAND)) {
            baseCost *= 2.0;
        }

        return baseCost;
    }

    private double heuristic(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private List<BlockPos> reconstructPath(Node end) {
        List<BlockPos> path = new ArrayList<>();
        Node current = end;
        while (current != null) {
            path.add(current.pos);
            current = current.parent;
        }
        Collections.reverse(path);
        return path;
    }

    private List<BlockPos> findDirectPath(BlockPos start, BlockPos end) {
        List<BlockPos> path = new ArrayList<>();
        int steps = (int) Math.sqrt(start.getSquaredDistance(end)) + 1;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            int x = (int) (start.getX() + (end.getX() - start.getX()) * t);
            int y = (int) (start.getY() + (end.getY() - start.getY()) * t);
            int z = (int) (start.getZ() + (end.getZ() - start.getZ()) * t);
            path.add(new BlockPos(x, y, z));
        }
        return path;
    }

    private static class Node implements Comparable<Node> {
        final BlockPos pos;
        final Node parent;
        final double g;
        final double h;

        Node(BlockPos pos, Node parent, double g, double h) {
            this.pos = pos;
            this.parent = parent;
            this.g = g;
            this.h = h;
        }

        double f() {
            return g + h;
        }

        @Override
        public int compareTo(Node other) {
            return Double.compare(this.f(), other.f());
        }
    }
}
