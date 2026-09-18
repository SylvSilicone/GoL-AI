/**
 * A one-step tactical player. Each possible spawn or kill is scored by
 * simulating the next Life update in the only 3x3 area that the move can
 * affect. This makes the AI create cells with viable neighbor counts, protect
 * useful local patterns, and attack cells whose removal helps nearby survival.
 */
public class MyAI extends CellAI {
    @Override
    public String getAIName() {
        return "JudyHoppsLover67AI";
    }

    /**
     * Selects the highest-scoring spawn or kill location on the current board.
     * Every location is evaluated as if the action were followed by the next
     * Conway update. Close scores are occasionally randomized to avoid making
     * identical choices in every equivalent position.
     */
    @Override
    public Location select(Grid grid) 
    {
        Location best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (int row = 0; row < grid.getRows(); row++) 
        {
            for (int col = 0; col < grid.getCols(); col++) 
                {
                double score = scoreMove(grid, row, col);
                if (score > bestScore || (Math.abs(score - bestScore) <= 0.000001 && randomDouble() < 0.15)) 
                {
                    bestScore = score;
                    best = new Location(row, col);
                }
            }
        }

        return best;
    }

    /**
     * Scores one possible action by comparing the affected neighborhood before
     * and after the action. Since one action changes only the selected cell,
     * only the surrounding 3x3 area needs to be reconsidered.
     *
     * @param grid current board snapshot
     * @param actionRow row of the candidate action
     * @param actionCol column of the candidate action
     * @return tactical value of the candidate action
     */
    private double scoreMove(Grid grid, int actionRow, int actionCol) {
        double score = 0.0;
        int actionCell = grid.getCell(actionRow, actionCol);

        // The action itself is part of the next generation's neighborhood.
        // Killing an enemy is slightly more valuable than killing our own cell.
        if (actionCell == -1) {
            score += 0.20;
        }
        else if (actionCell != getID()) {
            score += 0.35;
        }
        else {
            score -= 0.20;
        }

        for (int row = Math.max(0, actionRow - 1); row <= Math.min(grid.getRows() - 1, actionRow + 1); row++) {
            for (int col = Math.max(0, actionCol - 1); col <= Math.min(grid.getCols() - 1, actionCol + 1); col++) {
                double before = expectedLocalValue(grid, row, col, actionRow, actionCol, false);
                double after = expectedLocalValue(grid, row, col, actionRow, actionCol, true);
                score += after - before;
            }
        }

        // Prefer the center of the board when tactical outcomes are equal:
        // interior cells have more opportunities to form stable patterns.
        double centerRow = (grid.getRows() - 1) / 2.0;
        double centerCol = (grid.getCols() - 1) / 2.0;
        double distance = Math.abs(actionRow - centerRow) + Math.abs(actionCol - centerCol);
        return score - distance * 0.0001;
    }

    /**
     * Estimates the next-generation value of one affected cell. The estimate
     * follows the Life survival and birth counts and treats an ownership tie
     * as an expected share because the engine resolves ties randomly.
     *
     * @param grid current board snapshot
     * @param row row of the cell being evaluated
     * @param col column of the cell being evaluated
     * @param actionRow row of the candidate action
     * @param actionCol column of the candidate action
     * @param applyAction whether the candidate action should be simulated
     * @return expected own value minus expected nearby enemy value
     */
    private double expectedLocalValue(Grid grid, int row, int col, int actionRow, int actionCol, boolean applyAction) 
    {
        int livingNeighbors = 0;
        int ownNeighbors = 0;
        int maximumNeighborCount = 0;
        int tiedOwners = 0;

        for (int neighborRow = row - 1; neighborRow <= row + 1; neighborRow++) {
            for (int neighborCol = col - 1; neighborCol <= col + 1; neighborCol++) {
                if (neighborRow == row && neighborCol == col || neighborRow < 0 || neighborCol < 0 || neighborRow >= grid.getRows() || neighborCol >= grid.getCols()) 
                {
                    continue;
                }

                int owner = grid.getCell(neighborRow, neighborCol);
                if (applyAction && neighborRow == actionRow && neighborCol == actionCol) {
                    owner = owner == -1 ? getID() : -1;
                }
                if (owner != -1) {
                    livingNeighbors++;
                    if (owner == getID()) {
                        ownNeighbors++;
                    }
                }
            }
        }

        int currentOwner = grid.getCell(row, col);
        if (applyAction && row == actionRow && col == actionCol) {
            currentOwner = currentOwner == -1 ? getID() : -1;
        }

        boolean survives = currentOwner != -1
                ? livingNeighbors == 2 || livingNeighbors == 3
                : livingNeighbors == 3;
        if (!survives) {
            return 0.0;
        }

        // A cell's owner is the local majority. For a tie, use its expected
        // share rather than pretending the seeded random tie-break is known.
        for (int neighborRow = row - 1; neighborRow <= row + 1; neighborRow++) {
            for (int neighborCol = col - 1; neighborCol <= col + 1; neighborCol++) {
                if (neighborRow == row && neighborCol == col
                        || neighborRow < 0 || neighborCol < 0
                        || neighborRow >= grid.getRows() || neighborCol >= grid.getCols()) {
                    continue;
                }
                int owner = grid.getCell(neighborRow, neighborCol);
                if (applyAction && neighborRow == actionRow && neighborCol == actionCol) {
                    owner = owner == -1 ? getID() : -1;
                }
                if (owner != -1) {
                    int count = countOwnerAround(grid, row, col, owner, actionRow, actionCol, applyAction);
                    if (count > maximumNeighborCount) {
                        maximumNeighborCount = count;
                        tiedOwners = owner == getID() ? 1 : 0;
                    }
                    else if (count == maximumNeighborCount && count > 0 && owner == getID()) {
                        tiedOwners++;
                    }
                }
            }
        }

        double ownChance = maximumNeighborCount == 0 ? 0.0 : (double) tiedOwners / livingNeighbors;
        double enemyChance = 1.0 - ownChance;
        // Own survival is the primary objective; denying local enemy cells is
        // useful but deliberately weaker than building our own population.
        return ownChance * 2.0 - enemyChance * 0.45 + ownNeighbors * 0.02;
    }

    /**
     * Counts how many neighbors belong to a particular AI, including the
     * candidate action when requested.
     *
     * @param grid current board snapshot
     * @param row row whose neighbors should be inspected
     * @param col column whose neighbors should be inspected
     * @param wantedOwner AI ID to count
     * @param actionRow row of the candidate action
     * @param actionCol column of the candidate action
     * @param applyAction whether the candidate action should be simulated
     * @return number of neighboring cells owned by wantedOwner
     */
    private int countOwnerAround(Grid grid, int row, int col, int wantedOwner,
                                 int actionRow, int actionCol, boolean applyAction) {
        int count = 0;
        for (int neighborRow = row - 1; neighborRow <= row + 1; neighborRow++) {
            for (int neighborCol = col - 1; neighborCol <= col + 1; neighborCol++) {
                if (neighborRow == row && neighborCol == col
                        || neighborRow < 0 || neighborCol < 0
                        || neighborRow >= grid.getRows() || neighborCol >= grid.getCols()) {
                    continue;
                }
                int owner = grid.getCell(neighborRow, neighborCol);
                if (applyAction && neighborRow == actionRow && neighborCol == actionCol) {
                    owner = owner == -1 ? getID() : -1;
                }
                if (owner == wantedOwner) {
                    count++;
                }
            }
        }
        return count;
    }
}
