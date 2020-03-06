package dongdong.server;

import android.graphics.Rect;
import android.media.MediaCodec;
import android.os.Build;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import dongdong.util.Ln;

public final class Server {

    private static final String SERVER_PATH = "/data/local/tmp/scrcpy-server.jar";

    private Server() {
        // not instantiable
    }

    private static void scrcpy(Options options) throws IOException {
        final Device device = new Device(options);
        boolean tunnelForward = options.isTunnelForward();
        try (DesktopConnection connection = DesktopConnection.open(device, tunnelForward)) {
            ScreenEncoder screenEncoder = new ScreenEncoder(options.getSendFrameMeta(), options.getBitRate(), options.getMaxFps());

            if (options.getControl()) {
                Controller controller = new Controller(device, connection);

                // asynchronous
                startController(controller);
//                startDeviceMessageSender(controller.getSender());
            }

            try {
                // synchronous
                screenEncoder.streamScreen(device, connection.getVideoFd());
            } catch (IOException e) {
                // this is expected on close
                Ln.d("Screen streaming stopped");
            }
        }
    }

    private static void startController(final Controller controller) {
        new Thread(() -> {
            try {
                controller.control();
            } catch (IOException e) {
                // this is expected on close
                Ln.d("Controller stopped");
            }
        }).start();
    }

    private static void startDeviceMessageSender(final DeviceMessageSender sender) {
        new Thread(() -> {
            try {
                sender.loop();
            } catch (IOException | InterruptedException e) {
                // this is expected on close
                Ln.d("Device message sender stopped");
            }
        }).start();
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private static Options createOptions(String... args) {
//        if (args.length < 1) {
//            throw new IllegalArgumentException("Missing client version");
//        }

        String clientVersion = args.length < 1 ? BuildConfig.VERSION_NAME : args[0];
        if (!clientVersion.equals(BuildConfig.VERSION_NAME)) {
            throw new IllegalArgumentException(
                    "The server version (" + clientVersion + ") does not match the client " + "(" + BuildConfig.VERSION_NAME + ")");
        }

//        if (args.length != 8) {
//            throw new IllegalArgumentException("Expecting 8 parameters");
//        }

        Options options = new Options();

        int maxSize = (args.length < 2 ? 0 : Integer.parseInt(args[1])) & ~7;
        options.setMaxSize(maxSize);

        int bitRate = args.length < 3 ? 2000000 : Integer.parseInt(args[2]);
        options.setBitRate(bitRate);

        int maxFps = args.length < 4 ? 0 : Integer.parseInt(args[3]);
        options.setMaxFps(maxFps);

        boolean tunnelForward = args.length < 5 || Boolean.parseBoolean(args[4]);
        options.setTunnelForward(tunnelForward);

        Rect rect = parseCrop(args.length < 6 ? "-" : args[5]);
        options.setCrop(rect);

        boolean sendFrameMeta = args.length < 7 || Boolean.parseBoolean(args[6]);
        options.setSendFrameMeta(sendFrameMeta);

        boolean control = args.length < 8 || Boolean.parseBoolean(args[7]);
        options.setControl(control);

        return options;
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private static Rect parseCrop(String crop) {
        if ("-".equals(crop)) {
            return null;
        }
        // input format: "width:height:x:y"
        String[] tokens = crop.split(":");
        if (tokens.length != 4) {
            throw new IllegalArgumentException("Crop must contains 4 values separated by colons: \"" + crop + "\"");
        }
        int width = Integer.parseInt(tokens[0]);
        int height = Integer.parseInt(tokens[1]);
        int x = Integer.parseInt(tokens[2]);
        int y = Integer.parseInt(tokens[3]);
        return new Rect(x, y, x + width, y + height);
    }

    private static void unlinkSelf() {
        try {
            new File(SERVER_PATH).delete();
        } catch (Exception e) {
            Ln.e("Could not unlink server", e);
        }
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private static void suggestFix(Throwable e) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (e instanceof MediaCodec.CodecException) {
                MediaCodec.CodecException mce = (MediaCodec.CodecException) e;
                if (mce.getErrorCode() == 0xfffffc0e) {
                    Ln.e("The hardware encoder is not able to encode at the given definition.");
                    Ln.e("Try with a lower definition:");
                    Ln.e("    scrcpy -m 1024");
                }
            }
        }
    }

    static {
        Ln.setTHRESHOLD(BuildConfig.DEBUG ? Ln.Level.DEBUG : Ln.Level.INFO);
        Ln.setLogPath(new File("/data/local/tmp/log"));
    }

    public static void main(String... args) throws Exception {
        Ln.e("ssssssssssssss: " + Arrays.toString(args));
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            Ln.e("Exception on thread " + t, e);
            suggestFix(e);
        });

//        unlinkSelf();
        Options options = createOptions(args);
        scrcpy(options);
    }
}
