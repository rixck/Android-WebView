package com.digitalborder.webappessentials;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.NotificationCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class FragmentWebInteractive extends Fragment {

    public Context my_context;
    public View rootView;
    public ProgressDialog pd;
    public MediaPlayer mp;
    public NotificationManager mNotificationManager;
    public WebView webView;
    public SwipeRefreshLayout swipeContainer;
    private SharedPreferences preferences;
    public String loader;
    private ValueCallback<Uri[]> mFilePathCallback;
    private String mCameraPhotoPath;
    public static final int INPUT_FILE_REQUEST_CODE = 1;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        my_context = container.getContext();
        rootView = inflater.inflate(R.layout.fragment_web, container, false);
        preferences = PreferenceManager.getDefaultSharedPreferences(my_context);

        String type = getArguments().getString("type");
        String url = getArguments().getString("url");


        webView = (WebView) rootView.findViewById(R.id.webView);
        webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        // --------------- SWIPE CONTAINER ---------------
        swipeContainer = (SwipeRefreshLayout) rootView.findViewById(R.id.swipeContainer);
        // Setup refresh listener which triggers new data loading
        swipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                webView.reload();
            }
        });
        // Configure the refreshing colors
        swipeContainer.setColorSchemeResources(android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);


        // ------------------ WEBVIEW SETTINGS  --------------------
        WebSettings webSettings = webView.getSettings();

        // GET PREFERENCES
        if (preferences.getBoolean("pref_webview_cache", true)) {
            enableHTML5AppCache();
        }
        if (preferences.getBoolean("pref_webview_javascript", true)) {
            webSettings.setJavaScriptEnabled(true);
            webView.addJavascriptInterface(new WebAppInterface(my_context), "WebAppInterface");
        }

        // -------------------- LOADER ------------------------
        pd = new ProgressDialog(my_context);
        pd.setMessage("Please wait Loading...");


        loader = preferences.getString("pref_webview_loader_list", "dialog");

        if (loader.equals("pull")) {
            swipeContainer.setRefreshing(true);
        } else if (loader.equals("dialog")) {
            pd.show();
        } else if (loader.equals("never")) {
            Log.d("WebView", "No Loader selected");
        }


        webView.setWebViewClient(new MyWebViewClient());


        webView.setDownloadListener(new DownloadListener() {
            public void onDownloadStart(String url, String userAgent,
                                        String contentDisposition, String mimetype,
                                        long contentLength) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
            }
        });

        webView.setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY);
        webView.getSettings().setAllowFileAccess(true);



        // ---------------- LOADING CONTENT -----------------
        if (type.equals("file")) {
            webView.loadUrl("file:///android_asset/" + url);
        } else if (type.equals("url")) {
            webView.loadUrl(url);
        }

        //Update menu item on navigation drawer when press back button
        if (getArguments().getInt("item_position",99) != 99) {
            ((MainActivity) getActivity()).SetItemChecked(getArguments().getInt("item_position"));
        }

        webView.setWebChromeClient(new WebChromeClient() {
            public boolean onShowFileChooser(
                    WebView webView, ValueCallback<Uri[]> filePathCallback,
                    WebChromeClient.FileChooserParams fileChooserParams) {
                if(mFilePathCallback != null) {
                    mFilePathCallback.onReceiveValue(null);
                }
                mFilePathCallback = filePathCallback;

                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if (takePictureIntent.resolveActivity(getActivity().getPackageManager()) != null) {
                    // Create the File where the photo should go
                    File photoFile = null;
                    try {
                        photoFile = createImageFile();
                        takePictureIntent.putExtra("PhotoPath", mCameraPhotoPath);
                    } catch (IOException ex) {
                        // Error occurred while creating the File
                        Log.e("FragmentWeb", "Unable to create Image File", ex);
                    }

                    // Continue only if the File was successfully created
                    if (photoFile != null) {
                        mCameraPhotoPath = "file:" + photoFile.getAbsolutePath();
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,
                                Uri.fromFile(photoFile));
                    } else {
                        takePictureIntent = null;
                    }
                }

                Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
                contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
                contentSelectionIntent.setType("image/*");

                Intent[] intentArray;
                if(takePictureIntent != null) {
                    intentArray = new Intent[]{takePictureIntent};
                } else {
                    intentArray = new Intent[0];
                }

                Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
                chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
                chooserIntent.putExtra(Intent.EXTRA_TITLE, "Image Chooser");
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);

                startActivityForResult(chooserIntent, INPUT_FILE_REQUEST_CODE);

                return true;
            }
        });

        return rootView;

    }
    @Override
    public void onActivityResult (int requestCode, int resultCode, Intent data) {
        if(requestCode != INPUT_FILE_REQUEST_CODE || mFilePathCallback == null) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }

        Uri[] results = null;

        // Check that the response is a good one
        if(resultCode == Activity.RESULT_OK) {
            if(data == null) {
                // If there is not data, then we may have taken a photo
                if(mCameraPhotoPath != null) {
                    results = new Uri[]{Uri.parse(mCameraPhotoPath)};
                }
            } else {
                String dataString = data.getDataString();
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                }
            }
        }

        mFilePathCallback.onReceiveValue(results);
        mFilePathCallback = null;
        return;
    }
    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
        File imageFile = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
        return imageFile;
    }

    public Boolean canGoBack() {
        return webView.canGoBack();
    }

    public void GoBack() {
        webView.goBack();
    }

    private void enableHTML5AppCache() {
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAppCachePath("/data/data/" + getActivity().getPackageName() + "/cache");
        webView.getSettings().setAppCacheEnabled(true);
        webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
    }


    private class MyWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {


            if (loader.equals("pull")) {
                swipeContainer.setRefreshing(true);
            } else if (loader.equals("dialog")) {
                if (!pd.isShowing()) {
                    pd.show();
                }
            } else if (loader.equals("never")) {
                Log.d("WebView", "No Loader selected");
            }

            if (url != null && url.startsWith("file:///android_asset/[external]http")) {
                url = url.replace("file:///android_asset/[external]","");
                view.getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } else {
                view.loadUrl(url);
            }


            return true;
        }


        @Override
        public void onPageFinished(WebView view, String url) {
            if (pd.isShowing()) {
                pd.dismiss();
            }

            if (swipeContainer.isRefreshing()) {
                swipeContainer.setRefreshing(false);
            }
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            webView.loadUrl("file:///android_asset/" + getString(R.string.error_page));
        }
    }



    public class WebAppInterface {
        Context mContext;

        /**
         * Instantiate the interface and set the context
         */
        WebAppInterface(Context c) {
            mContext = c;
        }

        // -------------------------------- SHOW TOAST ---------------------------------------
        @JavascriptInterface
        public void showToast(String toast) {
            Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show();
        }

        // -------------------------------- START VIBRATE MP3 ---------------------------------------
        @JavascriptInterface
        public void vibrate(int milliseconds) {
            Vibrator v = (Vibrator) my_context.getSystemService(Context.VIBRATOR_SERVICE);
            // Vibrate for 500 milliseconds
            v.vibrate(milliseconds);
        }

        // -------------------------------- START PLAY MP3 ---------------------------------------
        @JavascriptInterface
        public void playSound() {
            mp = MediaPlayer.create(my_context, R.raw.demo);
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {

                @Override
                public void onCompletion(MediaPlayer mp) {
                    // TODO Auto-generated method stub
                    mp.release();
                }

            });
            mp.start();
        }

        // -------------------------------- STOP PLAY MP3 ---------------------------------------
        @JavascriptInterface
        public void stopSound() {
            if (mp.isPlaying()) {
                mp.stop();
            }
        }

        // -------------------------------- CREATE NOTIFICATION ---------------------------------------
        @JavascriptInterface
        public void newNotification(String title, String message) {
            mNotificationManager = (NotificationManager) my_context.getSystemService(Context.NOTIFICATION_SERVICE);

            PendingIntent contentIntent = PendingIntent.getActivity(my_context, 0, new Intent(my_context, MainActivity.class), 0);

            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(my_context)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(title)
                    .setStyle(new NotificationCompat.BigTextStyle()
                            .bigText(message))
                    .setContentText(message);

            mBuilder.setContentIntent(contentIntent);
            mNotificationManager.notify(1, mBuilder.build());
        }

        // -------------------------------- GET DATA ACCOUNT FROM DEVICE ---------------------------------------
        @JavascriptInterface
        public void snakBar(String message) {
            Snackbar.make(rootView, message, Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
        }
    }


}



