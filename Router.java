import java.net.DatagramSocket;
import java.io.IOException;
import java.net.DatagramPacket; 
import java.util.ArrayList;
import java.util.Timer;

import tcdIO.*;

/**
 *
 * Router class
 * 
 * In the project instructions, Routers were described as having 2 our more sockets.
 * The node class that I am using (from Assignment 1) is built around the assumption
 * that there is one socket, but it would be very time-consuming to make the changes
 * to that class to implement more than one socket variable for this given router,
 * which would ultimately have the same result in my program as just using a single 
 * socket that deals with all the communication. I understand the theory behind having 
 * multiple sockets, but as a router always creates a SNDContent from a received packet
 * in order to check its destination, then I can do a simple check to see if the destination
 * matches the host that is connected to this router, in which case it will send it to 
 * that host, or else it will check its flow chart to see which router it should forward
 * the packet to. This is effectively the same process as having multiple sockets.
 *
 */
public class Router extends Node {
	static int thisRouterSRCPort;
	static final String DEFAULT_DST_NODE = "localhost";
	static ArrayList<SNDContent> waitingToSend = new ArrayList<SNDContent>();
	static RouterFlowTable routerFlowTable;
	String flowRequestInformationToSendToController;
	String routerName;
	
	// For keeping track of the controller, host and all the routers directly connected to this router
	static ArrayList<NodeData> connectedRouters = new ArrayList<NodeData>();
	NodeData connectedController;
	NodeData connectedHost;
	
	boolean setupComplete;
	boolean hostReturnedHello;
	static Terminal terminal;

	Router(Terminal terminal, String[] routerArguments, int controllerPort) {
		try{
			Router.terminal = terminal;	
			setupComplete = false;
			hostReturnedHello = false;
			
			// The parameters of this router are the arguments included when running this program, in the form 
			// of a string array.
			routerName = routerArguments[0];
			thisRouterSRCPort = Integer.parseInt(routerArguments[1]);
			socket = new DatagramSocket(thisRouterSRCPort);
			routerFlowTable = new RouterFlowTable();
			
			// Create a node data for the controller
			connectedController = new NodeData(socket, controllerPort, "CONTROLLER", "CONTROLLER", 0);

			
			// We will start a string called informationToSendToController, which will consist of
			// the routers name, the name of the host connected to it, then a list of connected
			// routers and their distance to this router. We will continuously add to this
			// string over the course of this method.
			flowRequestInformationToSendToController = routerName;
			
			// Register the connected router as a NodeData. If the router is not connected to a host, 
			// then this value remains null
			if(!(routerArguments[2].equals("00")))
				connectedHost = new NodeData(socket, Integer.parseInt(routerArguments[3]), "HOST", routerArguments[2], 0);
			
			flowRequestInformationToSendToController += routerArguments[2];	// May be a Hosts name, or "00" if no host connected
			
			// We need to register each connected router as a NodeData. This is done by iterating through 
			// the arguments array. In our arguments, the the connected routers are written as
			// [router1name][router1port][router1distance][router2name][router2port][router2distance]...
			// So this for loop creates Node data under the assumption that the arguments are in this form.
			
			for(int i = 4; i < routerArguments.length; i = i + 3)
			{ 
				String connectedRouterName = routerArguments[i];
				int connectedRouterSocket = Integer.parseInt(routerArguments[i+1]);
				int connectedRouterDistance = Integer.parseInt(routerArguments[i+2]);
				connectedRouters.add(new NodeData(socket, connectedRouterSocket, "ROUTER", connectedRouterName, connectedRouterDistance));
				
				// Append the connected router's name and distance to our informationToSendToController string
				flowRequestInformationToSendToController += 
						connectedRouterName + ((connectedRouterDistance < 10) ? "0" + connectedRouterDistance : connectedRouterDistance);
			}
			
			listener.go();
		} 
		catch (java.lang.Exception e) { e.printStackTrace(); }
	}

	// Assume that incoming packets contain a String, create PacketContent which
	// sets the variables.

