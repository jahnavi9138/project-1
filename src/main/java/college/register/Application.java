package college.register;

import java.net.*;
import java.nio.file.*;
import java.util.*;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.http.Rfc6265CookieProcessor;

public final class Application {
    public static void main(String[] args) throws Exception {
        Path directory = Path.of(System.getenv().getOrDefault("CAMPUS_DATA_DIR", "data")).toAbsolutePath().normalize();
        int port = Integer.parseInt(System.getenv().getOrDefault("CAMPUS_PORT", System.getenv().getOrDefault("PORT", "8080")));
        if (port < 1 || port > 65535) throw new IllegalArgumentException("CAMPUS_PORT must be 1 to 65535.");
        try (Database db = new Database(directory)) {
            Accounts accounts = new Accounts(db);
            // A public deployment must not expose first-run setup to visitors.
            // Supply this secret through the hosting provider, never in source control.
            boolean publicMode = Boolean.parseBoolean(System.getenv().getOrDefault("CAMPUS_PUBLIC_MODE", "false"));
            if (publicMode) {
                String initialPassword = System.getenv("CAMPUS_ADMIN_PASSWORD");
                if (initialPassword == null || initialPassword.length() < 10 || initialPassword.length() > 128) {
                    throw new IllegalStateException("Public deployment requires CAMPUS_ADMIN_PASSWORD (10-128 characters).");
                }
                if (!accounts.exists()) accounts.setPassword(initialPassword.toCharArray(), false);
            }
            if (Arrays.equals(args, new String[]{"--reset-password"})) {
                accounts.resetAtConsole();
                return;
            }
            if (args.length != 0) throw new IllegalArgumentException("Supported option: --reset-password");
            Tomcat tomcat = new Tomcat();
            Path temp = Files.createTempDirectory("campus-register-");
            tomcat.setBaseDir(temp.toString());
            tomcat.setPort(port);
            tomcat.getConnector().setProperty("address", "0.0.0.0");
            tomcat.getConnector().setThrowOnFailure(true);
            tomcat.getConnector().setProperty("server", "CampusRegister");
            tomcat.getHost().setAutoDeploy(false);
            Context ctx = tomcat.addContext("", temp.toString());
            ctx.setParentClassLoader(Application.class.getClassLoader());
            ctx.setUseHttpOnly(true);
            ctx.setSessionTimeout(60);
            ctx.setSessionCookieName("CAMPUS_SESSION");
            if (publicMode) ctx.setSessionCookiePath("/");
            Rfc6265CookieProcessor cookies = new Rfc6265CookieProcessor();
            cookies.setSameSiteCookies("strict");
            ctx.setCookieProcessor(cookies);
            Tomcat.addServlet(ctx, "assets", new AssetsServlet());
            ctx.addServletMappingDecoded("/", "assets");
            Tomcat.addServlet(ctx, "api", new ApiServlet(db, accounts));
            ctx.addServletMappingDecoded("/api/*", "api");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { tomcat.stop(); tomcat.destroy(); } catch (Exception ignored) { }
                db.close();
            }));
            tomcat.start();
            if (!tomcat.getConnector().getState().isAvailable() || !ctx.getState().isAvailable()) {
                throw new IllegalStateException("Startup failed. Check that port " + port + " is free.");
            }
            System.out.println("\nCampus Register is ready.");
            System.out.println("On this computer: http://localhost:" + port);
            for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!n.isUp() || n.isLoopback()) continue;
                for (InetAddress a : Collections.list(n.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
                        System.out.println("On the same Wi-Fi: http://" + a.getHostAddress() + ":" + port);
                    }
                }
            }
            System.out.println("Database: " + directory.resolve("campus.mv.db"));
            System.out.println("Leave this window open. Press Ctrl+C to stop.\n");
            tomcat.getServer().await();
        }
    }

    private Application() { }
}
