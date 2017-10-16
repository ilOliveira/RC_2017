
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class Ftp17ClientSW {
	static final int DEFAULT_TIMEOUT = 1000;
	static final int DEFAULT_MAX_RETRIES = 5;
	private static final int DEFAULT_BLOCK_SIZE = 1024;
	static int WindowSize = 10; // this client is a stop and
								// wait one
	static int BlockSize = DEFAULT_BLOCK_SIZE;
	static int Timeout = DEFAULT_TIMEOUT;

	static ArrayList<Ftp17Packet> sent = new ArrayList<Ftp17Packet>();
	static ArrayList<Long> ackSN = new ArrayList<Long>();
	static ArrayList<Long> sendTimes = new ArrayList<Long>();

	private Stats stats;
	private String filename;
	private DatagramSocket socket;
	private BlockingQueue<Ftp17Packet> receiverQueue;
	volatile private SocketAddress srvAddress;

	int allSent;

	Ftp17ClientSW(String filename, SocketAddress srvAddress) {
		this.filename = filename;
		this.srvAddress = srvAddress;
	}

	private void sendData(byte[] block, int size, long sn, int pos)
			throws IOException {
		// Builds message to send
		System.out.println("SN: " + sn);
		Ftp17Packet data = new Ftp17Packet().putShort(Ftp17Packet.DATA)
				.putLong(sn).putBytes(block, size);
		// Add packet to the sent list
		System.err.println("sending: " + data);

		sendTimes.add(pos, System.currentTimeMillis());

		sent.add(pos, data);
		// System.out.println("data: "+data.getBlockData().length);
		ackSN.add(pos, sn + data.getBlockData().length);

		socket.send(data.toDatagram(srvAddress));

	}

	private void resendWindow() throws IOException {
		for (int i = 0; i < allSent; i++) {
			socket.send(sent.get(i).toDatagram(srvAddress));
		}
	}

	void sendFile() {
		try {

			// socket = new MyDatagramSocket();
			socket = new DatagramSocket();

			// create producer/consumer queue for ACKs
			receiverQueue = new ArrayBlockingQueue<>(1);
			// for statistics
			stats = new Stats();

			// start a receiver process to feed the queue
			// new Thread( ()-> { this crap is not coded like this
			new Thread() {
				public void run() {
					try {
						for (;;) {
							byte[] buffer = new byte[Ftp17Packet.MAX_FTP17_PACKET_SIZE];
							DatagramPacket msg = new DatagramPacket(buffer,
									buffer.length);
							socket.receive(msg);
							// update server address (it may change due to reply
							// to UPLOAD coming from a different port
							srvAddress = msg.getSocketAddress();
							System.err.println(new Ftp17Packet(msg.getData(),
									msg.getLength())
									+ "from:"
									+ msg.getSocketAddress());
							// make the packet available to sender process
							Ftp17Packet pkt = new Ftp17Packet(msg.getData(),
									msg.getLength());
							receiverQueue.put(pkt);
						}
					} catch (Exception e) {
					}
				}
			}.start();

			System.out.println("\nsending file: \"" + filename
					+ "\" to server: " + srvAddress + " from local port:"
					+ socket.getLocalPort() + "\n");

			sendRetry(new UploadPacket(filename), 1L, DEFAULT_MAX_RETRIES);

			FileInputStream f = new FileInputStream(filename);
			long nextByte = 1L; // block byte count starts at 1
			// read and send blocks
			int n;
			byte[] buffer = new byte[DEFAULT_BLOCK_SIZE];
			int lastSent = 0;
			while (true) {
				while (lastSent < WindowSize) {

					if ((n = f.read(buffer)) > 0) {
						System.out.println("n: " + n);

						sendData(buffer, n, nextByte, lastSent);

						System.out.println("NextBYTE: " + ackSN.get(lastSent));
						nextByte = ackSN.get(lastSent);
						lastSent++;
						System.out.println("LAst Sent: "+lastSent);
						System.out.println("Window: "+WindowSize);
						stats.newPacketSent(n);
					} else {
						// send the FIN packetsendData
						System.err.println("sending: "
								+ new FinPacket(nextByte));
						socket.send(new FinPacket(nextByte)
								.toDatagram(srvAddress));
						break;
					}
				}
				allSent = lastSent;
				do {
					Ftp17Packet ack = receiverQueue.poll(Timeout,
							TimeUnit.MILLISECONDS);
					if (ack != null) {
						if (ack.getOpcode() == Ftp17Packet.FINACK) {

							f.close();

							socket.close();
							System.out.println("Done...");
							break;

						}
						if (ack.getOpcode() == Ftp17Packet.ACK) {
							if (ackSN.get(allSent - lastSent) == ack.getSeqN()) {

								stats.newTimeoutMeasure(System
										.currentTimeMillis()
										- sendTimes.get(allSent - lastSent));

								System.err.println("got expected ack: "
										+ ack.getSeqN());
								lastSent--;

							} else {
								System.err.println("got wrong ack "
										+ ack.getSeqN());
								System.out.println("ACk compared: "
										+ ackSN.get(allSent - lastSent));
								lastSent = allSent;
								resendWindow();
							}
						} else {
							System.err.println("got unexpected packet (error)");
							lastSent = allSent;

							resendWindow();
						}
					} else {
						System.err.println("timeout...");
 						break;
						//resendWindow();
					}

				} while (lastSent >= 0);

			}// close the loop
		} catch (Exception x) {
			x.printStackTrace();
			System.exit(0);
		}
		stats.printReport();
	}

	/*
	 * Send a block to the server, repeating until the expected ACK is received,
	 * or the number of allowed retries is exceeded.
	 */
	void sendRetry(Ftp17Packet pkt, long expectedACK, int retries)
			throws Exception {
		for (int i = 0; i < retries; i++) {
			System.err.println("sending: " + pkt);
			long sendTime = System.currentTimeMillis();

			socket.send(pkt.toDatagram(srvAddress));

			Ftp17Packet ack = receiverQueue
					.poll(Timeout, TimeUnit.MILLISECONDS);
			if (ack != null)
				if (ack.getOpcode() == Ftp17Packet.ACK)
					if (expectedACK == ack.getSeqN()) {
						stats.newTimeoutMeasure(System.currentTimeMillis()
								- sendTime);
						System.err.println("got expected ack: " + expectedACK);
						return;
					} else {
						System.err.println("got wrong ack");
					}
				else {
					System.err.println("got unexpected packet (error)");
				}
			else
				System.err.println("timeout...");
		}
		throw new IOException("too many retries");
	}

	class Stats {
		private long totalRtt = 0;
		private int timesMeasured = 0;
		private int window = 1;
		private int totalPackets = 0;
		private int totalBytes = 0;
		private long startTime = 0L;;

		Stats() {
			startTime = System.currentTimeMillis();
		}

		void newPacketSent(int n) {
			totalPackets++;
			totalBytes += n;
		}

		void newTimeoutMeasure(long t) {
			timesMeasured++;
			totalRtt += t;
		}

		void printReport() {
			// compute time spent receiving bytes
			int milliSeconds = (int) (System.currentTimeMillis() - startTime);
			float speed = (float) (totalBytes * 8.0 / milliSeconds / 1000); // M
																			// bps
			float averageRtt = (float) totalRtt / timesMeasured;
			System.out.println("\nTransfer stats:");
			System.out.println("\nFile size:\t\t\t" + totalBytes);
			System.out.println("Packets sent:\t\t\t" + totalPackets);
			System.out.printf("End-to-end transfer time:\t%.3f s\n",
					(float) milliSeconds / 1000);
			System.out
					.printf("End-to-end transfer speed:\t%.3f M bps\n", speed);
			System.out.printf("Average rtt:\t\t\t%.3f ms\n", averageRtt);
			System.out.printf("Sending window size:\t\t%d packet(s)\n\n",
					window);
		}
	}

	public static void main(String[] args) throws Exception {
		// MyDatagramSocket.init(1, 1);
		try {
			switch (args.length) {
			case 5:
				// Ignored for S/W Client
				WindowSize = Integer.parseInt(args[4]);
			case 4:
				BlockSize = Integer.valueOf(args[3]);
				// BlockSize must be at least 1
				if (BlockSize <= 0)
					BlockSize = DEFAULT_BLOCK_SIZE;
				// for S/W Client WindowSize is equal to BlockSize
				// WindowSize = BlockSize;
			case 3:
				Timeout = Integer.valueOf(args[2]);
				// Timeout must be at least 1 ms
				if (Timeout <= 0)
					Timeout = 1;
			case 2:
				break;
			default:
				throw new Exception("bad parameters");
			}
		} catch (Exception x) {
			System.out
					.printf("usage: java Ftp17Client filename server [ timeout [ blocksize [ windowsize ]]]\n");
			System.exit(0);
		}
		String filename = args[0];
		String server = args[1];
		SocketAddress srvAddr = new InetSocketAddress(server,
				Ftp17Packet.FTP17_PORT);
		new Ftp17ClientSW(filename, srvAddr).sendFile();
	}

	static class UploadPacket extends Ftp17Packet {
		UploadPacket(String filename) {
			putShort(UPLOAD);
			putLong(0L);
			putBytes(DUMMY_SCRATCHPAD);
			putString(filename);
		}
	}

	static class DataPacket extends Ftp17Packet {
		DataPacket(long seqN, byte[] payload, int length) {
			putShort(DATA);
			putLong(seqN);
			putBytes(DUMMY_SCRATCHPAD);
			putBytes(payload, length);
		}
	}

	static class FinPacket extends Ftp17Packet {
		FinPacket(long seqN) {
			putShort(FIN);
			putLong(seqN);
			putBytes(DUMMY_SCRATCHPAD);
		}
	}

}
