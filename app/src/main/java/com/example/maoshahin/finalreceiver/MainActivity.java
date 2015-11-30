package com.example.maoshahin.finalreceiver;

import android.annotation.TargetApi;
import android.nfc.Tag;
import android.os.Build;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;

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
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import android.os.Handler;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;


public class MainActivity extends Activity {

    private DatagramSocket ds;
    private InetAddress host;
    private DatagramPacket dp;
    private TextView tv;
    private String st = "";


    public static final String TYPE = "TYPE";
    public static final String SEQ = "SEQ";
    public static final String DATA = "DATA";

    private static final int START_SEQUENCE_NUM = 1;

    private static final int SERVER_PORT = 7005;
    private static final String SERVER_IP = "192.168.168.113";
    private static final int PORT_MY = 7005;
    private static final String IP_MY = "192.168.168.117";
    private static final String downloadedFile = "/storage/sdcard0/Download/hello.txt";//"/storage/emulated/0/DCIM/COMP7005/hello.txt";//

    UDPReceiver mUDPReceiver = null;
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
        try {
            host = InetAddress.getByName(SERVER_IP);
            ds = new DatagramSocket();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onClickConnect(View v) {
        // start receiver thread
        if(mUDPReceiver == null) {
            mUDPReceiver = new UDPReceiver(MainActivity.this);
        }
        mUDPReceiver.start();
    }

    public void onClickDisconnect(View v) {
        mUDPReceiver.stop();
    }

    public void onClickClear(View v) {
        st = "";
        tv.setText(st);
    }


    private JSONObject createJson(String type, int sequenceNo,byte[] data) throws JSONException {
        JSONObject packet = new JSONObject();
        packet.put(TYPE, type);
        packet.put(SEQ,sequenceNo);
        packet.put(DATA, data);
        return packet;
    }

    public void dataArrived(JSONObject arrivedJson) throws JSONException {
        String packetType = arrivedJson.getString(TYPE);
        JSONObject sendJson = null;
        if (packetType.equals("SOS")) {
            sendJson = createJson("SOS",0,null);
            printOnPhoneScreen("Acking SOS" +" "+sendJson.toString());
        } else if (packetType.equals("EOT")) {// send eot three times
            sendJson = createJson("EOT",0,null);
            sendPacket(sendJson.toString().getBytes());
            sendPacket(sendJson.toString().getBytes());
            printOnPhoneScreen("Acking EOT");
        } else {// data
            int seq = arrivedJson.getInt(SEQ);
            sendJson = createJson("ACK",seq,null);
            printOnPhoneScreen("Acking packet with seq# "+seq +" "+sendJson.toString());
        }
        Log.d("sendingPacket",sendJson.toString());
        sendPacket(sendJson.toString().getBytes());
    }

    private void sendPacket( final byte[] data) {

        new Thread(new Runnable() {
            public void run() {
                try {
                    dp = new DatagramPacket(data, data.length, host, SERVER_PORT);
                    ds.send(dp);
                } catch (Exception e) {
                    System.err.println("sendPacket : " + e);
                }
            }
        }).start();
    }

    public void printOnPhoneScreen(String msg) {
        st += msg + "\n";
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
        mUDPReceiver.stop();
        super.onDestroy();
    }

    class UDPReceiver implements Runnable {
        private static final String TAG = "UDPReceiverThread";
        DatagramSocket mDatagramRecvSocket = null;
        ArrayList<JSONObject> framesArrived;
        MainActivity mActivity = null;

        Thread udpreceiverthread;

        public UDPReceiver(MainActivity mainActivity) {
            super();
            mActivity = mainActivity;

        }

        public void start() {
            if( udpreceiverthread == null ) {
                udpreceiverthread = new Thread( this );
                udpreceiverthread.start();
            }
        }

        public void stop() {
            if( udpreceiverthread != null ) {
                udpreceiverthread.interrupt();
            }
        }

        @Override
        public void run() {
            byte receiveBuffer[] = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            FileOutputStream fos = null;
            framesArrived = new ArrayList<JSONObject>();
            JSONObject ackedFrame = null;
            try {
                mDatagramRecvSocket = new DatagramSocket(PORT_MY);
                fos = new FileOutputStream(downloadedFile);
                ackedFrame = createJson("DATA",START_SEQUENCE_NUM - 1,null) ;
            } catch (Exception e) {
                e.printStackTrace();
            }

            Log.d(TAG, "In run(): thread start.");
            try {
                while (!udpreceiverthread.interrupted()){
                    mDatagramRecvSocket.receive(receivePacket);
                    String packetString = new String(receivePacket.getData(), 0, receivePacket.getLength());
                    JSONObject arrivedJson = new JSONObject(packetString);
                    String packetType = arrivedJson.getString(TYPE);
                    if (packetType.equals("DAT")) {
                        if(ackedFrame.getInt(SEQ)<arrivedJson.getInt(SEQ)) {
                            framesArrived.add(arrivedJson);
                            Collections.sort(framesArrived, new SeqNoComparator());// sorting arrived frames
                            int sizeFramesArrived = framesArrived.size();
                            int ackedFrameNo = ackedFrame.getInt(SEQ);
                            for (int i = ackedFrameNo; i < ackedFrameNo+sizeFramesArrived; i++) {
                                if (i + 1 == framesArrived.get(0).getInt(SEQ)) {
                                    fos.write(framesArrived.get(0).getString(DATA).getBytes());
                                    arrivedJson = framesArrived.get(0);
                                    ackedFrame = framesArrived.get(0);
                                    framesArrived.remove(0);
                                }
                            }
                        }
                        mActivity.dataArrived(ackedFrame);

                    } else if (packetType.equals("SOS")) {
                        framesArrived.add(arrivedJson);
                        mActivity.dataArrived(arrivedJson);
                    } else if (packetType.equals("EOT")) {
                        mActivity.finish();
                        mActivity.dataArrived(arrivedJson);
                        Toast.makeText(mActivity, "File Transfer is successfully finished", Toast.LENGTH_LONG);
                        break;
                    } else if (packetType.equals("ERR")) {
                        mActivity.finish();
                        Toast.makeText(mActivity, "There was an error", Toast.LENGTH_LONG);
                        break;
                    }
                    Log.d(TAG, "In run(): packet received [" + packetString + "]");

                }
                Log.d(TAG, "In run(): thread end.");
                if(mDatagramRecvSocket!=null) {
                    mDatagramRecvSocket.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }finally {
                udpreceiverthread = null;
            }
        }
    }

    class SeqNoComparator implements Comparator<JSONObject> {

        @TargetApi(Build.VERSION_CODES.KITKAT)
        public int compare(JSONObject a, JSONObject b) {
            int a_seq = 0;
            int b_seq = 0;
            try {
                a_seq = a.getInt(SEQ);
                b_seq = b.getInt(SEQ);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return Integer.compare(a_seq, b_seq);
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