package com.ariesbifold;

import com.facebook.react.uimanager.*;
import com.facebook.react.bridge.*;
import com.facebook.systrace.Systrace;
import com.facebook.systrace.SystraceMessage;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.ReactRootView;
import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.shell.MainReactPackage;
import com.facebook.soloader.SoLoader;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.ParcelUuid;
import android.os.Build;

import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.lang.Thread;
import java.lang.Object;
import java.util.Hashtable;
import java.util.Set;

public class BleAdvertiseModule extends ReactContextBaseJavaModule {

    public static final String TAG = "BleAdvertiseXX0";
    private BluetoothAdapter mBluetoothAdapter;
    
    private static Hashtable<String, BluetoothLeAdvertiser> mAdvertiserList;
    private static Hashtable<String, AdvertiseCallback> mAdvertiserCallbackList;
    private static BluetoothLeScanner mScanner;
    private static ScanCallback mScannerCallback;
    private int companyId;
    private Boolean mObservedState;

    //Constructor
    public BleAdvertiseModule(ReactApplicationContext reactContext) {
        super(reactContext);

        mAdvertiserList = new Hashtable<String, BluetoothLeAdvertiser>();
        mAdvertiserCallbackList = new Hashtable<String, AdvertiseCallback>();

        BluetoothManager bluetoothManager = (BluetoothManager) reactContext.getApplicationContext()
                .getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            mBluetoothAdapter = bluetoothManager.getAdapter();
        } 

        if (mBluetoothAdapter != null) {
            mObservedState = mBluetoothAdapter.isEnabled();
        }

