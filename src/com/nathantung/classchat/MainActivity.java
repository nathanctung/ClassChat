package com.nathantung.classchat;

import java.util.Set;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

/*

STORIES TO IMPLEMENT:
-save contacts
-save conversation
-recent connections
-recommended connections
-picture transfer

*/

// Bluetooth implementation based on http://examples.javacodegeeks.com/android/core/bluetooth/bluetoothadapter/android-bluetooth-example/

public class MainActivity extends Activity {
	
	// HANDLER MESSAGE TYPES
	public static final int MESSAGE_STATE_CHANGE = 1;
	public static final int MESSAGE_READ = 2;
	public static final int MESSAGE_WRITE = 3;
	public static final int MESSAGE_DEVICE_NAME = 4;
	public static final int MESSAGE_TOAST = 5;
	
	// REQUEST CODES
	private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;
    
    // HANDLER KEY NAMES
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
    public static String EXTRA_DEVICE_ADDRESS = "device_address";
    
	// VIEW AND LAYOUT
	private ListView mConversationView;
	private EditText mOutEditText;
	private Button mSendButton;
	
	private Button btnToggle;
	private Button btnDiscoverable;
	private Button btnPaired;
	private Button btnSearch;
	
	// COMMUNICATION VARIABLES
	private BluetoothConnection connection;
	private String mConnectedDeviceName = null;
	private ArrayAdapter<String> mConversationArrayAdapter;
	private StringBuffer mOutStringBuffer;
	
	// LIST AND DEVICES VARIABLES
	private ListView pairedListView;
	private ListView recommendedListView;
	private ListView otherListView;
	private ArrayAdapter<String> pairedDevicesArrayAdapter;
	private ArrayAdapter<String> recommendedDevicesArrayAdapter;
	private ArrayAdapter<String> otherDevicesArrayAdapter;

	// BLUETOOTH VARIABLES
	private BluetoothAdapter adapter;
	private Set<BluetoothDevice> devices;
	private SharedPreferences preferences;
	
/* ON INITIALIZATION */
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		
		// grab preferences and saved variables
		preferences = PreferenceManager.getDefaultSharedPreferences(this);
		
		// create instance of BluetoothAdapter
		adapter = BluetoothAdapter.getDefaultAdapter();
		
