package ru.orehovai.livegps;

import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

public class DataSendService extends Service {

    private static final String TAG = DataSendService.class.getSimpleName();

    private TCPClient mTcpClient;

    private Location location;
    private int numOfSats = 0;

    private final IBinder binder = new DataSendBinder();

    private Handler dataSendServiceHandler;
    private Runnable start;
    HandlerThread handlerThread;

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public int getNumOfSats() {
        return numOfSats;
    }

    public void setNumOfSats(int numOfSats) {
        this.numOfSats = numOfSats;
    }


    @Override
    public void onCreate() {

        if (handlerThread == null) handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        dataSendServiceHandler = new Handler(handlerThread.getLooper());

        //Клиент для подключения к серверу
        mTcpClient = new TCPClient(/*new TCPClient.OnMessageReceived() {
            @Override
            //реализуем метод для ответного сообщения с сервера
            public void messageReceived(String message) {
            }
        }*/);

        start = new Runnable() {
            @Override
            public void run() {
                mTcpClient.setStringForSend(Utils.getLocationStringForServer(getBatteryLevel(), getNumOfSats(), getLocation()));
                mTcpClient.run();
            }
        };
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        if(mTcpClient != null) {
            mTcpClient.stopClient();
            mTcpClient = null;
        }
        dataSendServiceHandler.removeCallbacksAndMessages(null);
        handlerThread.quit();
    }

    //передаем данные на сервер
    public void sendData() {
        dataSendServiceHandler.post(start);
    }

    public class DataSendBinder extends Binder {
        DataSendService getService() {
            return DataSendService.this;
        }
    }

    //получаем информацию об уровне заряда батареи
    public float getBatteryLevel() {
        Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        if(level == -1 || scale == -1) {
            return 50.0f;
        }

        return ((float)level / (float)scale) * 100.0f;
    }
}
