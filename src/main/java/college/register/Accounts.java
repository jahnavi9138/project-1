package college.register;

import java.io.Console;
import java.security.*;
import java.sql.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** One shared staff account for a college project team. No bundled password. */
final class Accounts {
    private final Database db;
    private static final SecureRandom RANDOM = new SecureRandom();

    Accounts(Database db) { this.db = db; }

    boolean exists() throws SQLException {
        try (Connection c = db.open(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT 1 FROM staff_account WHERE id=1")) {
            return rs.next();
        }
    }

    void setPassword(char[] password, boolean reset) throws SQLException {
        if (password.length < 10 || password.length > 128) throw Problem.invalid("Use 10 to 128 characters for the staff password.");
        byte[] salt = new byte[24];
        RANDOM.nextBytes(salt);
        String sql = reset ? "MERGE INTO staff_account (id, salt, password_hash) KEY(id) VALUES (1,?,?)"
                           : "INSERT INTO staff_account (id, salt, password_hash) VALUES (1,?,?)";
        try (Connection c = db.open(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setBytes(1, salt);
            p.setBytes(2, hash(password, salt));
            p.executeUpdate();
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) throw new Problem(409, "The staff password has already been set. Sign in.");
            throw e;
        } finally { Arrays.fill(password, '\0'); }
    }

    boolean matches(char[] password) throws SQLException {
        try (Connection c = db.open(); PreparedStatement p = c.prepareStatement("SELECT salt, password_hash FROM staff_account WHERE id=1");
             ResultSet rs = p.executeQuery()) {
            return password.length <= 128 && rs.next() && MessageDigest.isEqual(rs.getBytes(2), hash(password, rs.getBytes(1)));
        } finally { Arrays.fill(password, '\0'); }
    }

    private static byte[] hash(char[] password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, 600_000, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
        finally { spec.clearPassword(); }
    }

    void resetAtConsole() throws SQLException {
        Console console = System.console();
        if (console == null) throw new IllegalStateException("Run --reset-password in an interactive terminal.");
        char[] p = console.readPassword("New staff password (10–128 characters): ");
        char[] again = console.readPassword("Confirm new password: ");
        try {
            if (p == null || again == null || !Arrays.equals(p, again)) throw new IllegalArgumentException("Passwords did not match.");
            setPassword(p, true);
            console.printf("Staff password saved.%n");
        } finally {
            if (p != null) Arrays.fill(p, '\0');
            if (again != null) Arrays.fill(again, '\0');
        }
    }
}
