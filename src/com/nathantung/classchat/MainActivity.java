package com.nathantung.classchat;

import java.util.Set;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

// Bluetooth implementation based on http://examples.javacodegeeks.com/android/core/bluetooth/bluetoothadapter/android-bluetooth-example/

public class MainActivity extends Activity {
	
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
			Toast.makeText(getApplicationContext(), "Bluetooth is not supported on your device!", Toast.LENGTH_LONG).show();
			
			// disable buttons
			btnToggle.setEnabled(false);
			btnDiscoverable.setEnabled(false);
			btnPaired.setEnabled(false);
			btnSearch.setEnabled(false);
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
		for(BluetoothDevice d: devices)
			arrayAdapter.add(d.getName() + "\n" + d.getAddress());
		
		// if no devices currently paired, notify user
		if(arrayAdapter.isEmpty())
			arrayAdapter.add("No paired devices!");
	}
	
	final BroadcastReceiver receiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			
			// clear array
			arrayAdapter.clear();
			
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
		if(arrayAdapter.isEmpty())
			arrayAdapter.add("No devices found!");			
		
	}

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
			
			Toast.makeText(getApplicationContext(), "Welcome, " + name + "!\n"
					+ "After clicking \"Allow Visibility\", you will currently be discoverable for "+ time + " time units.", Toast.LENGTH_LONG).show();			
			
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
}
