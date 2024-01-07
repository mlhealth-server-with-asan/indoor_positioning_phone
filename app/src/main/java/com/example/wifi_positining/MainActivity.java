package com.example.wifi_positining;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private EditText serverAddressInput;
    private EditText positionInput;
    private String positionText;
    private TextView resultText;
    private Button addDatasetBtn;
    private Button findPositionBtn;

    private WifiManager wifiManager;

    private String serverAddress;
    private String URL;

    JSONObject one_wifi_json = new JSONObject();
    JSONObject result_json = new JSONObject();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        Log.d("MainActivity", "앱이 시작되었습니다.");
        serverAddressInput = findViewById(R.id.serverAddressInput);
        addDatasetBtn = findViewById(R.id.addDatasetBtn);
        findPositionBtn = findViewById(R.id.findPositionBtn);
        positionInput = findViewById(R.id.positionInput);
        resultText = findViewById(R.id.resultText);

        IntentFilter filter = new IntentFilter("com.example.wifi_positining.FIND_POSITION");
        registerReceiver(findPositionReceiver, filter);

        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            Log.d("permission","checkSelfPermission");
            if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                    Manifest.permission.ACCESS_COARSE_LOCATION)) {
                Log.d("permission","shouldShowRequestPermissionRationale");
                // 사용자에게 설명을 보여줍니다.
                // 권한 요청을 다시 시도합니다.
            } else {
                // 권한요청
                Log.d("permission","권한 요청");
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_WIFI_STATE,Manifest.permission.CHANGE_WIFI_STATE},
                        1000);
            }

        }


        addDatasetBtn.setOnClickListener(view -> {
            serverAddress = serverAddressInput.getText().toString();
            positionText = positionInput.getText().toString();

            if (serverAddress.equals("") || positionText.equals("")) {
                resultText.setText("Please input server address and position");
            } else {
                URL = serverAddress + "/api/addData";
                wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                scanWifi();
                addDatasetBtn.setEnabled(false);
                findPositionBtn.setEnabled(false);
            }
        });

        findPositionBtn.setOnClickListener(v -> {
            serverAddress = serverAddressInput.getText().toString();
            positionText = "";
            if (serverAddress.equals("")) {
                resultText.setText("Please input server address");
            } else {
                URL = serverAddress + "/findPosition";
                Log.d("test12", URL);
                wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                scanWifi();
                addDatasetBtn.setEnabled(false);
                findPositionBtn.setEnabled(false);
            }
        });

        startForegroundService();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter("com.example.wifi_positining.FIND_POSITION");
        registerReceiver(findPositionReceiver, filter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(findPositionReceiver);
    }

    private BroadcastReceiver findPositionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            findPosition();
        }
    };

    private void findPosition() {
        serverAddress = serverAddressInput.getText().toString();
        positionText = "";
        if (!serverAddress.equals("")) {
            URL = serverAddress + "/findPosition";
            Log.d("test12", URL);
            wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            scanWifi();
            addDatasetBtn.setEnabled(false);
            findPositionBtn.setEnabled(false);
        }
    }

    private void startForegroundService() {
        Intent serviceIntent = new Intent(this, ForegroundService.class);
        // 필요한 경우, 서비스에 데이터를 전달합니다.
        // 예: serviceIntent.putExtra("key", "value");

        ContextCompat.startForegroundService(this, serviceIntent);
    }

    private void scanWifi() {
        registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        wifiManager.startScan();
    }

    BroadcastReceiver wifiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            List<ScanResult> scanResultList = wifiManager.getScanResults();
            unregisterReceiver(this);
            // scan result 정렬
            scanResultList.sort((s1, s2) -> s2.level - s1.level);


            TextView logTextView = findViewById(R.id.app_log);
            String scanLog = "";
            for (ScanResult scanResult : scanResultList) {
                scanLog += "BSSID: " + scanResult.BSSID + "  level: " + scanResult.level + "\n";
            }
            logTextView.setText(scanLog);

            // 서버에 보낼 JSON 설정 부분
            try {
                result_json.put("position", positionText);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            JSONArray json_array = new JSONArray();
            for (ScanResult scanResult : scanResultList) {
                one_wifi_json = new JSONObject();
                String bssid = scanResult.BSSID;
                int rssi = scanResult.level;

                try {
                    one_wifi_json.put("bssid", bssid);
                    one_wifi_json.put("rssi", rssi);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                json_array.put(one_wifi_json);
            }
            try {
                result_json.put("wifi_data", json_array);

                EditText passwordText = findViewById(R.id.passwordInput);
                result_json.put("password", passwordText.getText().toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }

            // 서버와 통신하는 부분
            try {
                RequestQueue requestQueue = Volley.newRequestQueue(getApplicationContext());
                String mRequestBody = result_json.toString(); // json 을 통신으로 보내기위해 문자열로 변환하는 부분

                StringRequest stringRequest = new StringRequest(Request.Method.POST, URL, response -> {
                    Log.i("test12", response);
                    resultText.setText(response); // 결과 출력해주는 부분
                    addDatasetBtn.setEnabled(true);
                    findPositionBtn.setEnabled(true);
                }, error -> {
                    Log.e("test12", error.toString());
                    resultText.setText("Connection error! Check server address!\nExample : https://example.com");
                    addDatasetBtn.setEnabled(true);
                    findPositionBtn.setEnabled(true);
                }) {
                    @Override
                    public String getBodyContentType() {
                        return "application/json; charset=utf-8";
                    }

                    @Override
                    public byte[] getBody() { // 요청 보낼 데이터를 처리하는 부분
                        return mRequestBody.getBytes(StandardCharsets.UTF_8);
                    }

                    @Override
                    protected Response<String> parseNetworkResponse(NetworkResponse response) { // onResponse 에 넘겨줄 응답을 처리하는 부분
                        String responseString = "";
                        if (response != null) {
                            responseString = new String(response.data, StandardCharsets.UTF_8); // 응답 데이터를 변환해주는 부분
                        }
                        return Response.success(responseString, HttpHeaderParser.parseCacheHeaders(response));
                    }
                };
                requestQueue.add(stringRequest);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };
}