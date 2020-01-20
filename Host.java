import java.net.DatagramSocket;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import tcdIO.*;

/**
 *
 * Host class
 * 
 * An instance accepts user input
 *
 */
public class Host extends Node {
	int HostSRCPort;
	static final String DEFAULT_DST_NODE = "localhost";
	InetSocketAddress routerDSTAddress;
	String hostName;
	int nextSentPackNum;		//keeping track of sender window
	int nextExpectedPackNum;		//keeping track of receiver window
	int goBackNWindowSize;		//making sure that goBackNWindowSize <= (2^m)-1
	String[] otherHostsOnNetwork;
	Timer[] goBackNWindow = new Timer[16];
	static Terminal terminal;
	Timer doJobTimer;
	HostDoJob doJobClass;
	boolean connectionToNetworkEstablished;
	
	Host(Terminal terminal, String hostName, int numHostsOnNetwork, int hostPort, int routerPort) {
		try 
		{
			Host.terminal = terminal;
			this.hostName = hostName;
			connectionToNetworkEstablished = false;
			HostSRCPort = hostPort; // this Host's source port
			routerDSTAddress = new InetSocketAddress(DEFAULT_DST_NODE, routerPort);
			socket = new DatagramSocket(HostSRCPort); // socket for this Host (from Node class)
			
			
			// For Go-Back-N
			nextSentPackNum = 0;
			nextExpectedPackNum = 0;
			goBackNWindowSize = 0;	
			
			// The Host may want to send a packet to a host that the router does not 
			// know about, kind of like how we might want to visit a website that our
			// routers would have to do a DNS look-up for. To emulate this, I will
			// add an argument to the setup of a host to tell them how many hosts are
			// going to be on this network (this argument will be the same value for 
			// all hosts). This for-loop will just generate a name for each host, all 
			// of which have the structure "H#", and we'll store this on the 
			// otherHostsOnNetwork array.
			
			otherHostsOnNetwork = new String[numHostsOnNetwork];
			for(int i = 0; i < numHostsOnNetwork; i++)
				otherHostsOnNetwork[i] = "H" + (i + 1);
			
			// We want the host to send a packet at random intervals. 
			doJobTimer = new Timer();
			doJobClass = new HostDoJob();
			doJobTimer.schedule(doJobClass, 0, new Random().nextInt(5000) + 5000);
			
			listener.go();
		} 
		catch (java.lang.Exception e) { e.printStackTrace(); }
	}

	// Assume that incoming packets contain a String, create PacketContent which
	// sets the variables.

	public synchronized void onReceipt(DatagramPacket packet) {
		
		ACKContent potentialACK = new ACKContent(packet);
		if(potentialACK.isValidACK())
			acceptACKs(potentialACK.getACKNumber());
		else
		{	
			SNDContent content = new SNDContent(packet);
			// It is an information packet
			if(content.isValid() && content.getPacketNumber() == nextExpectedPackNum)
			{
				nextExpectedPackNum = (nextExpectedPackNum + 1 ) % 16;
				sendACK();
				
				if(content.getContentType().equals("HELLO"))
				{
					connectionToNetworkEstablished = true;
					terminal.println(hostName + ": Connection to network established.");
					SNDContent helloToRouter = new SNDContent("SND00HELLO0000" + '\u0003');
					sendPacket(helloToRouter);
				}
				else if(content.getContentType().equals("PACIN"))
				{
					terminal.println(hostName + ": Packet received: \"" + content.getPacketContent() 
										+ "\" from " + content.getSourceHostName());
				}
			}
			else
				sendACK();
		}
		this.notify();
	}
	
