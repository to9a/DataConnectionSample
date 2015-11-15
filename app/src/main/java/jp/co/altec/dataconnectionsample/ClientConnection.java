package jp.co.altec.dataconnectionsample;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * Created by tokue on 2015/11/10.
 */
public class ClientConnection {
    WifiManager mWifiManager;
    String mIpAddress;
    String TAG = "CLIENT-CONN";
    Context mContext;
    boolean mTcpIpAvailable = false;

    public boolean isTcpIpAvailable() {
        return mTcpIpAvailable;
    }

    /*コンストラクタ*/
    public ClientConnection(Context context){
        mWifiManager = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
        mContext = context;
    }

    //ブロードキャストアドレスの取得
    InetAddress getBroadcastAddress(){
        DhcpInfo dhcpInfo = mWifiManager.getDhcpInfo();
        int broadcast = (dhcpInfo.ipAddress & dhcpInfo.netmask) | ~dhcpInfo.netmask;
        byte[] quads = new byte[4];
        for (int i = 0; i < 4; i++){
            quads[i] = (byte)((broadcast >> i * 8) & 0xFF);
        }
        try {
            return InetAddress.getByAddress(quads);
        }catch (IOException e){
            e.printStackTrace();
            return null;
        }
    }

    boolean waiting = false;
    int udpPort = 9999;//ホスト、ゲストで統一

    // ホスト側の処理群
    ServerSocket serverSocket;
    Socket connectedSocket;
    int tcpPort = 3333;//ホスト、ゲストで統一

    //ホストからTCPでIPアドレスが返ってきたときに受け取るメソッド
    void receivedHostIp(){
        new Thread() {
            @Override
            public void run() {
                while (waiting) {
                    try {
                        if(serverSocket == null) {
                            serverSocket = new ServerSocket(tcpPort);
                        }
                        Socket socket = serverSocket.accept();
                        inputDeviceNameAndIp(socket);
                        if (serverSocket != null) {
                            serverSocket.close();
                            serverSocket = null;
                        }
                        if (socket != null) {
                            socket.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    waiting = false;
                    mTcpIpAvailable = true;
                }
            }
        }.start();
    }

    public SampleDevice getHostDevice() {
        return hostDevice;
    }

    SampleDevice hostDevice;

    //端末名とIPアドレスのセットを受け取る
    void inputDeviceNameAndIp(Socket socket){
        try {
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream())
            );
            int infoCounter = 0;
            String remoteDeviceInfo;
            //ホスト端末情報(端末名とIPアドレス)を保持
            hostDevice = new SampleDevice();

            while((remoteDeviceInfo = bufferedReader.readLine()) != null && !remoteDeviceInfo.equals("outputFinish")){
                switch(infoCounter){
                    case 0:
                        //1行目、端末名の格納
                        hostDevice.setDeviceName(remoteDeviceInfo);
                        infoCounter++;
                        break;
                    case 1:
                        //2行目、IPアドレスの取得
                        hostDevice.setDeviceIpAddress(remoteDeviceInfo);
                        infoCounter++;
                        break;
                    default:
                        break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.d(TAG, "host info : " + hostDevice);
    }

    /**
     * 自分のIPアドレスを取得する。
     * @return IPアドレス
     */
    String getMyIpAddress(){
        int ipAddress_int = mWifiManager.getConnectionInfo().getIpAddress();
        if(ipAddress_int == 0){
            mIpAddress = null;
        }else {
            mIpAddress = (ipAddress_int & 0xFF) + "." + (ipAddress_int >> 8 & 0xFF) + "." + (ipAddress_int >> 16 & 0xFF) + "." + (ipAddress_int >> 24 & 0xFF);
        }
        Log.d(TAG, "my ipAddress is " + mIpAddress);
        return mIpAddress;
    }

    //同一Wi-fiに接続している全端末に対してブロードキャスト送信を行う
    void sendBroadcast(){
        final String myIpAddress = getMyIpAddress();
        waiting = true;
        new Thread() {
            @Override
            public void run() {
                int count = 0;
                //送信回数を10回に制限する
                while (count < 10) {
                    try {
                        DatagramSocket udpSocket = new DatagramSocket(udpPort);
                        udpSocket.setBroadcast(true);
                        DatagramPacket packet = new DatagramPacket(myIpAddress.getBytes(), myIpAddress.length(), getBroadcastAddress(), udpPort);
                        udpSocket.send(packet);
                        udpSocket.close();
                    } catch (SocketException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    //5秒待って再送信を行う
                    try {
                        Thread.sleep(5000);
                        count++;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

    //IPアドレスが判明したホストに対して接続を行う
    void connect(final String remoteIpAddress){
        waiting = false;
        new Thread() {
            @Override
            public void run() {
                try{
                    if(connectedSocket == null) {
                        connectedSocket = new Socket(remoteIpAddress, tcpPort);
                    }

                    //この後はホストに対してInputStreamやOutputStreamを用いて入出力を行ったりする
                    BufferedWriter writer = null;
                    try {
                        // メッセージ送信オブジェクトのインスタンス化
                        writer = new BufferedWriter(new OutputStreamWriter(
                                connectedSocket.getOutputStream()));

                        writer.write(remoteIpAddress + ":" + "TCP/IP通信・・・" + "\r\n");
                        writer.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }finally{
                        try {
                            writer.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                            Toast.makeText(mContext, "サーバーとの接続に失敗しました。", Toast.LENGTH_SHORT).show();
                        }
                    }

                }catch(UnknownHostException e){
                    e.printStackTrace();
                }catch (ConnectException e){
                    e.printStackTrace();
                }catch(IOException e){
                    e.printStackTrace();
                }
            }
        }.start();
    }
}
