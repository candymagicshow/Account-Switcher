# Account Switcher Plugin

ZenithProxy plugin for rotating offline usernames on a timer.

The plugin keeps a list of target usernames, disconnects when the interval is reached, switches `CONFIG.authentication` to offline mode, updates the username, saves config, and reconnects after a short delay.

## Commands

Use the `accountSwitcher` command inside ZenithProxy:

- `accountSwitcher`
- `accountSwitcher on`
- `accountSwitcher off`
- `accountSwitcher interval <minutes>`
- `accountSwitcher reconnectDelay <seconds>`
- `accountSwitcher queue on|off`
- `accountSwitcher add <username>`
- `accountSwitcher remove <username>`
- `accountSwitcher clear`
- `accountSwitcher list`
- `accountSwitcher now`

`accountSwitcher now` triggers an immediate switch to the next configured username.

## Config

The plugin stores its config in the plugin config file registered under the plugin id `account-switcher`.

Example shape:

```json
{
  "rotation": {
    "enabled": true,
    "intervalMinutes": 180,
    "reconnectDelaySeconds": 5,
    "rotateWhileInQueue": false,
    "accounts": [
      "pigman_button2",
      "pigman_button3",
      "pigman_button4"
    ]
  }
}
```

## Build

Run:

```powershell
.\gradlew.bat build
```

The plugin jar is written to `build/libs`.
