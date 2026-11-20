package college.register;

import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

final class AcademicRepository {
    private final Database db;
    AcademicRepository(Database db) { this.db = db; }

    record Mark(long id, String subject, String assessment, BigDecimal score, BigDecimal maximum, int version) { }
    record Day(String date, String state) { }
    record Roll(long id, String roll, String name, String department, int semester, String state) { }

    Map<String, Object> profile(long id) throws SQLException {
        try (Connection c = db.open()) {
            StudentRepository.Student student = StudentRepository.find(c, id);
            List<Mark> marks = new ArrayList<>();
            try (PreparedStatement p = c.prepareStatement("SELECT * FROM marks WHERE student_id=? ORDER BY subject, assessment, id")) {
                p.setLong(1, id);
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) marks.add(new Mark(r.getLong("id"), r.getString("subject"), r.getString("assessment"),
                        r.getBigDecimal("score"), r.getBigDecimal("maximum"), r.getInt("version")));
                }
            }
            List<Day> history = new ArrayList<>();
            try (PreparedStatement p = c.prepareStatement("SELECT class_date,state FROM attendance WHERE student_id=? ORDER BY class_date DESC LIMIT 5")) {
                p.setLong(1, id);
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) history.add(new Day(r.getString(1), r.getString(2)));
                }
            }
            try (PreparedStatement p = c.prepareStatement("SELECT COUNT(*), COUNT(CASE WHEN state='Present' THEN 1 END) FROM attendance WHERE student_id=?")) {
                p.setLong(1, id);
                try (ResultSet r = p.executeQuery()) {
                    r.next();
                    return Map.of("student", student, "marks", marks, "history", history, "markedDays", r.getInt(1), "presentDays", r.getInt(2));
                }
            }
        }
    }

    void saveMark(long studentId, Long markId, JsonObject json) throws SQLException {
        String subject = Input.text(json, "subject", "a subject", 80, true);
        String assessment = Input.text(json, "assessment", "an assessment name", 60, true);
        BigDecimal score = Input.number(json, "score", "marks earned");
        BigDecimal maximum = Input.number(json, "maximum", "maximum marks");
        if (maximum.signum() == 0 || score.compareTo(maximum) > 0) throw Problem.invalid("Maximum marks must be positive and at least the marks earned.");
        String sql = markId == null
            ? "INSERT INTO marks (subject,assessment,score,maximum,student_id) VALUES (?,?,?,?,?)"
            : "UPDATE marks SET subject=?,assessment=?,score=?,maximum=?,version=version+1 WHERE student_id=? AND id=? AND version=?";
        try (Connection c = db.open(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, subject); p.setString(2, assessment); p.setBigDecimal(3, score);
            p.setBigDecimal(4, maximum); p.setLong(5, studentId);
            if (markId != null) { p.setLong(6, markId); p.setInt(7, Input.integer(json, "version", 1, Integer.MAX_VALUE)); }
            if (p.executeUpdate() == 0) throw Problem.stale();
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) throw new Problem(409, "This student already has marks for that subject and assessment.");
            if ("23506".equals(e.getSQLState())) throw Problem.missing();
            throw e;
        }
    }

    void deleteMark(long studentId, long markId, int version) throws SQLException {
        try (Connection c = db.open(); PreparedStatement p = c.prepareStatement("DELETE FROM marks WHERE student_id=? AND id=? AND version=?")) {
            p.setLong(1, studentId); p.setLong(2, markId); p.setInt(3, version);
            if (p.executeUpdate() == 0) throw Problem.stale();
        }
    }

    List<Roll> roster(LocalDate date, String department, int semester) throws SQLException {
        String sql = """
            SELECT s.id, s.roll_no, s.full_name, s.department_code, s.semester, COALESCE(a.state,'') AS state
            FROM students s LEFT JOIN attendance a ON a.student_id=s.id AND a.class_date=?
            WHERE (s.status='Active' OR a.state IS NOT NULL)
              AND (?='' OR s.department_code=?) AND (?=0 OR s.semester=?)
            ORDER BY LOWER(s.full_name), s.id
            """;
        try (Connection c = db.open(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, date); p.setString(2, department); p.setString(3, department);
            p.setInt(4, semester); p.setInt(5, semester);
            List<Roll> rows = new ArrayList<>();
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) rows.add(new Roll(r.getLong(1), r.getString(2), r.getString(3), r.getString(4), r.getInt(5), r.getString(6)));
            }
            return rows;
        }
    }

    void setAttendance(long studentId, JsonObject json) throws SQLException {
        LocalDate date = Input.date(Input.raw(json, "date"));
        String state = Input.choice(Input.raw(json, "state"), "attendance", "", "Present", "Absent");
        String previous = Input.choice(Input.raw(json, "previous"), "previous attendance", "", "Present", "Absent");
        try (Connection c = db.open()) {
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            c.setAutoCommit(false);
            try {
                String status;
                try (PreparedStatement p = c.prepareStatement("SELECT status FROM students WHERE id=? FOR UPDATE")) {
                    p.setLong(1, studentId);
                    try (ResultSet r = p.executeQuery()) { if (!r.next()) throw Problem.missing(); status = r.getString(1); }
                }
                String current = "";
                try (PreparedStatement p = c.prepareStatement("SELECT state FROM attendance WHERE student_id=? AND class_date=?")) {
                    p.setLong(1, studentId); p.setObject(2, date);
                    try (ResultSet r = p.executeQuery()) { if (r.next()) current = r.getString(1); }
                }
                if (!current.equals(previous) && !current.equals(state)) throw Problem.stale();
                if (!state.equals(current)) {
                    if (current.isEmpty() && !status.equals("Active")) throw new Problem(409, "Only active students can receive a new attendance entry.");
                    String sql = state.isEmpty() ? "DELETE FROM attendance WHERE student_id=? AND class_date=?"
                        : "MERGE INTO attendance (student_id,class_date,state) KEY(student_id,class_date) VALUES (?,?,?)";
                    try (PreparedStatement p = c.prepareStatement(sql)) {
                        p.setLong(1, studentId); p.setObject(2, date);
                        if (!state.isEmpty()) p.setString(3, state);
                        p.executeUpdate();
                    }
                }
                c.commit();
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        }
    }
}
