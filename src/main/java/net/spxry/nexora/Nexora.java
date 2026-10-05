package net.spxry.nexora;

import net.spxry.nexora.command.NexoraCommand;
import net.spxry.nexora.config.MessageManager;
import net.spxry.nexora.dialog.DialogMenus;
import net.spxry.nexora.edit.Schema;
import net.spxry.nexora.listener.InteractListener;
import net.spxry.nexora.nms.Packets;
import net.spxry.nexora.npc.ActionRunner;
import net.spxry.nexora.npc.SkinPalette;
import net.spxry.nexora.object.ObjectManager;
import net.spxry.nexora.object.Tracker;
import net.spxry.nexora.scheduler.FoliaScheduler;
import net.spxry.nexora.storage.ObjectStore;
import net.spxry.nexora.web.WebServer;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;

public final class Nexora extends JavaPlugin {
    private static Nexora instance;

    private FoliaScheduler scheduler;
    private MessageManager messages;
    private Schema schema;
    private ObjectStore store;
    private ObjectManager objects;
    private Tracker tracker;
    private ActionRunner actions;
    private SkinPalette skins;
    private DialogMenus menus;
    private WebServer web;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        saveIfMissing("messages.yml");
        saveIfMissing("editor.yml");
        scheduler = new FoliaScheduler(this);
        messages = new MessageManager(this);
        if (!Packets.init(this)) {
            getLogger().severe(messages.raw("startup.nms-failed"));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        schema = new Schema(this);
        store = new ObjectStore(this);
        objects = new ObjectManager(this);
        tracker = new Tracker(this);
        actions = new ActionRunner(this);
        skins = new SkinPalette(this);
        menus = new DialogMenus(this);
        web = new WebServer(this);

        var pm = getServer().getPluginManager();
        pm.registerEvents(tracker, this);
        pm.registerEvents(new InteractListener(this), this);
        var command = getCommand("nexora");
        if (command != null) {
            var executor = new NexoraCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        objects.loadAll();
        tracker.startAll();
        scheduler.runAsync(web::start);
    }

    @Override
    public void onDisable() {
        if (web != null) web.stop();
        if (tracker != null) tracker.stopAll();
        if (objects != null) objects.shutdown();
        if (store != null) store.shutdown();
        instance = null;
    }

    public void reloadAll() {
        reloadConfig();
        messages.reload();
        schema.reload();
        objects.reload();
        objects.restartAll();
    }

    private void saveIfMissing(String path) {
        if (!new File(getDataFolder(), path).exists()) saveResource(path, false);
    }

    public static Nexora get() { return instance; }

    public FoliaScheduler scheduler() { return scheduler; }

    public MessageManager messages() { return messages; }

    public Schema schema() { return schema; }

    public ObjectStore store() { return store; }

    public ObjectManager objects() { return objects; }

    public Tracker tracker() { return tracker; }

    public ActionRunner actions() { return actions; }

    public SkinPalette skins() { return skins; }

    public DialogMenus menus() { return menus; }

    public WebServer web() { return web; }
}
