//Name: Danny Rudnik
//AI Code Name: DuckyAI
//Strategy: This algorithm evaluates the best move based off of the next two generations of board space
//it is done to only the two moves due to preformance issues I was having when trying more generations.
//The evolve method copies the board and applies the conways game of life rules for the amount of generation instance variables
//Which then scores the move and then returns it
public class MyAI extends CellAI {
    private static final double floatpointrev = 0.000001; //This was added because I was having issues with floating point stuff.
    private static final int generations = 2; //ammount of generations to look ahead for.
    private static final int canidates = 48; //The number of moves that will be evaluated for future generations.

    @Override
    public String getAIName() {
        return "DuckyAI";
    }

    /**
     * Selects the best possible move based on my algorithm
     */
    @Override
    public Location select(Grid grid) {
        int[] candidateRows = new int[canidates];
        int[] candidateCols = new int[canidates];
        double[] candidateScores = new double[canidates];
        int candidateCount = 0;

        // Look ahead only for the strongest tactical moves. Forward simulation
        // is the expensive part of this strategy and does not need to run for
        // every cell on the board.
        for (int row = 0; row < grid.getRows(); row++) {
            for (int col = 0; col < grid.getCols(); col++) {
                double tacticalScore = scoreMove(grid, row, col, false);
                if (candidateCount < canidates) {
                    candidateRows[candidateCount] = row;
                    candidateCols[candidateCount] = col;
                    candidateScores[candidateCount] = tacticalScore;
                    candidateCount++;
                }
                else {
                    int weakest = 0;
                    for (int index = 1; index < candidateCount; index++) {
                        if (candidateScores[index] < candidateScores[weakest]) weakest = index;
                    }
                    if (tacticalScore > candidateScores[weakest]) {
                        candidateRows[weakest] = row; //weakest is just the placeholder for the index of the weakest score that we replace so essentially its my best move that gets recalculated.
                        candidateCols[weakest] = col;
                        candidateScores[weakest] = tacticalScore;
                    }
                }
            }
        }

        Location best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (int index = 0; index < candidateCount; index++) {
            int row = candidateRows[index];
            int col = candidateCols[index];
            double score = candidateScores[index] + futureValue(grid, row, col);
            if (score > bestScore|| (Math.abs(score - bestScore) <= floatpointrev && randomDouble() < 0.08)) //if two moves are equal it sometimes picks move 2 8% of the time 
            {
                bestScore = score;
                best = new Location(row, col);
            }
        }
        return best;
    }

    /**
     * Calculates the immediate value of changing one location.
     */
    private double scoreMove(Grid grid, int actionRow, int actionCol, boolean includeFuture) {
        double ownSwing = 0.0;
        double populationSwing = 0.0;
        double enemySwing = 0.0;

        for (int row = Math.max(0, actionRow - 2); row <= Math.min(grid.getRows() - 1, actionRow + 2); row++) //loops through the 25 surrounding squares to check the change in ownership
            {
            for (int col = Math.max(0, actionCol - 2); col <= Math.min(grid.getCols() - 1, actionCol + 2); col++) 
                {
                double before = expectedOwn(grid, row, col, actionRow, actionCol, false); //expectedown and expectedalive are used to calculate the change in ownership.
                double after = expectedOwn(grid, row, col, actionRow, actionCol, true);
                ownSwing += after - before;
                populationSwing += expectedAlive(grid, row, col, actionRow, actionCol, true)
                        - expectedAlive(grid, row, col, actionRow, actionCol, false);
                enemySwing += expectedEnemy(grid, row, col, actionRow, actionCol, true)
                        - expectedEnemy(grid, row, col, actionRow, actionCol, false);
            }
        }

        double score = ownSwing * 10.0 + populationSwing * 0.5 - enemySwing * 3.5; //The score is calculated with ownswing being our cells gained being the most important
        //population swing is the next most important as it is the total number of cells gained and enemy swing is the least important as it is the number of enemy cells lost.
        score += localStability(grid, actionRow, actionCol);
        //localstability is a bonus for occilating cells that can survive multiple generations.
        if (includeFuture) score += futureValue(grid, actionRow, actionCol);

        // A kill is worthwhile only when its surrounding generations justify it.
        if (grid.getCell(actionRow, actionCol) == getID()) {
            score -= 0.12;
        }
        int actionNeighbors = GridFunctions.getNeighbors(actionRow, actionCol, grid);
        if (actionNeighbors == 0) score -= 2.0;
        else if (actionNeighbors == 1) score -= 0.5;
        int actionOwner = grid.getCell(actionRow, actionCol);
        if (actionOwner >= 0 && actionOwner != getID()) score += 2.5;
        return score;
    }