		// checking whether adapter is instanced tells us if Bluetooth is supported
		if(adapter==null) {
			
			// alert the user of incompatibility before closing (finishing) the app
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage("Bluetooth is not supported on your device!")
			       .setCancelable(false)
			       .setPositiveButton("OK", new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			        	   finish();
			           }
			       });
			AlertDialog alert = builder.create();
			alert.show();
			
		}
		else {
			
			String name = preferences.getString("prefScreenName", "ClassChatter");
			Toast.makeText(getApplicationContext(), "Welcome, " + name + "!", Toast.LENGTH_SHORT).show();
			
			// setup Bluetooth toggle button
			btnToggle = (Button) findViewById(R.id.bluetoothToggle);
			btnToggle.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					toggleBluetooth(v);
				}
			});
			
			// check if Bluetooth is currently enabled or not
			if(adapter.isEnabled())
				btnToggle.setText("STATUS: ON");
			else
				btnToggle.setText("STATUS: OFF");

			// setup Bluetooth discoverable (visible) button
			btnDiscoverable = (Button) findViewById(R.id.bluetoothDiscoverable);
			btnDiscoverable.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					setDiscoverable(v);
				}
			});
			
			// setup Bluetooth fetch devices button
			btnPaired = (Button) findViewById(R.id.bluetoothFetchDevices);
			btnPaired.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					showPairedDevices(v);
					searchDevices(v);
				}
			});
			
			// set up listView to hold arrayAdapter's list of bluetooth devices
			
			pairedListView = (ListView) findViewById(R.id.pairedListView);
			pairedDevicesArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
			pairedListView.setAdapter(pairedDevicesArrayAdapter);
			
			recommendedListView = (ListView) findViewById(R.id.recommendedListView);
			recommendedDevicesArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
			recommendedListView.setAdapter(recommendedDevicesArrayAdapter);
			
			otherListView = (ListView) findViewById(R.id.otherListView);
			otherDevicesArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
			otherListView.setAdapter(otherDevicesArrayAdapter);
			
			// set up click listener on the list view
			pairedListView.setOnItemClickListener(new OnItemClickListener() {
				public void onItemClick(AdapterView<?> adapterView, View v, int position, long id) {
					setupConnection(v, position, pairedDevicesArrayAdapter);
				}
			});
			
			recommendedListView.setOnItemClickListener(new OnItemClickListener() {
				public void onItemClick(AdapterView<?> adapterView, View v, int position, long id) {
					setupConnection(v, position, recommendedDevicesArrayAdapter);
				}
			});
			
			otherListView.setOnItemClickListener(new OnItemClickListener() {
				public void onItemClick(AdapterView<?> adapterView, View v, int position, long id) {
					setupConnection(v, position, otherDevicesArrayAdapter);
				}
			});
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		
		// If BT is not on, request that it be enabled.
		// setupChat() will then be called during onActivityResult
		if (!adapter.isEnabled()) {
			Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
			// Otherwise, setup the chat session
		} else if(connection == null) {
			setupChat();
		}
	}
 
	@Override
	public synchronized void onResume() {
		super.onResume();

		// Performing this check in onResume() covers the case in which BT was
		// not eabled during onStart(), so we were paused to enable it...
		// onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
		if (connection != null) {
			// Only if the state is STATE_NONE, do we know that we haven't started already
			if (connection.getState() == BluetoothConnection.STATE_NONE) {
				// Start the Bluetooth chat services
				connection.start();
			}
		}
	}
	
	private void setupChat() {
        
		// Initialize the BluetoothConnection to perform bluetooth connections
		connection = new BluetoothConnection(this, mHandler);
		
	}
	
	protected void setupConnection(View v, int position, ArrayAdapter<String> arrayAdapter) {
		
		// obtain mac address of clicked device
		String deviceDescriptor = arrayAdapter.getItem(position);
		int length = deviceDescriptor.length();
		String mac = deviceDescriptor.substring(length-17, length);
		
		// reconstruct device using mac address
		BluetoothDevice device = adapter.getRemoteDevice(mac);
		
		Boolean secure = false;
		
		connection.connect(device, secure);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		// Stop the Bluetooth chat services
		if (connection != null) connection.stop();
	}
	
    private final void setStatus(int resId) {
        final ActionBar actionBar = getActionBar();
        actionBar.setSubtitle(resId);
        
        if(BluetoothMessage.actionBar!=null)
        	BluetoothMessage.actionBar.setSubtitle(resId);
    }

    private final void setStatus(CharSequence subTitle) {
        final ActionBar actionBar = getActionBar();
        actionBar.setSubtitle(subTitle);
        
        if(BluetoothMessage.actionBar!=null)
        	BluetoothMessage.actionBar.setSubtitle(subTitle);
    }
	
	private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_STATE_CHANGE:
                switch (msg.arg1) {
                case BluetoothConnection.STATE_CONNECTED:
                    setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
                    //mConversationArrayAdapter.clear();
                    break;
                case BluetoothConnection.STATE_CONNECTING:
                    setStatus(R.string.title_connecting);
                    break;
                case BluetoothConnection.STATE_LISTEN:
                case BluetoothConnection.STATE_NONE:
                case BluetoothConnection.STATE_BUSY:
                    setStatus(R.string.title_not_connected);
                    break;
                }
                break;
            case MESSAGE_WRITE:
                byte[] writeBuf = (byte[]) msg.obj;
                // construct a string from the buffer
                String writeMessage = new String(writeBuf);
                //mConversationArrayAdapter.add("Me:  " + writeMessage);
                String writeLine = "Me:  " + writeMessage;
                Intent intent_write = new Intent(getApplicationContext(), BluetoothMessage.class);
                intent_write.putExtra("new_line", writeLine);
        		startActivity(intent_write);
                
                break;
            case MESSAGE_READ:
                byte[] readBuf = (byte[]) msg.obj;
                // construct a string from the valid bytes in the buffer
                String readMessage = new String(readBuf, 0, msg.arg1);
                //mConversationArrayAdapter.add(mConnectedDeviceName + ":  " + readMessage);
                String readLine = mConnectedDeviceName + ":  " + readMessage;
                Intent intent_read = new Intent(getApplicationContext(), BluetoothMessage.class);
                intent_read.putExtra("new_line", readLine);
        		startActivity(intent_read);
                
                
                break;
            case MESSAGE_DEVICE_NAME:
                // save the connected device's name
                mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                Toast.makeText(getApplicationContext(), "Connected to " + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                
                BluetoothMessage.myConnection = connection;
        		Intent intent_new = new Intent(getApplicationContext(), BluetoothMessage.class);
        		startActivity(intent_new);
        		
                break;
            case MESSAGE_TOAST:
                Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST), Toast.LENGTH_SHORT).show();
                break;
            }
        }
    };
	
	protected void toggleBluetooth(View v) {
		
		if(!adapter.isEnabled()) {
			// if adapter is not enabled, start Bluetooth
			Intent intent = new Intent(adapter.ACTION_REQUEST_ENABLE);
			// overriden method checks actual Bluetooth status post-prompt, updates button text
			startActivityForResult(intent, REQUEST_ENABLE_BT);
		}
		else {
			// otherwise, Bluetooth is enabled, so we disable it
			adapter.disable();
			btnToggle.setText("STATUS: OFF");
		}
		
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(requestCode==REQUEST_ENABLE_BT) {
			if(adapter.isEnabled())
				btnToggle.setText("STATUS: ON");
			else
				btnToggle.setText("STATUS: OFF");
		}
	}
	
	protected void setDiscoverable(View v) {
		// start intent to make device visible/discoverable to other devices
		Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
		
		// length of discoverable time based on settings
		int time = Integer.parseInt(preferences.getString("prefDiscoverableTime", "120"));
		
		if(time!=120) {
			intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, time);			
		}

		startActivity(intent);
	}
	
