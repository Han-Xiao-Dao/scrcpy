package dongdong.pivot.controller;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
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
    private static final int CONTROL_BUFFER_LENGTH = 30;

    private final ByteBuffer videoBuffer;
    private final ByteBuffer controlBuffer;
    private SocketChannel clientVideoSc;
    private SocketChannel serverVideoSc;
    private SocketChannel clientControlSc;
    private SocketChannel serverControlSc;

    private SelectionKey selectionKey;
    private boolean isConnected;
    private final String serialNum;
    private final int port;

    public PhoneController(String phone) {
        videoBuffer = ByteBuffer.allocateDirect(VIDEO_BUFFER_LENGTH);
        controlBuffer = ByteBuffer.allocateDirect(CONTROL_BUFFER_LENGTH);
        serialNum = phone;
        port = PortManager.getInstance().getPort();
        isConnected = true;
    }

    public void forward() throws IOException {
        videoBuffer.clear();
        videoBuffer.put((byte) 0);
        videoBuffer.flip();
        clientVideoSc.write(videoBuffer);
        videoBuffer.clear();
        while (isConnected) {
            serverVideoSc.read(videoBuffer);
            videoBuffer.flip();
            System.out.println(videoBuffer.remaining());
            clientVideoSc.write(videoBuffer);
            videoBuffer.clear();
        }
    }

    public void handleControl() {
        controlBuffer.position(0);
        controlBuffer.limit(28);
        System.out.println("ssssssssssssssssssssssssssssss");
        while (controlBuffer.hasRemaining()) {
            try {
                clientControlSc.read(controlBuffer);
            } catch (IOException e) {
                e.printStackTrace();
                stopControl();
                return;
            }
        }
        controlBuffer.flip();
        System.out.println("controlBuffer:  " + controlBuffer.remaining());
        try {
            serverControlSc.write(controlBuffer);
        } catch (IOException e) {
            e.printStackTrace();
            stopControl();
        }
    }

    private void stopControl() {
        System.out.println("stopControl");
        new RuntimeException().printStackTrace();
        selectionKey.cancel();
        setClientControlSc(null);
        setSelectionKey(null);
    }

    public void connect() throws IOException {
        serverVideoSc = SocketChannel.open(new InetSocketAddress("127.0.0.1", port));
        videoBuffer.position(0);
        videoBuffer.limit(1);
        while (videoBuffer.hasRemaining()) {
            serverVideoSc.read(videoBuffer);
        }
        videoBuffer.clear();
        serverControlSc = SocketChannel.open(new InetSocketAddress("127.0.0.1", port));

    }

    public String getSerialNum() {
        return serialNum;
    }

    public boolean isConnected() {
        return isConnectedServer() && isConnectedClient();
    }

    public boolean isConnectedServer() {
        return isConnected(serverControlSc) && isConnected(serverVideoSc);
    }

    public boolean isConnectedClient() {
        return isConnected(clientControlSc) && isConnected(clientVideoSc);
    }

    public void runServer() throws IOException {
        Process process = ADBUtil.adbPush(serialNum, "E:\\android\\scrcpy\\server\\build\\outputs\\apk\\debug\\scrcpy_server.jar", "/data/local/tmp");
        try {
            Thread.sleep(3 * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        process.destroy();


        Process process1 = ADBUtil.adbForward(serialNum, port);
        try {
            Thread.sleep(2 * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        process1.destroy();


        Process process2 = ADBUtil.adbShell(serialNum, "CLASSPATH=/data/local/tmp/scrcpy_server.jar nohup app_process / dongdong.server.Server &");
        try {
            Thread.sleep(2 * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        process2.destroy();
    }

    public SocketChannel getClientVideoSc() {
        return clientVideoSc;
    }

    public void setClientVideoSc(SocketChannel clientVideoSc) {
        this.clientVideoSc = clientVideoSc;
    }

    public SocketChannel getServerVideoSc() {
        return serverVideoSc;
    }

    public void setServerVideoSc(SocketChannel serverVideoSc) {
        this.serverVideoSc = serverVideoSc;
    }

    public SocketChannel getClientControlSc() {
        return clientControlSc;
    }

    public void setClientControlSc(SocketChannel clientControlSc) {
        this.clientControlSc = clientControlSc;
    }

    public SocketChannel getServerControlSc() {
        return serverControlSc;
    }

    public void setServerControlSc(SocketChannel serverControlSc) {
        this.serverControlSc = serverControlSc;
    }

    public void setConnected(boolean connected) {
        isConnected = connected;
    }


    public SelectionKey getSelectionKey() {
        return selectionKey;
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
    public void close() throws IOException {
        ADBUtil.adbRemoveForward(serialNum, port);
        PortManager.getInstance().giveBackPort(port);
        setConnected(false);
        if (clientVideoSc != null) {
            clientVideoSc.close();
        }
        if (serverVideoSc != null) {
            serverVideoSc.close();
        }
        if (clientControlSc != null) {
            clientControlSc.close();
        }
        if (serverControlSc != null) {
            serverControlSc.close();
        }
        if (selectionKey != null) {
            selectionKey.cancel();
        }

    }

    public static boolean isConnected(SocketChannel socketSc) {
        return socketSc != null;
    }


}
