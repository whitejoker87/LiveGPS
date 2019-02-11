package ru.orehovai.livegps;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.GnssStatus;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

import java.util.Iterator;

import androidx.lifecycle.ViewModelProviders;

public class DataSendService extends Service implements GpsStatus.Listener {


    public DataSendService() {
    }

    private static final String TAG = DataSendService.class.getSimpleName();

    private TCPClient mTcpClient;

    private Location location;
    private int numOfSats;

    private final IBinder binder = new DataSendBinder();

    private Handler dataSendServiceHandler;
    private Runnable start, send;
    HandlerThread handlerThread;

    private LocationManager locationManager = null;

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


    @SuppressLint("MissingPermission")
    @Override
    public void onCreate() {

        locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        locationManager.addGpsStatusListener(this);

        if (handlerThread == null) handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        dataSendServiceHandler = new Handler(handlerThread.getLooper());

        //we create a TCPClient object and
        mTcpClient = new TCPClient(new TCPClient.OnMessageReceived() {
            @Override
            //here the messageReceived method is implemented
            public void messageReceived(String message) {

                //this method calls the onProgressUpdate
                //publishProgress(message);

            }
        });

        start = new Runnable() {
            @Override
            public void run() {
                mTcpClient.setStringForSend(Utils.getLocationStringForServer(getBatteryLevel(), getNumOfSats(), location));
                mTcpClient.run();
            }
        };

        send = new Runnable() {
            @Override
            public void run() {
                mTcpClient.sendMessage("rtt003,356217625371611,60.0311283,30.3993444,0023,0014,123,090,20130618,195430,+3,24,60,A,0");
            }
        };
    }

    @Override
    public IBinder onBind(Intent intent) {
        //dataSendServiceHandler.post(start);
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

    public void sendData() {

        dataSendServiceHandler.post(start);
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onGpsStatusChanged(int event) {

        @SuppressLint("MissingPermission") GpsStatus gpsStatus = locationManager.getGpsStatus(null);
        if (gpsStatus != null) {
            int satellites = 0;
            int sattelitesInFix = 0;
            @SuppressLint("MissingPermission") int timeToFix = locationManager.getGpsStatus(null).getTimeToFirstFix();
            for (GpsSatellite sat : locationManager.getGpsStatus(null).getSatellites()) {
                if (sat.usedInFix()) {
                    sattelitesInFix++;
                }
                satellites++;
            }
            setNumOfSats(satellites);
        }

    }

    public class DataSendBinder extends Binder {
        DataSendService getService() {
            return DataSendService.this;
        }
    }

    public float getBatteryLevel() {
        Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        // Error checking that probably isn't needed but I added just in case.
        if(level == -1 || scale == -1) {
            return 50.0f;
        }

        return ((float)level / (float)scale) * 100.0f;
    }
}
