package android.scrcpy.ui.activity;

import android.graphics.Rect;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.SystemClock;
import android.scrcpy.Controller;
import android.scrcpy.R;
import android.scrcpy.manager.ServerManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

import androidx.appcompat.app.AppCompatActivity;
import dongdong.util.Ln;

public class Main2Activity extends AppCompatActivity implements SurfaceHolder.Callback {
    private static final int REPEAT_FRAME_DELAY_US = 100_000; // repeat after 100ms
    private static final int BYTES_LENGTH = 102400;
    private static final ByteBuffer BYTE_BUFFER = ByteBuffer.allocate(BYTES_LENGTH);
    private static final ByteBuffer HEADER_BUFFER = ByteBuffer.allocate(12);


    private MediaCodec mCodec;
    private boolean isStart;
    private String phone;
    private MediaCodec.BufferInfo info;
    private SurfaceView mSurfaceView;
    private Controller controller;
    private ServerManager serverManager = ServerManager.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        phone = getIntent().getStringExtra("phone");
        Ln.e("onCreate: " + phone);
        if (phone == null) {
            finish();
        }

        setContentView(R.layout.activity_main2);

        controller = new Controller();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        controller.setmScreenWidth(dm.widthPixels);

        mSurfaceView = findViewById(R.id.surfaceView);
        mSurfaceView.getHolder().addCallback(this);

        try {
            mCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        } catch (IOException e) {
            finish();
        }

        info = new MediaCodec.BufferInfo();
    }

    private static MediaFormat createFormat(int width, int height) {
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 2000000);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 60);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);

        format.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, REPEAT_FRAME_DELAY_US);
        return format;
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int y = (int) (event.getY() - getTopHeight());
        if (y > mSurfaceView.getHeight()) {
            return false;
        }

        controller.sendTouchEvent((int) event.getX(), y, event.getAction());
        return false;
    }

    private int getTopHeight() {
        Rect frame = new Rect();
        getWindow().getDecorView().getWindowVisibleDisplayFrame(frame);
        return frame.top;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        isStart = true;
        new Thread(() -> connectServer(holder)).start();
    }

    private void connectServer(SurfaceHolder holder) {
        try {
            SocketChannel videoSc = serverManager.getVideoSc();
            byte[] bytes = phone.getBytes();
            BYTE_BUFFER.putInt(bytes.length);
            BYTE_BUFFER.put(bytes);
            BYTE_BUFFER.flip();
            videoSc.write(BYTE_BUFFER);
            BYTE_BUFFER.position(0);
            BYTE_BUFFER.limit(1);
            videoSc.read(BYTE_BUFFER);

            SocketChannel controlSc = serverManager.getControlSc();
            controller.setControlSc(controlSc);
            BYTE_BUFFER.clear();
            BYTE_BUFFER.putInt(1);
            BYTE_BUFFER.putInt(bytes.length);
            BYTE_BUFFER.put(bytes);
            BYTE_BUFFER.flip();
            controlSc.write(BYTE_BUFFER);

            BYTE_BUFFER.position(0);
            BYTE_BUFFER.limit(68);
            while (BYTE_BUFFER.remaining() > 0) {
                Ln.d("listenSocket: " + "  " + videoSc.read(BYTE_BUFFER));
            }
            BYTE_BUFFER.flip();

            Ln.d("listenSocket: " + new String(BYTE_BUFFER.array(), 0, 64, StandardCharsets.UTF_8));
            int videoWidth = (BYTE_BUFFER.get(64) << 8) + (BYTE_BUFFER.get(65) & 0xff);
            int videoHeight = (BYTE_BUFFER.get(66) << 8) + (BYTE_BUFFER.get(67) & 0xff);

            float mScaleValue = (float) controller.getmScreenWidth() / videoWidth;
            ViewGroup.LayoutParams lp = mSurfaceView.getLayoutParams();
            lp.height = (int) (videoHeight * mScaleValue);
            runOnUiThread(() -> mSurfaceView.setLayoutParams(lp));
            controller.setmScaleValue(mScaleValue);
            controller.setmVideoHeight(videoHeight);
            controller.setmVideoWidth(videoWidth);

            MediaFormat mediaFormat = createFormat(videoWidth, videoHeight);
            mCodec.configure(mediaFormat, holder.getSurface(), null, 0);
            mCodec.start();
            new Thread(this::listenSocket).start();
            new Thread(this::decodeOutput).start();

        } catch (IOException e) {
            Ln.e("connectServer: ", e);
            finish();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }


    private void listenSocket() {
        SocketChannel videoSc;
        try {
            videoSc = serverManager.getVideoSc();
        } catch (IOException e) {
            e.printStackTrace();
            finish();
            return;
        }
        while (isStart) {
            try {
                HEADER_BUFFER.clear();
                while (HEADER_BUFFER.hasRemaining()) {
                    videoSc.read(HEADER_BUFFER);
                }
                HEADER_BUFFER.flip();
                long pts = HEADER_BUFFER.getLong();
                int packetSize = HEADER_BUFFER.getInt();

                int inputBufferIndex = mCodec.dequeueInputBuffer(-1);

                if (inputBufferIndex >= 0) {
                    ByteBuffer inputBuffer = mCodec.getInputBuffer(inputBufferIndex);
                    if (inputBuffer == null) {
                        return;
                    }
                    inputBuffer.clear();
                    int i = 0;
                    Ln.d("packetSize");
                    while (i < packetSize) {
                        BYTE_BUFFER.position(0);
                        BYTE_BUFFER.limit(Math.min(packetSize - i, BYTES_LENGTH));
                        i += videoSc.read(BYTE_BUFFER);
                        Ln.d("socket channel.readssssss: " + i + "   " + packetSize);
                        videoSc.socket();
                        BYTE_BUFFER.flip();
                        inputBuffer.put(BYTE_BUFFER);
                        if (i < packetSize) {
                            SystemClock.sleep(5);
                        }
                    }
                    mCodec.queueInputBuffer(inputBufferIndex, 0, packetSize, pts, 0);
                }

            } catch (Throwable e) {
                Ln.e("listenSocket: ", e);
            }
        }


    }

    private void decodeOutput() {
        while (isStart) {
            try {
                int outputBufferIndex = mCodec.dequeueOutputBuffer(info, -1);
                if (outputBufferIndex >= 0) {
                    mCodec.releaseOutputBuffer(outputBufferIndex, true);
                }
            } catch (Throwable e) {
                Ln.e("decodeOutput: ", e);
            }
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void finish() {
        super.finish();
        if (mCodec != null) {
            mCodec.stop();
        }
        isStart = false;
    }
}
