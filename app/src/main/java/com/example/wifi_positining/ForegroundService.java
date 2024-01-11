package com.example.wifi_positining;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.nfc.Tag;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;


import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.Channel;

import org.json.JSONException;
import org.json.JSONObject;


public class ForegroundService extends Service {

    private Connection connection;
    private Channel channel;
    private final String TAG = "ForegroundService";

    private final String serverHost = "172.30.1.52"; // RabbitMQ 서버 호스트
    private final int serverPort = 5672; // RabbitMQ 서버 포트
    private final String username = "123"; // RabbitMQ 사용자 이름
    private final String password = "123"; // RabbitMQ 비밀번호

    private boolean isAmqpConnected = false;

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundServiceWithNotification();

        // 다른 초기화 작업...
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
        if (!isAmqpConnected) {
            initAmqpConnection(() -> startMessageReceiver()); // 첫 연결 시 콜백 사용
        } else {
            startMessageReceiver(); // 연결이 이미 되어 있으면 바로 메시지 수신 시작
        }
        return START_STICKY;
    }


    private void initAmqpConnection(AmqpConnectionListener listener) {
        new Thread(() -> {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(serverHost);
            factory.setPort(serverPort);
            factory.setUsername(username);
            factory.setPassword(password);
            factory.setVirtualHost("/");

            try {
                connection = factory.newConnection();
                channel = connection.createChannel();

                isAmqpConnected = true;
                // 콜백 호출
                if (listener != null) {
                    listener.onConnectionReady();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error initializing AMQP connection: " + e.getMessage(), e);
            }
        }).start();
    }

    private void startMessageReceiver() {
        try {

            String queueName = channel.queueDeclare().getQueue();
            String exchangeName = "mlhealth.fanout";
            String routingKey = "mlhealth.routing.#";

           // channel.queueDeclare(queueName, true, false, false, null);
            channel.queueBind(queueName, exchangeName, routingKey);

            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                    Log.e(TAG,"test");
                    String message = new String(delivery.getBody(), "UTF-8");
                    Log.e(TAG, "Received message: " + message);
                    try {
                        JSONObject jsonMessage = new JSONObject(message);
                        if ("find position".equals(jsonMessage.getString("title")) && "all".equals(jsonMessage.getString("content"))) {
                            Intent intent = new Intent("com.example.wifi_positining.FIND_POSITION");
                            Log.d(TAG,"yes");
                            sendBroadcast(intent);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    // 메시지 처리 로직
                };
            channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {});
        } catch (Exception e) {
            Log.e(TAG, "Error starting message receiver: " + e.getMessage(), e);
        }
    }

    private String getDeviceId() {
        return Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
    }



    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing AMQP connection: " + e.getMessage(), e);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
