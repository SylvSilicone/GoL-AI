/** Teacher-adjustable defaults for Cell Wars. */
public final class CellWarsConfig {

    private CellWarsConfig() {
    }

    public static final int STARTING_CELLS_PER_AI = 100;
    public static final int SIZE_MULTIPLIER = 1000;
    public static final int MAX_TURNS = 10_000;

    public static final int DEFAULT_TOURNAMENT_RUNS = 10;
    public static final long DEFAULT_BASE_SEED = 20260911L;

    public static final int AI_MOVE_TIMEOUT_MS = 1000;
    public static final int MAX_INVALID_MOVES = 3;

    /** Number of automatic replays allowed when a bracket match ends in a draw. */
    public static final int MAX_DRAW_REPLAYS = 2;
}
