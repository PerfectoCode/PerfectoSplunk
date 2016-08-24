package com.perfecto.splunk;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

//Class used to connect to splunk and submit the values to the index
public class SplunkConnect {

	private String splunkScheme;
	private String splunkHost;
	private String splunkPort;
	private String splunkToken;
	private Proxy proxy = null;

	private final String UTF_8 = "UTF-8";

	public enum availableSchemes {
		http, https
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

	public String getSplunkPort() {
		return splunkPort;
	}

	public void setSplunkPort(String splunkPort) {
		this.splunkPort = splunkPort;
	}

	public String getSplunkToken() {
		return splunkToken;
	}

	public void setSplunkToken(String splunkToken) {
		this.splunkToken = splunkToken;
	}

	// Set the splunk instance connection values
	public SplunkConnect(String splunkScheme, String splunkHost, String splunkPort, String splunkToken) {
		this.splunkScheme = splunkScheme;
		this.splunkHost = splunkHost;
		this.splunkPort = splunkPort;
		this.splunkToken = splunkToken;
	}

	// Set the splunk instance connection values
	public SplunkConnect(String splunkScheme, String splunkHost, String splunkPort, String splunkToken, Proxy proxy) {
		this.splunkScheme = splunkScheme;
		this.splunkHost = splunkHost;
		this.splunkPort = splunkPort;
		this.splunkToken = splunkToken;
		this.proxy = proxy;
	}

	public void main(String[] args) throws Exception {
	}

	public boolean isJSONValid(String jsonInString) {
		try {
			final ObjectMapper mapper = new ObjectMapper();
			mapper.readTree(jsonInString);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	public String splunkFeed(String value) throws Exception {

		if (!isJSONValid(value)) {
			throw new Exception("Invalid JSON String, please correct and try again");
		}

		URL obj;
		if (splunkPort != null) {
			obj = new URL(splunkScheme.toString() + "://" + splunkHost + ":" + splunkPort + "/services/collector");
		} else {
			obj = new URL(splunkScheme.toString() + "://" + splunkHost + "/services/collector");
		}

		try {
			HttpURLConnection con = null;
			if (proxy != null) {
				con = (HttpURLConnection) obj.openConnection(proxy);
			} else {
				con = (HttpURLConnection) obj.openConnection();
			}
			con.setRequestProperty("Content-Type", "application/json");
			con.setDoOutput(true);

			con.setRequestProperty("Authorization", "Splunk " + splunkToken);

			con.setRequestMethod("POST");

			OutputStreamWriter out = new OutputStreamWriter(con.getOutputStream());
			out.write("{\"event\":" + value + "}");
			out.close();

			new InputStreamReader(con.getInputStream());

			int responseCode = con.getResponseCode();
			String response = "";

			if (responseCode > HttpURLConnection.HTTP_OK) {
				handleError(con);
			} else {
				response = getStream(con);
			}

			System.out.println("\nSending 'GET' request to URL : " + obj.toURI());
			System.out.println("Response Code : " + responseCode);
			System.out.println("Response message: " + response.toString());

			return response;
		} catch (UnknownHostException ex) {
			throw new Exception(ex.getMessage() + "\nHostname is invalid please correct and try again.");
		} catch (Exception ex) {
			if (ex.getMessage().contains("Unexpected end of file")) {
				throw new Exception(ex.getMessage() + "\nMost likely cause is the scheme or the port number is wrong.");
			}

			if (ex.getMessage().contains("Connection timed out")) {
				throw new Exception(ex.getMessage()
						+ "\nMost likely cause is the scheme or the port number is wrong. You may also have firewall or a proxy blocking your attempt.");
			}

			if (ex.getMessage().contains("Server returned HTTP response code: 403")) {
				throw new Exception(ex.getMessage()
						+ "\nMost likely cause is invalid credentials or and invalid HTTP Event Collector Token");
			}

			throw ex;
		}
	}

	private void handleError(HttpURLConnection connection) throws IOException {
		String msg = "Failed to upload media.";
		InputStream errorStream = connection.getErrorStream();
		if (errorStream != null) {
			InputStreamReader inputStreamReader = new InputStreamReader(errorStream, UTF_8);
			BufferedReader bufferReader = new BufferedReader(inputStreamReader);
			try {
				StringBuilder builder = new StringBuilder();
				String outputString;
				while ((outputString = bufferReader.readLine()) != null) {
					if (builder.length() != 0) {
						builder.append("\n");
					}
					builder.append(outputString);
				}
				String response = builder.toString();
				msg += "Response: " + response;
			} finally {
				bufferReader.close();
			}
		}
		throw new RuntimeException(msg);
	}

	private String getStream(HttpURLConnection connection) throws IOException {
		InputStream inputStream = connection.getInputStream();
		InputStreamReader inputStreamReader = new InputStreamReader(inputStream, UTF_8);
		BufferedReader bufferReader = new BufferedReader(inputStreamReader);
		String response = "";
		try {
			StringBuilder builder = new StringBuilder();
			String outputString;
			while ((outputString = bufferReader.readLine()) != null) {
				if (builder.length() != 0) {
					builder.append("\n");
				}
				builder.append(outputString);
			}
			response = builder.toString();
		} finally {
			bufferReader.close();
		}
		return response;
	}
}
