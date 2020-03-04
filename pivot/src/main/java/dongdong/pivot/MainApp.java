package dongdong.pivot;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import dongdong.pivot.manager.PhoneManager;

public class MainApp {
    private static final int PORT = 43735;
    private static final ExecutorService SINGLE_THREAD_POOL = new ThreadPoolExecutor(8, 16, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1024), Executors.defaultThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
    private static boolean isRunning = true;


    public static void main(String[] args) {
        PhoneManager phoneManager = new PhoneManager();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            isRunning = false;
            phoneManager.closeAll();
        });
        SINGLE_THREAD_POOL.submit(() -> {
            while (isRunning) {
                phoneManager.checkPhones();
                System.out.println(phoneManager.PHONE_CONTROLLER_LIST);
                Thread.sleep(5 * 1000);
            }
            return false;
        });
        try (ServerSocketChannel ssc = SelectorProvider.provider().openServerSocketChannel()) {
            ssc.configureBlocking(false);
            ssc.bind(new InetSocketAddress(PORT));
            Selector selector = SelectorProvider.provider().openSelector();
            ssc.register(selector, SelectionKey.OP_ACCEPT);
            while (isRunning) {
                int ready = selector.select();
                if (ready <= 0) {
                    continue;
                }
                Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();
                    if (key.isAcceptable()) {
                        SocketChannel socketChannel = ssc.accept();
                        socketChannel.configureBlocking(false);
                        socketChannel.register(selector, SelectionKey.OP_READ);
                    } else if (key.isReadable()) {
                        phoneManager.handleClient(key);
                    }
                }
            }

        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            phoneManager.closeAll();
            isRunning = false;
            Runtime.getRuntime().exit(0);
        }
    }
}
