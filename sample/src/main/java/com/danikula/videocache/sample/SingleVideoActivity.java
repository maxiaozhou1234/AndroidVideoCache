package com.danikula.videocache.sample;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

import org.androidannotations.annotations.EActivity;

@EActivity(R.layout.activity_single_video)
public class SingleVideoActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        if (state == null) {
//            String url = "android.resource://" + getPackageName() + "/" + R.raw.orange1;
//            String url = Environment.getExternalStorageDirectory() + "/Pictures/music_player.mp4";
            String url = Video.ORANGE_1.url;
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.containerView, VideoFragment.build(url))//Video.ORANGE_1.url
                    .commit();
        }
    }
}
