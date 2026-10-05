package net.spxry.nexora.npc;

import net.spxry.nexora.Nexora;
import org.bukkit.Color;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.regex.Pattern;

public class SkinPalette {

    public record Palette(Color torso, Color arms, Color legs, Color feet) {}

    private record Entry(Optional<Palette> value, long expiresAt) {
        boolean expired() {
            return System.currentTimeMillis() >= expiresAt;
        }
    }

    private record Region(int x, int y, int w, int h) {}

    private static final String CFG_ENABLED = "npc.skin-palette.enabled";
    private static final String CFG_TEXTURE_URL = "npc.skin-palette.texture-url";
    private static final String CFG_NAME_URL = "npc.skin-palette.name-url";
    private static final String CFG_TIMEOUT = "npc.skin-palette.timeout-seconds";
    private static final String CFG_RETRY = "npc.skin-palette.retry-minutes";
    private static final String DEFAULT_TEXTURE_URL = "http://textures.minecraft.net/texture/{hash}";
    private static final String DEFAULT_NAME_URL = "https://mc-heads.net/skin/{name}";
    private static final long DEFAULT_TIMEOUT_SECONDS = 8L;
    private static final long DEFAULT_RETRY_MINUTES = 10L;
    private static final String HASH_PLACEHOLDER = "{hash}";
    private static final String NAME_PLACEHOLDER = "{name}";
    private static final String LOG_FAILED = "Skin palette fetch failed for ";
    private static final Pattern HASH = Pattern.compile("[0-9a-fA-F]{32,64}");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final int MAX_BYTES = 64 * 1024;
    private static final int SKIN_SIZE = 64;
    private static final int LEGACY_HEIGHT = 32;
    private static final int CHANNEL_SHIFT_RED = 16;
    private static final int CHANNEL_SHIFT_GREEN = 8;
    private static final int CHANNEL_SHIFT_ALPHA = 24;
    private static final int CHANNEL_MASK = 0xFF;
    private static final Region TORSO = new Region(20, 20, 8, 12);
    private static final Region JACKET = new Region(20, 36, 8, 12);
    private static final Region ARM = new Region(44, 20, 4, 12);
    private static final Region LEG = new Region(4, 20, 4, 8);
    private static final Region FOOT = new Region(4, 28, 4, 4);
    private static final Region[] TORSO_LEGACY = {TORSO};
    private static final Region[] TORSO_FULL = {TORSO, JACKET};

    private final Nexora plugin;
    private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<Optional<Palette>>> inFlight = new ConcurrentHashMap<>();
    private HttpClient client;

    public SkinPalette(Nexora plugin) {
        this.plugin = plugin;
    }

    public Optional<Palette> cached(String skinId) {
        var key = key(skinId);
        if (key == null) return Optional.empty();
        var entry = cache.get(key);
        if (entry == null) return Optional.empty();
        if (entry.expired()) {
            cache.remove(key, entry);
            return Optional.empty();
        }
        return entry.value();
    }

    public CompletableFuture<Optional<Palette>> request(String skinId) {
        var key = key(skinId);
        if (key == null || !plugin.getConfig().getBoolean(CFG_ENABLED, true) || !plugin.isEnabled()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        var entry = cache.get(key);
        if (entry != null && !entry.expired()) return CompletableFuture.completedFuture(entry.value());
        var future = new CompletableFuture<Optional<Palette>>();
        var existing = inFlight.putIfAbsent(key, future);
        if (existing != null) return existing;
        plugin.scheduler().runAsync(() -> resolve(key, future));
        return future;
    }

    private void resolve(String key, CompletableFuture<Optional<Palette>> future) {
        Optional<Palette> result;
        try {
            result = fetch(key);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result = Optional.empty();
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, LOG_FAILED + key, e);
            result = Optional.empty();
        }
        var expiresAt = result.isPresent() ? Long.MAX_VALUE : System.currentTimeMillis() + retryMillis();
        cache.put(key, new Entry(result, expiresAt));
        inFlight.remove(key);
        future.complete(result);
    }

    private Optional<Palette> fetch(String key) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create(url(key)))
            .timeout(Duration.ofSeconds(timeoutSeconds()))
            .GET()
            .build();
        var response = client().send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (var in = response.body()) {
            if (response.statusCode() != HttpURLConnection.HTTP_OK) return Optional.empty();
            var data = in.readNBytes(MAX_BYTES + 1);
            if (data.length > MAX_BYTES) return Optional.empty();
            var image = decode(data);
            return image == null ? Optional.empty() : sample(image);
        }
    }

    private BufferedImage decode(byte[] data) throws IOException {
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) return null;
            var reader = readers.next();
            try {
                reader.setInput(stream);
                var width = reader.getWidth(0);
                var height = reader.getHeight(0);
                if (width != SKIN_SIZE || (height != SKIN_SIZE && height != LEGACY_HEIGHT)) return null;
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    private Optional<Palette> sample(BufferedImage image) {
        var torso = average(image, image.getHeight() == SKIN_SIZE ? TORSO_FULL : TORSO_LEGACY);
        var arms = average(image, ARM);
        var legs = average(image, LEG);
        var feet = average(image, FOOT);
        if (torso == null || arms == null || legs == null || feet == null) return Optional.empty();
        return Optional.of(new Palette(torso, arms, legs, feet));
    }

    private Color average(BufferedImage image, Region... regions) {
        long red = 0L;
        long green = 0L;
        long blue = 0L;
        long count = 0L;
        for (var region : regions) {
            for (int x = region.x(); x < region.x() + region.w(); x++) {
                for (int y = region.y(); y < region.y() + region.h(); y++) {
                    var argb = image.getRGB(x, y);
                    if ((argb >>> CHANNEL_SHIFT_ALPHA) == 0) continue;
                    red += (argb >> CHANNEL_SHIFT_RED) & CHANNEL_MASK;
                    green += (argb >> CHANNEL_SHIFT_GREEN) & CHANNEL_MASK;
                    blue += argb & CHANNEL_MASK;
                    count++;
                }
            }
        }
        if (count == 0L) return null;
        return Color.fromRGB((int) (red / count), (int) (green / count), (int) (blue / count));
    }

    private String key(String skinId) {
        if (skinId == null) return null;
        var trimmed = skinId.trim();
        if (HASH.matcher(trimmed).matches() || NAME.matcher(trimmed).matches()) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        return null;
    }

    private String url(String key) {
        if (HASH.matcher(key).matches()) {
            return plugin.getConfig().getString(CFG_TEXTURE_URL, DEFAULT_TEXTURE_URL).replace(HASH_PLACEHOLDER, key);
        }
        return plugin.getConfig().getString(CFG_NAME_URL, DEFAULT_NAME_URL).replace(NAME_PLACEHOLDER, key);
    }

    private long timeoutSeconds() {
        return Math.max(1L, plugin.getConfig().getLong(CFG_TIMEOUT, DEFAULT_TIMEOUT_SECONDS));
    }

    private long retryMillis() {
        return TimeUnit.MINUTES.toMillis(Math.max(0L, plugin.getConfig().getLong(CFG_RETRY, DEFAULT_RETRY_MINUTES)));
    }

    private synchronized HttpClient client() {
        if (client == null) {
            client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        }
        return client;
    }
}
