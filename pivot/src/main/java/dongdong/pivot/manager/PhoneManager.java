package dongdong.pivot.manager;

import dongdong.pivot.MainApp;
import dongdong.pivot.controller.PhoneController;
import dongdong.pivot.util.ADBUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class PhoneManager {
    private static final int TYPE_CLOSE = 0;
    private static final int TYPE_INPUT = 1;
    private static final int TYPE_PHONE_INFO = 2;
    private static final int TYPE_CONNECT = 3;

    private final List<PhoneController> phoneControllerList = new Vector<>();
    private final Map<String, PhoneController> phoneControllerMap = new ConcurrentHashMap<>();
    private final ByteBuffer buffer = ByteBuffer.allocate(1024);

    public void checkPhones() {
        try {
            Process process = ADBUtil.executeCmd(" devices");
            process.waitFor(2, TimeUnit.SECONDS);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            List<String> phones = new Vector<>();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if ("List of devices attached".equals(line)) {
                    continue;
                }
                if (!line.contains("device")) {
                    continue;
                }
                String phone = line.replaceAll("device", "").trim();
                phones.add(phone);
            }
            process.destroy();

            Iterator<PhoneController> iterator = phoneControllerList.iterator();
            while (iterator.hasNext()) {
                PhoneController phone = iterator.next();
                if (phones.contains(phone.getSerialNum())) {
                    continue;
                }
                iterator.remove();
                phone.close();
                phoneControllerMap.remove(phone.getSerialNum());
            }

            for (String phone : phones) {
                if (phoneControllerMap.containsKey(phone)) {
                    continue;
                }
                PhoneController phoneController = new PhoneController(phone);
                phoneControllerMap.put(phone, phoneController);
                phoneControllerList.add(phoneController);
            }


        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void checkConnect() {
        for (PhoneController phoneController : phoneControllerList) {
            if (phoneController.isConnected()) {
                phoneController.checkConnect();
            }
        }
    }

    public void closeAll() {
        for (PhoneController phoneController : phoneControllerList) {
            phoneController.close();
        }
    }

    /**
     * 响应客户端的连接,并且区分是视频连接还是注册socket监听
     */
    public void handleAccept(SocketChannel socketChannel, SelectionKey selectionKey) {

        try {
            int cmd = readInt(socketChannel);
            if (cmd == 0) {
                //发送可以连接的板子的串码
                String phone = readStr(socketChannel);
                MainApp.SINGLE_THREAD_POOL.execute(() -> {
                    //连接板子
                    connectPhone(socketChannel, phone);
                });
            } else if (cmd == 1) {
                //注册选择器
                socketChannel.configureBlocking(false);
                try {
                    socketChannel.register(selectionKey.selector(), SelectionKey.OP_READ);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            } else if (cmd < 0) {
                selectionKey.cancel();
            }
        } catch (IOException | BufferUnderflowException e) {
            e.printStackTrace();
        }
    }

    public void handleRead(SelectionKey selectionKey) {
        SocketChannel channel = (SocketChannel) selectionKey.channel();
        int type;
        try {
            type = readInt(channel);
        } catch (IOException e) {
            e.printStackTrace();
            selectionKey.cancel();
            return;
        }
        if (type < 0) {
            selectionKey.cancel();
            return;
        }
        switch (type) {
            case TYPE_CLOSE:
                PhoneController phoneController = (PhoneController) selectionKey.attachment();
                phoneController.setConnected(false);
                break;
            case TYPE_INPUT:
                handleControl(selectionKey);
                break;
            case TYPE_PHONE_INFO:
                handlePhonesInfo(channel);
                break;
            case TYPE_CONNECT:
                handleConnect(selectionKey, channel);
            default:
                break;
        }
    }


    private void handleControl(SelectionKey selectionKey) {
        PhoneController phoneController = (PhoneController) selectionKey.attachment();
        phoneController.handleControl();
    }

    private void handlePhonesInfo(SocketChannel socketChannel) {
        StringBuilder sb = new StringBuilder();
        for (PhoneController controller : phoneControllerList) {
            sb.append(controller.getSerialNum()).append(",");
        }
        buffer.clear();
        System.out.println("phones:  " + sb);
        byte[] bytes = sb.toString().getBytes();
        buffer.putInt(bytes.length);
        buffer.put(bytes);
        buffer.flip();
        try {
            socketChannel.write(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleConnect(SelectionKey selectionKey, SocketChannel channel) {
        String phone;
        try {
            phone = readStr(channel);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        PhoneController controller = phoneControllerMap.get(phone);
        if (controller == null) {
            return;
        }
        selectionKey.attach(controller);
        controller.setSelectionKey(selectionKey);
        controller.setClientControlSc(channel);
    }

    private void connectPhone(SocketChannel socketChannel, String phone) {
        try (PhoneController phoneController = phoneControllerMap.get(phone)) {

            if (phoneController == null) {
                socketChannel.close();
                return;
            }

            phoneController.connect(socketChannel);
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    private String readStr(SocketChannel socketChannel) throws IOException {
        int length = readInt(socketChannel);
        System.out.println("length: " + length);
        buffer.position(0);
        buffer.limit(length);
        while (buffer.hasRemaining()) {
            if (socketChannel.read(buffer) == -1) {
                return null;
            }
        }
        return new String(buffer.array(), 0, length);
    }

    private int readInt(SocketChannel socketChannel) throws IOException {
        buffer.position(0);
        buffer.limit(4);
        while (buffer.hasRemaining()) {
            int i = socketChannel.read(buffer);
            if (i == -1) {
                return -1;
            }
        }
        buffer.flip();
        return buffer.getInt();
    }

}
