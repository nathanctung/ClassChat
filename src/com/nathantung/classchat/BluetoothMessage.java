package com.nathantung.classchat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.Calendar;

import org.apache.commons.io.output.ByteArrayOutputStream;

import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
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
	public static BluetoothAdapter myAdapter = null;
	public static ActionBar actionBar;
	
    // Layout Views
    private ListView mConversationView;
    private EditText mOutEditText;
    private Button mSendButton;
    private ArrayAdapter<String> mConversationArrayAdapter;

    // MainActivity Variables
	private StringBuffer mOutStringBuffer;
	
	private boolean sendingData;
	private boolean expectingData;
	private String receivedExt;
	ByteArrayOutputStream outputStream;
	public static final String HEADER_START = "{[<CLCH1>]}";
	public static final String HEADER_END = "{[<CLCH2>]}";
	public static final String RECOMMENDED_FRIEND_REQUEST = "F_RQ";
	public static final String RECOMMENDED_FRIEND_RESPONSE = "F_RS";
	
	public int dataPackets;
	
	// Activity States
    public static final int IMAGE_PATH_BROWSE = 5;       // we're doing nothing
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_message);
		
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		
		actionBar = getActionBar();
		
		// reset variables
		sendingData = false;
		expectingData = false;
		receivedExt = "";
		outputStream = null;
		dataPackets = 0;
		
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
			  String source = currIntent.getExtras().getString("source");
			  byte[] bytes = currIntent.getExtras().getByteArray("buf");
			  
			  // write message
			  String writeMessage = new String(bytes);
			  
			  // read message:
			  int size = currIntent.getExtras().getInt("size");
			  String message = new String(bytes, 0, size);
			  
			  // if a message was generated...
			  if(source!=null && (message!=null || writeMessage!=null)) {
				  
				  // writing own messages to array adapter
				  if(source.equals("Me")) {
					  
					  if(writeMessage.contains(HEADER_START)) {
						  sendingData = true;
					  }
					  else if(writeMessage.contains(HEADER_END)) {
						  sendingData = false;
						  
						  if(writeMessage.contains("IMAGE")) {
							  mConversationArrayAdapter.add("*Your image was sent! It may take some time to be transferred.");
						  }
					  }
					  else if(sendingData) {
						  // do nothing; you're automatically transmitting some data or file
					  }
					  else {
						// normal message
						  mConversationArrayAdapter.add(source + ": " + writeMessage);
					  }
				  }
				  // reading received messages to array adapter
				  else {
					  
					  if(!expectingData && message.contains(HEADER_START)) { //received wrapper beginning, no data expected yet
						  
						  Log.d("HEADER", message);
						  
						  expectingData = true;
						  outputStream = new ByteArrayOutputStream();
						  
					  }
					  else if(expectingData && message.contains(HEADER_END)) { //received wrapper ending, closing expected data
						  
						  Log.d("FOOTER", message);
						  Log.d("DATAPACKETS", ""+dataPackets);
						  dataPackets = 0;
						  
						  if(message.contains("IMAGE")) {

							  // set extension of picture file
							  receivedExt = message.substring(message.indexOf("IMAGE=")+"IMAGE=".length(), message.indexOf(HEADER_END));
							  
							  Log.d("outputStreamSize", outputStream.size() + " bytes / " + outputStream.size()/1024 + " kbytes");
							  
							  decodeImageToFile(outputStream.toString());
							  //saveImageToFile(outputStream.toByteArray(), receivedExt);
							  
							  mConversationArrayAdapter.add("*You received a(n) " + receivedExt + " image!");
							  mConversationArrayAdapter.add(outputStream.toString());
							  
							  
						  }
						  else if(message.contains(RECOMMENDED_FRIEND_REQUEST)) {
							  respondFriendRecommendations();
						  }
						  else if(message.contains(RECOMMENDED_FRIEND_RESPONSE)) {
							  
							  String contacts = outputStream.toString();
							  Log.d("CONTACTS", contacts);
							  handleFriendRecommendations(contacts);
						  }
						  else {
							  // respond to other header types
						  }
						  
						// reset variables
						  expectingData = false;
						  
						  if(outputStream!=null) {
							  try {
								  outputStream.close();
								  Log.d("HEADER_END", "closed outputstream");
							  } catch (IOException e) {}
						  }
						  
					  }
					  else if(expectingData) { //in the middle of the wrapper, expecting data and adding to outputStream
						  Log.d("DATA", new String(bytes));
						  
						  dataPackets++;
						  outputStream.write(bytes, 0, size);

					  }
					  else if(message.contains(HEADER_START) || message.contains(HEADER_END)) {
						  // do nothing (get rid of re-reading excess HEADER_END messages)
					  }
					  else {
						  // normal message
						  mConversationArrayAdapter.add(source + ": " + message);
					  }
				  }
			  }
			  else {
				  // do nothing (no message)
			  }
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
    
    private void sendMessage(byte[] bytes) {

    	// Check that we're actually connected before trying anything

    	if (myConnection.getState() != BluetoothConnection.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (bytes!=null) {
            // Get the message bytes and tell the BluetoothChatService to write
            myConnection.write(bytes);

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
            
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
			myConnection.endConnection();
		}
		
	}
    
    public void saveToFile() {

		File path = new File(Environment.getExternalStorageDirectory(), "ClassChat"); // /storage/emulated/0/ClassChat
		path.mkdirs();

		String fileName = "convo-" + filenameNow() + ".txt";
		
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
    
    public void decodeImageToFile(String data) {
    	byte[] decodedString = Base64.decode(data, Base64.DEFAULT);
    	Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
    	
		File path = new File(Environment.getExternalStorageDirectory(), "ClassChat"); // /storage/emulated/0/ClassChat
		path.mkdirs();
		String fileName = "image-"+ filenameNow() + ".png";
    	
		Log.d("decodedbyte", "decoded");
		
		FileOutputStream out = null;
		try {
		    out = new FileOutputStream(fileName);
		    decodedByte.compress(Bitmap.CompressFormat.PNG, 100, out);
		    
		    Log.d("decodedbyte", "compressed into png");    
		    
		} catch (Exception e) {
		    Toast.makeText(getApplicationContext(), "Bitmap could not be saved!", Toast.LENGTH_LONG).show();
		} finally {
		    try {
		        if (out != null) {
		            out.close();
		        }
		    } catch (IOException e) {
			    Toast.makeText(getApplicationContext(), "Bitmap could not be saved!", Toast.LENGTH_LONG).show();
		    }
		}
    }
    
    public void saveImageToFile(byte[] data, String ext) {

		File path = new File(Environment.getExternalStorageDirectory(), "ClassChat"); // /storage/emulated/0/ClassChat
		path.mkdirs();

		String fileName = "image-"+ filenameNow() + "."+ext;
		
		File file = new File(path, fileName);
		
		if(!file.exists()) {
			try {
				file.createNewFile();
			} catch (IOException e) {
				Toast.makeText(getApplicationContext(), "File cannot be created!", Toast.LENGTH_LONG).show();
			}
		}
		
		FileOutputStream fos;
		
		try {
			fos = new FileOutputStream(file.getPath());
			fos.write(data);
			fos.close();
			
		} catch (FileNotFoundException e1) {
			Toast.makeText(getApplicationContext(), "Could not write to file!", Toast.LENGTH_LONG).show();
		}
		catch (IOException e2) {
			Toast.makeText(getApplicationContext(), "Could not write to file!", Toast.LENGTH_LONG).show();
		}
		
		Toast.makeText(getApplicationContext(), "Saved as " + fileName, Toast.LENGTH_LONG).show();
    	
    }
    
    public void selectFileToTransfer() {
    	Intent intent = new Intent();
    	intent.setType("image/*");
    	intent.setAction(Intent.ACTION_GET_CONTENT);
    	startActivityForResult(Intent.createChooser(intent, "Select picture to send!"), IMAGE_PATH_BROWSE);
    }
    
    public void transferFile(String path) {
    	if (myConnection.getState() != BluetoothConnection.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

    	File file = new File(path);
    	byte[] data;

		try {
			data = org.apache.commons.io.FileUtils.readFileToByteArray(file);
	    	
			String ext = path.substring(path.lastIndexOf(".")+1);
			String headerStart = HEADER_START;
			String headerEnd = "IMAGE=" + ext + HEADER_END;
			
	        // Get the message bytes and tell the BluetoothChatService to write
			sendMessage(headerStart);

			Bitmap bm = BitmapFactory.decodeFile(path);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			bm.compress(Bitmap.CompressFormat.PNG, 100, baos);
			byte[] byteImage = baos.toByteArray();
			String encodedImage = Base64.encodeToString(byteImage, Base64.DEFAULT);

			sendMessage(encodedImage); //sendMessage(data);

			Log.d("encodedImage", encodedImage);
			
			sendMessage(headerEnd);
			
		} catch (IOException e) {
			Toast.makeText(this, "Cannot send picture!", Toast.LENGTH_LONG).show();
		}
    }
    
    public void transferFileWithOptions(String path) {
    	
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
    					// or "_data" instead of "MediaStore.Images.Media.DATA"
    					int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
    					Uri filePathUri = Uri.parse(cursor.getString(column_index));
    					String file_name = filePathUri.getLastPathSegment().toString();
    					String file_path=filePathUri.getPath();
    					transferFileWithOptions(file_path);
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
    		Toast.makeText(this, "Select picture to send!", Toast.LENGTH_SHORT).show();
    		selectFileToTransfer();
    		break;
    	case R.id.action_friends:
    		requestFriendRecommendations();
    		break;
    	default:
    		break;
    	}
    	
    	return true;
    }
    
    public String filenameNow() {
    	Calendar c = Calendar.getInstance();
		return c.get(Calendar.MONTH)+"-"+c.get(Calendar.DATE)+"-"+c.get(Calendar.YEAR)+"-"+c.get(Calendar.HOUR)+c.get(Calendar.MINUTE)+c.get(Calendar.SECOND);
    }
    
    public void requestFriendRecommendations() {
    	
		Toast.makeText(this, "Requesting friend recommendations!", Toast.LENGTH_SHORT).show();

		String headerStart = HEADER_START;
		String headerEnd = RECOMMENDED_FRIEND_REQUEST + HEADER_END;
		
		sendMessage(headerStart);
		sendMessage(headerEnd);
    }
    
    public void respondFriendRecommendations() {
    	
		Toast.makeText(this, "Responding to friend recommendation request!", Toast.LENGTH_SHORT).show();
    	
    	File path = new File(Environment.getExternalStorageDirectory(), "ClassChat"); // /storage/emulated/0/ClassChat
		path.mkdirs();

		String fileName = "recommendations.txt";
		
		File file = new File(path, fileName);
    	
		String headerStart = HEADER_START;
		String headerEnd = RECOMMENDED_FRIEND_RESPONSE + HEADER_END;
		
		String contacts = "";
		
		if(file.exists()) {
			InputStream is = null;
			try {
				is = new FileInputStream(file.getPath());
			} catch (FileNotFoundException e2) {
				Toast.makeText(getApplicationContext(), "Can't find recommendations.txt", Toast.LENGTH_SHORT).show();

			}
			BufferedReader buf = new BufferedReader(new InputStreamReader(is));
			
			String line = "";
			
			try {
				while((line=buf.readLine())!=null) {
					contacts+=(line+"\n");
				}
			} catch (IOException e) {
			}
		}
		else {
			// no friend recommendations!
		}
		
		// fetch paired devices and place them into recommended contacts as well
		for(BluetoothDevice d: myAdapter.getBondedDevices()) {
			contacts+=(d.getName()+"\n");
			contacts+=(d.getAddress()+"\n");
		}		
		
		Toast.makeText(this, "Providing contacts: " + contacts, Toast.LENGTH_SHORT).show();
		
		sendMessage(headerStart);
		sendMessage(contacts);
		sendMessage(headerEnd);
		
    }
    
    public void handleFriendRecommendations(String contacts) {
    	
		Toast.makeText(this, "Handling friend recommendations: " + contacts, Toast.LENGTH_SHORT).show();
    	
    	if(contacts.equals("")) {
    		// no contacts to share!
    		Toast.makeText(getApplicationContext(), "No recommendations found!", Toast.LENGTH_SHORT);
    		return;
    	}
	
    	File path = new File(Environment.getExternalStorageDirectory(), "ClassChat"); // /storage/emulated/0/ClassChat
		path.mkdirs();
		String fileName = "recommendations.txt";
		File file = new File(path, fileName);
    		
		if(!file.exists()) {
			try {
				file.createNewFile();
	    		Toast.makeText(this, "Created recommendations.txt!", Toast.LENGTH_SHORT).show();

			} catch (IOException e) {
				Toast.makeText(getApplicationContext(), "File cannot be created!", Toast.LENGTH_LONG).show();
			}
		}
		
		try {
			BufferedWriter bufWriter = new BufferedWriter(new FileWriter(file, true));
			
			BufferedReader bufReader = new BufferedReader(new StringReader(contacts));

			String line = "";
			try {
				while((line=bufReader.readLine())!=null) {
					
					// write each line to our own recommendations.txt
					bufWriter.write(line, 0, line.length());
					bufWriter.newLine();
					bufWriter.flush();
				}
			} catch (IOException e) {
			}
			
			bufWriter.close();
			
		} catch (IOException e) {
			Toast.makeText(getApplicationContext(), "Could not write to file!", Toast.LENGTH_LONG).show();
		}
    }
    
}