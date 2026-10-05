package net.spxry.nexora.npc;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.spxry.nexora.Nexora;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.regex.Pattern;

public final class SkinParts {
    public enum Part {
        HEAD, TORSO_UPPER, TORSO_LOWER,
        RIGHT_ARM_UPPER, RIGHT_ARM_LOWER, LEFT_ARM_UPPER, LEFT_ARM_LOWER,
        RIGHT_LEG_UPPER, RIGHT_LEG_LOWER, LEFT_LEG_UPPER, LEFT_LEG_LOWER
    }

    public enum Status { READY, GENERATING, FAILED, NO_KEY, NO_SKIN }

    private record Cached(Map<Part, ProfileProperty> parts, boolean slim) {}

    private record Reply(int status, JsonObject body) {}

    private static final String THREAD_NAME = "nexora-mineskin";
    private static final String CACHE_FILE = "skin-parts.yml";
    private static final String CACHE_TEMP_SUFFIX = ".tmp";
    private static final String TEXTURES_PROPERTY = "textures";
    private static final String PART_NAME_PREFIX = "nx-";
    private static final String VARIANT_CLASSIC = "classic";
    private static final String VARIANT_SLIM = "slim";
    private static final String DATA_URL_PREFIX = "data:image/png;base64,";
    private static final String HASH_PLACEHOLDER = "{hash}";
    private static final String NAME_PLACEHOLDER = "{name}";
    private static final Pattern HASH_ID = Pattern.compile("[0-9a-fA-F]{32,64}");
    private static final Pattern NAME_ID = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private static final String HEADER_AUTH = "Authorization";
    private static final String HEADER_AGENT = "User-Agent";
    private static final String HEADER_TYPE = "Content-Type";
    private static final String HEADER_ACCEPT = "Accept";
    private static final String HEADER_RETRY = "Retry-After";
    private static final String MEDIA_JSON = "application/json";
    private static final String BEARER = "Bearer ";

    private static final String KEY_URL = "url";
    private static final String KEY_VISIBILITY = "visibility";
    private static final String KEY_VARIANT = "variant";
    private static final String KEY_NAME = "name";
    private static final String KEY_JOB = "job";
    private static final String KEY_ID = "id";
    private static final String KEY_STATUS = "status";
    private static final String KEY_SKIN = "skin";
    private static final String KEY_TEXTURE = "texture";
    private static final String KEY_DATA = "data";
    private static final String KEY_VALUE = "value";
    private static final String KEY_SIGNATURE = "signature";
    private static final String KEY_RATE = "rateLimit";
    private static final String KEY_NEXT = "next";
    private static final String KEY_RELATIVE = "relative";
    private static final String KEY_TEXTURES = "textures";
    private static final String KEY_SKIN_TEXTURE = "SKIN";
    private static final String KEY_METADATA = "metadata";
    private static final String KEY_MODEL = "model";
    private static final String JOB_COMPLETED = "completed";
    private static final String JOB_FAILED = "failed";

    private static final String CFG_API_KEY = "model.mineskin.api-key";
    private static final String CFG_USER_AGENT = "model.mineskin.user-agent";
    private static final String CFG_QUEUE_URL = "model.mineskin.queue-url";
    private static final String CFG_POLL_MS = "model.mineskin.poll-ms";
    private static final String CFG_TIMEOUT = "model.mineskin.timeout-seconds";
    private static final String CFG_VISIBILITY = "model.mineskin.visibility";
    private static final String CFG_SKIN_HASH = "model.skin-url.hash";
    private static final String CFG_SKIN_NAME = "model.skin-url.name";

    private static final String DEFAULT_USER_AGENT = "Nexora/1.0";
    private static final String DEFAULT_QUEUE_URL = "https://api.mineskin.org/v2/queue";
    private static final String DEFAULT_VISIBILITY = "unlisted";
    private static final String DEFAULT_SKIN_HASH = "http://textures.minecraft.net/texture/{hash}";
    private static final String DEFAULT_SKIN_NAME = "https://mc-heads.net/skin/{name}";
    private static final int DEFAULT_POLL_MS = 1500;
    private static final int DEFAULT_TIMEOUT_SECONDS = 20;
    private static final int MIN_POLL_MS = 250;

