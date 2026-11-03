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
    private Button playHD;
    private Button playSD;
    private static final int FILE_SELECT_CODE_JSON = 0;
    private static final int FILE_SELECT_CODE_ZIP = 1;
    private ProgressServiceKeeper mProgressServiceKeeper;
    private ProgressLayout mProgressLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dummy_launcher);
        settings = findViewById(R.id.settings);
        playHD = findViewById(R.id.playHD);
        playSD = findViewById(R.id.playSD);
        mProgressLayout = findViewById(R.id.progress_layout);

        ProgressKeeper.addTaskCountListener((mProgressServiceKeeper = new ProgressServiceKeeper(this)));
        ProgressKeeper.addTaskCountListener(mProgressLayout);

        mProgressLayout.observe(ProgressLayout.UNPACK_RUNTIME);
        mProgressLayout.observe(ProgressLayout.INSTALL_MODPACK);

        playHD.setOnClickListener(view -> {
            if(!runtimeReady()) return;
            Intent intent = new Intent(ScapeLauncher.this, MainActivity.class);
            startActivity(intent);
        });

        playSD.setOnClickListener(view -> {
            if(!runtimeReady()) return;
            Intent intent = new Intent(ScapeLauncher.this, JavaGUILauncherActivity.class);
            startActivity(intent);
        });
        settings.setOnClickListener(view -> showBottomDialog());

        // TEMPORARY developer test (remove once the Darkan client launches): run hello.jar on Java 17.
        findViewById(R.id.devHello).setOnClickListener(view -> runHelloJava17());
        showLastHelloResult();
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