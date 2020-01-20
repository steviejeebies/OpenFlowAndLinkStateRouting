import java.net.DatagramPacket;

/*
 *  String Types:
 *  Packet Types - SND and ACK
 *  Packet Number - ##
 *  Content Types - HELLO, FETRQ, FETRP, PACIN, FLWMD
 *  Source Host - %%
 *  Destination Host - $$

 *  Content - (insert string here) + '\u0003'
 *
 */

public class SNDContent {
	String fullDataPacketString;
	String packetType;					// SND
	int packetNumber;					// ##
	String contentType;					// HELLO, FETRQ, FETRP, PACIN, FLWMD, PACKT
	String sourceHostName;
	String destinationHostName;
	String content;						// Job listing/Worker name string
	boolean validPacket;				// used for error checking
	
	// for creating PacketContent from a received packet
	public SNDContent(DatagramPacket packet) 
	{
		validPacket = false;	//set as false at start
		byte[] data;
		data = packet.getData();
		fullDataPacketString = new String(data);
		
		//all elements have to be valid for the entire packet to be considered valid. These methods
		//also set the relevant variables within this PacketContent object at the same time.
		validPacket = setPacketType(fullDataPacketString) 
					  && setPacketNumber(fullDataPacketString) 
					  && setContentType(fullDataPacketString) 
					  && setSourceHostName(fullDataPacketString) 
					  && setDestinationHostName(fullDataPacketString)
					  && setPacketContent(fullDataPacketString);
		
		if(validPacket) this.fullDataPacketString = getPacketType()
													+ getPacketNumberToString() 
													+ getContentType()
													+ getSourceHostName() 
													+ getDestinationHostName()
													+ getPacketContent()
													+ '\u0003';
	}
	
	//for creating a PacketContent manually from a String in order to send it as packet
	public SNDContent(String inputString)		
	{	
		validPacket = false;	//set as false at start
		validPacket = setPacketType(inputString) 
				  && setPacketNumber(inputString) 
				  && setContentType(inputString)
				  && setSourceHostName(inputString) 
				  && setDestinationHostName(inputString)
				  && setPacketContent(inputString);
	
		if(validPacket) this.fullDataPacketString = getPacketType() 
													+ getPacketNumberToString() 
													+ getContentType() 
													+ getSourceHostName() 
													+ getDestinationHostName()
													+ getPacketContent()
													+ '\u0003';
	}
	
	public String toString() {
		return getPacketType() + getPacketNumberToString() + getContentType() 
				+ getSourceHostName() + getDestinationHostName() + getPacketContent() + '\u0003';
	}
	
	/*
	 * toDatagramPacket() can be used (a) after instantiating a PacketContent object with the variables
	 * we want to send, and then turning it into a DatagramPacket in order to actually send it, or (b)
	 * after receiving a DatagramPacket and calling createACK() in order to turn it into an ACK packet.
	 * 
	 * '\u0003' is appended to the end of the fullDataPacketString. This is the character "End of Text",
	 * which makes sure that the receiver knows exactly when the String ends. I was getting some errors 
	 * without this, and it doesn't matter if there are multiple '\u0003's at the end of the packet, as 
	 * long as there is at least one.
	 */

	public DatagramPacket toDatagramPacket() {
		DatagramPacket packet= null;
		try 
		{
			this.fullDataPacketString = toString();
			byte[] data = this.fullDataPacketString.getBytes();
			packet = new DatagramPacket(data, data.length);
		} catch(Exception e) { e.printStackTrace(); }
		return packet;
	}
	
	// SET METHODS
	
	private boolean setPacketType(String packetString)	//boolean returns true if assignment is successful
	{
		String packetType = packetString.substring(0,3);	//gets first 3 letters of packet string
		if(packetType.equals("ACK") || packetType.equals("SND"))
		{
			this.packetType = packetType;
			return true;
		}
		return false;
	}
	
	//setPacketNumber establishes the packet number of the packet.
	
	private boolean setPacketNumber(String packetString)
	{
		String packetNumberString = packetString.substring(3, 5);
		this.packetNumber = Integer.parseInt(packetNumberString);
		if(packetNumber >= 0 && packetNumber <= 15)
			return true;	
		return false;
	}
	
	//setContentType() establishes if a packet is of type JOB/CMP/WVU/WVA.
	
	private boolean setContentType(String packetString)
	{
		String contentType = packetString.substring(5, 10);
		if(contentType.equals("HELLO") || contentType.contentEquals("FETRQ") 
				|| (contentType.equals("FETRP") || contentType.equals("PACIN")
				|| (contentType.equals("FLWMD"))))
		{
			this.contentType = contentType;
			return true;
		}
		return false;
	}
	
	// Both the destinationHostName and sourceHostName will have the structure
	// of one letter and one number, so we only need to find substring of length 2.
	
	private boolean setSourceHostName(String packetString)
	{
		sourceHostName = packetString.substring(10, 12);
		return true;
	}
	
	private boolean setDestinationHostName(String packetString)
	{
		destinationHostName = packetString.substring(12, 14);
		return true;
	}
	
	// setPacketContent() is for creating the string of information that is contained within a
	// packet, which will be either a job description or a worker name.
	
	private boolean setPacketContent(String packetString)
	{
		//Cutting off at EndOfText char
		String content = packetString.substring(14, (packetString.indexOf('\u0003'))); 
		this.content = content;
		return true;
	}
	
	public void resetContentType(String newContentType)
	{
		this.contentType = newContentType;
	}
	
	public void resetPacketNumber(int newPacketNumber)
	{
		this.packetNumber = newPacketNumber;
		// if we are resetting packet number, we know it will be a SND packet
		packetType = "SND"; 
	}
	
	// GET METHODS
	
	public String getPacketType()
	{
		return packetType;
	}
	
	public int getPacketNumber()
	{
		return packetNumber;
	}
	
	public String getPacketNumberToString()
	{
		return "" + ((packetNumber < 10) ? "0" + packetNumber : packetNumber);
	}
	
	public String getContentType()
	{
		return contentType;
	}
	
	public String getDestinationHostName()
	{
		return destinationHostName;
	}
	
	public String getSourceHostName()
	{
		return sourceHostName;
	}
	
	public String getPacketContent()
	{
		return content;
	}
	
	public boolean isValid()
	{
		return validPacket;
	}
}
