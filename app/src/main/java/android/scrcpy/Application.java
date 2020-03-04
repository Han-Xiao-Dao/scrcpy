package android.scrcpy;

import dongdong.util.Ln;

public class Application extends android.app.Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Ln.setLogPath(getExternalFilesDir("log"));
        Ln.setTHRESHOLD(BuildConfig.DEBUG ? Ln.Level.DEBUG : Ln.Level.INFO);
    }
}
