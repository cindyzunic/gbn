package com.gbn;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import static com.gbn.sender.scheduleTimer;

public class sender {

    public static Timer timer;
    public static final int timerValue = 150;
    public static boolean timerRunning = false;
    private static int totalPackets;

    public static void main(String[] args) {

        // basic error checking
        if(args.length < 4) {
            util.printErr("Invalid number of arguments");
            return;
        }

        String emulatorAddr, fileName;

        emulatorAddr = args[0];
        fileName = args[3];

        int emPort, ackPort;

        try {
            emPort = Integer.parseInt(args[1]);
            ackPort = Integer.parseInt(args[2]);
        } catch(NumberFormatException e) {
            util.printErr("invalid port numbers");
            return;
        }

        InetAddress ipAddress;
        if((ipAddress = util.getValidAddress(emulatorAddr)) == null) {
            util.printErr("Invalid nEmulator Address");
            return;
        }

        //create the timer
        timer = new Timer("ackTimer");

        List<packet> packetList = makePackets(fileName);
        totalPackets = packetList.size();

        // start thread to wait for ack's and update inflight count
        ackWait ackwait = new ackWait(ipAddress, ackPort, emPort, totalPackets);
        ackwait.start();

        //start thread to send the packets
        packetSender packetsender = new packetSender(ipAddress, emPort, packetList);
        packetsender.start();

    }

    //takes file and returns a list of packets
    private static ArrayList<packet> makePackets(String fileName) {
        try {
            InputStreamReader reader = new FileReader(fileName);
            int seqNum = 0;
            int numRead = 0;
            char[] buffer = new char[500];
            ArrayList<packet> packetList = new ArrayList<>();

            while (numRead != -1) {
                numRead = reader.read(buffer, 0, 500);

                if(numRead == -1) {
                    break;
                }

                //check if sending less than 500 chars, avoid null chars padding
                if(numRead % 500 != 0) {
                    int len = numRead % 500;
                    char[] temp = new char[len];
                    System.arraycopy(buffer, 0, temp, 0, len);
                    buffer = temp;
                }

                packetList.add(packet.createPacket(seqNum, new String(buffer)));
                seqNum = (seqNum + 1) % 32;
                buffer = new char[500];
            }
            reader.close();

            return packetList;
        } catch(IOException e) {
            util.printErr("Invalid File");
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    //resets timer
    public static synchronized void scheduleTimer() {
        timer.cancel();
        timer.purge();
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                timerRunning = false;
                timer.cancel();
                timer.purge();

                if(util.totalAcked == totalPackets) {
                    return;
                }

                util.resendFrom = util.totalAcked; //set resend flag
                util.inflight = 0;
            }
        }, timerValue);
        timerRunning = true;
    }
}

// encapsulates a thread that takes care of sending the packets
class packetSender {
    public Thread thread;
    private InetAddress addr;
    private int port;
    private List<packet> packetList;
    private static PrintWriter segnumlogger;
    private static final String segnumlog = "segnum.log";

    packetSender(InetAddress addr, int port, List<packet> packetList) {
        this.addr = addr;
        this.port = port;
        this.packetList = packetList;

        try {
            //create logger
            segnumlogger = util.createFileWriter(segnumlog);

        } catch (IOException e) {
            util.printErr("Failed to create output file");
        }
    }

    public void start() {
        if(thread == null) {
            thread = new Thread() {
                public void run() {

                    int i = 0;
                    //run this while not done sending
                    while(i < packetList.size() || util.totalAcked < packetList.size()) {

                        //if window is full, wait
                        while((util.inflight == util.WINDOW_SIZE)) {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                //meh
                            }
                        }

                        //nothing to send but haven't received all acks yet
                        if(i >= packetList.size() && util.resendFrom == -1) {
                           try {
                                Thread.sleep(10);
                                continue;
                            } catch (InterruptedException e) {
                                //meh
                            }
                        }

                        try {
                            if(util.resendFrom != -1) {
                                i = util.resendFrom;
                                util.resendFrom = -1; //clear resend flag
                            }

                            util.sendPacket(addr, port, packetList.get(i));
                            segnumlogger.println(packetList.get(i).getSeqNum());
                            i++;

                            //check if timer started, start if not
                            if(!sender.timerRunning && util.totalAcked != packetList.size()) {
                                sender.scheduleTimer();
                            }

                        } catch (IOException e) {
                            util.printErr("Failed to send packet " + packetList.get(i).getSeqNum());
                            return;
                        }
                    }
                    segnumlogger.close();
                    util.sendComplete = true;
                }
            };

            thread.start();
        }
    }
}

class ackWait {
    public Thread thread;
    private InetAddress addr;
    int port;
    int outPort;
    int totalPackets;
    private static final String acklog = "ack.log";
    private static PrintWriter acklogger;

    ackWait(InetAddress addr, int port, int outPort, int totalPackets) {
        this.addr = addr;
        this.port = port;
        this.outPort = outPort;
        this.totalPackets = totalPackets;

        try {
            //create logger
            acklogger = util.createFileWriter(acklog);

        } catch (IOException e) {
            util.printErr("Failed to create output file");
            return;
        }
    }

    public void start () {
        if (thread == null) {
            thread = new Thread () {
                public void run() {
                    //while not all packets accounted for
                    while(util.totalAcked < totalPackets) {
                        try {
                            packet pack = util.receivePacket(port);

                            //if not an ack, ignore it
                            if(pack.getType() != 0) {
                                continue;
                            }

                            acklogger.println(pack.getSeqNum());


                            //if duplicate ack, ignore
                            if(pack.getSeqNum() <= util.lastAcked && pack.getSeqNum() > (util.lastAcked - util.inflight)) {
                                continue;
                            }

                            //acked a new packet
                            //calculate offset
                            int temp = (pack.getSeqNum() - util.lastAcked) % 32;
                            if(temp < 0) {
                                temp += 32;
                            }

                            util.inflight = util.inflight - temp;
                            util.totalAcked = util.totalAcked + temp;
                            util.lastAcked = pack.getSeqNum();

                            if(util.inflight > 0) {
                                scheduleTimer();
                            }

                        } catch (Exception e) {
                            util.printErr("Receive Ack error");
                        }
                    }

                    acklogger.close();

                    try {
                        while(!util.sendComplete) {
                            //wait for other thread to complete
                            Thread.sleep(10);
                        }

                        //send eot
                        util.sendPacket(addr, outPort, packet.createEOT(0));
                        packet eot = util.receivePacket(port);

                        //make sure we actually receive an EOT back
                        while(eot.getType() != 2) {
                           // util.printOut("here");
                            util.sendPacket(addr, outPort, packet.createEOT(0));
                            eot = util.receivePacket(port);
                        }

                    } catch (Exception e) {
                        util.printErr("Error with EOT");
                    }
                }
            };

            thread.start();
        }
    }
}

