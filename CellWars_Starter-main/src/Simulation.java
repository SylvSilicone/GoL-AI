import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * Cell Wars 2.0
 *
 * The fundamental game remains the same: each AI selects one location to spawn
 * or kill, then one multiplayer Conway Game of Life update runs.
 *
 * This version improves visualization, scoring, concessions, fairness,
 * reproducibility, and tournament safety.
 */
public class Simulation {

    private static final Color BACKGROUND = new Color(248, 249, 251);
    private static final Color PANEL_BORDER = new Color(210, 214, 220);
    private static final Color GRID_LINE = new Color(226, 229, 233);
    private static final Color SPAWN_HIGHLIGHT = new Color(34, 139, 34);
    private static final Color KILL_HIGHLIGHT = new Color(205, 45, 45);

    private final JFrame window = new JFrame("Cell Wars 2.0");
    private final BoardPanel boardPanel = new BoardPanel();

    private final JLabel matchLabel = new JLabel("Ready");
    private final JLabel turnLabel = new JLabel("Turn: —");
    private final JLabel seedLabel = new JLabel("Match Seed: —");
    private final JLabel actionLabel = new JLabel("Select AIs and start a tournament.");
    private final JLabel leaderLabel = new JLabel(" ");
    private final JLabel seriesLabel = new JLabel(" ");

    private final JPanel scoreboardPanel = new JPanel();
    private final JPanel concedePanel = new JPanel();
    private final JPanel aiSelectionPanel = new JPanel();

