import java.util.ArrayList;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

/** Helpful read-only functions students may use while designing an AI. */
public final class GridFunctions {

    private GridFunctions() {
    }

    /**
     * Returns the ID of the most common living neighbor around (row, col).
     * If no living neighbors exist, returns -1. Ties are broken randomly.
     */
    public static int mostCommonNeighbor(int row, int col, Grid grid) {
        return mostCommonNeighbor(row, col, grid, ThreadLocalRandom.current());
    }

    /** Simulator version that uses the match's seeded random generator. */
    static int mostCommonNeighbor(int row, int col, Grid grid, Random random) {
        TreeMap<Integer, Integer> counts = new TreeMap<Integer, Integer>();

        for (int r = row - 1; r <= row + 1; r++) {
            for (int c = col - 1; c <= col + 1; c++) {
                if (r == row && c == col) {
                    continue;
                }

                if (r >= 0 && c >= 0 && r < grid.getRows() && c < grid.getCols()) {
                    int id = grid.getCell(r, c);
                    if (id != -1) {
                        counts.put(id, counts.getOrDefault(id, 0) + 1);
                    }
                }
            }
        }

        if (counts.isEmpty()) {
            return -1;
        }

        ArrayList<Integer> tiedIDs = new ArrayList<Integer>();
        int max = -1;

        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > max) {
                max = entry.getValue();
                tiedIDs.clear();
                tiedIDs.add(entry.getKey());
            }
            else if (entry.getValue() == max) {
                tiedIDs.add(entry.getKey());
            }
        }

        return tiedIDs.get(random.nextInt(tiedIDs.size()));
    }

    /** Returns the number of living neighbors around (row, col). */
    public static int getNeighbors(int row, int col, Grid grid) {
        int alive = 0;

        for (int r = row - 1; r <= row + 1; r++) {
            for (int c = col - 1; c <= col + 1; c++) {
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
}
