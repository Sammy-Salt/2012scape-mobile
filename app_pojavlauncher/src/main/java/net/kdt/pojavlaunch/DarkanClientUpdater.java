package net.kdt.pojavlaunch;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Downloads and verifies the official Darkan client jar from the GitLab package registry
 * (project 75976122, generic package "darkan-client", version "dev-latest").
 *
 * Installed layout: {darkanHome}/client/darkan-client-{sha12}.jar + client.json.
 * Manual override: put a jar named darkan-client-manual.jar in {darkanHome}/client/ and it is used as-is.
 */
public final class DarkanClientUpdater {
    public static final String LOBBY_HOST = "dev.darkan.org";
    public static final int LOBBY_PORT = 43594;

    private static final String API = "https://gitlab.com/api/v4/projects/75976122";
    private static final String PACKAGE_NAME = "darkan-client";
    private static final String PACKAGE_VERSION = "dev-latest";
    private static final String FILE_NAME = "darkan-client.jar";
    private static final String MANUAL_JAR = "darkan-client-manual.jar";

    private DarkanClientUpdater() {}

    /** Returns the jar to run, downloading or updating it first when possible. Call off the UI thread. */
    public static File ensureClient(File darkanHome) throws IOException {
        File clientDir = new File(darkanHome, "client");
        if (!clientDir.isDirectory() && !clientDir.mkdirs()) {
            throw new IOException("Cannot create " + clientDir);
        }

        File manual = new File(clientDir, MANUAL_JAR);
        if (manual.isFile()) {
            log("Using manually supplied client: " + manual);
            return manual;
        }

        File meta = new File(clientDir, "client.json");
        String installedSha = null;
        File installedJar = null;
        try {
            if (meta.isFile()) {
                JSONObject j = new JSONObject(readText(meta));
                installedSha = j.optString("sha256", null);
                File f = new File(clientDir, j.optString("file", ""));
                if (f.isFile()) installedJar = f;
            }
        } catch (Exception e) {
            log("client.json unreadable, ignoring: " + e);
        }

        try {
            log("Checking the Darkan client (" + PACKAGE_VERSION + ") on GitLab...");
            String remoteSha = fetchRemoteSha256();
            log("Remote client sha256: " + remoteSha);
            if (installedJar != null && remoteSha.equalsIgnoreCase(installedSha)) {
                log("Client is up to date: " + installedJar.getName());
                return installedJar;
            }
            return download(clientDir, meta, remoteSha);
        } catch (IOException | org.json.JSONException e) {
            log("Update check/download failed: " + e);
            if (installedJar != null) {
                log("Falling back to the installed client: " + installedJar.getName());
                return installedJar;
            }
            throw new IOException("Could not get the Darkan client and none is installed: " + e.getMessage(), e);
        }
    }

    private static String fetchRemoteSha256() throws IOException, org.json.JSONException {
        String listUrl = API + "/packages?package_type=generic&package_name=" + PACKAGE_NAME
                + "&package_version=" + PACKAGE_VERSION + "&order_by=created_at&sort=desc";
        JSONArray packages = new JSONArray(httpGetText(listUrl));
        long packageId = -1;
        for (int i = 0; i < packages.length(); i++) {
            JSONObject p = packages.getJSONObject(i);
            if (PACKAGE_VERSION.equals(p.optString("version"))) {
                packageId = p.getLong("id");
                break;
            }
        }
        if (packageId < 0) throw new IOException("Package " + PACKAGE_NAME + " " + PACKAGE_VERSION + " not found");

        JSONArray files = new JSONArray(httpGetText(API + "/packages/" + packageId + "/package_files?per_page=100"));
        String bestSha = null;
        long bestId = -1;
        for (int i = 0; i < files.length(); i++) {
            JSONObject f = files.getJSONObject(i);
            if (!FILE_NAME.equals(f.optString("file_name"))) continue;
            String sha = f.optString("file_sha256", "");
            if (sha.length() != 64) continue;
            long id = f.optLong("id", 0);
            if (id >= bestId) {
                bestId = id;
                bestSha = sha;
            }
        }
        if (bestSha == null) throw new IOException("No " + FILE_NAME + " with a sha256 in package " + packageId);
        return bestSha.toLowerCase(Locale.ROOT);
    }

    private static File download(File clientDir, File meta, String expectedSha) throws IOException {
        String url = API + "/packages/generic/" + PACKAGE_NAME + "/" + PACKAGE_VERSION + "/" + FILE_NAME;
        File part = new File(clientDir, "download.part");
        log("Downloading the client (about 95 MB)...");
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IOException(e);
        }

        HttpURLConnection c = open(url);
        try {
            long total = c.getContentLengthLong();
            long done = 0, nextLog = 10L * 1024 * 1024;
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(part)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    md.update(buf, 0, n);
                    done += n;
                    if (done >= nextLog) {
                        log("Downloaded " + (done / (1024 * 1024)) + " MB" + (total > 0 ? " of " + (total / (1024 * 1024)) + " MB" : ""));
                        nextLog += 10L * 1024 * 1024;
                    }
                }
            }
        } catch (IOException e) {
            part.delete();
            throw e;
        } finally {
            c.disconnect();
        }

        StringBuilder hex = new StringBuilder();
        for (byte b : md.digest()) hex.append(String.format(Locale.ROOT, "%02x", b));
        if (!hex.toString().equalsIgnoreCase(expectedSha)) {
            part.delete();
            throw new IOException("SHA-256 mismatch: expected " + expectedSha + " got " + hex);
        }
        log("SHA-256 verified.");

        File jar = new File(clientDir, "darkan-client-" + expectedSha.substring(0, 12) + ".jar");
        if (jar.exists() && !jar.delete()) throw new IOException("Cannot replace " + jar);
        if (!part.renameTo(jar)) throw new IOException("Cannot move download into place");

        try {
            JSONObject j = new JSONObject();
            j.put("file", jar.getName());
            j.put("sha256", expectedSha);
            j.put("version", PACKAGE_VERSION);
            j.put("installed_at", System.currentTimeMillis());
            try (OutputStream out = new FileOutputStream(meta)) {
                out.write(j.toString(2).getBytes(StandardCharsets.UTF_8));
            }
        } catch (org.json.JSONException e) {
            throw new IOException(e);
        }

        File[] old = clientDir.listFiles((d, name) -> name.startsWith("darkan-client-") && name.endsWith(".jar")
                && !name.equals(jar.getName()) && !name.equals(MANUAL_JAR));
        if (old != null) for (File f : old) f.delete();

        log("Installed " + jar.getName());
        return jar;
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "2012scape-mobile");
        int code = c.getResponseCode();
        if (code != 200) {
            c.disconnect();
            throw new IOException("HTTP " + code + " for " + url);
        }
        return c;
    }

    private static String httpGetText(String url) throws IOException {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream()) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
    }

    private static String readText(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void log(String s) {
        android.util.Log.i("DarkanClient", s);
        Logger.appendToLog("[Darkan] " + s);
    }
}
