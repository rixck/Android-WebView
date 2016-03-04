package com.digitalborder.webappessentials;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    public boolean doubleBackToExitPressedOnce = false;
    private InterstitialAd interstitial;
    private NavigationView navigationView;
    public Timer AdTimer;

    // GCM
    public static final String PROPERTY_REG_ID = "notifyId";
    private static final String PROPERTY_APP_VERSION = "appVersion";
    GoogleCloudMessaging gcm;
    SharedPreferences preferences;
    String reg_cgm_id;
    static final String TAG = "MainActivity";
    private boolean first_fragment = false;

    @Override
    protected void onPause() {
        super.onPause();
        if (AdTimer != null) {
            AdTimer.cancel();
            AdTimer = null;
        }
    }

   
    

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        if (getString(R.string.rtl_version).equals("true")) {
            getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        }

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        final DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);


        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (drawer.isDrawerOpen(GravityCompat.START)) {
                    drawer.closeDrawer(GravityCompat.START);
                } else {
                    drawer.openDrawer(GravityCompat.START);
                }
            }
        });

        // Go to first fragment
        Intent intent = getIntent();
        if (intent.getExtras() != null && intent.getExtras().getString("link", null) != null && !intent.getExtras().getString("link", null).equals("")) {
            String url = null;
            if (intent.getExtras().getString("link").contains("http")) {
                url = intent.getExtras().getString("link");
            } else {
                url = "http://" + intent.getExtras().getString("link");
            }

            Bundle bundle = new Bundle();
            bundle.putString("type", "url");
            bundle.putString("url", url);
            Fragment fragment = new FragmentWebInteractive();
            fragment.setArguments(bundle);
            FragmentManager fragmentManager = getSupportFragmentManager();
            fragmentManager.beginTransaction().replace(R.id.frame_container, fragment, "FragmentWebInteractive").commit();
            first_fragment = true;

        } else if (savedInstanceState == null) {
            Bundle bundle = new Bundle();
            bundle.putString("type", getString(R.string.home_type));
            bundle.putString("url", getString(R.string.home_url));
            Fragment fragment = new FragmentWebInteractive();
            fragment.setArguments(bundle);
            FragmentManager fragmentManager = getSupportFragmentManager();
            fragmentManager.beginTransaction().replace(R.id.frame_container, fragment, "FragmentWebInteractive").commit();
            first_fragment = true;
        }

        // GCM
        if (checkPlayServices()) {
            gcm = GoogleCloudMessaging.getInstance(getApplicationContext());
            String reg_cgm_id = getRegistrationId(getApplicationContext());
            Log.i(TAG, "Play Services Ok.");
            if (reg_cgm_id.isEmpty()) {
                Log.i(TAG, "Find Register ID.");
                registerInBackground();
            }
        } else {
            Log.i(TAG, "No valid Google Play Services APK found.");
        }

        // -------------------------------  AdMob Banner ------------------------------------------------------------
        AdView adView = (AdView) findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().setRequestAgent("android_studio:ad_template").build();
        adView.loadAd(adRequest);

        // -------------------------------- AdMob Interstitial ----------------------------
        // Prepare the Interstitial Ad
        interstitial = new InterstitialAd(MainActivity.this);
        // Insert the Ad Unit ID
        interstitial.setAdUnitId(getString(R.string.interstitial_ad_unit_id));

        // Load ads into Interstitial Ads
        interstitial.loadAd(adRequest);

        AdTimer = new Timer();

        // Prepare an Interstitial Ad Listener
        interstitial.setAdListener(new AdListener() {
            public void onAdLoaded() {
                // Call displayInterstitial() function with timer
                if (AdTimer != null) {
                    AdTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    displayInterstitial();
                                }
                            });
                        }
                    }, Integer.parseInt(getString(R.string.admob_interstiial_delay)));
                }
            }
        });
        
        AppRater.app_launched(this);
        //rate us
        

    }


