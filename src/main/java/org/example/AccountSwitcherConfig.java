package org.example;

import java.util.ArrayList;

public class AccountSwitcherConfig {
    public final RotationConfig rotation = new RotationConfig();

    public static final class RotationConfig {
        public boolean enabled = false;
        public int intervalMinutes = 180;
        public int reconnectDelaySeconds = 5;
        public boolean rotateWhileInQueue = false;
        public final ArrayList<String> accounts = new ArrayList<>();
    }
}
