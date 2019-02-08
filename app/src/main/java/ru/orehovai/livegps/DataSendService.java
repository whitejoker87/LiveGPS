package ru.orehovai.livegps;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.lifecycle.ViewModelProviders;

public class DataSendService extends Service {
    public DataSendService() {
    }

    private LocationViewModel viewModel;

    @Override
    public void onCreate() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
