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
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

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
	private ListView listView;
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
	
	// BLUETOOTH VARIABLES
	private BluetoothAdapter adapter;
	private Set<BluetoothDevice> devices;
	private ArrayAdapter<String> arrayAdapter;
	private SharedPreferences preferences;
	
/* ON INITIALIZATION */
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
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
			
			// setup Bluetooth paired button
			btnPaired = (Button) findViewById(R.id.bluetoothPaired);
			btnPaired.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					showPairedDevices(v);
				}
			});
			
			// setup Bluetooth search button
			btnSearch = (Button) findViewById(R.id.bluetoothSearch);
			btnSearch.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					searchDevices(v);
				}
			});
			
			// set up listView to hold arrayAdapter's list of bluetooth devices
			listView = (ListView) findViewById(R.id.listView);
			arrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
			listView.setAdapter(arrayAdapter);
			
			// set up click listener on the list view
			listView.setOnItemClickListener(new OnItemClickListener() {
				public void onItemClick(AdapterView<?> adapterView, View v, int position, long id) {
					//Toast.makeText(getApplicationContext(), "clicked on item: " + position + ", " + id, Toast.LENGTH_SHORT).show();
					setupConnection(v, position);
					
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

		/*
		// Initialize the array adapter for the conversation thread
		mConversationArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);
		mConversationView = (ListView) findViewById(R.id.in);
		mConversationView.setAdapter(mConversationArrayAdapter);
		
		// Initialize the compose field with a listener for the return key
		mOutEditText = (EditText) findViewById(R.id.edit_text_out);
		mOutEditText.setOnEditorActionListener(mWriteListener);
		
		// Initialize the send button with a listener that for click events
		mSendButton = (Button) findViewById(R.id.button_send);
		mSendButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
		
			// Send a message using content of the edit text widget
			TextView view = (TextView) findViewById(R.id.edit_text_out);
			String message = view.getText().toString();
			sendMessage(message);
			}
		});
		*/
		
		// Initialize the BluetoothConnection to perform bluetooth connections
		connection = new BluetoothConnection(this, mHandler);
		// Initialize the buffer for outgoing messages
		mOutStringBuffer = new StringBuffer("");

	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		// Stop the Bluetooth chat services
		if (connection != null) connection.stop();
	}
	
	private void sendMessage(String message) {
		// Check that we're actually connected before trying anything
		if (connection.getState() != BluetoothConnection.STATE_CONNECTED) {
			Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
			return;
		}
		
		// Check that there's actually something to send
		if (message.length() > 0) {
			// Get the message bytes and tell the BluetoothConnection to write
			byte[] send = message.getBytes();
			connection.write(send);
			// Reset out string buffer to zero and clear the edit text field
			mOutStringBuffer.setLength(0);
			mOutEditText.setText(mOutStringBuffer);
		}
	}
	
	// The action listener for the EditText widget, to listen for the return key
	private TextView.OnEditorActionListener mWriteListener = new TextView.OnEditorActionListener() {
		public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
			// If the action is a key-up event on the return key, send the message
			if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
				String message = view.getText().toString();
				sendMessage(message);
			}
			
			return true;
		}
	};
	
    private final void setStatus(int resId) {
        final ActionBar actionBar = getActionBar();
        actionBar.setSubtitle(resId);
    }

    private final void setStatus(CharSequence subTitle) {
        final ActionBar actionBar = getActionBar();
        actionBar.setSubtitle(subTitle);
    }
	
	private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_STATE_CHANGE:
        		Log.d("MESSAGE STATE CHANGE", "AHHHHH");
                switch (msg.arg1) {
                case BluetoothConnection.STATE_CONNECTED:
            		Log.d("CONNECTED", "AHHHHH");
                    setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
            		Log.d("CLEAR ATTEMPT", "AHHHHH");
                    //mConversationArrayAdapter.clear();
            		Log.d("CLEARED?", "AHHHHH");
                    break;
                case BluetoothConnection.STATE_CONNECTING:
            		Log.d("CONNECTING", "AHHHHH");
                    setStatus(R.string.title_connecting);
                    break;
                case BluetoothConnection.STATE_LISTEN:
                case BluetoothConnection.STATE_NONE:
            		Log.d("LISTEN OR NONE", "AHHHHH");
                    setStatus(R.string.title_not_connected);
                    break;
                }
                break;
            case MESSAGE_WRITE:
            	Log.d("MESSAGE WRITE", "AHHHHH");
                byte[] writeBuf = (byte[]) msg.obj;
                // construct a string from the buffer
                String writeMessage = new String(writeBuf);
                mConversationArrayAdapter.add("Me:  " + writeMessage);
                break;
            case MESSAGE_READ:
            	Log.d("MESSAGE READ", "AHHHHH");
                byte[] readBuf = (byte[]) msg.obj;
                // construct a string from the valid bytes in the buffer
                String readMessage = new String(readBuf, 0, msg.arg1);
                mConversationArrayAdapter.add(mConnectedDeviceName+":  " + readMessage);
                break;
            case MESSAGE_DEVICE_NAME:
            	Log.d("MESSAGE DEVICE NAME", "AHHHHH");
                // save the connected device's name
                mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                Toast.makeText(getApplicationContext(), "Connected to "
                               + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                break;
            case MESSAGE_TOAST:
            	Log.d("MESSAGE TOAST", "AHHHHH");
                Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                               Toast.LENGTH_SHORT).show();
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
	
	protected void showPairedDevices(View v) {
		// fetch devices currently paired with adapter
		devices = adapter.getBondedDevices();
		
		// clear array
		arrayAdapter.clear();
		
		// for each of these devices d, show name/address
		for(BluetoothDevice d: devices) {
			arrayAdapter.add(d.getName() + "\n" + d.getAddress());
		}
			
		// if no devices currently paired, notify user
		if(arrayAdapter.isEmpty())
			arrayAdapter.add("No paired devices!");
	}
	
	final BroadcastReceiver receiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			
			// clear array
			// arrayAdapter.clear();
			
			if(BluetoothDevice.ACTION_FOUND.equals(action)) {
				BluetoothDevice d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				arrayAdapter.add(d.getName() + "\n" + d.getAddress());
				arrayAdapter.notifyDataSetChanged();
			}	
		}
	};
	
	protected void searchDevices(View v) {
		if(adapter.isDiscovering()) {
			adapter.cancelDiscovery();
		}
		else {
			arrayAdapter.clear();
			adapter.startDiscovery();
			registerReceiver(receiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
		}
		
	}

	protected void setupConnection(View v, int position) {
		
		// obtain mac address of clicked device
		String deviceDescriptor = arrayAdapter.getItem(position);
		int length = deviceDescriptor.length();
		String mac = deviceDescriptor.substring(length-17, length);
		
		//Toast.makeText(getApplicationContext(), mac, Toast.LENGTH_SHORT).show();
		
		// reconstruct device using mac address
		BluetoothDevice device = adapter.getRemoteDevice(mac);
		
		connection.connect(device);
		
		Intent intent = new Intent(getApplicationContext(), BluetoothMessage.class);
		startActivity(intent);
		
        // Create the result Intent and include the MAC address
//        Intent intent = new Intent();
//        intent.putExtra(EXTRA_DEVICE_ADDRESS, mac);

        
        // Set result and finish this Activity
//        setResult(Activity.RESULT_OK, intent);
        //finish();
	}
	
	
/*
	
	private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        String address = data.getExtras().getString(this.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = adapter.getRemoteDevice(address);
        // Attempt to connect to the device
        connection.connect(device);
    }

*/

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
				Toast.makeText(getApplicationContext(), "Welcome, " + name + "!\n"
						+ "After clicking \"Allow Visibility\", you will currently be indefinitely discoverable.", Toast.LENGTH_SHORT).show();
			}
			
			Toast.makeText(getApplicationContext(), "Welcome, " + name + "!\n"
					+ "After clicking \"Allow Visibility\", you will currently be discoverable for "+ time + " seconds.", Toast.LENGTH_SHORT).show();			
			
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
/*
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent serverIntent = null;
        switch (item.getItemId()) {
        case R.id.secure_connect_scan:
            // Launch the DeviceListActivity to see devices and do scan
            serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
            return true;
        case R.id.insecure_connect_scan:
            // Launch the DeviceListActivity to see devices and do scan
            serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);
            return true;
        case R.id.discoverable:
            // Ensure this device is discoverable by others
            ensureDiscoverable();
            return true;
        }
        return false;
    }
*/
	
	
	
}