package gr.stei.lanannouncer;

import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;

/**
 * Repeatedly sends the vanilla "LAN world" announce packet, the same
 * multicast beacon a singleplayer world sends via "Open to LAN", so this
 * dedicated server also shows up under LAN worlds in the client's
 * multiplayer list.
 *
 * <p>The packet is sent out on every up, multicast-capable, non-loopback
 * interface rather than relying on the OS default route: on a host with
 * more than one NIC (e.g. a container with both a cluster and a LAN
 * interface) the default route often does not point at the LAN at all.
 */
final class LanBroadcastTask extends BukkitRunnable {

    private static final InetAddress GROUP;
    private static final int PORT = 4445;
    /** Vanilla broadcasts every 1500ms; 30 ticks matches that cadence. */
    static final long PERIOD_TICKS = 30L;

    static {
        try {
            GROUP = InetAddress.getByName("224.0.2.60");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final LanAnnouncer plugin;
    private final byte[] payload;
    private final MulticastSocket socket;

    LanBroadcastTask(LanAnnouncer plugin, String server, int serverPort) {
        this.plugin = plugin;
        String motd = server.replace("\n", " ").replace("[/MOTD]", "");
        String message = "[MOTD]" + motd + "[/MOTD][AD]" + serverPort + "[/AD]";
        this.payload = message.getBytes(StandardCharsets.UTF_8);

        try {
            this.socket = new MulticastSocket();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to open LAN announce socket", e);
        }
    }

    @Override
    public void run() {
        List<NetworkInterface> interfaces = usableInterfaces();
        if (interfaces.isEmpty()) {
            plugin.getLogger().warning("No up, multicast-capable network interface found to send the LAN announce packet on");
            return;
        }

        for (NetworkInterface iface : interfaces) {
            try {
                socket.setNetworkInterface(iface);
                DatagramPacket packet = new DatagramPacket(payload, payload.length, GROUP, PORT);
                socket.send(packet);
                plugin.getLogger().log(Level.FINE, "Sent LAN announce packet via " + iface.getName()
                        + " to " + GROUP.getHostAddress() + ":" + PORT);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to send LAN announce packet via " + iface.getName(), e);
            }
        }
    }

    private static List<NetworkInterface> usableInterfaces() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            return Collections.list(interfaces).stream()
                    .filter(LanBroadcastTask::isUsable)
                    .toList();
        } catch (SocketException e) {
            return List.of();
        }
    }

    private static boolean isUsable(NetworkInterface iface) {
        try {
            return iface.isUp() && iface.supportsMulticast() && !iface.isLoopback();
        } catch (SocketException e) {
            return false;
        }
    }

    void close() {
        socket.close();
    }
}
