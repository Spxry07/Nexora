package net.spxry.nexora.util;

import org.bukkit.inventory.ItemStack;
import java.util.Base64;
import java.util.Optional;

public final class ItemCodec {
    private ItemCodec() {}

    public static String encode(ItemStack item) {
        if (item == null || item.isEmpty()) return null;
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    public static Optional<ItemStack> decode(String data) {
        if (data == null || data.isEmpty()) return Optional.empty();
        try {
            return Optional.of(ItemStack.deserializeBytes(Base64.getDecoder().decode(data)));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
