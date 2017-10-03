
import java.io.*;
import java.net.*;


public class SwFtpClient {

	static final int TIMEOUT = 2000; // 2 sec.
	static final int TRIES = 3; // max number of times to try sending a packet
	static final int DEFAULT_PORT = SwFtpPacket.SWFCTPORT;

	static final int RRQ = SwFtpPacket.OP_RRQ;
	static final int WRQ = SwFtpPacket.OP_WRQ;
	static final int DATA = SwFtpPacket.OP_DATA;
	static final int ERROR = SwFtpPacket.OP_ERROR;
	static final int ACK = SwFtpPacket.OP_ACK;
	static final int FIN = SwFtpPacket.OP_FIN;
	static final int FINACK = SwFtpPacket.OP_FINACK;

	static final int MAXPKT = SwFtpPacket.MAX_SWFTP_PACKET_SIZE;
	static final int MAXDATA  = SwFtpPacket.MAX_SWFTP_PACKET_SIZE - 10;

	static DatagramSocket socket;
	static InetAddress serverAddress;
	static int port;

	public static void main(String[] args ) throws Exception {
		if( args.length != 2 ) {
			System.out.printf("usage: java SwFtpClient server file-name\n") ;
			System.exit(0);
		}
		// Prepare server address as well as port
		String server = args[0] ;
		String fname = args[1] ;
		port = 	DEFAULT_PORT;
		serverAddress = InetAddress.getByName( server );
		socket = new DatagramSocket();
		System.out.printf("request=\"%s\"; fname=\"%s\"\n", "WRR ", fname );
		sendWRequest(fname);
		sendFile(fname);
		socket.close();
	}



	private static void sendRRequest(String fname) {
		// Builsd message to send
		SwFtpPacket req = new SwFtpPacket();
		req.putShort(RRQ).putString(fname).putByte(0);
		try  {
			socket.send(new DatagramPacket(req.getPacketData(),req.getLength(),serverAddress,port) );
		} catch (IOException e) {
			System.err.println("failed to send read request");
			System.exit(1);
		}
	}
	
	private static void sendWRequest(String fname) {
		// Builds message to send
	}

       
	private static void sendACK(long sn) {
		// Builds message to send
	}
       

	private static void sendData(byte[] block, int size, long sn) {
		// Builds message to send
	}
       
	
	private static void sendFIN(long sn) {
		// Builds message to send
	}

	private static void sendFINACK(long sn) {
		// Builds message to send
	}


	private static void receiveFile(String fname) throws Exception {
		System.out.println("receiving file:"+fname);
		FileOutputStream f = null;
		int srvport=port; // the server port (starts with port but can be updated on the first datagram)
		long blkCount = 0; // number of blocks already received
		long expSn = 1; // Expected sn;
		// order of expected next first byte = bytes already correctly received + 1
		int acksSent = 0;
		long start = System.currentTimeMillis();
		byte []buffer = new byte[MAXPKT];
		DatagramPacket p = new DatagramPacket(buffer, buffer.length);
		for (;;) {
		    // sends all fle data blocks using the SW protocol
		    // at the end sends FIN packets and waits for their ACKs
		};
		f.close();
		System.out.printf("%d Bytes received, in %d data blocks, %d acks sent, in %d milliseconds\n", 
				expSn-1, blkCount, acksSent, System.currentTimeMillis()-start );
	}


	private static void sendFile(String fname) throws Exception {
		System.out.println("sending file:"+fname);

	}


}
