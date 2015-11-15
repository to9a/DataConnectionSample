package jp.co.altec.dataconnectionsample;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * Created by tokue on 2015/11/10.
 */
public class HostConnection {
    WifiManager mWifiManager;
    String mIpAddress;
    Context mContext;
    String TAG = "WIFI-HOST_CONN";

    boolean mTcpIpAvailable = false;

    public boolean isTcpIpAvailable() {
        return mTcpIpAvailable;
    }

    /*コンストラクタ*/
    public HostConnection(Context context){
        mWifiManager = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
        mContext = context;
    }

    DatagramSocket receiveUdpSocket;
    boolean waiting = false;
    int udpPort = 9999;//ホスト、ゲストで統一

    /**
     * ホスト処理
     * ゲストが通知されるIPアドレス情報を取得する
     */
    void createReceiveUdpSocket() {
        waiting = true;
        new Thread() {
            @Override
            public void run(){
                String address;
                try {
                    //受信用ソケット
                    receiveUdpSocket = new DatagramSocket(udpPort);
                    Log.d(TAG,"receive socket open. port :" + receiveUdpSocket.getLocalPort());

                    //waiting = trueの間、ブロードキャストを受け取る
                    while(waiting){
                        byte[] buf = new byte[256];
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);

                        //ゲスト端末からのブロードキャストを受け取る
                        //受け取るまでは待ち状態になる
                        Log.d(TAG, "waiting packet data ..........");
                        receiveUdpSocket.receive(packet);

                        int length = packet.getLength();
                        address = new String(buf, 0, length);

                        // 送信元情報の取得
                        Log.d(TAG, "receive socketAddress is " + packet.getSocketAddress().toString() + "packet data : " + address);

                        // 受信したIPアドレスへ自分のIPアドレスを通知
                        returnIpAdress(address);
                    }
                    receiveUdpSocket.close();
                } catch (SocketException e) {
                    waiting = false;
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    Socket returnSocket;
    int tcpPort = 3333;//ホスト、ゲストで統一

    private String getDeviceName() {
        return "Host";
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

    //端末名とIPアドレスのセットを送る
    void outputDeviceNameAndIp(final Socket outputSocket, final String deviceName, final String deviceAddress){
        new Thread(){
            @Override
            public void run(){
                final BufferedWriter bufferedWriter;
                try {
                    bufferedWriter = new BufferedWriter(
                            new OutputStreamWriter(outputSocket.getOutputStream())
                    );
                    //デバイス名を書き込む
                    bufferedWriter.write(deviceName);
                    bufferedWriter.newLine();
                    //IPアドレスを書き込む
                    bufferedWriter.write(deviceAddress);
                    bufferedWriter.newLine();
                    //出力終了の文字列を書き込む
                    bufferedWriter.write("outputFinish");
                    //出力する
                    bufferedWriter.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    //ブロードキャスト発信者(ゲスト)にIPアドレスと端末名を返す
    void returnIpAdress(final String address){
        new Thread() {
            @Override
            public void run() {
                try{
                    if(returnSocket != null){
                        returnSocket.close();
                        returnSocket = null;
                    }
                    returnSocket = new Socket(address, tcpPort);
                    Log.d(TAG,"connect remote socket  Address : " + returnSocket.getRemoteSocketAddress());
                    //端末情報をゲストに送り返す
                    outputDeviceNameAndIp(returnSocket, getDeviceName(), getMyIpAddress());
                }catch(UnknownHostException e){
                    e.printStackTrace();
                }catch (java.net.ConnectException e){
                    e.printStackTrace();
                    try{
                        if(returnSocket != null) {
                            returnSocket.close();
                            returnSocket = null;
                        }
                    }catch(IOException e1) {
                        e.printStackTrace();
                    }
                }catch(IOException e){
                    e.printStackTrace();
                }
            }
        }.start();
    }

    // ホスト側の処理群
    ServerSocket serverSocket;
    Socket connectedSocket;

    //ゲストからの接続を待つ処理
    void connect(){
        new Thread(){
            @Override
            public void run(){
                try {
                    //ServerSocketを生成する
                    serverSocket = new ServerSocket(tcpPort);
                    //ゲストからの接続が完了するまで待って処理を進める
                    connectedSocket = serverSocket.accept();
                    Toast.makeText(mContext, "接続されました " + connectedSocket.getRemoteSocketAddress().toString(), Toast.LENGTH_LONG).show();

                    //この後はconnectedSocketに対してInputStreamやOutputStreamを用いて入出力を行ったりする
                    BufferedReader in = new BufferedReader(new InputStreamReader(connectedSocket.getInputStream()));
                    PrintWriter out = new PrintWriter(connectedSocket.getOutputStream(), true);
                    String line;
                    while ( (line = in.readLine()) != null ) {
                        Log.d(TAG,"受信: " + line);
                        out.println(line);
                        Log.d(TAG, "送信: " + line);
                    }
                }catch (SocketException e){
                    e.printStackTrace();
                }catch(IOException e){
                    e.printStackTrace();
                }
            }
        }.start();
    }
}