    private static final int HTTP_OK = 200;
    private static final int HTTP_ACCEPTED = 202;
    private static final int HTTP_RATE_LIMITED = 429;
    private static final int HTTP_ERROR_FLOOR = 400;
    private static final int MAX_SOURCE_BYTES = 64 * 1024;
    private static final int MAX_RATE_RETRIES = 10;
    private static final long DEFAULT_RATE_WAIT_MS = 5_000L;
    private static final long MAX_RATE_WAIT_MS = 120_000L;
    private static final long JOB_DEADLINE_MS = 300_000L;
    private static final long RETRY_AFTER_FAILURE_MS = 10L * 60_000L;
    private static final long MS_PER_SECOND = 1000L;

    private final Nexora plugin;
    private final ScheduledExecutorService executor;
    private final HttpClient client;
    private final File cacheFile;
    private final Map<String, Cached> ready = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Optional<Map<Part, ProfileProperty>>>> inflight = new ConcurrentHashMap<>();
    private final Map<String, Long> failures = new ConcurrentHashMap<>();
    private final Set<String> warned = ConcurrentHashMap.newKeySet();
    private final Map<String, Map<Part, ProfileProperty>> partials = new ConcurrentHashMap<>();
    private long nextPostAt;

    public SkinParts(Nexora plugin) {
        this.plugin = plugin;
        this.cacheFile = new File(plugin.getDataFolder(), CACHE_FILE);
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
        this.client = HttpClient.newBuilder()
            .connectTimeout(timeout())
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        executor.execute(this::loadCache);
    }

    public CompletableFuture<Optional<Map<Part, ProfileProperty>>> request(String skinId) {
        if (!validId(skinId)) return CompletableFuture.completedFuture(Optional.empty());
        Cached hit = ready.get(skinId);
        if (hit != null) return CompletableFuture.completedFuture(Optional.of(hit.parts()));
        if (apiKey().isEmpty()) return CompletableFuture.completedFuture(Optional.empty());
        Long failedAt = failures.get(skinId);
        if (failedAt != null) {
            if (System.currentTimeMillis() - failedAt < RETRY_AFTER_FAILURE_MS) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            failures.remove(skinId);
        }
        var fresh = new CompletableFuture<Optional<Map<Part, ProfileProperty>>>();
        var existing = inflight.putIfAbsent(skinId, fresh);
        if (existing != null) return existing;
        try {
            executor.execute(() -> generate(skinId, fresh));
        } catch (RejectedExecutionException e) {
            inflight.remove(skinId);
            fresh.complete(Optional.empty());
        }
        return fresh;
    }

    public Optional<Map<Part, ProfileProperty>> cached(String skinId) {
        if (skinId == null) return Optional.empty();
        Cached hit = ready.get(skinId);
        return hit == null ? Optional.empty() : Optional.of(hit.parts());
    }

    public Status status(String skinId) {
        if (!validId(skinId)) return Status.NO_SKIN;
        if (ready.containsKey(skinId)) return Status.READY;
        if (apiKey().isEmpty()) return Status.NO_KEY;
        if (inflight.containsKey(skinId)) return Status.GENERATING;
        if (failures.containsKey(skinId)) return Status.FAILED;
        return Status.GENERATING;
    }

    public boolean slim(String skinId) {
        if (skinId == null) return false;
        Cached hit = ready.get(skinId);
        return hit != null && hit.slim();
    }

    public void shutdown() {
        executor.shutdownNow();
        client.shutdownNow();
        inflight.values().forEach(future -> future.complete(Optional.empty()));
        inflight.clear();
    }

    private static boolean validId(String skinId) {
        return skinId != null && (HASH_ID.matcher(skinId).matches() || NAME_ID.matcher(skinId).matches());
    }

