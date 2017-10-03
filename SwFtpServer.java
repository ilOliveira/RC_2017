
/**
 * SwFtpServer - a very simple SW FTP like server - RC FCT/UNL
 **/

import java.net.*;
import java.util.*;
import java.io.*;

public class SwFtpServer implements Runnable {
	static int DEFAULT_PORT = SwFtpPacket.SWFCTPORT; // my default port
	static int DEFAULT_TRANSFER_TIMEOUT = 15000; // terminates transfer after
	// this timeout if no data block is received

	private String filename;
	private SocketAddress cltAddr;

	static DatagramSocket socket;
	static final int TIMEOUT = 2000; // 2 sec.

	static final int RRQ = SwFtpPacket.OP_RRQ;
	static final int WRQ = SwFtpPacket.OP_WRQ;
	static final int DATA = SwFtpPacket.OP_DATA;
	static final int ERROR = SwFtpPacket.OP_ERROR;
	static final int ACK = SwFtpPacket.OP_ACK;
	static final int FIN = SwFtpPacket.OP_FIN;
	static final int FINACK = SwFtpPacket.OP_FINACK;

	static final int MAXPKT = SwFtpPacket.MAX_SWFTP_PACKET_SIZE;
	static final int MAXDATA  = SwFtpPacket.MAX_SWFTP_PACKET_SIZE - 10;

	SwFtpServer(SwFtpPacket req, SocketAddress cltAddr) {
		this.cltAddr = cltAddr;
		filename = req.getFilename();
	}

	public void run() {
		System.out.println("START!");
		receiveFile();
		System.out.println("DONE!");
	}

	private void receiveFile() {
		System.err.println("receiving file:" + filename );
		try {
			DatagramSocket socket = new DatagramSocket();
			// Defines the timeout to end the server, in case the client stops sending data
			socket.setSoTimeout(DEFAULT_TRANSFER_TIMEOUT);
			//confirms the file transfer request
			sendACK(socket, 1L, cltAddr);
			RandomAccessFile raf = new RandomAccessFile(filename + ".bak", "rw");
			boolean finished = false;
			long expectedByte = 1; // next byte in sequence
			do {
				byte[] buffer = new byte[MAXPKT];
				DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
				socket.receive(datagram);
				SwFtpPacket pkt = new SwFtpPacket(datagram.getData(), datagram.getLength());
				switch (pkt.getOpcode()) {
				case DATA:
					//saves the data at the proper offset
					byte[] data = pkt.getBlockData();
					raf.seek(pkt.getBlockSeqN() - 1L);
					raf.write(data);
					if (pkt.getBlockSeqN() == expectedByte)
						expectedByte += data.length;
					sendACK(socket, expectedByte, cltAddr);
					break;
				case FIN:
					if (pkt.getBlockSeqN() == expectedByte) {
						sendFINACK(socket, expectedByte, cltAddr);
						finished = true;
					}
					else
						sendACK(socket, expectedByte, cltAddr);
					break;
				case WRQ:
					sendACK(socket, 1L, cltAddr);
					break;
				case ERROR:
					throw new IOException("got error from client: " + pkt.getErrorCode() + ": " + pkt.getErrorMessage());
				default:
					throw new RuntimeException("error receiving file." + filename + "/" + pkt.getOpcode());
				}
			} while (!finished);
			raf.close();

		} catch (SocketTimeoutException x) {
			System.err.printf("interrupted transfer; no data received after %s ms\n", DEFAULT_TRANSFER_TIMEOUT);
		} catch (Exception x) {
			System.err.println("receive failed: " + x.getMessage());
		}
	}

	/*
	 * Prepare and send an ACK
	 */
	private static void sendACK(DatagramSocket s, long seqN, SocketAddress dst) throws IOException {
		SwFtpPacket ack = new SwFtpPacket().putShort(ACK).putLong(seqN);
		s.send(new DatagramPacket(ack.getPacketData(), ack.getLength(), dst));
		// System.err.printf("sent: %s \n", ack);
	}


	/*
	 * Sends a FINACK packet
	 */
	private static void sendFINACK(DatagramSocket s, long seqN, SocketAddress dst) throws IOException {
		SwFtpPacket ack = new SwFtpPacket().putShort(FINACK).putLong(seqN);
		s.send(new DatagramPacket(ack.getPacketData(), ack.getLength(), dst));
		// System.err.printf("sent: %s \n", ack);
	}
	
	/*
	 * Sends an error packet
	 */
	private static void sendError(DatagramSocket s, int err, String str, SocketAddress dstAddr)
			throws IOException {
		SwFtpPacket pkt = new SwFtpPacket().putShort(ERROR).putShort(err).putString(str).putByte(0);
		s.send(new DatagramPacket(pkt.getPacketData(), pkt.getLength(), dstAddr));
	}

	public static void main(String[] args) throws Exception {

		// create and bind socket to port for receiving client requests
		DatagramSocket mainSocket = new DatagramSocket(DEFAULT_PORT);
		System.out.println("New sw ftp server started at local port " + mainSocket.getLocalPort());
		for (;;) { // infinite processing loop...
			try {
				// receives request from clients
				byte[] buffer = new byte[MAXPKT];
				DatagramPacket msg = new DatagramPacket(buffer, MAXPKT);
				mainSocket.receive(msg);
				// look at datagram as a SwFtpPacket
				SwFtpPacket req = new SwFtpPacket(msg.getData(), msg.getLength());
				switch (req.getOpcode()) {
				case WRQ: // Write Request
					System.err.println("write request == receive file: " + req.getFilename());
					// Launch a dedicated thread to handle the client request...
					new Thread(new SwFtpServer(req, msg.getSocketAddress())).start();
					break;
				default: // unexpected packet op code!
					System.err.printf("Unknown or not imp. request type... opcode %d ignored\n", req.getOpcode());
					sendError(mainSocket, 0, "Unknown request type..." + req.getOpcode(), msg.getSocketAddress());
				}
			} catch (Exception x) {
				x.printStackTrace();
			}
		}
	}



}
