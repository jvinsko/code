package gvsu.vinsko.bluetoothscannerx;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.parceler.Parcels;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements DeviceFragment.OnListFragmentInteractionListener {
    public static int REQUEST_BLUETOOTH = 1;

    private Button logoutBtn;
    private TextView loggedIn;
    private BluetoothAdapter btAdapter;
    private FirebaseAuth mAuth;

    private DatabaseReference topRef;

    private LocationManager lm;
    private Location location;

    private DateTimeFormatter fmt;

    public static List<Device> currDevices;
    private List<Device> tempDevices;

    private Double currLat, currLong;

    private String userEmail;

    private Tracker mTracker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AnalyticsApplication application = (AnalyticsApplication) getApplication();
        mTracker = application.getDefaultTracker();

        currDevices = new ArrayList<Device>();
        tempDevices = new ArrayList<Device>();

        mAuth = FirebaseAuth.getInstance();

        logoutBtn = (Button) findViewById(R.id.logoutBtn);
        loggedIn = (TextView) findViewById(R.id.loggedIn);

        Intent i = this.getIntent();
        userEmail = i.getStringExtra("email");
        loggedIn.setText("Logged In As: " + userEmail);

        logoutBtn.setOnClickListener(v -> {
            //mAuth.signOut();
            mAuth.getInstance().signOut();
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        });

        fmt = DateTimeFormat.forPattern("dd MMM, yyyy HH:mm:ss");

        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
            //return;
        }
        location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (location != null) {
            currLong = location.getLongitude();
            currLat = location.getLatitude();
        }

        btAdapter = BluetoothAdapter.getDefaultAdapter();
        // Phone does not support Bluetooth so let the user know and exit.
        if (btAdapter == null) {
            new AlertDialog.Builder(this)
                    .setTitle("Not compatible")
                    .setMessage("Your phone does not support Bluetooth")
                    .setPositiveButton("Exit", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            System.exit(0);
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        }
        if (!btAdapter.isEnabled()) {
            Intent enableBT = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBT, REQUEST_BLUETOOTH);
        }

        btAdapter.startDiscovery();


        // Register for broadcasts when a device is discovered.
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        IntentFilter filter2 = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(bReceiver, filter);
        this.registerReceiver(bReceiver, filter2);
    }

    private final BroadcastReceiver bReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent
                        .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Device item = new Device();
                item.setName(device.getName());
                item.setAddress(device.getAddress());
                int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                item.setRssi(rssi);
                tempDevices.add(item);
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                currDevices = new ArrayList<Device>(tempDevices);
                DeviceFragment deviceFragment = (DeviceFragment) getSupportFragmentManager().findFragmentById(R.id.deviceFragment);
                if (deviceFragment != null) {
                    deviceFragment.refreshList();
                }
                tempDevices.clear();
                btAdapter.startDiscovery();
            }
        }
    };

    private Parcelable sendToFirebase(Device item) {
        Device device = item;
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        }
        location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (location != null) {
            currLong = location.getLongitude();
            currLat = location.getLatitude();
        } else {
            currLong = 0.0;
            currLat = 0.0;
        }
        device.setFoundLat(currLat);
        device.setFoundLong(currLong);
        device.setFoundDate(fmt.print(DateTime.now()));
        Parcelable parcel = Parcels.wrap(device);
        return parcel;
    }

    @Override
    public void onListFragmentInteraction(Device device) {
        Intent intent = new Intent(this, DeviceDetailsActivity.class);
        Parcelable parcel = sendToFirebase(device);
        intent.putExtra("DEVICE", parcel);
        startActivity(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        topRef = FirebaseDatabase.getInstance().getReference();
        mTracker.setScreenName("MainActivity");
        mTracker.send(new HitBuilders.ScreenViewBuilder().build());
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(bReceiver);
        super.onDestroy();
    }
}