	public synchronized void onReceipt(DatagramPacket receivedPacket) {
		
		// First we need to find the router that this packet was delivered from
		// so we can communicate back later
		int portDeliveredFrom = receivedPacket.getPort();
		NodeData nodeDeliveredFrom = findNode(portDeliveredFrom);
		
		// First check its an ACK, else it is a SND		
		ACKContent potentialACK = new ACKContent(receivedPacket);
		
		// First we have to check the case that the packet is from our connected host
		if(potentialACK.isValidACK())
			acceptACKs(nodeDeliveredFrom, potentialACK.getACKNumber());
		else
		{
			SNDContent newPacket = new SNDContent(receivedPacket);
			
			if(newPacket.isValid() && nodeDeliveredFrom != null)
			{
				if(newPacket.getPacketNumber() == nodeDeliveredFrom.getNextExpectedPackNum())
				{
					// Immediately send ACK back
					nodeDeliveredFrom.incrementNextExpectedPackNum();
					sendACK(nodeDeliveredFrom);
					
					if (nodeDeliveredFrom.getNodeType().equals("CONTROLLER")) // if new Job Listing
						processControllerInstruction(newPacket);
					else if(nodeDeliveredFrom.getNodeType().equals("ROUTER"))
						processRouterInstruction(newPacket);
					else if(nodeDeliveredFrom.getNodeType().equals("HOST"))
						processHostInstruction(newPacket);
				}
				else
				{
					// Send an ACK with next expected packet number if this node 
					// has already sent us this packet. We don't increment the 
					// next expected packet number here.
					if(nodeDeliveredFrom != null) sendACK(nodeDeliveredFrom);
				}
			}

		}

		this.notify();
}
	
	public NodeData findNode(int portDeliveredFrom)
	{
		if(connectedHost != null)
		{
			if(portDeliveredFrom == connectedHost.getDSTPort())
			return connectedHost;
		}
		
		if(portDeliveredFrom == connectedController.getDSTPort())
			return connectedController;
		
		for(NodeData iterationRouter : connectedRouters)
		{
			if(iterationRouter.getDSTPort() == portDeliveredFrom)
				return iterationRouter;
		}
		return null;
	}
	
	private void acceptACKs(NodeData nodeDeliveredFrom, int latestACK) {
		// Decrement ACK Packet Number by 1
		int iterationACK = (15 + latestACK) % 16;

		// find last timer placed in Go-Back-N Window
		Timer packetTimerIteration = nodeDeliveredFrom.goBackNWindow[iterationACK];
		
		// until we reach the null element...
		while (packetTimerIteration != null) 
		{
			// cancel timer and nullify it on array
			packetTimerIteration.cancel(); 
			nodeDeliveredFrom.goBackNWindow[iterationACK] = null;
			
			// Continue the while loop, so we can also ACK all previous messages to this router
			packetTimerIteration = nodeDeliveredFrom.goBackNWindow[iterationACK];
			nodeDeliveredFrom.goBackNWindowSize--;
			
			// equation for cycling backwards through a cyclical array of size 16
			iterationACK = (15 + iterationACK) % 16;
		}
	}
	
	private void sendACK(NodeData nodeDeliveredFrom)
	{
		// ACKs are typically only sent once, so we do not have to designate a timer
		// to them, and we do not have to add them to our Go-Back-N window.
		ACKContent newACK = new ACKContent(nodeDeliveredFrom.getNextExpectedPackNum());
		DatagramPacket ackPacket = newACK.toDatagramPacket(); 
		ackPacket.setSocketAddress(nodeDeliveredFrom.getDstAddress()); 
		try {
			socket.send(ackPacket);
		} catch (IOException e) { e.printStackTrace(); }; 
	}
	
	public void forwardPacket(SNDContent packetToForward, String destinationHost)
	{
		if(connectedHost != null)	
		{
			if(destinationHost.equals(connectedHost.getNodeName()))
			{	// For the specific case that we need to forward this packet to our connected host
				forwardToHost(packetToForward);
				return;
			}
		}

		String accessRouterName = routerFlowTable.getAccessRouter(destinationHost);
		NodeData nextRouter = null;
		for(NodeData iterationRouter : connectedRouters)
		{
			if(iterationRouter.getNodeName().equals(accessRouterName))
			{
				nextRouter = iterationRouter;
				break;
			}
		}
			
		if(nextRouter != null)
		{
			nextRouter.sendPacket(packetToForward);
			terminal.println(routerName + ": Packet forwarded to " + nextRouter.getNodeName() 
				+ " (Src: " + packetToForward.getSourceHostName() +
				", Dst: " + packetToForward.getDestinationHostName() 
				+ ", Content: " + packetToForward.getPacketContent() +")");
		}
	}
	
