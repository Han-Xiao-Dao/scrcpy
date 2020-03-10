package dongdong.pivot.controller;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;

import dongdong.pivot.MainApp;
import dongdong.pivot.util.ADBUtil;
import dongdong.pivot.manager.PortManager;

public class PhoneController implements Closeable {

    private static final int VIDEO_BUFFER_LENGTH = 10240;
    private static final int CONTROL_BUFFER_LENGTH = 40;

    private final ByteBuffer videoBuffer;
    private final ByteBuffer controlBuffer;
    private SocketChannel clientVideoSc;
    private SocketChannel serverVideoSc;
    private SocketChannel clientControlSc;
    private SocketChannel serverControlSc;

    private SelectionKey selectionKey;

    public boolean isConnected() {
        return isConnected;
    }

    private boolean isConnected;
    private final String serialNum;
    private final int port;

    public PhoneController(String phone) {
        videoBuffer = ByteBuffer.allocateDirect(VIDEO_BUFFER_LENGTH);
        controlBuffer = ByteBuffer.allocateDirect(CONTROL_BUFFER_LENGTH);
        serialNum = phone;
        port = PortManager.getInstance().getPort();
        isConnected = false;
//        pushServer();
    }

    public void runServer() throws IOException {

        pushServer();

        Process process1 = ADBUtil.adbForward(serialNum, port);
        try {
            process1.waitFor(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        process1.destroy();

        startServer();

    }

    private void pushServer() throws IOException {
        Process process = ADBUtil.adbPush(serialNum, "E:\\android\\scrcpy\\server\\build\\outputs\\apk\\debug\\scrcpy_server.jar", "/data/local/tmp");
        try {
            process.waitFor(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        process.destroy();

    }

    private void startServer() throws IOException {
        do {
            Process process2 = ADBUtil.adbShell(serialNum, "CLASSPATH=/data/local/tmp/scrcpy_server.jar nohup app_process / dongdong.server.Server &");
            try {
                process2.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            process2.destroy();
        } while (isNull(ADBUtil.adbGetPid(serialNum, "app_process")));
    }

    public void checkConnect() {
        ByteBuffer buffer = ByteBuffer.allocate(1);
        buffer.put((byte) 0);
        buffer.flip();
        try {
            clientControlSc.write(buffer);
        } catch (IOException e) {
            e.printStackTrace();
            setConnected(false);
        }
    }

    private boolean isNull(String s) {
        System.out.println(s);
        return s == null || "".equals(s.trim());
    }


    public void connect() throws IOException {
        if (isConnected) {
            return;
        }
        isConnected = true;
        serverVideoSc = SocketChannel.open(new InetSocketAddress("127.0.0.1", port));
        videoBuffer.position(0);
        videoBuffer.limit(1);
        while (videoBuffer.hasRemaining()) {
            serverVideoSc.read(videoBuffer);
        }
        videoBuffer.clear();
        serverControlSc = SocketChannel.open(new InetSocketAddress("127.0.0.1", port));

        videoBuffer.clear();
        videoBuffer.put((byte) 0);
        videoBuffer.flip();
        clientVideoSc.write(videoBuffer);
        videoBuffer.clear();
    }

    public void forward() throws IOException {
        while (isConnected) {
            serverVideoSc.read(videoBuffer);
            videoBuffer.flip();
            clientVideoSc.write(videoBuffer);
            videoBuffer.clear();
        }
    }

    public void handleControl() {
        try {
            controlBuffer.position(0);
            controlBuffer.limit(36);
            while (controlBuffer.hasRemaining()) {
                if (clientControlSc.read(controlBuffer) == -1) {
                    setConnected(false);
                    return;
                }
            }
            controlBuffer.flip();
            serverControlSc.write(controlBuffer);
        } catch (IOException | BufferUnderflowException e) {
            e.printStackTrace();
            setConnected(false);
        }

    }

    public String getSerialNum() {
        return serialNum;
    }

    public void setClientVideoSc(SocketChannel clientVideoSc) {
        this.clientVideoSc = clientVideoSc;
    }

    public void setClientControlSc(SocketChannel clientControlSc) {
        this.clientControlSc = clientControlSc;
    }

    public void setConnected(boolean connected) {
        isConnected = connected;
    }

    public void setSelectionKey(SelectionKey selectionKey) {
        this.selectionKey = selectionKey;
    }

    @Override
    public String toString() {
        return "PhoneController{" +
                "serialNum='" + serialNum + '\'' +
                ", port=" + port +
                '}';
    }

    @Override
    public void close() {
        try {
            Process process = ADBUtil.adbRemoveForward(serialNum, port);
            process.waitFor(2, TimeUnit.SECONDS);
            process.destroy();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        PortManager.getInstance().giveBackPort(port);
        setConnected(false);
        controlBuffer.clear();
        videoBuffer.clear();

        if (serverControlSc != null) {
            controlBuffer.clear();
            controlBuffer.put((byte) 11);
            controlBuffer.flip();
            try {
                serverControlSc.write(controlBuffer);
                serverControlSc.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (serverVideoSc != null) {
            try {
                serverVideoSc.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (clientVideoSc != null) {
            try {
                clientVideoSc.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (selectionKey != null) {
            selectionKey.cancel();
        }

    }

}
