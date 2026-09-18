/** Baseline AI: selects a completely random location each turn. */
public class RandomAI extends CellAI {

    @Override
    public String getAIName() {
        return "RandomAI";
    }

    @Override
    public Location select(Grid grid) {
        return new Location(randomInt(grid.getRows()), randomInt(grid.getCols()));
    }
}
