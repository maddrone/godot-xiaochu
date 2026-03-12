package com.kawaiimatch;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

/**
 * Single-activity host for the game.
 * Sets up a full-screen window and adds the GameSurfaceView as the content.
 */
public class MainActivity extends Activity {

    private GameSurfaceView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Full-screen, no title bar, keep screen on
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        gameView = new GameSurfaceView(this);
        setContentView(gameView);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // The SurfaceView's surfaceDestroyed callback stops the thread;
        // nothing extra needed here.
    }

    @Override
    protected void onResume() {
        super.onResume();
    }
}
