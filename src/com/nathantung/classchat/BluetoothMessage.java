package com.nathantung.classchat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
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

	// Activity States
    public static final int IMAGE_PATH_BROWSE = 5;       // we're doing nothing
	
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
    
    /*
     * 
     * new File(getFilesDir(), "ClassChat"); // /data/data/com.nathantung.classchat/files/ClassChat
     * getApplicationContext().getDir("Test", Context.MODE_PRIVATE); // /data /data/data/com.nathantung.classchat/app_Test
     * Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS); // /storage/emulated/0/Download 
     * Environment.getExternalStorageDirectory(); // /storage/emulated/0
     * Environment.getDataDirectory(); // /data
     * 
     */
    
    public void saveToFile() {

		File path = new File(Environment.getExternalStorageDirectory(), "ClassChat"); // /storage/emulated/0/ClassChat
		path.mkdirs();

		Calendar c = Calendar.getInstance();
		String fileName = "convo-"+c.get(Calendar.MONTH)+"-"+c.get(Calendar.DATE)+"-"+c.get(Calendar.YEAR)+"-"+c.get(Calendar.HOUR)+c.get(Calendar.MINUTE)+c.get(Calendar.SECOND)+".txt";
		
		File file = new File(path, fileName);
    	
    	if(mConversationArrayAdapter!=null) {
    		
    		if(!file.exists()) {
    			try {
    				file.createNewFile();
    			} catch (IOException e) {
					Toast.makeText(getApplicationContext(), "File cannot be created!", Toast.LENGTH_LONG).show();
    			}
    		}
    		
    		try {
				BufferedWriter buf = new BufferedWriter(new FileWriter(file));
				
				// write every line in array adapter to file
				for(int i=0; i< mConversationArrayAdapter.getCount(); i++) {
	        		String line = mConversationArrayAdapter.getItem(i);
	        		buf.write(line, 0, line.length());
	        		buf.newLine();
	        		buf.flush();
	        	}
				
				buf.close();
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				Toast.makeText(getApplicationContext(), "Could not write to file!", Toast.LENGTH_LONG).show();
			}
    		
    		Toast.makeText(getApplicationContext(), "Saved as " + fileName, Toast.LENGTH_LONG).show();
    		
    	}
    	else {
    		Toast.makeText(getApplicationContext(), "No conversation to save!", Toast.LENGTH_LONG).show();
    	}
    	
    }
    
    public void selectFileToTransfer() {
    	Intent intent = new Intent();
    	intent.setType("image/*");
    	intent.setAction(Intent.ACTION_GET_CONTENT);
    	startActivityForResult(Intent.createChooser(intent, "Select Picture"), IMAGE_PATH_BROWSE);
    }
    
    public void transferFile(String path) {
    	
		File file = new File(path);
    	
		// sends specified (image) file using default applications
    	Intent intent = new Intent();
    	intent.setAction(Intent.ACTION_SEND);
    	intent.setType("*/*");
    	intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
    	startActivity(intent);
		
    }
    
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
    	if(requestCode == IMAGE_PATH_BROWSE) {
    		if(resultCode == RESULT_OK) {
    			Uri uri = intent.getData();
    			if (uri.getScheme().toString().compareTo("content")==0) {      
    				Cursor cursor =getContentResolver().query(uri, null, null, null, null);
    				if (cursor.moveToFirst()) {
    					int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA); // or "_data" instead of "MediaStore.Images.Media.DATA"
    					Uri filePathUri = Uri.parse(cursor.getString(column_index));
    					String file_name = filePathUri.getLastPathSegment().toString();
    					String file_path=filePathUri.getPath();
    					transferFile(file_path);
    				}
    			}
    		}
    	}
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.message, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch (item.getItemId()) {
    	case R.id.action_save:
    		//Toast.makeText(this, "Saving conversation!", Toast.LENGTH_SHORT).show();
    		saveToFile();
    		break;
    	case R.id.action_camera:
    		Toast.makeText(this, "Choose picture to send!", Toast.LENGTH_SHORT).show();
    		selectFileToTransfer();
    		break;
    	default:
    		break;
    	}
    	
    	return true;
    } 
    
}