        this.companyId = 0x0000;

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        reactContext.registerReceiver(mReceiver, filter);
    }
    
    @Override
    public String getName() {
        return "BleAdvertise";
    }

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put("ADVERTISE_MODE_BALANCED",        AdvertiseSettings.ADVERTISE_MODE_BALANCED);
        constants.put("ADVERTISE_MODE_LOW_LATENCY",     AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY);
        constants.put("ADVERTISE_MODE_LOW_POWER",       AdvertiseSettings.ADVERTISE_MODE_LOW_POWER);
        constants.put("ADVERTISE_TX_POWER_HIGH",        AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
        constants.put("ADVERTISE_TX_POWER_LOW",         AdvertiseSettings.ADVERTISE_TX_POWER_LOW);
        constants.put("ADVERTISE_TX_POWER_MEDIUM",      AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM);
        constants.put("ADVERTISE_TX_POWER_ULTRA_LOW",   AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW);

        return constants;
    }

    @ReactMethod
    public void setCompanyId(int companyId) {
        this.companyId = companyId;
    }

    @ReactMethod
    public void broadcast(String uid, ReadableArray payload, ReadableMap options, Promise promise) {
        if (mBluetoothAdapter == null) {
            Log.w("BleAdvertiseModule", "Device does not support Bluetooth. Adapter is Null");
            promise.reject("Device does not support Bluetooth. Adapter is Null");
            return;
        } 
        
        if (companyId == 0x0000) {
            Log.w("BleAdvertiseModule", "Invalid company id");
            promise.reject("Invalid company id");
            return;
        } 
        
        if (mBluetoothAdapter == null) {
            Log.w("BleAdvertiseModule", "mBluetoothAdapter unavailable");
            promise.reject("mBluetoothAdapter unavailable");
            return;
        } 

        if (mObservedState != null && !mObservedState) {
            Log.w("BleAdvertiseModule", "Bluetooth disabled");
            promise.reject("Bluetooth disabled");
            return;
        }

        BluetoothLeAdvertiser tempAdvertiser;
        AdvertiseCallback tempCallback;

        if (mAdvertiserList.containsKey(uid)) {
            tempAdvertiser = mAdvertiserList.remove(uid);
            tempCallback = mAdvertiserCallbackList.remove(uid);

            tempAdvertiser.stopAdvertising(tempCallback);
        } else {
            tempAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
            tempCallback = new BleAdvertiseModule.SimpleAdvertiseCallback(promise);
        }
         
        if (tempAdvertiser == null) {
            Log.w("BleAdvertiseModule", "Advertiser Not Available unavailable");
            promise.reject("Advertiser unavailable on this device");
            return;
        }
        
        AdvertiseSettings settings = buildAdvertiseSettings(options);
        AdvertiseData data = buildAdvertiseData(ParcelUuid.fromString(uid), toByteArray(payload), options);

        tempAdvertiser.startAdvertising(settings, data, tempCallback);

        mAdvertiserList.put(uid, tempAdvertiser);
        mAdvertiserCallbackList.put(uid, tempCallback);
    }

    private byte[] toByteArray(ReadableArray payload) {
        byte[] temp = new byte[payload.size()];
        for (int i = 0; i < payload.size(); i++) {
            temp[i] = (byte)payload.getInt(i);
        }
        return temp;
    }

    private WritableArray toByteArray(byte[] payload) {
        WritableArray array = Arguments.createArray();
        for (byte data : payload) {
            array.pushInt(data);
        }
        return array;
    }

   @ReactMethod
    public void stopBroadcast(final Promise promise) {
        Log.w("BleAdvertiseModule", "Stop Broadcast call");

        if (mBluetoothAdapter == null) {
            Log.w("BleAdvertiseModule", "mBluetoothAdapter unavailable");
            promise.reject("mBluetoothAdapter unavailable");
            return;
        } 

        if (mObservedState != null && !mObservedState) {
            Log.w("BleAdvertiseModule", "Bluetooth disabled");
            promise.reject("Bluetooth disabled");
            return;
        }

        WritableArray promiseArray=Arguments.createArray();

        Set<String> keys = mAdvertiserList.keySet();
        for (String key : keys) {
            BluetoothLeAdvertiser tempAdvertiser = mAdvertiserList.remove(key);
            AdvertiseCallback tempCallback = mAdvertiserCallbackList.remove(key);
            if (tempAdvertiser != null) {
                tempAdvertiser.stopAdvertising(tempCallback);
                promiseArray.pushString(key);
            }
        }

        promise.resolve(promiseArray);
    }

    private AdvertiseSettings buildAdvertiseSettings(ReadableMap options) {
        AdvertiseSettings.Builder settingsBuilder = new AdvertiseSettings.Builder();

        if (options != null && options.hasKey("advertiseMode")) {
            settingsBuilder.setAdvertiseMode(options.getInt("advertiseMode"));
        }

        if (options != null && options.hasKey("txPowerLevel")) {
            settingsBuilder.setTxPowerLevel(options.getInt("txPowerLevel"));
        }

        if (options != null && options.hasKey("connectable")) {
            settingsBuilder.setConnectable(options.getBoolean("connectable"));
        }

        return settingsBuilder.build();
    }

    private AdvertiseData buildAdvertiseData(ParcelUuid uuid, byte[] payload, ReadableMap options) {
        AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder();

        if (options != null && options.hasKey("includeDeviceName")) 
            dataBuilder.setIncludeDeviceName(options.getBoolean("includeDeviceName"));
        
         if (options != null && options.hasKey("includeTxPowerLevel")) 
            dataBuilder.setIncludeTxPowerLevel(options.getBoolean("includeTxPowerLevel"));
        
        // dataBuilder.addManufacturerData(companyId, payload);
        dataBuilder.addServiceUuid(uuid);
        return dataBuilder.build();
    }

    private class SimpleAdvertiseCallback extends AdvertiseCallback {
        Promise promise;

        public SimpleAdvertiseCallback () {
        }

        public SimpleAdvertiseCallback (Promise promise) {
            this.promise = promise;
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            Log.i(TAG, "Advertising failed with code "+ errorCode);

            if (promise == null) return;

            switch (errorCode) {
                case ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                    promise.reject("This feature is not supported on this platform."); break;
                case ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                    promise.reject("Failed to start advertising because no advertising instance is available."); break;
                case ADVERTISE_FAILED_ALREADY_STARTED:
                    promise.reject("Failed to start advertising as the advertising is already started."); break;
                case ADVERTISE_FAILED_DATA_TOO_LARGE:
                    promise.reject("Failed to start advertising as the advertise data to be broadcasted is larger than 31 bytes."); break;
                case ADVERTISE_FAILED_INTERNAL_ERROR:
                    promise.reject("Operation failed due to an internal error."); break;
            }
        }

        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            Log.i(TAG, "Advertising successful");

            if (promise == null) return;
            promise.resolve(settingsInEffect.toString());
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                final int prevState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, BluetoothAdapter.ERROR);
                
                Log.d(TAG, String.valueOf(state));
                switch (state) {
                case BluetoothAdapter.STATE_OFF:
                    mObservedState = false;
                    break;
                case BluetoothAdapter.STATE_TURNING_OFF:
                    mObservedState = false;
                    break;
                case BluetoothAdapter.STATE_ON:
                    mObservedState = true;
                    break;
                case BluetoothAdapter.STATE_TURNING_ON:
                    mObservedState = true;
                    break;
                }

                // Only send enabled when fully ready. Turning on and Turning OFF are seen as disabled. 
                if (state == BluetoothAdapter.STATE_ON && prevState != BluetoothAdapter.STATE_ON) {
                    WritableMap params = Arguments.createMap();
                    params.putBoolean("enabled", true);
                    sendEvent("onBTStatusChange", params);
                } else if (state != BluetoothAdapter.STATE_ON && prevState == BluetoothAdapter.STATE_ON ) {
                    WritableMap params = Arguments.createMap();
                    params.putBoolean("enabled", false);
                    sendEvent("onBTStatusChange", params);
                }
            }
        }
    };

    private void sendEvent(String eventName, WritableMap params) {
        getReactApplicationContext()
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(eventName, params);
    }
}
