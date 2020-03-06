package android.scrcpy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import dongdong.util.Ln;

public class Controller {
    public static final int TYPE_INJECT_TOUCH_EVENT = 2;
    private static final ByteBuffer CONTROL_BUFFER = ByteBuffer.allocate(1024);
    private BlockingQueue<ControlMessage> queue = new LinkedBlockingQueue<>();

    private SocketChannel controlSc;
    private int mScreenWidth;
    private float mScaleValue;
    private int mVideoWidth;
    private int mVideoHeight;

    public Controller() {
        new Thread(() -> {
            try {
                while (true) {
                    ControlMessage controlMessage = queue.take();
                    CONTROL_BUFFER.clear();
                    CONTROL_BUFFER.put((byte) TYPE_INJECT_TOUCH_EVENT);
                    CONTROL_BUFFER.put((byte) controlMessage.action);
                    CONTROL_BUFFER.putLong(-1);
                    int x = (int) (controlMessage.x / mScaleValue);
                    int y = (int) (controlMessage.y / mScaleValue);
                    CONTROL_BUFFER.putInt(x);
                    CONTROL_BUFFER.putInt(y);
                    CONTROL_BUFFER.putShort((short) mVideoWidth);
                    CONTROL_BUFFER.putShort((short) mVideoHeight);
                    CONTROL_BUFFER.putShort((short) -1);
                    CONTROL_BUFFER.putInt(1);
                    CONTROL_BUFFER.flip();
                    controlSc.write(CONTROL_BUFFER);
                }
            } catch (InterruptedException | IOException ignored) {
                Ln.e("Controller: ", ignored);
            }
        }).start();
    }

    public void sendTouchEvent(int x, int y, int action) {
        Ln.d("a: " + x + " b: " + y + "  c: " + action);
        ControlMessage controlMessage = new ControlMessage();
        controlMessage.x = x;
        controlMessage.y = y;
        controlMessage.action = action;
        queue.add(controlMessage);
    }



    public SocketChannel getControlSc() {
        return controlSc;
    }

    public void setControlSc(SocketChannel controlSc) {
        this.controlSc = controlSc;
    }

    public int getmScreenWidth() {
        return mScreenWidth;
    }

    public void setmScreenWidth(int mScreenWidth) {
        this.mScreenWidth = mScreenWidth;
    }

    public float getmScaleValue() {
        return mScaleValue;
    }

    public void setmScaleValue(float mScaleValue) {
        this.mScaleValue = mScaleValue;
    }

    public int getmVideoWidth() {
        return mVideoWidth;
    }

    public void setmVideoWidth(int mVideoWidth) {
        this.mVideoWidth = mVideoWidth;
    }

    public int getmVideoHeight() {
        return mVideoHeight;
    }

    public void setmVideoHeight(int mVideoHeight) {
        this.mVideoHeight = mVideoHeight;
    }

    private static class ControlMessage implements Comparable<ControlMessage> {

        int x;
        int y;
        int action;
        long systemTime;

        ControlMessage() {
            systemTime = System.currentTimeMillis();
        }

        @Override
        public int compareTo(ControlMessage o) {
            return (int) (o.systemTime - systemTime);
        }
    }

}
