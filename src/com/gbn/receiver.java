package com.gbn;

import java.io.*;
import java.net.InetAddress;

public class receiver {
    private static int lastGood = -1;
    private static boolean isEOT = false;
    private static PrintWriter writer;
    private static PrintWriter logger;
    private static final String logName = "arrival.log";

    public static void main(String[] args) {
        // basic error checking
        if(args.length < 4) {
            util.printErr("Invalid number of arguments");
            return;
        }

        String emulatorAddr, fileName;

        emulatorAddr = args[0];
        fileName = args[3];

        int inPort, outPort;

        try {
            outPort = Integer.parseInt(args[1]);
            inPort = Integer.parseInt(args[2]);
        } catch(NumberFormatException e) {
            util.printErr("invalid port numbers");
            return;
        }

        InetAddress ipAddress;
        if((ipAddress = util.getValidAddress(emulatorAddr)) == null) {
            util.printErr("Invalid nEmulator Address");
            return;
        }

        try {
            //create output file writer
            writer = util.createFileWriter(fileName);

            //logger
            logger = util.createFileWriter(logName);

        } catch (IOException e) {
            util.printErr("Failed to create output file");
            return;
        }

        //while we haven't received EOT, wait for and ack packets
        while(!isEOT) {
            ack(ipAddress, inPort, outPort);
        }

        writer.close();
        logger.close();

        try {
            //send EOT
            util.sendPacket(ipAddress, outPort, packet.createEOT(0));
        } catch (Exception e) {
            util.printErr("Failed to send EOT");
        }
    }

    private static void ack(InetAddress addr, int inPort, int outPort) {

        try {
            packet pack = util.receivePacket(inPort);

            if(pack.getType() == 2) {
                isEOT = true;
                return;
            }

            if(pack.getType() == 0) {
                return; //should not receive an ack
            }

            logger.println(pack.getSeqNum());
            if(pack.getSeqNum() == (lastGood + 1) % 32) {
                //write to file
                writer.print(new String(pack.getData()));
                lastGood = (lastGood + 1) % 32;
            }

            if(lastGood < 0) {
                return;
            }
            util.sendPacket(addr, outPort, packet.createACK(lastGood));

        } catch (IOException e) {
            util.printErr("Failed to receive a packet");
            return;
        } catch (Exception e) {
            util.printErr("Failed to create packet");
            return;
        }
    }
}
