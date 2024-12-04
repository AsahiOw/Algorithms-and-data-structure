import java.util.Scanner;

public class singlethread {
    // Constants for grid dimensions
    private static final int GRID_SIZE = 8;
    private static final int TOTAL_MOVES = 63;

    // Progress tracking variables
    private static long totalPaths = 0;
    private static long lastUpdateTime = 0;
    private static long startTime = 0;
    private static int asteriskCount = 0;
    private static final long UPDATE_INTERVAL = 1000; // Update progress every 1 second

    // Possible movement directions
    private static final int[] DX = {1, -1, 0, 0};  // Down, Up, Right, Left
    private static final int[] DY = {0, 0, 1, -1};

    /**
     * Shows the current progress of the algorithm
     */
    private static void showProgress(int currentMove) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime >= UPDATE_INTERVAL) {
            long elapsedSeconds = (currentTime - startTime) / 1000;
            System.out.printf("\rProcessing... Move: %d/%d, Paths found: %,d, Time elapsed: %ds%n",
                    currentMove, TOTAL_MOVES, totalPaths, elapsedSeconds);
            lastUpdateTime = currentTime;
        }
    }

    /**
     * Estimates the complexity of the search based on number of asterisks
     */
    private static void showEstimatedComplexity(String path) {
        asteriskCount = (int) path.chars().filter(ch -> ch == '*').count();
        System.out.printf("Number of wildcards (*): %d%n", asteriskCount);
        if (asteriskCount > 30) {
            System.out.println("Warning: High number of wildcards may result in long execution time");
        }
        double estimatedPaths = Math.pow(4, asteriskCount);
        System.out.printf("Estimated maximum paths to explore: %.2e%n", estimatedPaths);
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
        // Show progress periodically
        if (moveIndex % 10 == 0) {
            showProgress(moveIndex);
        }

        if (moveIndex == TOTAL_MOVES) {
            if (x == GRID_SIZE - 1 && y == 0) {
                totalPaths++;
                if (totalPaths % 100000 == 0) {
                    showProgress(moveIndex);
                }
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

        // Show initial analysis
        System.out.println("\nAnalyzing path...");
        showEstimatedComplexity(path);

        // Initialize timers
        startTime = System.currentTimeMillis();
        lastUpdateTime = startTime;

        System.out.println("\nStarting path exploration...");
        long initialVisited = 1L;
        explorePaths(0, 0, 0, initialVisited, path);

        long endTime = System.currentTimeMillis();

        // Clear the progress line and show final results
        System.out.println("\n\nFinal Results:");
        System.out.println("Total paths: " + totalPaths);
        System.out.println("Time (ms): " + (endTime - startTime));
        System.out.printf("Average paths per second: %,.2f%n",
                (totalPaths * 1000.0) / (endTime - startTime));

        scanner.close();
    }
}