package ru.orehovai.livegps;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

public class LocationUpdatesService extends Service implements GpsStatus.Listener {

    private static final String PACKAGE_NAME = "ru.orehovai.livegps";

    //для запроса на сервер(этот сервис работает все время пока работает приложение)
    static final String PROTOCOL = "rtt003";
    static final String IMEI = "356217625371611";
    static final String UTC = "+3";
    static final String GSM_LEVEL = "99";
    static final String GPS_OR_LBS = "A";
    static final String SOS = "0";

    private static final String TAG = LocationUpdatesService.class.getSimpleName();

    //имя канала оповещений
    private static final String CHANNEL_ID = "channel_01";

    static final String ACTION_BROADCAST = PACKAGE_NAME + ".broadcast";

    static final String EXTRA_LOCATION = PACKAGE_NAME + ".location";
    private static final String EXTRA_STARTED_FROM_NOTIFICATION = PACKAGE_NAME + ".started_from_notification";

    private final IBinder mBinder = new LocalBinder();

    //Желаемый интервал для обновления местоположения. Обновления могут быть более или менее частыми.
    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 10000;

    //Самый быстрый показатель для активных обновлений местоположения. Обновления никогда не будут более частыми чем это значение
    private static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS;

    //Идентификатор уведомления, отображаемого для Foreground service.
    private static final int NOTIFICATION_ID = 12345678;

    //Для проверки связянной Activity.Если зменилась ориентация то НЕ создается уведомление для Foreground service.
    private boolean mChangingConfiguration = false;

    private NotificationManager mNotificationManager;

    //Contains parameters used by {@link com.google.android.gms.location.FusedLocationProviderApi}.
    private LocationRequest mLocationRequest;

    //Предоставляет доступ к API FusedLocationProvider.
    private FusedLocationProviderClient mFusedLocationClient;

    //Callback для изменения местоположения
    private LocationCallback mLocationCallback;

    //Нынешнее местоположение
    private Location mLocation;

    //для отправки данных на сервер
    private DataSendService dataSendService = null;
    private boolean dataBound = false;

    //для определния кол-ва спутников
    private LocationManager locationManager = null;

