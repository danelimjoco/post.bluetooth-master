        package org.post.aguaclara.post_bt_turbidimeter_widget;
        import android.app.Activity;
        import android.bluetooth.BluetoothAdapter;
        import android.bluetooth.BluetoothDevice;
        import android.bluetooth.BluetoothSocket;
        import android.content.Intent;
        import android.os.Bundle;
        import android.os.Handler;
        import android.view.View;
        import android.widget.Button;
        import android.widget.EditText;
        import android.widget.TextView;

//import org.json.simple.JSONArray;
//import org.json.simple.JSONObject;
        import org.json.JSONArray;
        import org.json.JSONObject;


        import java.io.IOException;
        import java.io.InputStream;
        import java.io.OutputStream;
        import java.util.Set;
        import java.util.UUID;
        import java.util.regex.Matcher;
        import java.util.regex.Pattern;
        import javax.json.Json;
        import java.lang.String;
        import java.lang.Object;

        import com.google.gson.Gson;


public class MainActivity extends Activity {
    TextView myLabel;
    EditText myTextbox;
    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    Thread workerThread;
    String allOutput = "";
    byte[] readBuffer;
    int readBufferPosition;
    int counter;
    volatile boolean stopWorker;
    Intent incomingIntent;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get the intent that started this activity
        Intent intent = getIntent();
        if (intent.getAction() == "org.post.aguaclara.post_bt_turbidimeter_widget.COLLECT") {
            incomingIntent = intent;
            //       System.out.println("You got intent!");

            //     makeJsonObject();
            Double mAnswer = makeJsonObject();
            //     Double mAnswer = 100.3;
            incomingIntent.putExtra("value", mAnswer);
            setResult(RESULT_OK, incomingIntent);
            //      System.out.println(mAnswer);
            //     System.out.println(intent.getExtras().get("rawWaterTurbidity"));
            finish();
        }


        Button openButton = (Button) findViewById(R.id.open);
        Button sendButton = (Button) findViewById(R.id.send);
        Button closeButton = (Button) findViewById(R.id.close);
        myLabel = (TextView) findViewById(R.id.label);
        myTextbox = (EditText) findViewById(R.id.entry);

