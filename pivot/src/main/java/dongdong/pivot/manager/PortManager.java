package dongdong.pivot.manager;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class PortManager {

    private static PortManager instance;
    private Set<Integer> ports = new HashSet<>();
    private int currentPort = 43736;


    private PortManager() {
    }

    public static PortManager getInstance() {
        if (instance == null) {
            synchronized (PortManager.class) {
                if (instance == null) {
                    instance = new PortManager();
                }
            }
        }
        return instance;
    }

    public synchronized int getPort() {
        if (ports.isEmpty()) {
            return currentPort++;
        }
        Iterator<Integer> iterator = ports.iterator();
        int port = iterator.next();
        iterator.remove();
        return port;
    }

    public void giveBackPort(int port) {
        ports.add(port);
    }
}
