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

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class BungeeFakeForgeMods extends Plugin implements Listener {
    private Map<String, List<ServerPing.ModItem>> serverMods = new HashMap<>();

    @Override
    public void onEnable() {
        // 7 min delay for that one time of the week where bungee restarts and all servers at the same time
        ProxyServer.getInstance().getScheduler().schedule(this, () -> { updateServers(); }, 7L, TimeUnit.MINUTES);
        getProxy().getPluginManager().registerListener(this, this);
        getProxy().getPluginManager().registerCommand(this, new InvalidateCommand());
    }

    // runs at startup
    private void updateServers() {
        getProxy().getScheduler().runAsync(this, () -> {
            getProxy().getServers().entrySet().forEach((server) -> {
                if (server.getKey().equals("hub")) return;
                server.getValue().ping((result, error) -> {
                    if (error == null) {
                        serverMods.put(server.getKey(), result.getModinfo().getModList());
                    }
                });
            });
        });
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onProxyPing(ProxyPingEvent event) {
        PendingConnection connection = event.getConnection();
        InetSocketAddress host = connection.getVirtualHost();
        if (host != null) {
            ServerInfo forcedHost = AbstractReconnectHandler.getForcedHost(connection);
            if (forcedHost != null && forcedHost.getName().equals("hub")) {
                event.getResponse().getModinfo().setType("VANILLA");
            } else if (forcedHost != null) {
                if (serverMods.containsKey(forcedHost.getName())) {
                    event.getResponse().getModinfo().setModList(serverMods.get(forcedHost.getName()));
                }
            }
        }
    }

    public class InvalidateCommand extends Command {
        public InvalidateCommand() {
            super("invalidatemods", "bffm.command.refresh");
        }

        @Override
        public void execute(CommandSender commandSender, String[] strings) {
            commandSender.sendMessage(new ComponentBuilder("Invalidating all server modlists!").create());
            serverMods.clear();
            updateServers();
        }
    }
}
