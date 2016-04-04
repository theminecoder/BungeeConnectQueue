package me.theminecoder;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class BungeeConnectQueue extends Plugin implements Runnable, Listener {

    private final ListMultimap<String, UUID> playerQueues = ArrayListMultimap.create();

    @Override
    public void onEnable() {
        this.getProxy().getScheduler().schedule(this, this, 0, 500, TimeUnit.MILLISECONDS);
        this.getProxy().getPluginManager().registerListener(this, this);
    }

    @Override
    public void onDisable() {

    }

    @EventHandler
    public void onPluginMessage(final PluginMessageEvent event) {
        if (!(event.getSender() instanceof Server)) {
            return;
        }

        final Server server = (Server) event.getSender();
        ProxiedPlayer playerTemp = null;
        if (event.getReceiver() instanceof ProxiedPlayer) {
            playerTemp = (ProxiedPlayer) event.getReceiver();
        }
        final ProxiedPlayer player = playerTemp;

        if (player == null) {
            return;
        }

        //Check Cancel Channels
        if ("BungeeCord".equalsIgnoreCase(event.getTag())) {
            final byte[] data = Arrays.copyOf(event.getData(), event.getData().length);
            ByteArrayDataInput in = ByteStreams.newDataInput(data);
            String command;
            try {
                command = in.readUTF();
            } catch (IllegalStateException e) {
                e.printStackTrace();
                return;
            }

            if (!"Connect".equalsIgnoreCase(command)) {
                return;
            }

            if (player.hasPermission("bungeequeue.skip")) {
                return;
            }

            String serverTo;
            try {
                serverTo = in.readUTF();
            } catch (IllegalStateException e) {
                e.printStackTrace();
                return;
            }

            event.setCancelled(true);
            synchronized (playerQueues) {
                if (!playerQueues.containsEntry(serverTo, player.getUniqueId())) {
                    for (String serverToKey : new ArrayList<>(playerQueues.keys())) {
                        playerQueues.get(serverToKey).remove(player.getUniqueId());
                    }
                    playerQueues.put(serverTo, player.getUniqueId());
                    //this.getLogger().info("Queued "+player.getName()+" as #"+playerQueues.get(serverTo).size()+" heading to "+serverTo);
                }
            }
        }
    }

    @EventHandler
    public void onServerConnect(ServerConnectedEvent event) {
        synchronized (playerQueues) {
            for (String serverToKey : new ArrayList<>(playerQueues.keys())) {
                playerQueues.get(serverToKey).remove(event.getPlayer().getUniqueId());
            }
        }
    }

    @Override
    public void run() {
        synchronized (playerQueues) {
            List<String> serversToRemove = new ArrayList<>();
            server:
            for (String serverTo : new ArrayList<>(playerQueues.keySet())) {
                ServerInfo serverInfo;
                if ((serverInfo = this.getProxy().getServerInfo(serverTo)) == null) {
                    serversToRemove.add(serverTo);
                    continue;
                }

                ProxiedPlayer player;
                do {
                    if (this.playerQueues.get(serverTo).size() <= 0) {
                        break server;
                    }
                    player = this.getProxy().getPlayer(this.playerQueues.get(serverTo).get(0));
                    if (player == null) {
                        this.playerQueues.get(serverTo).remove(0);
                    }
                } while (player == null);

                player.connect(serverInfo);
                //this.getLogger().info("Sent "+player.getName()+" to "+serverTo+"!");
            }
            for (String server : serversToRemove) {
                playerQueues.removeAll(server);
            }
        }
    }
}
