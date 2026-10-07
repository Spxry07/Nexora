package net.spxry.nexora.npc;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface TriggerHost {
    String kind();

    String id();

    double x();

    double y();

    double z();

    float facingYaw();

    Set<UUID> viewers();

    Location anchor();

    Vector anchorPoint(String anchor);

    boolean hostAction(String verb, List<String> args, Player player);
}
