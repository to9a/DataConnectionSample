package jp.co.altec.dataconnectionsample;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;



public class MainActivity extends Activity {
    private final String TAG = "DEBUG";
    Handler mHandler = new Handler();
    Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            String rcvData = "";
            if (mClientConnection != null) {
//                Log.d(TAG, "receive client message....");
                rcvData = mClientConnection.getData();
            } else if (mHostConnection != null) {
//                Log.d(TAG, "receive host message....");
                rcvData = mHostConnection.getData();
            }
            TextView textView = (TextView)findViewById(R.id.recvMsg);
            textView.setText(rcvData);

            mHandler.postDelayed(mRunnable,300);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button bt = (Button)findViewById(R.id.button);
        bt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mClientConnection != null) {
                    Log.d(TAG, "send client message....");
                    mClientConnection.sendData(((EditText) findViewById(R.id.editText)).getText().toString());
                } else if (mHostConnection != null) {
                    Log.d(TAG, "send host message....");
                    mHostConnection.sendData(((EditText) findViewById(R.id.editText)).getText().toString());
                }
            }
        });

        mHandler.postDelayed(mRunnable, 300);
    }

    HostConnection mHostConnection;

    public void onHostSwitchClicked(View view) {
        Switch swtOnOff = (Switch) view;
        if (swtOnOff.isChecked()) { // ON状態になったとき
            Toast.makeText(getApplicationContext(), "Host SwitchがONになりました。", Toast.LENGTH_SHORT).show();

            mHostConnection = new HostConnection(this);
            if (mHostConnection != null) {
                String ipAddress = mHostConnection.getMyIpAddress();
                TextView txtView = (TextView) findViewById(R.id.textView);
                txtView.setText(ipAddress);

                // ゲストからのIPアドレス受信可能状態とする。
                mHostConnection.createReceiveUdpSocket();

                Log.d(TAG, "//// TCP/IP通信開始(ホスト)  ////");
                mHostConnection.connect();
            }
        }
    }

    ClientConnection mClientConnection;
    Object mLock = new Object();

    public void onGuestSwitchClicked(View view) {
        Switch swtOnOff = (Switch) view;
        if (swtOnOff.isChecked()) { // ON状態になったとき
            Toast.makeText(getApplicationContext(), "Guest SwitchがONになりました。", Toast.LENGTH_SHORT).show();

            mClientConnection = new ClientConnection(this);
            if (mClientConnection != null) {
                String ipAddress = mClientConnection.getMyIpAddress();
                TextView txtView = (TextView) findViewById(R.id.textView);
                txtView.setText(ipAddress);

                // ゲストからのIPアドレス受信可能状態とする。
                mClientConnection.sendBroadcast();
                mClientConnection.receivedHostIp();

                new Thread() {
                    @Override
                    public void run() {
                        while (!mClientConnection.isTcpIpAvailable()) {
                            try {
                                sleep(500);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }

                        Log.d(TAG, "//// TCP/IP通信開始(ゲスト)  ////");
                        mClientConnection.connect(mClientConnection.getHostDevice().getDeviceIpAddress());
                    }
                }.start();

            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
