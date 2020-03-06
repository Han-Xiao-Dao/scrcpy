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
    public static final ExecutorService SINGLE_THREAD_POOL = new ThreadPoolExecutor(100, Integer.MAX_VALUE, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1024), Executors.defaultThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
    public static boolean isRunning = true;


    public static void main(String[] args) {
        PhoneManager phoneManager = new PhoneManager();
        //异常处理
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            isRunning = false;
            throwable.printStackTrace();
            phoneManager.closeAll();
            Runtime.getRuntime().exit(0);
        });
        //持续监测当前连接 android 设备
        SINGLE_THREAD_POOL.submit(() -> {
            while (isRunning) {
                phoneManager.checkPhones();

                Thread.sleep(5 * 1000);
            }
            return false;
        });

        //监听端口,等待连接
        try (Selector selector = SelectorProvider.provider().openSelector();
             ServerSocketChannel ssc = SelectorProvider.provider().openServerSocketChannel()) {
            ssc.bind(new InetSocketAddress(PORT));
            ssc.configureBlocking(false);
            ssc.register(selector, SelectionKey.OP_ACCEPT);
            while (isRunning) {
                int selectCount = selector.select();
                if (selectCount <= 0) {
                    continue;
                }
                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey selectionKey = keyIterator.next();
                    keyIterator.remove();
                    if (selectionKey.isAcceptable()) {
                        SocketChannel socketChannel = ssc.accept();
                        phoneManager.handleAccept(socketChannel, selectionKey);
                    } else if (selectionKey.isReadable()) {
                        phoneManager.handleRead(selectionKey);
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