    private void generate(String skinId, CompletableFuture<Optional<Map<Part, ProfileProperty>>> future) {
        Cached hit = ready.get(skinId);
        if (hit != null) {
            finish(skinId, future, Optional.of(hit.parts()));
            return;
        }
        try {
            SkinSlicer.Skin skin = SkinSlicer.parse(download(sourceUrl(skinId)));
            Map<Part, ProfileProperty> parts = partials.computeIfAbsent(skinId, key -> new EnumMap<>(Part.class));
            for (Part part : Part.values()) {
                if (!parts.containsKey(part)) parts.put(part, buildPart(skinId, skin, part));
            }
            Map<Part, ProfileProperty> done = Collections.unmodifiableMap(new EnumMap<>(parts));
            ready.put(skinId, new Cached(done, skin.slim()));
            partials.remove(skinId);
            failures.remove(skinId);
            warned.remove(skinId);
            saveCache();
            finish(skinId, future, Optional.of(done));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            finish(skinId, future, Optional.empty());
        } catch (Exception e) {
            failures.put(skinId, System.currentTimeMillis());
            if (warned.add(skinId)) {
                plugin.getLogger().log(Level.WARNING, "Skin part generation failed for " + skinId, e);
            }
            finish(skinId, future, Optional.empty());
        }
    }

    private void finish(String skinId, CompletableFuture<Optional<Map<Part, ProfileProperty>>> future,
                        Optional<Map<Part, ProfileProperty>> result) {
        inflight.remove(skinId);
        future.complete(result);
    }

    private ProfileProperty buildPart(String skinId, SkinSlicer.Skin skin, Part part) throws IOException, InterruptedException {
        if (part == Part.HEAD && HASH_ID.matcher(skinId).matches()) return headFromHash(skinId, skin.slim());
        int[] pixels = part == Part.HEAD ? skin.pixels() : SkinSlicer.slice(skin, part);
        String variant = part == Part.HEAD && skin.slim() ? VARIANT_SLIM : VARIANT_CLASSIC;
        String name = PART_NAME_PREFIX + part.name().toLowerCase(Locale.ROOT);
        return upload(SkinSlicer.encode(pixels), name, variant);
    }

    private ProfileProperty headFromHash(String hash, boolean slim) {
        JsonObject skinNode = new JsonObject();
        skinNode.addProperty(KEY_URL, config(CFG_SKIN_HASH, DEFAULT_SKIN_HASH).replace(HASH_PLACEHOLDER, hash));
        if (slim) {
            JsonObject metadata = new JsonObject();
            metadata.addProperty(KEY_MODEL, VARIANT_SLIM);
            skinNode.add(KEY_METADATA, metadata);
        }
        JsonObject textures = new JsonObject();
        textures.add(KEY_SKIN_TEXTURE, skinNode);
        JsonObject root = new JsonObject();
        root.add(KEY_TEXTURES, textures);
        String value = Base64.getEncoder().encodeToString(root.toString().getBytes(StandardCharsets.UTF_8));
        return new ProfileProperty(TEXTURES_PROPERTY, value);
    }

    private String sourceUrl(String skinId) {
        if (HASH_ID.matcher(skinId).matches()) {
            return config(CFG_SKIN_HASH, DEFAULT_SKIN_HASH).replace(HASH_PLACEHOLDER, skinId);
        }
        return config(CFG_SKIN_NAME, DEFAULT_SKIN_NAME).replace(NAME_PLACEHOLDER, skinId);
    }

