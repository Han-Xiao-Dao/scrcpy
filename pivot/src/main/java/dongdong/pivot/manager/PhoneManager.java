package dongdong.pivot.manager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

import dongdong.pivot.ADBUtil;
import dongdong.pivot.MainApp;
import dongdong.pivot.PhoneController;

public class PhoneManager {
    public final List<PhoneController> PHONE_CONTROLLER_LIST = new Vector<>();
    public final Map<String, PhoneController> PHONE_CONTROLLER_MAP = new HashMap<>();
    private final ByteBuffer READ_BUFF = ByteBuffer.allocate(1024);

    public void checkPhones() {
        try {
            Process process = ADBUtil.executeCmd("devices");
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

    public void handleRead(SelectionKey key) {
        PhoneController phoneController = (PhoneController) key.attachment();
        SocketChannel socketChannel = (SocketChannel) key.channel();
        if (phoneController == null) {
            int cmd;
            try {
                READ_BUFF.position(0);
                READ_BUFF.limit(4);
                socketChannel.read(READ_BUFF);
                READ_BUFF.flip();
                cmd = READ_BUFF.getInt();
            } catch (IOException e) {
                e.printStackTrace();
                key.cancel();
                return;
            }
            System.out.println(cmd);
            //获取可以连接的板子串码
            if (cmd == 0) {
                if (!getPhonesInfo(socketChannel)) {
                    key.cancel();
                }
                //连接板子
            } else if (cmd == 1) {
                if (!connectPhone(socketChannel)) {
                    key.cancel();
                }
                //控制板子
            } else if (cmd == 2) {
                if (!connectController(socketChannel)) {
                    key.cancel();
                }
            }
        } else {
            MainApp.SINGLE_THREAD_POOL.submit(() -> {
                phoneController.forward(socketChannel);
                return null;
            });
        }

    }

    private boolean getPhonesInfo(SocketChannel socketChannel) {
        StringBuilder sb = new StringBuilder();
        for (PhoneController controller : PHONE_CONTROLLER_LIST) {
            sb.append(controller.getSerialNum()).append(",");
        }
        READ_BUFF.clear();
        byte[] bytes = sb.toString().getBytes();
        READ_BUFF.putInt(bytes.length);
        READ_BUFF.put(bytes);
        READ_BUFF.flip();
        try {
            socketChannel.write(READ_BUFF);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private boolean connectPhone(SocketChannel socketChannel) {
        String phone;
        try {
            phone = readStr(socketChannel);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        PhoneController phoneController = PHONE_CONTROLLER_MAP.get(phone);
        if (phoneController == null) {
            return true;
        }
        try {
            phoneController.runServer();
            phoneController.connect();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        phoneController.setClientVideoSc(socketChannel);
        return true;
    }

    private boolean connectController(SocketChannel channel) {
        String phone;
        try {
            phone = readStr(channel);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        PhoneController phoneController = PHONE_CONTROLLER_MAP.get(phone);
        if (phoneController == null) {
            return true;
        }
        phoneController.setClientControlSc(channel);
        return true;
    }

    private String readStr(SocketChannel socketChannel) throws IOException {
        READ_BUFF.position(0);
        READ_BUFF.limit(4);
        while (READ_BUFF.hasRemaining()) {
            socketChannel.write(READ_BUFF);
        }
        READ_BUFF.flip();
        int length = READ_BUFF.getInt();
        READ_BUFF.position(0);
        READ_BUFF.limit(length);
        while (READ_BUFF.hasRemaining()) {
            socketChannel.read(READ_BUFF);
        }
        return new String(READ_BUFF.array(), 0, length);
    }
}
