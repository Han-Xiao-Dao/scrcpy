package dongdong.pivot.manager;

public class PortManager {

    private static PortManager instance;


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
        return 0;
    }

    public void returnPort(int port) {

    }
}
