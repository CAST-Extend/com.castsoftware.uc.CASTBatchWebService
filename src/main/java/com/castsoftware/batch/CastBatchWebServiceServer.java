package com.castsoftware.batch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import javax.xml.ws.Endpoint;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import com.castsoftware.util.GlobalProperties;
import com.castsoftware.util.GlobalPropertiesManager;
import com.castsoftware.util.VersionInfo;

public class CastBatchWebServiceServer {
	
	private static final Logger logger = Logger.getLogger(CastBatchWebServiceServer.class);
	private static final GlobalProperties globalProperties = GlobalPropertiesManager.getGlobalProperties();
	
	private static CastBatchWebServiceServer  serviceInstance = new CastBatchWebServiceServer();
	private Endpoint ep;
	private Boolean stopped = false;
	
	public static VersionInfo getFullVersion()
	{
		String version = CastBatchWebServiceServer.class.getPackage().getSpecificationVersion();
		if (version == null)
			version = "1.4";
		int build = 0;
		try
		{
			build = Integer.parseInt(CastBatchWebServiceServer.class.getPackage().getImplementationVersion());
		} catch (NumberFormatException e) {
			logger.warn("Invalid build number");
		}
		
		return new VersionInfo(version, build);
	}
	
	public void start() {
		logger.info("Starting CastBatchWebServiceServer...");
		stopped = false;
		
		int port;
		try {
			port = Integer.parseInt(globalProperties.getPropertyValue("webservice.port"));
		} catch (NumberFormatException e) {
			logger.error("Invalid webservice.port", e);
			port = 9898;
		} catch (InterruptedException e) {
			port = 9898;
		} catch (IOException e) {
			port = 9898;
		}
		
		//Execute Init Batch if any
		String initBatch;
		try
		{
			initBatch = globalProperties.getPropertyValue("init.batch");
			if ((initBatch != null) && (!initBatch.equals("")))
			{
				logger.info(String.format("Execute Init Batch: %s", initBatch));
				ProcessBuilder pb = new ProcessBuilder(initBatch);
				pb.redirectErrorStream(true);
				Process p = pb.start();
				
				BufferedReader br = null;
				try
				{
					br = new BufferedReader(new InputStreamReader(p.getInputStream()));
					StringBuilder builder = new StringBuilder();
					String line = null;
					while ( (line = br.readLine()) != null) {
					   builder.append(line);
					   builder.append(System.getProperty("line.separator"));
					}
					logger.debug(builder.toString());
				} finally
				{
					if (br != null)
						br.close();
				}
				
				logger.debug(String.format("Waiting for Exit Value..."));		
				int exitVal = p.waitFor();	
				logger.debug(String.format("Exit Value: %d", exitVal));
			}
		} catch (InterruptedException | IOException e) {
			logger.error(e);
		}
			
		logger.info(String.format("About to start Web Service on port %d", port));
		//Start Web Service
		try {
			//0.0.0.0 listen on all adapters
			ep = Endpoint.publish(String.format("http://%s:%d/CastBatchWebService", "0.0.0.0", port), new CastBatchWebService());
			logger.info(String.format("CastBatchWebService service is ready and listening on port %d", port));
			
			while(!stopped)
			{
				synchronized(this) {
		            try {
		               this.wait(10000);  // wait 10 seconds
		            }
		            catch(InterruptedException ie){
		            	logger.error(ie);
		            }
		         }
		      }
			ep.stop();
			logger.info("CastBatchWebService stopped");
		} catch (Exception e) {
			logger.error(e);
		}
	}
	
	public void stop() {
		logger.info("Stopping CastBatchWebService...");
		stopped = true;
	    synchronized(this)
	    {
	    	this.notify();
	    }
	}
	
	public static void main(String[] args) throws InterruptedException 
	{
		PropertyConfigurator.configure("log4j.properties");
		logger.info("Log4j Initialized");
		logger.debug(args);
		logger.info(String.format("Version: %s build %s",
				CastBatchWebServiceServer.class.getPackage().getSpecificationVersion(),
				CastBatchWebServiceServer.class.getPackage().getImplementationVersion()));	
		globalProperties.setPropertyFile("CastAIPWS.properties");
		
		logger.info("Service Parameter:");
		for(String s : args)
			logger.info(String.format(" %s", s));

		 String cmd = "start";
	      if(args.length > 0) {
	         cmd = args[0];
	      }
		
	      if("start".equals(cmd)) {
	         serviceInstance.start();
	      }
	      else {
	         serviceInstance.stop();
	      }
	}
}

