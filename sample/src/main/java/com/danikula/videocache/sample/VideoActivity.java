package com.danikula.videocache.sample;

import android.app.Activity;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.VideoView;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.ViewById;

/**
 * Created by Administrator_ma on 2019/4/19.
 */
@EActivity(R.layout.activity_video)
public class VideoActivity extends Activity {

    @ViewById(R.id.videoTest)
    VideoView videoTest;

    private int count = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @AfterViews
    void afterViewInjected() {
//        videoTest.setVideoURI(Uri.parse("http://s3.bytecdn.cn/aweme/resource/web/static/image/index/tvc-v2_30097df.mp4"));
        videoTest.setVideoURI(Uri.parse("https://raw.githubusercontent.com/maxiaozhou1234/AndroidVideoCache/master/files/orange1.mp4"));
        videoTest.setOnPreparedListener(mp -> videoTest.start());
        videoTest.setOnErrorListener((MediaPlayer mp, int what, int extra) -> {
            videoTest.start();
            Log.e("zhou", "what = " + what + " extra = " + extra);
            if (count++ < 3) {
                videoTest.suspend();
                videoTest.setVideoURI(Uri.parse("https://raw.githubusercontent.com/maxiaozhou1234/AndroidVideoCache/master/files/orange1.mp4"));
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        videoTest.stopPlayback();
    }
}
