
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class SwFtpPacket {

	public static final int MAX_SWFTP_DATA_SIZE = 1024;
	public static final int MAX_SWFTP_PACKET_SIZE = MAX_SWFTP_DATA_SIZE+10;
	public static final int SWFCTPORT = 9000;


	// Op Codes
	public static final short OP_RRQ = 1;
	public static final short OP_WRQ = 2;
	public static final short OP_DATA = 3;
	public static final short OP_ACK = 4;
	public static final short OP_ERROR = 5;
	public static final short OP_FIN = 6;
	public static final short OP_FINACK = 7;

	// Extensions - not used by the moment
	protected static final short OP_OACK = 8;

	protected byte[] packet;
	protected ByteBuffer bb;

	/**
	 * Constructor for creating a new, initially, empty SwFtpPacket
	 * 
	 **/
	public SwFtpPacket() {
		bb = ByteBuffer.allocate(MAX_SWFTP_PACKET_SIZE);
		packet = bb.array();
	}

	/**
	 * Constructor for decoding a byte array as a SwFtpPacket
	 * 
	 **/
	public SwFtpPacket(byte[] packet, int length) {
		this.packet = packet;
		this.bb = ByteBuffer.wrap(packet, 0, length);
		this.bb.position(length); // ensure internal bb position is at "length"
	}

	/**
	 * Gets the opcode from the first two bytes of the packet, stored in net
	 * byte order (Big Endian)
	 */
	public int getOpcode() {
		return bb.getShort(0);
	}

	/**
	 * 
	 * @return the size of the SwFtpPacket in bytes
	 */
	public int getLength() {
		return bb.position();
	}

	/**
	 * 
	 * @return the byte array containing the SwFtpPacket
	 */
	public byte[] getPacketData() {
		byte[] res = new byte[getLength()];
		System.arraycopy(packet, 0, res, 0, res.length);
		return res;
	}

	/**
	 * Assuming the SwFtpPacket is a RRQ or WRQ
	 * 
	 * @return the filename
	 */
	public String getFilename() {
		return new String(packet, 2, getLength() - 2).split("\0")[0];
	}



	/**
	 * Assuming the SwFtpPacket is an ERROR
	 * 
	 * @return the error code
	 */
	public int getErrorCode() {
		// ErrorCode is a short
		return bb.getShort(2);
	}

	/**
	 * Assuming the SwFtpPacket is an ERROR
	 * 
	 * @return the error message
	 */
	public String getErrorMessage() {
		// ErrorCode is a short
		return new String(packet, 4, getLength() - 4);
	}

	/**
	 * Assuming the SwFtpPacket is a DATA, a FIN, an ACK or a FINACK
	 * 
	 * @return the block number
	 */
	public long getBlockSeqN() {
		return bb.getLong(2);
	}

	/**
	 * Assuming the SwFtpPacket is a DATA
	 * 
	 * @return the byte array with the data payload
	 */
	public byte[] getBlockData() {
		final int offset = 10;
		byte[] res = new byte[getLength() - offset];
		System.arraycopy(packet, offset, res, 0, res.length);
		return res;
	}



	/**
	 * Appends a byte to the SwFtpPacket
	 * 
	 */
	public SwFtpPacket putByte(int b) {
		bb.put((byte) b);
		return this;
	}

	/**
	 * Appends a short (2 bytes, in net order) to the SwFtpPacket
	 * 
	 */
	public SwFtpPacket putShort(int s) {
		bb.putShort((short) s);
		return this;
	}

	/**
	 * Appends a long (8 bytes, in net order) to the SwFtpPacket
	 * 
	 */
	public SwFtpPacket putLong(long l) {
		bb.putLong(l);
		return this;
	}

	/**
	 * Appends a string (ascii 8-bit chars) to the SwFtpPacket [does not include
	 * '\0' to terminate the string]
	 * 
	 */
	public SwFtpPacket putString(String s) {
		bb.put(s.getBytes());
		return this;
	}

	/**
	 * Appends length bytes of the given (block) byte array to the SwFtpPacket
	 * 
	 */
	public SwFtpPacket putBytes(byte[] block, int length) {
		bb.put(block, 0, length);
		return this;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		switch (this.getOpcode()) {
		case OP_RRQ:
			sb.append("RRQ<");
		case OP_WRQ:
			sb.append("WRQ< filename: ");
			sb.append(this.getFilename()).append(", mode: ").append(this.getMode()).append("> options: ").append(this.getOptions());
			break;
		case OP_DATA:
			sb.append("DATA<").append( this.getBlockSeqN()).append(" : ").append( this.getBlockData().length ).append(">");
			break;
		case OP_ACK:
			sb.append("ACK<").append(this.getBlockSeqN() ).append(">");
			break;
		case OP_FIN:
			sb.append("FIN<").append(this.getBlockSeqN() ).append(">");
			break;
		case OP_FINACK:
			sb.append("FINACK<").append(this.getBlockSeqN() ).append(">");
			break;
		}
		return sb.toString();
	}




	/**
	 * Extensions for options - ignore them by the moment
	 */



	/**
	 * Assuming the SwFtpPacket is a RRQ or WRQ, or OACK, returns the options
	 * <key,value> as a map
	 * 
	 * @return the byte array with the data payload
	 */
	public Map<String, String> getOptions() {
		Map<String, String> res = new HashMap<>();
		int offset = getOpcode() == OP_OACK ? 0 : 2;
		String[] str = new String(packet, 2, getLength() - 2).split("\0");
		for (int i = offset; i + 1 < str.length; i += 2)
			res.put(str[i], str[i + 1]);

		return res;
	}

	/**
	 * Assuming the SwFtpPacket is a RRQ or WRQ
	 * 
	 * @return the transfer mode
	 */
	public String getMode() {
		return new String(packet, 2, getLength() - 2).split("\0")[1];
	}

}
