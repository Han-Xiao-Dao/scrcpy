package dongdong.pivot.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ADBUtil {
    private final static String ADB = System.getProperty("os.name").contains("Windows") ? "adb" : "~/bipartite/adb";
    public static Process executeCmd(String cmd) throws IOException {
        cmd = ADB + cmd;
        System.out.println("execute cmd:  " + cmd);
        return Runtime.getRuntime().exec(cmd);
    }

    public static Process adbForward(String phone, int port) throws IOException {
        String cmd = " -s " + phone + " forward tcp:" + port + " localabstract:scrcpy";
        return executeCmd(cmd);
    }

    public static Process adbRemoveForward(String phone, int port) throws IOException {
        String cmd = " -s " + phone + " forward --remove tcp:" + port;
        return executeCmd(cmd);
    }

    public static Process adbPush(String phone, String fPath, String pPath) throws IOException {
        String cmd = " -s " + phone + " push " + fPath + " " + pPath;
        return executeCmd(cmd);
    }

    public static Process adbShell(String phone, String shell) throws IOException {
        String cmd = " -s " + phone + " shell " + shell;
        return executeCmd(cmd);
    }

    public static String adbGetPid(String phone, String pName) throws IOException {
        Process process = adbShell(phone, " ps |grep " + pName);
        String line;
        StringBuilder stringBuilder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        while ((line = reader.readLine()) != null) {
            stringBuilder.append(line);
            System.out.println(line);
        }
        process.destroy();
        int i = 0;
        StringBuilder sb = new StringBuilder();
        while (i < stringBuilder.length()) {
            char s = stringBuilder.charAt(i);
            i++;
            if (s > 57 || s < 48) {
                if (sb.length() > 0) {
                    break;
                } else {
                    continue;
                }
            }
            sb.append(s);
        }
        return sb.toString();
    }


}
