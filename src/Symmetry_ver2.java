import java.util.Scanner;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicLong;
import java.util.ArrayList;
import java.util.List;

public class Symmetry_ver2 {
    private static final int GRID_SIZE = 8;
    private static final int TOTAL_MOVES = 63;
    private static final int PARALLEL_THRESHOLD = 10;

    private static final AtomicLong totalPaths = new AtomicLong(0);
    private static long lastUpdateTime = 0;
    private static long startTime = 0;
    private static final long UPDATE_INTERVAL = 1000;

    // Movement directions (Down, Up, Right, Left)
    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DY = {0, 0, 1, -1};

    private static class PathExplorer extends RecursiveAction {
        private final int x, y, moveIndex;
        private final long visited;
        private final String path;
        private final int leftmostReached; // Track leftmost column reached

        public PathExplorer(int x, int y, int moveIndex, long visited, String path, int leftmostReached) {
            this.x = x;
            this.y = y;
            this.moveIndex = moveIndex;
            this.visited = visited;
            this.path = path;
            this.leftmostReached = leftmostReached;
        }

        @Override
        protected void compute() {
            // Base case
            if (moveIndex == TOTAL_MOVES) {
                if (x == GRID_SIZE - 1 && y == 0) {
                    totalPaths.incrementAndGet();
                }
                return;
            }

            if (!canReachEnd(x, y, TOTAL_MOVES - moveIndex, visited)) {
                return;
            }

            char currentMove = path.charAt(moveIndex);
            List<PathExplorer> subtasks = new ArrayList<>();

            if (currentMove == '*') {
                // Handle wildcard moves with symmetry rules
                handleWildcardMoves(subtasks);
            } else {
                // Handle specified moves with symmetry rules
                int dir = getDirectionIndex(currentMove);
                tryMoveWithSymmetry(dir, subtasks);
            }

            if (!subtasks.isEmpty()) {
                invokeAll(subtasks);
            }
        }

        private void handleWildcardMoves(List<PathExplorer> subtasks) {
            for (int dir = 0; dir < 4; dir++) {
                // Apply symmetry rules for wildcard moves
                if (shouldSkipMove(dir)) continue;
                tryMoveWithSymmetry(dir, subtasks);
            }
        }

        private boolean shouldSkipMove(int dir) {
            // Skip LEFT moves if this would create a symmetric path
            if (dir == 3) { // LEFT move
                // If we're at y=leftmostReached, going left would create a symmetric path
                return y <= leftmostReached;
            }
            return false;
        }

        private void tryMoveWithSymmetry(int dir, List<PathExplorer> subtasks) {
            int newX = x + DX[dir];
            int newY = y + DY[dir];

            if (!isValid(newX, newY)) return;

            long pos = (long) newX * GRID_SIZE + newY;
            long bitMask = 1L << pos;
            if ((visited & bitMask) != 0) return;

            if (moveIndex == TOTAL_MOVES - 1 && (newX != GRID_SIZE - 1 || newY != 0)) {
                return;
            }

            // Update leftmost column reached
            int newLeftmost = leftmostReached;
            if (newY < leftmostReached) {
                newLeftmost = newY;
            }

            // Create new task or explore further
            if (moveIndex < PARALLEL_THRESHOLD) {
                subtasks.add(new PathExplorer(newX, newY, moveIndex + 1,
                        visited | bitMask, path, newLeftmost));
            } else {
                explorePaths(newX, newY, moveIndex + 1, visited | bitMask,
                        path, newLeftmost);
            }
        }
    }

    private static void explorePaths(int x, int y, int moveIndex, long visited,
                                     String path, int leftmostReached) {
        if (moveIndex == TOTAL_MOVES) {
            if (x == GRID_SIZE - 1 && y == 0) {
                totalPaths.incrementAndGet();
            }
            return;
        }

        if (!canReachEnd(x, y, TOTAL_MOVES - moveIndex, visited)) {
            return;
        }

        char currentMove = path.charAt(moveIndex);
        if (currentMove == '*') {
            for (int dir = 0; dir < 4; dir++) {
                if (dir == 3 && y <= leftmostReached) continue; // Symmetry rule
                tryExploreMove(x, y, moveIndex, visited, path, dir, leftmostReached);
            }
        } else {
            int dir = getDirectionIndex(currentMove);
            if (dir == 3 && y <= leftmostReached) return; // Symmetry rule
            tryExploreMove(x, y, moveIndex, visited, path, dir, leftmostReached);
        }
    }

