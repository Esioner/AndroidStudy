package com.esioner.photowall;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.GridView;

public class MainActivity extends AppCompatActivity {

    private GridViewAdapter adaper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        GridView gridView = findViewById(R.id.grid_view);
        adaper = new GridViewAdapter(this, 0, Images.imageThumbUrls, gridView);
        PhotoWallAdapter wallAdapter = new PhotoWallAdapter(this, 0, Images.imageThumbUrls, gridView);
        gridView.setAdapter(adaper);

    }

    @Override
    protected void onDestroy() {
        adaper.cancelAllTasks();
        super.onDestroy();
    }
}
