/**
 * Sample AI: repeatedly chooses random dead locations so that it always tries
 * to spawn rather than intentionally kill a cell.
 */
public class SpawnerAI extends CellAI {

    @Override
    public String getAIName() {
        return "SpawnerAI";
    }

    @Override
    public Location select(Grid grid) {
        int attempts = grid.getRows() * grid.getCols();

        for (int i = 0; i < attempts; i++) {
            int row = randomInt(grid.getRows());
            int col = randomInt(grid.getCols());
            if (grid.getCell(row, col) == -1) {
                return new Location(row, col);
            }
        }

        // Extremely unlikely unless the board is completely full.
        return new Location(0, 0);
    }
}
