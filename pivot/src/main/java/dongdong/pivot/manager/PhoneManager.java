package dongdong.pivot.manager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

import dongdong.pivot.MainApp;
import dongdong.pivot.exception.NoSuchPhoneException;
import dongdong.pivot.util.ADBUtil;
import dongdong.pivot.controller.PhoneController;

public class PhoneManager {
    public final List<PhoneController> PHONE_CONTROLLER_LIST = new Vector<>();
    public final Map<String, PhoneController> PHONE_CONTROLLER_MAP = new HashMap<>();

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

            Iterator<PhoneController> iterator = PHONE_CONTROLLER_LIST.iterator();
            while (iterator.hasNext()) {
                PhoneController phone = iterator.next();
                if (phones.contains(phone.getSerialNum())) {
                    continue;
                }
                iterator.remove();
                phone.close();
                PHONE_CONTROLLER_MAP.remove(phone.getSerialNum());
            }

            for (String phone : phones) {
                if (PHONE_CONTROLLER_MAP.containsKey(phone)) {
                    continue;
                }
                PhoneController phoneController = new PhoneController(phone);
                PHONE_CONTROLLER_MAP.put(phone, phoneController);
                PHONE_CONTROLLER_LIST.add(phoneController);
            }


        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void closeAll() {
        for (PhoneController phoneController : PHONE_CONTROLLER_LIST) {
            try {
                phoneController.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void handleAccept(SocketChannel socketChannel, SelectionKey selectionKey) {
        ByteBuffer readBuff = ByteBuffer.allocate(1024);
        int cmd;
        try {
            readBuff.position(0);
            readBuff.limit(4);
            socketChannel.read(readBuff);
            readBuff.flip();
            cmd = readBuff.getInt();
            if (cmd == 0) {
                //发送可以连接的板子的串码
                MainApp.SINGLE_THREAD_POOL.execute(() -> {

                    try {
                        sendPhonesInfo(socketChannel, readBuff);
                    } catch (IOException e) {
                        e.printStackTrace();
                        try {
                            socketChannel.close();
                        } catch (IOException ignored) {
                        }
                        return;
                    }
                    //连接板子
                    connectPhone(socketChannel, readBuff);
                });
            } else {
                //连接板子控制器
                connectController(socketChannel, readBuff, selectionKey);
            }
        } catch (IOException | BufferUnderflowException | NoSuchPhoneException e) {
            e.printStackTrace();
        }
    }

    public void handleRead(SelectionKey selectionKey) {
        PhoneController phoneController = (PhoneController) selectionKey.attachment();
        phoneController.handleControl();
    }

    private void sendPhonesInfo(SocketChannel socketChannel, ByteBuffer readBuff) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (PhoneController controller : PHONE_CONTROLLER_LIST) {
            sb.append(controller.getSerialNum()).append(",");
        }
        readBuff.clear();
        System.out.println("phones:  " + sb);
        byte[] bytes = sb.toString().getBytes();
        readBuff.putInt(bytes.length);
        readBuff.put(bytes);
        readBuff.flip();
        socketChannel.write(readBuff);

    }

    private void connectPhone(SocketChannel socketChannel, ByteBuffer readBuffer) {
        String phone;
        try {
            phone = readStr(socketChannel, readBuffer);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        try (PhoneController phoneController = PHONE_CONTROLLER_MAP.get(phone)) {
            if (phoneController == null) {
                throw new NoSuchPhoneException(phone);
            }
            phoneController.runServer();
            phoneController.setClientVideoSc(socketChannel);
            phoneController.connect();
            phoneController.forward();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    private void connectController(SocketChannel channel, ByteBuffer readBuffer, SelectionKey selectionKey) throws IOException {
        String phone = readStr(channel, readBuffer);
        PhoneController phoneController = PHONE_CONTROLLER_MAP.get(phone);
        if (phoneController == null) {
            throw new NoSuchPhoneException(phone);
        }
        channel.configureBlocking(false);
        try {
            SelectionKey key = channel.register(selectionKey.selector(), SelectionKey.OP_READ, phoneController);
            phoneController.setSelectionKey(key);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        phoneController.setClientControlSc(channel);
    }

    private String readStr(SocketChannel socketChannel, ByteBuffer readBuffer) throws IOException {
        readBuffer.position(0);
        readBuffer.limit(4);
        while (readBuffer.hasRemaining()) {
            socketChannel.read(readBuffer);
        }

        readBuffer.flip();
        int length = readBuffer.getInt();
        System.out.println("length: " + length);
        readBuffer.position(0);
        readBuffer.limit(length);
        while (readBuffer.hasRemaining()) {
            socketChannel.read(readBuffer);
        }
        System.out.println("str: " + new String(readBuffer.array(), 0, length));
        return new String(readBuffer.array(), 0, length);
    }
}
