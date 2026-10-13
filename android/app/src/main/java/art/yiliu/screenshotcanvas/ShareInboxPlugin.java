package art.yiliu.screenshotcanvas;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Receives images shared into the app (Android share sheet) and copies them into
 * an inbox folder in the app's storage. The folder is the queue: take() hands every
 * file in it to the web layer, which calls release() once the image is saved.
 */
@CapacitorPlugin(name = "ShareInbox")
public class ShareInboxPlugin extends Plugin {

    /** Paths already handed to the web layer in this process, awaiting release(). */
    private static final java.util.Set<String> handedOut = new java.util.HashSet<>();

    static File inbox(Context ctx) {
        File dir = new File(ctx.getFilesDir(), "inbox");
        dir.mkdirs();
        return dir;
    }

    /** Called by MainActivity for launch and new intents. Returns true if images were queued. */
    static boolean handleIntent(Context ctx, Intent intent) {
        if (intent == null) return false;
        String action = intent.getAction();
        String type = intent.getType();
        if (type == null || !type.startsWith("image/")) return false;

        List<Uri> uris = new ArrayList<>();
        if (Intent.ACTION_SEND.equals(action)) {
            Uri u = getStream(intent);
            if (u != null) uris.add(u);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> list = Build.VERSION.SDK_INT >= 33
                ? intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri.class)
                : intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (list != null) uris.addAll(list);
        } else {
            return false;
        }

        File dir = inbox(ctx);
        ContentResolver cr = ctx.getContentResolver();
        boolean any = false;
        for (Uri uri : uris) {
            try {
                String name = displayName(cr, uri);
                File out = new File(dir, System.nanoTime() + "-" + name.replaceAll("[^A-Za-z0-9._-]", "_"));
                try (InputStream in = cr.openInputStream(uri); OutputStream os = new FileOutputStream(out)) {
                    if (in == null) continue;
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                }
                any = true;
            } catch (Exception ignored) {
            }
        }
        // Consume the intent so a config change doesn't re-import the same images
        intent.setAction(null);
        return any;
    }

    @SuppressWarnings("deprecation")
    private static Uri getStream(Intent intent) {
        return Build.VERSION.SDK_INT >= 33
            ? intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class)
            : (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
    }

    private static String displayName(ContentResolver cr, Uri uri) {
        try (Cursor c = cr.query(uri, new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String s = c.getString(0);
                if (s != null && !s.isEmpty()) return s;
            }
        } catch (Exception ignored) {
        }
        return "Shared image";
    }

    void notifyShared() {
        notifyListeners("shared", new JSObject());
    }

    /** Returns inbox files ({path, name, type}) not yet handed out. */
    @PluginMethod
    public void take(PluginCall call) {
        JSArray files = new JSArray();
        File[] list = inbox(getContext()).listFiles();
        if (list != null) {
            java.util.Arrays.sort(list);
            synchronized (handedOut) {
                for (File f : list) {
                    if (!f.isFile() || handedOut.contains(f.getAbsolutePath())) continue;
                    handedOut.add(f.getAbsolutePath());
                    // stored as "<nanotime>-<original name>"
                    String name = f.getName().replaceFirst("^\\d+-", "");
                    JSObject o = new JSObject();
                    o.put("path", f.getAbsolutePath());
                    o.put("name", name);
                    o.put("type", mimeFor(name));
                    files.put(o);
                }
            }
        }
        JSObject ret = new JSObject();
        ret.put("files", files);
        call.resolve(ret);
    }

    /** Deletes an inbox file once the web layer has stored it. */
    @PluginMethod
    public void release(PluginCall call) {
        String path = call.getString("path");
        if (path != null) {
            File f = new File(path);
            if (inbox(getContext()).equals(f.getParentFile())) f.delete();
            synchronized (handedOut) { handedOut.remove(path); }
        }
        call.resolve();
    }

    private static String mimeFor(String name) {
        String n = name.toLowerCase(java.util.Locale.US);
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".heic") || n.endsWith(".heif")) return "image/heic";
        return "image/jpeg";
    }
}
