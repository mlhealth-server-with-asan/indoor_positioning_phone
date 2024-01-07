package com.example.wifi_positining;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;

/*
This is WiFi Rtt implementation code.
This is code for testing and is not yet actually used in apps.
*/
public class WifiScanActivity extends AppCompatActivity {

    private WifiManager wifiManager;
    private Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_rtt_test);

        Button button = findViewById(R.id.button);

        context = getApplicationContext();
        wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        // scan failure handling
        BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
                if (success) {
                    Log.d("test12", "Scan Success!");
                    scanSuccess();
                } else {
                    // scan failure handling
                    Log.d("test12", "Scan Fail!");
                    scanFailure();
                }
            }
        };


        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        context.registerReceiver(wifiScanReceiver, intentFilter);

        button.setOnClickListener(view -> {
            boolean success = wifiManager.startScan();
            if (!success) {
                // scan failure handling
                Log.d("test12", "Scan Fail1");
                scanFailure();
            } else {
                Log.d("test12", "Scan Success1");
            }
        });


    }

    private void scanSuccess() {
        List<ScanResult> results = wifiManager.getScanResults();
        List<ScanResult> trueResults = new ArrayList<>();
        RangingRequest.Builder builder = new RangingRequest.Builder();
        builder.addAccessPoints(results);

        for(ScanResult result : results){
            trueResults.add(result);
//            Log.d("test12", result.toString());
//            if(result.is80211mcResponder()){
//                trueResults.add(result);
//            }
            if(trueResults.size() >= RangingRequest.getMaxPeers()){
                break;
            }
        }


        for (int i = 0; i < results.size(); i++) {
            Log.d("test12", results.get(i).toString()+"\n\n\n");
        }

        for (int i = 0; i < trueResults.size(); i++) {
            Log.d("test13", trueResults.get(i).toString());
        }

        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_RTT)) {
            Log.d("test12", "Rtt config!!");
        }

        WifiRttManager wifiRttManager = (WifiRttManager) context.getSystemService(Context.WIFI_RTT_RANGING_SERVICE);

        if (wifiRttManager.isAvailable()) {
            Log.d("test12", "Rtt available!!");
        } else {
            Log.d("test12", "Rtt not available");
        }

        RangingRequest trueRequest = new RangingRequest.Builder().addAccessPoints(trueResults).build();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        wifiRttManager.startRanging(trueRequest, getApplication().getMainExecutor(), new RangingResultCallback() {

            @Override
            public void onRangingFailure(int code) {
                Log.d("test12", "onRangingFailure() code: " + code);
            }

            @Override
            public void onRangingResults(List<RangingResult> results) {
                //for(int i=0;i<results.size();i++){

                Log.d("test12", "onRangingResults():" +results);
                //}
            }
        });
    }

    private void scanFailure() {
        // handle failure: new scan did NOT succeed
        // consider using old scan results: these are the OLD results!
        List<ScanResult> results = wifiManager.getScanResults();
    }
}