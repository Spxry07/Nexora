package net.spxry.nexora.nms;

import com.mojang.datafixers.util.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public final class Packets {
    private static final String INTERPOLATION_START_FIELD = "DATA_TRANSFORMATION_INTERPOLATION_START_DELTA_TICKS_ID";
    private static final float ANGLE_SCALE = 256.0F / 360.0F;

    private static EntityDataAccessor<Integer> interpolationStart;
    private static Scoreboard scoreboard;

    private Packets() {}

    @SuppressWarnings("unchecked")
    public static boolean init(Plugin plugin) {
        try {
            var field = Display.class.getDeclaredField(INTERPOLATION_START_FIELD);
            field.setAccessible(true);
            interpolationStart = (EntityDataAccessor<Integer>) field.get(null);
            scoreboard = new Scoreboard();
            return true;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, plugin.getName(), t);
            return false;
        }
    }

    public static Entity handle(org.bukkit.entity.Entity entity) {
        return ((CraftEntity) entity).getHandle();
    }

    public static Packet<?> spawn(Entity handle, double x, double y, double z, float yaw, float pitch) {
        return new ClientboundAddEntityPacket(handle.getId(), handle.getUUID(), x, y, z, pitch, yaw, handle.getType(), 0, Vec3.ZERO, yaw);
    }

    public static Packet<?> fullData(Entity handle) {
        var values = handle.getEntityData().getNonDefaultValues();
        handle.getEntityData().packDirty();
        return values == null || values.isEmpty() ? null : new ClientboundSetEntityDataPacket(handle.getId(), values);
    }

    public static Packet<?> dirtyData(Entity handle, boolean restartInterpolation) {
        var dirty = handle.getEntityData().packDirty();
        List<SynchedEntityData.DataValue<?>> values = dirty == null ? new ArrayList<>() : new ArrayList<>(dirty);
        if (restartInterpolation && handle instanceof Display) values.add(SynchedEntityData.DataValue.create(interpolationStart, 0));
        return values.isEmpty() ? null : new ClientboundSetEntityDataPacket(handle.getId(), values);
    }

    public static Packet<?> teleport(int id, double x, double y, double z, float yaw, float pitch) {
        return ClientboundTeleportEntityPacket.teleport(id, new PositionMoveRotation(new Vec3(x, y, z), Vec3.ZERO, yaw, pitch), Set.of(), false);
    }

    public static Packet<?> rotation(int id, float yaw, float pitch) {
        return new ClientboundMoveEntityPacket.Rot(id, angle(yaw), angle(pitch), false);
    }

    public static Packet<?> head(Entity handle, float yaw) {
        return new ClientboundRotateHeadPacket(handle, angle(yaw));
    }

    public static Packet<?> swing(Entity handle, boolean offHand) {
        return new ClientboundAnimatePacket(handle, offHand ? ClientboundAnimatePacket.SWING_OFF_HAND : ClientboundAnimatePacket.SWING_MAIN_HAND);
    }

    public static Packet<?> hurt(int id, float yaw) {
        return new ClientboundHurtAnimationPacket(id, yaw);
    }

    public static Packet<?> equipment(int id, Map<EquipmentSlot, ItemStack> items) {
        List<Pair<net.minecraft.world.entity.EquipmentSlot, net.minecraft.world.item.ItemStack>> list = new ArrayList<>();
        for (var entry : items.entrySet()) {
            var slot = slot(entry.getKey());
            if (slot != null) list.add(Pair.of(slot, CraftItemStack.asNMSCopy(entry.getValue())));
        }
        return list.isEmpty() ? null : new ClientboundSetEquipmentPacket(id, list);
    }

    public static Packet<?> attributes(Entity handle) {
        if (!(handle instanceof LivingEntity living)) return null;
        var attributes = living.getAttributes().getSyncableAttributes();
        return attributes.isEmpty() ? null : new ClientboundUpdateAttributesPacket(handle.getId(), attributes);
    }

    public static Packet<?> teamCreate(String name, String entry, String color) {
        var team = new PlayerTeam(scoreboard, name);
        var format = ChatFormatting.getByName(color);
        team.setColor(format == null || !format.isColor() ? ChatFormatting.WHITE : format);
        team.setCollisionRule(Team.CollisionRule.NEVER);
        team.setNameTagVisibility(Team.Visibility.ALWAYS);
        team.getPlayers().add(entry);
        return ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true);
    }

    public static Packet<?> teamRemove(String name) {
        return ClientboundSetPlayerTeamPacket.createRemovePacket(new PlayerTeam(scoreboard, name));
    }

    public static Packet<?> destroy(int... ids) {
        return new ClientboundRemoveEntitiesPacket(ids);
    }

    @SuppressWarnings("unchecked")
    public static Packet<?> bundle(List<Packet<?>> packets) {
        List<Packet<? super ClientGamePacketListener>> list = new ArrayList<>(packets.size());
        for (var packet : packets) if (packet != null) list.add((Packet<? super ClientGamePacketListener>) packet);
        return new ClientboundBundlePacket(list);
    }

    public static void send(Player player, Packet<?> packet) {
        if (packet == null) return;
        var connection = ((CraftPlayer) player).getHandle().connection;
        if (connection != null) connection.send(packet);
    }

    private static byte angle(float degrees) {
        return (byte) Math.floor(degrees * ANGLE_SCALE);
    }

    private static net.minecraft.world.entity.EquipmentSlot slot(EquipmentSlot slot) {
        return switch (slot) {
            case HAND -> net.minecraft.world.entity.EquipmentSlot.MAINHAND;
            case OFF_HAND -> net.minecraft.world.entity.EquipmentSlot.OFFHAND;
            case HEAD -> net.minecraft.world.entity.EquipmentSlot.HEAD;
            case CHEST -> net.minecraft.world.entity.EquipmentSlot.CHEST;
            case LEGS -> net.minecraft.world.entity.EquipmentSlot.LEGS;
            case FEET -> net.minecraft.world.entity.EquipmentSlot.FEET;
            default -> null;
        };
    }
}
