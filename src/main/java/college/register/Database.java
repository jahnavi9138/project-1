package college.register;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import org.h2.jdbcx.JdbcConnectionPool;
import org.h2.tools.RunScript;

/** Owns the embedded JDBC pool and installs the database schema. */
final class Database implements AutoCloseable {
    private final JdbcConnectionPool pool;

    Database(Path directory) throws IOException, SQLException {
        Files.createDirectories(directory);
        Path file = directory.resolve("campus").toAbsolutePath().normalize();
        if (file.toString().contains(";")) throw new IOException("Choose a data folder without a semicolon in its path.");
        pool = JdbcConnectionPool.create("jdbc:h2:file:" + file + ";DB_CLOSE_ON_EXIT=FALSE", "sa", "");
        pool.setMaxConnections(16);
        try (Connection c = open()) {
            script(c, "schema.sql");
        }
    }

    Connection open() throws SQLException { return pool.getConnection(); }

    static void script(Connection c, String filename) throws SQLException, IOException {
        try (InputStream stream = Database.class.getResourceAsStream("/db/" + filename)) {
            if (stream == null) throw new FileNotFoundException(filename);
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                ResultSet last = RunScript.execute(c, reader);
                if (last != null) last.close();
            }
        }
    }

    @Override public void close() { pool.dispose(); }
}
