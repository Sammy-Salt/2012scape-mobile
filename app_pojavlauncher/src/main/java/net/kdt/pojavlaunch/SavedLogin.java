package net.kdt.pojavlaunch;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** "Remember login": kept in a private file of this app only. The game wrapper reads it and types it into the login screen. */
public class SavedLogin {
    public static File file() {
        return new File(Tools.DIR_DATA, "darkan-login.txt");
    }

    public static String[] load() {
        try {
            File f = file();
            if (f.isFile()) {
                String[] l = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n", -1);
                if (l.length >= 3) return new String[]{l[0], l[1], l[2]};
            }
        } catch (Throwable ignored) {
        }
        return new String[]{"", "", "0"};
    }

    public static void save(String user, String pass, boolean autoSubmit) throws java.io.IOException {
        Files.write(file().toPath(), (user + "\n" + pass + "\n" + (autoSubmit ? "1" : "0")).getBytes(StandardCharsets.UTF_8));
    }

    public static void forget() {
        file().delete();
    }
}
