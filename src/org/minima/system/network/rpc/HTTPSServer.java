package org.minima.system.network.rpc;

import java.io.IOException;
import java.net.BindException;
import java.security.KeyStore;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.minima.utils.MinimaLogger;
import org.minima.utils.ssl.SSLManager;
 
public abstract class HTTPSServer extends Server implements Runnable {
    
	private boolean mShutdown 	= false;
    
    volatile SSLServerSocket mSSLServerSocket = null;
    
    /** Why the server did not start, or null */
    volatile String mStartError = null;
    
    /** "" = every interface; else the one address to bind (the RPC uses GeneralParams.RPC_BIND_HOST) */
    String mBindHost = "";
    
    public HTTPSServer(int port){
        this(port, "");
    }
    
    public HTTPSServer(int port, String zBindHost){
        super(port);
        mBindHost = zBindHost == null ? "" : zBindHost;
    	
        //Run it..
		Thread tt = new Thread(this);
		tt.start();
    }
    
    @Override
    public void shutdown() {
    	try {
    		mShutdown = true;
			
    		if(mSSLServerSocket != null) {
    			mSSLServerSocket.close();
    		}
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
    
    public abstract Runnable getSocketHandler(SSLSocket zSocket);
	
    // Create the and initialize the SSLContext
    private SSLContext createSSLContext(){
        try{
        	
        	//Get the Key store
        	KeyStore keyStore = SSLManager.getSSLKeyStore();
        	
            //Get the Factory
            KeyManagerFactory keyManagerFactory = SSLManager.getSSLKeyFactory(keyStore);
            
            //Get the Key manager
            KeyManager[] km = keyManagerFactory.getKeyManagers();
             
            // Create trust manager
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);
            TrustManager[] tm = trustManagerFactory.getTrustManagers();
             
            // Initialise SSLContext
            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(km,  tm, null);
             
            return sslContext;
        } catch (Exception ex){
            ex.printStackTrace();
        }
         
        return null;
    }
     
    @Override
    public void run(){
        SSLContext sslContext = this.createSSLContext();
        if(sslContext == null) {
        	mStartError = "Cannot listen on "+(mBindHost.isEmpty() ? "*" : mBindHost)+":"+mPort+" - no SSL keystore";
        	MinimaLogger.log("SSL Server @ Port "+mPort+" not started: "+mStartError);
        	return;
        }
         
        try{
            // Create server socket factory
            SSLServerSocketFactory sslServerSocketFactory = sslContext.getServerSocketFactory();
             
            // Create server socket
            if(mBindHost.isEmpty()) {
            	mSSLServerSocket = (SSLServerSocket) sslServerSocketFactory.createServerSocket(this.mPort);
            }else {
            	mSSLServerSocket = (SSLServerSocket) sslServerSocketFactory.createServerSocket(this.mPort, 50, java.net.InetAddress.getByName(mBindHost));
            }
             
            MinimaLogger.log("SSL server started on port "+mPort);
            while(!mShutdown){
                
            	//Get the socket
            	SSLSocket sslSocket = (SSLSocket) mSSLServerSocket.accept();
                
            	//Get the Handler..
				Runnable handler = getSocketHandler(sslSocket);
				
				//Run in a new Thread
				Thread rpcthread = new Thread(handler, "Socket Handler @ "+getPort());
				rpcthread.setDaemon(true);
				rpcthread.start();
            }
        
        } catch (BindException e) {
			//Could not bind - port in use, or an address this machine does not have
			mStartError = "Cannot listen on "+(mBindHost.isEmpty() ? "*" : mBindHost)+":"+mPort+" - "+e.getMessage();
			MinimaLogger.log("SSL Server @ Port "+mPort+" not started: "+mStartError);
		
        } catch (Exception ex){
        	if(mSSLServerSocket == null) {
        		mStartError = "Cannot listen on "+(mBindHost.isEmpty() ? "*" : mBindHost)+":"+mPort+" - "+ex;
        	}
        	if(!mShutdown) {
        		MinimaLogger.log(ex);
        	}
        }
    }
    
    @Override
    public boolean isListening() {
    	return !mShutdown && mSSLServerSocket != null && !mSSLServerSocket.isClosed();
    }
    
    @Override
    public String getStartError() {
    	return mStartError;
    }
}