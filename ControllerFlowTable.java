import java.util.ArrayList;

public class ControllerFlowTable {
	
	// Each router is given its own element on this ArrayList. Within this element is a list of 
	// all routers that this router is immediately connected to, and it's distance to that given router.
	// For the sake of simplicity, we assume that the distance between a host and its router is zero.
	// A router may have 0 or 1 hosts, but no more than that. 
	
	// I've set a rule that once a router has connected to the Controller and has signalled which routers
	// it is connected to, then this is set and cannot be changed, i.e. it cannot directly connected to
	// another router. However, it can discover routers it is not directly connected to later.
	
	static ArrayList<DirectConnectionsPerRouter> allRouters = new ArrayList<DirectConnectionsPerRouter>();
	
	// The following two ArrayLists makes it easy for us to track all the hosts that are connected to the 
	// network. Once a new router is added that is actually connected to a host, then we add just the name of the Host
	// to the allHostsNames ArrayList. The givenHostsRouter ArrayList works in conjunction with this. For 
	// any given index of allHostsName, the router connected to this host is in the same index in givenHostsRouter.
	// This will be useful for creating Flow Charts that we can send to the Routers when they request them.
	
	static ArrayList<String> allHostNames = new ArrayList<String>();
	static ArrayList<String> givenHostsRouter = new ArrayList<String>();
	
	public class DirectConnectionsPerRouter{
		// Each Router connected to the Controller is given it's own DirectConnectionsPerRouter class. This
		// will contain the router's name, and the host its connected to. 
		// Also within each router's DirectConnectionsPerRouter class, there is an array of DistancesBetweenRouters,
		// which is a simple class that has the name of the router it is connected to, and the distance to this router.
		
		String routerName;
		String connectedHost;
		public ArrayList<DistancesBetweenRouters> distances = new ArrayList<DistancesBetweenRouters>();
		
		public void addConnectionToNewRouter(String newRouterName, int newRouterDistance)
		{
			DistancesBetweenRouters newRouter = new DistancesBetweenRouters(newRouterName, newRouterDistance);
			distances.add(newRouter);
		}
	}
	
	public class DistancesBetweenRouters{
		String toRouter;
		int distance;
		
		DistancesBetweenRouters(String connectedRouterName, int distance)
		{
			toRouter = connectedRouterName;
			this.distance = distance;
		}
		
		public String getConnectedRouterName()
		{
			return toRouter;
		}
		
		public int getDistanceToRouter()
		{
			return distance;
		}
	}
	
	// The controller will call this method whenever a router sends it its feature request

	public void addANewRouter(String[] routerInformation)
	{
		DirectConnectionsPerRouter newRouter = new DirectConnectionsPerRouter();
		newRouter.routerName = routerInformation[0];  	// sets name of router
		newRouter.connectedHost = routerInformation[1];	// Name of the host connected to this router. If no host connected, this value is "00"
		
		// The controller doesn't need to know the actual socket ports that a router is connecting other 
		// routers through, it only needs to know the names of the Routers, and the distance between
		// the routers. The routers themselves can work out which sockets to send a packet to.
		
		// All subsequent elements on the routerInformation array are bundled together in twos, consisting of the
		// name of a router connected to new router, and the distance to this router.
		for(int i = 2; i < routerInformation.length; i = i + 2)
		{
			newRouter.addConnectionToNewRouter(routerInformation[i], Integer.parseInt(routerInformation[i+1]));
		}
		
		allRouters.add(newRouter);
		
		if(!(newRouter.connectedHost.equals("00")))		// If this router actually has a host
		{
			allHostNames.add(newRouter.connectedHost);
			givenHostsRouter.add(newRouter.routerName);
		}
	}
		
