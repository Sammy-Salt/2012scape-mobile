package net.kdt.pojavlaunch;

import static net.kdt.pojavlaunch.MainActivity.fullyExit;
import static net.kdt.pojavlaunch.Tools.currentDisplayMetrics;

import static java.security.AccessController.getContext;

import android.annotation.SuppressLint;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;

import com.kdt.LoggerView;

import net.kdt.pojavlaunch.customcontrols.keyboard.AwtCharSender;
import net.kdt.pojavlaunch.customcontrols.keyboard.TouchCharInput;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.JREUtils;
import net.kdt.pojavlaunch.utils.KeyEncoder;
import net.kdt.pojavlaunch.utils.MathUtils;

import org.lwjgl.glfw.CallbackBridge;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JavaGUILauncherActivity extends BaseActivity implements View.OnTouchListener {

    private static final String DARKAN_RUNTIME_NAME = "Internal-17";

    private AWTCanvasView mTextureView;
    private LoggerView mLoggerView;
    private TouchCharInput mTouchCharInput;

    private LinearLayout mTouchPad;
    private ImageView mMousePointerImageView;
    private GestureDetector mGestureDetector;

    private GestureDetector longPressDetector;
    private boolean cameraMode = false;
    float prevX = 0, prevY = 0;
    private long lastPress = 0;
    private ScaleGestureDetector scaleGestureDetector;
    private boolean rcState = false;
    private boolean mSkipDetectMod;
    private static boolean mIsVirtualMouseEnabled;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_java_gui_launcher);

        try {
            File latestLogFile = new File(Tools.DIR_GAME_HOME, "latestlog.txt");
            if (!latestLogFile.exists() && !latestLogFile.createNewFile())
                throw new IOException("Failed to create a new log file");
            Logger.begin(latestLogFile.getAbsolutePath());
        }catch (IOException e) {
            Tools.showError(this, e, true);
        }
        MainActivity.GLOBAL_CLIPBOARD = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        mTouchCharInput = findViewById(R.id.awt_touch_char);
        mTouchCharInput.setCharacterSender(new AwtCharSender());

        findViewById(R.id.mouseMode).setOnTouchListener(this);
        findViewById(R.id.keyboard).setOnTouchListener(this);
        findViewById(R.id.camera).setOnTouchListener(this);
        findViewById(R.id.mb2).setOnTouchListener(this);

        mTouchPad = findViewById(R.id.main_touchpad);
        mLoggerView = findViewById(R.id.launcherLoggerView);
        mMousePointerImageView = findViewById(R.id.main_mouse_pointer);
        mTextureView = findViewById(R.id.installmod_surfaceview);
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener());
        mGestureDetector = new GestureDetector(this, new SingleTapConfirm());
        mTouchPad.setFocusable(false);
        mTouchPad.setVisibility(View.GONE);

        mMousePointerImageView.post(() -> {
            ViewGroup.LayoutParams params = mMousePointerImageView.getLayoutParams();
            params.width = (int) (36 / 100f * LauncherPreferences.PREF_MOUSESCALE);
            params.height = (int) (54 / 100f * LauncherPreferences.PREF_MOUSESCALE);
            if(LauncherPreferences.PREF_VIRTUAL_MOUSE_START)
                toggleVirtualMouse();
        });

        mTouchPad.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // MotionEvent reports input details from the touch screen
                // and other input controls. In this case, you are only
                // interested in events where the touch position changed.
                // int index = event.getActionIndex();
                int action = event.getActionMasked();
                float mouseSpeed = LauncherPreferences.PREF_MOUSESPEED;

                float x = event.getX();
                float y = event.getY();
                float mouseX, mouseY;

                // Scale the mouse speed
                mouseX = mMousePointerImageView.getX();
                mouseY = mMousePointerImageView.getY();

                if (mGestureDetector.onTouchEvent(event)) {
                    sendScaledMousePosition(mouseX,mouseY);
                    clickMouse();
                } else {
                    if (action == MotionEvent.ACTION_MOVE) { // 2
                        mouseX = Math.max(0, Math.min(currentDisplayMetrics.widthPixels, mouseX + (x - prevX) * mouseSpeed));
                        mouseY = Math.max(0, Math.min(currentDisplayMetrics.heightPixels, mouseY + (y - prevY) * mouseSpeed));
                        placeMouseAt(mouseX, mouseY);
                        sendScaledMousePosition(mouseX, mouseY);
                    }
                    // Check if there are two fingers on the screen
                    if (action == MotionEvent.ACTION_POINTER_DOWN) {
                        if (event.getPointerCount() == 2) {
                            // Right-click when a second finger touches the screen
                            tapMouse(AWTInputEvent.BUTTON3_MASK);
                        }
                    }
                }

                prevY = y;
                prevX = x;
                return true;
            }
        });

        mTextureView.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() != MotionEvent.ACTION_MOVE && touchLogCount++ < 60)
                Logger.appendToLog("[Touch] texture action=" + event.getActionMasked() + " x=" + event.getX() + " y=" + event.getY());
            scaleGestureDetector.onTouchEvent(event);
            longPressDetector.onTouchEvent(event);
            float x = event.getX();
            float y = event.getY();
            if (mGestureDetector.onTouchEvent(event)) {
                sendScaledMousePosition(x + mTextureView.getX(), y);
                clickMouse();
                return true;
            }

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_UP: // 1
                case MotionEvent.ACTION_CANCEL: // 3
                case MotionEvent.ACTION_POINTER_UP: // 6
                    break;
                case MotionEvent.ACTION_MOVE: // 2
                    sendScaledMousePosition(x + mTextureView.getX(), y);
                    try {
                        panCamera(prevX-x, prevY-y);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    break;
            }

            prevY = y;
            prevX = x;
            return true;
        });

        longPressDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public void onLongPress(MotionEvent e) {
                // Right click at the pressed position (a real right mouse button, not the 2009 client's F11 trick)
                sendScaledMousePosition(e.getX() + mTextureView.getX(), e.getY());
                tapMouse(AWTInputEvent.BUTTON3_MASK);
                super.onLongPress(e);
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                return super.onSingleTapUp(e);
            }
        });

        try {

            placeMouseAt(CallbackBridge.physicalWidth / 2f, CallbackBridge.physicalHeight / 2f);

            String jreName = DARKAN_RUNTIME_NAME;
            final Runtime runtime = MultiRTUtils.forceReread(jreName);
            if (runtime.versionString == null) {
                throw new IllegalStateException("Java 17 runtime is not installed: " + jreName);
            }

            mSkipDetectMod = false;
            if (mSkipDetectMod) {
                new Thread(() -> launchJavaRuntime(runtime, ""), "JREMainThread").start();
                return;
            }

            // No skip detection
            openLogOutput(null);
            new Thread(() -> {
                try {
                    final int exit = launchJavaRuntime(runtime, "");
                    Logger.appendToLog(getString(R.string.toast_optifine_success));
                    if (exit != 0) return;
                    runOnUiThread(() -> {
                        Toast.makeText(JavaGUILauncherActivity.this, R.string.toast_optifine_success, Toast.LENGTH_SHORT).show();
                        fullyExit();
                    });

                } catch (Throwable e) {
                    Logger.appendToLog("Install failed:");
                    Logger.appendToLog(Log.getStackTraceString(e));
                    Tools.showError(JavaGUILauncherActivity.this, e);
                }
            }, "Installer").start();
        } catch (Throwable th) {
            Tools.showError(this, th, true);
        }
    }

    private void panCamera(float dx, float dy) throws InterruptedException {
        //Log.i("downthecrop-pan","dx: " +dx + " dy: " + dy);
        final float threshold = 8.0f; // adjust this value as needed to control the sensitivity of the panning

        // Check horizontal panning
        if(dx > threshold) {
            // Finger moved to the right, pan camera to the right
            AWTInputBridge.sendKey((char)AWTInputEvent.VK_RIGHT, AWTInputEvent.VK_RIGHT);
        } else if(dx < -threshold) {
            AWTInputBridge.sendKey((char)AWTInputEvent.VK_LEFT, AWTInputEvent.VK_LEFT);
        }

        // Check vertical panning
        if(dy > threshold) {
            // Finger moved down, pan camera up
            AWTInputBridge.sendKey((char)AWTInputEvent.VK_UP, AWTInputEvent.VK_UP);
        } else if(dy < -threshold) {
            // Finger moved up, pan camera down
            AWTInputBridge.sendKey((char)AWTInputEvent.VK_DOWN, AWTInputEvent.VK_DOWN);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        final int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        final View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(uiOptions);
    }

    public static class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            if (scaleFactor > 1) { //Send F4 To Zoom Out
                AWTInputBridge.sendKey((char)AWTInputEvent.VK_F3, AWTInputEvent.VK_F3);
            } else { //116 F3 To Zoom In
                AWTInputBridge.sendKey((char)AWTInputEvent.VK_F4,AWTInputEvent.VK_F4);
            }
            return true;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {

        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent e) { // these AWTInputEvent doesn't work for some reason
        long time = System.currentTimeMillis();
        if (time > lastPress + 500) {
            switch (v.getId()) {
                case R.id.keyboard:
                    toggleKeyboard(this.getCurrentFocus());
                    break;
                case R.id.mb2:
                    if (!rcState) {
                        activateRC(); // Send F11 to activate RightClick
                    } else {
                        clearRC(); // Send F10 to clear RightClick
                    }
                    break;
                case R.id.camera:
                    if (!cameraMode) { // Camera Mode On
                        AWTInputBridge.sendKey((char) AWTInputEvent.VK_F9, AWTInputEvent.VK_F9); // Send F9
                        cameraMode = true;
                        findViewById(R.id.camera).setBackground(getResources().getDrawable( R.drawable.control_button_pressed ));
                    } else { // Camera Mode off
                        AWTInputBridge.sendKey((char) AWTInputEvent.VK_F8, AWTInputEvent.VK_F8);
                        cameraMode = false;
                        findViewById(R.id.camera).setBackground(getResources().getDrawable( R.drawable.control_button_normal ));
                    }
                    break;
                case R.id.mouseMode:
                    toggleVirtualMouse();
            }
            lastPress = time;
        }
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if(event.getAction() == KeyEvent.ACTION_DOWN){
            KeyEncoder.sendEncodedChar(event.getKeyCode(),(char)event.getUnicodeChar());
        }
        return true;
    }

    public void placeMouseAt(float x, float y) {
        mMousePointerImageView.setX(x);
        mMousePointerImageView.setY(y);
    }

    /** Left click, or one right click if the on-screen right-click button was armed. */
    private int touchLogCount = 0;
    private final java.util.concurrent.ExecutorService mClickExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();

    /** Press, hold and release a mouse button. The Darkan client samples button state once per frame,
     *  so a press+release in the same millisecond is never seen as a click. */
    private void tapMouse(final int downMask) {
        mClickExecutor.execute(() -> {
            try {
                Thread.sleep(60);
                AWTInputBridge.sendMousePress(downMask, true);
                Thread.sleep(130);
                AWTInputBridge.sendMousePress(downMask, false);
            } catch (InterruptedException ignored) {
            }
        });
    }

    private void clickMouse() {
        Logger.appendToLog("[Touch] click button=" + (rcState ? "right" : "left"));
        tapMouse(rcState ? AWTInputEvent.BUTTON3_MASK : AWTInputEvent.BUTTON1_DOWN_MASK);
        if (rcState) clearRC();
    }

    private void clearRC(){
        rcState = false;
        runOnUiThread(() -> findViewById(R.id.mb2).setBackground(getResources().getDrawable( R.drawable.control_button_normal )));
    }

    private void activateRC(){
        rcState = true;
        findViewById(R.id.mb2).setBackground(getResources().getDrawable( R.drawable.control_button_pressed ));
    }

    @SuppressWarnings("SuspiciousNameCombination")
    void sendScaledMousePosition(float x, float y){
        // Clamp positions to the borders of the usable view, then scale them
        x = androidx.core.math.MathUtils.clamp(x, mTextureView.getX(), mTextureView.getX() + mTextureView.getWidth());
        y = androidx.core.math.MathUtils.clamp(y, mTextureView.getY(), mTextureView.getY() + mTextureView.getHeight());

        AWTInputBridge.sendMousePos(
                (int) MathUtils.map(x, mTextureView.getX(), mTextureView.getX() + mTextureView.getWidth(), 0, AWTCanvasView.AWT_CANVAS_WIDTH),
                (int) MathUtils.map(y, mTextureView.getY(), mTextureView.getY() + mTextureView.getHeight(), 0, AWTCanvasView.AWT_CANVAS_HEIGHT)
                );
    }

    public void openLogOutput(View v) {
        mLoggerView.setVisibility(View.VISIBLE);
    }

    public void toggleVirtualMouse() {
        mIsVirtualMouseEnabled = !mIsVirtualMouseEnabled;
        ImageView view = findViewById(R.id.mouseModeIco);
        if(!mIsVirtualMouseEnabled){
            view.setImageResource(R.drawable.touch);
        } else{
            view.setImageResource(R.drawable.ic_mouse3);
        }
        mTouchPad.setVisibility(mIsVirtualMouseEnabled ? View.VISIBLE : View.GONE);
        Toast.makeText(this,
                mIsVirtualMouseEnabled ? R.string.control_mouseon : R.string.control_mouseoff,
                Toast.LENGTH_SHORT).show();
    }

    public int launchJavaRuntime(Runtime runtime, String javaArgs) {
        JREUtils.redirectAndPrintJRELog();
        try {
            if (runtime == null || runtime.versionString == null) {
                throw new IllegalStateException("Selected Java runtime is not installed");
            }

            List<String> javaArgList = new ArrayList<>();
            File gamedir = new File(Tools.DIR_DATA);

            // Enable Caciocavallo
            Tools.getCacioJavaArgs(javaArgList, false);
            Logger.appendToLog("Info: Selected Java runtime: " + runtime.name
                    + " (Java " + runtime.javaVersion + ")");
            File darkanHome = new File(Tools.DIR_GAME_HOME, ".darkanrs");
            if (!darkanHome.isDirectory() && !darkanHome.mkdirs()) {
                throw new IllegalStateException("Cannot create " + darkanHome);
            }
            File clientJar = DarkanClientUpdater.ensureClient(darkanHome);

            // Safe (software) renderer: toolkit 0 needs no native OpenGL library.
            javaArgList.add("-Xms256m");
            javaArgList.add("-Xmx1024m");
            javaArgList.add("-Dbot.enabled=false");
            javaArgList.add("-Dtoolkit=0");
            // The client bundles glibc LWJGL natives that cannot load on Android (libpthread.so.0), and LWJGL
            // always prefers a copy found on the classpath. So put Pojav's bionic builds in a folder laid out
            // like the jar's resources and place that folder BEFORE the client jar on the classpath.
            // Must be in the app's private storage: Android refuses to load native code from shared storage.
            File nativeCp = new File(Tools.DIR_DATA, "androidnatives");
            String[][] natives = {
                    {"liblwjgl.so", "linux/arm64/org/lwjgl/liblwjgl.so"},
                    {"liblwjgl_opengl.so", "linux/arm64/org/lwjgl/opengl/liblwjgl_opengl.so"},
                    {"liblwjgl_stb.so", "linux/arm64/org/lwjgl/stb/liblwjgl_stb.so"},
            };
            for (String[] n : natives) {
                File src = new File(Tools.NATIVE_LIB_DIR, n[0]);
                File dst = new File(nativeCp, n[1]);
                if (!src.isFile()) {
                    Logger.appendToLog("Warn: missing Android native " + src);
                    continue;
                }
                dst.getParentFile().mkdirs();
                java.nio.file.Files.copy(src.toPath(), dst.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Logger.appendToLog("Info: LWJGL native ready: " + dst);
            }

            // -cp instead of -jar ignores the jar manifest, so repeat its Add-Opens/Add-Exports.
            javaArgList.add("--add-opens=java.desktop/java.awt=ALL-UNNAMED");
            javaArgList.add("--add-exports=java.desktop/sun.awt.X11=ALL-UNNAMED");
            // TEMPORARY diagnostic: thread dumps to <darkanHome>/logs/watchdog-threads.txt (loaded by AWT).
            File watchdogJar = new File(Tools.DIR_DATA, "watchdog.jar");
            File watchdogOut = new File(darkanHome, "logs/watchdog-threads.txt");
            Tools.copyAssetFile(this, "dev/watchdog.jar", Tools.DIR_DATA, true);
            watchdogOut.getParentFile().mkdirs();
            watchdogOut.delete();
            new File(darkanHome, "logs/exit-trace.txt").delete();
            javaArgList.add("-Dwatchdog.out=" + watchdogOut.getAbsolutePath());
            javaArgList.add("-cp");
            javaArgList.add(nativeCp.getAbsolutePath() + ":" + clientJar.getAbsolutePath() + ":" + watchdogJar.getAbsolutePath());
            javaArgList.add("DevMain"); // wrapper that starts the watchdog, then calls com.darkan.bot.BotKt.main
            javaArgList.add(DarkanClientUpdater.LOBBY_HOST);
            javaArgList.add(String.valueOf(DarkanClientUpdater.LOBBY_PORT));
            gamedir = darkanHome;

            Logger.appendToLog("Info: Java arguments: " + Arrays.toString(javaArgList.toArray(new String[0])));

            return JREUtils.launchJavaVM(this, runtime,gamedir,javaArgList, LauncherPreferences.PREF_CUSTOM_JAVA_ARGS);
        } catch (Throwable th) {
            Tools.showError(this, th, true);
            return -1;
        }
    }
    public void toggleKeyboard(View view) {
        mTouchCharInput.switchKeyboardState();
    }
}
