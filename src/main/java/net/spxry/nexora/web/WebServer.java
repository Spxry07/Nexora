package net.spxry.nexora.web;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.spxry.nexora.Nexora;
import net.spxry.nexora.config.MessageManager;
import net.spxry.nexora.edit.Schema;
import net.spxry.nexora.object.HoloLine;
import net.spxry.nexora.object.Hologram;
import net.spxry.nexora.object.NexoraObject;
import net.spxry.nexora.object.Npc;
import net.spxry.nexora.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.regex.Pattern;
import static java.net.HttpURLConnection.HTTP_BAD_METHOD;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static java.net.HttpURLConnection.HTTP_ENTITY_TOO_LARGE;
import static java.net.HttpURLConnection.HTTP_GATEWAY_TIMEOUT;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;

public final class WebServer {
    private static final String ROUTE_ROOT = "/";
    private static final String ROUTE_API = "/api/";
    private static final String PAGE_RESOURCE = "web/index.html";
    private static final String THREAD_NAME = "nexora-web";
    private static final String BEARER = "Bearer ";
    private static final String LINK_SUFFIX = "/#";
    private static final String GET = "GET";
    private static final String POST = "POST";
    private static final String TYPE_HTML = "text/html; charset=utf-8";
    private static final String TYPE_JSON = "application/json; charset=utf-8";
    private static final String CSP_DEFAULT = "default-src 'none'";
    private static final String CSP_TAIL = "base-uri 'none'; form-action 'none'; frame-ancestors 'none'";
    private static final String CSP_SEPARATOR = "; ";
    private static final String CSP_INLINE = "'unsafe-inline'";
    private static final String CSP_SELF = "'self'";
    private static final String CSP_DATA = "data:";
    private static final String CSP_NONE = " 'none'";
    private static final String ASSET_PATH = "web.assets.";
    private static final String ASSET_FONT_HOST = "web.assets.font-files-host";
    private static final String A_TEXTURE = "texture";
    private static final String A_SKIN = "skin";
    private static final String A_HEAD = "head";
    private static final String A_BODY = "body";
    private static final String A_VIEWER = "viewer";
    private static final String A_FONT = "font";
    private static final List<String> ASSET_KEYS = List.of(A_TEXTURE, A_SKIN, A_HEAD, A_BODY, A_VIEWER, A_FONT);
    private static final Set<String> ASSET_SCHEMES = Set.of("http", "https");
    private static final Pattern URL_PLACEHOLDER = Pattern.compile("\\{[^}]*}");
    private static final String URL_PLACEHOLDER_VALUE = "x";
    private static final String SCHEME_SEPARATOR = "://";
    private static final int TOKEN_BYTES = 24;
    private static final long MS_PER_MINUTE = 60_000L;
    public static final int MIN_PORT = 1024;
    public static final int MAX_PORT = 65535;
    private static final int DEFAULT_PORT = 0;
    private static final int DEFAULT_RANDOM_MIN = 20000;
    private static final int DEFAULT_RANDOM_MAX = 40000;
    private static final int DEFAULT_RANDOM_ATTEMPTS = 20;
    private static final int DEFAULT_LOOKUP_TIMEOUT_SECONDS = 5;
    private static final String DEFAULT_LOOKUP_URL = "https://api.ipify.org";
    private static final String PORT_FILE = "web-port.txt";
    private static final Set<String> UNSPECIFIED_HOSTS = Set.of("0.0.0.0", "::", "[::]");
    private static final Pattern ADDRESS = Pattern.compile("(?=.*[.:])[0-9a-fA-F:.]{2,45}");
    private static final int DEFAULT_SESSION_MINUTES = 60;
    private static final int DEFAULT_BODY_BYTES = 262144;
    private static final int DEFAULT_TIMEOUT_SECONDS = 10;
    private static final int DEFAULT_MAX_LINES = 32;
    private static final int DEFAULT_MAX_WAYPOINTS = 64;
    private static final int DEFAULT_TERRAIN_RADIUS = 48;
    private static final double DEFAULT_MOVE_DISTANCE = 64.0;
    private static final double MAX_COORD = 3.0E7;
    private static final int CHUNK_SHIFT = 4;
    private static final int NO_COLOR = -1;
    private static final int NO_HEIGHT = Short.MIN_VALUE;
    private static final int POINT_SIZE = 3;
    private static final int CLAMP_FLOOR = 1;
    private static final String DEFAULT_BIND = "0.0.0.0";
    private static final String DEFAULT_HOST = "localhost";
    private static final String HTTP_SCHEME = "http://";
    private static final String PORT_MARK = ":";
    private static final String IPV6_MARK = ":";
    private static final String IPV6_OPEN = "[";
    private static final String IPV6_CLOSE = "]";
    private static final String K_SLOT = "slot";
    private static final String K_MATERIAL = "material";
    private static final String DEFAULT_NPC_TYPE = "MANNEQUIN";
    private static final String DEFAULT_WEB_PERMISSION = "nexora.web";
    private static final String PROP_ENTITY_TYPE = "entity-type";
    private static final String K_KIND = "kind";
    private static final String K_ID = "id";
    private static final String K_PROPS = "props";
    private static final String K_LINES = "lines";
    private static final String K_REF = "ref";
    private static final String K_ACTION = "action";
    private static final String K_ENTITY = "entityType";
    private static final String K_OK = "ok";
    private static final String K_WORLD = "world";
    private static final String K_X = "x";
    private static final String K_Y = "y";
    private static final String K_Z = "z";
    private static final String K_YAW = "yaw";
    private static final String K_RADIUS = "radius";
    private static final String K_POINTS = "points";
    private static final String K_WAYPOINTS = "waypoints";
    private static final String K_ERROR = "error";
    private static final String K_LABEL = "label";
    private static final String ACTION_ADD = "add";
    private static final String ACTION_CLEAR = "clear";
    private static final String LINE_INVALID = "lines.%d.%s";
    private static final List<String> KINDS = List.of(NexoraObject.HOLOGRAM, NexoraObject.NPC);
    private static final List<String> SCHEMA_KINDS = List.of(NexoraObject.HOLOGRAM, NexoraObject.LINE, NexoraObject.NPC);
    private static final Gson GSON = new Gson();

