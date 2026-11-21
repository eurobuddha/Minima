package org.minima.system.network.rpc;

public abstract class Server {

	//What Port are we running on
	protected int mPort = 0;
	
	public Server(int zPort) {
		mPort = zPort;
	}
	
	public int getPort() {
		return mPort;
	}
    
	public abstract void shutdown();
	
	/** Is the server socket open and accepting? */
	public abstract boolean isListening();
	
	/** Why the server did not start, or null */
	public abstract String getStartError();
}
