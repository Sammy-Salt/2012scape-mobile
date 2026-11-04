package net.kdt.pojavlaunch;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

/** TEMPORARY developer helper: bundles the logs into one text file in the public Download folder. */
public final class DevLogExporter {
    private DevLogExporter() {}

    public static String build() {
        StringBuilder sb = new StringBuilder();
        sb.append("2012Scape Mobile log export\n");
        sb.append("time: ").append(new Date()).append('\n');
        sb.append("device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(", Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("game home: ").append(Tools.DIR_GAME_HOME).append("\n\n");

        File darkanHome = new File(Tools.DIR_GAME_HOME, ".darkanrs");
        sb.append("=== .darkanrs contents ===\n");
        describe(darkanHome, sb, 0, 3);

        File clientLogs = new File(darkanHome, "logs");
        File[] logs = clientLogs.listFiles();
        if (logs != null) {
            Arrays.sort(logs, Comparator.comparingLong(File::lastModified).reversed());
            for (int i = 0; i < Math.min(3, logs.length); i++) {
                section(sb, "client log " + logs[i].getName(), logs[i], 200 * 1024);
            }
        }
        File[] hs = darkanHome.listFiles((d, n) -> n.startsWith("hs_err_pid"));
        if (hs != null) for (File f : hs) section(sb, "JVM crash " + f.getName(), f, 100 * 1024);

        section(sb, "latestlog.txt (end of file)", new File(Tools.DIR_GAME_HOME, "latestlog.txt"), 400 * 1024);
        return sb.toString();
    }

    private static void section(StringBuilder sb, String title, File f, int maxBytes) {
        sb.append("\n=== ").append(title).append(" ===\n");
        if (!f.isFile()) {
            sb.append("(missing: ").append(f).append(")\n");
            return;
        }
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            long len = raf.length();
            long start = Math.max(0, len - maxBytes);
            raf.seek(start);
            byte[] buf = new byte[(int) (len - start)];
            raf.readFully(buf);
            if (start > 0) sb.append("(showing last ").append(buf.length).append(" of ").append(len).append(" bytes)\n");
            sb.append(new String(buf, StandardCharsets.UTF_8)).append('\n');
        } catch (IOException e) {
            sb.append("(unreadable: ").append(e).append(")\n");
        }
    }

    private static long size(File f) {
        if (f.isFile()) return f.length();
        long total = 0;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) total += size(k);
        return total;
    }

    private static void describe(File dir, StringBuilder sb, int depth, int maxDepth) {
        File[] kids = dir.listFiles();
        if (kids == null) {
            sb.append("(cannot list ").append(dir).append(")\n");
            return;
        }
        Arrays.sort(kids);
        for (File k : kids) {
            for (int i = 0; i < depth; i++) sb.append("  ");
            sb.append(k.getName()).append(k.isDirectory() ? "/" : "").append("  ")
                    .append(size(k) / 1024).append(" KB")
                    .append("  modified ").append(new Date(k.lastModified())).append('\n');
            if (k.isDirectory() && depth + 1 < maxDepth) describe(k, sb, depth + 1, maxDepth);
        }
    }

    /** Saves into the public Download folder. Returns a human-readable location. */
    public static String saveToDownloads(Context ctx, String name, String text) throws IOException {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver cr = ctx.getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IOException("Could not create a file in Downloads");
            try (OutputStream out = cr.openOutputStream(uri)) {
                if (out == null) throw new IOException("Could not open the new file");
                out.write(data);
            }
        } else {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
            try (OutputStream out = new FileOutputStream(new File(dir, name))) {
                out.write(data);
            }
        }
        return "Download/" + name;
    }
}
