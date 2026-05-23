package thunder.hack.core;

import java.util.HashSet;
import java.util.Set;

public class InputBlocker {
    private static final Set<String> blockedOwners = new HashSet<>();

    public static void block(String owner) {
        synchronized (blockedOwners) {
            blockedOwners.add(owner);
        }
    }

    public static void unblock(String owner) {
        synchronized (blockedOwners) {
            blockedOwners.remove(owner);
        }
    }

    public static boolean isBlocked() {
        synchronized (blockedOwners) {
            return !blockedOwners.isEmpty();
        }
    }

    public static boolean isBlockedBy(String owner) {
        synchronized (blockedOwners) {
            return blockedOwners.contains(owner);
        }
    }

    public static void clear() {
        synchronized (blockedOwners) {
            blockedOwners.clear();
        }
    }
              }
