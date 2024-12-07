import java.util.Scanner;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;
import java.util.List;

public class Pre_compute_valid_move {
    // Constants for grid dimensions
    private static final int GRID_SIZE = 8;
    private static final int TOTAL_MOVES = 63;
    private static final int PARALLEL_THRESHOLD = 10;

    // Progress tracking variables
    private static final AtomicLong totalPaths = new AtomicLong(0);
    private static long lastUpdateTime = 0;
    private static long startTime = 0;
    private static final long UPDATE_INTERVAL = 1000;

    // Direction arrays for movement
    private static final int[] DX = {1, -1, 0, 0};  // Down, Up, Right, Left
    private static final int[] DY = {0, 0, 1, -1};

    // Pre-computed boundary check tables
    private static final boolean[][] canMoveDown;
    private static final boolean[][] canMoveUp;
    private static final boolean[][] canMoveRight;
    private static final boolean[][] canMoveLeft;

    // Initialize boundary check tables
    static {
        canMoveDown = new boolean[GRID_SIZE][GRID_SIZE];
        canMoveUp = new boolean[GRID_SIZE][GRID_SIZE];
        canMoveRight = new boolean[GRID_SIZE][GRID_SIZE];
        canMoveLeft = new boolean[GRID_SIZE][GRID_SIZE];

        for (int x = 0; x < GRID_SIZE; x++) {
            for (int y = 0; y < GRID_SIZE; y++) {
                canMoveDown[x][y] = x < GRID_SIZE - 1;
                canMoveUp[x][y] = x > 0;
                canMoveRight[x][y] = y < GRID_SIZE - 1;
                canMoveLeft[x][y] = y > 0;
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
            if (moveIndex == TOTAL_MOVES) {
                if (x == GRID_SIZE - 1 && y == 0) {
                    totalPaths.incrementAndGet();
                    List<Long> currentPaths = pathsPerThread.get();
                    currentPaths.set(threadId, currentPaths.get(threadId) + 1);
                }
                return;
            }

            if (!canReachEnd(x, y, TOTAL_MOVES - moveIndex, visited)) {
                return;
            }

            char currentMove = path.charAt(moveIndex);
            List<PathExplorer> subtasks = new ArrayList<>();

            if (currentMove == '*') {
                for (int dir = 0; dir < 4; dir++) {
                    exploreMove(dir, subtasks);
                }
            } else {
                int dir = getDirectionIndex(currentMove);
                if (dir != -1) {
                    exploreMove(dir, subtasks);
                }
            }

            if (!subtasks.isEmpty()) {
                invokeAll(subtasks);
            }
        }

        private void exploreMove(int dir, List<PathExplorer> subtasks) {
            boolean canMove = switch(dir) {
                case 0 -> canMoveDown[x][y];
                case 1 -> canMoveUp[x][y];
                case 2 -> canMoveRight[x][y];
                case 3 -> canMoveLeft[x][y];
                default -> false;
            };

            if (!canMove) {
                return;
            }

            int newX = x + DX[dir];
            int newY = y + DY[dir];

            long pos = (long) newX * GRID_SIZE + newY;
            long bitMask = 1L << pos;
            if ((visited & bitMask) != 0) {
                return;
            }

            if (moveIndex == TOTAL_MOVES - 1 && (newX != GRID_SIZE - 1 || newY != 0)) {
                return;
            }

            if (moveIndex < PARALLEL_THRESHOLD) {
                subtasks.add(new PathExplorer(newX, newY, moveIndex + 1,
                        visited | bitMask, path, pathsPerThread, threadId));
            } else {
                explorePaths(newX, newY, moveIndex + 1, visited | bitMask, path);
            }
        }
    }

    private static void explorePaths(int x, int y, int moveIndex, long visited, String path) {
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
                exploreMoveDirection(x, y, dir, moveIndex, visited, path);
            }
        } else {
            int dir = getDirectionIndex(currentMove);
            if (dir != -1) {
                exploreMoveDirection(x, y, dir, moveIndex, visited, path);
            }
        }
    }

    private static void exploreMoveDirection(int x, int y, int dir, int moveIndex, long visited, String path) {
        boolean canMove = switch(dir) {
            case 0 -> canMoveDown[x][y];
            case 1 -> canMoveUp[x][y];
            case 2 -> canMoveRight[x][y];
            case 3 -> canMoveLeft[x][y];
            default -> false;
        };

        if (!canMove) {
            return;
        }

        int newX = x + DX[dir];
        int newY = y + DY[dir];

        long pos = (long) newX * GRID_SIZE + newY;
        long bitMask = 1L << pos;
        if ((visited & bitMask) != 0) {
            return;
        }

        if (moveIndex == TOTAL_MOVES - 1 && (newX != GRID_SIZE - 1 || newY != 0)) {
            return;
        }

        explorePaths(newX, newY, moveIndex + 1, visited | bitMask, path);
    }

    private static boolean canReachEnd(int x, int y, int movesLeft, long visited) {
        int minMovesToEnd = Math.abs(x - (GRID_SIZE - 1)) + Math.abs(y);
        if (minMovesToEnd > movesLeft) {
            return false;
        }

        int unvisitedCells = GRID_SIZE * GRID_SIZE - Long.bitCount(visited);
        if (movesLeft > unvisitedCells) {
            return false;
        }

        return !hasIsolatedUnvisitedCells(x, y, visited);
    }

    private static boolean hasIsolatedUnvisitedCells(int currentX, int currentY, long visited) {
        if (Long.bitCount(visited) > GRID_SIZE * GRID_SIZE - 10) {
            boolean[][] grid = new boolean[GRID_SIZE][GRID_SIZE];
            for (int i = 0; i < GRID_SIZE; i++) {
                for (int j = 0; j < GRID_SIZE; j++) {
                    grid[i][j] = (visited & (1L << (i * GRID_SIZE + j))) != 0;
                }
            }

            for (int i = 0; i < GRID_SIZE; i++) {
                for (int j = 0; j < GRID_SIZE; j++) {
                    if (!grid[i][j] && (i != currentX || j != currentY)) {
                        int accessibleNeighbors = 0;
                        for (int dir = 0; dir < 4; dir++) {
                            int newX = i + DX[dir];
                            int newY = j + DY[dir];
                            boolean canMove = switch(dir) {
                                case 0 -> canMoveDown[i][j];
                                case 1 -> canMoveUp[i][j];
                                case 2 -> canMoveRight[i][j];
                                case 3 -> canMoveLeft[i][j];
                                default -> false;
                            };
                            if (canMove && !grid[newX][newY]) {
                                accessibleNeighbors++;
                            }
                        }
                        if (accessibleNeighbors == 0) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
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

        int processors = Runtime.getRuntime().availableProcessors();
        System.out.println("\nUsing " + processors + " processor cores");
        ForkJoinPool pool = ForkJoinPool.commonPool();

        List<Long> initialList = new ArrayList<>();
        for (int i = 0; i < processors; i++) {
            initialList.add(0L);
        }
        AtomicReference<List<Long>> pathsPerThread = new AtomicReference<>(initialList);

        startTime = System.currentTimeMillis();
        lastUpdateTime = startTime;

        System.out.println("Starting parallel path exploration...\n");

        long initialVisited = 1L;
        PathExplorer rootTask = new PathExplorer(0, 0, 0, initialVisited, path, pathsPerThread, 0);
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
        System.out.println("Total paths: " + totalPaths.get());
        System.out.println("Time (ms): " + totalTime);
        System.out.printf("Average paths per second: %,.2f%n",
                (totalPaths.get() * 1000.0) / totalTime);

        scanner.close();
    }
}