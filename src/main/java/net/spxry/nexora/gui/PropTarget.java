package net.spxry.nexora.gui;

import org.bukkit.entity.Player;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

record PropTarget(
    String kind,
    Map<String, String> placeholders,
    Supplier<CompletableFuture<Map<String, String>>> values,
    Function<Map<String, String>, CompletableFuture<List<String>>> save,
    Consumer<Player> back) {}