    private static void tryExploreMove(int x, int y, int moveIndex, long visited,
                                       String path, int dir, int leftmostReached) {
        int newX = x + DX[dir];
        int newY = y + DY[dir];

        if (!isValid(newX, newY)) return;

        long pos = (long) newX * GRID_SIZE + newY;
        long bitMask = 1L << pos;
        if ((visited & bitMask) != 0) return;

        if (moveIndex == TOTAL_MOVES - 1 && (newX != GRID_SIZE - 1 || newY != 0)) {
            return;
        }

        // Update leftmost column reached
        int newLeftmost = leftmostReached;
        if (newY < leftmostReached) {
            newLeftmost = newY;
        }

        explorePaths(newX, newY, moveIndex + 1, visited | bitMask, path, newLeftmost);
    }

    private static boolean canReachEnd(int x, int y, int movesLeft, long visited) {
        // Manhattan distance to end point
        int minMovesToEnd = Math.abs(x - (GRID_SIZE - 1)) + Math.abs(y);
        if (minMovesToEnd > movesLeft) {
            return false;
        }

        // Check for sufficient remaining unvisited cells
        int unvisitedCells = GRID_SIZE * GRID_SIZE - Long.bitCount(visited);
        if (movesLeft > unvisitedCells) {
            return false;
        }

        // Check move parity
        boolean needEvenMoves = ((GRID_SIZE - 1 - x) + y) % 2 == 0;
        boolean haveEvenMoves = movesLeft % 2 == 0;
        return needEvenMoves == haveEvenMoves;
    }

    private static boolean isValid(int x, int y) {
        return x >= 0 && x < GRID_SIZE && y >= 0 && y < GRID_SIZE;
    }

    private static int getDirectionIndex(char direction) {
        return switch (direction) {
            case 'D' -> 0;
            case 'U' -> 1;
            case 'R' -> 2;
            case 'L' -> 3;
            default -> -1;
        };
    }

    private static boolean isValidInput(String path) {
        if (path.length() != TOTAL_MOVES) {
            return false;
        }
        return path.matches("[UDLR*]+");
    }

    private static void showProgress() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime >= UPDATE_INTERVAL) {
            long elapsedSeconds = (currentTime - startTime) / 1000;
            System.out.printf("\rPaths found: %,d, Time elapsed: %ds, Paths/second: %,d",
                    totalPaths.get(), elapsedSeconds,
                    elapsedSeconds > 0 ? totalPaths.get() / elapsedSeconds : 0);
            lastUpdateTime = currentTime;
        }
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Input:\n");
        String path = scanner.nextLine();

        if (!isValidInput(path)) {
            System.out.println("Invalid input. Path must be " + TOTAL_MOVES +
                    " characters long and contain only U, D, L, R, or *");
            return;
        }

        int processors = Runtime.getRuntime().availableProcessors();
        System.out.println("\nUsing " + processors + " processor cores");
        System.out.println("Start point: (0,0)");
        System.out.println("End point: (7,0)");

        ForkJoinPool pool = ForkJoinPool.commonPool();

        startTime = System.currentTimeMillis();
        lastUpdateTime = startTime;

        System.out.println("Starting parallel path exploration with position-based symmetry elimination...\n");

        long initialVisited = 1L;
        PathExplorer rootTask = new PathExplorer(0, 0, 0, initialVisited, path, 0);
        pool.execute(rootTask);

        while (!rootTask.isDone()) {
            showProgress();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        System.out.println("\n\nFinal Results:");
        System.out.println("Total paths found: " + totalPaths.get());
        System.out.println("Time (ms): " + totalTime);
        System.out.printf("Average paths per second: %,.2f%n",
                (totalPaths.get() * 1000.0) / totalTime);

        scanner.close();
    }
}