    @FunctionalInterface
    private interface Route {
        Object handle(Session session, JsonObject body) throws Exception;
    }

    private static Map.Entry<String, Route> entry(String path, Route route) {
        return Map.entry(path, route);
    }

    private record Session(UUID uuid, String name, long expiresAt) {}

    private static final class ApiException extends RuntimeException {
        private final int status;
        private final String key;
        private final transient Map<String, String> placeholders;

        private ApiException(int status, String key) {
            this(status, key, Map.of());
        }

        private ApiException(int status, String key, Map<String, String> placeholders) {
            super(key, null, false, false);
            this.status = status;
            this.key = key;
            this.placeholders = placeholders;
        }
    }

    private final Nexora plugin;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, Route> gets = Map.of(
        "/api/schema", this::schema,
        "/api/objects", this::list,
        "/api/me", this::me,
        "/api/materials", this::materials);
    private final Map<String, Route> posts = Map.ofEntries(
        entry("/api/object", this::detail),
        entry("/api/save", this::save),
        entry("/api/create", this::create),
        entry("/api/delete", this::delete),
        entry("/api/movehere", this::moveHere),
        entry("/api/teleport", this::teleport),
        entry("/api/path", this::path),
        entry("/api/equip", this::equip),
        entry("/api/terrain", this::terrain),
        entry("/api/move", this::move),
        entry("/api/waypoints", this::waypoints));
    private volatile HttpServer server;
    private volatile ExecutorService executor;
    private volatile byte[] page = new byte[0];
    private volatile Map<String, List<String>> materialCache;
    private volatile String autoHost = DEFAULT_HOST;
    private boolean lookupStarted;

    public WebServer(Nexora plugin) {
        this.plugin = plugin;
    }

