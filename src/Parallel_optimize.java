import java.util.Scanner;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicLong;
import java.util.ArrayList;
import java.util.List;

public class Parallel_optimize {
    // Constants for grid dimensions
    private static final int GRID_SIZE = 8;
    private static final int TOTAL_MOVES = 63;
    private static final int PARALLEL_THRESHOLD = 8; // Adjusted threshold for parallelization

    // Progress tracking variables
    private static long startTime = 0;
    private static long lastUpdateTime = 0;
    private static final long UPDATE_INTERVAL = 1000;

    // Possible movement directions
    private static final int[] DX = {1, -1, 0, 0};  // Down, Up, Right, Left
    private static final int[] DY = {0, 0, 1, -1};

    private static class PathFinder extends RecursiveTask<Long> {
        private final int x, y, moveIndex;
        private final long visited;
        private final String path;

        public PathFinder(int x, int y, int moveIndex, long visited, String path) {
            this.x = x;
            this.y = y;
            this.moveIndex = moveIndex;
            this.visited = visited;
            this.path = path;
        }

        @Override
        protected Long compute() {
            // Base case: reached end of path
            if (moveIndex == TOTAL_MOVES) {
                return (x == GRID_SIZE - 1 && y == 0) ? 1L : 0L;
            }

            // Early termination checks
            if (!canReachEnd(x, y, TOTAL_MOVES - moveIndex, visited)) {
                return 0L;
            }

            char currentMove = path.charAt(moveIndex);

            // Handle single direction moves
            if (currentMove != '*') {
                return processDirectionalMove(currentMove);
            }

            // Handle wildcard moves with potential parallelization
            if (moveIndex <= PARALLEL_THRESHOLD) {
                return processWildcardMoveParallel();
            } else {
                return processWildcardMoveSequential();
            }
        }

        private Long processDirectionalMove(char move) {
            int dir = getDirectionIndex(move);
            int newX = x + DX[dir];
            int newY = y + DY[dir];

            if (!isValid(newX, newY)) return 0L;

            long pos = (long) newX * GRID_SIZE + newY;
            long bitMask = 1L << pos;

            if ((visited & bitMask) != 0) return 0L;

            // Last move must end at target position
            if (moveIndex == TOTAL_MOVES - 1 && (newX != GRID_SIZE - 1 || newY != 0)) {
                return 0L;
            }

            return new PathFinder(newX, newY, moveIndex + 1, visited | bitMask, path).compute();
        }

        private Long processWildcardMoveParallel() {
            List<PathFinder> subtasks = new ArrayList<>();

            for (int dir = 0; dir < 4; dir++) {
                int newX = x + DX[dir];
                int newY = y + DY[dir];

                if (!isValid(newX, newY)) continue;

                long pos = (long) newX * GRID_SIZE + newY;
                long bitMask = 1L << pos;

                if ((visited & bitMask) != 0) continue;

                // Skip invalid end positions
                if (moveIndex == TOTAL_MOVES - 1 && (newX != GRID_SIZE - 1 || newY != 0)) {
                    continue;
                }

                subtasks.add(new PathFinder(newX, newY, moveIndex + 1, visited | bitMask, path));
            }

            return ForkJoinTask.invokeAll(subtasks)
                    .stream()
                    .mapToLong(ForkJoinTask::join)
                    .sum();
        }

        private Long processWildcardMoveSequential() {
            long totalPaths = 0;

            for (int dir = 0; dir < 4; dir++) {
                int newX = x + DX[dir];
                int newY = y + DY[dir];

                if (!isValid(newX, newY)) continue;

                long pos = (long) newX * GRID_SIZE + newY;
                long bitMask = 1L << pos;

                if ((visited & bitMask) != 0) continue;

                if (moveIndex == TOTAL_MOVES - 1 && (newX != GRID_SIZE - 1 || newY != 0)) {
                    continue;
                }

                totalPaths += new PathFinder(newX, newY, moveIndex + 1, visited | bitMask, path).compute();
            }

            return totalPaths;
        }
    }

    private static boolean canReachEnd(int x, int y, int movesLeft, long visited) {
        // If not enough moves left to reach the end point
        int minMovesToEnd = Math.abs(x - (GRID_SIZE - 1)) + Math.abs(y);
        if (minMovesToEnd > movesLeft) return false;

        // If too many moves left compared to unvisited cells
        int unvisitedCells = GRID_SIZE * GRID_SIZE - Long.bitCount(visited);
        if (movesLeft > unvisitedCells) return false;

        return true;
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
        if (path.length() != TOTAL_MOVES) return false;
        return path.matches("[UDLR*]+");
    }

    private static void showProgress(long paths) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime >= UPDATE_INTERVAL) {
            long elapsedSeconds = (currentTime - startTime) / 1000;
            System.out.printf("\rPaths found: %,d, Time elapsed: %ds, Paths/second: %,d",
                    paths, elapsedSeconds,
                    elapsedSeconds > 0 ? paths / elapsedSeconds : 0);
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

        ForkJoinPool pool = new ForkJoinPool(processors);

        startTime = System.currentTimeMillis();
        lastUpdateTime = startTime;

        System.out.println("Starting parallel path exploration...\n");

        long result = pool.invoke(new PathFinder(0, 0, 0, 1L, path));

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        System.out.println("\n\nFinal Results:");
        System.out.println("Total paths: " + result);
        System.out.println("Time (ms): " + totalTime);
        System.out.printf("Average paths per second: %,.2f%n",
                (result * 1000.0) / totalTime);

        scanner.close();
        pool.shutdown();
    }
}