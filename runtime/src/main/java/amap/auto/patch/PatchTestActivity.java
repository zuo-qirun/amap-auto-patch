package amap.auto.patch;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class PatchTestActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PatchRuntime.init(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(32, 32, 32, 32);

        TextView title = new TextView(this);
        title.setText("AMap Auto Patch VM Test");
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        Button show = new Button(this);
        show.setText("Show Overlay");
        show.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PatchRuntime.showTestOverlay(PatchTestActivity.this);
            }
        });
        root.addView(show, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
        root.postDelayed(new Runnable() {
            @Override
            public void run() {
                PatchRuntime.showTestOverlay(PatchTestActivity.this);
            }
        }, 600);
    }
}
