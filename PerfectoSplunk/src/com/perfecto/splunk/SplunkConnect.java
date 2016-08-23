package com.perfecto.splunk;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import com.splunk.*;

//Class used to connect to splunk and submit the values to the index
public class SplunkConnect {

	private String splunkUser;
	private String splunkPass;
	private String splunkHost;
	private String splunkScheme;
	private int splunkPort;
	private String splunkToken;

	public String getSplunkUser() {
		return splunkUser;
	}

	public void setSplunkUser(String splunkUser) {
		this.splunkUser = splunkUser;
	}

	public String getSplunkPass() {
		return splunkPass;
	}

	public void setSplunkPass(String splunkPass) {
		this.splunkPass = splunkPass;
	}

	public String getSplunkHost() {
		return splunkHost;
	}

	public void setSplunkHost(String splunkHost) {
		this.splunkHost = splunkHost;
	}

	public String getSplunkScheme() {
		return splunkScheme;
	}

	public void setSplunkScheme(String splunkScheme) {
		this.splunkScheme = splunkScheme;
	}

	public int getSplunkPort() {
		return splunkPort;
	}

	public void setSplunkPort(int splunkPort) {
		this.splunkPort = splunkPort;
	}

	public String getSplunkToken() {
		return splunkToken;
	}

	public void setSplunkToken(String splunkToken) {
		this.splunkToken = splunkToken;
	}

	//Set the splunk instance connection values
	public SplunkConnect(String splunkHost, int splunkPort, String splunkScheme, String splunkToken, String splunkUser,
			String splunkPassword) {
		this.splunkUser = splunkUser;
		this.splunkPass = splunkPassword;
		this.splunkHost = splunkHost;
		this.splunkPort = splunkPort;
		this.splunkScheme = splunkScheme;
		this.splunkToken = splunkToken;
	}

	//Grabbing the index and submitting the value to splunk
	public void splunkFeed(String splunkValue, String index) throws Exception {
		HttpService.setSslSecurityProtocol(SSLSecurityProtocol.TLSv1_2);
		ServiceArgs serviceArgs = new ServiceArgs();
		serviceArgs.setUsername(splunkUser);
		serviceArgs.setPassword(splunkPass);
		serviceArgs.setHost(splunkHost);
		serviceArgs.setPort(splunkPort);
		serviceArgs.setToken(splunkToken);
		serviceArgs.setScheme(splunkScheme);

		//Connecting to the splunk instance
		Service service = Service.connect(serviceArgs);
		
		//Grabbing the index
		Index myIndex = null;
		try {
			myIndex = service.getIndexes().get(index);
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}

		//Setting the source and host information
		Args eventArgs = new Args();
		eventArgs.put("sourcetype", "PerfectoSeleniumExecution");
		try {
			eventArgs.put("host", InetAddress.getLocalHost().getHostName());
		} catch (UnknownHostException e1) {
			e1.printStackTrace();
		}
		
		//Submitting to the splunk index
		try {
			myIndex.submit(eventArgs, splunkValue);
		} catch (Exception e) {
			e.printStackTrace();
			if (myIndex==null)
			{
			throw new Exception("Splunk Index not found");
			}
			else
			{
				throw e;
			}
		}
	}
}