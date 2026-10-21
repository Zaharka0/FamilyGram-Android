package org.telegram.messenger;

/** Immutable settings shared by the UI, storage and network queues. */
public final class GhostModeSettings {
    public static final int ENABLED = 1;
    public static final int HIDE_READ = 1 << 1;
    public static final int HIDE_STORIES = 1 << 2;
    public static final int HIDE_ONLINE = 1 << 3;
    public static final int HIDE_TYPING = 1 << 4;
    public static final int RESTORE_OFFLINE = 1 << 5;
    public static final int DEFAULT_FLAGS = HIDE_READ | HIDE_STORIES | HIDE_ONLINE | HIDE_TYPING;
    private static final int ALL_FLAGS = DEFAULT_FLAGS | ENABLED | RESTORE_OFFLINE;

    public final int flags;

    public GhostModeSettings(int flags) {
        this.flags = flags & ALL_FLAGS;
    }

    public boolean selected(int option) {
        return (flags & option) != 0;
    }

    public boolean active(int option) {
        return selected(ENABLED) && selected(option);
    }

    public boolean restoreOffline() {
        return active(HIDE_ONLINE) && selected(RESTORE_OFFLINE);
    }

    public GhostModeSettings with(int option, boolean selected) {
        return new GhostModeSettings(selected ? flags | option : flags & ~option);
    }
}