    private final ServiceConnection dataSendServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            DataSendService.DataSendBinder binder = (DataSendService.DataSendBinder) service;
            dataSendService = binder.getService();
            dataBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            dataSendService = null;
            dataBound = false;
        }
    };

    @Override
    public void onCreate() {
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        //колбек с изменениями локации
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                onNewLocation(locationResult.getLastLocation());
            }
        };

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        createLocationRequest();//создание запроса местополодения
        getLastLocation();//запрос последней локации

        //биндинг сервиса для отправки на сервер данных
        bindService(new Intent(this, DataSendService.class), dataSendServiceConnection, Context.BIND_AUTO_CREATE);

        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Android O требуется канал дя оповещений
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.app_name);
            // Создаем канал для оповещений
            NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT);
            // Устанавливаем Notification Channel для Notification Manager.
            mNotificationManager.createNotificationChannel(mChannel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service location started");
        boolean startedFromNotification = intent.getBooleanExtra(EXTRA_STARTED_FROM_NOTIFICATION, false);
        //после удаления обновления местоположения из уведомления
        if (startedFromNotification) {
            removeLocationUpdates();
            stopSelf();
        }
        //Сообщает системе пытаться воссоздать службу после ее уничтожения
        return START_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mChangingConfiguration = true;
    }

    @Override
    public IBinder onBind(Intent intent) {
        //вызывается когда Activity становится активной(Service в этом случае должен перестать быть Foreground)
        Log.i(TAG, "in onBind()");
        stopForeground(true);
        mChangingConfiguration = false;
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        //вызывается когда Activity возвращается на передний план(Service в этом случае должен перестать быть Foreground)
        Log.i(TAG, "in onRebind()");
        stopForeground(true);
        mChangingConfiguration = false;
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "Last client unbound from service");
        //если unBind в результате изменения конфигурации то ничего не делать, иначе делаем Foreground Service
        if (!mChangingConfiguration && Utils.requestingLocationUpdates(this)) {
            Log.i(TAG, "Starting foreground service");
            startForeground(NOTIFICATION_ID, getNotification());
        }
        return true;//  для  onRebind() при повторной привязке
    }

    @Override
    public void onDestroy() {
        if (dataBound) {
            unbindService(dataSendServiceConnection);
            dataBound = false;
        }
        locationManager.removeGpsStatusListener(this);
        mFusedLocationClient.removeLocationUpdates(mLocationCallback);
    }

    public void requestLocationUpdates() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {//необходимая проверка прав перез запросом к LocationManager
                return;
            }
        }
        locationManager.addGpsStatusListener(this);//добавляем слушатель(для количества спутников)
        Log.i(TAG, "ЗАпрос на обновление местоположения");
        Utils.setRequestingLocationUpdates(this, true);
        startService(new Intent(getApplicationContext(), LocationUpdatesService.class));
        try {
            mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());//запрос информации о местоположении
        } catch (SecurityException unlikely) {
            Utils.setRequestingLocationUpdates(this, false);
            Log.e(TAG, "Нет прав на обновление местоположения " + unlikely);
        }
    }

    public void removeLocationUpdates() {
        Log.i(TAG, "удаления запроса на местоположение");
        try {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
            Utils.setRequestingLocationUpdates(this, false);
            stopSelf();
        } catch (SecurityException unlikely) {
            Utils.setRequestingLocationUpdates(this, true);
            Log.e(TAG, "Нет прав на удаление запроса о местополодении" + unlikely);
        }
    }

    /**
     * Returns the {@link NotificationCompat} used as part of the foreground service.
     */
    private Notification getNotification() {
        Intent intent = new Intent(this, LocationUpdatesService.class);

        CharSequence text = Utils.getLocationText(mLocation);

        //мы в onStartCommand через уведомление или нет.
        intent.putExtra(EXTRA_STARTED_FROM_NOTIFICATION, true);


        //приводит к вызову onStartCommand() в этом сервисе.
        PendingIntent servicePendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        //для старта Activity.
        PendingIntent activityPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this).addAction(R.drawable.ic_launch, getString(R.string.launch_activity), activityPendingIntent)
                .addAction(R.drawable.ic_cancel, getString(R.string.remove_location_updates), servicePendingIntent)
                .setContentText(text)
                .setContentTitle(Utils.getLocationTitle(this))
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setTicker(text)
                .setWhen(System.currentTimeMillis());

        // ИД канала для Android O.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_ID);
        }

        return builder.build();
    }

    private void getLastLocation() {
        try {
            mFusedLocationClient.getLastLocation()
                    .addOnCompleteListener(new OnCompleteListener<Location>() {
                        @Override
                        public void onComplete(@NonNull Task<Location> task) {
                            if (task.isSuccessful() && task.getResult() != null) {
                                mLocation = task.getResult();
                            } else {
                                Log.w(TAG, "Ошибка получения местополодения");
                            }
                        }
                    });
        } catch (SecurityException unlikely) {
            Log.e(TAG, "Нет прав на получение местополодения" + unlikely);
        }
    }

    private void onNewLocation(Location location) {
        Log.i(TAG, "Новое местоположение: " + location);

        mLocation = location;

        //Уведомление о новом местоположении
        Intent intent = new Intent(ACTION_BROADCAST);
        intent.putExtra(EXTRA_LOCATION, location);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);

        if (location != null) dataSendService.setLocation(location);//уведомляем о новой локации сервис отправки на сервер
        //отправляем полученные данные на сервер
        if (dataBound) dataSendService.sendData();

        // Обновление содержимого уведомления, если оно работает в Foreground Service.
        if (serviceIsRunningInForeground(this)) {
            mNotificationManager.notify(NOTIFICATION_ID, getNotification());
        }
    }

    //Параметры запроса места
    private void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    //класс для Binder
    public class LocalBinder extends Binder {
        LocationUpdatesService getService() {
            return LocationUpdatesService.this;
        }
    }

    //true если Foreground
    public boolean serviceIsRunningInForeground(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(
                Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(
                Integer.MAX_VALUE)) {
            if (getClass().getName().equals(service.service.getClassName())) {
                if (service.foreground) {
                    return true;
                }
            }
        }
        return false;
    }

    //слушатель изменения gps(для количества спутников)
    @Override
    public void onGpsStatusChanged(int event) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Permission in onGpsStatusChanged denied");
                return;
            }
        }
        GpsStatus gpsStatus = locationManager.getGpsStatus(null);
        if (gpsStatus != null) {
            int satellites = 0;
            //int sattelitesInFix = 0;
            //int timeToFix = locationManager.getGpsStatus(null).getTimeToFirstFix();
            for (GpsSatellite sat : locationManager.getGpsStatus(null).getSatellites()) {
                //if (sat.usedInFix()) {
                    //sattelitesInFix++;//для зафиксированных спутников. по какой то причине спутники не фиксируются.
                    // в задании указано число спутников >30 и требуется количество спутников.
                    // Я сделал вывод что имеется ввиду общее количество. Его и использовал в ответе серверу.
                //}
                satellites++;
            }
            Log.e(TAG, "We have " + satellites + " satellites");
            if (dataBound)dataSendService.setNumOfSats(satellites);
        } else if (dataBound)dataSendService.setNumOfSats(0); //на случай отсутствия информации о спутниках

    }

}