	public void processControllerInstruction(SNDContent newPacket)
	{
		if(newPacket.getContentType().equals("HELLO"))
		{
			terminal.println(routerName + ": Controller said hello back!");
		}
		else if(newPacket.getContentType().equals("FETRQ"))
		{
			terminal.println("Controller is requesting a FeatureReply...");
			SNDContent featureReply = new SNDContent("SND00FETRP0000" 
							+ flowRequestInformationToSendToController + '\u0003');
			connectedController.sendPacket(featureReply);
			terminal.println(routerName + ": FeatureReply sent, setup complete.");
			setupComplete = true;
			
			// Now we must send a "Hello" to the host, so they know they can 
			// start sending strings
			SNDContent helloToHost = new SNDContent("SND00HELLO0000" + '\u0003');
			if(connectedHost != null) connectedHost.sendPacket(helloToHost);
			
		}
		else if(newPacket.getContentType().equals("FLWMD"))
		{
			if(newPacket.getPacketContent() != null)
			{
				routerFlowTable.updateFlowChart(newPacket.getPacketContent());
				terminal.println(routerName + ": Controller has updated our flow table!");
			}
		}
	}
	
	public void processRouterInstruction(SNDContent newPacket)
	{
		// Routers will receive only "PACIN" packet content types from other routers
		if(newPacket.getContentType().equals("PACIN"))
		{
			String destinationHost = newPacket.getDestinationHostName();
				
			// If we know about the destination host (judging by our flow table), then
			// we can send the packet on. Otherwise, we must request a new flow table
			// from the Controller.
			
			if(routerFlowTable.isDestinationHostKnown(destinationHost))
				forwardPacket(newPacket, destinationHost);						
			else
			{
				terminal.println(routerName + ": Requesting flow modification from Controller...");
				SNDContent requestFlowMod = new SNDContent("SND00PACIN0000" + '\u0003');
				connectedController.sendPacket(requestFlowMod);
				waitingToSend.add(newPacket);
			}
		}
	}
	
	public void processHostInstruction(SNDContent newPacket)
	{
		// Routers will also received only "PACIN" packets from hosts
		if(newPacket.getContentType().equals("PACIN"))
		{
			terminal.println(routerName + ": Packet received from " + connectedHost.getNodeName() + ", attempting to send to " + newPacket.getDestinationHostName());
			if(routerFlowTable.isDestinationHostKnown(newPacket.getDestinationHostName()))
				forwardPacket(newPacket, newPacket.getDestinationHostName());	
			else
			{
				SNDContent requestFlowMod = new SNDContent("SND00PACIN0000" + '\u0003');
				connectedController.sendPacket(requestFlowMod);
				waitingToSend.add(newPacket);
			}
		}
		else if(newPacket.getContentType().equals("HELLO"))
			hostReturnedHello = true;
	}
	
	public void forwardToHost(SNDContent content)
	{
		if(hostReturnedHello)	// if we know that the host is available
		{
			connectedHost.sendPacket(content);
			terminal.println(routerName + ": Packet DELIVERED to " + connectedHost.getNodeName() 
			+ " (Src: " + content.getSourceHostName() +
			", Dst: " + content.getDestinationHostName() 
			+ ", Content: " + content.getPacketContent() +")");
		}
		else
		{
			terminal.println(routerName + ": Packet received for " + connectedHost.getNodeName() + ", but "
					+ "this host is not connected. Packet discarded");
		}
	}
	
	public synchronized void start() throws Exception {		
		SNDContent helloToController = new SNDContent("SND00HELLO0000" + routerName + '\u0003');
		terminal.println(routerName + ": Saying hello to Controller...");
		connectedController.sendPacket(helloToController);
		ArrayList<SNDContent> removable = new ArrayList<SNDContent>();
		while(true) 
		{
			if(setupComplete)
			{
				// Will iterate over the packets that we previously were not able to send,
				// and see if our flow table has been updated to include the destination host
				
				for(SNDContent recheckingPacket : waitingToSend)
				{
					if(routerFlowTable.isDestinationHostKnown(recheckingPacket.getDestinationHostName()))
					{
						forwardPacket(recheckingPacket, recheckingPacket.getDestinationHostName());
						removable.add(recheckingPacket);
					}
				}
				for(SNDContent removePacket : removable)
					waitingToSend.remove(removePacket);
				removable.clear();
			}
			this.wait();
		}
	}
	
	public static void main(String[] args) {
		try 
		{
			String [] routerArguments = args;
			Terminal terminal1 = new Terminal("Router");
			(new Router(terminal1, routerArguments, 50000)).start();
		} catch (java.lang.Exception e) { e.printStackTrace(); }
	}
}
	
	
