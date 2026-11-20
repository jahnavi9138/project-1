package college.register;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RegisterTest {
    @TempDir Path files;
    private Database db;
    private StudentRepository students;
    private AcademicRepository academics;
    private final StudentRepository.Draft draft = new StudentRepository.Draft("CSE-17","Test Student","CSE",3,"","Active");

    @BeforeEach void open() throws Exception {
        db = new Database(files);
        students = new StudentRepository(db);
        academics = new AcademicRepository(db);
    }
    @AfterEach void close() { db.close(); }

    @Test void duplicateRollAndStaleEditsAreRejected() throws Exception {
        long id = students.save(null, draft, 0);
        assertEquals(409, assertThrows(Problem.class, () -> students.save(null, draft, 0)).status);
        students.save(id, draft, 1);
        assertEquals(409, assertThrows(Problem.class, () -> students.save(id, draft, 1)).status);
        assertEquals(409, assertThrows(Problem.class, () -> students.delete(id, 1)).status);
        try (Connection c = db.open()) { assertEquals(2, StudentRepository.find(c, id).version()); }
    }

    @Test void attendanceDistinguishesUnmarkedAndAbsentAndRejectsStaleChanges() throws Exception {
        long id = students.save(null, draft, 0);
        JsonObject day = new JsonObject();
        day.addProperty("date", LocalDate.now().toString());
        day.addProperty("previous", ""); day.addProperty("state", "Present");
        academics.setAttendance(id, day);
        academics.setAttendance(id, day); // A retry of the same request is safe.
        day.addProperty("state", "Absent");
        assertEquals(409, assertThrows(Problem.class, () -> academics.setAttendance(id, day)).status);
        day.addProperty("previous", "Present");
        academics.setAttendance(id, day);
        assertEquals("Absent", academics.roster(LocalDate.now(), "CSE", 3).get(0).state());
        day.addProperty("previous", "Absent"); day.addProperty("state", "");
        academics.setAttendance(id, day);
        assertEquals(0, academics.profile(id).get("markedDays"));
    }

    @Test void marksAndAttendanceSurviveReopenAndCascadeOnDelete() throws Exception {
        long id = students.save(null, draft, 0);
        JsonObject mark = new JsonObject();
        mark.addProperty("subject", "Java"); mark.addProperty("assessment", "Lab");
        mark.addProperty("score", 0); mark.addProperty("maximum", 30);
        academics.saveMark(id, null, mark);
        assertEquals(409, assertThrows(Problem.class, () -> academics.saveMark(id, null, mark)).status);
        JsonObject day = new JsonObject();
        day.addProperty("date", LocalDate.now().toString()); day.addProperty("state", "Present"); day.addProperty("previous", "");
        academics.setAttendance(id, day);
        db.close();
        open();
        assertEquals(1, academics.profile(id).get("markedDays"));
        assertEquals(1, ((java.util.List<?>)academics.profile(id).get("marks")).size());
        students.delete(id, 1);
        try (Connection c = db.open(); var st = c.createStatement()) {
            for (String table : java.util.List.of("students","marks","attendance")) {
                try (var r = st.executeQuery("SELECT COUNT(*) FROM " + table)) { r.next(); assertEquals(0, r.getInt(1)); }
            }
        }
    }
}
