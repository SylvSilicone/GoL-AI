/** Result of safely asking an AI to choose a location. */
class AISelectionResult {

    enum Status {
        OK,
        NULL_LOCATION,
        TIMEOUT,
        EXCEPTION
    }

    final Status status;
    final Location location;
    final String message;

    private AISelectionResult(Status status, Location location, String message) {
        this.status = status;
        this.location = location;
        this.message = message;
    }

    static AISelectionResult ok(Location location) {
        return new AISelectionResult(Status.OK, location, "");
    }

    static AISelectionResult nullLocation() {
        return new AISelectionResult(Status.NULL_LOCATION, null, "AI returned null");
    }

    static AISelectionResult timeout() {
        return new AISelectionResult(Status.TIMEOUT, null, "AI exceeded the move time limit");
    }

    static AISelectionResult exception(Throwable throwable) {
        String detail = throwable == null ? "unknown error" : throwable.getClass().getSimpleName();
        if (throwable != null && throwable.getMessage() != null && !throwable.getMessage().isBlank()) {
            detail += ": " + throwable.getMessage();
        }
        return new AISelectionResult(Status.EXCEPTION, null, detail);
    }
}