    /**
     * Checks the value of a move by simulating a couple generations of the board (changable via the Generations constant)
     */
    private double futureValue(Grid grid, int actionRow, int actionCol) {
        int[][] state = copyBoard(grid);
        state[actionRow][actionCol] = state[actionRow][actionCol] == -1 ? getID() : -1; //this line tests the action that it is given at the row and column.
        double value = 0.0;
        double discount = 1.0;

        for (int generation = 1; generation <= generations; generation++) {
            int[][] next = evolve(state);
            value += discount * boardValue(state, next);
            value += discount * 0.20 * frontierValue(next);
            state = next;
            discount *= 0.40;
        }
        return value;
    }

    // Computes the change in strategic board value between two generations.
    private double boardValue(int[][] before, int[][] after) {
        return stateValue(after) - stateValue(before); //this is the main method that calculates the score of a move based on its before and after.
    }

    /**
     * Creates a single score for a given board state based on the squares of the board and surrounding details in the cells 
     * (shown in methods in the method)
     */
    private double stateValue(int[][] state) {
        int own = countOwned(state, getID());
        int enemy = countNonOwned(state, getID());
        int ownStable = countStableOwn(state);
        int enemyStable = countStableEnemy(state);
        int ownBirths = countPotentialBirths(state, getID());
        int enemyBirths = countPotentialBirths(state, -2);
        return (own - enemy) * 10.0
                + (ownStable - enemyStable) * 5.0
                + (ownBirths - enemyBirths) * 3.0;
    }

