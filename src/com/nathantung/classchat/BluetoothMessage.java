package com.nathantung.classchat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class BluetoothMessage extends Activity {

	public static BluetoothConnection myConnection = null;
	public static ActionBar actionBar;
	
    // Layout Views
    private ListView mConversationView;
    private EditText mOutEditText;
    private Button mSendButton;
    private ArrayAdapter<String> mConversationArrayAdapter;

    // MainActivity Variables
	private StringBuffer mOutStringBuffer;

	
	/*
		Configuration config = getResources().getConfiguration();
		FragmentManager fragmentManager = getSupportFragmentManager();
		FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
		
		Fragment messageFragment = new MessageFragment();
		fragmentTransaction.replace(android.R.id.content, messageFragment);		
		fragmentTransaction.commit();
    */
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_message);
		
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		
		actionBar = getActionBar();
		
		// Initialize the array adapter for the conversation thread
        //mConversationArrayAdapter = new ArrayAdapter<String>(this, R.layout.single_message, R.id.messageText);
        mConversationArrayAdapter = new ArrayAdapter<String>(this, R.layout.single_message);
        mConversationView = (ListView) findViewById(R.id.messageListView);
        mConversationView.setAdapter(mConversationArrayAdapter);

        // Initialize the compose field with a listener for the return key
        mOutEditText = (EditText) findViewById(R.id.messageEditText);
        mOutEditText.setOnEditorActionListener(mWriteListener);

        // Initialize the send button with a listener that for click events
        mSendButton = (Button) findViewById(R.id.messageSend);
        mSendButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                TextView view = (TextView) findViewById(R.id.messageEditText);
                String message = view.getText().toString();
                sendMessage(message);
            }
        });

		// Initialize the buffer for outgoing messages
		mOutStringBuffer = new StringBuffer("");
		
	}
	
	@Override
	protected void onNewIntent(Intent intent) {
		  super.onNewIntent(intent);
		  
		  // set getIntent() to return this new intent
		  setIntent(intent);
		  
		  Intent currIntent= getIntent();
		  
		  Bundle extras = currIntent.getExtras();
		  
		  if(extras!=null) {
			  String line = currIntent.getExtras().getString("new_line");
			  
			  if(line!=null)
				  mConversationArrayAdapter.add(line);
		  }
		  
	}
	
    private void sendMessage(String message) {

    	// Check that we're actually connected before trying anything

    	if (myConnection.getState() != BluetoothConnection.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            myConnection.write(send);

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
            mOutEditText.setText(mOutStringBuffer);
            
        }	
    }

    // The action listener for the EditText widget, to listen for the return key
    private TextView.OnEditorActionListener mWriteListener =
        new TextView.OnEditorActionListener() {
        public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
            // If the action is a key-up event on the return key, send the message
            if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                String message = view.getText().toString();
                sendMessage(message);
            }
            return true;
        }
    };
    
    @Override
	public void onDestroy() {
		super.onDestroy();

		if(myConnection.getState()==BluetoothConnection.STATE_CONNECTED) {
		
			if(mConversationArrayAdapter!=null)
				mConversationArrayAdapter.add("CONNECTION TERMINATED!");
			
			myConnection.endConnection();
		}
		
	}
    
    public void saveToFile() {
    	
    	if(mConversationArrayAdapter!=null) {

        	File file = getFileStreamPath("test.txt");

        	if (!file.exists()) {
        	   try {
				file.createNewFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        	}

        	FileOutputStream writer;
			try {
				writer = openFileOutput(file.getName(), Context.MODE_PRIVATE);
				
				for(int i=0; i< mConversationArrayAdapter.getCount(); i++) {
	        		String line = mConversationArrayAdapter.getItem(i);
	        		try {
						writer.write(line.getBytes());
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
	        	    try {
						writer.flush();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
	        	}

				try {
					writer.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
    	else {
    		Toast.makeText(getApplicationContext(), "No conversation to save!", Toast.LENGTH_LONG).show();
    	}
    }
    
}
