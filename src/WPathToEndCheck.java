import java.util.Scanner;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;
import java.util.List;

public class WPathToEndCheck {
    // Constants for grid dimensions
    private static final int GRID_SIZE = 8;
    private static final int TOTAL_MOVES = 63;
    private static final int PARALLEL_THRESHOLD = 10;

    // Progress tracking variables
    private static final AtomicLong totalPaths = new AtomicLong(0);
    private static long lastUpdateTime = 0;
    private static long startTime = 0;
    private static final long UPDATE_INTERVAL = 1000;

    // Possible movement directions
    private static final int[] DX = {1, -1, 0, 0};  // Down, Up, Right, Left
    private static final int[] DY = {0, 0, 1, -1};

    /**
     * Enhanced path validation with multiple early elimination checks
     */
    private static boolean canReachEnd(int x, int y, int movesLeft, long visited) {
        // Manhattan distance check to end position (7,0)
        int manhattanDistance = Math.abs(x - (GRID_SIZE - 1)) + Math.abs(y - 0);
        if (manhattanDistance > movesLeft) {
            return false;
        }

        // Check if we have enough moves left
        int unvisitedCells = GRID_SIZE * GRID_SIZE - Long.bitCount(visited);
        if (movesLeft < unvisitedCells) {  // Must have at least enough moves for remaining cells
            return false;
        }

        // Basic trapped check only near the end to avoid false positives
        if (movesLeft < 3) {
            if (isTrapped(x, y, visited)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check if current position is trapped (no valid moves available)
     */
    private static boolean isTrapped(int x, int y, long visited) {
        // Only check if we're not already at the end position
        if (x == GRID_SIZE - 1 && y == 0) {
            return false;
        }

        // Check if any adjacent cell is unvisited
        for (int dir = 0; dir < 4; dir++) {
            int newX = x + DX[dir];
            int newY = y + DY[dir];

            if (isValid(newX, newY)) {
                long pos = (long) newX * GRID_SIZE + newY;
                if ((visited & (1L << pos)) == 0) {
                    return false;  // Found at least one valid move
                }
            }
        }

        return true;  // No valid moves available
    }

    /**
     * Check if there are any unreachable unvisited cells
     */
    private static boolean hasUnreachableRegion(int currentX, int currentY, long visited) {
        // Convert visited bitmap to 2D grid for easier processing
        boolean[][] grid = new boolean[GRID_SIZE][GRID_SIZE];
        for (int i = 0; i < GRID_SIZE; i++) {
            for (int j = 0; j < GRID_SIZE; j++) {
                grid[i][j] = (visited & (1L << (i * GRID_SIZE + j))) != 0;
            }
        }

        // Check each unvisited cell
        for (int i = 0; i < GRID_SIZE; i++) {
            for (int j = 0; j < GRID_SIZE; j++) {
                if (!grid[i][j] && (i != currentX || j != currentY)) {
                    boolean hasUnvisitedNeighbor = false;
                    for (int dir = 0; dir < 4; dir++) {
                        int newX = i + DX[dir];
                        int newY = j + DY[dir];
                        if (isValid(newX, newY) && !grid[newX][newY]) {
                            hasUnvisitedNeighbor = true;
                            break;
                        }
                    }
                    if (!hasUnvisitedNeighbor) {
                        return true;  // Found an isolated unvisited cell
                    }
                }
            }
        }

        return false;
    }

    /**
     * Check if end position is still reachable using flood fill
     */
    private static boolean isEndUnreachable(int currentX, int currentY, long visited) {
        // Don't need to check if we're already at the end
        if (currentX == GRID_SIZE - 1 && currentY == 0) {
            return false;
        }

        // Create a copy of the visited grid for flood fill
        boolean[][] grid = new boolean[GRID_SIZE][GRID_SIZE];
        for (int i = 0; i < GRID_SIZE; i++) {
            for (int j = 0; j < GRID_SIZE; j++) {
                grid[i][j] = (visited & (1L << (i * GRID_SIZE + j))) != 0;
            }
        }

        // Mark current position as visited
        grid[currentX][currentY] = true;

        // Do flood fill from current position
        return !canFloodFillReachEnd(currentX, currentY, grid);
    }

    /**
     * Helper method for flood fill
     */
    private static boolean canFloodFillReachEnd(int x, int y, boolean[][] grid) {
        if (x == GRID_SIZE - 1 && y == 0) {
            return true;  // Reached the end
        }

        grid[x][y] = true;  // Mark as visited

        // Try all directions
        for (int dir = 0; dir < 4; dir++) {
            int newX = x + DX[dir];
            int newY = y + DY[dir];

            if (isValid(newX, newY) && !grid[newX][newY]) {
                if (canFloodFillReachEnd(newX, newY, grid)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static class PathExplorer extends RecursiveAction {
        private final int x, y, moveIndex;
        private final long visited;
        private final String path;
        private final AtomicReference<List<Long>> pathsPerThread;
        private final int threadId;

        public PathExplorer(int x, int y, int moveIndex, long visited, String path,
                            AtomicReference<List<Long>> pathsPerThread, int threadId) {
            this.x = x;
            this.y = y;
            this.moveIndex = moveIndex;
            this.visited = visited;
            this.path = path;
            this.pathsPerThread = pathsPerThread;
            this.threadId = threadId;
        }

        @Override
        protected void compute() {
            // Early termination checks
            if (!canReachEnd(x, y, TOTAL_MOVES - moveIndex, visited)) {
                return;
            }

            // Base case: reached end of path
            if (moveIndex == TOTAL_MOVES) {
                if (x == GRID_SIZE - 1 && y == 0) {
                    totalPaths.incrementAndGet();
                    List<Long> currentPaths = pathsPerThread.get();
                    currentPaths.set(threadId, currentPaths.get(threadId) + 1);
                }
                return;
            }

            char currentMove = path.charAt(moveIndex);
            List<PathExplorer> subtasks = new ArrayList<>();

            // Handle wildcard moves
            if (currentMove == '*') {
                for (int dir = 0; dir < 4; dir++) {
                    int newX = x + DX[dir];
                    int newY = y + DY[dir];

                    long pos = (long) newX * GRID_SIZE + newY;
                    long bitMask = 1L << pos;

                    if (isValid(newX, newY) && (visited & bitMask) == 0) {
                        // Additional check for end position
                        if (moveIndex == TOTAL_MOVES - 1 && (newX != GRID_SIZE - 1 || newY != 0)) {
                            continue;
                        }

                        if (moveIndex < PARALLEL_THRESHOLD) {
                            subtasks.add(new PathExplorer(newX, newY, moveIndex + 1,
                                    visited | bitMask, path, pathsPerThread, threadId));
                        } else {
                            explorePaths(newX, newY, moveIndex + 1, visited | bitMask, path);
                        }
                    }
                }
            } else {
                int dir = getDirectionIndex(currentMove);
                int newX = x + DX[dir];
                int newY = y + DY[dir];

                long pos = (long) newX * GRID_SIZE + newY;
                long bitMask = 1L << pos;

                if (isValid(newX, newY) && (visited & bitMask) == 0) {
                    // Check if this is the last move and it's not reaching the end position
                    if (moveIndex == TOTAL_MOVES - 1 && (newX != GRID_SIZE - 1 || newY != 0)) {
                        return;
                    }
                    explorePaths(newX, newY, moveIndex + 1, visited | bitMask, path);
                }
            }

            if (!subtasks.isEmpty()) {
                invokeAll(subtasks);
            }
        }
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

    private static void explorePaths(int x, int y, int moveIndex, long visited, String path) {
        // Base case: reached end of path
        if (moveIndex == TOTAL_MOVES) {
            if (x == GRID_SIZE - 1 && y == 0) {
                totalPaths.incrementAndGet();
            }
            return;
        }

        char currentMove = path.charAt(moveIndex);

        if (currentMove == '*') {
            // Try all four directions for wildcard
            for (int dir = 0; dir < 4; dir++) {
                int newX = x + DX[dir];
                int newY = y + DY[dir];

                // Skip if move is invalid or cell is already visited
                if (!isValid(newX, newY)) continue;

                long pos = (long) newX * GRID_SIZE + newY;
                long bitMask = 1L << pos;

                if ((visited & bitMask) == 0) {
                    // Early termination check after position validation
                    if (!canReachEnd(newX, newY, TOTAL_MOVES - moveIndex - 1, visited | bitMask)) {
                        continue;
                    }

                    // Additional check for last move
                    if (moveIndex == TOTAL_MOVES - 1 && (newX != GRID_SIZE - 1 || newY != 0)) {
                        continue;
                    }

                    explorePaths(newX, newY, moveIndex + 1, visited | bitMask, path);
                }
            }
        } else {
            // Follow the specified direction
            int dir = getDirectionIndex(currentMove);
            int newX = x + DX[dir];
            int newY = y + DY[dir];

            // Skip if move is invalid
            if (!isValid(newX, newY)) return;

            long pos = (long) newX * GRID_SIZE + newY;
            long bitMask = 1L << pos;

            if ((visited & bitMask) == 0) {
                // Early termination check after position validation
                if (!canReachEnd(newX, newY, TOTAL_MOVES - moveIndex - 1, visited | bitMask)) {
                    return;
                }

                // Additional check for last move
                if (moveIndex == TOTAL_MOVES - 1 && (newX != GRID_SIZE - 1 || newY != 0)) {
                    return;
                }

                explorePaths(newX, newY, moveIndex + 1, visited | bitMask, path);
            }
        }
    }

    private static boolean isValidInput(String path) {
        if (path.length() != TOTAL_MOVES) {
            return false;
        }
        return path.matches("[UDLR*]+");
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

        // Initialize parallel processing
        int processors = Runtime.getRuntime().availableProcessors();
        System.out.println("\nUsing " + processors + " processor cores");
        ForkJoinPool pool = ForkJoinPool.commonPool();

        // Initialize thread-specific counters
        List<Long> initialList = new ArrayList<>();
        for (int i = 0; i < processors; i++) {
            initialList.add(0L);
        }
        AtomicReference<List<Long>> pathsPerThread = new AtomicReference<>(initialList);

        // Start timing
        startTime = System.currentTimeMillis();
        lastUpdateTime = startTime;

        System.out.println("Starting parallel path exploration...\n");

        // Start parallel processing
        long initialVisited = 1L;  // Mark starting position as visited
        PathExplorer rootTask = new PathExplorer(0, 0, 0, initialVisited, path, pathsPerThread, 0);
        pool.execute(rootTask);

        // Show progress while computing
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

        // Show final results
        System.out.println("\n\nFinal Results:");
        System.out.println("Total paths: " + totalPaths.get());
        System.out.println("Time (ms): " + totalTime);
        System.out.printf("Average paths per second: %,.2f%n",
                (totalPaths.get() * 1000.0) / totalTime);

        scanner.close();
    }
}