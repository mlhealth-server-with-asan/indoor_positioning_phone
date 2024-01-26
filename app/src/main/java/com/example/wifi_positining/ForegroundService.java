package com.example.wifi_positining;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;


import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.Channel;

import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ForegroundService extends Service {

    private final String serverHost = "192.168.45.47"; // RabbitMQ 서버 호스트
//    private final int serverPort = 5672; // RabbitMQ 서버 포트
//    private final String username = "123"; // RabbitMQ 사용자 이름
//    private final String password = "123"; // RabbitMQ 비밀번호
//    private boolean isAmqpConnected = false;


    private BeaconManager beaconManager;
    private Connection connection;
    private Channel channel;
    private final String TAG = "ForegroundService";
    private Handler handler = new Handler();
    private Runnable runnableCode;

    private boolean isScanning = false;
    private HashMap<String, Integer> beaconDataMap = new HashMap<String, Integer>();


    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundServiceWithNotification();


        // BeaconManager 초기화
        beaconManager = BeaconManager.getInstanceForApplication(this);

        // iBeacon을 사용하고 있다고 가정
        beaconManager.getBeaconParsers().add(new BeaconParser().
                setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));

    }

    private void startForegroundServiceWithNotification() {
        // 알림 채널 생성 (Android O 이상 필요)
        NotificationChannel channel = new NotificationChannel("YOUR_CHANNEL_ID", "YOUR_CHANNEL_NAME", NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);

        // 알림 생성
        Notification notification = new NotificationCompat.Builder(this, "YOUR_CHANNEL_ID")
                .setContentTitle("Foreground Service")
                .setContentText("Service is running in foreground")
                .setSmallIcon(R.mipmap.ic_launcher) // 알림 아이콘 설정
                .build();

        // 포그라운드 서비스 시작
        startForeground(1, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
//        if (!isAmqpConnected) {
//            initAmqpConnection(() -> startMessageReceiver()); // 첫 연결 시 콜백 사용
//        } else {
//            startMessageReceiver(); // 연결이 이미 되어 있으면 바로 메시지 수신 시작
//        }

        if (intent != null) {
            String serverAddress = intent.getStringExtra("serverAddress");
            String positionText = intent.getStringExtra("positionText");
            String passwordText = intent.getStringExtra("passwordText");
            if (serverAddress != null && positionText != null) {
                startScanning(serverAddress, positionText,passwordText);
            }
        }

        return START_STICKY;
    }


    private void startScanning(String serverAddress, String positionText, String passwordText) {
        if (!isScanning) {
            runnableCode = new Runnable() {
                @Override
                public void run() {
                    performBackgroundTask(serverAddress, positionText,passwordText);

                    // 10초 후에 다시 실행
                    if (isScanning) {
                        handler.postDelayed(this, 5000);
                    }
                }
            };

            // 초기 실행 및 스캐닝 상태 설정
            handler.post(runnableCode);
            isScanning = true;

            // 60초 후에 스캐닝 종료
            handler.postDelayed(() -> {
                stopScanning();
            }, 100000);
        }
    }

    private void stopScanning() {
        if (isScanning) {
            handler.removeCallbacks(runnableCode);
            isScanning = false;
            // 스캔 중지 관련 처리
        }
    }


    private void performBackgroundTask(String serverAddress,String positionText,String passwordText) {

        String URL = serverAddress + "/findPosition";

        startBleScanAndProcessData();

        JSONObject result_json = new JSONObject();
        JSONArray json_array = new JSONArray();
        try {
            for (Map.Entry<String, Integer> entry : beaconDataMap.entrySet()) {
//                String macAddress = entry.getKey();
//                List<Integer> rssiList = entry.getValue();


                JSONObject one_wifi_json = new JSONObject();
                one_wifi_json.put("bssid", entry.getKey());
                one_wifi_json.put("rssi", entry.getValue());
                json_array.put(one_wifi_json);
            }
            result_json.put("wifi_data", json_array);
            result_json.put("position", "");
            result_json.put("password", passwordText);
            // 기타 필요한 데이터 추가
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // 서버와 통신
        sendNetworkRequest(URL, result_json);
    }


    private void sendNetworkRequest(String URL, JSONObject jsonData) {
        RequestQueue requestQueue = Volley.newRequestQueue(getApplicationContext());
        String mRequestBody = jsonData.toString();

        StringRequest stringRequest = new StringRequest(Request.Method.POST, URL, response -> {
            // 응답 처리
            Intent intent = new Intent("ACTION_UPDATE_UI");
            intent.putExtra("response", response);
            Log.i("NetworkResponse", response);
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
        }, error -> {
            Log.e("NetworkError", error.toString());
        }) {
            @Override
            public String getBodyContentType() {
                return "application/json; charset=utf-8";
            }

            @Override
            public byte[] getBody() {
                return mRequestBody.getBytes(StandardCharsets.UTF_8);
            }

            @Override
            protected Response<String> parseNetworkResponse(NetworkResponse response) {
                String responseString = "";
                if (response != null) {
                    responseString = new String(response.data, StandardCharsets.UTF_8);
                }
                return Response.success(responseString, HttpHeaderParser.parseCacheHeaders(response));
            }
        };

        requestQueue.add(stringRequest);
    }

    private void startBleScanAndProcessData() {

        // BLE 스캔 시작
        beaconManager.startRangingBeacons(new Region("myRegion", null, null, null));

        // 2초 후에 스캔 중지 및 결과 방송
        new Handler().postDelayed(() -> {
            beaconManager.stopRangingBeacons(new Region("myRegion", null, null, null));
            // BLE 스캔 결과 방송
            Intent bleScanResultIntent = new Intent("com.example.wifi_positining.BLE_SCAN_RESULT");
            // 필요한 경우 여기에 추가 데이터를 넣습니다.
            sendBroadcast(bleScanResultIntent);
        }, 10000);

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





//    private void initAmqpConnection(AmqpConnectionListener listener) {
//        new Thread(() -> {
//            ConnectionFactory factory = new ConnectionFactory();
//            factory.setHost(serverHost);
//            factory.setPort(serverPort);
//            factory.setUsername(username);
//            factory.setPassword(password);
//            factory.setVirtualHost("/");
//
//            try {
//                connection = factory.newConnection();
//                channel = connection.createChannel();
//
//                isAmqpConnected = true;
//                // 콜백 호출
//                if (listener != null) {
//                    listener.onConnectionReady();
//                }
//            } catch (Exception e) {
//                Log.e(TAG, "Error initializing AMQP connection: " + e.getMessage(), e);
//            }
//        }).start();
//    }


//    private double calculateMedian(List<Integer> values) {
//        Collections.sort(values);
//        int middle = values.size() / 2;
//        if (values.size() % 2 == 0) {
//            return (values.get(middle - 1) + values.get(middle)) / 2.0;
//        } else {
//            return values.get(middle);
//        }
//    }

//    private void startMessageReceiver() {
//        try {
//
//            String queueName = channel.queueDeclare().getQueue();
//            String exchangeName = "mlhealth.fanout";
//            String routingKey = "mlhealth.routing.#";
//
//           // channel.queueDeclare(queueName, true, false, false, null);
//            channel.queueBind(queueName, exchangeName, routingKey);
//
//            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
//                    Log.e(TAG,"test");
//                    String message = new String(delivery.getBody(), "UTF-8");
//                    Log.e(TAG, "Received message: " + message);
//                    try {
//                        JSONObject jsonMessage = new JSONObject(message);
//                        if ("find position".equals(jsonMessage.getString("title")) && "all".equals(jsonMessage.getString("content"))) {
//                            Intent intent = new Intent("com.example.wifi_positining.FIND_POSITION");
//                            Log.d(TAG,"yes");
//                            sendBroadcast(intent);
//                        }
//                    } catch (JSONException e) {
//                        e.printStackTrace();
//                    }
//                    // 메시지 처리 로직
//                };
//            channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {});
//        } catch (Exception e) {
//            Log.e(TAG, "Error starting message receiver: " + e.getMessage(), e);
//        }
//    }

//    private String getDeviceId() {
//        return Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
//    }



    @Override
    public void onDestroy() {
        super.onDestroy();

        // 진행 중인 스캔 중지
        if (beaconManager != null) {
            beaconManager.stopRangingBeacons(new Region("myRegion", null, null, null));
        }

        // 핸들러의 콜백 제거
        if (handler != null) {
            handler.removeCallbacks(runnableCode);
        }

        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
        } catch (Exception e) {
//            Log.e(TAG, "Error closing AMQP connection: " + e.getMessage(), e);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
