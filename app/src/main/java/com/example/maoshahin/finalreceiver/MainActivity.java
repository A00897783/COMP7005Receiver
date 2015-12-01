package com.example.maoshahin.finalreceiver;

import android.annotation.TargetApi;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.nfc.Tag;
import android.os.Build;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;

import android.app.Activity;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;

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
    private static final String downloadedFile = "/storage/emulated/0/DCIM//COMP7005/hello2.txt";//"/storage/emulated/0/DCIM/COMP7005/hello.txt";//

    private static final int RECEIVER_BUFFER_SIZE = 1024*2;
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
        //get local ip
        ((TextView) findViewById(R.id.tv_ip_my)).setText(getIpAddress());
        ((TextView) findViewById(R.id.tv_port_my)).setText(PORT_MY + "");
        if(mUDPReceiver == null) {
            mUDPReceiver = new UDPReceiver(MainActivity.this);
        }
        mUDPReceiver.start();

        tv = (TextView) findViewById(R.id.tv);
        mHandler = new Handler();
        try {
            host = InetAddress.getByName(SERVER_IP);
            ds = new DatagramSocket();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getIpAddress() {
        try {
            for (Enumeration en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = (NetworkInterface) en.nextElement();
                for (Enumeration enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = (InetAddress) enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()&&inetAddress instanceof Inet4Address) {
                        String ipAddress=inetAddress.getHostAddress().toString();
                        Log.e("IP address",""+ipAddress);
                        return ipAddress;
                    }
                }
            }
        }  catch (SocketException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void onClickConnect(View v) {
        // start receiver thread
        if(mUDPReceiver == null) {
            mUDPReceiver = new UDPReceiver(MainActivity.this);
            mUDPReceiver.start();
        }else {
            mUDPReceiver.reset();
            printOnPhoneScreen("ACK Receiver thread running");
        }
    }

    public void onClickDisconnect(View v) {
        //mUDPReceiver.stop();
    }

    public void onClickClear(View v) {
        st = "";
        tv.setText(st);
    }


    public void dataArrived(Frame arrivedFrame) {
        String packetType = arrivedFrame.getTYPE();
        Frame sendFrame = null;
       if (packetType.equals("EOT")) {// send eot three times
            sendFrame = new Frame("EOT",0,null);
            sendPacket(sendFrame.toString().getBytes());
            sendPacket(sendFrame.toString().getBytes());
            printOnPhoneScreen("Acking EOT");
        } else {// data
            int seq = arrivedFrame.getSEQ();
            sendFrame = new Frame("ACK",seq,null);
            printOnPhoneScreen("Acking packet with seq# " + seq);
        }
        Log.d("sendingPacket",sendFrame.toString());
        sendPacket(sendFrame.toString().getBytes());
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
        if(mUDPReceiver != null) {
            mUDPReceiver.stop();
        }
        super.onDestroy();
    }

    class UDPReceiver implements Runnable {
        private static final String TAG = "UDPReceiverThread";
        DatagramSocket mDatagramRecvSocket = null;
        ArrayList<Frame> framesArrived;
        MainActivity mActivity = null;
        FileOutputStream fos;
        Frame ackedFrame;

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
                mDatagramRecvSocket.disconnect();
                mDatagramRecvSocket.close();
            }
        }

        public void reset(){
            fos = null;
            framesArrived = new ArrayList<Frame>();
            ackedFrame = null;
            try {
                fos = new FileOutputStream(downloadedFile);
                ackedFrame = new Frame("DATA",START_SEQUENCE_NUM - 1,null) ;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            byte receiveBuffer[] = new byte[RECEIVER_BUFFER_SIZE];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            try {
                mDatagramRecvSocket = new DatagramSocket(PORT_MY);
            } catch (SocketException e) {
                e.printStackTrace();
            }
            reset();

            mActivity.printOnPhoneScreen("Receiver thread running");
            try {
                while (!udpreceiverthread.interrupted()){
                    mDatagramRecvSocket.receive(receivePacket);
                    String packetString = new String(receivePacket.getData(), 0, receivePacket.getLength());
                    Frame arrivedFrame = Frame.createFrameFromString(packetString);
                    String packetType = arrivedFrame.getTYPE();
                    mActivity.printOnPhoneScreen(packetType+" packet received, "+"sequence No:"+arrivedFrame.getSEQ());
                    if (packetType.equals("DAT")) {
                        if(ackedFrame.getSEQ()<arrivedFrame.getSEQ()) {
                            framesArrived.add(arrivedFrame);
                            Collections.sort(framesArrived, new SeqNoComparator());// sorting arrived frames
                            int sizeFramesArrived = framesArrived.size();
                            for (int i = 0; i < sizeFramesArrived; i++) {
                                if( framesArrived.get(0).getSEQ() <= ackedFrame.getSEQ()){
                                    framesArrived.remove(0);
                                }else if ((ackedFrame.getSEQ() + 1) == framesArrived.get(0).getSEQ()) {
                                    ackedFrame = framesArrived.get(0);
                                    mActivity.printOnPhoneScreen("Writing data of a packet, "+"sequence No:"+ackedFrame.getSEQ());
                                    fos.write(ackedFrame.getDATA());
                                    framesArrived.remove(0);
                                }
                            }
                        }
                        mActivity.dataArrived(ackedFrame);

                    } else if (packetType.equals("EOT")) {
                        mActivity.dataArrived(arrivedFrame);
                        mActivity.printOnPhoneScreen("File Transfer is successfully finished");
                        break;
                    }
                    Log.d(TAG, "In run(): packet received [" + packetString + "]");

                }
                mActivity.printOnPhoneScreen("Receiver thread end");
                if(mDatagramRecvSocket!=null) {
                    mDatagramRecvSocket.disconnect();
                    mDatagramRecvSocket.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }finally {
                udpreceiverthread = null;
                mActivity.mUDPReceiver = null;
            }
        }
    }

    class SeqNoComparator implements Comparator<Frame> {
        @TargetApi(Build.VERSION_CODES.KITKAT)
        public int compare(Frame a, Frame b) {
            return Integer.compare(a.getSEQ(), b.getSEQ());
        }

    }

}