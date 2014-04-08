/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.BluetoothChat;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * This is the main Activity that displays the current chat session.
 */
public class BluetoothChat extends Activity {
    // Debugging
    private static final String TAG = "BluetoothChat";
    private static final boolean D = true;

    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    
    public static final int MESSAGE_SOURCE_NAME  = 6;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
    
    public static final String SOURCE_NAME  = "source_name";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    // Layout Views
    private TextView mTitle;
    private ListView mConversationView;
    private EditText mOutEditText;
    private Button mSendButton;

    // Name of the connected device
    private String mConnectedDeviceName = null;
    // Array adapter for the conversation thread
    private ArrayAdapter<String> mConversationArrayAdapter;
    // String buffer for outgoing messages
    private StringBuffer mOutStringBuffer;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the chat services
    private BluetoothChatService mChatService = null;
    
    
    private String mSourceDevice = null;
    
    String Sourceuser ;
    
    String PairedDeviceSourceName = "NOTDEFINIED";
    boolean InternetOn = false; 


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(D) Log.e(TAG, "+++ ON CREATE +++");

        // Set up the window layout
        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        setContentView(R.layout.main);
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.custom_title);

        // Set up the custom title
        mTitle = (TextView) findViewById(R.id.title_left_text);
        mTitle.setText(R.string.app_name);
        mTitle = (TextView) findViewById(R.id.title_right_text);

        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        
        /*
        	StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
            .detectNetwork() // or .detectAll() for all detectable problems
            .penaltyDialog()  //show a dialog
            .permitNetwork() //permit Network access 
            .build());
        	*/
     
        
        /*
        if(networkStatus())
        {
        	System.out.println(" Internet Connection Available");
        	
        	new PingServer().execute();
        }
        */
    }

    @Override
    public void onStart() {
        super.onStart();
        if(D) Log.e(TAG, "++ ON START ++");

        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        // Otherwise, setup the chat session
        } else {
            if (mChatService == null) setupChat();
        }
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        if(D) Log.e(TAG, "+ ON RESUME +");

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
              // Start the Bluetooth chat services
              mChatService.start();
            }
        }
    }

    private void setupChat() {
        Log.d(TAG, "setupChat()");

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
            	
            	
            	/*
                // Send a message using content of the edit text widget
                TextView view = (TextView) findViewById(R.id.edit_text_out);
                String message = view.getText().toString();
                sendMessage(message);                
                */
            	TextView view1 = (TextView) findViewById(R.id.targetUser);
            	String Targetname = view1.getText().toString();
            	
            	TextView view2 = (TextView) findViewById(R.id.edit_text_out);
            	String Message = view2.getText().toString();
            	
                if(Targetname.length() > 0 &&  Message.length() > 0)
                {
                
	            	if(mChatService.getState() == BluetoothChatService.STATE_CONNECTED && Targetname.equals(PairedDeviceSourceName))            
	            	{
	            		Message = Message + ";" + Targetname + ";" + Sourceuser ;
	            		sendMessage(Message);
	            		
	            	}
	            	else if(networkStatus())
	            	{	
	            		System.out.println(" Internet Connection Available"); 	
	                	
	                	StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
	                    .detectNetwork() // or .detectAll() for all detectable problems
	                    .penaltyDialog()  //show a dialog
	                    .permitNetwork() //permit Network access 
	                    .build());
	                	
	                	
	                	//http post request for user table
	                    InputStream is=null;
	                	String line = null;
	                	
	                	String HttpString = "http://www.harshalchaudhari.com/cnproject/sendMessage.php?";
	                	
	                	HttpString += "srcname=" + Sourceuser + "&destname=" + Targetname + "&msg=" + Message;
	                         	
	                	try
	                    {
	                        //System.out.println("2");
	                       
	                        HttpClient httpclient = new DefaultHttpClient();
	                        HttpPost httppost = new      
	                        HttpPost(HttpString);                      
	                        HttpResponse response = httpclient.execute(httppost);
	                        HttpEntity entity = response.getEntity();
	                        is = entity.getContent();
	                        Log.i("postData", response.getStatusLine().toString());
	                        
	                        String display = "From: " + Sourceuser + " To: " + Targetname + "  Message: " + Message ;
	                        mConversationArrayAdapter.add(display);
	                    }
	                    catch(Exception e)
	                    {
	                        Log.e("log_tag", "Error in http connection "+e.toString());
	                    }
	                    
	
	                    //to parse http response into a string
	                    String result = "";
	
	                    try
	                    {
	                        BufferedReader reader = new BufferedReader(new InputStreamReader(is,"iso-8859-1"),8);
	                        StringBuilder sb = new StringBuilder();
	                        while ((line = reader.readLine()) != null) {
	                            sb.append(line + "\n");
	                        }
	                        is.close();
	                        result=sb.toString();
	                    }catch(Exception e){
	                        Log.e("log_tag", "Error in http connection" +e.toString());
	                    }
	                    //System.out.println("4");
	                    //to parse jason object and insert in user table
	                    try
	                    {	 if(result!=null)
	                      {
	                    	
	                    	JSONArray jArray = new JSONArray(result);
	                    	for(int i=0;i<jArray.length();i++)
	                        {
	                        	
	                        JSONObject json_data = jArray.getJSONObject(i);
	                  
	                        
	                        }
	                      }
	                        }catch(JSONException e){
	                        Log.e("log_tag", "Error parsing data" +e.toString());
	                    }
	            		
	            	}
	            	else if (mChatService.getState() == BluetoothChatService.STATE_CONNECTED)
	            	{
	            		Message = Message + ";" + Targetname + ";" + Sourceuser ;
	            		sendMessage(Message);
	            	}
            	
                }
                else
                	return ;
                       	
            }
        });

        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothChatService(this, mHandler);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        if(D) Log.e(TAG, "- ON PAUSE -");
    }

    @Override
    public void onStop() {
        super.onStop();
        if(D) Log.e(TAG, "-- ON STOP --");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop the Bluetooth chat services
        if (mChatService != null) mChatService.stop();
        if(D) Log.e(TAG, "--- ON DESTROY ---");
    }

    private void ensureDiscoverable() {
        if(D) Log.d(TAG, "ensure discoverable");
        if (mBluetoothAdapter.getScanMode() !=
            BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    /**
     * Sends a message.
     * @param message  A string of text to send.
     */
    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        /*
        // Check that there's actually something to send
        if (message.length() > 0) {
        	
            TextView view = (TextView) findViewById(R.id.targetUser);
            String Targetuser = view.getText().toString();
            
            TextView view2 = (TextView) findViewById(R.id.sourceUser);
            String Sourceuser = view2.getText().toString();
            
            if(Targetuser.length() > 0 &&  Sourceuser.length() > 0)
            {
            	message = message + ";" + Targetuser + ";" + Sourceuser;
            }
            else
            {
            	Toast.makeText(this, "Please Specify Target username and Source UserName ", Toast.LENGTH_SHORT).show();
                return;
            }
            	
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mChatService.write(send);

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
            mOutEditText.setText(mOutStringBuffer);
        }
        
        */
        
        if (message.length() > 0) 
        {
	        // Get the message bytes and tell the BluetoothChatService to write
	        byte[] send = message.getBytes();
	        mChatService.write(send);
	
	        // Reset out string buffer to zero and clear the edit text field
	        mOutStringBuffer.setLength(0);
	        mOutEditText.setText(mOutStringBuffer);
        }
    }
    
    
    
    
    
    
    public void setSourceButtonClick(View view)
    {
        System.out.println("SetSource Call received");
    	
    	TextView view5 = (TextView) findViewById(R.id.sourceUser);
        Sourceuser = view5.getText().toString();
        
        Button btn = (Button) findViewById(R.id.setSourceButton);
        
        if(Sourceuser.length() > 0)
        {
        	view5.setEnabled(false);
        	btn.setEnabled(false);
        	
        	TextView view1 = (TextView) findViewById(R.id.targetUser);
        	view1.setEnabled(true);
        	
        	TextView view2 = (TextView) findViewById(R.id.edit_text_out);
        	view2.setEnabled(true);
        	
        	Button btn2 = (Button) findViewById(R.id.button_send);
        	btn2.setEnabled(true);
        	
        	
      /*  	
            if(networkStatus())
            {
            	System.out.println(" Internet Connection Available"); 	
            	
            	StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectNetwork() // or .detectAll() for all detectable problems
                .penaltyDialog()  //show a dialog
                .permitNetwork() //permit Network access 
                .build());
            	
            	
            	//http post request for user table
                InputStream is=null;
            	String line = null;
            	try
                {
                    //System.out.println("2");
                   
                    HttpClient httpclient = new DefaultHttpClient();
                    HttpPost httppost = new      
                    HttpPost("harshalchaudhari.com/cnproject/sendMessage.php?srcname=harshal21&destname=saurabh2&msg=hello1123");                      
                    HttpResponse response = httpclient.execute(httppost);
                    HttpEntity entity = response.getEntity();
                    is = entity.getContent();
                    Log.i("postData", response.getStatusLine().toString());
                }
                catch(Exception e)
                {
                    Log.e("log_tag", "Error in http connection "+e.toString());
                }
                

                //to parse http response into a string
                String result = "";

                try
                {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is,"iso-8859-1"),8);
                    StringBuilder sb = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        sb.append(line + "\n");
                    }
                    is.close();
                    result=sb.toString();
                }catch(Exception e){
                    Log.e("log_tag", "Error in http connection" +e.toString());
                }
                System.out.println("4");
                //to parse jason object and insert in user table
                try
                {	 if(result!=null)
                {
                	
                	JSONArray jArray = new JSONArray(result);
                	for(int i=0;i<jArray.length();i++)
                    {
                    	
                    JSONObject json_data = jArray.getJSONObject(i);
              
                    
                    }
                }
                    }catch(JSONException e){
                    Log.e("log_tag", "Error parsing data" +e.toString());
                }
            	
            	
            	
         	
            	new PingServer().execute();
            }
            
            */
        	
        	
        	if(mChatService.getState() == BluetoothChatService.STATE_CONNECTED)
        	{
        		TextView view110 = (TextView) findViewById(R.id.sourceUser);
        		String message = view110.getText().toString();
        		message = "****SOURCENAME****" + ":"+ message;
        		sendMessage(message);
        	}
        	
        	
            if(networkStatus())
            {     
            	  Thread thread1 = new Thread()
            	  {
					public void run() {

						//android.os.Debug.waitForDebugger();
						for (;;) 
						{
							try {
								StrictMode
										.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
												.detectNetwork() // or
																	// .detectAll()
																	// for all
																	// detectable
																	// problems
												.penaltyDialog() // show a
																	// dialog
												.permitNetwork() // permit
																	// Network
																	// access
												.build());

								// http post request for user table
								InputStream is = null;
								String line = null;

								String HttpString = "http://www.harshalchaudhari.com/cnproject/checkMessages.php?";

								try {
									if (PairedDeviceSourceName
											.equals("NOTDEFINED")) {

									}

								} catch (Exception e) {
									System.out.println(e.toString());
								}
								HttpString += "destname=" + Sourceuser
										+ "&nname=" + PairedDeviceSourceName;
								try {
									// System.out.println("2");

									HttpClient httpclient = new DefaultHttpClient();
									HttpPost httppost = new HttpPost(HttpString);
									HttpResponse response = httpclient
											.execute(httppost);
									HttpEntity entity = response.getEntity();
									is = entity.getContent();
									Log.i("postData", response.getStatusLine()
											.toString());
								} catch (Exception e) {
									Log.e("log_tag",
											"Error in http connection "
													+ e.toString());
								}

								// to parse http response into a string
								String result = "";

								try {
									BufferedReader reader = new BufferedReader(
											new InputStreamReader(is,
													"iso-8859-1"), 8);
									StringBuilder sb = new StringBuilder();
									while ((line = reader.readLine()) != null) {
										sb.append(line + "\n");
									}
									is.close();
									result = sb.toString();
								} catch (Exception e) {
									Log.e("log_tag", "Error in http connection"
											+ e.toString());
								}
								System.out.println("4  ---- Inside Thread ");
								// to parse jason object and insert in user
								// table
								try {
									if (result != null) {

										JSONArray jArray = new JSONArray(result);

										String source;
										String destination;
										String msg;

										for (int i = 0; i < jArray.length(); i++) {

											JSONObject json_data = jArray
													.getJSONObject(i);

											source = json_data
													.getString("source_name");
											destination = json_data
													.getString("destination_name");
											msg = json_data.getString("msg");
											
											String 	Message = msg + ";" + destination + ";" + source ;
											
											if(destination.equals(Sourceuser))
											{

												
											    if (msg.length() > 0) 
										        {
											
											        
										    	        // Get the message bytes and tell the BluetoothChatService to write
										    	        byte[] send = Message.getBytes();
										    	        mHandler.obtainMessage(BluetoothChat.MESSAGE_WRITE, -1, -1, send)
								                        .sendToTarget();									    	        			    	        
										            
										        }
												
												
											}
						
											if(destination.equals(PairedDeviceSourceName))
											{
									      	    //String 	Message = msg + ";" + destination + ";" + source ;
								    	        byte[] send = Message.getBytes();
								    	        mHandler.obtainMessage(BluetoothChat.MESSAGE_WRITE, -1, -1, send)
						                        .sendToTarget();	
									      	    sendMessage(Message);   
											}

										}

									}
								} catch (JSONException e) {
									Log.e("log_tag",
											"Error parsing data" + e.toString());
								}

								sleep(5000);

							} catch (Exception e) {
								e.printStackTrace();

							}
						}
					}
				};
            	    
           
				thread1.start();
				
				
            	
            	
            	
            	
            }

        }
        else 
        	Toast.makeText(this, "Please enter Sourcename ", Toast.LENGTH_SHORT).show();
        
        

        	
        	return;      
    }
    
    public boolean networkStatus() {
    	
        boolean haveConnectedWifi = false;
        boolean haveConnectedMobile = false;

        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo[] netInfo = cm.getAllNetworkInfo();
        for (NetworkInfo ni : netInfo) {
            if (ni.getTypeName().equalsIgnoreCase("WIFI"))
                if (ni.isConnected())
                    haveConnectedWifi = true;
            if (ni.getTypeName().equalsIgnoreCase("MOBILE"))
                if (ni.isConnected())
                    haveConnectedMobile = true;
        }
        return haveConnectedWifi || haveConnectedMobile;
    	
    	
    	
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
            if(D) Log.i(TAG, "END onEditorAction");
            return true;
        }
    };

    // The Handler that gets information back from the BluetoothChatService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_STATE_CHANGE:
                if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                switch (msg.arg1) {
                case BluetoothChatService.STATE_CONNECTED:
                    mTitle.setText(R.string.title_connected_to);
                    mTitle.append(mConnectedDeviceName);
                    mConversationArrayAdapter.clear();
                    break;
                case BluetoothChatService.STATE_CONNECTING:
                    mTitle.setText(R.string.title_connecting);
                    break;
                case BluetoothChatService.STATE_LISTEN:
                case BluetoothChatService.STATE_NONE:
                    mTitle.setText(R.string.title_not_connected);
                    break;
                }
                break;
            case MESSAGE_WRITE:
                byte[] writeBuf = (byte[]) msg.obj;
                // construct a string from the buffer
                String writeMessage = new String(writeBuf);
                
                boolean var1 = writeMessage.contains("****SOURCENAME****");
                System.out.println("var value = " + var1);
                if (!var1)
                {
                	
                    if (writeMessage.contains(";"))
                    {
                    	String split[] = writeMessage.split(";");
                    	String msgtext = split[0];
                    	String dest = split[1];
                    	String src = split[2];
                    	
                    	String display = "From: " + src + " To:" + dest + "  Message:" + msgtext;
                    	mConversationArrayAdapter.add(display);
                    }
                	//mConversationArrayAdapter.add("Me:  " + writeMessage);
                }
                break;
            case MESSAGE_READ:
                byte[] readBuf = (byte[]) msg.obj;
                // construct a string from the valid bytes in the buffer
                String readMessage = new String(readBuf, 0, msg.arg1);
                
                System.out.println("Inside Message Read");
                boolean var = readMessage.contains("****SOURCENAME****");
                System.out.println("var value = " + var);
                if(var)
                {
                	String [] split = readMessage.split(":");
                	PairedDeviceSourceName = split[1];
                	System.out.println(PairedDeviceSourceName);
                	
                    Toast.makeText(getApplicationContext(), "Paired Device Source Name: "
                            + PairedDeviceSourceName, Toast.LENGTH_SHORT).show();
                	
                }
                else
                {
                	String [] split = readMessage.split(";");
                	String message = split[0];
                	String target = split[1];
                	String source = split[2];
                         	
                	if(Sourceuser.equals(target))
                	{
                		mConversationArrayAdapter.add(source+":  " +split[0] );
                	}
                	
                	else if (source.equals(PairedDeviceSourceName) && networkStatus())
                	{
                		// Send to internet 
                		
                		StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
	                    .detectNetwork() // or .detectAll() for all detectable problems
	                    .penaltyDialog()  //show a dialog
	                    .permitNetwork() //permit Network access 
	                    .build());
	                	
	                	
	                	//http post request for user table
	                    InputStream is=null;
	                	String line = null;
	                	
	                	String HttpString = "http://www.harshalchaudhari.com/cnproject/sendMessage.php?";
	                	
	                	HttpString += "srcname=" + source + "&destname=" + target + "&msg=" + message;
	                         	
	                	try
	                    {
	                        //System.out.println("2");
	                       
	                        HttpClient httpclient = new DefaultHttpClient();
	                        HttpPost httppost = new      
	                        HttpPost(HttpString);                      
	                        HttpResponse response = httpclient.execute(httppost);
	                        HttpEntity entity = response.getEntity();
	                        is = entity.getContent();
	                        Log.i("postData", response.getStatusLine().toString());
	                    }
	                    catch(Exception e)
	                    {
	                        Log.e("log_tag", "Error in http connection "+e.toString());
	                    }
	                    
	
	                    //to parse http response into a string
	                    String result = "";
	
	                    try
	                    {
	                        BufferedReader reader = new BufferedReader(new InputStreamReader(is,"iso-8859-1"),8);
	                        StringBuilder sb = new StringBuilder();
	                        while ((line = reader.readLine()) != null) {
	                            sb.append(line + "\n");
	                        }
	                        is.close();
	                        result=sb.toString();
	                    }catch(Exception e){
	                        Log.e("log_tag", "Error in http connection" +e.toString());
	                    }
	                    //System.out.println("4");
	                    //to parse jason object and insert in user table
	                    try
	                    {	 if(result!=null)
	                      {
	                    	
	                    	JSONArray jArray = new JSONArray(result);
	                    	for(int i=0;i<jArray.length();i++)
	                        {
	                        	
	                        JSONObject json_data = jArray.getJSONObject(i);
	                  
	                        
	                        }
	                      }
	                        }catch(JSONException e){
	                        Log.e("log_tag", "Error parsing data" +e.toString());
	                    }
                		
                	}
                	else
                        Toast.makeText(getApplicationContext(), "Message has died "
                                , Toast.LENGTH_SHORT).show();
                }
                break;
            case MESSAGE_DEVICE_NAME:
                // save the connected device's name
                mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                Toast.makeText(getApplicationContext(), "Connected to "
                               + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                break;
                
            case MESSAGE_TOAST:
                Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                               Toast.LENGTH_SHORT).show();
                break;
            /*    
            case MESSAGE_SOURCE_NAME: 
            	
            	mSourceDevice = msg.getData().getString(SOURCE_NAME);
                Toast.makeText(getApplicationContext(), "source "
                        + mSourceDevice, Toast.LENGTH_SHORT).show();
                
                break;
            	*/
            }
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
        case REQUEST_CONNECT_DEVICE:
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                // Get the device MAC address
                String address = data.getExtras()
                                     .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                // Get the BLuetoothDevice object
                BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                // Attempt to connect to the device
                mChatService.connect(device);
            }
            break;
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled, so set up a chat session
                setupChat();
            } else {
                // User did not enable Bluetooth or an error occured
                Log.d(TAG, "BT not enabled");
                Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.scan:
            // Launch the DeviceListActivity to see devices and do scan
            Intent serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
            return true;
        case R.id.discoverable:
            // Ensure this device is discoverable by others
            ensureDiscoverable();
            return true;
        }
        return false;
    }
}

