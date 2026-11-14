package app.xylon.verification.youtube;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Only installed on CI emulators. Not included in the Xylon APK. */
public final class FixtureActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView label = new TextView(this);
        label.setText("Aplicación de verificación abierta por Xylon");
        label.setTextSize(24);
        label.setPadding(32, 64, 32, 32);
        setContentView(label);
    }
}