    /**
     * Counts the number of cells that would be given to an owner.
     */
    private int countPotentialBirths(int[][] state, int owner) {
        int count = 0;
        for (int row = 0; row < state.length; row++) {
            for (int col = 0; col < state[row].length; col++) {
                if (state[row][col] == -1 && livingNeighbors(state, row, col) == 3) {
                    int birthOwner = winningOwner(state, row, col);
                    if (owner == -2 ? birthOwner != getID() && birthOwner != -1 : birthOwner == owner) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * Calculates immediate growth for cells with two neighbors
     */
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

    /**
     * This is the main updater method used for giving the next generation of board.
    */
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

    /**
     * Determines the owner of a cell after one generation of life.
     */
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
        if (tied) {
            // The engine resolves ties randomly. Keep an existing cell's owner
            // when possible, but let a tied birth remain alive in projection.
            return state[row][col] != -1 ? state[row][col] : ids[0]; //the ? and : are if and else statements learnt this one from google lol.
        }
        return winner;
    }

    /**
     * Counts living cells in the eight-cell neighborhood around a location.
     */
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

    /**
     * Tests whether a coordinate lies inside a projected board.
     */
    private boolean inside(int[][] state, int row, int col) {
        return row >= 0 && col >= 0 && row < state.length && col < state[0].length;
    }

    /**
     * Copies the board into the game similar to the evolve method but just used as a perfect photocopy.
     */
    private int[][] copyBoard(Grid grid) {
        int[][] result = new int[grid.getRows()][grid.getCols()];
        for (int row = 0; row < grid.getRows(); row++) {
            for (int col = 0; col < grid.getCols(); col++) {
                result[row][col] = grid.getCell(row, col);
            }
        }
        return result;
    }

    /**
     * Counts cells belonging to one owner.
     */
    private int countOwned(int[][] state, int owner) {
        int count = 0;
        for (int[] row : state) {
            for (int cell : row) {
                if (cell == owner) count++;
            }
        }
        return count;
    }

    /**
     * Counts living cells belonging to any owner except for my AI.
     */
    private int countNonOwned(int[][] state, int owner) {
        int count = 0;
        for (int[] row : state) {
            for (int cell : row) {
                if (cell != -1 && cell != owner) count++;
            }
        }
        return count;
    }

    /**
     * Counts my AI's cells currently meeting Conway's survival condition.
     */
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

    /**
     * Counts enemy cells currently meeting life survival condition.
     */
    private int countStableEnemy(int[][] state) {
        int count = 0;
        for (int row = 0; row < state.length; row++) {
            for (int col = 0; col < state[row].length; col++) {
                if (state[row][col] != -1 && state[row][col] != getID()) {
                    int neighbors = livingNeighbors(state, row, col);
                    if (neighbors == 2 || neighbors == 3) count++;
                }
            }
        }
        return count;
    }

    /**
     * Estimates the chance that a cell becomes Ducky-owned after an action.
     * Ownership ties are split evenly among the tied local owners.
     */
    private double expectedOwn(Grid grid, int row, int col, int actionRow, int actionCol, boolean applyAction) {
        int livingNeighbors = 0;
        int ownNeighbors = 0;
        int[] ownerIds = new int[8]; // 8 is one per neighbor for a cell.
        int[] ownerCounts = new int[8];
        int uniqueOwners = 0;
        for (int neighborRow = row - 1; neighborRow <= row + 1; neighborRow++) {
            for (int neighborCol = col - 1; neighborCol <= col + 1; neighborCol++) {
                if (!isNeighbor(grid, row, col, neighborRow, neighborCol)) continue; //this is the method that checks if a cell is a neighbor to the cell we are checking.
                int owner = ownerAt(grid, neighborRow, neighborCol, actionRow, actionCol, applyAction);
                if (owner == -1) continue; //continue statements check if the statement is true and then continues if it is.
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
    //all of the following methods are used to calculate the expected owner of a cell after an action is taken you can just read the method names to get an idea of what they do.
    private double expectedAlive(Grid grid, int row, int col, int actionRow, int actionCol, boolean applyAction) {
        int neighbors = countLiving(grid, row, col, actionRow, actionCol, applyAction);
        int owner = ownerAt(grid, row, col, actionRow, actionCol, applyAction);
        return owner == -1 ? (neighbors == 3 ? 1.0 : 0.0) : (neighbors == 2 || neighbors == 3 ? 1.0 : 0.0);
    }
    //checks enemy cells at a point
    private double expectedEnemy(Grid grid, int row, int col, int actionRow, int actionCol, boolean applyAction) {
        return expectedAlive(grid, row, col, actionRow, actionCol, applyAction)
                - expectedOwn(grid, row, col, actionRow, actionCol, applyAction);
    }
    //occilator check while prioritizing the occilators.
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
    //counts the number of living cells around a cell and checks if they are neighbors and if they are owned by an enemy or not.
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
    //self explanatory.
    private boolean isNeighbor(Grid grid, int row, int col, int neighborRow, int neighborCol) {
        return !(neighborRow == row && neighborCol == col)
                && neighborRow >= 0 && neighborCol >= 0
                && neighborRow < grid.getRows() && neighborCol < grid.getCols();
    }
    //also self explanatory.
    private int ownerAt(Grid grid, int row, int col, int actionRow, int actionCol, boolean applyAction) {
        int owner = grid.getCell(row, col);
        if (applyAction && row == actionRow && col == actionCol) return owner == -1 ? getID() : -1;
        return owner;
    }
}