class PingServer extends AsyncTask<String , Void, Void> {
	   
	@Override 
	protected Void doInBackground(String... names) 
	{
        //int count = 1000000;
		
		http://harshalchaudhari.com/cnproject/checkMessages.php?destname=saurabh&nname=saurabh2
		
		
        for (; ;) 
        {
        	System.out.println("async");
        	SystemClock.sleep(5000); 
        	
        	
        	StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
            .detectNetwork() // or .detectAll() for all detectable problems
            .penaltyDialog()  //show a dialog
            .permitNetwork() //permit Network access 
            .build());
        	
        	
        	//http post request for user table
            InputStream is=null;
        	String line = null;
        	
        	String HttpString = "http://www.harshalchaudhari.com/cnproject/checkMessages.php?";
        	
        	try
        	{
        		if(names[1].contains("NOTDEFINED"))		
        	{
        		names[1] = names[0];
        		
        	}
        	
        }
	catch(Exception e)
	{
		System.out.println(e.toString());
	}
        	HttpString += "destname=" + names[0]  + "&nname=" + names[1] ; 	
        	try
            {
                //System.out.println("2");
               
                HttpClient httpclient = new DefaultHttpClient();
                HttpPost httppost = new      
                HttpPost(HttpString);                      
                HttpResponse response = httpclient.execute(httppost);
                HttpEntity entity = response.getEntity();
                is = entity.getContent();
                Log.i("postData", response.getStatusLine().toString());
            }
            catch(Exception e)
            {
                Log.e("log_tag", "Error in http connection "+e.toString());
            }
            

            //to parse http response into a string
            String result = "";

            try
            {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is,"iso-8859-1"),8);
                StringBuilder sb = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    sb.append(line + "\n");
                }
                is.close();
                result=sb.toString();
            }catch(Exception e){
                Log.e("log_tag", "Error in http connection" +e.toString());
            }
            //System.out.println("4");
            //to parse jason object and insert in user table
            try
            {	 if(result!=null)
              {
            	
            	JSONArray jArray = new JSONArray(result);
            	
            	String source;
            	String destination;
            	String msg;
            	
            	
            	for(int i=0;i<jArray.length();i++)
                {
                	
                JSONObject json_data = jArray.getJSONObject(i);
                
                source = json_data.getString("source_name");
                destination =json_data.getString("destination_name");
                msg = json_data.getString("msg");
                

          
                
                }
              }
                }catch(JSONException e){
                Log.e("log_tag", "Error parsing data" +e.toString());
            }
        	
        }
    }

	@Override 
    protected void onProgressUpdate(Void... params) {
        //setProgressPercent(progress[0]);
    }

	@Override 
    protected void onPostExecute(Void result) {
        //showDialog("Downloaded " + result + " bytes");
    }
}



