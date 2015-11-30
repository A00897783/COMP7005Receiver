package com.example.maoshahin.finalreceiver;

import android.nfc.Tag;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;


import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import android.os.Handler;


public class MainActivity extends Activity {

    private DatagramSocket ds;
    private DatagramPacket dp;
    private TextView tv;
    private String st;


    private static final int SERVER_PORT = 7005;
    private static final String SERVER_IP = "192.168.168.106";
    private static final int PORT_MY = 7005;
    private static final String IP_MY = "192.168.168.117";
    private static final String downloadedFile = "/storage/emulated/0/DCIM/COMP7005/hello.txt";//"/storage/sdcard0/Download/hello.png";

    UDPReceiverThread mUDPReceiver = null;
    Handler mHandler = null;


    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ((TextView) findViewById(R.id.tv_ip)).setText(SERVER_IP);
        ((TextView) findViewById(R.id.tv_port)).setText(SERVER_PORT + "");
        ((TextView) findViewById(R.id.tv_ip_my)).setText(IP_MY);
        ((TextView) findViewById(R.id.tv_port_my)).setText(PORT_MY + "");
        tv = (TextView) findViewById(R.id.tv);
        mHandler = new Handler();
    }

    public void onClickConnect(View v) {
        // start receiver thread
        mUDPReceiver = new UDPReceiverThread(MainActivity.this);
        mUDPReceiver.start();
    }

    public void onClickDisconnect(View v) {
        mUDPReceiver.onStop();
        Log.d("onclick", "stop");
    }

    public void onClickClear(View v) {
        st = "";
        tv.setText(st);
    }

    private void sendPacket() {
        new Thread(new Runnable() {
            public void run() {
                try {
                    InetAddress host = InetAddress.getByName(SERVER_IP);
                    String message = "send by Android " + st + " \n";
                    ds = new DatagramSocket();
                    byte[] data = message.getBytes();
                    dp = new DatagramPacket(data, data.length, host, SERVER_PORT);
                    ds.send(dp);
                } catch (Exception e) {
                    System.err.println("sendPacket : " + e);
                }
            }
        }).start();
    }

    public void printOnPhoneScreen(String msg) {
        st += msg+"\n";
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                tv.setText(st);
                return;
            }
        });
    }

    @Override
    public void onDestroy() {
        mUDPReceiver.onStop();
        super.onDestroy();
    }

    class UDPReceiverThread extends Thread {
        private static final String TAG="UDPReceiverThread";
        private static final int comm_port = 7005;
        public static final String COMM_END_STRING="end";

        DatagramSocket mDatagramRecvSocket= null;
        MainActivity mActivity= null;
        boolean mIsArive= false;

        public UDPReceiverThread( MainActivity mainActivity ) {
            super();
            mActivity= mainActivity;
            try {
                mDatagramRecvSocket= new DatagramSocket(comm_port);
            }catch( Exception e ) {
                e.printStackTrace();
            }

        }
        @Override
        public void start() {
            mIsArive= true;
            super.start();
        }
        public void onStop() {
            mIsArive= false;
        }
        @Override
        public void run() {
            byte receiveBuffer[] = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(downloadedFile);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            Log.d(TAG, "In run(): thread start.");
            try {
                while (mIsArive) {
                    mDatagramRecvSocket.receive(receivePacket);
                    String packetString=new String(receivePacket.getData(),0, receivePacket.getLength());
                    Log.d(TAG,"In run(): packet received ["+packetString+"]");
                    byte[] receivedData = receivePacket.getData();
                    mActivity.printOnPhoneScreen("datacame!");
                    fos.write(receivedData);
                    if( packetString.equals(COMM_END_STRING) ) {
                        mActivity.finish();
                        break;
                    }else{
                        Log.d(TAG,packetString.equals(COMM_END_STRING)+"");
                        Log.d(TAG,packetString+" "+COMM_END_STRING);

                    }
                }
            }catch( Exception e ) {
                e.printStackTrace();
            }
            Log.d(TAG,"In run(): thread end.");
            mDatagramRecvSocket.close();
            mDatagramRecvSocket= null;
            mActivity= null;
            receivePacket= null;
            receiveBuffer= null;
        }
    }

    /*
    class UDPReceiverThread extends Thread {
        private static final String TAG = "UDPReceiverThread";
        public static final String COMM_END_STRING = "end";

        DatagramSocket mDatagramRecvSocket = null;
        MainActivity mActivity = null;
        boolean mIsArive = false;

        public UDPReceiverThread(MainActivity mainActivity) {
            super();
            mActivity = mainActivity;
            try {
                mDatagramRecvSocket = new DatagramSocket(mActivity.PORT_MY);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        @Override
        public void start() {
            mIsArive = true;
            super.start();
        }

        public void onStop() {
            Log.d(TAG, "stop");
            mIsArive = false;
        }

        @Override
        public void run() {
            byte receiveBuffer[] = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(downloadedFile);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            Log.d(TAG, "In run(): thread start.");
                while (mIsArive) {
                    try {
                        /*if( !mIsArive ) {
                            // 終了メッセージを受信したらActivity終了
                            // whileループを抜けてソケットclose＆スレッド終了
                            mActivity.finish();
                            break;
                        }
                        mDatagramRecvSocket.receive(receivePacket);
                        byte[] receivedData = receivePacket.getData();
                        mActivity.printOnPhoneScreen("datacame!");
                        fos.write(receivedData);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    Log.d(TAG, "In run(): thread end.");
                    mDatagramRecvSocket.close();
                    mDatagramRecvSocket = null;
                    mActivity = null;
                    receivePacket = null;
                    receiveBuffer = null;
                }

        }
    }*/
}