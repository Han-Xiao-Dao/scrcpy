package android.scrcpy.manager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

public class ServerManager {
    private static final int SERVER_PORT = 43735;
    private static ServerManager serverManager = new ServerManager();

    private String ipAddress;
    private SocketChannel videoSc;
    private SocketChannel controlSc;

    private ServerManager() {
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
        }
        return videoSc;
    }

    public SocketChannel getControlSc() throws IOException {
        if (controlSc == null) {
            controlSc = SocketChannel.open(new InetSocketAddress(ipAddress, SERVER_PORT));
        }
        return controlSc;
    }
}