	public String updateFlowChartForRouter(String routerName)
	{
		// This is my implementation of Djikstra's Algorithm. We start by creating
		// three arrays, which will eventually fill to become the new Flow Table for
		// the router requesting one. We will call the router requesting a new flow 
		// table RouterReq. The first array is the name of the Routers that it can
		// connect to, either directly or through another Router (essentially a list
		// of all routers). The second array will keep track of the distance to a given 
		// router (the index of any given router is shared between the three arrays). 
		// The third will keep track of shortest path to this router (or more specifically,
		// the router that RouterReq should forward on the packet to in order to reach
		// this router).
		
		// The benefit of Djikstra's algorithm is that the controller does not have 
		// to keep a overall preconfigured table of all the hosts and how to access them.
		// We instead generate a flow table for each router that requires one, using the
		// routers that we are aware of. 
		
		ArrayList<String> shortestPathTreeRouterName = new ArrayList<String>();
		ArrayList<Integer> shortestPathTreeDistance = new ArrayList<Integer>();
		ArrayList<String> shortestPathOut = new ArrayList<String>();
		
		// Next, we create three ArrayLists that will serve as the list of tentative
		// nodes, with a similar structure as above.
		ArrayList<String> tentativeNodeName = new ArrayList<String>();
		ArrayList<Integer> tentativeNodeDistance = new ArrayList<Integer>();
		ArrayList<String> tentativePathOut = new ArrayList<String>();
		
		// To start, we find the router requesting a new flow table on our list
		// of allRouters
		
		DirectConnectionsPerRouter routerReq = null;
		
		for(DirectConnectionsPerRouter iterateRouter : allRouters)
		{
			if(iterateRouter.routerName.equals(routerName))
			{
				routerReq = iterateRouter;
				break;
			}
		}
		
		// If routerReq is null at this point, then the router has not yet
		// sent us their FeatureRequest, so there's not much we can do for them.
		if(routerReq == null) return null;
		
		// Now that we have the Router requesting a flow table, we must add it to the
		// top of our ShortestPath arrays, and make it our first permanent node.
		
		shortestPathTreeRouterName.add(routerReq.routerName);
		shortestPathTreeDistance.add(0);
		shortestPathOut.add(routerReq.routerName);
		
		// Now to start the algorithm. The first step of this is to get the routers directly 
		// connected to RoutReq, and add them to the list of tentative nodes in our algorithm.
		// In this case only, we will get the names of the routers directly connected to RoutReq
		// and create a short string array out of it. This will make it easier to create a flow table
		// that RouterReq can understand, but this will be used at a later part of this algorithm.
		
		ArrayList<String> directlyConnectedRouters = new ArrayList<String>();
		
		for(DistancesBetweenRouters iterationRouter : routerReq.distances)
		{
			tentativeNodeName.add(iterationRouter.toRouter);
			tentativeNodeDistance.add(iterationRouter.distance);
			tentativePathOut.add(iterationRouter.toRouter);
			
			directlyConnectedRouters.add(iterationRouter.toRouter);
		}
		
		// We constantly have to keep track of the element that was last placed on the permanent
		// shortest path list. We'll set this as routerReq to start, and continuously update it within
		// the following while-loop.
		
		DirectConnectionsPerRouter previousPermanentElement = routerReq;
		
		while(tentativeNodeName.size() > 0)
		{
			int minimumDistance = 999999;
			int minValIndex = -1;
			
			// First find the element on the tentative list with the shortest distance.
			
			for(int index = 0; index < tentativeNodeName.size(); index++)
			{
				if(tentativeNodeDistance.get(index) < minimumDistance)
				{
					minimumDistance = tentativeNodeDistance.get(index);
					minValIndex = index;
				}
			}
					
			// We now add this element to the permanent Flow Table
			
			shortestPathTreeRouterName.add(tentativeNodeName.get(minValIndex));
			shortestPathTreeDistance.add(tentativeNodeDistance.get(minValIndex));
			shortestPathOut.add(tentativePathOut.get(minValIndex));
			
			// The next step before we can continue on to the next iteration of this while-loop
			// is to grab the last router that we just put on the permanent list out of our 
			// ArrayList of allRouters, then add all of *this* routers connections to our tentative list. 
			
			String lastName = tentativeNodeName.get(minValIndex);
			boolean valueChanged = false;
			for(DirectConnectionsPerRouter iterationRouter : allRouters)
			{
				if(iterationRouter.routerName.equals(lastName))
				{
					previousPermanentElement = iterationRouter;
					valueChanged = true;
					break;
				}
			}
			
			// If a router that is to be added to the tentative list is already on the tentative list,
			// then we have to compare the distances and determine which is shorter.
			
			// First though, we need to correct the router Access (Path Out). Let's say that RouterReq is 
			// connected to R2, and R2 to R3, and R3 to R4. If we are at a point that we are adding 
			// R4 to the permanent shortest path list, RouterReq needs to know how to access R4, 
			// i.e. which router it needs to forward packets to. So if we represented the routers 
			// directly connected to RoutReq as [R2], and the access to R4 as [R3], then we need
			// to switch [R3] with [R2]. This can be achieved by checking if the access value is
			// in the array directlyConnectedRouters, that I briefly mentioned above. In other words,
			// we need to find the access to R3, which would be R2, which is actually connected to R1.
			// We declare the string pathOut, which will carry over each time.
			
			String pathOut = tentativePathOut.get(minValIndex);
			
			if(valueChanged)
			{
				for(DistancesBetweenRouters iterationRouter : previousPermanentElement.distances)
				{
					if(shortestPathTreeRouterName.contains(iterationRouter.toRouter))
					{
						// just a check to make sure that we aren't adding an element that is already
						// on our permanent list back onto the tentative list.
					}
					else if(tentativeNodeName.contains(iterationRouter.toRouter))
					{
						int comparisonIndex = tentativeNodeName.indexOf(iterationRouter.toRouter);
						if(tentativeNodeDistance.get(comparisonIndex) > (iterationRouter.distance + tentativeNodeDistance.get(minValIndex)))
						{
							// iterationRouter.distance + tentativeNodeDistance.get(minValIndex) is the sum of the distance between 
							// the tentative element/previously permanent router and the distance between the previously permanent 
							// router/RoutReq.
							
							tentativeNodeDistance.set(comparisonIndex, iterationRouter.distance + tentativeNodeDistance.get(minValIndex));
							tentativePathOut.set(comparisonIndex, pathOut);
						}
						//else leave it alone
					}
					else	// if the router is not present on the tentative list
					{
						tentativeNodeName.add(iterationRouter.toRouter);
						tentativeNodeDistance.add(iterationRouter.distance + tentativeNodeDistance.get(minValIndex));
						tentativePathOut.add(pathOut);
					}
				}
			}
			
			// Finally, remove the last element added to the permanent list from our tentative list,
			// and continue on to the next iteration of our while-loop.
			tentativeNodeName.remove(minValIndex);
			tentativeNodeDistance.remove(minValIndex);
			tentativePathOut.remove(minValIndex);
			
		}
		
		// Once we have our flow table, we need to introduce the actual hosts, and then convert all of this
		// into a form that the router can actually work with.
		
		// Distance becomes irrelevant here, as the router trusts that the controller has made
		// the correct calculations on distance thanks to the algorithm. The router only needs 
		// to know two things from this: the names of all the hosts, and the router it needs to 
		// forward the packet on to in order to eventually reach this destination host.
		
		// The contents of a FLWMD string being sent to a router will have the following structure:
		// [host1Name][routerToAccessHost1][host2Name][routerToAccessHost2][host3Name][routerToAccessHost3] etc...
		
		// In other words, the router will get the structure like this:
		// [HOST][ROUTER OUT]
		//	H1		R2
		//  H2		R3
		//  H3		R2
		
		String finalFlowChartAsString = "";
		
		for(int i = 0; i < allHostNames.size(); i++)
		{
			String hostName = allHostNames.get(i);
			String routerConnectedToHost = givenHostsRouter.get(i);
			int indexOnFlowChart = shortestPathTreeRouterName.indexOf(routerConnectedToHost);
			if(indexOnFlowChart < shortestPathOut.size() && indexOnFlowChart >= 0)
				finalFlowChartAsString += hostName + shortestPathOut.get(indexOnFlowChart);
		}
		
		System.out.println("NEW FLOW TO " + routerReq.routerName + ": " + finalFlowChartAsString);
		return finalFlowChartAsString;
	}
	
}


 
