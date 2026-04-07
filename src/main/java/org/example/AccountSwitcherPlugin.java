package org.example;

import com.zenith.plugin.api.Plugin;
import com.zenith.plugin.api.PluginAPI;
import com.zenith.plugin.api.ZenithProxyPlugin;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.example.command.AccountSwitcherCommand;
import org.example.module.AccountSwitcherModule;

@Plugin(
    id = BuildConstants.PLUGIN_ID,
    version = BuildConstants.VERSION,
    description = "Periodically rotates offline usernames and reconnects the bot",
    url = "https://wiki.2b2t.vc/Commands/",
    authors = {"bryon"},
    mcVersions = {BuildConstants.MC_VERSION}
)
public class AccountSwitcherPlugin implements ZenithProxyPlugin {
    public static AccountSwitcherConfig PLUGIN_CONFIG;
    public static ComponentLogger LOG;

    @Override
    public void onLoad(final PluginAPI pluginAPI) {
        LOG = pluginAPI.getLogger();
        LOG.info("Account Switcher loading...");
        PLUGIN_CONFIG = pluginAPI.registerConfig(BuildConstants.PLUGIN_ID, AccountSwitcherConfig.class);
        pluginAPI.registerModule(new AccountSwitcherModule());
        pluginAPI.registerCommand(new AccountSwitcherCommand());
        LOG.info("Account Switcher loaded!");
    }
}
