package org.example.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;
import org.example.module.AccountSwitcherModule;

import java.util.StringJoiner;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.CustomStringArgumentType.getString;
import static com.zenith.command.brigadier.CustomStringArgumentType.wordWithChars;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;
import static org.example.AccountSwitcherPlugin.PLUGIN_CONFIG;

public class AccountSwitcherCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("accountSwitcher")
            .category(CommandCategory.MANAGE)
            .description("""
                Periodically switches ZenithProxy to the next configured offline username.
                """)
            .usageLines(
                "",
                "on/off",
                "interval <minutes>",
                "reconnectDelay <seconds>",
                "queue on/off",
                "add <username>",
                "remove <username>",
                "clear",
                "list",
                "now"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("accountSwitcher")
            .executes(c -> {
                c.getSource().getEmbed()
                    .title("Account Switcher");
                return OK;
            })
            .then(argument("toggle", toggle()).executes(c -> {
                PLUGIN_CONFIG.rotation.enabled = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("Account Switcher " + toggleStrCaps(PLUGIN_CONFIG.rotation.enabled))
                    .primaryColor();
                return OK;
            }))
            .then(literal("interval").then(argument("minutes", integer(1)).executes(c -> {
                PLUGIN_CONFIG.rotation.intervalMinutes = getInteger(c, "minutes");
                c.getSource().getEmbed()
                    .title("Rotation Interval Set")
                    .description("Now switching every " + PLUGIN_CONFIG.rotation.intervalMinutes + " minute(s).")
                    .primaryColor();
                return OK;
            })))
            .then(literal("reconnectDelay").then(argument("seconds", integer(0, 300)).executes(c -> {
                PLUGIN_CONFIG.rotation.reconnectDelaySeconds = getInteger(c, "seconds");
                c.getSource().getEmbed()
                    .title("Reconnect Delay Set")
                    .description("Now waiting " + PLUGIN_CONFIG.rotation.reconnectDelaySeconds + " second(s) before reconnect.")
                    .primaryColor();
                return OK;
            })))
            .then(literal("queue").then(argument("toggle", toggle()).executes(c -> {
                PLUGIN_CONFIG.rotation.rotateWhileInQueue = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("Rotate In Queue " + toggleStrCaps(PLUGIN_CONFIG.rotation.rotateWhileInQueue))
                    .primaryColor();
                return OK;
            })))
            .then(literal("add").then(argument("username", wordWithChars()).executes(c -> {
                var username = getString(c, "username").trim();
                if (username.isBlank()) {
                    c.getSource().getEmbed()
                        .title("Invalid Username")
                        .errorColor();
                    return ERROR;
                }
                if (containsAccount(username)) {
                    c.getSource().getEmbed()
                        .title("Account Already Added")
                        .description(username)
                        .errorColor();
                    return ERROR;
                }
                PLUGIN_CONFIG.rotation.accounts.add(username);
                c.getSource().getEmbed()
                    .title("Account Added")
                    .description(username)
                    .primaryColor();
                return OK;
            })))
            .then(literal("remove").then(argument("username", wordWithChars()).executes(c -> {
                var username = getString(c, "username").trim();
                var removed = PLUGIN_CONFIG.rotation.accounts.removeIf(existing -> existing.equalsIgnoreCase(username));
                if (!removed) {
                    c.getSource().getEmbed()
                        .title("Account Not Found")
                        .description(username)
                        .errorColor();
                    return ERROR;
                }
                c.getSource().getEmbed()
                    .title("Account Removed")
                    .description(username)
                    .primaryColor();
                return OK;
            })))
            .then(literal("clear").executes(c -> {
                PLUGIN_CONFIG.rotation.accounts.clear();
                c.getSource().getEmbed()
                    .title("Accounts Cleared")
                    .primaryColor();
                return OK;
            }))
            .then(literal("list").executes(c -> {
                c.getSource().getEmbed()
                    .title("Account Switcher Accounts")
                    .primaryColor();
                return OK;
            }))
            .then(literal("now").executes(c -> {
                var result = MODULE.get(AccountSwitcherModule.class).startNextRotation("manual command");
                c.getSource().getEmbed()
                    .title(result.success() ? "Account Switch Started" : "Account Switch Blocked")
                    .description(result.message())
                    .primaryColor();
                if (!result.success()) {
                    c.getSource().getEmbed().errorColor();
                    return ERROR;
                }
                return OK;
            }));
    }

    @Override
    public void defaultEmbed(final Embed embed) {
        var module = MODULE.get(AccountSwitcherModule.class);
        embed
            .addField("Enabled", toggleStr(PLUGIN_CONFIG.rotation.enabled))
            .addField("Current Username", CONFIG.authentication.username)
            .addField("Next Username", orNotSet(module.getNextUsernamePreview()))
            .addField("Switch In Progress", toggleStr(module.switchInProgress()))
            .addField("Interval", PLUGIN_CONFIG.rotation.intervalMinutes + " minute(s)")
            .addField("Reconnect Delay", PLUGIN_CONFIG.rotation.reconnectDelaySeconds + " second(s)")
            .addField("Rotate In Queue", toggleStr(PLUGIN_CONFIG.rotation.rotateWhileInQueue))
            .addField("Accounts", formatAccounts())
            .primaryColor();
    }

    private boolean containsAccount(final String username) {
        return PLUGIN_CONFIG.rotation.accounts.stream().anyMatch(existing -> existing.equalsIgnoreCase(username));
    }

    private String formatAccounts() {
        if (PLUGIN_CONFIG.rotation.accounts.isEmpty()) {
            return "none";
        }
        var joiner = new StringJoiner(", ");
        for (var account : PLUGIN_CONFIG.rotation.accounts) {
            joiner.add(account);
        }
        return joiner.toString();
    }

    private String orNotSet(final String value) {
        return value == null ? "none" : value;
    }
}
