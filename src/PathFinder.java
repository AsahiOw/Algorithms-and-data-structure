import java.util.concurrent.*;
import java.util.*;

public class PathFinder {
    private static final int SIZE = 8;
    private static final int MOVES = 63;
    private static final int END_ROW = 7;
    private static final int END_COL = 0;

    // Direction vectors: U, D, L, R
    private static final int[] DX = {0, 0, -1, 1};
    private static final int[] DY = {-1, 1, 0, 0};

    private static final long[][] NEIGHBOR_MASKS = new long[SIZE * SIZE][4];
    private static final int[][] DISTANCE_TO_END = new int[SIZE][SIZE];
    private static final int PROCESSORS = Runtime.getRuntime().availableProcessors();

    private static final ExecutorService THREAD_POOL = Executors.newFixedThreadPool(PROCESSORS);
    private final Map<String, Long> cache = Collections.synchronizedMap(new HashMap<>());

    static {
        initializeLookupTables();
    }

    /**
     * Precomputes distance and neighbor masks for all positions on the grid.
     */
    private static void initializeLookupTables() {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                DISTANCE_TO_END[r][c] = Math.abs(r - END_ROW) + Math.abs(c - END_COL);
                int pos = r * SIZE + c;

                for (int dir = 0; dir < 4; dir++) {
                    int nr = r + DY[dir];
                    int nc = c + DX[dir];
                    if (isValid(nr, nc)) {
                        NEIGHBOR_MASKS[pos][dir] = 1L << (nr * SIZE + nc);
                    }
                }
            }
        }
    }

    /**
     * Main method to count all possible paths for a given pattern.
     */
    public long countPaths(String pattern) {
        if (!isValidPattern(pattern)) {
            throw new IllegalArgumentException("Invalid pattern.");
        }

        if (cache.containsKey(pattern)) return cache.get(pattern);

        int wildcards = countWildcards(pattern);
        long result = (wildcards > 15) ? parallelPathSearch(pattern) : sequentialPathSearch(pattern);

        cache.put(pattern, result);
        return result;
    }

    /**
     * Sequential path search using recursive backtracking.
     */
    private long sequentialPathSearch(String pattern) {
        return backtrack(0, 0, pattern.toCharArray(), 0, 1L);
    }

    /**
     * Parallel path search using multithreading for initial moves.
     */
    private long parallelPathSearch(String pattern) {
        List<Future<Long>> futures = new ArrayList<>();
        long visited = 1L; // Start at (0, 0)

        char firstMove = pattern.charAt(0);
        if (firstMove == '*') {
            for (int dir = 0; dir < 4; dir++) {
                int newRow = DY[dir];
                int newCol = DX[dir];
                if (isValid(newRow, newCol)) {
                    long newVisited = visited | (1L << (newRow * SIZE + newCol));
                    futures.add(THREAD_POOL.submit(() ->
                            backtrack(newRow, newCol, pattern.toCharArray(), 1, newVisited)
                    ));
                }
            }
        } else {
            int dir = getDirectionIndex(firstMove);
            int newRow = DY[dir];
            int newCol = DX[dir];
            if (isValid(newRow, newCol)) {
                long newVisited = visited | (1L << (newRow * SIZE + newCol));
                futures.add(THREAD_POOL.submit(() ->
                        backtrack(newRow, newCol, pattern.toCharArray(), 1, newVisited)
                ));
            }
        }

        return collectResults(futures);
    }

    /**
     * Recursive backtracking with bitmasking for visited positions.
     */
    private long backtrack(int row, int col, char[] pattern, int depth, long visited) {
        // Check if indices are valid before proceeding
        if (!isValid(row, col)) {
            return 0;
        }

        // Check if we've completed the path
        if (depth == MOVES) {
            return (row == END_ROW && col == END_COL) ? 1 : 0;
        }

        // Early pruning for unreachable paths
        if (!canReachEnd(row, col, depth, visited)) {
            return 0;
        }

        long paths = 0;
        int position = row * SIZE + col;
        char move = pattern[depth];

        if (move == '*') {
            for (int dir = 0; dir < 4; dir++) {
                long neighborMask = NEIGHBOR_MASKS[position][dir];
                if (neighborMask != 0 && (visited & neighborMask) == 0) {
                    int newRow = row + DY[dir];
                    int newCol = col + DX[dir];
                    paths += backtrack(newRow, newCol, pattern, depth + 1, visited | neighborMask);
                }
            }
        } else {
            int dir = getDirectionIndex(move);
            long neighborMask = NEIGHBOR_MASKS[position][dir];
            if (neighborMask != 0 && (visited & neighborMask) == 0) {
                int newRow = row + DY[dir];
                int newCol = col + DX[dir];
                paths += backtrack(newRow, newCol, pattern, depth + 1, visited | neighborMask);
            }
        }

        return paths;
    }

    private boolean canReachEnd(int row, int col, int depth, long visited) {
        if (!isValid(row, col)) {
            return false;
        }

        int minDist = DISTANCE_TO_END[row][col];
        int remainingMoves = MOVES - depth;
        int visitedCount = Long.bitCount(visited);

        return minDist <= remainingMoves &&
                remainingMoves <= (SIZE * SIZE - visitedCount);
    }

    private static boolean isValid(int row, int col) {
        return row >= 0 && row < SIZE && col >= 0 && col < SIZE;
    }

    /**
     * Collects results from multithreaded tasks.
     */
    private long collectResults(List<Future<Long>> futures) {
        long total = 0;
        try {
            for (Future<Long> future : futures) {
                total += future.get();
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return total;
    }


    private static int getDirectionIndex(char dir) {
        return switch (dir) {
            case 'U' -> 0;
            case 'D' -> 1;
            case 'L' -> 2;
            case 'R' -> 3;
            default -> throw new IllegalArgumentException("Invalid direction: " + dir);
        };
    }

    private static boolean isValidPattern(String pattern) {
        return pattern != null && pattern.length() == MOVES && pattern.matches("[UDLR*]+");
    }

    private static int countWildcards(String pattern) {
        return (int) pattern.chars().filter(ch -> ch == '*').count();
    }

    public void shutdown() {
        THREAD_POOL.shutdown();
        try {
            if (!THREAD_POOL.awaitTermination(10, TimeUnit.SECONDS)) {
                THREAD_POOL.shutdownNow();
            }
        } catch (InterruptedException e) {
            THREAD_POOL.shutdownNow();
        }
    }

    public static void main(String[] args) {
        PathFinder finder = new PathFinder();
        String[] testCases = {
//                "***************************************************************",
                "*****DR******R******R********************R*D************L******",
//                "DDDDDDRUUUUUURDDDDDDRUUUUUURDDDDDDRUUUUUURRDLDRDLDRDLDRDLLLLLLL"
        };

        for (String test : testCases) {
            System.out.println("Pattern: " + test);

            long startTime = System.currentTimeMillis();
            long result = finder.countPaths(test);
            long endTime = System.currentTimeMillis();

            System.out.println("Total paths: " + result);
            System.out.println("Time (ms): " + (endTime - startTime));
        }

        finder.shutdown();
    }
}