// FINDING PAIRED DEVICES
	
	protected void showPairedDevices(View v) {
		// fetch devices currently paired with adapter
		devices = adapter.getBondedDevices();
		
		// clear array
		pairedDevicesArrayAdapter.clear();
		
		// for each of these devices d, show name/address
		for(BluetoothDevice d: devices) {
			pairedDevicesArrayAdapter.add(d.getName() + "\n" + d.getAddress());
		}
			
	}
	
// FINDING OTHER DEVICES
	
	final BroadcastReceiver receiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			
			// clear array
			otherDevicesArrayAdapter.clear();
			
			if(BluetoothDevice.ACTION_FOUND.equals(action)) {
				BluetoothDevice d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				otherDevicesArrayAdapter.add(d.getName() + "\n" + d.getAddress());
				otherDevicesArrayAdapter.notifyDataSetChanged();
			}	
		}
	};
	
	protected void searchDevices(View v) {
		if(adapter.isDiscovering()) {
			adapter.cancelDiscovery();
		}
		else {
			otherDevicesArrayAdapter.clear();
			adapter.startDiscovery();
			registerReceiver(receiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
		}
		
	}

/* SETTINGS ICON AND ACTIVITY */	
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			
			// start settings intent
			Intent intent = new Intent(getApplicationContext(), SettingsActivity.class);
			startActivity(intent);
			
			String name = preferences.getString("prefScreenName", "ClassChatter");
			String time = preferences.getString("prefDiscoverableTime", "120");
			
			if(time.equals("0")) {
				Toast.makeText(getApplicationContext(), "Hello " + name + "!\n"
						+ "After clicking \"Go Visible\", you will currently be indefinitely discoverable.", Toast.LENGTH_SHORT).show();
			}
			
			Toast.makeText(getApplicationContext(), "Hello " + name + "!\n"
					+ "After clicking \"Go Visible\", you will currently be discoverable for "+ time + " seconds.", Toast.LENGTH_SHORT).show();			
			
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
}