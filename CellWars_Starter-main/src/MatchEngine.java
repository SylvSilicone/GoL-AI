import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Core Cell Wars game engine. Swing/UI code lives in Simulation.java so the
 * fundamental game rules remain separate from how the match is displayed.
 */
class MatchEngine {

    private final List<CellAI> participants;
    private final List<CellAI> activePlayers;
    private final Random random;
    private final long seed;

    private int[][] grid;
    private Map<Integer, Integer> counts = new LinkedHashMap<Integer, Integer>();
    private Map<Integer, Integer> deltas = new LinkedHashMap<Integer, Integer>();
    // Counts at the beginning of the current player's turn. This lets the GUI
    // show an immediate +/-1 change during the AI action phase and then the
    // full net change after the Conway update.
    private Map<Integer, Integer> turnStartCounts = new LinkedHashMap<Integer, Integer>();
    private final Map<Integer, Integer> strikes = new LinkedHashMap<Integer, Integer>();

    private int completedTurns = 0;
    private TurnAction lastAction;
    private MatchResult result;

    MatchEngine(List<CellAI> orderedPlayers, long seed) {
        if (orderedPlayers == null || orderedPlayers.size() < 2) {
            throw new IllegalArgumentException("A Cell Wars match needs at least two AIs.");
        }

        this.participants = new ArrayList<CellAI>(orderedPlayers);
        this.activePlayers = new ArrayList<CellAI>(orderedPlayers);
        this.seed = seed;
        this.random = new Random(seed);

        for (CellAI ai : participants) {
            strikes.put(ai.getID(), 0);
            ai.resetRandom(mixSeed(seed, ai.getID()));
        }

        populateGrid();
    }

    private long mixSeed(long base, int id) {
        long value = base ^ (0x9E3779B97F4A7C15L * (id + 1L));
        value ^= (value >>> 30);
        value *= 0xBF58476D1CE4E5B9L;
        value ^= (value >>> 27);
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private void populateGrid() {
        int minimumCells = participants.size() * CellWarsConfig.STARTING_CELLS_PER_AI;
        int size = (int) Math.sqrt(participants.size() * CellWarsConfig.SIZE_MULTIPLIER);
        size = Math.max(size, 1);
        while (size * size < minimumCells) {
            size++;
        }

        grid = new int[size][size];
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                grid[row][col] = -1;
            }
        }

        for (CellAI ai : participants) {
            int placed = 0;
            while (placed < CellWarsConfig.STARTING_CELLS_PER_AI) {
                int row = random.nextInt(size);
                int col = random.nextInt(size);

                if (grid[row][col] == -1) {
                    grid[row][col] = ai.getID();
                    placed++;
                }
            }
        }

