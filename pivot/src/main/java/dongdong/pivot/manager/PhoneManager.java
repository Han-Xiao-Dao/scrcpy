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

    public void handleClient(SelectionKey key) {
        try {
            PhoneController phoneController = (PhoneController) key.attachment();
            SocketChannel socketChannel = (SocketChannel) key.channel();
            if (phoneController == null) {
                READ_BUFF.position(0);
                READ_BUFF.limit(4);
                socketChannel.read(READ_BUFF);
                READ_BUFF.flip();
                int cmd = READ_BUFF.getInt();
                System.out.println(cmd);
                if (cmd == 0) {
                    StringBuilder sb = new StringBuilder();
                    for (PhoneController controller : PHONE_CONTROLLER_LIST) {
                        sb.append(controller.getSerialNum()).append(",");
                    }
                    READ_BUFF.clear();
                    byte[] bytes = sb.toString().getBytes();
                    READ_BUFF.putInt(bytes.length);
                    READ_BUFF.put(bytes);
                    READ_BUFF.flip();
                    socketChannel.write(READ_BUFF);
                } else if (cmd == 1) {
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
                    String phone = new String(READ_BUFF.array(), 0, length);
                    phoneController = PHONE_CONTROLLER_MAP.get(phone);
                    if (phoneController == null) {
                        return;
                    }
                    phoneController.runServer();
                    phoneController.connect();
                }
            } else {
                phoneController.forward(socketChannel);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