    private byte[] download(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(timeout())
            .header(HEADER_AGENT, config(CFG_USER_AGENT, DEFAULT_USER_AGENT))
            .GET()
            .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = response.body()) {
            if (response.statusCode() != HTTP_OK) throw new IOException("skin download status " + response.statusCode());
            byte[] data = in.readNBytes(MAX_SOURCE_BYTES + 1);
            if (data.length > MAX_SOURCE_BYTES) throw new IOException("skin download too large");
            return data;
        }
    }

    private ProfileProperty upload(byte[] png, String name, String variant) throws IOException, InterruptedException {
        JsonObject payload = new JsonObject();
        payload.addProperty(KEY_URL, DATA_URL_PREFIX + Base64.getEncoder().encodeToString(png));
        payload.addProperty(KEY_VISIBILITY, config(CFG_VISIBILITY, DEFAULT_VISIBILITY));
        payload.addProperty(KEY_VARIANT, variant);
        payload.addProperty(KEY_NAME, name);
        HttpRequest post = HttpRequest.newBuilder(URI.create(queueUrl()))
            .timeout(timeout())
            .header(HEADER_AUTH, BEARER + apiKey())
            .header(HEADER_AGENT, config(CFG_USER_AGENT, DEFAULT_USER_AGENT))
            .header(HEADER_TYPE, MEDIA_JSON)
            .header(HEADER_ACCEPT, MEDIA_JSON)
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build();
        Reply reply = exchange(post, true);
        if (reply.status() == HTTP_OK) return propertyOf(reply.body());
        if (reply.status() != HTTP_ACCEPTED) throw new IOException("mineskin upload status " + reply.status());
        String jobId = text(reply.body(), KEY_JOB, KEY_ID);
        if (jobId == null) throw new IOException("mineskin job id missing");
        return await(jobId);
    }

    private ProfileProperty await(String jobId) throws IOException, InterruptedException {
        long deadline = System.currentTimeMillis() + JOB_DEADLINE_MS;
        long pollMs = Math.max(MIN_POLL_MS, plugin.getConfig().getInt(CFG_POLL_MS, DEFAULT_POLL_MS));
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(pollMs);
            HttpRequest poll = HttpRequest.newBuilder(URI.create(queueUrl() + "/" + jobId))
                .timeout(timeout())
                .header(HEADER_AUTH, BEARER + apiKey())
                .header(HEADER_AGENT, config(CFG_USER_AGENT, DEFAULT_USER_AGENT))
                .header(HEADER_ACCEPT, MEDIA_JSON)
                .GET()
                .build();
            Reply reply = exchange(poll, false);
            if (reply.status() >= HTTP_ERROR_FLOOR) throw new IOException("mineskin poll status " + reply.status());
            String state = text(reply.body(), KEY_JOB, KEY_STATUS);
            if (JOB_COMPLETED.equals(state)) return propertyOf(reply.body());
            if (JOB_FAILED.equals(state)) throw new IOException("mineskin job failed");
        }
        throw new IOException("mineskin job timed out");
    }

    private Reply exchange(HttpRequest request, boolean paced) throws IOException, InterruptedException {
        for (int attempt = 0; ; attempt++) {
            if (paced) waitForSlot();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject body = parse(response.body());
            long suggested = suggestedDelay(response, body);
            if (response.statusCode() != HTTP_RATE_LIMITED) {
                if (paced && suggested > 0) nextPostAt = System.currentTimeMillis() + Math.min(suggested, MAX_RATE_WAIT_MS);
                return new Reply(response.statusCode(), body);
            }
            if (attempt >= MAX_RATE_RETRIES) throw new IOException("mineskin rate limited");
            long wait = Math.min(suggested > 0 ? suggested : DEFAULT_RATE_WAIT_MS, MAX_RATE_WAIT_MS);
            if (paced) nextPostAt = System.currentTimeMillis() + wait;
            else Thread.sleep(wait);
        }
    }

    private void waitForSlot() throws InterruptedException {
        long wait = nextPostAt - System.currentTimeMillis();
        if (wait > 0) Thread.sleep(wait);
    }

    private static long suggestedDelay(HttpResponse<String> response, JsonObject body) {
        Optional<String> header = response.headers().firstValue(HEADER_RETRY);
        if (header.isPresent()) {
            try {
                return Long.parseLong(header.get().trim()) * MS_PER_SECOND;
            } catch (NumberFormatException ignored) {
            }
        }
        JsonElement relative = walk(body, KEY_RATE, KEY_NEXT, KEY_RELATIVE);
        if (relative != null && relative.isJsonPrimitive() && relative.getAsJsonPrimitive().isNumber()) {
            return relative.getAsLong();
        }
        return 0L;
    }

    private static ProfileProperty propertyOf(JsonObject body) throws IOException {
        JsonElement data = walk(body, KEY_SKIN, KEY_TEXTURE, KEY_DATA);
        if (data == null || !data.isJsonObject()) throw new IOException("mineskin response missing texture data");
        JsonObject node = data.getAsJsonObject();
        String value = text(node, KEY_VALUE);
        String signature = text(node, KEY_SIGNATURE);
        if (value == null || signature == null) throw new IOException("mineskin texture incomplete");
        return new ProfileProperty(TEXTURES_PROPERTY, value, signature);
    }

    private static JsonObject parse(String raw) {
        try {
            JsonElement element = JsonParser.parseString(raw);
            return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (JsonParseException e) {
            return new JsonObject();
        }
    }

    private static JsonElement walk(JsonObject root, String... keys) {
        JsonElement current = root;
        for (String key : keys) {
            if (current == null || !current.isJsonObject()) return null;
            current = current.getAsJsonObject().get(key);
        }
        return current;
    }

    private static String text(JsonObject root, String... keys) {
        JsonElement element = walk(root, keys);
        if (element == null || !element.isJsonPrimitive()) return null;
        String value = element.getAsString();
        return value.isEmpty() ? null : value;
    }

    private String config(String path, String fallback) {
        String value = plugin.getConfig().getString(path, fallback);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String apiKey() {
        String key = plugin.getConfig().getString(CFG_API_KEY, "");
        return key == null ? "" : key.trim();
    }

    private String queueUrl() {
        String url = config(CFG_QUEUE_URL, DEFAULT_QUEUE_URL);
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private Duration timeout() {
        return Duration.ofSeconds(Math.max(1, plugin.getConfig().getInt(CFG_TIMEOUT, DEFAULT_TIMEOUT_SECONDS)));
    }

    private void loadCache() {
        if (!cacheFile.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(cacheFile);
        for (String skinId : yaml.getKeys(false)) {
            ConfigurationSection entry = yaml.getConfigurationSection(skinId);
            if (entry == null) continue;
            Map<Part, ProfileProperty> parts = readParts(entry.getConfigurationSection("parts"));
            if (parts.size() == Part.values().length) {
                ready.put(skinId, new Cached(Collections.unmodifiableMap(parts), entry.getBoolean("slim", false)));
            }
        }
    }

    private Map<Part, ProfileProperty> readParts(ConfigurationSection section) {
        Map<Part, ProfileProperty> parts = new EnumMap<>(Part.class);
        if (section == null) return parts;
        for (Part part : Part.values()) {
            ConfigurationSection node = section.getConfigurationSection(part.name());
            if (node == null) continue;
            String value = node.getString(KEY_VALUE);
            if (value == null) continue;
            parts.put(part, new ProfileProperty(TEXTURES_PROPERTY, value, node.getString(KEY_SIGNATURE)));
        }
        return parts;
    }

    private void saveCache() {
        YamlConfiguration yaml = new YamlConfiguration();
        ready.forEach((skinId, cached) -> {
            yaml.set(skinId + ".slim", cached.slim());
            cached.parts().forEach((part, property) -> {
                String base = skinId + ".parts." + part.name();
                yaml.set(base + "." + KEY_VALUE, property.getValue());
                if (property.getSignature() != null) yaml.set(base + "." + KEY_SIGNATURE, property.getSignature());
            });
        });
        try {
            Path target = cacheFile.toPath();
            Files.createDirectories(target.toAbsolutePath().getParent());
            Path temp = target.resolveSibling(cacheFile.getName() + CACHE_TEMP_SUFFIX);
            Files.writeString(temp, yaml.saveToString(), StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save " + CACHE_FILE, e);
        }
    }
}
