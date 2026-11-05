package net.kdt.pojavlaunch;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;
import android.util.Log;

import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.utils.JREUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.services.ProgressServiceKeeper;

public class ScapeLauncher extends BaseActivity {

    private TextView settings;
    private Button playOnline;
    private Button playOffline;
    private static final int FILE_SELECT_CODE_JSON = 0;
    private static final int FILE_SELECT_CODE_ZIP = 1;
    private ProgressServiceKeeper mProgressServiceKeeper;
    private ProgressLayout mProgressLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dummy_launcher);
        settings = findViewById(R.id.settings);
        playOnline = findViewById(R.id.playOnline);
        playOffline = findViewById(R.id.playOffline);
        mProgressLayout = findViewById(R.id.progress_layout);

        ProgressKeeper.addTaskCountListener((mProgressServiceKeeper = new ProgressServiceKeeper(this)));
        ProgressKeeper.addTaskCountListener(mProgressLayout);

        mProgressLayout.observe(ProgressLayout.UNPACK_RUNTIME);
        mProgressLayout.observe(ProgressLayout.INSTALL_MODPACK);

        playOnline.setOnClickListener(view -> {
            if(!runtimeReady()) return;
            Intent intent = new Intent(ScapeLauncher.this, JavaGUILauncherActivity.class);
            startActivity(intent);
        });

        // Single-player / offline mode is a later milestone.
        playOffline.setOnClickListener(view ->
                Toast.makeText(this, "Offline single-player is coming in a later version.", Toast.LENGTH_SHORT).show());
        settings.setOnClickListener(view -> showBottomDialog());
        findViewById(R.id.savedLogin).setOnClickListener(view -> showSavedLoginDialog());

        // TEMPORARY developer test (remove once the Darkan client launches): run hello.jar on Java 17.
        findViewById(R.id.devHello).setOnClickListener(view -> runHelloJava17());
        showLastHelloResult();
        findViewById(R.id.devExport).setOnClickListener(view -> exportLogs());
    }

    private void showSavedLoginDialog() {
        final String[] cur = SavedLogin.load();
        final android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        final android.widget.EditText user = new android.widget.EditText(this);
        user.setHint("Username or email");
        user.setSingleLine(true);
        user.setText(cur[0]);
        final android.widget.EditText pass = new android.widget.EditText(this);
        pass.setHint("Password");
        pass.setSingleLine(true);
        pass.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pass.setText(cur[1]);
        final android.widget.CheckBox auto = new android.widget.CheckBox(this);
        auto.setText("Press Log In for me");
        auto.setChecked("1".equals(cur[2]));
        box.addView(user);
        box.addView(pass);
        box.addView(auto);
        new AlertDialog.Builder(this)
                .setTitle("Remember login")
                .setMessage("Saved only inside this app on your phone. It is typed into the game's login screen when it appears.")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    try {
                        if (user.getText().length() == 0) SavedLogin.forget();
                        else SavedLogin.save(user.getText().toString(), pass.getText().toString(), auto.isChecked());
                        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "Could not save: " + e, Toast.LENGTH_LONG).show();
                    }
                })
                .setNeutralButton("Forget", (d, w) -> {
                    SavedLogin.forget();
                    Toast.makeText(this, "Login forgotten", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** TEMPORARY: save logs and a folder listing to the public Download folder. */
    private void exportLogs() {
        new Thread(() -> {
            try {
                String name = "2012scape-log-"
                        + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(new java.util.Date())
                        + ".txt";
                String where = DevLogExporter.saveToDownloads(this, name, DevLogExporter.build());
                runOnUiThread(() -> Toast.makeText(this, "Saved to " + where, Toast.LENGTH_LONG).show());
            } catch (Throwable t) {
                Log.e("DarkanDev", "Log export failed", t);
                runOnUiThread(() -> Toast.makeText(this, "Export failed: " + t, Toast.LENGTH_LONG).show());
            }
        }, "DarkanExport").start();
    }

    private File helloResultFile() {
        return new File(Tools.DIR_DATA, "hello-java17.txt");
    }

    /** Shows the result written by hello.jar on a previous run (the JVM ends the process when it exits). */
    private void showLastHelloResult() {
        File f = helloResultFile();
        if (!f.isFile()) return;
        try {
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            new AlertDialog.Builder(this)
                    .setTitle("Last Java 17 test result")
                    .setMessage(text)
                    .setPositiveButton("OK", null)
                    .setNeutralButton("Clear", (d, w) -> f.delete())
                    .show();
        } catch (Exception e) {
            Log.e("DarkanDev", "Could not read hello result", e);
        }
    }

    private void runHelloJava17() {
        if (!runtimeReady()) return;
        final String tag = "DarkanDev";
        Runtime rt = MultiRTUtils.forceReread("Internal-17");
        Log.i(tag, "Internal-17 versionString=" + rt.versionString + " javaVersion=" + rt.javaVersion + " arch=" + rt.arch);
        if (rt.versionString == null) {
            Log.e(tag, "Internal-17 runtime is NOT installed");
            Toast.makeText(this, "Internal-17 runtime NOT installed (see logcat tag DarkanDev)", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "Starting Java " + rt.javaVersion + " test...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                Tools.copyAssetFile(this, "dev/hello.jar", Tools.DIR_DATA, true);
                helloResultFile().delete();
                JREUtils.redirectAndPrintJRELog();
                List<String> args = new ArrayList<>();
                args.add("-Djava.awt.headless=true");
                args.add("-jar");
                args.add(Tools.DIR_DATA + "/hello.jar");
                args.add(helloResultFile().getAbsolutePath());
                Log.i(tag, "Launching hello.jar: " + args);
                int code = JREUtils.launchJavaVM(this, rt, new File(Tools.DIR_DATA), args, "");
                Log.i(tag, "hello.jar finished, exit code " + code);
            } catch (Throwable t) {
                Log.e(tag, "hello.jar launch failed", t);
                runOnUiThread(() -> Tools.showError(this, t, false));
            }
        }, "DarkanHello").start();
    }


    private boolean runtimeReady(){
        if(mProgressLayout.hasProcesses()){
            Toast.makeText(this, R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showBottomDialog() {
        MyDialogFragment dialog = new MyDialogFragment();
        dialog.show(getSupportFragmentManager(), "tag");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ProgressKeeper.removeTaskCountListener(mProgressServiceKeeper);
    }
}