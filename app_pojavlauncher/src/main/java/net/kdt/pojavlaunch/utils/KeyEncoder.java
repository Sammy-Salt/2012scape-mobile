package net.kdt.pojavlaunch.utils;

import net.kdt.pojavlaunch.AWTInputBridge;
import net.kdt.pojavlaunch.customcontrols.keyboard.TouchCharInput;

import java.util.HashMap;
import java.util.Map;

public class KeyEncoder {

    private static final Map<Character, Character> specialCharMap = createSpecialCharMap();
    private static final char MODIFIER = 123; // F12 key as a modifier for caps lock
    private static final char BACKSPACE_ANDROID = 67;
    private static final char BACKSPACE_UNICODE = 8;

    // Initialize the mapping of special characters to their respective keys
    private static Map<Character, Character> createSpecialCharMap() {
        Map<Character, Character> map = new HashMap<>();
        map.put('!', '1');
        map.put('@', '2');
        map.put('#', '3');
        map.put('$', '4');
        map.put('%', '5');
        map.put('^', '6');
        map.put('&', '7');
        map.put('*', '8');
        map.put('(', '9');
        map.put(')', '0');
        map.put('_', '-');
        map.put('+', '=');
        map.put('{', '[');
        map.put('}', ']');
        map.put(':', ';');
        map.put('"', '\'');
        map.put('<', ',');
        map.put('>', '.');
        map.put('?', '/');
        map.put('|', '\\');
        return map;
    }

    public static void sendUnicodeBackspace(){
        AWTInputBridge.sendKey(BACKSPACE_UNICODE, BACKSPACE_UNICODE);
    }

    /** Printable character from the on-screen keyboard: deliver it as real AWT typed text (case and symbols intact). */
    public static void sendEncodedChar(int keyCode, char c) {
        if (c == '\n') {
            AWTInputBridge.sendKey('\n', 10);
        } else if (c == '\b') {
            sendUnicodeBackspace();
        } else if (c >= 32 && c != 127) {
            AWTInputBridge.sendChar(c);
        }
    }

    /** Key event coming from Android (soft or hardware keyboard): translate to standard AWT key codes. */
    public static void sendAndroidKey(int androidKeyCode, char unicode) {
        int vk;
        switch (androidKeyCode) {
            case 67:  sendUnicodeBackspace(); return;            // DEL (backspace)
            case 112: vk = 127; break;                           // forward delete
            case 66:                                              // ENTER
            case 160: AWTInputBridge.sendKey('\n', 10); return; // numpad enter
            case 61:  vk = 9; break;                             // TAB
            case 111: vk = 27; break;                            // ESC
            case 19:  vk = 38; break;                            // up
            case 20:  vk = 40; break;                            // down
            case 21:  vk = 37; break;                            // left
            case 22:  vk = 39; break;                            // right
            case 122: vk = 36; break;                            // home
            case 123: vk = 35; break;                            // end
            case 92:  vk = 33; break;                            // page up
            case 93:  vk = 34; break;                            // page down
            default:
                sendEncodedChar(androidKeyCode, unicode);
                return;
        }
        AWTInputBridge.sendKey((char) 0, vk);
    }
}
