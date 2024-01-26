package com.example.wifi_positining;
import android.content.ClipData;
import android.content.ClipDescription;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;


import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1;
    private BroadcastReceiver updateUIReceiver;
    private BeaconManager beaconManager;
    private EditText serverAddressInput;
    private EditText positionInput;
    private String positionText;
    private TextView resultText;
    private Button addDatasetBtn;
    private Button findPositionBtn;
    private WifiManager wifiManager;
    private String serverAddress;

    private boolean isServiceRunning = false;

    private String URL;
    JSONObject one_wifi_json = new JSONObject();
    JSONObject result_json = new JSONObject();

    private HashMap<String, Integer> beaconDataMap = new HashMap<String, Integer>();
    private RelativeLayout layoutDesignPlan;
    private ImageView iconImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // BeaconManager 초기화
        beaconManager = BeaconManager.getInstanceForApplication(this);

        beaconManager.getBeaconParsers().add(new BeaconParser().
                setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));

        Log.d("MainActivity", "앱이 시작되었습니다.");
        serverAddressInput = findViewById(R.id.serverAddressInput);
        addDatasetBtn = findViewById(R.id.addDatasetBtn);
        findPositionBtn = findViewById(R.id.findPositionBtn);
        positionInput = findViewById(R.id.positionInput);
        resultText = findViewById(R.id.resultText);
        EditText passwordText = findViewById(R.id.passwordInput);
        Button selectImageButton = findViewById(R.id.ImageButton);
        selectImageButton.setOnClickListener(v -> openImageChooser());
        IntentFilter filter = new IntentFilter("com.example.wifi_positining.FIND_POSITION");
        registerReceiver(findPositionReceiver, filter);


        iconImageView = findViewById(R.id.iconImageView);
        layoutDesignPlan = findViewById(R.id.layoutDesignPlan);


        // 아이콘을 보이게 설정하고 드래그 가능하게 만들기
        iconImageView.setVisibility(View.VISIBLE);
        iconImageView.setOnTouchListener(new View.OnTouchListener() {
            private long lastTouchDown = 0;
            private int clickCount = 0;
            private static final long DOUBLE_CLICK_TIME_DELTA = 300; // milliseconds

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        long thisTime = System.currentTimeMillis();
                        if (thisTime - lastTouchDown < DOUBLE_CLICK_TIME_DELTA) {
                            // 더블 클릭 감지
                            clickCount++;
                            if (clickCount == 2) {
                                ClipData.Item item = new ClipData.Item((CharSequence) view.getTag());
                                ClipData dragData = new ClipData((CharSequence) view.getTag(), new String[]{ClipDescription.MIMETYPE_TEXT_PLAIN}, item);
                                View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(view);
                                view.startDragAndDrop(dragData, shadowBuilder, null, 0);
                                showLabelInput(view.getX(), view.getY());
                                clickCount = 0;
                            }
                        } else {
                            clickCount = 1;
                        }
                        lastTouchDown = thisTime;
                        View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(view);
                        view.startDrag(null, shadowBuilder, view, 0);
                        break;
                    case MotionEvent.ACTION_UP:
                        // 드래그 완료 후 처리
                        break;
                }
                return true;
            }
        });


        layoutDesignPlan.setOnDragListener(new View.OnDragListener() {
            @Override
            public boolean onDrag(View view, DragEvent event) {
                switch (event.getAction()) {
                    case DragEvent.ACTION_DROP:
                        float x = event.getX();
                        float y = event.getY();

                        // 아이콘의 새 위치 설정
                        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                                RelativeLayout.LayoutParams.WRAP_CONTENT,
                                RelativeLayout.LayoutParams.WRAP_CONTENT);
                        params.leftMargin = (int)x - iconImageView.getWidth() / 2;
                        params.topMargin = (int)y - iconImageView.getHeight() / 2;
                        iconImageView.setLayoutParams(params);
                        break;
                }
                return true;
            }
        });

        // Define the BroadcastReceiver
        updateUIReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Get the response from the Intent
                String response = intent.getStringExtra("response");
                // Set the text of resultText
                resultText.setText(response);
            }
        };

        IntentFilter uiUpdateFilter = new IntentFilter("ACTION_UPDATE_UI");
        LocalBroadcastManager.getInstance(this).registerReceiver(updateUIReceiver, uiUpdateFilter);


        addDatasetBtn.setOnClickListener(view -> {
            serverAddress = serverAddressInput.getText().toString();
            positionText = positionInput.getText().toString();

            if (serverAddress.equals("") || positionText.equals("")) {
                resultText.setText("Please input server address and position");
            } else {
                URL = serverAddress + "/api/addData";
                startBleScanAndProcessData();
                addDatasetBtn.setEnabled(false);
                findPositionBtn.setEnabled(false);
            }
        });


        findPositionBtn.setOnClickListener(v -> {
            serverAddress = serverAddressInput.getText().toString();
            positionText = "";
            Intent serviceIntent = new Intent(this, ForegroundService.class);
            serviceIntent.putExtra("serverAddress", serverAddress);
            serviceIntent.putExtra("positionText", positionText);
            serviceIntent.putExtra("passwordText", passwordText.getText().toString());

            if (serverAddress.equals("")) {
                resultText.setText("Please input server address");
            } else {
                if (!isServiceRunning) {
                    startService(serviceIntent);
                    isServiceRunning = true;
                } else {
                    stopService(serviceIntent);
                    isServiceRunning = false;
                }
            }
        });

    }

    //설계도 업로드
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri imageUri = data.getData();
            ImageView selectedImageView = findViewById(R.id.selectedImageView);
            selectedImageView.setImageURI(imageUri);
        }
    }
    private void openImageChooser() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_REQUEST);
    }


    //설계도 위에 위치 라벨링
    private void showLabelInput(float x, float y) {
        final EditText editText = new EditText(MainActivity.this);
        editText.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT));
        editText.setX(x);
        editText.setY(y);

        layoutDesignPlan.addView(editText);

        final Button saveButton = new Button(MainActivity.this);
        saveButton.setText("Save");
        saveButton.setX(x);
        saveButton.setY(y + 50); // 조정한 Y 위치

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String labelText = editText.getText().toString();
                TextView labelView = new TextView(MainActivity.this);
                labelView.setText(labelText);
                labelView.setX(x);
                labelView.setY(y - 30); // 조정한 Y 위치

                layoutDesignPlan.addView(labelView);
                layoutDesignPlan.removeView(editText); // EditText 제거
                layoutDesignPlan.removeView(saveButton); // 저장 버튼 제거
            }
        });

        layoutDesignPlan.addView(saveButton);
    }


    //블루투스 스캔
    private void startBleScanAndProcessData() {
        // bleReceiver 등록
        IntentFilter bleFilter = new IntentFilter("com.example.wifi_positining.BLE_SCAN_RESULT");
        registerReceiver(bleReceiver, bleFilter);

        // BLE 스캔 시작
        beaconManager.startRangingBeacons(new Region("myRegion", null, null, null));

        // 2초 후에 스캔 중지 및 결과 방송
        new Handler().postDelayed(() -> {
            beaconManager.stopRangingBeacons(new Region("myRegion", null, null, null));
            // BLE 스캔 결과 방송
            Intent bleScanResultIntent = new Intent("com.example.wifi_positining.BLE_SCAN_RESULT");

            sendBroadcast(bleScanResultIntent);
        }, 2000);

        beaconManager.addRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                for (Beacon beacon : beacons) {
                    String macAddress = beacon.getBluetoothAddress();
                    int rssi = beacon.getRssi();
                    Log.i("asd", "macAddress: " + macAddress + " rssi: " + rssi);
                    beaconDataMap.put(macAddress, rssi);
                }
            }
        });
    }


    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter("com.example.wifi_positining.FIND_POSITION");
        registerReceiver(findPositionReceiver, filter);
    }


    private String getAndroidId() {
        try {
            return Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Exception e) {
            e.printStackTrace();
            return "unknown";
        }
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
            //scanWifi();
            // startBleScanWithTimeout(30000);
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




    BroadcastReceiver bleReceiver  = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            unregisterReceiver(this);

//            // 서버에 보낼 JSON 설정 부분
            try {
                result_json.put("position", positionText);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            String scanLog = "";
            JSONArray json_array = new JSONArray();
            TextView logTextView = findViewById(R.id.app_log);
            for (Map.Entry<String, Integer> entry : beaconDataMap .entrySet()) {
                String bssid = entry.getKey(); // 키(주소) 가져오기
                Integer bleResult = entry.getValue(); // 값(데이터) 가져오기

                // 필요한 작업 수행
                scanLog += "BSSID: " + bssid + "  level: " + bleResult + "\n";
                // scanLog를 사용하거나 다른 작업을 수행할 수 있습니다.
            }
            logTextView.setText(scanLog);

            for (Map.Entry<String, Integer> entry : beaconDataMap .entrySet()) {

                try {
                    one_wifi_json.put("bssid", entry.getKey());
                    one_wifi_json.put("rssi", entry.getValue());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            Log.d("check Log",scanLog);

            json_array.put(one_wifi_json);
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