//    private void onShareAction(){
//        // Create the share Intent
//        String playStoreLink = "https://play.google.com/store/apps/details?id=" + getPackageName();
//        String yourShareText = getResources().getString(R.string.share_text) + playStoreLink;
//        Intent shareIntent = ShareCompat.IntentBuilder.from(this).setType("text/plain").setText(yourShareText).getIntent();
//        // Set the share Intent
//        if (mShareActionProvider != null) {
//            mShareActionProvider.setShareIntent(shareIntent);
//        }
//    }

    public void displayInterstitial() {
        // If Ads are loaded, show Interstitial else show nothing.
        if (interstitial.isLoaded()) {
            interstitial.show();
        }
    }

    @Override
    public void onBackPressed() {

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        }

        Fragment webviewfragment = getSupportFragmentManager().findFragmentByTag("FragmentWebInteractive");
        if (webviewfragment instanceof FragmentWebInteractive) {
            if (((FragmentWebInteractive) webviewfragment).canGoBack()) {
                ((FragmentWebInteractive) webviewfragment).GoBack();


                return;
            }
        }

        if (doubleBackToExitPressedOnce) {
            finish();
            return;
        } else {
            if (first_fragment == false) {
                super.onBackPressed();
            }
        }

        this.doubleBackToExitPressedOnce = true;
        Toast.makeText(this, "Please click BACK again to exit", Toast.LENGTH_SHORT).show();

        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {
                doubleBackToExitPressedOnce = false;
            }
        }, 1500);


    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();
        Fragment fragment = null;
        String tag = null;
        first_fragment = false;

        if (id == R.id.home) {

            Bundle bundle = new Bundle();
            bundle.putInt("item_position", 0);
            bundle.putString("type", getString(R.string.home_type));
            bundle.putString("url", getString(R.string.home_url));
            fragment = new FragmentWebInteractive();
            fragment.setArguments(bundle);
            tag = "FragmentWebInteractive";
            first_fragment = true;

        } else if (id == R.id.about_us) {

            Bundle bundle = new Bundle();
            bundle.putInt("item_position", 1);
            bundle.putString("type", getString(R.string.about_us_type));
            bundle.putString("url", getString(R.string.about_us_url));
            fragment = new FragmentWebInteractive();
            fragment.setArguments(bundle);
            tag = "FragmentWebInteractive";

        } else if (id == R.id.portfolio) {

            Bundle bundle = new Bundle();
            bundle.putInt("item_position", 2);
            bundle.putSerializable("item_id", R.id.portfolio);
            bundle.putString("type", getString(R.string.portfolio_type));
            bundle.putString("url", getString(R.string.portfolio_url));
            fragment = new FragmentWebInteractive();
            fragment.setArguments(bundle);
            tag = "FragmentWebInteractive";

        } else if (id == R.id.contacts) {
            fragment = new FragmentContacts();
            tag = "FragmentContacts";
        }

        // ##################### --------------- EXAMPLE ----------------------- #################

