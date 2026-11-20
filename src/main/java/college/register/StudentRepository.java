package college.register;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.sql.*;
import java.util.*;

/** Student directory queries. All user input is bound to JDBC parameters. */
final class StudentRepository {
    private final Database db;
    static final int PAGE_SIZE = 10;

    StudentRepository(Database db) { this.db = db; }

    record Department(String code, String name) { }
    record Student(long id, String roll, String name, String department, int semester, String email, String status, int version) { }
    record Draft(String roll, String name, String department, int semester, String email, String status) {
        static Draft from(JsonObject json) {
            String roll = Input.text(json, "roll", "a roll number", 24, true).toUpperCase(Locale.ROOT);
            if (!roll.matches("[A-Z0-9][A-Z0-9./_-]{0,23}")) throw Problem.invalid("Use letters, numbers, dot, slash, dash or underscore for the roll number.");
            String email = Input.text(json, "email", "email", 160, false);
            if (!email.isEmpty() && !email.matches("[^\\s@<>]+@[^\\s@<>]+\\.[^\\s@<>]+")) throw Problem.invalid("Enter a valid email address.");
            return new Draft(roll, Input.text(json, "name", "a full name", 90, true),
                Input.text(json, "department", "a department", 12, true), Input.integer(json, "semester", 1, 8), email,
                Input.choice(Input.raw(json, "status"), "student status", "Active", "Graduated", "Withdrawn"));
        }
        void bind(PreparedStatement p) throws SQLException {
            p.setString(1, roll); p.setString(2, name); p.setString(3, department);
            p.setInt(4, semester); p.setString(5, email); p.setString(6, status);
        }
    }

    List<Department> departments() throws SQLException {
        List<Department> rows = new ArrayList<>();
        try (Connection c = db.open(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT code, name FROM departments ORDER BY code")) {
            while (rs.next()) rows.add(new Department(rs.getString(1), rs.getString(2)));
        }
        return rows;
    }

    static Student student(ResultSet r) throws SQLException {
        return new Student(r.getLong("id"), r.getString("roll_no"), r.getString("full_name"), r.getString("department_code"),
            r.getInt("semester"), r.getString("email"), r.getString("status"), r.getInt("version"));
    }

    static Student find(Connection c, long id) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT * FROM students WHERE id=?")) {
            p.setLong(1, id);
            try (ResultSet r = p.executeQuery()) { if (r.next()) return student(r); }
        }
        throw Problem.missing();
    }

    Map<String, Object> list(String query, String department, int semester, int requestedPage) throws SQLException {
        if (query.length() > 100) throw Problem.invalid("Use up to 100 characters for a search.");
        String where = " FROM students WHERE (LOWER(full_name) LIKE ? ESCAPE '!' OR LOWER(roll_no) LIKE ? ESCAPE '!')"
                     + " AND (?='' OR department_code=?) AND (?=0 OR semester=?)";
        String pattern = "%" + query.strip().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        try (Connection c = db.open()) {
            c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            c.setAutoCommit(false);
            try (PreparedStatement count = c.prepareStatement("SELECT COUNT(*)" + where);
                 PreparedStatement page = c.prepareStatement("SELECT *" + where + " ORDER BY LOWER(full_name), id LIMIT ? OFFSET ?");
                 Statement stats = c.createStatement()) {
                for (PreparedStatement p : List.of(count, page)) {
                    p.setString(1, pattern); p.setString(2, pattern);
                    p.setString(3, department); p.setString(4, department);
                    p.setInt(5, semester); p.setInt(6, semester);
                }
                long matches;
                try (ResultSet r = count.executeQuery()) { r.next(); matches = r.getLong(1); }
                long current = Math.min(requestedPage, Math.max(1, (matches + PAGE_SIZE - 1) / PAGE_SIZE));
                page.setInt(7, PAGE_SIZE);
                page.setLong(8, (current - 1) * PAGE_SIZE);
                List<Student> rows = new ArrayList<>();
                try (ResultSet r = page.executeQuery()) { while (r.next()) rows.add(student(r)); }
                try (ResultSet r = stats.executeQuery("SELECT COUNT(*), COUNT(CASE WHEN status='Active' THEN 1 END), COUNT(DISTINCT department_code) FROM students")) {
                    r.next();
                    Map<String, Long> summary = Map.of("total", r.getLong(1), "active", r.getLong(2), "departments", r.getLong(3));
                    c.commit();
                    return Map.of("items", rows, "matches", matches, "page", current, "pageSize", PAGE_SIZE, "summary", summary);
                }
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        }
    }

    long save(Long id, Draft draft, int version) throws SQLException {
        String sql = id == null
            ? "INSERT INTO students (roll_no, full_name, department_code, semester, email, status) VALUES (?,?,?,?,?,?)"
            : "UPDATE students SET roll_no=?,full_name=?,department_code=?,semester=?,email=?,status=?,version=version+1 WHERE id=? AND version=?";
        try (Connection c = db.open(); PreparedStatement p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            draft.bind(p);
            if (id != null) { p.setLong(7, id); p.setInt(8, version); }
            if (p.executeUpdate() == 0) { find(c, id); throw Problem.stale(); }
            if (id != null) return id;
            try (ResultSet r = p.getGeneratedKeys()) { r.next(); return r.getLong(1); }
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) throw new Problem(409, "That roll number already belongs to a student.");
            if ("23506".equals(e.getSQLState())) throw Problem.invalid("Choose a listed department.");
            throw e;
        }
    }

    void delete(long id, int version) throws SQLException {
        try (Connection c = db.open(); PreparedStatement p = c.prepareStatement("DELETE FROM students WHERE id=? AND version=?")) {
            p.setLong(1, id); p.setInt(2, version);
            if (p.executeUpdate() == 0) { find(c, id); throw Problem.stale(); }
        }
    }

    void demo() throws SQLException, IOException {
        try (Connection c = db.open()) {
            c.setAutoCommit(false);
            try (Statement s = c.createStatement()) {
                // Serialize demo loads; the entire import rolls back if any row fails.
                try (ResultSet lock = s.executeQuery("SELECT id FROM staff_account WHERE id=1 FOR UPDATE")) { lock.next(); }
                try (ResultSet r = s.executeQuery("SELECT COUNT(*) FROM students")) {
                    r.next();
                    if (r.getLong(1) != 0) throw new Problem(409, "Demo records can only be loaded into an empty directory.");
                }
                Database.script(c, "demo.sql");
                c.commit();
            } catch (SQLException | IOException | RuntimeException e) { c.rollback(); throw e; }
        }
    }
}
