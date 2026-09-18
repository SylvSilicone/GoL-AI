import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Final outcome of one Cell Wars match. */
class MatchResult {

    final Integer winnerID; // null means draw
    final String reason;
    final Map<Integer, Integer> finalCounts;
    final List<Integer> drawIDs;
    final long seed;
    final int turns;

    MatchResult(Integer winnerID, String reason, Map<Integer, Integer> finalCounts,
                List<Integer> drawIDs, long seed, int turns) {
        this.winnerID = winnerID;
        this.reason = reason;
        this.finalCounts = Collections.unmodifiableMap(new LinkedHashMap<Integer, Integer>(finalCounts));
        this.drawIDs = Collections.unmodifiableList(drawIDs);
        this.seed = seed;
        this.turns = turns;
    }

    boolean isDraw() {
        return winnerID == null;
    }
}
