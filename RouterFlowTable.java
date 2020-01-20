import java.util.ArrayList;

public class RouterFlowTable {
	
	static ArrayList<String> allHostNames = new ArrayList<String>();
	static ArrayList<HostAndAccess> routerFlowTable = new ArrayList<HostAndAccess>();
	
	public class HostAndAccess{
		String hostName;
		String accessRouter;
		
		HostAndAccess(String hostName, String accessRouter)
		{
			this.hostName = hostName;
			this.accessRouter = accessRouter;
		}
	}
	
	public void updateFlowChart(String updatedFlowString)
	{
		// This method is called when the controller sends a router a Flow Modification packet.
		// While this would normally involve just appending a new entry to the router's
		// flow table, in my program it's easier and takes very little time to just start the router's
		// flow table from scratch every time, since we're just getting a series of substrings from the
		// controllers packet. There is also the case that, if the router knows about a host already, 
		// but the controller has discovered an even faster route due to new routers on the network, 
		// then this router will instead use that new route. This wouldn't be possible if we were to 
		// simply append to the flow table.
		
		allHostNames.clear();
		routerFlowTable.clear();
		
		for(int i = 0; i < updatedFlowString.length(); i = i + 4)
		{
			String hostName = updatedFlowString.substring(i, i+2);
			String accessRouter = updatedFlowString.substring(i+2, i+4);
			allHostNames.add(hostName);
			routerFlowTable.add(new HostAndAccess(hostName, accessRouter));
		}
	}
	
	public boolean isDestinationHostKnown(String hostName)
	{
		return allHostNames.contains(hostName);
	}
	
	public String getAccessRouter(String hostName)
	{
		for(HostAndAccess iteration : routerFlowTable)
		{
			if(iteration.hostName.equals(hostName))
				return iteration.accessRouter;
		}
		return null;
	}

}
