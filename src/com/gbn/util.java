package com.gbn;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.*;

public class util {
    public static final int WINDOW_SIZE = 10;
    public static int inflight = 0;
    public static int lastAcked = -1;
    public static int resendFrom = -1;
    public static int totalAcked = 0;
    public static boolean sendComplete = false;

    public static InetAddress getValidAddress(String address) {
        try {
            return Inet4Address.getByName(address);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    //send packet to ip and port
    public static void sendPacket(InetAddress addr, int port, packet pack) throws IOException {
        try {
            DatagramSocket udpSocket = new DatagramSocket();

            byte[] sendData;
            sendData = pack.getUDPdata();

            //send server the packet
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, addr, port);
            udpSocket.send(sendPacket);

            udpSocket.close();
            inflight++;
        } catch (IOException e) {
            throw e;
        }
    }

    //receive a packet from given port
    public static packet receivePacket(int port) throws IOException {
        DatagramSocket udpSocket = new DatagramSocket(port);
        byte[] receiveData = new byte[1024];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        udpSocket.receive(receivePacket);

        udpSocket.close();

        packet pack;
        try {
            pack = packet.parseUDPdata(receiveData);
            return pack;
        } catch (Exception e) {
            return null;
        }
    }

    public static PrintWriter createFileWriter(String name) throws IOException {
        return new PrintWriter(
                new BufferedWriter(
                        new FileWriter(name)
                )
        );
    }

    public static void printOut(String msg) {
        System.out.println(msg);
    }

    public static void printErr(String err) {
        System.err.println(err);
    }
}
