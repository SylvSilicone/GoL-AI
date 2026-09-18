/** Description of the AI action phase of one turn. */
class TurnAction {

    enum Type {
        SPAWN,
        KILL,
        SKIPPED,
        FORFEIT
    }

    final int playerID;
    final String playerName;
    final Type type;
    final Location location;
    final String message;
    final int strikeCount;
    final boolean shouldRunLifeUpdate;

    TurnAction(int playerID, String playerName, Type type, Location location,
               String message, int strikeCount, boolean shouldRunLifeUpdate) {
        this.playerID = playerID;
        this.playerName = playerName;
        this.type = type;
        this.location = location;
        this.message = message;
        this.strikeCount = strikeCount;
        this.shouldRunLifeUpdate = shouldRunLifeUpdate;
    }
}
