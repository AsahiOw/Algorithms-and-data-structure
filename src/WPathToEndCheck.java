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

    private static PathPattern pattern;  // Global pattern instance

    /**
     * Class to analyze fixed positions in the path pattern
     */
    private static class PathPattern {
        private final String path;
        private final int[] fixedRPositions;
        private final int[] fixedDPositions;
        private final int[] fixedLPositions;

        public PathPattern(String path) {
            this.path = path;
            List<Integer> rPos = new ArrayList<>();
            List<Integer> dPos = new ArrayList<>();
            List<Integer> lPos = new ArrayList<>();

            for (int i = 0; i < path.length(); i++) {
                char c = path.charAt(i);
                if (c == 'R') rPos.add(i);
                else if (c == 'D') dPos.add(i);
                else if (c == 'L') lPos.add(i);
            }

            fixedRPositions = rPos.stream().mapToInt(Integer::intValue).toArray();
            fixedDPositions = dPos.stream().mapToInt(Integer::intValue).toArray();
            fixedLPositions = lPos.stream().mapToInt(Integer::intValue).toArray();
        }

        public boolean canSatisfyRemainingMoves(int x, int y, int moveIndex) {
            int remainingRight = 0;
            int remainingDown = 0;
            int remainingLeft = 0;

            // Count remaining fixed moves after current position
            for (int pos : fixedRPositions) {
                if (pos >= moveIndex) remainingRight++;
            }
            for (int pos : fixedDPositions) {
                if (pos >= moveIndex) remainingDown++;
            }
            for (int pos : fixedLPositions) {
                if (pos >= moveIndex) remainingLeft++;
            }

            // Check if we can make all required moves from current position
            if (y + remainingRight >= GRID_SIZE) return false;
            if (x + remainingDown >= GRID_SIZE) return false;
            if (y - remainingLeft < 0) return false;

            return true;
        }

        public char getMoveAt(int index) {
            return path.charAt(index);
        }
    }

    /**
     * Enhanced path validation with early elimination checks
     */
    private static boolean canReachEnd(int x, int y, int moveIndex, int movesLeft, long visited) {
        // Manhattan distance check to end position (7,0)
        int manhattanDistance = Math.abs(x - (GRID_SIZE - 1)) + Math.abs(y - 0);
        if (manhattanDistance > movesLeft) {
            return false;
        }

        // Check if we have enough moves for remaining cells
        int unvisitedCells = GRID_SIZE * GRID_SIZE - Long.bitCount(visited);
        if (movesLeft < unvisitedCells) {
            return false;
        }

        // Check if we can satisfy remaining fixed moves from current position
        if (!pattern.canSatisfyRemainingMoves(x, y, moveIndex)) {
            return false;
        }

        // Only perform trapped check near the end
        if (movesLeft < 5) {
            boolean hasUnvisitedNeighbor = false;
            for (int dir = 0; dir < 4; dir++) {
                int newX = x + DX[dir];
                int newY = y + DY[dir];
                if (isValid(newX, newY)) {
                    long pos = (long) newX * GRID_SIZE + newY;
                    if ((visited & (1L << pos)) == 0) {
                        hasUnvisitedNeighbor = true;
                        break;
                    }
                }
            }
            if (!hasUnvisitedNeighbor && movesLeft > 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Path exploration logic with early elimination
     */
    private static void explorePaths(int x, int y, int moveIndex, long visited, String path) {
        // Base case: reached end of path
        if (moveIndex == TOTAL_MOVES) {
            if (x == GRID_SIZE - 1 && y == 0) {
                totalPaths.incrementAndGet();
            }
            return;
        }

        if (!canReachEnd(x, y, moveIndex, TOTAL_MOVES - moveIndex, visited)) {
            return;
        }

        char currentMove = path.charAt(moveIndex);

        if (currentMove == '*') {
            // Try all four directions for wildcard
            for (int dir = 0; dir < 4; dir++) {
                int newX = x + DX[dir];
                int newY = y + DY[dir];

                if (!isValid(newX, newY)) continue;

                long pos = (long) newX * GRID_SIZE + newY;
                long bitMask = 1L << pos;

                if ((visited & bitMask) == 0) {
                    // Additional check for last move
                    if (moveIndex == TOTAL_MOVES - 1 && (newX != GRID_SIZE - 1 || newY != 0)) {
                        continue;
                    }

                    explorePaths(newX, newY, moveIndex + 1, visited | bitMask, path);
                }
            }
        } else {
            int dir = getDirectionIndex(currentMove);
            if (dir == -1) return;  // Invalid direction

            int newX = x + DX[dir];
            int newY = y + DY[dir];

            if (!isValid(newX, newY)) return;

            long pos = (long) newX * GRID_SIZE + newY;
            long bitMask = 1L << pos;

            if ((visited & bitMask) == 0) {
                // Additional check for last move
                if (moveIndex == TOTAL_MOVES - 1 && (newX != GRID_SIZE - 1 || newY != 0)) {
                    return;
                }

                explorePaths(newX, newY, moveIndex + 1, visited | bitMask, path);
            }
        }
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
            if (!canReachEnd(x, y, moveIndex, TOTAL_MOVES - moveIndex, visited)) {
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

            if (currentMove == '*') {
                for (int dir = 0; dir < 4; dir++) {
                    int newX = x + DX[dir];
                    int newY = y + DY[dir];

                    if (!isValid(newX, newY)) continue;

                    long pos = (long) newX * GRID_SIZE + newY;
                    long bitMask = 1L << pos;

                    if ((visited & bitMask) == 0) {
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
                if (dir != -1) {
                    int newX = x + DX[dir];
                    int newY = y + DY[dir];

                    if (isValid(newX, newY)) {
                        long pos = (long) newX * GRID_SIZE + newY;
                        long bitMask = 1L << pos;

                        if ((visited & bitMask) == 0) {
                            if (moveIndex == TOTAL_MOVES - 1 && (newX != GRID_SIZE - 1 || newY != 0)) {
                                return;
                            }
                            explorePaths(newX, newY, moveIndex + 1, visited | bitMask, path);
                        }
                    }
                }
            }

            if (!subtasks.isEmpty()) {
                invokeAll(subtasks);
            }
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

        pattern = new PathPattern(path);

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