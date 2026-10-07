package net.spxry.nexora.gui;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.spxry.nexora.config.MessageManager;
import org.bukkit.inventory.ItemStack;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

final class Heads {
    private static final String PROPERTY_PATH = "heads.property";
    private static final String HASH_PATTERN_PATH = "heads.hash-pattern";
    private static final String URL_PATH = "heads.url";
    private static final String JSON_PATH = "heads.json";
    private static final String NAME_LENGTH_PATH = "heads.max-name-length";
    private static final int DEFAULT_NAME_LENGTH = 16;

    private Heads() {}

    static void apply(GuiConfig cfg, ItemStack stack, String skinId) {
        if (stack == null || skinId == null || skinId.isBlank()) return;
        var id = skinId.trim();
        var builder = ResolvableProfile.resolvableProfile();
        if (Pattern.matches(cfg.string(GuiConfig.COMMON, HASH_PATTERN_PATH, ""), id)) {
            var url = MessageManager.apply(cfg.string(GuiConfig.COMMON, URL_PATH, ""), Map.of("hash", id));
            var json = MessageManager.apply(cfg.string(GuiConfig.COMMON, JSON_PATH, ""), Map.of("url", url));
            var encoded = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
            builder.uuid(UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8)))
                .addProperty(new ProfileProperty(cfg.string(GuiConfig.COMMON, PROPERTY_PATH, ""), encoded));
        } else if (id.length() <= cfg.integer(GuiConfig.COMMON, NAME_LENGTH_PATH, DEFAULT_NAME_LENGTH)) {
            builder.name(id);
        } else {
            return;
        }
        stack.setData(DataComponentTypes.PROFILE, builder);
    }
}
