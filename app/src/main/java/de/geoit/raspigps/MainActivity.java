package de.geoit.raspigps;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.MapBoxTileSource;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.w3c.dom.Text;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private final int REQUEST_PERMISSIONS_REQUEST_CODE = 1;
    private MapView map = null;

    private static final String TAG = "MainActivity";
    // Declare TextViews for displaying the GNSS data
    TextView gnssStatus, timestamp, num_sats, quality, receivedDataLat, receivedDataY, receivedDataLon, receivedDataX, receivedDataAlt, horizontalDil;
    private static final int MESSAGE_READ = 1;
    static final int STATE_MESSAGE_RECEIVED=5;
    private BluetoothAdapter bluetoothAdapter = null;
    private BluetoothSocket bluetoothSocket = null;
    private StringBuilder stringBuilder = new StringBuilder();
    private ConnectedThread connectedThread;

    // Raspberry Pi's Bluetooth MAC Address
    private static final String DEVICE_ADDRESS = "00:1A:7D:A0:06:58";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Mabbox Satellite Tile Provider for the MapView
        OnlineTileSourceBase MAPBOXSATELLITELABELLED = new MapBoxTileSourceFixed("MapBoxSatelliteLabelled", 1, 19, 256);
        ((MapBoxTileSource) MAPBOXSATELLITELABELLED).retrieveAccessToken(this);
        ((MapBoxTileSource) MAPBOXSATELLITELABELLED).retrieveMapBoxMapId(this);
        TileSourceFactory.addTileSource(MAPBOXSATELLITELABELLED);

        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));

        setContentView(R.layout.activity_main);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Initialize the Map View
        map = (MapView) findViewById(R.id.map);
        map.setTileSource(MAPBOXSATELLITELABELLED);

        // Extra Configuration (Look at Berlin when the App is opened)
        map.setBuiltInZoomControls(true);
        map.setMultiTouchControls(true);
        map.getController().setZoom(14.0);
        map.getController().setCenter(new GeoPoint(52.45, 13.35));

        // Request the neccessary Permissions
        requestPermissionsIfNecessary(new String[] {
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.INTERNET,
                Manifest.permission.BLUETOOTH
        });

        // Initialize Bluetooth Adapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Check if Bluetooth is supported on the device
        if (bluetoothAdapter == null) {
            Toast.makeText(MainActivity.this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
        }

        // Check if Bluetooth is enabled on the device
        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(getApplicationContext(), "Please enable Bluetooth and try again", Toast.LENGTH_LONG).show();
            finish();
        }




        Button startRTKButton = findViewById(R.id.bt_start_rtk);
        startRTKButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendData("START_RTK");
            }

        });



        // Connect Button
        ImageButton buttonConnect = findViewById(R.id.bt_connect);
        buttonConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                connectToDevice();
            }
        });

        Switch recordSwitch = findViewById(R.id.record_switch);

        // Set a listener to respond to switch state changes
        recordSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // Start recording GNSS data to CSV file
                    connectedThread.setRecording(true);
                } else {
                    // Stop recording GNSS data
                    connectedThread.setRecording(false);
                }
            }
        });

        // Initiallize the TextViews
        gnssStatus = findViewById(R.id.status_gnss);
        timestamp = findViewById(R.id.timestamp);
        num_sats = findViewById(R.id.num_sats);
        quality = findViewById(R.id.quality);
        receivedDataLat = findViewById(R.id.received_data_lat);
        receivedDataY = findViewById(R.id.received_data_y);
        receivedDataX = findViewById(R.id.received_data_x);
        receivedDataLon = findViewById(R.id.received_data_lon);
        receivedDataAlt = findViewById(R.id.received_data_alt);
        horizontalDil = findViewById(R.id.horizontal_dil);

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        ArrayList<String> permissionsToRequest = new ArrayList<>();
        for (int i = 0; i < grantResults.length; i++) {
            permissionsToRequest.add(permissions[i]);
        }
        if (permissionsToRequest.size() > 0) {
            ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toArray(new String[0]),
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }

    private void requestPermissionsIfNecessary(String[] permissions) {
        ArrayList<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                // Permission is not granted
                permissionsToRequest.add(permission);
            }
        }
        if (permissionsToRequest.size() > 0) {
            ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toArray(new String[0]),
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }

    // Add a marker to the map at the location of the connected GNSS device and keep it updated
    private void updateGNSSLocation(Double lat, Double lon) {
        if (lat != null && lon != null) {
            GeoPoint location = new GeoPoint(lat, lon);
            Marker locationMarker = new Marker(map);
            locationMarker.setPosition(location);
            locationMarker.setIcon(getResources().getDrawable(R.drawable.ic_marker));
            for (Overlay overlay : map.getOverlays()) {
                if (overlay instanceof Marker) {
                    map.getOverlayManager().remove(overlay);
                }
            }
            map.getOverlayManager().add(locationMarker);
            map.invalidate();
        }
        else {
            return;
        }

    }

    // Handle the messages received from the Bluetooth Server (Raspberry Pi)
    public Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {
            if (message.what == STATE_MESSAGE_RECEIVED) {
                String receivedData = (String) message.obj;
                //Log.d(TAG, "Received data: " + receivedData); // Verify received data

                // Handle Status information
                if (receivedData.startsWith("STATUS:")) {
                    String status = receivedData.substring(7); // Extract status message
                    gnssStatus.setText("GNSS status: " + status);
                }
                else if (receivedData.startsWith("01:")) {
                    Toast.makeText(MainActivity.this, "RTK started", Toast.LENGTH_SHORT).show();
                }
                else if (receivedData.startsWith("02:")) {
                    Toast.makeText(MainActivity.this, "Error executing str2str command", Toast.LENGTH_SHORT).show();
                }
                else if (receivedData.startsWith("03:")) {
                    Toast.makeText(MainActivity.this, "The Raspberry Pi has no Internet Connection", Toast.LENGTH_SHORT).show();
                }
                else {
                    // Handle coordinate data
                    String[] dataParts = receivedData.split(",");
                    if (dataParts.length >= 8) {
                        String time = dataParts[0];
                        String n_sats = dataParts[1];
                        String qual = dataParts[2];
                        String latitude = dataParts[3];
                        String dirLat = dataParts[4];
                        String longitude = dataParts[5];
                        String dirLon = dataParts[6];
                        String altitude = dataParts[7];
                        String h_dil = dataParts[8];
                        String utmY = dataParts[10];
                        String utmX = dataParts[11];

                        // Update TextViews with latitude, longitude, and altitude
                        timestamp.setText(time);
                        num_sats.setText(n_sats);
                        quality.setText(qual);
                        receivedDataLat.setText(latitude + " " + dirLat);
                        receivedDataLon.setText(longitude + " " + dirLon);
                        receivedDataAlt.setText(altitude + " m");
                        horizontalDil.setText("Horizontal dilation: " + h_dil);
                        receivedDataY.setText(utmY);
                        receivedDataX.setText(utmX);
                        updateGNSSLocation(Double.valueOf(latitude), Double.valueOf(longitude));
                    }
                }
            }
            return true;
        }
    });

    // Method to establish connection to the Bluetooth device
    private void connectToDevice() {
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(DEVICE_ADDRESS);
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // Standard SerialPortService ID
        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid);
            bluetoothSocket.connect();
            Toast.makeText(getApplicationContext(), "Connected to Raspberry Pi", Toast.LENGTH_LONG).show();
            connectedThread = new ConnectedThread(bluetoothSocket);
            connectedThread.start();
        } catch (IOException e) {
            Log.e(TAG, "Connection failed: " + e.getMessage());
            Toast.makeText(getApplicationContext(), "Connection failed. Check the Bluetooth device.", Toast.LENGTH_LONG).show();
        }
    }



    // Method to send data to the Raspberry Pi
    private void sendData(String data) {
        if (bluetoothSocket != null) {
            connectedThread.write(data.getBytes());
        } else {
            Toast.makeText(getApplicationContext(), "Bluetooth socket is not initialized", Toast.LENGTH_LONG).show();
        }
    }

    // Method to close the Bluetooth connection (not needed right now)
    private void disconnect() {
        if (bluetoothSocket != null) {
            try {
                bluetoothSocket.close();
                Toast.makeText(getApplicationContext(), "Disconnected from Raspberry Pi", Toast.LENGTH_LONG).show();
            } catch (IOException e) {
                Log.e(TAG, "Error while closing Bluetooth socket: " + e.getMessage());
            }
        }
    }


    // Thread to manage Bluetooth connection
    private class ConnectedThread extends Thread {
        private final InputStream inputStream;
        private final OutputStream outputStream;
        private BufferedWriter fileWriter;
        private boolean isRecording = false;
        private File outputFile;

        public ConnectedThread(BluetoothSocket socket) {
            InputStream tempIn = null;
            OutputStream tempOut = null;

            try {
                tempIn = socket.getInputStream();
                tempOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error creating InputStream/OutputStream: " + e.getMessage());
            }

            inputStream = tempIn;
            outputStream = tempOut;


        }

        private void createNewOutputFile() {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
            String timestamp = sdf.format(new Date());

            // Generate new output file name based on current timestamp
            outputFile = new File(getExternalFilesDir(null), "gnss_data_" + timestamp + ".csv");

            try {
                // Write the received data to a new csv file
                fileWriter = new BufferedWriter(new FileWriter(outputFile));
                fileWriter.write("Timestamp,Number_of_Satellites,Quality,Latitude,Latitude_Direction,Longitude,Longitude_Direction,Altitude,Horizontal_Dilation\n");
            } catch (IOException e) {
                Log.e(TAG, "Error opening CSV file: " + e.getMessage());
            }
        }


        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            while (true) {
                try {
                    bytes = inputStream.read(buffer);
                    String receivedData = new String(buffer, 0, bytes);
                    Log.d("receivedData:", receivedData);
                    handler.obtainMessage(STATE_MESSAGE_RECEIVED, bytes, -1, receivedData).sendToTarget();

                    if (isRecording) {

                        int startIndex = receivedData.startsWith("STATUS:") ? 6 : 0;
                        String dataAfterStart = receivedData.substring(startIndex);

                        if (dataAfterStart.contains(",UTM:")) {
                            String[] parts = dataAfterStart.split(",UTM:");
                            String relevantData = parts[0].trim();
                            Log.d("relevantData:", relevantData);
                            writeToFile(relevantData);
                        } else {
                            Log.d("relevantData:", dataAfterStart);
                            writeToFile(dataAfterStart);
                        }
                    }

                } catch (IOException e) {
                    Log.e(TAG, "Error reading from InputStream: " + e.getMessage());
                    break;
                }
            }
        }

        public void write(byte[] bytes) {
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                Log.e(TAG, "Error writing to OutputStream: " + e.getMessage());
            }
        }

        private void writeToFile(String data) {
            try {
                if (fileWriter == null) {
                    createNewOutputFile();
                }
                fileWriter.write(data + "\n");
                fileWriter.flush();
            } catch (IOException e) {
                Log.e(TAG, "Error writing to CSV file: " + e.getMessage());
            }
        }

        public void closeFileWriter() {
            try {
                if (fileWriter != null) {
                    fileWriter.close();
                    fileWriter = null; // Reset fileWriter to null after closing
                }
            } catch (IOException e) {
                Log.e(TAG, "Error closing CSV file: " + e.getMessage());
            }
        }

        public void setRecording(boolean recording) {
            isRecording = recording;
            if (!isRecording) {
                // If recording is stopped, close the file writer
                closeFileWriter();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        map.onResume(); //needed for compass, my location overlays, v6.0.0 and up
    }

    @Override
    public void onPause() {
        super.onPause();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().save(this, prefs);
        map.onPause();  //needed for compass, my location overlays, v6.0.0 and up
    }

}