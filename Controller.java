import java.net.DatagramSocket;
import java.io.IOException;
import java.net.DatagramPacket;
import java.util.ArrayList;
import java.util.Timer;

import tcdIO.*;

/**
 *
 * Controller class
 * 
 * An instance accepts user input 
 *
 */
public class Controller extends Node {
	static final String DEFAULT_DST_NODE = "localhost";	
	static final int Controller_SRC_PORT = 50000;
	static ArrayList<NodeData> connectedRouters = new ArrayList<NodeData>();
	static ControllerFlowTable controllerFlowTable = new ControllerFlowTable();
	
	Terminal terminal;
	
	Controller(Terminal terminal, String dstHost, int srcPort) {
		try 
		{
			this.terminal = terminal;
			socket = new DatagramSocket(srcPort);
			listener.go();
		}
		catch(java.lang.Exception e) { e.printStackTrace(); }
	}

	public synchronized void onReceipt(DatagramPacket receivedPacket) {
		// First we need to find the router that this packet was delivered from
		// so we can communicate back later
		int portDeliveredFrom = receivedPacket.getPort();
		NodeData nodeDeliveredFrom = findNode(portDeliveredFrom);
		
		// First check its an ACK, else it is a SND		
		ACKContent potentialACK = new ACKContent(receivedPacket);
		if(potentialACK.isValidACK())
			acceptACKs(nodeDeliveredFrom, potentialACK.getACKNumber());
		else
		{
			SNDContent newPacket = new SNDContent(receivedPacket);
			
			if(newPacket.isValid() && newPacket.getContentType().equals("HELLO"))
				registerNewRouter(receivedPacket);
			else if(newPacket.isValid() && newPacket.getPacketNumber() == nodeDeliveredFrom.getNextExpectedPackNum())
			{
				// Immediately send ACK
				nodeDeliveredFrom.incrementNextExpectedPackNum();
				sendACK(nodeDeliveredFrom);
				
				if(newPacket.getContentType().equals("FETRP"))
					generateRoutersConnectionsFromFeatureReply(nodeDeliveredFrom, newPacket);
				else if(newPacket.getContentType().equals("PACIN"))
					createNewFlowTableForRouter(nodeDeliveredFrom);
			}
			else 
			{		
				// Send an ACK with next expected packet number if this node 
				// has already sent us this packet. We don't increment the 
				// next expected packet number here.
				if(nodeDeliveredFrom != null) sendACK(nodeDeliveredFrom);
			}
		}
		this.notify();
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
	
	private void generateRoutersConnectionsFromFeatureReply(NodeData nodeDeliveredFrom, SNDContent featureReplyFromRouter)
	{
		// When the router sends us its list of features (i.e. connected host and connected routers), this
		// will be in the form of a string. We must convert it back into an Array before passing it as
		// a parameter to the Flow Table's addANewRouterMethod.
		
		String routerFeatures = featureReplyFromRouter.getPacketContent();
		String[] routerInformation = new String[routerFeatures.length()/2];
		int arrayIndex = 0;
		for(int i = 0; i < routerFeatures.length(); i = i + 4)
		{
			routerInformation[arrayIndex++] = routerFeatures.substring(i, i + 2);
			routerInformation[arrayIndex++] = routerFeatures.substring(i + 2, i + 4);
		}
		
		controllerFlowTable.addANewRouter(routerInformation);
		terminal.println("FeatureReply received from " + routerInformation[0]);
	}
	
	private void registerNewRouter(DatagramPacket receivedPacket)
	{
		SNDContent content = new SNDContent(receivedPacket);
		
		// If the Controller doesn't recognise this node, we have to register it as a connectedRouter
	
		// create a new NodeData and add it to the list of connected routers. The packet content here is just 
		// the name of the router saying hello.
		NodeData newRouter = new NodeData(socket, receivedPacket.getPort(), "ROUTER", content.getPacketContent(), 0);
		connectedRouters.add(newRouter);
		newRouter = connectedRouters.get(connectedRouters.size()-1);
		terminal.println("Router " + newRouter.getNodeName() + " says hello!");
		
		// Send ACK back
		newRouter.incrementNextExpectedPackNum();
		sendACK(newRouter);
		
		// Send "Hello" back to router
		SNDContent sayHelloBack = new SNDContent("SND00HELLO0000" + '\u0003');
		newRouter.sendPacket(sayHelloBack);
		
		// Then send a feature request to the Router. The router will
		// send back a Feature Reply, which will name the host it is connected
		// to, the routers it is connected to, and the distances.
		
		SNDContent featureRequest = new SNDContent("SND00FETRQ0000" + '\u0003');
		newRouter.sendPacket(featureRequest);
		terminal.println("Sending FeatureRequest to " + newRouter.getNodeName());
	}
	
	private void createNewFlowTableForRouter(NodeData nodeDeliveredFrom)
	{
		String routerName = nodeDeliveredFrom.getNodeName();
		terminal.println(routerName + " has requested a new Flow Table. Finding shortest routes...");
		
		String updatedFlowChart = controllerFlowTable.updateFlowChartForRouter(routerName);
		SNDContent FLWMDToRouter = new SNDContent("SND00FLWMD0000" + updatedFlowChart + '\u0003');
		if(updatedFlowChart != null)
		{
			nodeDeliveredFrom.sendPacket(FLWMDToRouter);
			terminal.println("New flow chart created and sent to Router " + routerName);
		}
		else
			terminal.println("Cannot produce flow chart for " + routerName + ", waiting for FeatureReply");
	}
	
	
	public NodeData findNode(int portDeliveredFrom)
	{
		for(NodeData iterationRouter : connectedRouters)
		{
			if(iterationRouter.getDSTPort() == portDeliveredFrom)
				return iterationRouter;
		}
		return null;
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
	
	public synchronized void start() throws Exception {		
		terminal.println("Controller (PORT " + Controller_SRC_PORT + "):");
		terminal.println("Awaiting Routers...");
		
		while(true)
		{
			// Nothing for Controller to do here, so stay in loop of this.wait()
			this.wait();
		}
	}

	public static void main(String[] args) {
		try {					
			Terminal terminal = new Terminal("Controller");		
			(new Controller(terminal, DEFAULT_DST_NODE, Controller_SRC_PORT)).start();
		} catch(java.lang.Exception e) {e.printStackTrace();}
	}
}
