import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Timer;

public class NodeData extends Thread{
	private final String nodeName;
	private final String nodeType; // Host, Router, or Controller
	private final int dstPort;
	private final DatagramSocket thisSocket;
	private final InetSocketAddress dstAddress;
	private int nextSentPackNum;		//keeping track of sender window	(from perspective of Broker)
	private int nextExpectedPackNum;	//keeping track of receiver window	(from perspective of Broker)
	public String nodeID;				//Node's name, e.g. H3 for a host, R5 for a router.
	
	public Timer[] goBackNWindow = new Timer[16];
	int goBackNWindowSize = 0;
	int distanceToThisRouter;

	// This version of the NodeData class is a slightly reduced version of the one used in Assignment 1.
	// This class will be used by Controller and Routers, and is used solely for communication between
	// nodes. I decided to reuse the Go-Back-N model of my last assignment, as it will greatly 
	// simplify setting up communication between nodes for this assignment.

	NodeData(DatagramSocket thisSocket, int connectedNodePort, String nodeType, String nodeName, int distanceToThisRouter)		
	{
		this.nodeName = nodeName;
		this.nodeType = nodeType;
		nextSentPackNum = 0;
		nextExpectedPackNum = 0;
		dstPort = connectedNodePort;
		dstAddress = new InetSocketAddress("localhost", dstPort);
		this.distanceToThisRouter = distanceToThisRouter;
		this.thisSocket = thisSocket;
	}
	
	public String getNodeName() {
		return nodeName;
	}

	public int getDSTPort() {
		return dstPort;
	}

	public InetSocketAddress getDstAddress() {
		return dstAddress;
	}
	
	public String getNodeType() {
		return nodeType;
	}
	
	public int getNextSentPackNum() {
		return nextSentPackNum;
	}
	
	public String getNextSentPackNumToString() {
		return "" + ((nextSentPackNum < 10) ? "0" + nextSentPackNum : nextSentPackNum);
	}

	public int getNextExpectedPackNum() {
		return nextExpectedPackNum;
	}
	
	public String getNextExpectedPackNumToString() {
		return "" + ((nextExpectedPackNum < 10) ? "0" + nextExpectedPackNum : nextExpectedPackNum);
	}
	
	public void incrementNextExpectedPackNum() 
	{
		nextExpectedPackNum = (nextExpectedPackNum + 1) % 16; // iterate nextSentPackNum
	}
	
	public void sendPacket(SNDContent PacketContentToSend) {
		// always one element on the goBackNWindow that is null.
		while (goBackNWindowSize >= 15) 
		{
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) { e.printStackTrace(); }
		}
		
		// Reset packet number so it matches up with node's next expected packet number,
		// and then create Datagram packet
		PacketContentToSend.resetPacketNumber(nextSentPackNum);
		DatagramPacket packetToSend = PacketContentToSend.toDatagramPacket();
		packetToSend.setSocketAddress(dstAddress); // sets this node's dstAddress as the destination for this packet
		
		Timer packetTimer = new Timer(); // start new timer
		TimerFlowControl ARQ = new TimerFlowControl(thisSocket, packetToSend);
		packetTimer.schedule(ARQ, 100, 5000); // delay of 2 seconds, repeat every 2 seconds
		goBackNWindow[nextSentPackNum] = packetTimer;
		
		nextSentPackNum = (nextSentPackNum + 1) % 16; // iterate nextSentPackNum
		goBackNWindowSize++;
	}
}