//        else if (id == R.id.nav_1) {
//            Intent i = new Intent(getBaseContext(), SettingsActivity.class);
//            startActivity(i);
//            return true;
//
//        } else if (id == R.id.nav_2) {
//            // ---------------------------------  Load WebiView with Remote URL -------------------- //
//            Bundle bundle = new Bundle();
//            bundle.putString("type", getString(R.string.remote_type));
//            bundle.putString("url", getString(R.string.remote_url));
//            fragment = new FragmentWebInteractive();
//            fragment.setArguments(bundle);
//            tag = "FragmentWebInteractive";
//
//        } else if (id == R.id.nav_3) {
//            // ---------------------------------  Load WebiView with Remote URL -------------------- //
//            Bundle bundle = new Bundle();
//            bundle.putString("type", getString(R.string.interactive_type));
//            bundle.putString("url", getString(R.string.interactive_url));
//            fragment = new FragmentWebInteractive();
//            fragment.setArguments(bundle);
//            tag = "FragmentWebInteractive";
//
//        } else if (id == R.id.nav_4) {
//            // ---------------------------------  Load WebiView with Remote URL -------------------- //
//            Bundle bundle = new Bundle();
//            bundle.putString("type", getString(R.string.credits_type));
//            bundle.putString("url", getString(R.string.credits_url));
//            fragment = new FragmentWebInteractive();
//            fragment.setArguments(bundle);
//            tag = "FragmentWebInteractive";
//
//        }

        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction().replace(R.id.frame_container, fragment, tag).addToBackStack(null).commit();

        setTitle(item.getTitle());
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    public void SetItemChecked(int position) {
        navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.getMenu().getItem(position).setChecked(true);
    }

    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(MainActivity.this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this, 9000).show();
            } else {
                Log.i(TAG, "This device is not supported.");
            }
            return false;
        }
        return true;
    }

    /**
     * Stores the registration ID and the app versionCode in the application's
     * {@code SharedPreferences}.
     *
     * @param context application's context.
     * @param regId   registration ID
     */
    private void storeRegistrationId(Context context, String regId) {
        int appVersion = getAppVersion(context);
        Log.i(TAG, "Saving regId on app version " + appVersion);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(PROPERTY_REG_ID, regId);
        editor.putInt(PROPERTY_APP_VERSION, appVersion);
        editor.commit();
    }

    /**
     * Gets the current registration ID for application on GCM service, if there is one.
     * <p/>
     * If result is empty, the app needs to register.
     *
     * @return registration ID, or empty string if there is no existing
     * registration ID.
     */
    private String getRegistrationId(Context context) {
        String registrationId = preferences.getString(PROPERTY_REG_ID, "");
        if (registrationId.isEmpty()) {
            Log.i(TAG, "Registration not found.");
            return "";
        }
        // Check if app was updated; if so, it must clear the registration ID
        // since the existing regID is not guaranteed to work with the new
        // app version.
        int registeredVersion = preferences.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);
        if (registeredVersion != currentVersion) {
            Log.i(TAG, "App version changed.");
            return "";
        }
        return registrationId;
    }

    /**
     * Registers the application with GCM servers asynchronously.
     * <p/>
     * Stores the registration ID and the app versionCode in the application's
     * shared preferences.
     */
    private void registerInBackground() {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                String msg = "";
                try {
                    if (gcm == null) {
                        gcm = GoogleCloudMessaging.getInstance(MainActivity.this);
                    }
                    reg_cgm_id = gcm.register(getString(R.string.google_api_sender_id));
                    msg = "Device registered, registration ID=" + reg_cgm_id;
                    Log.d(TAG, "ID GCM: " + reg_cgm_id);

                    // You should send the registration ID to your server over HTTP, so it
                    // can use GCM/HTTP or CCS to send messages to your app.
                    sendRegistrationIdToBackend();

                    // For this demo: we don't need to send it because the device will send
                    // upstream messages to a server that echo back the message using the
                    // 'from' address in the message.

                    // Persist the regID - no need to register again.
                    storeRegistrationId(MainActivity.this, reg_cgm_id);
                } catch (IOException ex) {
                    msg = "Error :" + ex.getMessage();
                    // If there is an error, don't just keep trying to register.
                    // Require the user to click a button again, or perform
                    // exponential back-off.
                }
                return msg;
            }

            @Override
            protected void onPostExecute(String msg) {

            }
        }.execute(null, null, null);
    }

    /**
     * @return Application's version code from the {@code PackageManager}.
     */
    private static int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            // should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    /**
     * Sends the registration ID to your server over HTTP, so it can use GCM/HTTP or CCS to send
     * messages to your app. Not needed for this demo since the device sends upstream messages
     * to a server that echoes back the message using the 'from' address in the message.
     */
    private void sendRegistrationIdToBackend() {

        // Register GCM Token ID to server
        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
        nameValuePairs.add(new BasicNameValuePair("token", reg_cgm_id));
        new HttpTask(null, MainActivity.this, getString(R.string.server_url), nameValuePairs, false).execute();

    }
}
