//Name: Danny Rudnik
//AI Code Name: DuckyAI
//Strategy: This algorithm finds the best move and plays the second best move 12% of the time
public class MyAI extends CellAI {
    @Override
    public String getAIName() {
        return "DuckyAI";
    }

    /** Selects the highest-scoring spawn or kill location on the board. */
    @Override
    public Location select(Grid grid) {
        Location best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (int row = 0; row < grid.getRows(); row++) {
            for (int col = 0; col < grid.getCols(); col++) {
                double score = scoreMove(grid, row, col);
                if (score > bestScore
                        || (Math.abs(score - bestScore) <= 0.000001 && randomDouble() < 0.12)) {
                    bestScore = score;
                    best = new Location(row, col);
                }
            }
        }

        return best;
    }

    /** Scores one action using its expected local effect after one Life update. */
    private double scoreMove(Grid grid, int actionRow, int actionCol) {
        double ownSwing = 0.0;
        double populationSwing = 0.0;
        double enemySwing = 0.0;

        // An action changes neighbor counts in a 3x3 area. Those cells can
        // affect the next generation in a surrounding 5x5 area.
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

        // Avoid self-destructive kills unless the Life update clearly benefits.
        if (grid.getCell(actionRow, actionCol) == getID()) {
            score -= 0.12;
        }
        return score;
    }

    /** Returns the probability that a cell becomes owned by this AI next turn. */
    private double expectedOwn(Grid grid, int row, int col, int actionRow, int actionCol, boolean applyAction) {
        int livingNeighbors = 0;
        int ownNeighbors = 0;
        int[] ownerIds = new int[8];
        int[] ownerCounts = new int[8];
        int uniqueOwners = 0;

        for (int neighborRow = row - 1; neighborRow <= row + 1; neighborRow++) {
            for (int neighborCol = col - 1; neighborCol <= col + 1; neighborCol++) {
                if (!isNeighbor(grid, row, col, neighborRow, neighborCol)) {
                    continue;
                }
                int owner = ownerAt(grid, neighborRow, neighborCol, actionRow, actionCol, applyAction);
                if (owner != -1) {
                    livingNeighbors++;
                    int ownerIndex = 0;
                    while (ownerIndex < uniqueOwners && ownerIds[ownerIndex] != owner) {
                        ownerIndex++;
                    }
                    if (ownerIndex == uniqueOwners) {
                        ownerIds[uniqueOwners] = owner;
                        uniqueOwners++;
                    }
                    ownerCounts[ownerIndex]++;
                    if (owner == getID()) {
                        ownNeighbors++;
                    }
                }
            }
        }

        int currentOwner = ownerAt(grid, row, col, actionRow, actionCol, applyAction);
        boolean survives = currentOwner != -1
                ? livingNeighbors == 2 || livingNeighbors == 3
                : livingNeighbors == 3;
        if (!survives || livingNeighbors == 0) {
            return 0.0;
        }

        int maximum = 0;
        int tiedOwners = 0;
        for (int ownerIndex = 0; ownerIndex < uniqueOwners; ownerIndex++) {
            int count = ownerCounts[ownerIndex];
            if (count > maximum) {
                maximum = count;
                tiedOwners = 1;
            }
            else if (count == maximum && count > 0) {
                tiedOwners++;
            }
        }
        return ownNeighbors == maximum ? 1.0 / tiedOwners : 0.0;
    }

    /** Returns whether a cell is expected to be alive after the next update. */
    private double expectedAlive(Grid grid, int row, int col, int actionRow, int actionCol, boolean applyAction) {
        int neighbors = countLiving(grid, row, col, actionRow, actionCol, applyAction);
        int owner = ownerAt(grid, row, col, actionRow, actionCol, applyAction);
        return owner == -1 ? (neighbors == 3 ? 1.0 : 0.0) : (neighbors == 2 || neighbors == 3 ? 1.0 : 0.0);
    }

    /** Returns the expected alive value not owned by this AI. */
    private double expectedEnemy(Grid grid, int row, int col, int actionRow, int actionCol, boolean applyAction) {
        return expectedAlive(grid, row, col, actionRow, actionCol, applyAction)
                - expectedOwn(grid, row, col, actionRow, actionCol, applyAction);
    }

    /** Rewards nearby cells and empty locations that already have stable counts. */
    private double localStability(Grid grid, int actionRow, int actionCol) {
        double stability = 0.0;
        for (int row = Math.max(0, actionRow - 1); row <= Math.min(grid.getRows() - 1, actionRow + 1); row++) {
            for (int col = Math.max(0, actionCol - 1); col <= Math.min(grid.getCols() - 1, actionCol + 1); col++) {
                int neighbors = GridFunctions.getNeighbors(row, col, grid);
                if (neighbors == 2 || neighbors == 3) {
                    if (grid.getCell(row, col) == getID()) {
                        stability += 0.05;
                    }
                    else if (grid.getCell(row, col) == -1) {
                        stability += 0.015;
                    }
                }
            }
        }
        return stability;
    }

    /** Counts living neighbors while optionally applying the candidate action. */
    private int countLiving(Grid grid, int row, int col, int actionRow, int actionCol, boolean applyAction) {
        int livingNeighbors = 0;
        for (int neighborRow = row - 1; neighborRow <= row + 1; neighborRow++) {
            for (int neighborCol = col - 1; neighborCol <= col + 1; neighborCol++) {
                if (!isNeighbor(grid, row, col, neighborRow, neighborCol)) {
                    continue;
                }
                if (ownerAt(grid, neighborRow, neighborCol, actionRow, actionCol, applyAction) != -1) {
                    livingNeighbors++;
                }
            }
        }
        return livingNeighbors;
    }

    /** Returns whether a coordinate is a valid neighbor of the target cell. */
    private boolean isNeighbor(Grid grid, int row, int col, int neighborRow, int neighborCol) {
        return !(neighborRow == row && neighborCol == col)
                && neighborRow >= 0 && neighborCol >= 0
                && neighborRow < grid.getRows() && neighborCol < grid.getCols();
    }

    /** Reads a cell owner, toggling the candidate location when requested. */
    private int ownerAt(Grid grid, int row, int col, int actionRow, int actionCol, boolean applyAction) {
        int owner = grid.getCell(row, col);
        if (applyAction && row == actionRow && col == actionCol) {
            return owner == -1 ? getID() : -1;
        }
        return owner;
    }
}
