package org.example.module;

import com.github.rfresh2.EventConsumer;
import com.zenith.Proxy;
import com.zenith.event.client.ClientBotTick;
import com.zenith.event.client.ClientConnectEvent;
import com.zenith.event.client.ClientDisconnectEvent;
import com.zenith.module.api.Module;
import com.zenith.module.impl.AutoReconnect;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.EXECUTOR;
import static com.zenith.Globals.MODULE;
import static com.zenith.Globals.saveConfigAsync;
import static com.zenith.util.DisconnectMessages.SYSTEM_DISCONNECT;
import static com.zenith.util.config.Config.Authentication.AccountType.OFFLINE;
import static org.example.AccountSwitcherPlugin.PLUGIN_CONFIG;

public class AccountSwitcherModule extends Module {
    private RotationPhase phase = RotationPhase.IDLE;
    private long sessionStartedAtMs = 0L;
    private @Nullable String pendingUsername = null;
    private @Nullable Future<?> reconnectFuture = null;

    @Override
    public boolean enabledSetting() {
        return true;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ClientBotTick.class, this::handleBotTick),
            of(ClientConnectEvent.class, this::handleConnectEvent),
            of(ClientDisconnectEvent.class, this::handleDisconnectEvent)
        );
    }

    @Override
    public void onEnable() {
        if (Proxy.getInstance().isConnected()) {
            sessionStartedAtMs = System.currentTimeMillis();
        }
    }

    @Override
    public synchronized void onDisable() {
        cancelPendingReconnect();
        phase = RotationPhase.IDLE;
        pendingUsername = null;
        sessionStartedAtMs = 0L;
    }

    public synchronized RotationRequestResult startNextRotation(final String trigger) {
        if (phase != RotationPhase.IDLE) {
            return new RotationRequestResult(false, "A switch is already in progress.");
        }
        var nextUsername = resolveNextUsername();
        if (nextUsername == null) {
            return new RotationRequestResult(false, "Add at least one target account that is different from the current username.");
        }

        pendingUsername = nextUsername;
        Proxy.getInstance().cancelLogin();
        MODULE.get(AutoReconnect.class).cancelAutoReconnect();
        info("Starting account rotation from {} to {} ({})", CONFIG.authentication.username, nextUsername, trigger);

        if (Proxy.getInstance().isConnected()) {
            phase = RotationPhase.WAITING_FOR_DISCONNECT;
            Proxy.getInstance().disconnect(SYSTEM_DISCONNECT);
        } else {
            phase = RotationPhase.WAITING_FOR_RECONNECT;
            applyPendingUsername();
            scheduleReconnect();
        }

        return new RotationRequestResult(true, "Switching to `" + nextUsername + "`.");
    }

    public synchronized boolean switchInProgress() {
        return phase != RotationPhase.IDLE;
    }

    public synchronized @Nullable String getNextUsernamePreview() {
        return resolveNextUsername();
    }

    private void handleBotTick(final ClientBotTick event) {
        if (!PLUGIN_CONFIG.rotation.enabled) {
            return;
        }
        if (phase != RotationPhase.IDLE) {
            return;
        }
        if (PLUGIN_CONFIG.rotation.accounts.isEmpty()) {
            return;
        }
        if (!Proxy.getInstance().isConnected()) {
            return;
        }
        if (!PLUGIN_CONFIG.rotation.rotateWhileInQueue && Proxy.getInstance().isInQueue()) {
            return;
        }
        if (sessionStartedAtMs == 0L) {
            sessionStartedAtMs = System.currentTimeMillis();
            return;
        }
        var intervalMs = TimeUnit.MINUTES.toMillis(PLUGIN_CONFIG.rotation.intervalMinutes);
        if (System.currentTimeMillis() - sessionStartedAtMs < intervalMs) {
            return;
        }

        var result = startNextRotation("timer");
        if (!result.success()) {
            warn("Scheduled switch skipped: {}", result.message());
            sessionStartedAtMs = System.currentTimeMillis();
        }
    }

    private synchronized void handleConnectEvent(final ClientConnectEvent event) {
        cancelPendingReconnect();
        sessionStartedAtMs = System.currentTimeMillis();
        if (phase == RotationPhase.WAITING_FOR_RECONNECT && pendingUsername != null) {
            info("Connected with offline username {}", CONFIG.authentication.username);
            pendingUsername = null;
            phase = RotationPhase.IDLE;
        }
    }

    private synchronized void handleDisconnectEvent(final ClientDisconnectEvent event) {
        sessionStartedAtMs = 0L;
        if (phase == RotationPhase.WAITING_FOR_DISCONNECT) {
            applyPendingUsername();
            phase = RotationPhase.WAITING_FOR_RECONNECT;
            scheduleReconnect();
            return;
        }
        if (phase == RotationPhase.WAITING_FOR_RECONNECT) {
            warn("Reconnect attempt ended disconnected: {}", event.reason());
            cancelPendingReconnect();
            pendingUsername = null;
            phase = RotationPhase.IDLE;
        }
    }

    private synchronized void applyPendingUsername() {
        if (pendingUsername == null) {
            return;
        }
        CONFIG.authentication.accountType = OFFLINE;
        CONFIG.authentication.username = pendingUsername;
        saveConfigAsync();
        info("Applied offline username {}", pendingUsername);
    }

    private synchronized void scheduleReconnect() {
        cancelPendingReconnect();
        var delaySeconds = Math.max(0, PLUGIN_CONFIG.rotation.reconnectDelaySeconds);
        reconnectFuture = EXECUTOR.schedule(() -> {
            synchronized (this) {
                reconnectFuture = null;
                if (phase != RotationPhase.WAITING_FOR_RECONNECT || pendingUsername == null) {
                    return;
                }
            }
            info("Reconnecting with offline username {}", CONFIG.authentication.username);
            Proxy.getInstance().connectAndCatchExceptions();
        }, delaySeconds, TimeUnit.SECONDS);
    }

    private synchronized void cancelPendingReconnect() {
        if (reconnectFuture != null && !reconnectFuture.isDone()) {
            reconnectFuture.cancel(true);
        }
        reconnectFuture = null;
    }

    private synchronized @Nullable String resolveNextUsername() {
        var accounts = PLUGIN_CONFIG.rotation.accounts.stream()
            .map(String::trim)
            .filter(account -> !account.isEmpty())
            .toList();
        if (accounts.isEmpty()) {
            return null;
        }
        var currentUsername = CONFIG.authentication.username;
        var currentIndex = accounts.indexOf(currentUsername);
        if (currentIndex == -1) {
            return accounts.getFirst();
        }
        if (accounts.size() == 1) {
            return null;
        }
        return accounts.get((currentIndex + 1) % accounts.size());
    }

    public record RotationRequestResult(boolean success, String message) {}

    private enum RotationPhase {
        IDLE,
        WAITING_FOR_DISCONNECT,
        WAITING_FOR_RECONNECT
    }
}
