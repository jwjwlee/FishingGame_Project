package com.jungwonlee.fishinggameproject;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private TextView mConnectionStatus;

    //블루투스
    private final int REQUEST_BLUETOOTH_ENABLE = 100;
    private static final String TAG = "BluetoothClient";
    private String mConnectedDeviceName = null;
    static BluetoothAdapter mBluetoothAdapter;
    static boolean isConnectionError = false;
    ConnectedTask mConnectedTask = null;

    //센서
    private SensorManager mSensorManager;
    private Sensor mAccelometer;


    private ArrayAdapter<String> mConversationArrayAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //센서
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mAccelometer = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        //연결 상태를 보여주는 뷰
        mConnectionStatus = (TextView)findViewById(R.id.connection_status_textview);
        ListView mMessageListview = (ListView) findViewById(R.id.message_listview);

        mConversationArrayAdapter = new ArrayAdapter<>( this, android.R.layout.simple_list_item_1 );
        mMessageListview.setAdapter(mConversationArrayAdapter);



        Log.d(TAG, "Initalizing Bluetooth adapter...");
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();  //블루투스 사용 가능한지 검사
        if(mBluetoothAdapter == null) {
            //showErrorDialog("This device is not implement Bluetooth.");
            return;
        }
        if(!mBluetoothAdapter.isEnabled()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQUEST_BLUETOOTH_ENABLE);
        }
        else {
            Log.d(TAG, "Initialisation successful.");
            //페어링 되어 있는 블루투스 장치들의 목록을 보여줍니다.
            //목록에서 블루투스 장치를 선택하면 선택한 디바이스를 인자로 하여
            showDeviceListDialog();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAccelometer, 9000000);
    }

    @Override
    public void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if ( mConnectedTask != null ) mConnectedTask.cancel(true);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if(event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            double x = event.values[0];
            double y = event.values[1];
            double z = event.values[2];
            Log.e("LOG", "ACCELOMETER           [X]:" + String.format("%.4f", x)
                    + "           [Y]:" + String.format("%.4f", y)
                    + "           [Z]:" + String.format("%.4f", z)
            );

            String speed = Double.toString(y);
            sendMessage(speed);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    //연결
    private class ConnectTask extends AsyncTask<Void, Void, Boolean> {

        private BluetoothSocket mBluetoothSocket = null;
        private BluetoothDevice mBluetoothDevice = null;

        ConnectTask(BluetoothDevice bluetoothDevice) {
            mBluetoothDevice = bluetoothDevice;
            mConnectedDeviceName = bluetoothDevice.getName();

            //SPP
            UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

            //블루투스 디바이스와 연결을 위한 소켓 생성
            try {
                mBluetoothSocket = mBluetoothDevice.createRfcommSocketToServiceRecord(uuid);
                Log.d( TAG, "create socket for "+mConnectedDeviceName);
            } catch (IOException e) {
                Log.e( TAG, "socket create failed " + e.getMessage());
            }
            mConnectionStatus.setText("연결중......");
        }

        @Override
        protected Boolean doInBackground(Void... params) {

            //블루투스 검색 중단
            mBluetoothAdapter.cancelDiscovery();

            //블루투스 소켓으로 연결
            try {
                //Block
                mBluetoothSocket.connect();
            } catch (IOException e) {
                //소켓 close
                try {
                    mBluetoothSocket.close();;
                } catch (IOException e1) {
                    Log.e(TAG, "unable to close() " +
                            " socket during connection failure", e1);
                }
                return false;
            }
            return  true;
        }

        @Override
        protected void onPostExecute(Boolean isSuccess) {
            if(isSuccess) {
                //연결 성공시 작업 수행
                connected(mBluetoothSocket);
            }
            else {
                isConnectionError = true;
                Log.d( TAG,  "연결할 수 없습니다");
                showErrorDialog("연결할 수 없습니다");
            }
        }
    }

    //연결 성공시 작업 수행 함수
    public void connected( BluetoothSocket socket ) {
        mConnectedTask = new ConnectedTask(socket);
        mConnectedTask.execute();
    }

    //////////////////////////////////////////////////////////////////////////////
    //연결시 작업 부분
    private class ConnectedTask extends AsyncTask<Void, String, Boolean> {
        private InputStream mInputStream = null;
        private OutputStream mOutputStream = null;
        private BluetoothSocket mBluetoothSocket = null;

        ConnectedTask(BluetoothSocket socket){

            mBluetoothSocket = socket;
            try {
                mInputStream = mBluetoothSocket.getInputStream();
                mOutputStream = mBluetoothSocket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "socket not created", e );
            }

            Log.d( TAG, mConnectedDeviceName + "에 연결하였습니다");
            mConnectionStatus.setText(mConnectedDeviceName + "에 연결하였습니다");
        }


        @Override
        protected Boolean doInBackground(Void... params) {

            byte [] readBuffer = new byte[1024];
            int readBufferPosition = 0;

            // Keep listening to the InputStream while connected
            while (true) {

                if ( isCancelled() ) return false;

                try {

                    int bytesAvailable = mInputStream.available();

                    if(bytesAvailable > 0) {

                        byte[] packetBytes = new byte[bytesAvailable];
                        // Read from the InputStream
                        mInputStream.read(packetBytes);

                        for(int i=0;i<bytesAvailable;i++) {

                            byte b = packetBytes[i];
                            if(b == '\n')
                            {
                                byte[] encodedBytes = new byte[readBufferPosition];
                                System.arraycopy(readBuffer, 0, encodedBytes, 0,
                                        encodedBytes.length);
                                String recvMessage = new String(encodedBytes, "UTF-8");

                                readBufferPosition = 0;

                                Log.d(TAG, "recv message: " + recvMessage);
                                publishProgress(recvMessage);
                            }
                            else
                            {
                                readBuffer[readBufferPosition++] = b;
                            }
                        }
                    }
                } catch (IOException e) {

                    Log.e(TAG, "disconnected", e);
                    return false;
                }
            }

        }

        @Override
        protected void onProgressUpdate(String... recvMessage) {

            mConversationArrayAdapter.insert(mConnectedDeviceName + ": " + recvMessage[0], 0);
        }

        @Override
        protected void onPostExecute(Boolean isSucess) {
            super.onPostExecute(isSucess);

            if ( !isSucess ) {


                closeSocket();
                Log.d(TAG, "Device connection was lost");
                isConnectionError = true;
                showErrorDialog("Device connection was lost");
            }
        }

        @Override
        protected void onCancelled(Boolean aBoolean) {
            super.onCancelled(aBoolean);

            closeSocket();
        }

        void closeSocket(){

            try {

                mBluetoothSocket.close();
                Log.d(TAG, "close socket()");

            } catch (IOException e2) {

                Log.e(TAG, "unable to close() " +
                        " socket during connection failure", e2);
            }
        }

        void write(String msg){
            msg += "\n";
            try {
                mOutputStream.write(msg.getBytes());
                mOutputStream.flush();
            } catch (IOException e) {
                Log.e(TAG, "Exception during send", e );
            }
        }
    }


    //페어링된 블루투스 기기 리스트를 보여주는 함수
    public void showDeviceListDialog() {
        Set<BluetoothDevice> device = mBluetoothAdapter.getBondedDevices();
        final BluetoothDevice[] DevicesList = device.toArray(new BluetoothDevice[0]);

        //페어링된 기기가 하나도 없을 때
        if(DevicesList.length == 0) {
            showQuitDialog("페어링된 기기가 없습니다.");
            return;
        }

        String[] contents;
        contents = new String[DevicesList.length];
        for(int i = 0; i < DevicesList.length; i++) {
            contents[i] = DevicesList[i].getName();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("연결할 기기를 선택하세요");
        builder.setCancelable(false);
        builder.setItems(contents, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();

                //연결 및 소켓 생성
                ConnectTask task = new ConnectTask(DevicesList[which]);
                task.execute();
            }
        });
        builder.create().show();
    }

    //에러 다이얼로그
    public void showErrorDialog(String message)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Quit");
        builder.setCancelable(false);
        builder.setMessage(message);
        builder.setPositiveButton("OK",  new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                if ( isConnectionError  ) {
                    isConnectionError = false;
                    finish();
                }
            }
        });
        builder.create().show();
    }

    //끝내는 다이얼로그
    public void showQuitDialog(String message)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Quit");
        builder.setCancelable(false);
        builder.setMessage(message);
        builder.setPositiveButton("OK",  new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                finish();
            }
        });
        builder.create().show();
    }

    void sendMessage(String msg){
        if ( mConnectedTask != null ) {
            mConnectedTask.write(msg);
            Log.d(TAG, "send message: " + msg);
            //mConversationArrayAdapter.insert("Me:  " + msg, 0);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_BLUETOOTH_ENABLE) {  //블루투스가 지원되고 있을 때
            if (resultCode == RESULT_OK){
                //BlueTooth is now Enabled
                showDeviceListDialog();
            }
            if(resultCode == RESULT_CANCELED){  //블루투스가 지원안되고 있을 때
                showQuitDialog( "블루투스가 꺼져있습니다");
            }
        }
    }
}
