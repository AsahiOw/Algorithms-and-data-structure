package ADT_Self_Implement;

import java.util.Scanner;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class TotalPath2 {
    // Static constants for grid configuration and movement
    private static final int GRID_SIZE = 8;
    private static final int TOTAL_MOVES = 63;
    private static final int PARALLEL_THRESHOLD = 10;
    private static final int[] DX = {1, -1, 0, 0};  // Down, Up, Right, Left
    private static final int[] DY = {0, 0, 1, -1};
    private static final long UPDATE_INTERVAL = 1000;

    // Progress tracking variables
    private static final AtomicLong totalPaths = new AtomicLong(0);
    private static long lastUpdateTime = 0;
    private static long startTime = 0;

    /**
     * Inner class that handles path exploration using ForkJoin framework
     */
    public static class PathExplorer extends RecursiveAction {
        private final int x, y, moveIndex;
        private final long visited;
        private final String path;
        private final AtomicReference<DynamicArray> pathsPerThread;
        private final int threadId;

        public PathExplorer(int x, int y, int moveIndex, long visited, String path,
                            AtomicReference<DynamicArray> pathsPerThread, int threadId) {
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
            if (!canReachEnd(x, y, TOTAL_MOVES - moveIndex, visited)) {
                return;
            }

            if (isEndOfPath()) {
                handleEndOfPath();
                return;
            }

            processCurrentMove();
        }

        // Checks if we've reached the end of the path
        private boolean isEndOfPath() {
            return moveIndex == TOTAL_MOVES;
        }

        // Handles end-of-path logic, incrementing counters if valid
        private void handleEndOfPath() {
            if (x == GRID_SIZE - 1 && y == 0) {
                totalPaths.incrementAndGet();
                DynamicArray currentPaths = pathsPerThread.get();
                currentPaths.set(threadId, currentPaths.get(threadId) + 1);
            }
        }

        // Processes the current move based on whether it's a wildcard or directional
        private void processCurrentMove() {
            char currentMove = path.charAt(moveIndex);
            TaskQueue subtasks = new TaskQueue();

            if (currentMove == '*') {
                handleWildcardMove(subtasks);
            } else {
                handleDirectionalMove(currentMove);
            }

            if (!subtasks.isEmpty()) {
                invokeAll(subtasks.getAll());
            }
        }

        // Handles wildcard moves by exploring all possible directions
        private void handleWildcardMove(TaskQueue subtasks) {
            for (int dir = 0; dir < 4; dir++) {
                processMove(dir, subtasks);
            }
        }

        // Handles specific directional moves
        private void handleDirectionalMove(char move) {
            int dir = getDirectionIndex(move);
            processMove(dir, null);
        }

        // Processes a single move in a given direction
        private void processMove(int dir, TaskQueue subtasks) {
            int newX = x + DX[dir];
            int newY = y + DY[dir];

            if (isValidMove(newX, newY)) {
                long pos = (long) newX * GRID_SIZE + newY;
                long newVisited = visited | (1L << pos);

                if (moveIndex < PARALLEL_THRESHOLD && subtasks != null) {
                    subtasks.add(new PathExplorer(newX, newY, moveIndex + 1,
                            newVisited, path, pathsPerThread, threadId));
                } else {
                    explorePaths(newX, newY, moveIndex + 1, newVisited, path);
                }
            }
        }

        // Validates if a move to the new position is legal
        private boolean isValidMove(int newX, int newY) {
            if (!isValid(newX, newY)) return false;

            long pos = (long) newX * GRID_SIZE + newY;
            long bitMask = 1L << pos;
            if ((visited & bitMask) != 0) return false;

            if (moveIndex == TOTAL_MOVES - 1) {
                return newX == GRID_SIZE - 1 && newY == 0;
            }

            return true;
        }
    }

    // Checks if it's still possible to reach the end from current position
    private static boolean canReachEnd(int x, int y, int movesLeft, long visited) {
        if (!hasEnoughMovesToReachEnd(x, y, movesLeft)) {
            return false;
        }

        if (hasTooManyMovesLeft(movesLeft, visited)) {
            return false;
        }

        return !hasIsolatedUnvisitedCells(x, y, visited);
    }

    // Checks if minimum moves required is less than moves available
    private static boolean hasEnoughMovesToReachEnd(int x, int y, int movesLeft) {
        int minMovesToEnd = Math.abs(x - (GRID_SIZE - 1)) + Math.abs(y);
        return minMovesToEnd <= movesLeft;
    }

    // Checks if there are too many moves left compared to unvisited cells
    private static boolean hasTooManyMovesLeft(int movesLeft, long visited) {
        int unvisitedCells = GRID_SIZE * GRID_SIZE - Long.bitCount(visited);
        return movesLeft > unvisitedCells;
    }

    // Checks for isolated unvisited cells that can't be reached
    private static boolean hasIsolatedUnvisitedCells(int currentX, int currentY, long visited) {
        if (Long.bitCount(visited) <= GRID_SIZE * GRID_SIZE - 10) {
            return false;
        }

        boolean[][] grid = convertToGrid(visited);
        return checkForIsolatedCells(grid, currentX, currentY);
    }

    // Converts visited bitset to 2D grid representation
    private static boolean[][] convertToGrid(long visited) {
        boolean[][] grid = new boolean[GRID_SIZE][GRID_SIZE];
        for (int i = 0; i < GRID_SIZE; i++) {
            for (int j = 0; j < GRID_SIZE; j++) {
                grid[i][j] = (visited & (1L << (i * GRID_SIZE + j))) != 0;
            }
        }
        return grid;
    }

    // Checks entire grid for isolated unvisited cells
    private static boolean checkForIsolatedCells(boolean[][] grid, int currentX, int currentY) {
        for (int i = 0; i < GRID_SIZE; i++) {
            for (int j = 0; j < GRID_SIZE; j++) {
                if (!grid[i][j] && (i != currentX || j != currentY)) {
                    if (isIsolatedCell(grid, i, j)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // Checks if a specific cell is isolated (no unvisited neighbors)
    private static boolean isIsolatedCell(boolean[][] grid, int x, int y) {
        int accessibleNeighbors = 0;
        for (int dir = 0; dir < 4; dir++) {
            int newX = x + DX[dir];
            int newY = y + DY[dir];
            if (isValid(newX, newY) && !grid[newX][newY]) {
                accessibleNeighbors++;
            }
        }
        return accessibleNeighbors == 0;
    }

    // Displays progress of path finding
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

    // Validates if coordinates are within grid bounds
    private static boolean isValid(int x, int y) {
        return x >= 0 && x < GRID_SIZE && y >= 0 && y < GRID_SIZE;
    }

    // Converts direction character to index
    private static int getDirectionIndex(char direction) {
        return switch (direction) {
            case 'D' -> 0;
            case 'U' -> 1;
            case 'R' -> 2;
            case 'L' -> 3;
            default -> -1;
        };
    }

    // Validates input path string
    private static boolean isValidInput(String path) {
        if (path.length() != TOTAL_MOVES) {
            return false;
        }
        return path.matches("[UDLR*]+");
    }

    // Explores all possible paths from current position
    private static void explorePaths(int x, int y, int moveIndex, long visited, String path) {
        if (moveIndex == TOTAL_MOVES) {
            if (x == GRID_SIZE - 1 && y == 0) {
                totalPaths.incrementAndGet();
            }
            return;
        }

        char currentMove = path.charAt(moveIndex);
        if (currentMove == '*') {
            exploreWildcardMove(x, y, moveIndex, visited, path);
        } else {
            exploreDirectionalMove(x, y, moveIndex, visited, path, currentMove);
        }
    }

    // Handles wildcard move exploration
    private static void exploreWildcardMove(int x, int y, int moveIndex, long visited, String path) {
        for (int dir = 0; dir < 4; dir++) {
            processSingleMove(x, y, moveIndex, visited, path, dir);
        }
    }

    // Handles directional move exploration
    private static void exploreDirectionalMove(int x, int y, int moveIndex, long visited, String path, char move) {
        int dir = getDirectionIndex(move);
        processSingleMove(x, y, moveIndex, visited, path, dir);
    }

    // Processes a single move in the path
    private static void processSingleMove(int x, int y, int moveIndex, long visited, String path, int dir) {
        int newX = x + DX[dir];
        int newY = y + DY[dir];

        if (!isValid(newX, newY)) return;

        long pos = (long) newX * GRID_SIZE + newY;
        long bitMask = 1L << pos;

        if ((visited & bitMask) != 0) return;

        if (moveIndex == TOTAL_MOVES - 1 && (newX != GRID_SIZE - 1 || newY != 0)) {
            return;
        }

        explorePaths(newX, newY, moveIndex + 1, visited | bitMask, path);
    }

    // Main entry point of the program
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Input:\n");
        String path = scanner.nextLine();

        if (!isValidInput(path)) {
            System.out.println("Invalid input. Path must be " + TOTAL_MOVES +
                    " characters long and contain only U, D, L, R, or *");
            return;
        }

        runParallelPathExploration(path);
        scanner.close();
    }

    // Initializes and runs parallel path exploration
    private static void runParallelPathExploration(String path) {
        int processors = Runtime.getRuntime().availableProcessors();
        System.out.println("\nUsing " + processors + " processor cores");

        ForkJoinPool pool = ForkJoinPool.commonPool();
        AtomicReference<DynamicArray> pathsPerThread = initializeThreadCounters(processors);

        startTiming();
        System.out.println("Starting parallel path exploration...\n");

        PathExplorer rootTask = new PathExplorer(0, 0, 0, 1L, path, pathsPerThread, 0);
        executeAndMonitor(pool, rootTask);

        displayResults();
    }

    // Initializes thread-specific counters
    private static AtomicReference<DynamicArray> initializeThreadCounters(int processors) {
        DynamicArray initialArray = new DynamicArray(processors);
        for (int i = 0; i < processors; i++) {
            initialArray.add(0);
        }
        return new AtomicReference<>(initialArray);
    }

    // Initializes timing variables
    private static void startTiming() {
        startTime = System.currentTimeMillis();
        lastUpdateTime = startTime;
    }

    // Executes and monitors the path exploration task
    private static void executeAndMonitor(ForkJoinPool pool, PathExplorer rootTask) {
        pool.execute(rootTask);
        while (!rootTask.isDone()) {
            showProgress();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    // Displays final results of path exploration
    private static void displayResults() {
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        System.out.println("\n\nFinal Results:");
        System.out.println("Total paths: " + totalPaths.get());
        System.out.println("Time (ms): " + totalTime);
        System.out.printf("Average paths per second: %,.2f%n",
                (totalPaths.get() * 1000.0) / totalTime);
    }
}