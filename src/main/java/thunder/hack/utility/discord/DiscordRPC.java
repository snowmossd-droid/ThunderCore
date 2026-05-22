package vcore.utility.discord;

import com.sun.jna.Library;
import com.sun.jna.Native;

public interface DiscordRPC extends Library {
    DiscordRPC INSTANCE;

    static {
        DiscordRPC instance = null;
        try {
            String osName = System.getProperty("os.name").toLowerCase();
            String osArch = System.getProperty("os.arch").toLowerCase();
            
            boolean isWindows = osName.contains("win");
            boolean isMac = osName.contains("mac");
            boolean isLinuxX64 = osName.contains("linux") && (osArch.contains("amd64") || osArch.contains("x86_64"));
            boolean isLinuxArm = osName.contains("linux") && (osArch.contains("aarch64") || osArch.contains("arm"));
            
            String libName = "discord-rpc";
            
            if (isLinuxArm && !isWindows && !isMac) {
                System.out.println("[DiscordRPC] Android/ARM64 detected - Discord RPC disabled");
                instance = null;
            }
            else if (isWindows || isMac || isLinuxX64) {
                System.out.println("[DiscordRPC] Loading on " + osName + " " + osArch);
                instance = (DiscordRPC) Native.load(libName, DiscordRPC.class);
            }
            else {
                System.out.println("[DiscordRPC] Unsupported platform: " + osName + " " + osArch);
                instance = null;
            }
        } catch (Throwable e) {
            System.err.println("[DiscordRPC] Failed to load: " + e.getMessage());
            instance = null;
        }
        INSTANCE = instance;
    }

    void Discord_UpdateHandlers(DiscordEventHandlers var1);
    void Discord_UpdatePresence(DiscordRichPresence var1);
    void Discord_Respond(String var1, int var2);
    void Discord_Register(String var1, String var2);
    void Discord_Shutdown();
    void Discord_UpdateConnection();
    void Discord_RegisterSteamGame(String var1, String var2);
    void Discord_RunCallbacks();
    void Discord_Initialize(String var1, DiscordEventHandlers var2, boolean var3, String var4);
    void Discord_ClearPresence();
}
