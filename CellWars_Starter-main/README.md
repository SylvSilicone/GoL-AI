# Cell Wars 2.0 - Student Starter

Cell Wars is a multiplayer strategy game based on Conway's Game of Life. Your job is **not** to rewrite the simulator. Your job is to build an AI that makes one smart decision each turn and competes against other AIs.

## Quick Start in VS Code

1. Open the **entire `CellWars_2_0_Starter` folder** in VS Code.
2. Make sure the **Extension Pack for Java** is installed.
3. Open `src/Simulation.java`.
4. Click **Run** above `main`, or use the Run button in VS Code.
5. In the Cell Wars window, choose which AIs you want to include and click **Start Tournament**.

Use **Java 17 or newer** for this project.

Windows users can also run:

```powershell
.\scripts\run.ps1
```

macOS/Linux users can run:

```bash
./scripts/run.sh
```

> The graphical simulator is intended for local VS Code. The Java model can compile elsewhere, but Swing graphics are not the normal Codespaces workflow.

---

## The Game

The board is a square grid of integers:

- `-1` = dead cell
- `0` or greater = living cell owned by the AI with that ID

Every AI gets a unique ID from `getID()`.

On your turn, your AI returns **one `Location`**.

- If that location is dead, your AI spawns one of its own cells there.
- If that location is alive, that cell is killed. You may kill an opponent's cell or your own.
- After the AI action, the simulator runs **one multiplayer Game of Life update**.

### Multiplayer Game of Life Rules

For each cell, the eight surrounding locations are its possible neighbors. The board does **not** wrap around.

- A living cell dies with fewer than 2 living neighbors.
- A living cell dies with more than 3 living neighbors.
- A living cell with 2 or 3 living neighbors survives, but becomes the type of the most common neighboring AI.
- A dead cell with exactly 3 living neighbors becomes alive as the type of the most common neighboring AI.
- If there is a tie for most common neighbor, the simulator uses a seeded random tie-break.

Each AI begins with **100 randomly placed cells**.

---

## Your File: `MyAI.java`

This is the file you should change.

```java
public class MyAI extends CellAI {

    @Override
    public String getAIName() {
        return "Your Code Name";
    }

    @Override
    public Location select(Grid grid) {
        // Your strategy here
    }
}
```

Your final AI should be fundamentally different from the sample strategies.

### Helpful Methods

```java
getID()
grid.getRows()
grid.getCols()
grid.getCell(row, col)
GridFunctions.getNeighbors(row, col, grid)
GridFunctions.mostCommonNeighbor(row, col, grid)
randomInt(bound)
randomDouble()
```

`randomInt()` and `randomDouble()` are preferred over `Math.random()` because they make your AI's random decisions reproducible when the same match seed is used.

---

## Sample AIs

The project includes several opponents:

- `RandomAI` - a simple baseline opponent with visible source code.
- `SpawnerAI` - another simple sample with visible source code.
- `LawnMowerAttackAI` - a precompiled challenge opponent. You can test against it, but its implementation is intentionally hidden.
- `MyAI` - your starter file.

The simulator automatically discovers concrete subclasses of `CellAI` after they compile. You do **not** need to register your class in `Simulation.java`.

Use the checkboxes in the simulator to choose which AIs should compete. For example, select only `RandomAI` and your AI for a head-to-head test.

---

## Understanding the New Interface

### AI Action vs. Conway Update

Each turn is shown in two phases:

1. **AI ACTION** - the selected location is highlighted.
   - Green `+` = spawn
   - Red `X` = kill
2. **CONWAY UPDATE** - the Game of Life rules are applied to the entire board.

Slow the turn-delay slider when you want to study exactly what happened.

### Pause and Step

- **Pause** stops before the next AI turn.
- **Step** runs exactly one complete turn while paused.
- **Resume** continues automatic play.

This is useful when debugging your AI's decisions.

---

## Live Scoring

The **LIVE CELL SCORE** shows, for every AI in the current match:

- number of living cells
- percentage of all living cells
- change since the previous completed turn (`Delta`)
- current leader and margin

This tells you who is currently controlling more of the board.

A match normally ends when only one AI remains alive.

If the match reaches the turn limit:

- the AI with the most living cells wins **by decision**
- equal top cell counts result in a **draw**

Tournament standings use:

- **W** = match wins
- **L** = match losses
- **D** = match draws
- **Titles** = tournament championships

---

## Conceding a Stalemate

The simulator creates a **Concede** button for every active AI.

Use it when a match has clearly become a stalemate and you do not want to wait for the turn limit. A confirmation box appears before the concession is accepted.

Concession is an administrative match result; it does not turn the conceding player's cells into another player's cells.

---

## Fairness and Reproducibility

The simulator displays a **Base Seed** and a **Match Seed**.

Running the same tournament with the same base seed reproduces simulator-controlled randomness, including:

- initial cell placement
- Game of Life ownership tie-breaks
- bracket shuffling and persistent-draw tie-breaks

The simulator also rotates who moves first in repeated matches.

If your AI uses randomness, use `randomInt()` / `randomDouble()` for reproducible testing.

---

## Tournament Options

Before starting, you can change:

- **Base Seed** - repeat a tournament exactly
- **Tournament Runs** - useful for repeated head-to-head testing
- **AIs per Match** - default is 2, but multiplayer matches are supported
- **Turn Delay** - slow for analysis, fast for tournament testing

If a bracket match ends in a draw, the simulator automatically replays it with a new deterministic seed. If repeated draws persist, a seeded bracket tie-break chooses who advances, but that tie-break does **not** count as a match win.

---

## Broken AI Protection

Tournament day should not be ruined by one bug.

An AI receives a warning if it:

- returns `null`
- returns a location outside the grid
- throws an exception
- takes longer than the allowed move time

The turn is skipped and play continues. After **3 warnings**, that AI forfeits the match.

This protection is not a substitute for testing your AI carefully.

---

## Project Files

```text
CellWars_2_0_Starter/
|-- src/
|   |-- CellAI.java
|   |-- Grid.java
|   |-- GridFunctions.java
|   |-- Location.java
|   |-- MyAI.java              <-- YOUR MAIN FILE
|   |-- RandomAI.java
|   |-- SpawnerAI.java
|   |-- Simulation.java
|   |-- MatchEngine.java
|   `-- supporting simulator classes
|-- lib/
|   `-- LawnMowerAttackAI.class  <-- precompiled opponent
|-- .vscode/
|-- scripts/
|-- .gitignore
`-- README.md
```

Unless your instructor tells you otherwise, focus your work on **your `CellAI` subclass** rather than modifying the simulator.
