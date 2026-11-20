package college.register;

/** An expected request error that can be shown to staff. */
final class Problem extends RuntimeException {
    final int status;

    Problem(int status, String message) { super(message); this.status = status; }

    static Problem invalid(String message) { return new Problem(400, message); }
    static Problem missing() { return new Problem(404, "This record could not be found."); }
    static Problem stale() { return new Problem(409, "Someone changed this record. Refresh it before saving again."); }
}
