package com.esioner.photowall;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v4.util.LruCache;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import java.io.InputStream;
import java.util.Set;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 手敲代码，因为导入的是 android 的 LruCache 包导致无法正常显示缓存，将起改为 v4 包恢复正常
 * Created by Administrator on 2017/11/24.
 */

public class GridViewAdapter extends ArrayAdapter implements AbsListView.OnScrollListener {
    /**
     * 图片缓存的核心类，用于缓存所有下载完成的图片，在程序内达到设定值将会将最近少使用的图片移除
     */
    private LruCache<String, Bitmap> mMemoryCache;
    /**
     * GridView 实例
     */
    private GridView mPhotoWall;
    /**
     * 记录所有正在下载或等待下载的任务
     */
    private Set<BitmapWorkerTask> taskCollection;
    /**
     * 第一张可见图片的下标
     */
    private int mFirstVisibleItem;
    /**
     * 一屏有多少张图片可见
     */
    private int mVisibleItemCount;
    /**
     * 记录是否第一次启动程序，用于解决进入程序不滚动屏幕不会下载图片
     */
    private boolean isFirstEnter = true;
    OkHttpClient client = new OkHttpClient();

    public GridViewAdapter(@NonNull Context context, int textViewResourceId, String[] objects, GridView photoWall) {
        super(context, textViewResourceId, objects);
        this.mPhotoWall = photoWall;
        //获取程序最大可用内存
        int maxMemory = (int) Runtime.getRuntime().maxMemory();
        //缓存最大可用内存
        int cacheSize = maxMemory / 8;
        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };
        mPhotoWall.setOnScrollListener(this);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final String url = (String) getItem(position);
        Log.d("String", "getView: " + url);
        View view;
        if (convertView == null) {
            view = LayoutInflater.from(getContext()).inflate(R.layout.photo_layout, null);
        } else {
            view = convertView;
        }
        final ImageView photo = view.findViewById(R.id.iv);
        photo.setTag(url);
        setImageView(url, photo);
        return view;
    }

    /**
     * 给 ImageView 设置图片，首先从 LRUCache 中取出图片，设置到 ImageView 上
     * 如果 LRUCache 中没有，就给 ImageVIew 设置一张默认图片
     *
     * @param url   图片的 URL 地址，作为 LRUCache 的 Key
     * @param photo 显示图片的控件
     */
    private void setImageView(String url, ImageView photo) {
        Bitmap bitmap = getBitmapMemoryCache(url);
        if (bitmap != null) {
            photo.setImageBitmap(bitmap);
        } else {
            photo.setImageResource(R.mipmap.ic_launcher_round);
        }
    }

    /**
     * 将图片缓存到内存中
     *
     * @param key
     * @param bitmap
     */
    private void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        if (mMemoryCache.get(key) == null) {
            mMemoryCache.put(key, bitmap);
        }
    }

    /**
     * 从 LRUCache 中获取图片
     *
     * @param url key
     * @return bitmap
     */
    private Bitmap getBitmapMemoryCache(String url) {
        return mMemoryCache.get(url);
    }


    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        //仅当 GridView 静止时才去下载图片，GridView 滑动时取消所有正在下载的任务
        if (scrollState == SCROLL_STATE_IDLE) {
            loadBitmaps(mFirstVisibleItem, mVisibleItemCount);
        } else {
            cancelAllTasks();
        }
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        mFirstVisibleItem = firstVisibleItem;
        mVisibleItemCount = visibleItemCount;
        //下载任务应该由 onScrollStateChanged 里调用，但首次进入程序时 onScrollStateChanged 并不会调用
        if (isFirstEnter && visibleItemCount > 0) {
            loadBitmaps(firstVisibleItem, visibleItemCount);
            isFirstEnter = false;
        }
    }

    /**
     * 加载 Bitmap 对象，此方法会在 LRUCache 中检查所有屏幕中可见的 ImageView 的 Bitmap 对象
     * 如果发现任何一个 ImageView 的 Bitmap 对象不再缓存中，就会开启异步线程去加载图片
     *
     * @param firstVisibleItem 第一个可见的 ImageView 的下标
     * @param visibleItemCount 屏幕中可见的元素总数
     */
    public void loadBitmaps(int firstVisibleItem, int visibleItemCount) {
        try {
            for (int i = firstVisibleItem; i < firstVisibleItem + visibleItemCount; i++) {
                String imageUrl = Images.imageThumbUrls[i];
                Bitmap bitmap = getBitmapMemoryCache(imageUrl);
                if (bitmap == null) {
                    BitmapWorkerTask task = new BitmapWorkerTask();
                    taskCollection.add(task);
                    task.execute(imageUrl);
                } else {
                    ImageView imageView = mPhotoWall.findViewWithTag(imageUrl);
                    if (imageView != null && bitmap != null) {
                        imageView.setImageBitmap(bitmap);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void cancelAllTasks() {
        if (taskCollection != null) {
            for (BitmapWorkerTask task : taskCollection) {
                task.cancel(false);
            }
        }
    }

    class BitmapWorkerTask extends AsyncTask<String, Void, Bitmap> {

        private String imageUrl;

        @Override
        protected Bitmap doInBackground(String... params) {
            imageUrl = params[0];
            //在后台开始下载图片
            Bitmap bitmap = downloadBitmap(imageUrl);
            if (bitmap != null) {
                //图片下载完成存到缓存中去
                addBitmapToMemoryCache(imageUrl, bitmap);
            }
            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);
            //根据 Tag 找到相应的 ImageView 控件，将下载的图片显示出来
            ImageView imageView = mPhotoWall.findViewWithTag(imageUrl);
            if (imageView != null && bitmap != null) {
                imageView.setImageBitmap(bitmap);
            }
            taskCollection.remove(this);
        }
    }

    //下载 Bitmap
    private Bitmap downloadBitmap(String imageUrl) {
        Bitmap bitmap = null;
        try {
            Request request = new Request.Builder().url(imageUrl).build();
            Response response = client.newCall(request).execute();
            InputStream is = response.body().byteStream();
            Log.i("bitmap__", "downloadBitmap: " + is);
            bitmap = BitmapFactory.decodeStream(is);
//            URL url = new URL(imageUrl);
//            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
//            connection.setConnectTimeout(5000);
//            connection.setReadTimeout(5000);
//            if (connection.getResponseCode() == 200) {
//            bitmap = BitmapFactory.decodeStream(connection.getInputStream());
//            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bitmap;
    }

}