	private void sendPacket(SNDContent packetContent){		
		// One element on the goBackN window array must always be null.
		while(goBackNWindowSize >= 15)  
		{
			try { Thread.sleep(500); 
			} catch (InterruptedException e) { e.printStackTrace(); }
		}
		
		// reset the PacketNumber of the Packet so it matches up with the 
		// Broker's next expected Packet
		packetContent.resetPacketNumber(nextSentPackNum);
		
		// turn into DatagramPacket and set destination for packet
		DatagramPacket packetToSend = packetContent.toDatagramPacket(); 
		packetToSend.setSocketAddress(routerDSTAddress);
				
		 // start new timer for Go-Back-N
		Timer packetTimer = new Timer();
		TimerFlowControl ARQ = new TimerFlowControl(socket, packetToSend);
		packetTimer.schedule(ARQ, 0, 3000);
		
		// Make 2 arrays where any given i on both arrays are for the 
		// matching PacketContent and timer.
		goBackNWindow[nextSentPackNum] = packetTimer;
		goBackNWindowSize++;
		
		//iterate nextSentPackNum
		nextSentPackNum = (nextSentPackNum + 1) % 16;	
	}
	
	private void sendACK()
	{
		// ACKs are typically only sent once, so we do not have to designate a timer
		// to them, and we do not have to add them to our Go-Back-N window.
		
		ACKContent newACK = new ACKContent(nextExpectedPackNum);

		DatagramPacket ackPacket = newACK.toDatagramPacket(); 
		ackPacket.setSocketAddress(routerDSTAddress); 
		try {
			socket.send(ackPacket);
		} catch (IOException e) { e.printStackTrace(); }; 
	}
	
	private void acceptACKs(int latestACK) {	
		int iterationACK = (15 + latestACK) % 16; 
		
		// find last timer placed in window
		Timer packetTimerIteration = goBackNWindow[iterationACK];
		
		while (packetTimerIteration != null)
		{
			packetTimerIteration.cancel(); // cancel timer and nullify it on array
			goBackNWindow[iterationACK] = null;
			
			iterationACK = (15 + iterationACK) % 16; 
			packetTimerIteration = goBackNWindow[iterationACK];
			goBackNWindowSize--;
		}
	}
	
	// For a Host to send a new packet, I created a class HostDoJob, which was required as a
	// timer-task.  All this class does it call this Host's sendAPacketRandomly() method. 
	
	public class HostDoJob extends TimerTask {
		@Override
		public void run() {
			if(connectionToNetworkEstablished) sendAPacketRandomly();
		}
	}	
	
	public void sendAPacketRandomly()
	{
		if(new Random().nextInt(5)<2)	// Just a condition so that the Host doesn't send a packet every single time
		{
			// The strings that a host sends will just be a series of random numbers. This is 
			// just a workaround to an issue that I had in the last assignment. If we were to
			// ask the user every time to input a string, then the start() method would contain
			// a while loop that would impede with the process of receiving new packets, due to
			// the fact that our program alternates between onReceipt() and start(). Randomly
			// generating some strings solves this issue.
			
			String randomString = "" + new Random().nextInt(5000) + new Random().nextInt(5000);
			
			// Randomly select a host. A host is able to send a packet to itself, which was
			// not originally intended, but it works!
			String hostToSendTo = otherHostsOnNetwork[new Random().nextInt(otherHostsOnNetwork.length)];
			
			terminal.println(hostName + ": Sending string \"" + randomString + "\" to " + hostToSendTo);
			
			SNDContent contentToSend = new SNDContent(
							"SND00PACIN" + hostName + hostToSendTo + randomString + '\u0003');
			// Send to router
			sendPacket(contentToSend);
		}
		
	}
	
	public synchronized void start() throws Exception {
		while(true) 
		{		
			// There's nothing for a Host to do in start(), so we'll just keep a loop of this.wait()
			this.wait();
		}
	}
	
	public static void main(String[] args) {
		// The arguments for this node are expected to be the port number of the node.
		String hostName = args[0];
		int numHostsOnNetwork = Integer.parseInt(args[1]);
		int hostPortNumber = Integer.parseInt(args[2]);
		int routerPortNumber = Integer.parseInt(args[3]);
		try
		{
			Terminal terminal = new Terminal("Host");
			(new Host(terminal, hostName, numHostsOnNetwork, hostPortNumber, routerPortNumber)).start();
		} catch (java.lang.Exception e) { e.printStackTrace(); }
	}
}
	
	
