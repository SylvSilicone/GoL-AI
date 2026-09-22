/**
 * STUDENT FILE
 *
 * Name: Dylan Varon
 * AI Code Name: ClearsightAI
 *
 * Strategy Description:
 * This algorithm first finds all spots that would just kill the newly placed cell.
 * It then calculates every possible move, and returns the position that would return the highest possible point value (number of own tiles minus number of opponents tiles)
 */
public class MyAI extends CellAI {

    @Override
    public String getAIName() {
        return "ClearsightAI";
    }

    @Override
    public Location select(Grid grid) {
        /*
         * Replace this starter strategy.
         *
         * Helpful information:
         *   getID()                     -> your cell ID
         *   grid.getRows()              -> number of rows
         *   grid.getCols()              -> number of columns
         *   grid.getCell(r, c)          -> -1 if dead, otherwise an AI ID
         *   GridFunctions.getNeighbors  -> number of living neighbors
         *   GridFunctions.mostCommonNeighbor -> most common neighboring AI
         *   randomInt(bound)            -> reproducible random integer
         */

        boolean[][] potentialMoves = new boolean[grid.getRows()][grid.getCols()];

        // Finds all viable plays that don't waste a turn
        for(int r = 0; r < grid.getRows(); r++) {
            for(int c = 0; c < grid.getCols(); c++) {
                if(grid.getCell(r,c) == -1) {
                    if(getLongNeighbors(r, c, grid) > 0) {
                        potentialMoves[r][c] = true;
                    }
                    else {
                        potentialMoves[r][c] = false;
                    }
                }
                else {
                    if(GridFunctions.getNeighbors(r, c, grid) > 0) {
                        potentialMoves[r][c] = true;
                    }
                    else {
                        potentialMoves[r][c] = false;
                    }
                }

            }
        }

        Location bestLocation = new Location(0,0);
        int bestValue = Integer.MIN_VALUE;
        boolean oppDead = false;

        //picking the best possible move
        for(int r = 0; r < potentialMoves.length && !oppDead; r++) {
            for(int c = 0; c < potentialMoves[0].length; c++) {
                
                if(!potentialMoves[r][c])
                {
                    continue;
                }

                Grid simGrid = simulateMove(r,c,grid);

                int myCount = 0;
                int oppCount = 0;
                
                for(int r2 = 0; r2 < simGrid.getRows(); r2++) {
                    for(int c2 = 0; c2 < simGrid.getCols(); c2++) {

                        int neighbors = GridFunctions.getNeighbors(r2, c2, simGrid);

                        if (simGrid.getCell(r2, c2) != -1) {
                            if (!(neighbors < 2 || neighbors > 3)) {
                                int cellID = GridFunctions.mostCommonNeighbor(r2, c2, simGrid);

                                if(cellID == -1) {}
                                else if(cellID == getID()) {
                                    myCount++;
                                }
                                else {
                                    oppCount++;
                                }

                            }
                        }
                        else {
                            if (neighbors == 3) {
                                int cellID = GridFunctions.mostCommonNeighbor(r2, c2, simGrid);
                                
                                if(cellID == -1) {}
                                else if(cellID == getID()) {
                                    myCount++;
                                }
                                else {
                                    oppCount++;
                                }
                            }
                            
                        }

                    }
                }

                int turnValue = myCount - oppCount;

                if(oppCount == 0) {
                    bestLocation = new Location(r,c);
                    oppDead = true;
                    break;

                }
                else if(turnValue > bestValue) {
                    bestLocation = new Location(r,c);
                    bestValue = turnValue;
                }
            }
        }

        return bestLocation;
    }

    private static int getLongNeighbors(int row, int col, Grid grid) {
        int alive = 0;

        for (int r = row - 2; r <= row + 2; r++) {
            for (int c = col - 2; c <= col + 2; c++) {
                if (r == row && c == col) {
                    continue;
                }

                if (r >= 0 && c >= 0 && r < grid.getRows() && c < grid.getCols()) {
                    if (grid.getCell(r, c) != -1) {
                        alive++;
                    }
                }
            }
        }

        return alive;
    }

    private Grid simulateMove(int row, int col, Grid grid) {
        int[][] simulated = new int[grid.getRows()][grid.getCols()];

        for (int r = 0; r < grid.getRows(); r++) {
            for (int c = 0; c < grid.getCols(); c++) {
                simulated[r][c] = grid.getCell(r, c);
            }
        }

        if (simulated[row][col] == -1) {
            simulated[row][col] = getID();
        }
        else {
            simulated[row][col] = -1;
        }

        return new Grid(simulated);
    }
}