        counts = calculateCounts();
        turnStartCounts = new LinkedHashMap<Integer, Integer>(counts);
        for (CellAI ai : participants) {
            deltas.put(ai.getID(), 0);
        }
    }

    /**
     * Phase 1 of a turn: ask the current AI for a location and apply only that
     * spawn/kill choice. The Conway update happens separately in finishTurn().
     */
    TurnAction beginTurn(SafeAIInvoker invoker) {
        CellAI current;
        Grid snapshot;

        synchronized (this) {
            if (isFinished() || activePlayers.isEmpty()) {
                return null;
            }

            // Preserve the original Cell Wars round-robin turn order.
            current = activePlayers.remove(0);
            activePlayers.add(current);
            turnStartCounts = new LinkedHashMap<Integer, Integer>(counts);
            snapshot = new Grid(grid);
        }

        AISelectionResult selection = invoker.select(current, snapshot);

        synchronized (this) {
            if (isFinished() || !isActive(current.getID())) {
                lastAction = new TurnAction(
                        current.getID(), safeName(current), TurnAction.Type.SKIPPED,
                        null, "Turn cancelled because the match already ended.",
                        strikes.getOrDefault(current.getID(), 0), false);
                return lastAction;
            }

            if (selection.status != AISelectionResult.Status.OK) {
                return registerInvalidMove(current, selection.message);
            }

            Location loc = selection.location;
            if (loc.getRow() < 0 || loc.getCol() < 0
                    || loc.getRow() >= grid.length || loc.getCol() >= grid[0].length) {
                return registerInvalidMove(current,
                        "Invalid location " + loc + " is outside the grid.");
            }

            TurnAction.Type type;
            String message;

            if (grid[loc.getRow()][loc.getCol()] == -1) {
                grid[loc.getRow()][loc.getCol()] = current.getID();
                type = TurnAction.Type.SPAWN;
                message = "spawned a cell at " + loc;
            }
            else {
                int killedID = grid[loc.getRow()][loc.getCol()];
                grid[loc.getRow()][loc.getCol()] = -1;
                type = TurnAction.Type.KILL;
                CellAI killedAI = findParticipant(killedID);
                String target = killedAI == null ? "a living cell" : safeName(killedAI) + "'s cell";
                message = "killed " + target + " at " + loc;
            }

            // Keep the live scoreboard synchronized with the board during the
            // brief AI-action phase, before Conway's rules are applied.
            counts = calculateCounts();
            deltas = calculateDeltas(turnStartCounts, counts);

            lastAction = new TurnAction(
                    current.getID(), safeName(current), type, loc, message,
                    strikes.getOrDefault(current.getID(), 0), true);
            return lastAction;
        }
    }

    private TurnAction registerInvalidMove(CellAI current, String problem) {
        int newStrikes = strikes.getOrDefault(current.getID(), 0) + 1;
        strikes.put(current.getID(), newStrikes);

        if (newStrikes >= CellWarsConfig.MAX_INVALID_MOVES) {
            removeActivePlayer(current.getID(), true);
            lastAction = new TurnAction(
                    current.getID(), safeName(current), TurnAction.Type.FORFEIT,
                    null,
                    problem + " Strike " + newStrikes + "/" + CellWarsConfig.MAX_INVALID_MOVES
                            + ". " + safeName(current) + " forfeits.",
                    newStrikes, false);
            resolveAdministrativeFinish(current.getID(), "Forfeit");
        }
        else {
            // No board action occurred, so the action-phase delta for this
            // turn is zero rather than the previous turn's delta.
            deltas = calculateDeltas(turnStartCounts, counts);
            lastAction = new TurnAction(
                    current.getID(), safeName(current), TurnAction.Type.SKIPPED,
                    null,
                    problem + " Turn skipped. Strike " + newStrikes + "/"
                            + CellWarsConfig.MAX_INVALID_MOVES + ".",
                    newStrikes, true);
        }

        return lastAction;
    }

    /** Phase 2 of a turn: run one complete multiplayer Game of Life update. */
    synchronized void finishTurn() {
        if (isFinished()) {
            return;
        }

        Grid before = new Grid(grid);
        int[][] next = new int[grid.length][grid[0].length];

        for (int row = 0; row < grid.length; row++) {
            for (int col = 0; col < grid[row].length; col++) {
                int neighbors = GridFunctions.getNeighbors(row, col, before);

                if (before.getCell(row, col) != -1) {
                    if (neighbors < 2 || neighbors > 3) {
                        next[row][col] = -1;
                    }
                    else {
                        next[row][col] = GridFunctions.mostCommonNeighbor(row, col, before, random);
                    }
                }
                else {
                    if (neighbors == 3) {
                        next[row][col] = GridFunctions.mostCommonNeighbor(row, col, before, random);
                    }
                    else {
                        next[row][col] = -1;
                    }
                }
            }
        }

        Map<Integer, Integer> baselineCounts = turnStartCounts.isEmpty()
                ? counts
                : turnStartCounts;
        grid = next;
        counts = calculateCounts();
        deltas = calculateDeltas(baselineCounts, counts);
        completedTurns++;

        // Remove AIs whose cells died out after the Conway update.
        ArrayList<Integer> extinct = new ArrayList<Integer>();
        for (CellAI ai : activePlayers) {
            if (counts.getOrDefault(ai.getID(), 0) == 0) {
                extinct.add(ai.getID());
            }
        }
        for (int id : extinct) {
            removeActivePlayer(id, false);
        }

        if (activePlayers.isEmpty()) {
            // Only the players who were still active at the start of this update
            // should receive a draw. A player eliminated earlier in a multiplayer
            // match should remain a loss, not be pulled back into the draw.
            finishDraw("Simultaneous extinction", extinct);
            return;
        }

        if (activePlayers.size() == 1) {
            finishWinner(activePlayers.get(0).getID(), "Elimination");
            return;
        }

        if (completedTurns >= CellWarsConfig.MAX_TURNS) {
            resolveTurnLimit();
        }
    }

    /** Manual concession from the GUI. */
    synchronized boolean concede(int aiID) {
        if (isFinished() || !isActive(aiID)) {
            return false;
        }

        int activeBefore = activePlayers.size();
        removeActivePlayer(aiID, activeBefore > 2);
        resolveAdministrativeFinish(aiID, "Concession");
        return true;
    }

    private void resolveAdministrativeFinish(int removedID, String reason) {
        if (activePlayers.isEmpty()) {
            finishDraw(reason + " left no active players", Collections.<Integer>emptyList());
        }
        else if (activePlayers.size() == 1) {
            finishWinner(activePlayers.get(0).getID(), reason);
        }
    }

    private void resolveTurnLimit() {
        int best = Integer.MIN_VALUE;
        ArrayList<Integer> leaders = new ArrayList<Integer>();

        for (CellAI ai : activePlayers) {
            int value = counts.getOrDefault(ai.getID(), 0);
            if (value > best) {
                best = value;
                leaders.clear();
                leaders.add(ai.getID());
            }
            else if (value == best) {
                leaders.add(ai.getID());
            }
        }

        if (leaders.size() == 1) {
            finishWinner(leaders.get(0), "Turn limit — win by cell-count decision");
        }
        else {
            finishDraw("Turn limit — tied cell count", leaders);
        }
    }

    private void finishWinner(int winnerID, String reason) {
        result = new MatchResult(
                winnerID, reason, counts,
                Collections.<Integer>emptyList(), seed, completedTurns);
    }

    private void finishDraw(String reason, List<Integer> drawIDs) {
        List<Integer> ids = drawIDs;
        if (ids == null || ids.isEmpty()) {
            ids = new ArrayList<Integer>();
            for (CellAI ai : participants) {
                ids.add(ai.getID());
            }
        }

        result = new MatchResult(null, reason, counts, ids, seed, completedTurns);
    }

    private void removeActivePlayer(int aiID, boolean clearCells) {
        activePlayers.removeIf(ai -> ai.getID() == aiID);

        if (clearCells) {
            Map<Integer, Integer> previousCounts = counts;
            for (int row = 0; row < grid.length; row++) {
                for (int col = 0; col < grid[row].length; col++) {
                    if (grid[row][col] == aiID) {
                        grid[row][col] = -1;
                    }
                }
            }
            counts = calculateCounts();
            deltas = calculateDeltas(previousCounts, counts);
        }
    }

    private Map<Integer, Integer> calculateCounts() {
        LinkedHashMap<Integer, Integer> resultCounts = new LinkedHashMap<Integer, Integer>();
        for (CellAI ai : participants) {
            resultCounts.put(ai.getID(), 0);
        }

        for (int[] row : grid) {
            for (int id : row) {
                if (id >= 0 && resultCounts.containsKey(id)) {
                    resultCounts.put(id, resultCounts.get(id) + 1);
                }
            }
        }

        return resultCounts;
    }

    private Map<Integer, Integer> calculateDeltas(Map<Integer, Integer> before,
                                                   Map<Integer, Integer> after) {
        LinkedHashMap<Integer, Integer> resultDeltas = new LinkedHashMap<Integer, Integer>();
        for (CellAI ai : participants) {
            int id = ai.getID();
            resultDeltas.put(id,
                    after.getOrDefault(id, 0) - before.getOrDefault(id, 0));
        }
        return resultDeltas;
    }

    private boolean isActive(int id) {
        for (CellAI ai : activePlayers) {
            if (ai.getID() == id) {
                return true;
            }
        }
        return false;
    }


    private String safeName(CellAI ai) {
        try {
            String name = ai.getAIName();
            if (name != null && !name.trim().isEmpty()) {
                return name.trim();
            }
        }
        catch (Throwable ignored) {
        }
        return ai.getClass().getSimpleName();
    }

    private CellAI findParticipant(int id) {
        for (CellAI ai : participants) {
            if (ai.getID() == id) {
                return ai;
            }
        }
        return null;
    }

    synchronized boolean isFinished() {
        return result != null;
    }

    synchronized MatchResult getResult() {
        return result;
    }

    synchronized MatchSnapshot snapshot() {
        int[][] copy = new int[grid.length][grid[0].length];
        for (int row = 0; row < grid.length; row++) {
            System.arraycopy(grid[row], 0, copy[row], 0, grid[row].length);
        }

        return new MatchSnapshot(
                copy, participants, activePlayers, counts, deltas, strikes,
                completedTurns, seed, lastAction, result);
    }
}