    private final DefaultTableModel standingsModel = new DefaultTableModel(
            new Object[] { "AI", "W", "L", "D", "Titles" }, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable standingsTable = new JTable(standingsModel);
    private final JTextArea eventLog = new JTextArea(9, 30);

    private final JButton startButton = new JButton("Start Tournament");
    private final JButton pauseButton = new JButton("Pause");
    private final JButton stepButton = new JButton("Step");
    private final JButton newSeedButton = new JButton("New Seed");

    private final JSlider delaySlider = new JSlider(50, 1500, 500);
    private final JTextField seedField = new JTextField(Long.toString(CellWarsConfig.DEFAULT_BASE_SEED), 12);
    private final JSpinner tournamentSpinner = new JSpinner(
            new SpinnerNumberModel(CellWarsConfig.DEFAULT_TOURNAMENT_RUNS, 1, 100, 1));
    private final JSpinner aisPerMatchSpinner = new JSpinner(new SpinnerNumberModel(2, 2, 8, 1));

    private final List<CellAI> allAIs = new ArrayList<CellAI>();
    // Keep external class loaders alive for the lifetime of the simulator so
    // precompiled opponents can be loaded from lib/ without exposing source.
    private final List<ClassLoader> externalAILoaders = new ArrayList<ClassLoader>();
    private final Map<Integer, Color> colorsByID = new LinkedHashMap<Integer, Color>();
    private final Map<Integer, String> displayNamesByID = new LinkedHashMap<Integer, String>();
    private final Map<Integer, JCheckBox> aiCheckBoxes = new LinkedHashMap<Integer, JCheckBox>();

    private final Object pauseLock = new Object();
    private volatile boolean paused = false;
    private volatile boolean stepRequested = false;
    private volatile boolean tournamentRunning = false;
    private volatile int delayMillis = 500;

    private volatile MatchEngine currentEngine;
    private TournamentStats stats;
    private List<CellAI> selectedAIs = new ArrayList<CellAI>();
    private int matchSequence = 0;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new Simulation();
            }
        });
    }

    public Simulation() {
        loadAIs();
        assignColorsAndNames();
        buildInterface();
        refreshStandings();
    }

    private void buildInterface() {
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setLayout(new BorderLayout(8, 8));
        window.getContentPane().setBackground(BACKGROUND);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(31, 35, 43));
        header.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

        JLabel title = new JLabel("CELL WARS 2.0");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        header.add(title, BorderLayout.WEST);

        matchLabel.setForeground(Color.WHITE);
        matchLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        matchLabel.setFont(matchLabel.getFont().deriveFont(Font.BOLD, 14f));
        header.add(matchLabel, BorderLayout.EAST);
        window.add(header, BorderLayout.NORTH);

        boardPanel.setPreferredSize(new Dimension(730, 730));
        boardPanel.setMinimumSize(new Dimension(600, 600));
        boardPanel.setBorder(BorderFactory.createLineBorder(PANEL_BORDER));
        window.add(boardPanel, BorderLayout.CENTER);

        JPanel side = new JPanel();
        side.setBackground(BACKGROUND);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 8));

        side.add(section("MATCH STATUS", buildStatusPanel()));
        side.add(Box.createVerticalStrut(8));
        side.add(section("LIVE CELL SCORE", buildScorePanel()));
        side.add(Box.createVerticalStrut(8));
        side.add(section("CONTROLS", buildControlsPanel()));
        side.add(Box.createVerticalStrut(8));
        side.add(section("CONCEDE MATCH", concedePanel));
        side.add(Box.createVerticalStrut(8));
        side.add(section("TOURNAMENT STANDINGS", buildStandingsPanel()));
        side.add(Box.createVerticalStrut(8));
        side.add(section("SELECT AIs", buildAISelectionPanel()));
        side.add(Box.createVerticalStrut(8));
        side.add(section("EVENT LOG", buildEventLogPanel()));

        JScrollPane sideScroll = new JScrollPane(side);
        sideScroll.setBorder(null);
        sideScroll.setPreferredSize(new Dimension(455, 730));
        sideScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sideScroll.getVerticalScrollBar().setUnitIncrement(14);
        window.add(sideScroll, BorderLayout.EAST);

        pauseButton.setEnabled(false);
        stepButton.setEnabled(false);

        window.pack();
        window.setMinimumSize(new Dimension(1120, 720));
        window.setLocationRelativeTo(null);
        window.setVisible(true);

        if (allAIs.size() < 2) {
            JOptionPane.showMessageDialog(window,
                    "Cell Wars found fewer than two concrete CellAI subclasses.\n"
                            + "Add at least two AIs to src and compile again.",
                    "Not Enough AIs", JOptionPane.WARNING_MESSAGE);
        }
    }

    private JPanel section(String title, Component content) {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(PANEL_BORDER),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));

        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        panel.add(label, BorderLayout.NORTH);
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildStatusPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        turnLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        seedLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        actionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(turnLabel);
        panel.add(Box.createVerticalStrut(3));
        panel.add(seedLabel);
        panel.add(Box.createVerticalStrut(6));
        panel.add(actionLabel);
        return panel;
    }

    private JPanel buildScorePanel() {
        JPanel container = new JPanel(new BorderLayout(4, 4));
        container.setOpaque(false);
        scoreboardPanel.setOpaque(false);
        scoreboardPanel.setLayout(new BoxLayout(scoreboardPanel, BoxLayout.Y_AXIS));
        container.add(scoreboardPanel, BorderLayout.CENTER);

        JPanel labels = new JPanel();
        labels.setOpaque(false);
        labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));
        leaderLabel.setFont(leaderLabel.getFont().deriveFont(Font.BOLD));
        labels.add(leaderLabel);
        labels.add(seriesLabel);
        container.add(labels, BorderLayout.SOUTH);
        return container;
    }

    private JPanel buildControlsPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        startButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        startButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, startButton.getPreferredSize().height));
        panel.add(startButton);
        panel.add(Box.createVerticalStrut(5));

        JPanel buttons = new JPanel(new GridLayout(1, 2, 5, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, pauseButton.getPreferredSize().height));
        buttons.add(pauseButton);
        buttons.add(stepButton);
        panel.add(buttons);

        panel.add(Box.createVerticalStrut(8));
        panel.add(new JLabel("<html>Turn delay <span style='font-weight:normal'>(slow down to study each decision)</span>:</html>"));
        delaySlider.setMajorTickSpacing(500);
        delaySlider.setPaintTicks(true);
        delaySlider.setOpaque(false);
        delaySlider.addChangeListener(e -> delayMillis = delaySlider.getValue());
        panel.add(delaySlider);

        JPanel seedRow = new JPanel(new BorderLayout(5, 0));
        seedRow.setOpaque(false);
        seedRow.add(new JLabel("Base Seed:"), BorderLayout.WEST);
        seedRow.add(seedField, BorderLayout.CENTER);
        seedRow.add(newSeedButton, BorderLayout.EAST);
        seedRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, seedField.getPreferredSize().height));
        panel.add(seedRow);

        JPanel tournamentRow = new JPanel(new GridLayout(1, 2, 5, 0));
        tournamentRow.setOpaque(false);
        tournamentRow.add(new JLabel("Tournament Runs:"));
        tournamentRow.add(tournamentSpinner);
        panel.add(tournamentRow);

        JPanel groupRow = new JPanel(new GridLayout(1, 2, 5, 0));
        groupRow.setOpaque(false);
        groupRow.add(new JLabel("AIs per Match:"));
        groupRow.add(aisPerMatchSpinner);
        panel.add(groupRow);

        startButton.addActionListener(this::startTournamentClicked);
        pauseButton.addActionListener(e -> togglePause());
        stepButton.addActionListener(e -> requestStep());
        newSeedButton.addActionListener(e -> seedField.setText(Long.toString(System.currentTimeMillis())));

        return panel;
    }

    private JPanel buildStandingsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        standingsTable.setFillsViewportHeight(true);
        standingsTable.setRowSelectionAllowed(false);
        standingsTable.getTableHeader().setReorderingAllowed(false);

        standingsTable.getColumnModel().getColumn(0).setPreferredWidth(170);
        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        for (int col = 1; col < standingsTable.getColumnCount(); col++) {
            standingsTable.getColumnModel().getColumn(col).setCellRenderer(center);
        }

        JScrollPane scroll = new JScrollPane(standingsTable);
        scroll.setPreferredSize(new Dimension(395, 125));
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildAISelectionPanel() {
        aiSelectionPanel.setOpaque(false);
        aiSelectionPanel.setLayout(new BoxLayout(aiSelectionPanel, BoxLayout.Y_AXIS));

        if (allAIs.isEmpty()) {
            aiSelectionPanel.add(new JLabel("No AI classes found."));
        }
        else {
            for (CellAI ai : allAIs) {
                JCheckBox box = new JCheckBox(displayName(ai), true);
                box.setOpaque(false);
                box.setForeground(colorsByID.get(ai.getID()).darker());
                aiCheckBoxes.put(ai.getID(), box);
                aiSelectionPanel.add(box);
            }
        }

        return aiSelectionPanel;
    }

    private JPanel buildEventLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        eventLog.setEditable(false);
        eventLog.setLineWrap(true);
        eventLog.setWrapStyleWord(true);
        eventLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        JScrollPane scroll = new JScrollPane(eventLog);
        scroll.setPreferredSize(new Dimension(395, 150));
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private void startTournamentClicked(ActionEvent event) {
        if (tournamentRunning) {
            return;
        }

        selectedAIs = getSelectedAIs();
        if (selectedAIs.size() < 2) {
            JOptionPane.showMessageDialog(window,
                    "Select at least two AIs before starting.",
                    "Select AIs", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        long baseSeed;
        try {
            baseSeed = Long.parseLong(seedField.getText().trim());
        }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(window,
                    "Base Seed must be a whole number.",
                    "Invalid Seed", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int runs = (Integer) tournamentSpinner.getValue();
        int aisPerMatch = Math.min((Integer) aisPerMatchSpinner.getValue(), selectedAIs.size());

        tournamentRunning = true;
        paused = false;
        stepRequested = false;
        matchSequence = 0;
        stats = new TournamentStats(selectedAIs);
        eventLog.setText("");
        setSetupControlsEnabled(false);
        pauseButton.setEnabled(true);
        stepButton.setEnabled(false);
        pauseButton.setText("Pause");
        refreshStandings();

        log("Starting " + runs + " tournament run(s) with base seed " + baseSeed + ".");
        log("Loaded competitors: " + joinNames(selectedAIs));

        final long requestedSeed = baseSeed;
        Thread tournamentThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runTournamentSeries(requestedSeed, runs, aisPerMatch);
            }
        }, "CellWars-Tournament");
        tournamentThread.setDaemon(true);
        tournamentThread.start();
    }

    private void runTournamentSeries(long baseSeed, int runs, int aisPerMatch) {
        try (SafeAIInvoker invoker = new SafeAIInvoker()) {
            for (int tournament = 1; tournament <= runs; tournament++) {
                List<CellAI> bracket = new ArrayList<CellAI>(selectedAIs);
                Collections.shuffle(bracket, new Random(deriveSeed(baseSeed, tournament, 0, 0, 0)));

                log("\n=== Tournament " + tournament + " of " + runs + " ===");
                int round = 1;

                while (bracket.size() > 1) {
                    List<CellAI> nextRound = new ArrayList<CellAI>();
                    int matchIndex = 0;

                    for (int start = 0; start < bracket.size(); start += aisPerMatch) {
                        int end = Math.min(start + aisPerMatch, bracket.size());
                        List<CellAI> group = new ArrayList<CellAI>(bracket.subList(start, end));

                        if (group.size() == 1) {
                            nextRound.add(group.get(0));
                            log(displayName(group.get(0)) + " receives a bye.");
                            continue;
                        }

                        matchIndex++;
                        CellAI advancer = playBracketMatch(
                                group, baseSeed, tournament, runs, round, matchIndex, invoker);
                        nextRound.add(advancer);
                    }

                    bracket = nextRound;
                    round++;
                }

                CellAI champion = bracket.get(0);
                stats.recordChampionship(champion.getID());
                log("Tournament " + tournament + " champion: " + displayName(champion));
                refreshStandingsOnEDT();
            }

            log("\n=== SERIES COMPLETE ===");
            log(finalSummary());
        }
        catch (Exception e) {
            log("Tournament stopped because of an unexpected simulator error: " + e);
            e.printStackTrace();
        }
        finally {
            tournamentRunning = false;
            currentEngine = null;
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    matchLabel.setText("Series Complete");
                    actionLabel.setText("Tournament series finished.");
                    pauseButton.setEnabled(false);
                    stepButton.setEnabled(false);
                    setSetupControlsEnabled(true);
                    refreshConcedeButtons(null);
                }
            });
        }
    }

    private CellAI playBracketMatch(List<CellAI> group, long baseSeed, int tournament,
                                    int totalRuns, int round, int matchIndex,
                                    SafeAIInvoker invoker) throws InterruptedException {
        MatchResult latest = null;
        List<CellAI> ordered = new ArrayList<CellAI>(group);

        for (int replay = 0; replay <= CellWarsConfig.MAX_DRAW_REPLAYS; replay++) {
            long matchSeed = deriveSeed(baseSeed, tournament, round, matchIndex, replay);

            // Rotate who moves first so repeated games do not always favor the same AI.
            if (!ordered.isEmpty()) {
                int shift = (tournament + round + matchIndex + replay) % ordered.size();
                Collections.rotate(ordered, -shift);
            }

            latest = playOneMatch(
                    ordered, matchSeed, tournament, totalRuns, round, matchIndex, replay, invoker);
            stats.recordMatch(latest, ordered);
            refreshStandingsOnEDT();

            if (!latest.isDraw()) {
                return findAIByID(latest.winnerID, ordered);
            }

            if (replay < CellWarsConfig.MAX_DRAW_REPLAYS) {
                log("Draw — replaying this bracket matchup with a new deterministic seed.");
            }
        }

        // A bracket must advance somebody. Persistent draws remain recorded as draws;
        // this deterministic tie-break does NOT add a win to the standings.
        List<CellAI> eligible = new ArrayList<CellAI>();
        if (latest != null && latest.drawIDs != null && !latest.drawIDs.isEmpty()) {
            for (int id : latest.drawIDs) {
                CellAI ai = findAIByID(id, group);
                if (ai != null) {
                    eligible.add(ai);
                }
            }
        }
        if (eligible.isEmpty()) {
            eligible.addAll(group);
        }

        Random tieBreaker = new Random(deriveSeed(baseSeed, tournament, round, matchIndex, 999));
        CellAI advancer = eligible.get(tieBreaker.nextInt(eligible.size()));
        log("Persistent draw: " + displayName(advancer)
                + " advances by seeded bracket tie-break. No win is awarded.");
        return advancer;
    }

    private MatchResult playOneMatch(List<CellAI> participants, long matchSeed,
                                     int tournament, int totalRuns, int round,
                                     int matchIndex, int replay, SafeAIInvoker invoker)
            throws InterruptedException {
        matchSequence++;
        MatchEngine engine = new MatchEngine(participants, matchSeed);
        currentEngine = engine;

        final String matchText = "Tournament " + tournament + "/" + totalRuns
                + " • Round " + round + " • Match " + matchIndex
                + (replay == 0 ? "" : " • Replay " + replay);

        log("\n" + matchText + ": " + joinNames(participants)
                + " | seed " + matchSeed);

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                matchLabel.setText(matchText);
                refreshConcedeButtons(engine);
            }
        });
        updateFromEngine(engine, false);

        while (!engine.isFinished()) {
            awaitTurnPermission(engine);
            if (engine.isFinished()) {
                break;
            }

            TurnAction action = engine.beginTurn(invoker);
            if (action != null) {
                logAction(action);
            }
            updateFromEngine(engine, true);

            if (engine.isFinished()) {
                break;
            }

            sleepVisualHalfTurn();

            if (!engine.isFinished() && action != null && action.shouldRunLifeUpdate) {
                engine.finishTurn();
            }
            updateFromEngine(engine, false);

            if (!engine.isFinished()) {
                sleepVisualHalfTurn();
            }
        }

        MatchResult result = engine.getResult();
        updateFromEngine(engine, false);
        logMatchResult(result, participants);
        return result;
    }

    private void awaitTurnPermission(MatchEngine engine) throws InterruptedException {
        synchronized (pauseLock) {
            while (paused && !stepRequested && !engine.isFinished()) {
                pauseLock.wait();
            }
            if (stepRequested) {
                stepRequested = false;
            }
        }
    }

    private void sleepVisualHalfTurn() throws InterruptedException {
        int millis = Math.max(20, delayMillis / 2);
        Thread.sleep(millis);
    }

    private void togglePause() {
        if (!tournamentRunning) {
            return;
        }

        paused = !paused;
        pauseButton.setText(paused ? "Resume" : "Pause");
        stepButton.setEnabled(paused);

        if (!paused) {
            synchronized (pauseLock) {
                pauseLock.notifyAll();
            }
        }
    }

    private void requestStep() {
        if (!tournamentRunning) {
            return;
        }

        paused = true;
        pauseButton.setText("Resume");
        stepButton.setEnabled(true);
        synchronized (pauseLock) {
            stepRequested = true;
            pauseLock.notifyAll();
        }
    }

    private void updateFromEngine(MatchEngine engine, boolean actionPhase) {
        MatchSnapshot snapshot = engine.snapshot();
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                boardPanel.setSnapshot(snapshot, actionPhase);
                turnLabel.setText("Completed Turns: " + snapshot.completedTurns
                        + " / " + CellWarsConfig.MAX_TURNS);
                seedLabel.setText("Match Seed: " + snapshot.seed);
                actionLabel.setText(buildActionText(snapshot, actionPhase));
                refreshScoreboard(snapshot);
                refreshConcedeButtons(engine);
                boardPanel.repaint();
            }
        });
    }

    private String buildActionText(MatchSnapshot snapshot, boolean actionPhase) {
        if (snapshot.result != null) {
            if (snapshot.result.isDraw()) {
                return "Result: DRAW — " + snapshot.result.reason;
            }
            CellAI winner = findAIByID(snapshot.result.winnerID, snapshot.participants);
            return "Result: " + displayName(winner) + " wins — " + snapshot.result.reason;
        }

        TurnAction action = snapshot.lastAction;
        if (action == null) {
            if (!snapshot.activePlayers.isEmpty()) {
                return "Next turn: " + displayName(snapshot.activePlayers.get(0));
            }
            return "Preparing match...";
        }

        String phase = actionPhase ? "AI ACTION: " : "CONWAY UPDATE COMPLETE: ";
        return "<html>" + phase + "<b>" + escapeHTML(displayName(action.playerID))
                + "</b> " + escapeHTML(action.message) + "</html>";
    }

    private void refreshScoreboard(MatchSnapshot snapshot) {
        scoreboardPanel.removeAll();

        int totalLiving = 0;
        for (CellAI ai : snapshot.participants) {
            totalLiving += snapshot.counts.getOrDefault(ai.getID(), 0);
        }

        int best = -1;
        List<CellAI> leaders = new ArrayList<CellAI>();

        for (CellAI ai : snapshot.participants) {
            int id = ai.getID();
            int count = snapshot.counts.getOrDefault(id, 0);
            int delta = snapshot.deltas.getOrDefault(id, 0);
            boolean active = containsID(snapshot.activePlayers, id);

            if (active) {
                if (count > best) {
                    best = count;
                    leaders.clear();
                    leaders.add(ai);
                }
                else if (count == best) {
                    leaders.add(ai);
                }
            }

            JPanel row = new JPanel(new BorderLayout(5, 2));
            row.setOpaque(false);
            row.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));

            JLabel name = new JLabel("■ " + displayName(ai));
            name.setForeground(colorsByID.get(id).darker());
            name.setFont(name.getFont().deriveFont(Font.BOLD));
            row.add(name, BorderLayout.NORTH);

            double percent = totalLiving == 0 ? 0.0 : (100.0 * count / totalLiving);
            JProgressBar bar = new JProgressBar(0, 1000);
            bar.setValue((int) Math.round(percent * 10));
            bar.setForeground(colorsByID.get(id));
            bar.setStringPainted(true);
            String deltaText = delta > 0 ? "+" + delta : Integer.toString(delta);
            String state = active ? "" : " • OUT";
            bar.setString(count + " cells • " + String.format("%.1f%%", percent)
                    + " • Δ " + deltaText + state);
            row.add(bar, BorderLayout.CENTER);

            int strikeCount = snapshot.strikes.getOrDefault(id, 0);
            if (strikeCount > 0) {
                JLabel strike = new JLabel("AI warnings: " + strikeCount + "/"
                        + CellWarsConfig.MAX_INVALID_MOVES);
                strike.setFont(strike.getFont().deriveFont(10f));
                row.add(strike, BorderLayout.SOUTH);
            }

            scoreboardPanel.add(row);
        }

        if (snapshot.result != null && !snapshot.result.isDraw()) {
            leaderLabel.setText("Winner: " + displayName(snapshot.result.winnerID));
        }
        else if (leaders.size() == 1) {
            int second = -1;
            for (CellAI ai : snapshot.activePlayers) {
                if (ai.getID() != leaders.get(0).getID()) {
                    second = Math.max(second, snapshot.counts.getOrDefault(ai.getID(), 0));
                }
            }
            int margin = second < 0 ? best : best - second;
            leaderLabel.setText("Leader: " + displayName(leaders.get(0))
                    + (snapshot.activePlayers.size() > 1 ? " by " + margin + " cells" : ""));
        }
        else if (leaders.size() > 1) {
            leaderLabel.setText("Tied lead: " + joinNames(leaders) + " at " + best + " cells");
        }
        else {
            leaderLabel.setText("No living cells");
        }

        updateSeriesLabel();
        scoreboardPanel.revalidate();
        scoreboardPanel.repaint();
    }

    private void refreshConcedeButtons(MatchEngine engine) {
        concedePanel.removeAll();
        concedePanel.setOpaque(false);
        concedePanel.setLayout(new BoxLayout(concedePanel, BoxLayout.Y_AXIS));

        if (engine == null || engine.isFinished()) {
            concedePanel.add(new JLabel("No active match."));
        }
        else {
            MatchSnapshot snapshot = engine.snapshot();
            for (CellAI ai : snapshot.activePlayers) {
                JButton button = new JButton("Concede " + displayName(ai));
                button.setAlignmentX(Component.LEFT_ALIGNMENT);
                button.addActionListener(e -> confirmConcession(engine, ai));
                concedePanel.add(button);
                concedePanel.add(Box.createVerticalStrut(3));
            }
        }

        concedePanel.revalidate();
        concedePanel.repaint();
    }

    private void confirmConcession(MatchEngine engine, CellAI ai) {
        if (engine != currentEngine || engine.isFinished()) {
            return;
        }

        int answer = JOptionPane.showConfirmDialog(window,
                displayName(ai) + " is conceding.\n\n"
                        + "This will end that AI's participation in the current match.\n"
                        + "Continue?",
                "Confirm Concession",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (answer == JOptionPane.YES_OPTION) {
            if (engine.concede(ai.getID())) {
                log(displayName(ai) + " conceded the current match.");
                updateFromEngine(engine, false);
                synchronized (pauseLock) {
                    pauseLock.notifyAll();
                }
            }
        }
    }

    private void logAction(TurnAction action) {
        if (action.type == TurnAction.Type.SKIPPED || action.type == TurnAction.Type.FORFEIT) {
            log(displayName(action.playerID) + ": " + action.message);
        }
    }

    private void logMatchResult(MatchResult result, List<CellAI> participants) {
        if (result == null) {
            log("Match ended without a result.");
            return;
        }

        if (result.isDraw()) {
            log("DRAW after " + result.turns + " turns — " + result.reason
                    + ". Final cells: " + formatCounts(result, participants));
        }
        else {
            log(displayName(result.winnerID) + " wins after " + result.turns
                    + " turns by " + result.reason + ". Final cells: "
                    + formatCounts(result, participants));
        }
    }

    private String formatCounts(MatchResult result, List<CellAI> participants) {
        ArrayList<String> pieces = new ArrayList<String>();
        for (CellAI ai : participants) {
            pieces.add(displayName(ai) + " " + result.finalCounts.getOrDefault(ai.getID(), 0));
        }
        return String.join(" | ", pieces);
    }

    private void refreshStandingsOnEDT() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                refreshStandings();
            }
        });
    }

    private void refreshStandings() {
        standingsModel.setRowCount(0);
        List<CellAI> rows = selectedAIs.isEmpty() ? allAIs : selectedAIs;

        for (CellAI ai : rows) {
            TournamentStats.Record record = stats == null ? null : stats.get(ai.getID());
            standingsModel.addRow(new Object[] {
                    displayName(ai),
                    record == null ? 0 : record.wins,
                    record == null ? 0 : record.losses,
                    record == null ? 0 : record.draws,
                    record == null ? 0 : record.championships
            });
        }
        updateSeriesLabel();
    }

    private void updateSeriesLabel() {
        if (stats == null || selectedAIs.size() != 2) {
            seriesLabel.setText(" ");
            return;
        }

        CellAI a = selectedAIs.get(0);
        CellAI b = selectedAIs.get(1);
        TournamentStats.Record aRecord = stats.get(a.getID());
        TournamentStats.Record bRecord = stats.get(b.getID());
        if (aRecord == null || bRecord == null) {
            seriesLabel.setText(" ");
            return;
        }

        int draws = Math.min(aRecord.draws, bRecord.draws);
        seriesLabel.setText("Series: " + displayName(a) + " " + aRecord.wins
                + " — " + displayName(b) + " " + bRecord.wins
                + " • Draws " + draws);
    }

    private String finalSummary() {
        StringBuilder sb = new StringBuilder();
        List<CellAI> ranked = new ArrayList<CellAI>(selectedAIs);
        ranked.sort(new Comparator<CellAI>() {
            @Override
            public int compare(CellAI a, CellAI b) {
                TournamentStats.Record ar = stats.get(a.getID());
                TournamentStats.Record br = stats.get(b.getID());
                int byTitles = Integer.compare(br.championships, ar.championships);
                if (byTitles != 0) return byTitles;
                int byWins = Integer.compare(br.wins, ar.wins);
                if (byWins != 0) return byWins;
                return displayName(a).compareToIgnoreCase(displayName(b));
            }
        });

        for (CellAI ai : ranked) {
            TournamentStats.Record r = stats.get(ai.getID());
            sb.append(displayName(ai)).append(": ")
                    .append(r.wins).append(" W, ")
                    .append(r.losses).append(" L, ")
                    .append(r.draws).append(" D, ")
                    .append(r.championships).append(" title(s)\n");
        }
        return sb.toString().trim();
    }

    private void setSetupControlsEnabled(boolean enabled) {
        startButton.setEnabled(enabled && allAIs.size() >= 2);
        seedField.setEnabled(enabled);
        newSeedButton.setEnabled(enabled);
        tournamentSpinner.setEnabled(enabled);
        aisPerMatchSpinner.setEnabled(enabled);
        for (JCheckBox box : aiCheckBoxes.values()) {
            box.setEnabled(enabled);
        }
    }

    private List<CellAI> getSelectedAIs() {
        ArrayList<CellAI> result = new ArrayList<CellAI>();
        for (CellAI ai : allAIs) {
            JCheckBox box = aiCheckBoxes.get(ai.getID());
            if (box != null && box.isSelected()) {
                result.add(ai);
            }
        }
        return result;
    }

    private boolean containsID(List<CellAI> ais, int id) {
        for (CellAI ai : ais) {
            if (ai.getID() == id) {
                return true;
            }
        }
        return false;
    }

    private CellAI findAIByID(Integer id, List<CellAI> list) {
        if (id == null) {
            return null;
        }
        for (CellAI ai : list) {
            if (ai.getID() == id) {
                return ai;
            }
        }
        return null;
    }

    private long deriveSeed(long base, int tournament, int round, int match, int replay) {
        long x = base;
        x ^= 0x9E3779B97F4A7C15L * tournament;
        x ^= 0xBF58476D1CE4E5B9L * (round + 17L);
        x ^= 0x94D049BB133111EBL * (match + 31L);
        x ^= 0xD6E8FEB86659FD93L * (replay + 47L);
        x ^= (x >>> 30);
        x *= 0xBF58476D1CE4E5B9L;
        x ^= (x >>> 27);
        x *= 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }

    private void log(String message) {
        System.out.println(message);
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                eventLog.append(message + "\n");
                if (eventLog.getDocument().getLength() > 30_000) {
                    eventLog.setText(eventLog.getText().substring(eventLog.getText().length() - 20_000));
                }
                eventLog.setCaretPosition(eventLog.getDocument().getLength());
            }
        });
    }

    private String joinNames(List<CellAI> ais) {
        ArrayList<String> names = new ArrayList<String>();
        for (CellAI ai : ais) {
            names.add(displayName(ai));
        }
        return String.join(", ", names);
    }

    private String displayName(CellAI ai) {
        if (ai == null) {
            return "Unknown AI";
        }
        return displayNamesByID.getOrDefault(ai.getID(), ai.getClass().getSimpleName());
    }

    private String displayName(int id) {
        return displayNamesByID.getOrDefault(id, "AI #" + id);
    }

    private String escapeHTML(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void loadAIs() {
        List<Class<? extends CellAI>> classes = discoverAIClasses();

        for (Class<? extends CellAI> clazz : classes) {
            try {
                allAIs.add(clazz.getDeclaredConstructor().newInstance());
            }
            catch (Exception e) {
                System.err.println("Could not load " + clazz.getName() + ": " + e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Class<? extends CellAI>> discoverAIClasses() {
        Map<String, Class<? extends CellAI>> discovered =
                new LinkedHashMap<String, Class<? extends CellAI>>();

        // 1) Discover normal compiled student/sample AIs from the application
        // classpath (normally the VS Code bin/ folder).
        String[] classPathEntries = System.getProperty("java.class.path").split(File.pathSeparator);
        for (String entry : classPathEntries) {
            File dir = new File(entry);
            if (dir.isDirectory()) {
                discoverAIsInDirectory(dir, Simulation.class.getClassLoader(), discovered);
            }
        }

        // 2) Discover precompiled opponents stored in lib/. This lets us ship
        // LawnMowerAttackAI as a .class file only while keeping normal VS Code
        // Run behavior unchanged.
        File libDir = findPrecompiledOpponentDirectory();
        if (libDir.isDirectory()) {
            try {
                URLClassLoader loader = new URLClassLoader(
                        new URL[] { libDir.toURI().toURL() },
                        Simulation.class.getClassLoader());
                externalAILoaders.add(loader);
                discoverAIsInDirectory(libDir, loader, discovered);
            }
            catch (Exception e) {
                System.err.println("Could not load precompiled opponents from lib/: " + e);
            }
        }

        ArrayList<Class<? extends CellAI>> result =
                new ArrayList<Class<? extends CellAI>>(discovered.values());
        result.sort(Comparator.comparing(Class::getSimpleName));
        return result;
    }

    private File findPrecompiledOpponentDirectory() {
        // Normal case: VS Code/scripts are launched from the project root.
        File fromWorkingDirectory = new File(System.getProperty("user.dir"), "lib");
        if (fromWorkingDirectory.isDirectory()) {
            return fromWorkingDirectory;
        }

        // Fallback: locate lib/ next to the compiled bin/ directory. This keeps
        // the precompiled opponent discoverable even if the working directory
        // was changed by a launcher.
        try {
            File codeLocation = new File(
                    Simulation.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File parent = codeLocation.isDirectory() ? codeLocation.getParentFile() : null;
            if (parent != null) {
                File siblingLib = new File(parent, "lib");
                if (siblingLib.isDirectory()) {
                    return siblingLib;
                }
            }
        }
        catch (Exception ignored) {
            // Fall through to the normal path so the caller simply finds no
            // precompiled opponents instead of failing to start.
        }

        return fromWorkingDirectory;
    }

    @SuppressWarnings("unchecked")
    private void discoverAIsInDirectory(File dir, ClassLoader loader,
            Map<String, Class<? extends CellAI>> discovered) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            String fileName = file.getName();
            if (!file.isFile() || !fileName.endsWith(".class") || fileName.contains("$")) {
                continue;
            }

            String className = fileName.substring(0, fileName.length() - 6);
            if (discovered.containsKey(className)) {
                continue;
            }

            try {
                Class<?> clazz = Class.forName(className, true, loader);
                if (clazz != CellAI.class
                        && CellAI.class.isAssignableFrom(clazz)
                        && !Modifier.isAbstract(clazz.getModifiers())
                        && !clazz.isInterface()) {
                    discovered.put(className, (Class<? extends CellAI>) clazz);
                }
            }
            catch (Throwable ignored) {
                // Ignore unrelated or incompatible .class files.
            }
        }
    }

    private void assignColorsAndNames() {
        Color[] palette = {
                new Color(50, 100, 220),
                new Color(220, 70, 65),
                new Color(35, 155, 90),
                new Color(145, 85, 200),
                new Color(225, 145, 35),
                new Color(20, 155, 175),
                new Color(215, 75, 150),
                new Color(95, 105, 115)
        };

        Map<String, Integer> nameCounts = new HashMap<String, Integer>();
        for (CellAI ai : allAIs) {
            String raw = safeAIName(ai);
            nameCounts.put(raw, nameCounts.getOrDefault(raw, 0) + 1);
        }

        for (int i = 0; i < allAIs.size(); i++) {
            CellAI ai = allAIs.get(i);
            Color color;
            if (i < palette.length) {
                color = palette[i];
            }
            else {
                color = Color.getHSBColor((float) i / Math.max(1, allAIs.size()), 0.65f, 0.82f);
            }
            colorsByID.put(ai.getID(), color);

            String raw = safeAIName(ai);
            String display = nameCounts.getOrDefault(raw, 0) > 1
                    ? raw + " (#" + ai.getID() + ")"
                    : raw;
            displayNamesByID.put(ai.getID(), display);
        }
    }

    private String safeAIName(CellAI ai) {
        try {
            String name = ai.getAIName();
            if (name != null && !name.isBlank()) {
                return name.trim();
            }
        }
        catch (Throwable ignored) {
        }
        return ai.getClass().getSimpleName();
    }

    /** Custom board renderer with visible grid cells and action highlighting. */
    private class BoardPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        private transient MatchSnapshot snapshot;
        private boolean actionPhase;

        BoardPanel() {
            setBackground(Color.WHITE);
        }

        void setSnapshot(MatchSnapshot snapshot, boolean actionPhase) {
            this.snapshot = snapshot;
            this.actionPhase = actionPhase;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (snapshot == null || snapshot.grid == null) {
                g.setColor(new Color(110, 115, 125));
                g.setFont(g.getFont().deriveFont(Font.BOLD, 20f));
                String message = "Cell Wars is ready";
                int width = g.getFontMetrics().stringWidth(message);
                g.drawString(message, Math.max(20, (getWidth() - width) / 2), getHeight() / 2);
                g.dispose();
                return;
            }

            int rows = snapshot.grid.length;
            int cols = snapshot.grid[0].length;
            int boardSize = Math.min(getWidth(), getHeight());
            int offsetX = (getWidth() - boardSize) / 2;
            int offsetY = (getHeight() - boardSize) / 2;

            g.setColor(Color.WHITE);
            g.fillRect(offsetX, offsetY, boardSize, boardSize);

            for (int row = 0; row < rows; row++) {
                int y0 = offsetY + (row * boardSize) / rows;
                int y1 = offsetY + ((row + 1) * boardSize) / rows;
                for (int col = 0; col < cols; col++) {
                    int x0 = offsetX + (col * boardSize) / cols;
                    int x1 = offsetX + ((col + 1) * boardSize) / cols;
                    int id = snapshot.grid[row][col];

                    if (id >= 0) {
                        g.setColor(colorsByID.getOrDefault(id, Color.DARK_GRAY));
                        g.fillRect(x0 + 1, y0 + 1, Math.max(1, x1 - x0 - 1), Math.max(1, y1 - y0 - 1));
                    }
                }
            }

            double cellWidth = (double) boardSize / cols;
            double cellHeight = (double) boardSize / rows;
            if (Math.min(cellWidth, cellHeight) >= 5.0) {
                g.setColor(GRID_LINE);
                g.setStroke(new BasicStroke(1f));
                for (int row = 0; row <= rows; row++) {
                    int y = offsetY + (row * boardSize) / rows;
                    g.drawLine(offsetX, y, offsetX + boardSize, y);
                }
                for (int col = 0; col <= cols; col++) {
                    int x = offsetX + (col * boardSize) / cols;
                    g.drawLine(x, offsetY, x, offsetY + boardSize);
                }
            }

            if (actionPhase && snapshot.lastAction != null && snapshot.lastAction.location != null) {
                drawActionHighlight(g, snapshot.lastAction, rows, cols, boardSize, offsetX, offsetY);
            }

            g.setColor(new Color(90, 95, 105));
            g.drawRect(offsetX, offsetY, boardSize - 1, boardSize - 1);
            g.dispose();
        }

        private void drawActionHighlight(Graphics2D g, TurnAction action, int rows, int cols,
                                         int boardSize, int offsetX, int offsetY) {
            int row = action.location.getRow();
            int col = action.location.getCol();
            if (row < 0 || col < 0 || row >= rows || col >= cols) {
                return;
            }

            int x0 = offsetX + (col * boardSize) / cols;
            int x1 = offsetX + ((col + 1) * boardSize) / cols;
            int y0 = offsetY + (row * boardSize) / rows;
            int y1 = offsetY + ((row + 1) * boardSize) / rows;

            int width = Math.max(4, x1 - x0);
            int height = Math.max(4, y1 - y0);
            Color highlight = action.type == TurnAction.Type.SPAWN ? SPAWN_HIGHLIGHT : KILL_HIGHLIGHT;
            g.setColor(highlight);
            g.setStroke(new BasicStroke(Math.max(2f, Math.min(width, height) / 5f)));
            g.drawRect(x0 + 1, y0 + 1, Math.max(1, width - 3), Math.max(1, height - 3));

            if (action.type == TurnAction.Type.SPAWN) {
                int cx = (x0 + x1) / 2;
                int cy = (y0 + y1) / 2;
                int arm = Math.max(2, Math.min(width, height) / 3);
                g.drawLine(cx - arm, cy, cx + arm, cy);
                g.drawLine(cx, cy - arm, cx, cy + arm);
            }
            else if (action.type == TurnAction.Type.KILL) {
                int pad = Math.max(2, Math.min(width, height) / 5);
                g.drawLine(x0 + pad, y0 + pad, x1 - pad, y1 - pad);
                g.drawLine(x1 - pad, y0 + pad, x0 + pad, y1 - pad);
            }
        }
    }
}
