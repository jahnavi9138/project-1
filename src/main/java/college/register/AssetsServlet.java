package college.register;

import jakarta.servlet.http.*;
import java.io.*;
import java.util.Map;

final class AssetsServlet extends HttpServlet {
    private static final Map<String, String> FILES = Map.of(
        "/index.html", "text/html;charset=UTF-8",
        "/styles.css", "text/css;charset=UTF-8",
        "/app.js", "text/javascript;charset=UTF-8",
        "/fonts/dm-sans-latin-400-normal.woff2", "font/woff2",
        "/fonts/dm-sans-latin-600-normal.woff2", "font/woff2",
        "/fonts/dm-serif-text-latin-400-normal.woff2", "font/woff2"
    );

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String path = req.getServletPath();
        if (path.equals("/") || path.isEmpty()) path = "/index.html";
        if (!FILES.containsKey(path)) { res.setStatus(404); return; }
        res.setContentType(FILES.get(path));
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("Referrer-Policy", "same-origin");
        res.setHeader("Cache-Control", "no-cache");
        res.setHeader("Content-Security-Policy", "default-src 'self'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
        try (InputStream stream = getClass().getResourceAsStream("/web" + path)) {
            if (stream == null) { res.setStatus(404); return; }
            stream.transferTo(res.getOutputStream());
        }
    }
}
