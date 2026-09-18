import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable copy of current match information used by the Swing interface. */
class MatchSnapshot {

    final int[][] grid;
    final List<CellAI> participants;
    final List<CellAI> activePlayers;
    final Map<Integer, Integer> counts;
    final Map<Integer, Integer> deltas;
    final Map<Integer, Integer> strikes;
    final int completedTurns;
    final long seed;
    final TurnAction lastAction;
    final MatchResult result;

    MatchSnapshot(int[][] grid, List<CellAI> participants, List<CellAI> activePlayers,
                  Map<Integer, Integer> counts, Map<Integer, Integer> deltas,
                  Map<Integer, Integer> strikes, int completedTurns, long seed,
                  TurnAction lastAction, MatchResult result) {
        this.grid = grid;
        this.participants = new ArrayList<CellAI>(participants);
        this.activePlayers = new ArrayList<CellAI>(activePlayers);
        this.counts = new LinkedHashMap<Integer, Integer>(counts);
        this.deltas = new LinkedHashMap<Integer, Integer>(deltas);
        this.strikes = new LinkedHashMap<Integer, Integer>(strikes);
        this.completedTurns = completedTurns;
        this.seed = seed;
        this.lastAction = lastAction;
        this.result = result;
    }
}
