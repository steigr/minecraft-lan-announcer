package gr.stei.lanannouncer;

import org.bukkit.plugin.java.JavaPlugin;

public final class LanAnnouncer extends JavaPlugin {

    private LanBroadcastTask broadcastTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        startOrRestartBroadcast();
        getLogger().info("LanAnnouncer started.");
    }

    @Override
    public void onDisable() {
        stopBroadcast();
        getLogger().info("LanAnnouncer stopped.");
    }

    void startOrRestartBroadcast() {
        stopBroadcast();

        if (!getConfig().getBoolean("announce.enabled", true)) {
            getLogger().info("LAN announcement is disabled in config.yml.");
            return;
        }

        String server = getConfig().getString("announce.server", "");
        if (server == null || server.isBlank()) {
            server = getServer().getMotd();
        }

        broadcastTask = new LanBroadcastTask(this, server, getServer().getPort());
        broadcastTask.runTaskTimerAsynchronously(this, 0L, LanBroadcastTask.PERIOD_TICKS);
    }

    private void stopBroadcast() {
        if (broadcastTask != null) {
            broadcastTask.cancel();
            broadcastTask.close();
            broadcastTask = null;
        }
    }
}
