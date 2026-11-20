package college.register;

import com.google.gson.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import java.util.regex.*;
import java.util.logging.*;

final class ApiServlet extends HttpServlet {
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();
    private static final Logger LOG = Logger.getLogger(ApiServlet.class.getName());
    private static final Pattern STUDENT = Pattern.compile("/students/([1-9][0-9]{0,17})(?:/(marks)(?:/([1-9][0-9]{0,17}))?)?");
    private static final Pattern ATTENDANCE = Pattern.compile("/attendance/([1-9][0-9]{0,17})");
    private final Accounts accounts;
    private final StudentRepository students;
    private final AcademicRepository academics;
    private final Map<String, Attempts> failures = new HashMap<>();
    private record Attempts(long until, int count) { }

    ApiServlet(Database db, Accounts accounts) {
        this.accounts = accounts;
        students = new StudentRepository(db);
        academics = new AcademicRepository(db);
    }

    @Override protected void service(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setContentType("application/json;charset=UTF-8");
        res.setHeader("Cache-Control", "no-store");
        res.setHeader("X-Content-Type-Options", "nosniff");
        try {
            Object result = route(req, res);
            reply(res, res.getStatus(), result);
        }
        catch (Problem p) { reply(res, p.status, Map.of("error", p.getMessage())); }
        catch (JsonParseException p) { reply(res, 400, Map.of("error", "Send a valid JSON object.")); }
        catch (Exception e) {
            LOG.log(Level.SEVERE, "Request failed", e);
            reply(res, 500, Map.of("error", "The request could not be completed. Check the server terminal."));
        }
    }

    private static void reply(HttpServletResponse res, int status, Object data) throws IOException {
        res.setStatus(status);
        res.getWriter().write(JSON.toJson(data));
    }

    private Object route(HttpServletRequest req, HttpServletResponse res) throws SQLException, IOException {
        String method = req.getMethod();
        String path = Objects.toString(req.getPathInfo(), "/");
        if (path.equals("/session") && method.equals("GET")) return sessionData(req);
        if (!Set.of("GET", "POST", "PUT", "DELETE").contains(method)) throw new Problem(405, "Unsupported request method.");

        JsonObject body = new JsonObject();
        if (!method.equals("GET")) {
            HttpSession session = req.getSession(false);
            if (session == null || !Objects.equals(session.getAttribute("csrf"), req.getHeader("X-CSRF-Token")) || req.getHeader("X-CSRF-Token") == null)
                throw new Problem(403, "Your session has changed. Refresh the page and try again.");
            String type = Objects.toString(req.getContentType(), "").split(";", 2)[0].strip();
            if (!type.equalsIgnoreCase("application/json")) throw new Problem(415, "Send application/json.");
            byte[] bytes = req.getInputStream().readNBytes(16_385);
            if (bytes.length > 16_384) throw new Problem(413, "The request is too large.");
            JsonElement parsed = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) throw Problem.invalid("Send a valid JSON object.");
            body = parsed.getAsJsonObject();
        }
        if (method.equals("POST") && (path.equals("/setup") || path.equals("/login"))) {
            if (path.equals("/setup")) {
                if (!onHost(req)) throw new Problem(403, "Set the first password on the host laptop at localhost.");
                if (accounts.exists()) throw new Problem(409, "Staff access is already set up. Sign in.");
                accounts.setPassword(Input.raw(body, "password").toCharArray(), false);
            } else {
                String address = req.getRemoteAddr();
                throttle(address, false);
                if (!accounts.matches(Input.raw(body, "password").toCharArray())) {
                    throttle(address, true);
                    throw new Problem(401, "The staff password is incorrect.");
                }
                synchronized (failures) { failures.remove(address); }
            }
            req.getSession().invalidate();
            req.getSession(true).setAttribute("staff", true);
            return sessionData(req);
        }
        HttpSession session = req.getSession(false);
        if (session == null || !Boolean.TRUE.equals(session.getAttribute("staff"))) throw new Problem(401, "Sign in to continue.");
        if (path.equals("/logout") && method.equals("POST")) { session.invalidate(); return Map.of("saved", true); }
        if (path.equals("/demo") && method.equals("POST")) { students.demo(); return Map.of("saved", true); }
        if (path.equals("/students")) {
            if (method.equals("GET")) return students.list(param(req, "q", ""), param(req, "department", ""), semester(req),
                Input.integer(param(req, "page", "1"), "page", 1, Integer.MAX_VALUE));
            if (method.equals("POST")) {
                long id = students.save(null, StudentRepository.Draft.from(body), 0);
                res.setStatus(201);
                return Map.of("id", id);
            }
        }
        if (path.equals("/attendance") && method.equals("GET")) {
            return academics.roster(Input.date(param(req, "date", "")), param(req, "department", ""), semester(req));
        }
        Matcher attendance = ATTENDANCE.matcher(path);
        if (attendance.matches() && method.equals("PUT")) {
            academics.setAttendance(Long.parseLong(attendance.group(1)), body);
            return Map.of("saved", true);
        }
        Matcher student = STUDENT.matcher(path);
        if (student.matches()) {
            long id = Long.parseLong(student.group(1));
            if (student.group(2) == null) {
                if (method.equals("GET")) return academics.profile(id);
                if (method.equals("PUT")) return Map.of("id", students.save(id, StudentRepository.Draft.from(body), version(body)));
                if (method.equals("DELETE")) { students.delete(id, version(body)); return Map.of("saved", true); }
            } else {
                Long markId = student.group(3) == null ? null : Long.parseLong(student.group(3));
                if ((markId == null && method.equals("POST")) || (markId != null && method.equals("PUT"))) {
                    academics.saveMark(id, markId, body);
                    if (markId == null) res.setStatus(201);
                    return Map.of("saved", true);
                }
                if (markId != null && method.equals("DELETE")) {
                    academics.deleteMark(id, markId, version(body)); return Map.of("saved", true);
                }
            }
        }
        throw new Problem(404, "Unknown request.");
    }

    private Map<String, Object> sessionData(HttpServletRequest req) throws SQLException, IOException {
        HttpSession session = req.getSession(true);
        boolean staff = Boolean.TRUE.equals(session.getAttribute("staff"));
        synchronized (session) {
            if (session.getAttribute("csrf") == null) session.setAttribute("csrf", UUID.randomUUID().toString());
        }
        return Map.of("signedIn", staff, "setupRequired", !accounts.exists(), "onHost", onHost(req),
            "csrf", session.getAttribute("csrf"), "today", LocalDate.now().toString(),
            "departments", staff ? students.departments() : List.of());
    }

    private static boolean onHost(HttpServletRequest req) throws IOException {
        return InetAddress.getByName(req.getRemoteAddr()).isLoopbackAddress()
            && Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(req.getServerName().toLowerCase(Locale.ROOT));
    }

    private void throttle(String address, boolean failed) {
        long now = System.currentTimeMillis();
        synchronized (failures) {
            failures.entrySet().removeIf(e -> e.getValue().until() <= now);
            Attempts a = failures.getOrDefault(address, new Attempts(now + 60_000L, 0));
            if (a.count() >= 8) throw new Problem(429, "Too many attempts. Try again in one minute.");
            if (failed) failures.put(address, new Attempts(a.until(), a.count() + 1));
        }
    }

    private static int version(JsonObject body) { return Input.integer(body, "version", 1, Integer.MAX_VALUE); }
    private static int semester(HttpServletRequest req) { return Input.integer(param(req, "semester", "0"), "semester", 0, 8); }
    private static String param(HttpServletRequest req, String name, String fallback) {
        return Objects.toString(req.getParameter(name), fallback);
    }
}
