package pl.mibac.dnsonlyjoin;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.event.PlayerHandshakeEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.event.ProxyPingEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Map;
import java.util.WeakHashMap;

public final class DnsOnlyJoin extends Plugin implements Listener {
    private final Map<PendingConnection, BaseComponent> rejectMap = new WeakHashMap<>();
    private String domainName;
    private String motdText;
    private String kickText;

    @Override
    public void onEnable() {
        super.onEnable();
        getProxy().getPluginManager().registerListener(this, this);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            boolean _success = configFile.getParentFile().mkdirs();
            try (InputStream in = getResourceAsStream("config.yml")) {
                Files.copy(in, configFile.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            Configuration config = ConfigurationProvider.getProvider(YamlConfiguration.class)
                                                        .load(new File(getDataFolder(), "config.yml"));
            domainName = config.getString("domain_name");
            motdText = ChatColor.translateAlternateColorCodes('&', config.getString("motd_text", ""));
            if (motdText.isBlank()) {
                motdText = null;
            }
            kickText = ChatColor.translateAlternateColorCodes('&', config.getString("kick_text"));
        } catch (IOException e) {
            getLogger().severe(String.format("Failed to load config: %s", e));
        }
    }

    @EventHandler
    public void onLogin(PreLoginEvent event) {
        if (event.isCancelled()) return;
        BaseComponent rejectReason = rejectMap.get(event.getConnection());
        if (rejectReason != null) {
            event.setCancelled(true);
            event.setCancelReason(rejectReason);
        }
    }

    @EventHandler
    public void onStatus(ProxyPingEvent event) {
        BaseComponent rejectReason = rejectMap.get(event.getConnection());
        if (rejectReason != null) {
            ServerPing originalResp = event.getResponse();
            originalResp.setDescriptionComponent(rejectReason);
            // In case BungeeCord ever starts copying the response before giving it out, make sure we still work
            event.setResponse(originalResp);
        }
    }

    @EventHandler
    public void onHandshake(PlayerHandshakeEvent event) {
        String usedHost = event.getHandshake().getHost();
        if (usedHost.equalsIgnoreCase(domainName)) return;

        if (event.getHandshake().getRequestedProtocol() == 1 /* status (motd) */) {
            if (motdText != null) {
                rejectMap.put(event.getConnection(), new TextComponent(String.format(motdText, domainName, usedHost)));
            }
        } else if (event.getHandshake().getRequestedProtocol() == 2 /* login */) {
            rejectMap.put(event.getConnection(), new TextComponent(String.format(kickText, domainName, usedHost)));
        } else {
            // As of 1.16.5 there's no other valid value for this field
            return;
        }

        getLogger().info(String.format("%s Tried to connect using address: `%s`, instead of: `%s`, disconnecting!", event.getConnection(), usedHost, domainName));
    }
}
