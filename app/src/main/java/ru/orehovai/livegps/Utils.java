package ru.orehovai.livegps;

import android.content.Context;
import android.location.Location;
import android.preference.PreferenceManager;

import java.text.DateFormat;
import java.util.Date;

import static ru.orehovai.livegps.LocationUpdatesService.*;

class Utils {

    static final String KEY_REQUESTING_LOCATION_UPDATES = "requesting_locaction_updates";

    //true если запрос данных о местополождении
    static boolean requestingLocationUpdates(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(KEY_REQUESTING_LOCATION_UPDATES, false);
    }

    //Записывает местоположение в SharedPreferences.
    static void setRequestingLocationUpdates(Context context, boolean requestingLocationUpdates) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(KEY_REQUESTING_LOCATION_UPDATES, requestingLocationUpdates)
                .apply();
    }

    //Возвращает данные о местополодении как строку
    static String getLocationText(Location location) {
        return location == null ? "Unknown location" :
                "(" + location.getLatitude() + ", " + location.getLongitude() + ")";
    }

    static String getLocationTitle(Context context) {
        return context.getString(R.string.location_updated,
                DateFormat.getDateTimeInstance().format(new Date()));
    }

    static String getLocationStringForServer(float batteryLevel, int numOfSats, Location location) {
        //if (location.getExtras() != null)  numOfSats = location.getExtras().getInt("satellites");
        return location == null ? "" : PROTOCOL + ","
                + IMEI + ","
                + location.getLatitude() + ","
                + location.getLongitude() + ","
                + location.getSpeed() +  ","
                + location.getAltitude() + ","
                + location.getBearing() + ","
                + batteryLevel + ","
                + DateFormat.getDateInstance().format(new Date()) + ","
                + location.getTime() + ","
                + UTC + ","
                //+ location.getExtras().getInt("satellites") + ","
                + numOfSats + ","
                + GSM_LEVEL + ","
                + GPS_OR_LBS + ","
                + SOS;
    }
}
