package com.nathantung.classchat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class BluetoothConnection {
	
	// STATIC APP INFORMATION
	public static final String SERVICE_STRING = "ClassChat";
	public static final UUID APP_UUID = UUID.fromString("1b6e87f1-39f9-4ee9-93eb-a454c48bd2f5");
	
	// BLUETOOTH VARIABLES
	private BluetoothAdapter adapter;
	
	// THREADS
	private final Handler mHandler;
	private AcceptThread mAcceptThread;
	private ConnectThread mConnectThread;
	private ConnectedThread mConnectedThread;

	// CONNECTION STATE
	private int currentState;
	public static final int STATE_NONE = 0; // doing nothing
	public static final int STATE_LISTEN = 1; // listening for incoming connections
	public static final int STATE_CONNECTING = 2; // initiating outgoing connection
	public static final int STATE_CONNECTED = 3; // connected to remote device
	
	public BluetoothConnection(Context context, Handler handler) {
		adapter = BluetoothAdapter.getDefaultAdapter();
		currentState = STATE_NONE;
		mHandler = handler;
	}
	
	private synchronized void setState(int state) {
		currentState = state;
		
		// Give the new state to the Handler so the UI Activity can update
		mHandler.obtainMessage(MainActivity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
		
		Log.d("MAMAMAAMMAMA", "AHHHHH");
	}
	
	public synchronized int getState() {
		return currentState;
	}
	
	public synchronized void start() {
		
		// Cancel any thread attempting to make a connection
		if (mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}
		
		// Cancel any thread currently running a connection
		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}
		
		// Start the thread to listen on a BluetoothServerSocket
		if (mAcceptThread == null) {
			mAcceptThread = new AcceptThread();
			mAcceptThread.start();
		}
		
		setState(STATE_LISTEN);
	}
	
	public synchronized void connect(BluetoothDevice device) {
		
        // Cancel any thread attempting to make a connection
        if (currentState == STATE_CONNECTING) {
            if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        }
        
        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
        
	 }
	
	public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {

		// Cancel the thread that completed the connection
		if (mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}
		
		// Cancel any thread currently running a connection
		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}
		
		// Cancel the accept thread because we only want to connect to one device
		if (mAcceptThread != null) {
			mAcceptThread.cancel();
			mAcceptThread = null;
		}
		
		// Start the thread to manage the connection and perform transmissions
		mConnectedThread = new ConnectedThread(socket);
		mConnectedThread.start();

		// Send the name of the connected device back to the UI Activity
		Message msg = mHandler.obtainMessage(MainActivity.MESSAGE_DEVICE_NAME);
		Bundle bundle = new Bundle();
		bundle.putString(MainActivity.DEVICE_NAME, device.getName());
		msg.setData(bundle);
		mHandler.sendMessage(msg);
		
		setState(STATE_CONNECTED);
		
	}
	
	public synchronized void stop() {
		if (mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}
		 
		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}
		
		if (mAcceptThread != null) {
			mAcceptThread.cancel();
			mAcceptThread = null;
		}
		
		setState(STATE_NONE);
	}
	
	public void write(byte[] out) {
		// Create temporary object
		ConnectedThread r;
	
		// Synchronize a copy of the ConnectedThread
		synchronized (this) {
			if (currentState != STATE_CONNECTED) return;
			r = mConnectedThread;
		}
		
		// Perform the write unsynchronized
		r.write(out);
	}

	private void connectionFailed() {
		setState(STATE_LISTEN);

		// Send a failure message back to the Activity
		Message msg = mHandler.obtainMessage(MainActivity.MESSAGE_TOAST);
		Bundle bundle = new Bundle();
		bundle.putString(MainActivity.TOAST, "Unable to connect device");
		msg.setData(bundle);
		mHandler.sendMessage(msg);
	}
	/**
	* Indicate that the connection was lost and notify the UI Activity.
	*/
	private void connectionLost() {
		setState(STATE_LISTEN);
		
		// Send a failure message back to the Activity
		Message msg = mHandler.obtainMessage(MainActivity.MESSAGE_TOAST);
		Bundle bundle = new Bundle();
		bundle.putString(MainActivity.TOAST, "Device connection was lost");
		msg.setData(bundle);
		mHandler.sendMessage(msg);
	}
	
	/* ACCEPT THREAD TO ESTABLISH SERVER SOCKET AND ACCEPT CONNECTIONS */

	private class AcceptThread extends Thread {
	    private final BluetoothServerSocket mmServerSocket;
	 
	    public AcceptThread() {
	        // use a temporary object that is later assigned to mmServerSocket, because mmServerSocket is final
	        BluetoothServerSocket tmp = null;
	        try {
	            // MY_UUID is the app's UUID string, also used by the client code
	            tmp = adapter.listenUsingRfcommWithServiceRecord(SERVICE_STRING, APP_UUID);
	        } catch (IOException e) { }
	        mmServerSocket = tmp;
	    }
	 
	    public void run() {
	        BluetoothSocket socket = null;
	        // keep listening until exception occurs or a socket is returned
	        while (currentState != STATE_CONNECTED) {
	            try {
	                socket = mmServerSocket.accept();
	            } catch (IOException e) {
	                break;
	            }
	            // if a connection was accepted
	            if (socket != null) {
	                // do work to manage the connection (in a separate thread)
	                synchronized (BluetoothConnection.this) {
	                	switch(currentState) {
		                	case STATE_LISTEN:
		                	case STATE_CONNECTING:
		                		connected(socket, socket.getRemoteDevice());
		                		break;
		                	case STATE_NONE:
		                	case STATE_CONNECTED:
				                try {
									mmServerSocket.close();
								} catch (IOException e) {}
				                break;
	                	}
	                }
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
	        // use a temporary object that is later assigned to mmSocket, because mmSocket is final
	        BluetoothSocket tmp = null;
	        mmDevice = device;
	        
	        // get a BluetoothSocket to connect with the given BluetoothDevice
	        try {
	            // MY_UUID is the app's UUID string, also used by the server code
	            tmp = device.createRfcommSocketToServiceRecord(APP_UUID);
	        } catch (IOException e) { }
	        mmSocket = tmp;
	    }
	 
	    public void run() {
	        // cancel discovery because it will slow down the connection
	        adapter.cancelDiscovery();
	 
	        Log.d("MMSOCKET1", mmDevice.getName());
	        
	        try {
		        Log.d("MMSOCKET2", mmDevice.getName());

	            // connect the device through the socket; this will block until it succeeds or throws an exception
	            mmSocket.connect();
	        } catch (IOException connectException) {
		        Log.d("MMSOCKET", mmDevice.getName()+" FAIL");

	            // unable to connect; close the socket and get out
	        	connectionFailed();
	            try {
	                mmSocket.close();
	            } catch (IOException closeException) { }
	            return;
	        }
	 
	        // do work to manage the connection (in a separate thread)
	        BluetoothConnection.this.start();
	        return;
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
	 
	        // get the input and output streams, using temp objects because member streams are final
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
	 
	        // keep listening to the InputStream until an exception occurs
	        while (true) {
	            try {
	                // read from the InputStream
	    	        Log.d("1", "CRAP");
	                bytes = mmInStream.read(buffer);
	                Log.d("2", "CRAP");
	                // send the obtained bytes to the UI activity
	                mHandler.obtainMessage(MainActivity.MESSAGE_READ, bytes, -1, buffer).sendToTarget();
	            } catch (IOException e) {
	            	connectionLost();
	                break;
	            }
	        }
	    }
	 
	    /* Call this from the main activity to send data to the remote device */
	    public void write(byte[] buffer) {
	        try {
	            mmOutStream.write(buffer);
	            mHandler.obtainMessage(MainActivity.MESSAGE_WRITE, -1, -1, buffer).sendToTarget();
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
