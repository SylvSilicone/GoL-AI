/**
 * Read-only snapshot of the Cell Wars grid supplied to each AI.
 *
 * -1 means dead.
 * 0 or greater is the ID of the AI that owns the living cell.
 */
public class Grid {

    private final int[][] grid;

    public Grid(int[][] source) {
        grid = new int[source.length][source[0].length];
        for (int row = 0; row < source.length; row++) {
            System.arraycopy(source[row], 0, grid[row], 0, source[row].length);
        }
    }

    public int getRows() {
        return grid.length;
    }

    public int getCols() {
        return grid[0].length;
    }

    public int getCell(int row, int col) {
        return grid[row][col];
    }
}
