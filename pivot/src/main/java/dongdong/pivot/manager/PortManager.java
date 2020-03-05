package dongdong.pivot.manager;

import java.util.HashSet;
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

    public int getPort() {
        if (ports.isEmpty()) {
            return currentPort++;
        }
        return ports.iterator().next();
    }

    public void giveBackPort(int port) {
        ports.add(port);
    }
}
