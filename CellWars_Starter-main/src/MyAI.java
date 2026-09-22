//Name: Danny Rudnik
//AI Code Name: DuckyAI
//Strategy: This algorithm evaluates immediate and multi-generation consequences
public class MyAI extends CellAI {
    private static final double EPSILON = 0.000001;
    private static final int LOOKAHEAD_GENERATIONS = 3;

    @Override
    public String getAIName() {
        return "DuckyAI";
    }

    /** Selects the move with the best immediate result and projected future. */
    @Override
    public Location select(Grid grid) {
        Location best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (int row = 0; row < grid.getRows(); row++) {
            for (int col = 0; col < grid.getCols(); col++) {
                double score = scoreMove(grid, row, col);
                if (score > bestScore
                        || (Math.abs(score - bestScore) <= EPSILON && randomDouble() < 0.08)) {
                    bestScore = score;
                    best = new Location(row, col);
                }
            }
        }
        return best;
    }

    /** Combines tactical value with a short, inexpensive forward simulation. */
    private double scoreMove(Grid grid, int actionRow, int actionCol) {
        double ownSwing = 0.0;
        double populationSwing = 0.0;
        double enemySwing = 0.0;

        for (int row = Math.max(0, actionRow - 2); row <= Math.min(grid.getRows() - 1, actionRow + 2); row++) {
            for (int col = Math.max(0, actionCol - 2); col <= Math.min(grid.getCols() - 1, actionCol + 2); col++) {
                double before = expectedOwn(grid, row, col, actionRow, actionCol, false);
                double after = expectedOwn(grid, row, col, actionRow, actionCol, true);
                ownSwing += after - before;
                populationSwing += expectedAlive(grid, row, col, actionRow, actionCol, true)
                        - expectedAlive(grid, row, col, actionRow, actionCol, false);
                enemySwing += expectedEnemy(grid, row, col, actionRow, actionCol, true)
                        - expectedEnemy(grid, row, col, actionRow, actionCol, false);
            }
        }

        double score = ownSwing * 8.0 + populationSwing * 0.7 - enemySwing * 1.5;
        score += localStability(grid, actionRow, actionCol);
        score += futureValue(grid, actionRow, actionCol);

        // A kill is worthwhile only when its surrounding generations justify it.
        if (grid.getCell(actionRow, actionCol) == getID()) {
            score -= 0.12;
        }
        return score;
    }

    /**
     * Looks several generations ahead.  This deliberately uses a small integer
     * simulation rather than changing Grid, so it remains compatible with the
     * starter API.  Ties retain the current owner, which is a conservative
     * approximation of the simulator's tie handling.
     */
    private double futureValue(Grid grid, int actionRow, int actionCol) {
        int[][] state = copyBoard(grid);
        state[actionRow][actionCol] = state[actionRow][actionCol] == -1 ? getID() : -1;
        double value = 0.0;
        double discount = 1.0;

        for (int generation = 1; generation <= LOOKAHEAD_GENERATIONS; generation++) {
            int[][] next = evolve(state);
            value += discount * boardValue(state, next);
            value += discount * 0.20 * frontierValue(next);
            state = next;
            discount *= 0.55;
        }
        return value;
    }

    /** Rewards durable territory, growth opportunities, and cells near the edge of influence. */
    private double boardValue(int[][] before, int[][] after) {
        int ownBefore = countOwned(before, getID());
        int ownAfter = countOwned(after, getID());
        int enemyBefore = countNonOwned(before, getID());
        int enemyAfter = countNonOwned(after, getID());
        return (ownAfter - ownBefore) * 3.0
                + (enemyBefore - enemyAfter) * 0.8
                + countStableOwn(after) * 0.12;
    }

    /** Values empty cells with exactly two living neighbors: useful future births. */
    private double frontierValue(int[][] state) {
        double result = 0.0;
        for (int row = 0; row < state.length; row++) {
            for (int col = 0; col < state[row].length; col++) {
                if (state[row][col] == -1 && livingNeighbors(state, row, col) == 2) {
                    result += 1.0;
                }
            }
        }
        return result;
    }

