import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** W/L/D and championship totals for the current tournament series. */
class TournamentStats {

    static class Record {
        int wins;
        int losses;
        int draws;
        int championships;
    }

    private final Map<Integer, Record> records = new LinkedHashMap<Integer, Record>();
    private final Map<Integer, Map<Integer, Integer>> headToHeadWins =
            new LinkedHashMap<Integer, Map<Integer, Integer>>();

    TournamentStats(List<CellAI> ais) {
        for (CellAI ai : ais) {
            records.put(ai.getID(), new Record());
            headToHeadWins.put(ai.getID(), new LinkedHashMap<Integer, Integer>());
        }
    }

    void recordMatch(MatchResult result, List<CellAI> participants) {
        if (result == null) {
            return;
        }

        if (result.isDraw()) {
            for (CellAI ai : participants) {
                Record record = records.get(ai.getID());
                if (record == null) {
                    continue;
                }

                if (result.drawIDs.contains(ai.getID())) {
                    record.draws++;
                }
                else {
                    // In a multiplayer turn-limit match, players below tied leaders
                    // lost even though the leaders drew with each other.
                    record.losses++;
                }
            }
            return;
        }

        int winnerID = result.winnerID;
        Record winnerRecord = records.get(winnerID);
        if (winnerRecord != null) {
            winnerRecord.wins++;
        }

        for (CellAI ai : participants) {
            if (ai.getID() != winnerID) {
                Record loserRecord = records.get(ai.getID());
                if (loserRecord != null) {
                    loserRecord.losses++;
                }

                Map<Integer, Integer> row = headToHeadWins.get(winnerID);
                if (row != null) {
                    row.put(ai.getID(), row.getOrDefault(ai.getID(), 0) + 1);
                }
            }
        }
    }

    void recordChampionship(int aiID) {
        Record record = records.get(aiID);
        if (record != null) {
            record.championships++;
        }
    }

    Record get(int aiID) {
        return records.get(aiID);
    }

    int getHeadToHeadWins(int winnerID, int loserID) {
        Map<Integer, Integer> row = headToHeadWins.get(winnerID);
        if (row == null) {
            return 0;
        }
        return row.getOrDefault(loserID, 0);
    }
}
