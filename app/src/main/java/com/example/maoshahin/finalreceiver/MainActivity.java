package com.example.maoshahin.finalreceiver;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;

import android.os.Handler;


public class MainActivity extends Activity {

    private DatagramSocket ds;
    private InetAddress host;
    private DatagramPacket dp;
    private TextView tv;
    private String st = "";



    private static final int SERVER_PORT = 7005;
    private static final String SERVER_IP = "192.168.1.38";
    private static final int PORT_MY = 7005;
    private static final String downloadedFile = "/storage/emulated/0/DCIM//COMP7005/20KBReceived.txt";

    private static final int START_SEQUENCE_NUM = 1;
    private static final int RECEIVER_BUFFER_SIZE = 1024*2;

    UDPReceiver mUDPReceiver = null;
    Handler mHandler = null;


    /**
     * Called when the activity is first created.
     *
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //set texts in text fields
        ((TextView) findViewById(R.id.tv_ip)).setText(SERVER_IP);
        ((TextView) findViewById(R.id.tv_port)).setText(SERVER_PORT + "");
        ((TextView) findViewById(R.id.tv_ip_my)).setText(getIpAddress());
        ((TextView) findViewById(R.id.tv_port_my)).setText(PORT_MY + "");

        // create receiver thread and start
        if(mUDPReceiver == null) {
            mUDPReceiver = new UDPReceiver(MainActivity.this);
        }
        mUDPReceiver.start();

        // get textfield for status
        tv = (TextView) findViewById(R.id.tv);
        // handler is used for setting text to text view from different thread
        mHandler = new Handler();


        // create  and sockets to send data
        try {
            host = InetAddress.getByName(SERVER_IP);
            ds = new DatagramSocket();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * get local ip address and return as a string
     * @return ip address
     */
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

    /**
     * this is called when connect button is pressed
     * @param v
     */
    public void onClickConnect(View v) {
        // start receiver thread
        if(mUDPReceiver == null) {
            mUDPReceiver = new UDPReceiver(MainActivity.this);
            mUDPReceiver.start();
        }else {
            // if the it is already set, reset files and other settings
            mUDPReceiver.reset();
            printOnPhoneScreen("ACK Receiver thread running");
        }
    }

    /**
     * clears status field
     * @param v
     */
    public void onClickClear(View v) {
        st = "";
        tv.setText(st);
    }


    /**
     * a method to deal with arrived frames on main thread. mostly called from receiver thread
     * @param arrivedFrame
     */
    public void dataArrived(Frame arrivedFrame) {
        String packetType = arrivedFrame.getTYPE();
        Frame sendFrame = null;
        // when EOT arrived, send 3 EOT packets back
       if (packetType.equals("EOT")) {
            sendFrame = new Frame("EOT",0,null);
            sendPacket(sendFrame.toString().getBytes());
            sendPacket(sendFrame.toString().getBytes());
            printOnPhoneScreen("Acking EOT");
        } else {
        // if it is not EOT, it is always DAT
        // when DATA arrived, send ack with  highest frame
            int seq = arrivedFrame.getSEQ();
            sendFrame = new Frame("ACK",seq,null);
            printOnPhoneScreen("Acking packet with seq# " + seq);
        }
        Log.d("sendingPacket",sendFrame.toString());
        sendPacket(sendFrame.toString().getBytes());
    }

    /**
     * a function to send a packet
     * @param data
     */
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

    /**
     * a method to update status fields
     * @param msg
     */
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

    /**
     * when app is closed, this method is called
     */
    @Override
    public void onDestroy() {
        if(mUDPReceiver != null) {
            // stop the thread
            mUDPReceiver.stop();
        }
        super.onDestroy();
    }

    /**
     * UDP receiver thread, it is almost always runnning
     */
    class UDPReceiver implements Runnable {
        DatagramSocket mDatagramRecvSocket = null;
        ArrayList<Frame> framesArrived;// used to store arrived frames
        MainActivity mActivity = null;//used to access method in main thread
        FileOutputStream fos;
        Frame ackedFrame; //arrived frame with highest sequence number

        Thread udpreceiverthread; // receiver thread

        public UDPReceiver(MainActivity mainActivity) {
            super();
            mActivity = mainActivity;

        }

        /**
         * called to start thread
         */
        public void start() {
            if( udpreceiverthread == null ) {
                udpreceiverthread = new Thread( this );
                udpreceiverthread.start();
            }
        }

        /**
         * called to stop thread
         */
        public void stop() {
            if( udpreceiverthread != null ) {
                udpreceiverthread.interrupt();// stop thread
                mDatagramRecvSocket.disconnect();// close socket
                mDatagramRecvSocket.close();
            }
        }

        /**
         * reset variables to the initial state
         */
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
            // set variables needed for receiving packets
            byte receiveBuffer[] = new byte[RECEIVER_BUFFER_SIZE];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            try {
                mDatagramRecvSocket = new DatagramSocket(PORT_MY);
            } catch (SocketException e) {
                e.printStackTrace();
            }
            //reset variables
            reset();

            mActivity.printOnPhoneScreen("Receiver thread running");
            try {
                while (!udpreceiverthread.interrupted()){
                    // receive and convert it to Frame object
                    mDatagramRecvSocket.receive(receivePacket);
                    String packetString = new String(receivePacket.getData(), 0, receivePacket.getLength());
                    Frame arrivedFrame = Frame.createFrameFromString(packetString);

                    String packetType = arrivedFrame.getTYPE();
                    mActivity.printOnPhoneScreen(packetType+" packet received, "+"sequence No:"+arrivedFrame.getSEQ());

                    if (packetType.equals("DAT")) {
                        // if it is DAT packet and if the sequence number is higher than highest acked sequence number
                        if(ackedFrame.getSEQ()<arrivedFrame.getSEQ()) {
                            framesArrived.add(arrivedFrame);
                            Collections.sort(framesArrived, new SeqNoComparator());// sorting arrived frames
                            int sizeFramesArrived = framesArrived.size();
                            for (int i = 0; i < sizeFramesArrived; i++) {// go through array list
                                if( framesArrived.get(0).getSEQ() <= ackedFrame.getSEQ()){
                                    //if a packet is  smaller or equal to highest acked packet,discard packet from array list
                                    framesArrived.remove(0);
                                }else if ((ackedFrame.getSEQ() + 1) == framesArrived.get(0).getSEQ()) {
                                    // if it is the next packet of highest acked packet, write data and
                                    ackedFrame = framesArrived.get(0);
                                    mActivity.printOnPhoneScreen("Writing data of a packet, "+"sequence No:"+ackedFrame.getSEQ());
                                    fos.write(ackedFrame.getDATA());
                                    framesArrived.remove(0);
                                }
                            }
                        }
                        // send frame with highest sequence number
                        mActivity.dataArrived(ackedFrame);

                    } else if (packetType.equals("EOT")) {
                        // send EOT
                        mActivity.dataArrived(arrivedFrame);
                        mActivity.printOnPhoneScreen("File Transfer is successfully finished");
                        break; //end this thread
                    }
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
                mActivity.mUDPReceiver = null;// this is set to null to inform the thread is done
            }
        }
    }

    /**
     * this function is used to sort array list of Frame object
     */
    class SeqNoComparator implements Comparator<Frame> {
        @TargetApi(Build.VERSION_CODES.KITKAT)
        public int compare(Frame a, Frame b) {
            return Integer.compare(a.getSEQ(), b.getSEQ());
        }

    }

}