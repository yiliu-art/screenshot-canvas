package art.yiliu.screenshotcanvas;

import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebView;

import androidx.activity.OnBackPressedCallback;

import com.getcapacitor.BridgeActivity;
import com.getcapacitor.PluginHandle;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(ShareInboxPlugin.class);
        super.onCreate(savedInstanceState);
        ShareInboxPlugin.handleIntent(this, getIntent());

        // Back closes the full-screen viewer (it owns a history entry) before leaving the app
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                WebView wv = bridge != null ? bridge.getWebView() : null;
                if (wv != null && wv.canGoBack()) {
                    wv.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Any new intent (a share, or a relaunch after images were copied in) re-checks the inbox
        ShareInboxPlugin.handleIntent(this, intent);
        if (bridge != null) {
            PluginHandle h = bridge.getPlugin("ShareInbox");
            if (h != null && h.getInstance() instanceof ShareInboxPlugin) {
                ((ShareInboxPlugin) h.getInstance()).notifyShared();
            }
        }
    }
}
