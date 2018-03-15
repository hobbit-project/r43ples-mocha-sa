/**
 * 
 */
package eu.hobbit.mocha.systems.r43ples;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFactory;
import org.hobbit.core.components.AbstractSystemAdapter;
import org.hobbit.core.rabbit.RabbitMQUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.hobbit.mocha.systems.r43ples.util.Constants;

/**
 * @author papv
 *
 */
public class R43plesSystemAdapter extends AbstractSystemAdapter {
		
	private static final Logger LOGGER = LoggerFactory.getLogger(R43plesSystemAdapter.class);
	
	private AtomicInteger totalReceived = new AtomicInteger(0);
	private AtomicInteger totalSent = new AtomicInteger(0);
	private Semaphore allVersionDataReceivedMutex = new Semaphore(0);
	
	// used to check if bulk loading phase has finished in  order to proceed with the querying phase
	private boolean dataLoadingFinished = false;
	private int loadingNumber = 0;
	private String datasetFolderName;

	@Override
    public void init() throws Exception {
		LOGGER.info("Initializing R43ples test system...");
        super.init();        
        datasetFolderName = "/r43ples/spvb_data/";
        File theDir = new File(datasetFolderName);
		theDir.mkdir();
		LOGGER.info("R43ples initialized successfully .");
    }

	/* (non-Javadoc)
	 * @see org.hobbit.core.components.TaskReceivingComponent#receiveGeneratedData(byte[])
	 */
	public void receiveGeneratedData(byte[] data) {
		ByteBuffer dataBuffer = ByteBuffer.wrap(data);
		String fileName = RabbitMQUtils.readString(dataBuffer);

		// read the data contents
		byte[] dataContentBytes = new byte[dataBuffer.remaining()];
		dataBuffer.get(dataContentBytes, 0, dataBuffer.remaining());
		
		if (dataContentBytes.length != 0) {
			FileOutputStream fos = null;
			try {
				if (fileName.contains("/")) {
					fileName = fileName.replaceAll("[^/]*[/]", "");
				}
				fos = new FileOutputStream(datasetFolderName + File.separator + fileName);
				IOUtils.write(dataContentBytes, fos);
				fos.close();
			} catch (FileNotFoundException e) {
				LOGGER.error("Exception while creating/opening files to write received data.", e);
			} catch (IOException e) {
				LOGGER.error("Exception while writing data file", e);
			}
		}
		
		if(totalReceived.incrementAndGet() == totalSent.get()) {
			allVersionDataReceivedMutex.release();
		}
	}

