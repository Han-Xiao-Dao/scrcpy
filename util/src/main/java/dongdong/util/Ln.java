package dongdong.util;

import android.util.Log;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;


/**
 * Log both to Android logger (so that logs are visible in "adb logcat") and standard output/error (so that they are visible in the terminal
 * directly).
 */
public final class Ln {

    private static final String TAG = "scrcpy";
    private static final String PREFIX = "[server] ";
    private static final DateFormat SDF = new SimpleDateFormat("[HH:mm:ss:SSS]: ", Locale.US);
    private static final DateFormat SDF2 = new SimpleDateFormat("yyMMdd", Locale.US);


    private static Level THRESHOLD;
    private static File LOG_PATH;

    public enum Level {
        /**
         *
         */
        DEBUG,
        INFO,
        WARN,
        ERROR
    }


    private Ln() {
        // not instantiable
    }

    public static void setLogPath(File logPath) {
        LOG_PATH = logPath;
        LOG_PATH.mkdirs();
    }

    public static void setTHRESHOLD(Level level) {
        THRESHOLD = level;
    }

    public static boolean isEnabled(Level level) {
        return level.ordinal() >= THRESHOLD.ordinal();
    }

    public static void d(String message) {
        if (isEnabled(Level.DEBUG)) {
            Log.d(TAG, message);

            System.out.println(PREFIX + "DEBUG: " + message);
            write(message);
        }
    }

    public static void i(String message) {
        if (isEnabled(Level.INFO)) {
            Log.i(TAG, message);
            System.out.println(PREFIX + "INFO: " + message);
            write(message);
        }
    }

    public static void w(String message) {
        if (isEnabled(Level.WARN)) {
            Log.w(TAG, message);
            System.out.println(PREFIX + "WARN: " + message);
            write(message);
        }
    }

    public static void e(String message, Throwable throwable) {
        if (isEnabled(Level.ERROR)) {
            Log.e(TAG, message, throwable);
            System.out.println(PREFIX + "ERROR: " + message);
            StringBuilder sb = new StringBuilder(message).append("\n");
            if (throwable != null) {

                sb.append(":--------------------------------------------------\n")
                        .append(throwable.getMessage()).append(" ")
                        .append(throwable.getCause()).append(" ")
                        .append(Log.getStackTraceString(throwable))
                        .append("==================================================\n");
            }
            write(sb.toString());
        }
    }

    public static void e(String message) {
        e(message, null);
    }


    public static void write(String text) {
        File file = new File(LOG_PATH, SDF2.format(System.currentTimeMillis()) + ".txt");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8))) {
            writer.write(SDF.format(System.currentTimeMillis()) + text + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void deleteLog() {
        if (LOG_PATH.exists() && LOG_PATH.isDirectory()) {
            LOG_PATH.listFiles(file -> {
                if (file.length() >= 10 * 1024 * 1024) {
                    file.delete();
                    return false;
                }
                String name = file.getName();
                int curDay = Integer.parseInt(SDF2.format(System.currentTimeMillis()));
                int fileName;
                try {
                    fileName = Integer.parseInt(name.substring(0, name.length() - 4));
                } catch (Throwable e) {
                    return false;
                }
                if (curDay - fileName >= 2) {
                    file.delete();
                }
                return false;
            });
        }
    }
}

