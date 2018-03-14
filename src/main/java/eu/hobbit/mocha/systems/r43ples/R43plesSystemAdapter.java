/**
 * 
 */
package eu.hobbit.mocha.systems.r43ples;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFactory;
import org.apache.jena.query.ResultSetFormatter;
import eu.hobbit.mocha.systems.r43ples.util.Constants;
import org.hobbit.core.components.AbstractSystemAdapter;
import org.hobbit.core.rabbit.RabbitMQUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
		
		
	}

	/* (non-Javadoc)
	 * @see org.hobbit.core.components.TaskReceivingComponent#receiveGeneratedTask(java.lang.String, byte[])
	 */
	public void receiveGeneratedTask(String tId, byte[] data) {
		LOGGER.info("Task " + tId + " received from task generator");
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
