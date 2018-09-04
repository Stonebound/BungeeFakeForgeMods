/*
 * Copyright 2018 phit. All rights reserved.
 * https://github.com/phit/BungeeFakeForgeMods
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 *
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list
 *       of conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ''AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE AUTHOR OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation are those of the
 * authors and contributors and should not be interpreted as representing official policies,
 * either expressed or implied, of anybody else.
 */
package net.stonebound.forgeserverlistping;

import ch.jamiete.mcping.MinecraftPing;
import ch.jamiete.mcping.MinecraftPingOptions;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.md_5.bungee.api.AbstractReconnectHandler;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.event.ProxyPingEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public final class BungeeFakeForgeMods extends Plugin {
    private Map<String, List<ServerPing.ModItem>> serverMods = new HashMap<>();

    @Override
    public void onEnable() {
        pingServers();
        getProxy().getPluginManager().registerListener(this, new Events());
        getProxy().getPluginManager().registerCommand(this, new RefreshCommand());
    }

    private void pingServers() {
        ProxyServer.getInstance().getScheduler().runAsync(this, () -> {
            ProxyServer.getInstance().getServers().values().forEach((server) -> {
                try {
                    String ping = new MinecraftPing().getPing(new MinecraftPingOptions().setHostname(server.getAddress().getHostName()).setPort
                            (server.getAddress().getPort()));

                    JsonParser parser = new JsonParser();
                    final JsonObject obj = parser.parse(ping).getAsJsonObject();
                    final JsonObject modinfo = obj.getAsJsonObject("modinfo");
                    final JsonArray modlist = modinfo.getAsJsonArray("modList");
                    int n = modlist.size();
                    List<ServerPing.ModItem> modItemList = new ArrayList<>();
                    for (int i = 0; i < n; ++i) {
                        JsonElement modElement = modlist.get(i);
                        JsonObject mod = modElement.getAsJsonObject();
                        String modid = mod.get("modid").getAsString();
                        String version = mod.get("version").getAsString();
                        modItemList.add(new ServerPing.ModItem(modid, version));
                    }
                    serverMods.put(server.getName(), modItemList);
                } catch (IOException ioe) {
                    getLogger().log(Level.WARNING, "Refreshing mod info failed!", ioe);
                }
            });
        });
    }

    public class Events implements Listener {
        @EventHandler(priority = EventPriority.LOW)
        public void onPing(ProxyPingEvent event) {
            PendingConnection con = event.getConnection();
            InetSocketAddress host = con.getVirtualHost();
            if (host != null) {
                ServerInfo forcedHost = AbstractReconnectHandler.getForcedHost(con);
                if (serverMods.get(forcedHost.getName()) != null) {
                    event.getResponse().getModinfo().setModList(serverMods.get(forcedHost.getName()));
                }
            }
        }
    }

    public class RefreshCommand extends Command {
        public RefreshCommand() {
            super("refreshmods", "fsp.command.refresh");
        }

        @Override
        public void execute(CommandSender commandSender, String[] strings) {
            commandSender.sendMessage(new ComponentBuilder("Refreshing stored server modlist!").create());
            serverMods.clear();
            pingServers();
        }
    }
}
