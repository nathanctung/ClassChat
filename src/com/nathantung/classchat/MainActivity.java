package com.nathantung.classchat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

// Bluetooth implementation based on http://examples.javacodegeeks.com/android/core/bluetooth/bluetoothadapter/android-bluetooth-example/

public class MainActivity extends Activity {
	
	// STATIC APP INFORMATION
	public static final String SERVICE_STRING = "ClassChat";
	public static final UUID APP_UUID = UUID.fromString("1b6e87f1-39f9-4ee9-93eb-a454c48bd2f5");
	
	// APP VARIABLES
	private static final int REQUEST_ENABLE_BT = 1;
	private Button btnToggle;
	private Button btnDiscoverable;
	private Button btnPaired;
	private Button btnSearch;
	private BluetoothAdapter adapter;
	private Set<BluetoothDevice> devices;
	private ListView listView;
	private ArrayAdapter<String> arrayAdapter;
	private SharedPreferences preferences; 

	// THREADS
	//private final Handler mHandler;
	private AcceptThread mAcceptThread;
	private ConnectThread mConnectThread;
	private ConnectedThread mConnectedThread;
	
	// APP CONNECTION STATE
	private int currentState;
	public static final int STATE_NONE = 0; // doing nothing
	public static final int STATE_LISTEN = 1; // listening for incoming connections
	public static final int STATE_CONNECTING = 2; // initiating outgoing connection
	public static final int STATE_CONNECTED = 3; // connected to remote device
	
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
			
			// Toast.makeText(getApplicationContext(), "Bluetooth is not supported on your device!", Toast.LENGTH_LONG).show();
			
		}
		else {
			Toast.makeText(getApplicationContext(), "Congratulations, Bluetooth is supported!", Toast.LENGTH_SHORT).show();
			
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
		
		// if no devices found, notify user
		//if(arrayAdapter.isEmpty())
			//arrayAdapter.add("No devices found!");
		
	}

	protected void setupConnection(View v, int position) {
		
		// obtain mac address of clicked device
		String deviceDescriptor = arrayAdapter.getItem(position);
		int length = deviceDescriptor.length();
		String mac = deviceDescriptor.substring(length-17, length);
		Toast.makeText(getApplicationContext(), mac, Toast.LENGTH_SHORT).show();
		
		// reconstruct device using mac address
		BluetoothDevice device = adapter.getRemoteDevice(mac);
		
		 // Cancel any thread attempting to make a connection
		if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
		
		// Cancel any thread currently running a connection
		if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}
		
		// Start the thread to listen on a BluetoothServerSocket
		if (mAcceptThread == null) {
			mAcceptThread = new AcceptThread();
			mAcceptThread.start();
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
				Toast.makeText(getApplicationContext(), "Welcome, " + name + "!\n"
						+ "After clicking \"Allow Visibility\", you will currently be indefinitely discoverable.", Toast.LENGTH_SHORT).show();
			}
			
			Toast.makeText(getApplicationContext(), "Welcome, " + name + "!\n"
					+ "After clicking \"Allow Visibility\", you will currently be discoverable for "+ time + " seconds.", Toast.LENGTH_SHORT).show();			
			
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
/* ACCEPT THREAD TO ESTABLISH SERVER SOCKET AND ACCEPT CONNECTIONS */

	private class AcceptThread extends Thread {
	    private final BluetoothServerSocket mmServerSocket;
	 
	    public AcceptThread() {
	        // Use a temporary object that is later assigned to mmServerSocket,
	        // because mmServerSocket is final
	        BluetoothServerSocket tmp = null;
	        try {
	            // MY_UUID is the app's UUID string, also used by the client code
	            tmp = adapter.listenUsingRfcommWithServiceRecord(SERVICE_STRING, APP_UUID);
	        } catch (IOException e) { }
	        mmServerSocket = tmp;
	    }
	 
	    public void run() {
	        BluetoothSocket socket = null;
	        // Keep listening until exception occurs or a socket is returned
	        while (true) {
	            try {
	                socket = mmServerSocket.accept();
	            } catch (IOException e) {
	                break;
	            }
	            // If a connection was accepted
	            if (socket != null) {
	                // Do work to manage the connection (in a separate thread)
	                //manageConnectedSocket(socket);
	                try {
						mmServerSocket.close();
					} catch (IOException e) {}
	                break;
	            }
	        }
	    }
	 
	    /** Will cancel the listening socket, and cause the thread to finish */
	    public void cancel() {
	        try {
	            mmServerSocket.close();
	        } catch (IOException e) { }
	    }
	}
	

/* CONNECT THREAD TO INITIATE CONNECTION WITH SERVER */	

	private class ConnectThread extends Thread {
	    private final BluetoothSocket mmSocket;
	    private final BluetoothDevice mmDevice;
	 
	    public ConnectThread(BluetoothDevice device) {
	        // Use a temporary object that is later assigned to mmSocket,
	        // because mmSocket is final
	        BluetoothSocket tmp = null;
	        mmDevice = device;
	 
	        // Get a BluetoothSocket to connect with the given BluetoothDevice
	        try {
	            // MY_UUID is the app's UUID string, also used by the server code
	            tmp = device.createRfcommSocketToServiceRecord(APP_UUID);
	        } catch (IOException e) { }
	        mmSocket = tmp;
	    }
	 
	    public void run() {
	        // Cancel discovery because it will slow down the connection
	        adapter.cancelDiscovery();
	 
	        try {
	            // Connect the device through the socket. This will block
	            // until it succeeds or throws an exception
	            mmSocket.connect();
	        } catch (IOException connectException) {
	            // Unable to connect; close the socket and get out
	            try {
	                mmSocket.close();
	            } catch (IOException closeException) { }
	            return;
	        }
	 
	        // Do work to manage the connection (in a separate thread)
	        //manageConnectedSocket(mmSocket);
	    }
	 
	    /** Will cancel an in-progress connection, and close the socket */
	    public void cancel() {
	        try {
	            mmSocket.close();
	        } catch (IOException e) { }
	    }
	}
		

/* ALREADY CONNECTED THREAD TO MANAGE CONNECTION */

	private class ConnectedThread extends Thread {
	    private final BluetoothSocket mmSocket;
	    private final InputStream mmInStream;
	    private final OutputStream mmOutStream;
	 
	    public ConnectedThread(BluetoothSocket socket) {
	        mmSocket = socket;
	        InputStream tmpIn = null;
	        OutputStream tmpOut = null;
	 
	        // Get the input and output streams, using temp objects because
	        // member streams are final
	        try {
	            tmpIn = socket.getInputStream();
	            tmpOut = socket.getOutputStream();
	        } catch (IOException e) { }
	 
	        mmInStream = tmpIn;
	        mmOutStream = tmpOut;
	    }
	 
	    public void run() {
	        byte[] buffer = new byte[1024];  // buffer store for the stream
	        int bytes; // bytes returned from read()
	 
	        // Keep listening to the InputStream until an exception occurs
	        while (true) {
	            try {
	                // Read from the InputStream
	                bytes = mmInStream.read(buffer);
	                // Send the obtained bytes to the UI activity
	                //mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer).sendToTarget();
	            } catch (IOException e) {
	                break;
	            }
	        }
	    }
	 
	    /* Call this from the main activity to send data to the remote device */
	    public void write(byte[] bytes) {
	        try {
	            mmOutStream.write(bytes);
	        } catch (IOException e) { }
	    }
	 
	    /* Call this from the main activity to shutdown the connection */
	    public void cancel() {
	        try {
	            mmSocket.close();
	        } catch (IOException e) { }
	    }
	}
	
	
}