import java.util.Random;

/**
 * Base class for every Cell Wars AI.
 *
 * Students should create a subclass, override getAIName(), and override select().
 */
public abstract class CellAI {

    private final int id;
    private static int nextID = 0;
    private final Random random = new Random();

    public CellAI() {
        id = nextID++;
    }

    /** Unique non-negative ID used to identify this AI's cells on the grid. */
    public final int getID() {
        return id;
    }

    /** The name shown in the Cell Wars interface and tournament results. */
    public abstract String getAIName();

    /**
     * Called once on this AI's turn.
     *
     * Return a Location. If the location is dead, your cell is spawned there.
     * If the location is alive, that cell is killed.
     */
    public abstract Location select(Grid grid);

    /**
     * Preferred helper for random choices. Using this helper makes simulator
     * runs reproducible when the same seed is used.
     */
    protected final int randomInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        return random.nextInt(bound);
    }

    /** Preferred reproducible random double in the range [0.0, 1.0). */
    protected final double randomDouble() {
        return random.nextDouble();
    }

    /** Used by the simulator at the beginning of each match. */
    final void resetRandom(long seed) {
        random.setSeed(seed);
    }
}