	/* (non-Javadoc)
	 * @see org.hobbit.core.components.TaskReceivingComponent#receiveGeneratedTask(java.lang.String, byte[])
	 */
	public void receiveGeneratedTask(String tId, byte[] data) {
		LOGGER.info("Task " + tId + " received from task generator");
		try {
			Thread.sleep(1000 * 60 * 60);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public String rewriteQuery(String queryType, String queryText) {
		String rewrittenQuery = "";
		String graphName = "http://test.com/r43ples";
		Pattern graphPattern = Pattern.compile("graph.version.(\\d+)>");
		
		Matcher graphMatcher = graphPattern.matcher(queryText);
		while (graphMatcher.find()) {
			int version = Integer.parseInt(graphMatcher.group(1));
			int revision = version + 2;
			queryText = queryText.replaceAll("<http://graph.version." + version + ">", "<" + graphName + "> REVISION \"" + revision + "\"");
		}
		try {
			rewrittenQuery = URLEncoder.encode(queryText, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			LOGGER.error("Error occured while trying to encode the query string", e);
		};
		return rewrittenQuery;
	}
	
	private void loadVersion(String graphURI) {
		LOGGER.info("Loading data on " + graphURI + "...");
		try {
			String scriptFilePath = System.getProperty("user.dir") + File.separator + "load.sh";
			String[] command = {"/bin/bash", scriptFilePath, datasetFolderName, graphURI};
			Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
			BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String line;
			while ((line = in.readLine()) != null) {
				LOGGER.info(line);		
			}
			p.waitFor();
			LOGGER.info(graphURI + " loaded successfully.");
			in.close();
		} catch (IOException e) {
            LOGGER.error("Exception while executing script for loading data.", e);
		} catch (InterruptedException e) {
            LOGGER.error("Exception while executing script for loading data.", e);
		}
	}
	
	private void serverRestart() {
		LOGGER.info("Restarting R43ples...");
		try {
			String scriptFilePath = System.getProperty("user.dir") + File.separator + "server_restart.sh";
			String[] command = {"/bin/bash", scriptFilePath };
			Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
			BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String line;
			while ((line = in.readLine()) != null) {
				LOGGER.info(line);		
			}
			p.waitFor();
			in.close();
			LOGGER.info("R43ples restarted successfully.");
		} catch (IOException e) {
            LOGGER.error("Exception while executing script for loading data.", e);
		} catch (InterruptedException e) {
            LOGGER.error("Exception while executing script for loading data.", e);
		}	
	}
	
	private ResultSet executeQuery(String taskId, String query) {
		LOGGER.info("Executing task " + taskId + "..." );
		ResultSet results = null;
		try {
			String scriptFilePath = System.getProperty("user.dir") + File.separator + "execute_query.sh";
			String[] command = {"/bin/bash", scriptFilePath, query};
			Process p = new ProcessBuilder(command).start();
			results = ResultSetFactory.fromJSON(p.getInputStream());
			p.waitFor();
			LOGGER.info("Task " + taskId + " executed successfully.");
		} catch (IOException e) {
            LOGGER.error("Exception while executing task " + taskId, e);
		} catch (InterruptedException e) {
            LOGGER.error("Exception while executing task " + taskId, e);
		}
		return results;
	}
	
	@Override
	public void receiveCommand(byte command, byte[] data) {
    	if (command == Constants.BULK_LOAD_DATA_GEN_FINISHED) {
    		ByteBuffer buffer = ByteBuffer.wrap(data);
            int numberOfMessages = buffer.getInt();
            boolean lastLoadingPhase = buffer.get() != 0;
   			LOGGER.info("Received signal that all data of version " + loadingNumber + " successfully sent from all data generators (#" + numberOfMessages + ")");

			// if all data have been received before BULK_LOAD_DATA_GEN_FINISHED command received
   			// release before acquire, so it can immediately proceed to bulk loading
   			if(totalReceived.get() == totalSent.addAndGet(numberOfMessages)) {
				allVersionDataReceivedMutex.release();
			}
			
			LOGGER.info("Wait for receiving all data of version " + loadingNumber + ".");
			try {
				allVersionDataReceivedMutex.acquire();
			} catch (InterruptedException e) {
				LOGGER.error("Exception while waitting for all data of version " + loadingNumber + " to be recieved.", e);
			}
			
			LOGGER.info("All data of version " + loadingNumber + " received. Proceed to the loading of such version.");
			loadVersion("http://graph.version." + loadingNumber);
			
			LOGGER.info("Send signal to Benchmark Controller that all data of version " + loadingNumber + " successfully loaded.");
			try {
				sendToCmdQueue(Constants.BULK_LOADING_DATA_FINISHED);
			} catch (IOException e) {
				LOGGER.error("Exception while sending signal that all data of version " + loadingNumber + " successfully loaded.", e);
			}
			File theDir = new File(datasetFolderName);
			for (File f : theDir.listFiles()) {
				f.delete();
			}
			loadingNumber++;
			dataLoadingFinished = lastLoadingPhase;
    	}
    	super.receiveCommand(command, data);
    }

	
	@Override
    public void close() throws IOException {
		LOGGER.info("Closing System Adapter...");
        // Always close the super class after yours!
        super.close();
		LOGGER.info("System Adapter closed successfully.");
    }
}