        //Open Button
        openButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    findBT();
                    openBT();
                } catch (IOException ex) {
                }
            }
        });

        //Send Button
        sendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    sendData();
                } catch (IOException ex) {
                }
            }
        });

        //Close button
        closeButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    closeBT();
                } catch (IOException ex) {
                }
            }
        });
    }

    void findBT() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            myLabel.setText("No bluetooth adapter available");
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 0);
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                if (device.getName().equals("HC-06")) {
                    mmDevice = device;
                    break;
                }
            }
        }
        myLabel.setText(getResources().getString(R.string.bluetooth_connected));
    }

    void openBT() throws IOException {
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID
        mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
        mmSocket.connect();
        mmOutputStream = mmSocket.getOutputStream();
        mmInputStream = mmSocket.getInputStream();

        beginListenForData();

        myLabel.setText(getResources().getString(R.string.bluetooth_connected));
    }

    void beginListenForData() {
        final Handler handler = new Handler();
        final byte delimiter = 10; //This is the ASCII code for a newline character

        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[2048];
        workerThread = new Thread(new Runnable() {
            public void run() {
                while (!Thread.currentThread().isInterrupted() && !stopWorker) {
                    try {
                        int bytesAvailable = mmInputStream.available();
                        if (bytesAvailable > 0) {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);
                            for (int i = 0; i < bytesAvailable; i++) {
                                byte b = packetBytes[i];
                                if (b == delimiter) {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    allOutput = data + " " + allOutput;
                                    readBufferPosition = 0;
                                } else {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                            handler.post(new Runnable() {
                                public void run() {

                                    if (allOutput.contains("NTU")) {
                                        int ntuIndex = allOutput.indexOf("NTU") - 4;
                                        myLabel.setText(String.valueOf(parseNtu(allOutput)));
                                        Bundle result = new Bundle();
                                        result.putFloat("textFieldInGroup", parseNtu(allOutput));
                                        sendToODK(result);
                                    }
                                }
                            });
                        }
                    } catch (IOException ex) {
                        stopWorker = true;
                    }
                }
            }
        });

        workerThread.start();
    }

    void sendData() throws IOException {
        String msg = myTextbox.getText().toString();
        msg += "\n";
        mmOutputStream.write(msg.getBytes());
        myLabel.setText("Data Sent");
    }

    // puts the values asked for by ODK into the returning intent and passes it off to ODK and shuts
// down the app
    void sendToODK(Bundle extras) {
        Intent outgoingintent = incomingIntent;
        for (String key : incomingIntent.getExtras().keySet()) {
            if (extras.containsKey(key)) {
                outgoingintent.putExtra(key, (String) extras.get(key));
            }
        }
        setResult(RESULT_OK, outgoingintent);

    }

    void closeBT() throws IOException {
        stopWorker = true;
        mmOutputStream.close();
        mmInputStream.close();
        mmSocket.close();
        myLabel.setText("Bluetooth Closed");
    }

    public static float parseNtu(String string) {
        //       String first_match = parseWithRegex(string, "(\\d+)(.)(\\d+)(\\s*)(NTU)");
        String second_match = parseWithRegex(string, "([)(\\d)(.)(\\d)(])");
        return Float.parseFloat(second_match);
    }

    //  Takes a string and a Java Regex pattern string and returns the first match. Returns empty string
// if no match exists
    public static String parseWithRegex(String string, String pattern) {
        // Create a Pattern object
        Pattern r = Pattern.compile(pattern);

        // Now create matcher object.
        Matcher m = r.matcher(string);
        if (m.find()) {
            return m.group(0);
        } else return "";
    }

    public double makeJsonObject() {
        System.out.println("You got here!");

        try {
            JSONObject obj = new JSONObject();


            JSONObject rd = new JSONObject();

            JSONObject tur = new JSONObject();
            JSONObject u = new JSONObject();
            JSONArray t = new JSONArray();
            JSONArray d = new JSONArray();

            JSONArray e = new JSONArray();
            JSONArray ge = new JSONArray();

            rd.put("tur", tur);
            tur.put("u", "ntu");
            tur.put("t", t);
            tur.put("d", d);
            tur.put("e", e);


            t.put(0);
            d.put(1.40);


            obj.put("dt", "hhT");
            obj.put("id", "01010101");
            obj.put("rt", 0);
            obj.put("ts", 0);
            obj.put("rd", rd);
            obj.put("ge", ge);
            obj.put("al", "{}");
            System.out.println(obj);

            System.out.println(obj.getJSONObject("rd").getJSONObject("tur").getJSONArray("d").getDouble(0));
            return obj.getJSONObject("rd").getJSONObject("tur").getJSONArray("d").getDouble(0);
        } catch (org.json.JSONException e) {
            System.out.println(e);
            finish();
        }
        //fix this later
        return 0.0;
    }
}







//System.out.print(obj);

//       String jsonString;
//       jsonString = obj.toJSONString();


//       System.out.println(jsonString);
//       System.out.println("You printed jsonString!");
//       System.out.println(parseNtu(jsonString));

//if we import org.json.JSONObject instead, then we have access to the getDouble method
//such that obj.getDouble("tur") works.









//        if (jsonString.contains("NTU")) {
//            Bundle result = new Bundle();
//            result.putFloat("rawWaterTurbidity", parseNtu(jsonString));
//            sendToODK(result);
//
//
////         Intent intent = new Intent();
////         intent.putExtra("rawWaterTurbidity", 10);
////         setResult(RESULT_OK, intent);
////         finish();
//
//
////         int ntuIndex = jsonString.indexOf("NTU") - 4;
////         myLabel.setText(String.valueOf(parseNtu(jsonString)));
////         Bundle result = new Bundle();
////         result.putFloat("textFieldInGroup", parseNtu(jsonString));
////         sendToODK(result);
//
//            System.out.print("WOO");
//        }

