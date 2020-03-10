package android.scrcpy.manager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class ServerManager {
    private static final int TYPE_CLOSE = 0;
    private static final int TYPE_INPUT = 1;
    private static final int TYPE_PHONE_INFO = 2;
    private static final int TYPE_CONNECT = 3;
    private static final int SERVER_PORT = 43735;
    private static ServerManager serverManager = new ServerManager();

    private String ipAddress;
    private SocketChannel videoSc;
    private SocketChannel controlSc;
    private ByteBuffer byteBuffer;

    private ServerManager() {
        byteBuffer = ByteBuffer.allocate(4);
    }

    public static ServerManager getInstance() {
        return serverManager;
    }

    public void initAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public SocketChannel getVideoSc() throws IOException {
        if (videoSc == null) {
            videoSc = SocketChannel.open(new InetSocketAddress(ipAddress, SERVER_PORT));
            byteBuffer.clear();
            byteBuffer.putInt(0);
            byteBuffer.flip();
            videoSc.write(byteBuffer);
        }
        return videoSc;
    }

    public SocketChannel getControlSc() throws IOException {
        if (controlSc == null) {
            controlSc = SocketChannel.open(new InetSocketAddress(ipAddress, SERVER_PORT));
            byteBuffer.clear();
            byteBuffer.putInt(1);
            byteBuffer.flip();
            controlSc.write(byteBuffer);
        }
        return controlSc;
    }

}
