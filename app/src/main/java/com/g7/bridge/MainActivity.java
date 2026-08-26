package com.g7.bridge;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.TextView;

import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothHidDevice hidDeviceService;
    private BluetoothDevice connectedDevice;
    private TextView statusTextView;

    private static final byte[] HID_DESCRIPTOR = new byte[]{
        0x05, 0x01, 0x09, 0x05, (byte) 0xa1, 0x01,
        0x05, 0x09, 0x19, 0x01, 0x29, 0x08, 0x15, 0x00, 0x25, 0x01, (byte) 0x95, 0x08, 0x75, 0x01, (byte) 0x81, 0x02,
        0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x09, 0x32, 0x09, 0x35, 0x15, (byte) 0x81, 0x25, 0x7f, 0x75, 0x08, (byte) 0x95, 0x04, (byte) 0x81, 0x02,
        (byte) 0xc0
    };

    private byte buttonState = 0;
    private byte lx = 0, ly = 0, rx = 0, ry = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        statusTextView = new TextView(this);
        statusTextView.setText("G7 Bridge Gamepad Active\nWaiting for Bluetooth connection...");
        statusTextView.setTextSize(18);
        statusTextView.setPadding(40, 40, 40, 40);
        setContentView(statusTextView);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            statusTextView.setText("Error: Bluetooth not supported.");
            return;
        }

        // ტელეფონის ხილვადობის ჩართვა აპის გახსნისას
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        startActivity(discoverableIntent);

        initHidProfile();
    }

    private void initHidProfile() {
        bluetoothAdapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDeviceService = (BluetoothHidDevice) proxy;
                    registerHidApp();
                }
            }

            @Override
            public void onServiceDisconnected(int profile) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDeviceService = null;
                }
            }
        }, BluetoothProfile.HID_DEVICE);
    }

    private void registerHidApp() {
        BluetoothHidDeviceAppSdpSettings sdp = new BluetoothHidDeviceAppSdpSettings(
                "G7 Bridge Gamepad", "Virtual Gamepad Bridge", "Android",
                BluetoothHidDevice.SUBCLASS1_COMBO, HID_DESCRIPTOR
        );

        try {
            hidDeviceService.registerApp(sdp, null, null, Executors.newSingleThreadExecutor(), new BluetoothHidDevice.Callback() {
                @Override
                public void onConnectionStateChanged(BluetoothDevice device, int state) {
                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        connectedDevice = device;
                        runOnUiThread(() -> statusTextView.setText("Connected to iPhone: " + device.getName()));
                    } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                        connectedDevice = null;
                        runOnUiThread(() -> statusTextView.setText("Disconnected. Waiting for connection..."));
                    }
                }
            });
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (updateButtonState(keyCode, true)) {
            sendReport();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (updateButtonState(keyCode, false)) {
            sendReport();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private boolean updateButtonState(int keyCode, boolean pressed) {
        int mask = 0;
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A: mask = 1 << 0; break;
            case KeyEvent.KEYCODE_BUTTON_B: mask = 1 << 1; break;
            case KeyEvent.KEYCODE_BUTTON_X: mask = 1 << 2; break;
            case KeyEvent.KEYCODE_BUTTON_Y: mask = 1 << 3; break;
            case KeyEvent.KEYCODE_BUTTON_L1: mask = 1 << 4; break;
            case KeyEvent.KEYCODE_BUTTON_R1: mask = 1 << 5; break;
            case KeyEvent.KEYCODE_BUTTON_L2: mask = 1 << 6; break;
            case KeyEvent.KEYCODE_BUTTON_R2: mask = 1 << 7; break;
            default: return false;
        }
        if (pressed) buttonState |= mask;
        else buttonState &= ~mask;
        return true;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
            lx = (byte) (event.getAxisValue(MotionEvent.AXIS_X) * 127);
            ly = (byte) (event.getAxisValue(MotionEvent.AXIS_Y) * 127);
            rx = (byte) (event.getAxisValue(MotionEvent.AXIS_Z) * 127);
            ry = (byte) (event.getAxisValue(MotionEvent.AXIS_RZ) * 127);
            sendReport();
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    private void sendReport() {
        if (hidDeviceService != null && connectedDevice != null) {
            try {
                hidDeviceService.sendReport(connectedDevice, 0, new byte[]{buttonState, lx, ly, rx, ry});
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        }
    }
}
