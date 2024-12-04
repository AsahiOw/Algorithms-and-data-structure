import java.util.Scanner;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;
import java.util.List;

public class nonearlyeliminate {
    // Constants for grid dimensions
    private static final int GRID_SIZE = 8;
    private static final int TOTAL_MOVES = 63;
    private static final int PARALLEL_THRESHOLD = 10; // Depth at which to stop parallelizing

    // Progress tracking variables
    private static final AtomicLong totalPaths = new AtomicLong(0);
    private static long lastUpdateTime = 0;
    private static long startTime = 0;
    private static final long UPDATE_INTERVAL = 1000; // Update progress every 1 second

    // Possible movement directions
    private static final int[] DX = {1, -1, 0, 0};  // Down, Up, Right, Left
    private static final int[] DY = {0, 0, 1, -1};

    /**
     * PathExplorer class that extends RecursiveAction for parallel processing
     */
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

                    long pos = newX * GRID_SIZE + newY;
                    long bitMask = 1L << pos;

                    if (isValid(newX, newY) && (visited & bitMask) == 0) {
                        if (moveIndex < PARALLEL_THRESHOLD) {
                            // Create new subtask for parallel processing
                            subtasks.add(new PathExplorer(newX, newY, moveIndex + 1,
                                    visited | bitMask, path, pathsPerThread, threadId));
                        } else {
                            // Process sequentially beyond threshold
                            explorePaths(newX, newY, moveIndex + 1, visited | bitMask, path);
                        }
                    }
                }
            } else {
                // Handle specific direction
                int dir = getDirectionIndex(currentMove);
                int newX = x + DX[dir];
                int newY = y + DY[dir];

                long pos = newX * GRID_SIZE + newY;
                long bitMask = 1L << pos;

                if (isValid(newX, newY) && (visited & bitMask) == 0) {
                    explorePaths(newX, newY, moveIndex + 1, visited | bitMask, path);
                }
            }

            // Invoke all subtasks in parallel
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
        switch (direction) {
            case 'D': return 0;
            case 'U': return 1;
            case 'R': return 2;
            case 'L': return 3;
            default: return -1;
        }
    }

    private static void explorePaths(int x, int y, int moveIndex, long visited, String path) {
        if (moveIndex == TOTAL_MOVES) {
            if (x == GRID_SIZE - 1 && y == 0) {
                totalPaths.incrementAndGet();
            }
            return;
        }

        char currentMove = path.charAt(moveIndex);

        if (currentMove == '*') {
            for (int dir = 0; dir < 4; dir++) {
                int newX = x + DX[dir];
                int newY = y + DY[dir];

                long pos = newX * GRID_SIZE + newY;
                long bitMask = 1L << pos;

                if (isValid(newX, newY) && (visited & bitMask) == 0) {
                    explorePaths(newX, newY, moveIndex + 1, visited | bitMask, path);
                }
            }
        } else {
            int dir = getDirectionIndex(currentMove);
            int newX = x + DX[dir];
            int newY = y + DY[dir];

            long pos = newX * GRID_SIZE + newY;
            long bitMask = 1L << pos;

            if (isValid(newX, newY) && (visited & bitMask) == 0) {
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
        long initialVisited = 1L;
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

        // Show per-thread statistics
        System.out.println("\nPaths found per thread:");
        List<Long> finalPaths = pathsPerThread.get();
        for (int i = 0; i < finalPaths.size(); i++) {
            System.out.printf("Thread %d: %,d paths%n", i, finalPaths.get(i));
        }

        scanner.close();
    }
}