    public synchronized void start() {
        if (server != null || !plugin.isEnabled() || !plugin.getConfig().getBoolean("web.enabled", true)) return;
        var bytes = loadPage();
        if (bytes == null) {
            plugin.getLogger().log(Level.WARNING, text("web-page-missing", Map.of()));
            return;
        }
        page = bytes;
        var http = bind(plugin.getConfig().getString("web.bind", DEFAULT_BIND));
        if (http == null) return;
        var pool = Executors.newSingleThreadExecutor(runnable -> {
            var thread = new Thread(runnable, THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
        http.createContext(ROUTE_ROOT, this::handle);
        http.setExecutor(pool);
        http.start();
        server = http;
        executor = pool;
        startHostLookup();
    }

    public int port() {
        var http = server;
        return http == null ? -1 : http.getAddress().getPort();
    }

    public String host() {
        var configured = plugin.getConfig().getString("web.host", "").trim();
        if (!configured.isEmpty()) return bracket(configured);
        var ip = Bukkit.getIp().trim();
        return bracket(ip.isEmpty() || UNSPECIFIED_HOSTS.contains(ip) ? autoHost : ip);
    }

    public CompletableFuture<Integer> restart() {
        return restart(false);
    }

    public CompletableFuture<Integer> restart(boolean forgetPort) {
        var future = new CompletableFuture<Integer>();
        plugin.scheduler().runAsync(() -> {
            try {
                stop();
                if (forgetPort) Files.deleteIfExists(portFile());
                start();
                future.complete(port());
            } catch (IOException | RuntimeException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private HttpServer bind(String bind) {
        var configured = cfgInt("web.port", DEFAULT_PORT);
        if (configured > 0) return bindFixed(bind, configured);
        var stored = storedPort();
        if (stored > 0) {
            try {
                return HttpServer.create(new InetSocketAddress(bind, stored), 0);
            } catch (IOException | RuntimeException ignored) {
            }
        }
        return bindRandom(bind);
    }

    private HttpServer bindFixed(String bind, int port) {
        try {
            return HttpServer.create(new InetSocketAddress(bind, port), 0);
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, text("web-bind-failed", Map.of("bind", bind, "port", String.valueOf(port))), e);
            return null;
        }
    }

    private HttpServer bindRandom(String bind) {
        var min = Math.max(MIN_PORT, cfgInt("web.random-port-min", DEFAULT_RANDOM_MIN));
        var max = Math.min(MAX_PORT, cfgInt("web.random-port-max", DEFAULT_RANDOM_MAX));
        if (min > max) {
            min = DEFAULT_RANDOM_MIN;
            max = DEFAULT_RANDOM_MAX;
        }
        var attempts = Math.max(1, cfgInt("web.random-port-attempts", DEFAULT_RANDOM_ATTEMPTS));
        Exception last = null;
        for (var i = 0; i < attempts; i++) {
            var port = random.nextInt(min, max + 1);
            try {
                var http = HttpServer.create(new InetSocketAddress(bind, port), 0);
                storePort(port);
                return http;
            } catch (IOException | RuntimeException e) {
                last = e;
            }
        }
        plugin.getLogger().log(Level.WARNING, text("web-port-exhausted", Map.of("min", String.valueOf(min), "max", String.valueOf(max), "attempts", String.valueOf(attempts))), last);
        return null;
    }

    private Path portFile() {
        return plugin.getDataFolder().toPath().resolve(PORT_FILE);
    }

    private int storedPort() {
        var file = portFile();
        if (!Files.isRegularFile(file)) return 0;
        try {
            var value = Integer.parseInt(Files.readString(file).trim());
            return value >= MIN_PORT && value <= MAX_PORT ? value : 0;
        } catch (IOException | NumberFormatException e) {
            return 0;
        }
    }

    private void storePort(int port) {
        try {
            var file = portFile();
            Files.createDirectories(file.getParent());
            Files.writeString(file, String.valueOf(port));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, text("web-port-save-failed", Map.of("file", PORT_FILE)), e);
        }
    }

    private void startHostLookup() {
        if (lookupStarted) return;
        lookupStarted = true;
        plugin.scheduler().runAsync(() -> autoHost = discoverHost());
    }

    private String discoverHost() {
        var publicIp = publicIp();
        if (publicIp != null) return publicIp;
        var local = siteLocalAddress();
        return local != null ? local : DEFAULT_HOST;
    }

    private String publicIp() {
        var url = plugin.getConfig().getString("web.ip-lookup-url", DEFAULT_LOOKUP_URL).trim();
        if (url.isEmpty()) return null;
        var timeout = Duration.ofSeconds(Math.max(1, cfgInt("web.ip-lookup-timeout-seconds", DEFAULT_LOOKUP_TIMEOUT_SECONDS)));
        try (var client = HttpClient.newBuilder().connectTimeout(timeout).build()) {
            var request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            var body = response.body().trim();
            return response.statusCode() == HTTP_OK && ADDRESS.matcher(body).matches() ? body : null;
        } catch (IOException | IllegalArgumentException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private String siteLocalAddress() {
        try {
            return NetworkInterface.networkInterfaces()
                .filter(this::usable)
                .flatMap(NetworkInterface::inetAddresses)
                .filter(address -> address instanceof Inet4Address && address.isSiteLocalAddress())
                .map(InetAddress::getHostAddress)
                .findFirst()
                .orElse(null);
        } catch (SocketException e) {
            return null;
        }
    }

    private boolean usable(NetworkInterface nic) {
        try {
            return nic.isUp() && !nic.isLoopback();
        } catch (SocketException e) {
            return false;
        }
    }

    private static String bracket(String host) {
        return host.contains(IPV6_MARK) && !host.startsWith(IPV6_OPEN) ? IPV6_OPEN + host + IPV6_CLOSE : host;
    }

    public synchronized void stop() {
        var http = server;
        server = null;
        if (http != null) http.stop(0);
        var pool = executor;
        executor = null;
        if (pool != null) pool.shutdownNow();
        sessions.clear();
    }

    public void sendLink(Player player) {
        var messages = plugin.messages();
        if (server == null) {
            messages.send(player, "web-disabled");
            return;
        }
        if (!player.hasPermission(plugin.getConfig().getString("permissions.web", DEFAULT_WEB_PERMISSION))) {
            messages.send(player, "web-no-permission");
            return;
        }
        purgeExpired();
        var token = newToken();
        var minutes = Math.max(1, cfgInt("web.session-minutes", DEFAULT_SESSION_MINUTES));
        sessions.put(token, new Session(player.getUniqueId(), player.getName(), System.currentTimeMillis() + minutes * MS_PER_MINUTE));
        var url = publicUrl() + LINK_SUFFIX + token;
        var component = messages.component("web-link", Map.of("minutes", String.valueOf(minutes)))
            .clickEvent(ClickEvent.openUrl(url))
            .hoverEvent(HoverEvent.showText(messages.component("web-link-hover")));
        player.sendMessage(component);
    }

    private byte[] loadPage() {
        try (var in = plugin.getResource(PAGE_RESOURCE)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private String publicUrl() {
        var url = plugin.getConfig().getString("web.public-url", "").trim();
        if (url.isEmpty()) url = HTTP_SCHEME + host() + PORT_MARK + port();
        return url.endsWith(ROUTE_ROOT) ? url.substring(0, url.length() - 1) : url;
    }

    private String newToken() {
        var bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void purgeExpired() {
        var now = System.currentTimeMillis();
        sessions.values().removeIf(session -> session.expiresAt() < now);
    }

    private int cfgInt(String path, int fallback) {
        return plugin.getConfig().getInt(path, fallback);
    }

    private String csp() {
        return String.join(CSP_SEPARATOR,
            CSP_DEFAULT,
            directive("script-src", CSP_INLINE, assetOrigin(A_VIEWER)),
            directive("style-src", CSP_INLINE, assetOrigin(A_FONT)),
            directive("font-src", originOf(plugin.getConfig().getString(ASSET_FONT_HOST, ""))),
            directive("connect-src", CSP_SELF, assetOrigin(A_SKIN)),
            directive("img-src", CSP_DATA, assetOrigin(A_TEXTURE), assetOrigin(A_SKIN), assetOrigin(A_HEAD), assetOrigin(A_BODY)),
            CSP_TAIL);
    }

    private static String directive(String name, String... sources) {
        var unique = new LinkedHashSet<String>();
        for (var source : sources) {
            if (source != null) unique.add(source);
        }
        return unique.isEmpty() ? name + CSP_NONE : name + " " + String.join(" ", unique);
    }

    private String assetOrigin(String key) {
        return originOf(plugin.getConfig().getString(ASSET_PATH + key, ""));
    }

    private static String originOf(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            var uri = URI.create(URL_PLACEHOLDER.matcher(url.trim()).replaceAll(URL_PLACEHOLDER_VALUE));
            var scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null || !ASSET_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) return null;
            return scheme.toLowerCase(Locale.ROOT) + SCHEME_SEPARATOR + uri.getHost() + (uri.getPort() < 0 ? "" : PORT_MARK + uri.getPort());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String text(String key, Map<String, String> placeholders) {
        var template = ColorUtil.plain(plugin.messages().raw(key));
        return template.isEmpty() ? key : MessageManager.apply(template, placeholders);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            var headers = exchange.getResponseHeaders();
            headers.set("Cache-Control", "no-store");
            headers.set("X-Content-Type-Options", "nosniff");
            headers.set("X-Frame-Options", "DENY");
            headers.set("Referrer-Policy", "no-referrer");
            headers.set("Content-Security-Policy", csp());
            var path = exchange.getRequestURI().getPath();
            if (ROUTE_ROOT.equals(path)) servePage(exchange);
            else if (path.startsWith(ROUTE_API)) serveApi(exchange, path);
            else fail(exchange, new ApiException(HTTP_NOT_FOUND, "web-error-not-found"));
        } catch (RuntimeException e) {
            fail(exchange, translate(e));
        } finally {
            exchange.close();
        }
    }

    private void servePage(HttpExchange exchange) throws IOException {
        if (!GET.equals(exchange.getRequestMethod())) {
            fail(exchange, new ApiException(HTTP_BAD_METHOD, "web-error-method"));
            return;
        }
        send(exchange, HTTP_OK, TYPE_HTML, page);
    }

    private void serveApi(HttpExchange exchange, String path) throws IOException {
        var session = authenticate(exchange);
        if (session == null) {
            fail(exchange, new ApiException(HTTP_UNAUTHORIZED, "web-error-unauthorized"));
            return;
        }
        var method = exchange.getRequestMethod();
        var route = GET.equals(method) ? gets.get(path) : POST.equals(method) ? posts.get(path) : null;
        if (route == null) {
            var known = gets.containsKey(path) || posts.containsKey(path);
            fail(exchange, new ApiException(known ? HTTP_BAD_METHOD : HTTP_NOT_FOUND, known ? "web-error-method" : "web-error-not-found"));
            return;
        }
        Object result;
        try {
            var body = POST.equals(method) ? readBody(exchange) : new JsonObject();
            result = route.handle(session, body);
        } catch (Exception e) {
            fail(exchange, translate(e));
            return;
        }
        json(exchange, HTTP_OK, result);
    }

    private Session authenticate(HttpExchange exchange) {
        var header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith(BEARER)) return null;
        var token = header.substring(BEARER.length()).trim();
        var session = sessions.get(token);
        if (session == null) return null;
        if (session.expiresAt() < System.currentTimeMillis()) {
            sessions.remove(token);
            return null;
        }
        return session;
    }

    private JsonObject readBody(HttpExchange exchange) throws IOException {
        var max = cfgInt("web.max-body-bytes", DEFAULT_BODY_BYTES);
        var bytes = exchange.getRequestBody().readNBytes(max + 1);
        if (bytes.length > max) throw new ApiException(HTTP_ENTITY_TOO_LARGE, "web-error-too-large");
        if (bytes.length == 0) return new JsonObject();
        try {
            var element = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (element.isJsonObject()) return element.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new ApiException(HTTP_BAD_REQUEST, "web-error-bad-request");
        }
        throw new ApiException(HTTP_BAD_REQUEST, "web-error-bad-request");
    }

    private ApiException translate(Throwable error) {
        if (error instanceof ExecutionException && error.getCause() != null) return translate(error.getCause());
        if (error instanceof ApiException api) return api;
        if (error instanceof TimeoutException) return new ApiException(HTTP_GATEWAY_TIMEOUT, "web-error-timeout");
        if (error instanceof InterruptedException) Thread.currentThread().interrupt();
        plugin.getLogger().log(Level.WARNING, text("web-error-internal", Map.of()), error);
        return new ApiException(HTTP_INTERNAL_ERROR, "web-error-internal");
    }

    private void fail(HttpExchange exchange, ApiException error) throws IOException {
        json(exchange, error.status, Map.of(K_ERROR, text(error.key, error.placeholders)));
    }

    private void json(HttpExchange exchange, int status, Object payload) throws IOException {
        send(exchange, status, TYPE_JSON, GSON.toJson(payload).getBytes(StandardCharsets.UTF_8));
    }

    private void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, body.length);
        try (var out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(cfgInt("web.request-timeout-seconds", DEFAULT_TIMEOUT_SECONDS), TimeUnit.SECONDS);
    }

    private <T> T await(NexoraObject object, CompletableFuture<T> future) throws Exception {
        try {
            return await(future);
        } catch (ExecutionException e) {
            if (object.isRemoved()) throw new ApiException(HTTP_NOT_FOUND, "web-error-gone");
            throw e;
        }
    }

    private static String str(JsonObject body, String key) {
        var value = optStr(body, key);
        if (value == null) throw new ApiException(HTTP_BAD_REQUEST, "web-error-bad-request");
        return value;
    }

    private static String optStr(JsonObject body, String key) {
        var element = body.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private static Map<String, String> strings(JsonElement element) {
        Map<String, String> out = new LinkedHashMap<>();
        if (element == null || !element.isJsonObject()) return out;
        for (var entry : element.getAsJsonObject().entrySet()) {
            if (entry.getValue().isJsonPrimitive()) out.put(entry.getKey(), entry.getValue().getAsString());
        }
        return out;
    }

    private static int refOf(JsonObject spec) {
        var element = spec.get(K_REF);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return -1;
        return element.getAsInt();
    }

    private String kind(JsonObject body) {
        var kind = str(body, K_KIND);
        if (!KINDS.contains(kind)) throw new ApiException(HTTP_BAD_REQUEST, "web-error-invalid-kind", Map.of("kind", kind));
        return kind;
    }

    private String id(JsonObject body) {
        var id = str(body, K_ID);
        if (!plugin.objects().validId(id)) throw new ApiException(HTTP_BAD_REQUEST, "web-error-invalid-id", Map.of("id", id));
        return id;
    }

    private NexoraObject find(JsonObject body) {
        var kind = kind(body);
        var id = id(body);
        return plugin.objects().get(kind, id)
            .orElseThrow(() -> new ApiException(HTTP_NOT_FOUND, "web-error-unknown-object", Map.of("kind", kind, "id", id)));
    }

    private Player onlinePlayer(Session session) {
        var player = Bukkit.getPlayer(session.uuid());
        if (player == null) throw new ApiException(HTTP_CONFLICT, "web-error-offline");
        return player;
    }

    private Location locationOf(Player player) throws Exception {
        var future = new CompletableFuture<Location>();
        plugin.scheduler().runAtEntity(player, () -> future.complete(player.getLocation()), () -> future.complete(null));
        return await(future);
    }

    private Location playerLocation(Session session) throws Exception {
        var location = locationOf(onlinePlayer(session));
        if (location == null) throw new ApiException(HTTP_CONFLICT, "web-error-offline");
        return location;
    }

    private void onRegion(Location location, Runnable task) {
        if (location.getWorld() == null) plugin.scheduler().runGlobal(task);
        else plugin.scheduler().runAtLocation(location, task);
    }

    private Object schema(Session session, JsonObject body) {
        Map<String, Object> kinds = new LinkedHashMap<>();
        for (var kind : SCHEMA_KINDS) kinds.put(kind, Map.of("sections", sectionViews(kind)));
        return Map.of("kinds", kinds, "config", configView());
    }

    private List<Map<String, Object>> sectionViews(String kind) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (var section : plugin.schema().sections(kind)) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put(K_ID, section.id());
            view.put("title", ColorUtil.plain(section.title()));
            view.put("props", section.props().stream().map(this::propView).toList());
            out.add(view);
        }
        return out;
    }

    private Map<String, Object> propView(Schema.Prop prop) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put(K_ID, prop.id());
        view.put(K_LABEL, ColorUtil.plain(prop.label()));
        view.put("type", prop.type().name().toLowerCase(Locale.ROOT));
        view.put("min", prop.min());
        view.put("max", prop.max());
        view.put("step", prop.step());
        List<Map<String, String>> options = new ArrayList<>();
        for (var option : prop.options()) options.add(Map.of(K_ID, option.id(), K_LABEL, ColorUtil.plain(option.label())));
        view.put("options", options);
        return view;
    }

    private Map<String, Object> configView() {
        var config = plugin.getConfig();
        Map<String, Object> effects = new LinkedHashMap<>();
        effects.put("typewriterHold", config.getInt("effects.typewriter-hold-steps", 20));
        effects.put("rainbowStep", config.getDouble("effects.rainbow-step", 0.02));
        effects.put("rainbowSpread", config.getDouble("effects.rainbow-spread", 0.06));
        effects.put("rainbowSaturation", config.getDouble("effects.rainbow-saturation", 0.85));
        effects.put("rainbowBrightness", config.getDouble("effects.rainbow-brightness", 1.0));
        effects.put("waveStep", config.getDouble("effects.wave-step", 0.25));
        effects.put("waveSpread", config.getDouble("effects.wave-spread", 0.35));
        effects.put("scrollGap", config.getString("effects.scroll-gap", ""));
        effects.put("flickerChance", config.getDouble("effects.flicker-chance", 0.35));
        effects.put("fadeStep", config.getDouble("effects.fade-step", 0.08));
        Map<String, Object> placeholders = new LinkedHashMap<>();
        placeholders.put("online", Bukkit.getOnlinePlayers().size());
        placeholders.put("max", Bukkit.getMaxPlayers());
        placeholders.put("timeFormat", config.getString("placeholders.time-format", ""));
        placeholders.put("dateFormat", config.getString("placeholders.date-format", ""));
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("maxLines", cfgInt("limits.max-lines", DEFAULT_MAX_LINES));
        view.put("maxWaypoints", cfgInt("limits.max-waypoints", DEFAULT_MAX_WAYPOINTS));
        view.put("assets", assetView());
        view.put("frameSeparator", config.getString("animation.frame-separator", ""));
        view.put("effects", effects);
        view.put("placeholders", placeholders);
        view.put("equipmentSlots", config.getStringList("npc.equipment-slots"));
        view.put("idPattern", config.getString("ids.pattern", ""));
        view.put("defaultNpcType", config.getString("npc.default-type", DEFAULT_NPC_TYPE));
        view.put("lineDefaults", plugin.objects().defaults(NexoraObject.LINE));
        view.put("nameplateSuffix", config.getString("npc.nameplate.id-suffix", ""));
        view.put("lookAngle", config.getDouble("npc.triggers.look-angle", 10));
        return view;
    }

    private Map<String, String> assetView() {
        Map<String, String> assets = new LinkedHashMap<>();
        for (var key : ASSET_KEYS) assets.put(key, plugin.getConfig().getString(ASSET_PATH + key, ""));
        return assets;
    }

    private Object equip(Session session, JsonObject body) throws Exception {
        var object = find(body);
        if (!(object instanceof Npc npc)) throw new ApiException(HTTP_BAD_REQUEST, "web-error-bad-request");
        var slotName = str(body, K_SLOT).toUpperCase(Locale.ROOT);
        if (!plugin.getConfig().getStringList("npc.equipment-slots").contains(slotName)) throw new ApiException(HTTP_BAD_REQUEST, "web-error-bad-request");
        var slot = EquipmentSlot.valueOf(slotName);
        var raw = optStr(body, K_MATERIAL);
        ItemStack item = null;
        if (raw != null && !raw.isBlank()) {
            var material = Material.matchMaterial(raw.trim());
            if (material == null || material.isAir() || !material.isItem()) throw new ApiException(HTTP_BAD_REQUEST, "web-error-bad-material", Map.of(K_MATERIAL, raw));
            item = ItemStack.of(material);
        }
        var stack = item;
        await(npc, plugin.objects().<Npc>mutate(npc, target -> {
            if (stack == null) target.equipment().remove(slot);
            else target.equipment().put(slot, stack);
        }));
        return Map.of(K_OK, true);
    }

    private Map<String, Object> summary(NexoraObject object) {
        var world = object.anchor().getWorld();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put(K_KIND, object.kind());
        view.put(K_ID, object.id());
        view.put(K_WORLD, world == null ? "" : world.getName());
        view.put(K_X, object.x());
        view.put(K_Y, object.y());
        view.put(K_Z, object.z());
        view.put(K_YAW, object.anchor().getYaw());
        view.put("pathPoints", object.waypointCount());
        if (object instanceof Npc npc) {
            view.put(K_ENTITY, npc.entityType());
            view.put("skinId", npc.skinId());
        }
        if (object instanceof Hologram hologram) view.put(K_LINES, hologram.lines().size());
        return view;
    }

    private Object list(Session session, JsonObject body) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (var kind : KINDS) {
            for (var object : plugin.objects().list(kind)) out.add(summary(object));
        }
        return Map.of("objects", out);
    }

    private Object detail(Session session, JsonObject body) throws Exception {
        var object = find(body);
        return await(object, plugin.objects().<NexoraObject, Map<String, Object>>query(object, this::describe));
    }

    private Map<String, Object> describe(NexoraObject object) {
        var view = summary(object);
        view.put(K_WAYPOINTS, object.waypointCount());
        view.put("waypointList", object.waypoints().stream().map(point -> new double[]{point.getX(), point.getY(), point.getZ()}).toList());
        view.put(K_PROPS, object.values());
        if (object instanceof Hologram hologram) view.put(K_LINES, hologram.lines().stream().map(this::lineView).toList());
        if (object instanceof Npc npc) view.put("equipment", equipment(npc));
        return view;
    }

    private Map<String, Object> lineView(HoloLine line) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put(K_PROPS, line.values(plugin.schema()));
        view.put("hasItem", line.itemData() != null);
        return view;
    }

    private List<Map<String, Object>> equipment(Npc npc) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (var name : plugin.getConfig().getStringList("npc.equipment-slots")) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("slot", name);
            try {
                var item = npc.equipment().get(EquipmentSlot.valueOf(name.toUpperCase(Locale.ROOT)));
                if (item != null && !item.isEmpty()) view.put("material", item.getType().name());
            } catch (IllegalArgumentException ignored) {
            }
            out.add(view);
        }
        return out;
    }

    private Object save(Session session, JsonObject body) throws Exception {
        var object = find(body);
        var props = strings(body.get(K_PROPS));
        var lines = body.get(K_LINES) instanceof JsonArray array ? array : null;
        var maxLines = cfgInt("limits.max-lines", DEFAULT_MAX_LINES);
        List<String> invalid = new ArrayList<>();
        await(object, plugin.objects().<NexoraObject>mutate(object, target -> {
            invalid.addAll(target.apply(props));
            if (target instanceof Hologram hologram && lines != null) invalid.addAll(rebuildLines(hologram, lines, maxLines));
        }));
        return Map.of(K_OK, true, "invalid", invalid);
    }

    private List<String> rebuildLines(Hologram hologram, JsonArray specs, int maxLines) {
        var existing = new ArrayList<>(hologram.lines());
        List<HoloLine> next = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        var count = Math.min(specs.size(), maxLines);
        for (var i = 0; i < count; i++) {
            if (!specs.get(i).isJsonObject()) continue;
            var spec = specs.get(i).getAsJsonObject();
            var ref = refOf(spec);
            var line = ref >= 0 && ref < existing.size() ? existing.get(ref).copy() : plugin.objects().newLine();
            for (var id : line.apply(plugin.schema(), strings(spec.get(K_PROPS)))) invalid.add(LINE_INVALID.formatted(next.size(), id));
            next.add(line);
        }
        hologram.lines().clear();
        hologram.lines().addAll(next);
        return invalid;
    }

    private Object create(Session session, JsonObject body) throws Exception {
        var kind = kind(body);
        var id = id(body);
        if (plugin.objects().exists(kind, id)) throw new ApiException(HTTP_CONFLICT, "web-error-id-exists", Map.of("kind", kind, "id", id));
        var player = onlinePlayer(session);
        var type = entityType(body);
        var done = new CompletableFuture<NexoraObject>();
        plugin.scheduler().runAtEntity(player, () -> {
            try {
                var location = player.getLocation();
                done.complete(NexoraObject.HOLOGRAM.equals(kind) ? plugin.objects().createHologram(id, location) : plugin.objects().createNpc(id, location, type));
            } catch (RuntimeException e) {
                done.completeExceptionally(e);
            }
        }, () -> done.completeExceptionally(new ApiException(HTTP_CONFLICT, "web-error-offline")));
        await(done);
        return Map.of(K_OK, true, K_KIND, kind, K_ID, id);
    }

    private String entityType(JsonObject body) {
        var fallback = plugin.getConfig().getString("npc.default-type", DEFAULT_NPC_TYPE);
        var prop = plugin.schema().props(NexoraObject.NPC).get(PROP_ENTITY_TYPE);
        var raw = optStr(body, K_ENTITY);
        if (prop == null || raw == null) return fallback;
        var normalized = Schema.normalize(prop, raw);
        return normalized == null ? fallback : normalized;
    }

    private Object delete(Session session, JsonObject body) throws Exception {
        var object = find(body);
        var done = new CompletableFuture<Void>();
        onRegion(object.anchor(), () -> {
            try {
                plugin.objects().delete(object);
                done.complete(null);
            } catch (RuntimeException e) {
                done.completeExceptionally(e);
            }
        });
        await(done);
        return Map.of(K_OK, true);
    }

    private Object moveHere(Session session, JsonObject body) throws Exception {
        var object = find(body);
        var location = playerLocation(session);
        await(object, plugin.objects().<NexoraObject>mutate(object, target -> target.moveTo(location)));
        return Map.of(K_OK, true);
    }

    private Object teleport(Session session, JsonObject body) throws Exception {
        var object = find(body);
        var player = onlinePlayer(session);
        var target = object.anchor();
        if (target.getWorld() == null) throw new ApiException(HTTP_CONFLICT, "web-error-world-missing");
        var done = new CompletableFuture<Boolean>();
        plugin.scheduler().runAtEntity(player, () -> player.teleportAsync(target).whenComplete((result, error) -> {
            if (error != null) done.completeExceptionally(error);
            else done.complete(result);
        }), () -> done.complete(false));
        if (!Boolean.TRUE.equals(await(done))) throw new ApiException(HTTP_CONFLICT, "web-error-teleport");
        return Map.of(K_OK, true);
    }

    private Object path(Session session, JsonObject body) throws Exception {
        var object = find(body);
        var action = str(body, K_ACTION);
        switch (action) {
            case ACTION_ADD -> {
                var limit = cfgInt("limits.max-waypoints", DEFAULT_MAX_WAYPOINTS);
                if (object.waypointCount() >= limit) throw new ApiException(HTTP_CONFLICT, "web-error-waypoint-limit", Map.of("limit", String.valueOf(limit)));
                var location = playerLocation(session);
                await(object, plugin.objects().<NexoraObject>mutate(object, target -> target.addWaypoint(location)));
            }
            case ACTION_CLEAR -> await(object, plugin.objects().<NexoraObject>mutate(object, NexoraObject::clearWaypoints));
            default -> throw new ApiException(HTTP_BAD_REQUEST, "web-error-bad-action", Map.of("action", action));
        }
        var count = await(object, plugin.objects().<NexoraObject, Integer>query(object, NexoraObject::waypointCount));
        return Map.of(K_OK, true, K_WAYPOINTS, count);
    }

    private Object me(Session session, JsonObject body) throws Exception {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("name", session.name());
        view.put("uuid", session.uuid().toString());
        var player = Bukkit.getPlayer(session.uuid());
        var location = player == null ? null : locationOf(player);
        var online = location != null && location.getWorld() != null;
        view.put("online", online);
        if (online) {
            view.put(K_WORLD, location.getWorld().getName());
            view.put(K_X, location.getX());
            view.put(K_Y, location.getY());
            view.put(K_Z, location.getZ());
            view.put(K_YAW, location.getYaw());
        }
        return view;
    }

    private Object materials(Session session, JsonObject body) {
        var cached = materialCache;
        if (cached != null) return cached;
        List<String> items = new ArrayList<>();
        List<String> blocks = new ArrayList<>();
        for (var material : Material.values()) {
            if (material.isLegacy() || material.isAir() || !material.isItem()) continue;
            var name = material.name().toLowerCase(Locale.ROOT);
            items.add(name);
            if (material.isBlock()) blocks.add(name);
        }
        Map<String, List<String>> built = Map.of("items", List.copyOf(items), "blocks", List.copyOf(blocks));
        materialCache = built;
        return built;
    }

    private static Double optNumber(JsonObject body, String key) {
        var element = body.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return null;
        var value = element.getAsDouble();
        if (!Double.isFinite(value)) throw new ApiException(HTTP_BAD_REQUEST, "web-error-bad-request");
        return value;
    }

    private static double number(JsonObject body, String key) {
        var value = optNumber(body, key);
        if (value == null) throw new ApiException(HTTP_BAD_REQUEST, "web-error-bad-request");
        return value;
    }

    private static double coord(double value) {
        return Math.clamp(value, -MAX_COORD, MAX_COORD);
    }

    private World world(String name) {
        var world = Bukkit.getWorld(name);
        if (world == null) throw new ApiException(HTTP_NOT_FOUND, "web-error-unknown-world", Map.of(K_WORLD, name));
        return world;
    }

    private Object terrain(Session session, JsonObject body) throws Exception {
        var world = world(str(body, K_WORLD));
        var centerX = (int) Math.floor(coord(number(body, K_X)));
        var centerZ = (int) Math.floor(coord(number(body, K_Z)));
        var maxRadius = Math.max(CLAMP_FLOOR, cfgInt("web.terrain-max-radius", DEFAULT_TERRAIN_RADIUS));
        var radius = (int) Math.clamp(Math.round(coord(number(body, K_RADIUS))), CLAMP_FLOOR, maxRadius);
        var done = new CompletableFuture<Map<String, Object>>();
        plugin.scheduler().runAtLocation(new Location(world, centerX, world.getMinHeight(), centerZ), () -> {
            try {
                done.complete(sampleTerrain(world, centerX - radius, centerZ - radius, radius * 2 + 1));
            } catch (RuntimeException e) {
                done.completeExceptionally(e);
            }
        });
        return await(done);
    }

    private Map<String, Object> sampleTerrain(World world, int x0, int z0, int size) {
        var colors = new int[size * size];
        var heights = new int[size * size];
        var chunkX0 = x0 >> CHUNK_SHIFT;
        var chunkZ0 = z0 >> CHUNK_SHIFT;
        var chunksX = ((x0 + size - 1) >> CHUNK_SHIFT) - chunkX0 + 1;
        var chunksZ = ((z0 + size - 1) >> CHUNK_SHIFT) - chunkZ0 + 1;
        var readable = new boolean[chunksX * chunksZ];
        for (var cz = 0; cz < chunksZ; cz++) {
            for (var cx = 0; cx < chunksX; cx++) {
                var chunkX = chunkX0 + cx;
                var chunkZ = chunkZ0 + cz;
                readable[cz * chunksX + cx] = world.isChunkLoaded(chunkX, chunkZ) && Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ);
            }
        }
        for (var dz = 0; dz < size; dz++) {
            for (var dx = 0; dx < size; dx++) {
                var index = dz * size + dx;
                var blockX = x0 + dx;
                var blockZ = z0 + dz;
                var chunkIndex = ((blockZ >> CHUNK_SHIFT) - chunkZ0) * chunksX + ((blockX >> CHUNK_SHIFT) - chunkX0);
                if (!readable[chunkIndex]) {
                    colors[index] = NO_COLOR;
                    heights[index] = NO_HEIGHT;
                    continue;
                }
                var block = world.getHighestBlockAt(blockX, blockZ, HeightMap.WORLD_SURFACE);
                colors[index] = block.getBlockData().getMapColor().asRGB();
                heights[index] = block.getY();
            }
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("x0", x0);
        view.put("z0", z0);
        view.put("size", size);
        view.put("colors", colors);
        view.put("heights", heights);
        return view;
    }

    private Object move(Session session, JsonObject body) throws Exception {
        var object = find(body);
        var current = object.anchor();
        var world = current.getWorld();
        if (world == null) throw new ApiException(HTTP_CONFLICT, "web-error-world-missing");
        var requested = optStr(body, K_WORLD);
        if (requested != null && !requested.equals(world.getName())) {
            throw new ApiException(HTTP_BAD_REQUEST, "web-error-world-mismatch", Map.of(K_WORLD, world.getName()));
        }
        var x = coord(number(body, K_X));
        var z = coord(number(body, K_Z));
        var y = optNumber(body, K_Y);
        var yaw = optNumber(body, K_YAW);
        var targetY = y == null ? current.getY() : Math.clamp(y, world.getMinHeight(), world.getMaxHeight());
        var targetYaw = yaw == null ? current.getYaw() : Location.normalizeYaw(yaw.floatValue());
        var limit = plugin.getConfig().getDouble("web.max-move-distance", DEFAULT_MOVE_DISTANCE);
        if (new Vector(x, targetY, z).distance(current.toVector()) > limit) {
            throw new ApiException(HTTP_BAD_REQUEST, "web-error-move-too-far", Map.of("max", String.valueOf(limit)));
        }
        var target = new Location(world, x, targetY, z, targetYaw, current.getPitch());
        await(object, plugin.objects().<NexoraObject>mutate(object, moved -> moved.moveTo(target)));
        return Map.of(K_OK, true);
    }

    private Object waypoints(Session session, JsonObject body) throws Exception {
        var object = find(body);
        var element = body.get(K_POINTS);
        if (element == null || !element.isJsonArray()) throw new ApiException(HTTP_BAD_REQUEST, "web-error-bad-request");
        var array = element.getAsJsonArray();
        var limit = cfgInt("limits.max-waypoints", DEFAULT_MAX_WAYPOINTS);
        if (array.size() > limit) throw new ApiException(HTTP_CONFLICT, "web-error-waypoint-limit", Map.of("limit", String.valueOf(limit)));
        List<Vector> points = new ArrayList<>();
        for (var entry : array) points.add(point(entry));
        await(object, plugin.objects().<NexoraObject>mutate(object, target -> target.setWaypoints(points)));
        var count = await(object, plugin.objects().<NexoraObject, Integer>query(object, NexoraObject::waypointCount));
        return Map.of(K_OK, true, K_WAYPOINTS, count);
    }

    private static Vector point(JsonElement entry) {
        if (!entry.isJsonArray() || entry.getAsJsonArray().size() != POINT_SIZE) throw new ApiException(HTTP_BAD_REQUEST, "web-error-bad-request");
        var values = new double[POINT_SIZE];
        for (var i = 0; i < POINT_SIZE; i++) {
            var part = entry.getAsJsonArray().get(i);
            if (!part.isJsonPrimitive() || !part.getAsJsonPrimitive().isNumber()) throw new ApiException(HTTP_BAD_REQUEST, "web-error-bad-request");
            var value = part.getAsDouble();
            if (!Double.isFinite(value)) throw new ApiException(HTTP_BAD_REQUEST, "web-error-bad-request");
            values[i] = coord(value);
        }
        return new Vector(values[0], values[1], values[2]);
    }
}