    /** Applies Conway's rules and propagates the majority owner into births. */
    private int[][] evolve(int[][] state) {
        int rows = state.length;
        int cols = state[0].length;
        int[][] next = new int[rows][cols];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int living = livingNeighbors(state, row, col);
                boolean survives = state[row][col] != -1
                        ? living == 2 || living == 3
                        : living == 3;
                next[row][col] = survives ? winningOwner(state, row, col) : -1;
            }
        }
        return next;
    }

    /** Uses the strongest local owner; retaining an existing owner breaks ties conservatively. */
    private int winningOwner(int[][] state, int row, int col) {
        int[] ids = new int[8];
        int[] counts = new int[8];
        int used = 0;
        for (int r = row - 1; r <= row + 1; r++) {
            for (int c = col - 1; c <= col + 1; c++) {
                if (!inside(state, r, c) || (r == row && c == col) || state[r][c] == -1) {
                    continue;
                }
                int owner = state[r][c];
                int index = 0;
                while (index < used && ids[index] != owner) {
                    index++;
                }
                if (index == used) {
                    ids[used] = owner;
                    counts[used++] = 0;
                }
                counts[index]++;
            }
        }
        int winner = state[row][col];
        int maximum = 0;
        boolean tied = false;
        for (int i = 0; i < used; i++) {
            if (counts[i] > maximum) {
                maximum = counts[i];
                winner = ids[i];
                tied = false;
            }
            else if (counts[i] == maximum && counts[i] > 0) {
                tied = true;
            }
        }
        if (tied && state[row][col] != -1) {
            return state[row][col];
        }
        return winner;
    }

    private int livingNeighbors(int[][] state, int row, int col) {
        int count = 0;
        for (int r = row - 1; r <= row + 1; r++) {
            for (int c = col - 1; c <= col + 1; c++) {
                if (inside(state, r, c) && !(r == row && c == col) && state[r][c] != -1) {
                    count++;
                }
            }
        }
        return count;
    }

    private boolean inside(int[][] state, int row, int col) {
        return row >= 0 && col >= 0 && row < state.length && col < state[0].length;
    }

    private int[][] copyBoard(Grid grid) {
        int[][] result = new int[grid.getRows()][grid.getCols()];
        for (int row = 0; row < grid.getRows(); row++) {
            for (int col = 0; col < grid.getCols(); col++) {
                result[row][col] = grid.getCell(row, col);
            }
        }
        return result;
    }

    private int countOwned(int[][] state, int owner) {
        int count = 0;
        for (int[] row : state) {
            for (int cell : row) {
                if (cell == owner) count++;
            }
        }
        return count;
    }

    private int countNonOwned(int[][] state, int owner) {
        int count = 0;
        for (int[] row : state) {
            for (int cell : row) {
                if (cell != -1 && cell != owner) count++;
            }
        }
        return count;
    }

    private int countStableOwn(int[][] state) {
        int count = 0;
        for (int row = 0; row < state.length; row++) {
            for (int col = 0; col < state[row].length; col++) {
                if (state[row][col] == getID()) {
                    int neighbors = livingNeighbors(state, row, col);
                    if (neighbors == 2 || neighbors == 3) count++;
                }
            }
        }
        return count;
    }

    private double expectedOwn(Grid grid, int row, int col, int actionRow, int actionCol, boolean applyAction) {
        int livingNeighbors = 0;
        int ownNeighbors = 0;
        int[] ownerIds = new int[8];
        int[] ownerCounts = new int[8];
        int uniqueOwners = 0;
        for (int neighborRow = row - 1; neighborRow <= row + 1; neighborRow++) {
            for (int neighborCol = col - 1; neighborCol <= col + 1; neighborCol++) {
                if (!isNeighbor(grid, row, col, neighborRow, neighborCol)) continue;
                int owner = ownerAt(grid, neighborRow, neighborCol, actionRow, actionCol, applyAction);
                if (owner == -1) continue;
                livingNeighbors++;
                int ownerIndex = 0;
                while (ownerIndex < uniqueOwners && ownerIds[ownerIndex] != owner) ownerIndex++;
                if (ownerIndex == uniqueOwners) {
                    ownerIds[uniqueOwners] = owner;
                    uniqueOwners++;
                }
                ownerCounts[ownerIndex]++;
                if (owner == getID()) ownNeighbors++;
            }
        }
        int currentOwner = ownerAt(grid, row, col, actionRow, actionCol, applyAction);
        boolean survives = currentOwner != -1 ? livingNeighbors == 2 || livingNeighbors == 3 : livingNeighbors == 3;
        if (!survives || livingNeighbors == 0) return 0.0;
        int maximum = 0;
        int tiedOwners = 0;
        for (int i = 0; i < uniqueOwners; i++) {
            if (ownerCounts[i] > maximum) { maximum = ownerCounts[i]; tiedOwners = 1; }
            else if (ownerCounts[i] == maximum && ownerCounts[i] > 0) tiedOwners++;
        }
        return ownNeighbors == maximum ? 1.0 / tiedOwners : 0.0;
    }

    private double expectedAlive(Grid grid, int row, int col, int actionRow, int actionCol, boolean applyAction) {
        int neighbors = countLiving(grid, row, col, actionRow, actionCol, applyAction);
        int owner = ownerAt(grid, row, col, actionRow, actionCol, applyAction);
        return owner == -1 ? (neighbors == 3 ? 1.0 : 0.0) : (neighbors == 2 || neighbors == 3 ? 1.0 : 0.0);
    }

    private double expectedEnemy(Grid grid, int row, int col, int actionRow, int actionCol, boolean applyAction) {
        return expectedAlive(grid, row, col, actionRow, actionCol, applyAction)
                - expectedOwn(grid, row, col, actionRow, actionCol, applyAction);
    }

    private double localStability(Grid grid, int actionRow, int actionCol) {
        double stability = 0.0;
        for (int row = Math.max(0, actionRow - 1); row <= Math.min(grid.getRows() - 1, actionRow + 1); row++) {
            for (int col = Math.max(0, actionCol - 1); col <= Math.min(grid.getCols() - 1, actionCol + 1); col++) {
                int neighbors = GridFunctions.getNeighbors(row, col, grid);
                if (neighbors == 2 || neighbors == 3) {
                    if (grid.getCell(row, col) == getID()) stability += 0.05;
                    else if (grid.getCell(row, col) == -1) stability += 0.015;
                }
            }
        }
        return stability;
    }

    private int countLiving(Grid grid, int row, int col, int actionRow, int actionCol, boolean applyAction) {
        int count = 0;
        for (int r = row - 1; r <= row + 1; r++) {
            for (int c = col - 1; c <= col + 1; c++) {
                if (isNeighbor(grid, row, col, r, c)
                        && ownerAt(grid, r, c, actionRow, actionCol, applyAction) != -1) count++;
            }
        }
        return count;
    }

    private boolean isNeighbor(Grid grid, int row, int col, int neighborRow, int neighborCol) {
        return !(neighborRow == row && neighborCol == col)
                && neighborRow >= 0 && neighborCol >= 0
                && neighborRow < grid.getRows() && neighborCol < grid.getCols();
    }

    private int ownerAt(Grid grid, int row, int col, int actionRow, int actionCol, boolean applyAction) {
        int owner = grid.getCell(row, col);
        if (applyAction && row == actionRow && col == actionCol) return owner == -1 ? getID() : -1;
        return owner;
    }
}
