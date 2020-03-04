package dongdong.pivot;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;

import dongdong.pivot.manager.PortManager;

public class PhoneController implements Closeable {

    private static final int VIDEO_BUFFER_LENGTH = 10240;
    private static final int CONTROL_BUFFER_LENGTH = 1024;

    private final ByteBuffer videoBuffer;
    private final ByteBuffer controlBuffer;
    private SocketChannel clientVideoSc;
    private SocketChannel serverVideoSc;
    private SocketChannel clientControlSc;
    private SocketChannel serverControlSc;
    private final String serialNum;
    private final int port;

    public PhoneController(String phone) {
        videoBuffer = ByteBuffer.allocateDirect(VIDEO_BUFFER_LENGTH);
        controlBuffer = ByteBuffer.allocateDirect(CONTROL_BUFFER_LENGTH);
        serialNum = phone;
        port = PortManager.getInstance().getPort();
    }

    public void forward(SocketChannel socketChannel) throws IOException {
        if (socketChannel == serverVideoSc) {
            while (serverVideoSc.read(videoBuffer) > 0) {
                videoBuffer.flip();
                clientVideoSc.write(videoBuffer);
                videoBuffer.clear();
            }
        } else if (socketChannel == clientControlSc) {
            while (clientControlSc.read(controlBuffer) > 0) {
                controlBuffer.flip();
                serverControlSc.write(controlBuffer);
                controlBuffer.clear();
            }
        }

    }

    public void connect() throws IOException {
        SocketAddress address = new InetSocketAddress("127.0.0.1", port);
        serverVideoSc = SocketChannel.open(address);
        serverVideoSc.configureBlocking(false);
        videoBuffer.position(0);
        videoBuffer.limit(1);
        serverVideoSc.read(videoBuffer);
        serverControlSc = SocketChannel.open(address);
        serverControlSc.configureBlocking(false);
    }

    public void register(Selector selector) {
        try {
            serverVideoSc.register(selector, SelectionKey.OP_READ, this);
        } catch (ClosedChannelException e) {
            e.printStackTrace();
        }
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

    public void runServer() {
        try {
            Process process = ADBUtil.adbPush(serialNum, "E:\\android\\scrcpy\\server\\build\\outputs\\apk\\debug\\scrcpy_server.jar", "/data/local/tmp");
            process.waitFor(5, TimeUnit.SECONDS);
            process.destroy();

            Process process1 = ADBUtil.adbForward(serialNum, port);
            process1.waitFor(2, TimeUnit.SECONDS);
            process1.destroy();

            Process process2 = ADBUtil.adbShell(serialNum, "CLASSPATH=/data/local/tmp/scrcpy_server.jar app_process / dongdong.server.Server ");
            process2.waitFor(2, TimeUnit.SECONDS);
            process2.destroy();
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() throws IOException {
        ADBUtil.adbRemoveForward(serialNum, port);
        PortManager.getInstance().returnPort(port);
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
    }

    @Override
    public String toString() {
        return "PhoneController{" +
                "serialNum='" + serialNum + '\'' +
                '}';
    }

    public static boolean isConnected(SocketChannel socketSc) {
        return socketSc != null;
    }


}
