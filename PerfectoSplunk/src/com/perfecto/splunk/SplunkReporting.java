package com.perfecto.splunk;

import java.net.Proxy;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class SplunkReporting extends Reporting {

	// Main method is set to allow exporting of jar with embedded jars
	public static void main(String[] args) {
	}

	// initializing splunk connection values
	public SplunkReporting(long sla, String splunkScheme, String splunkHost, String splunkPort,  String splunkToken) {
		super(sla, splunkScheme, splunkHost, splunkPort,  splunkToken);
	}
	
	public SplunkReporting(long sla, String splunkScheme, String splunkHost, String splunkPort,
			String splunkToken, Proxy proxy) {
		super(sla, splunkScheme, splunkHost, splunkPort, splunkToken, proxy);
	}

	// merges and the various maps to create the json and finally submit them to
	// splunk
	public String commitSplunk(String reportTitle, String testName) throws Exception {
		this.steps.put("Steps", this.stepCollector);
		Gson gson = new GsonBuilder().enableComplexMapKeySerialization().disableHtmlEscaping().serializeNulls()
				.create();
		gson = new GsonBuilder().enableComplexMapKeySerialization().disableHtmlEscaping().serializeNulls().create();

		HashMap<String, HashMap> methodDetails = new HashMap<String, HashMap>();
		methodDetails.put(testName, this.steps);

		this.reporting.put("methods", methodDetails);

		String stepsJson = gson.toJson(this.steps);
		if (stepsJson.contains("Fail")) {
			this.reporting.put("performanceStatus", "Fail");
		} else if (stepsJson.contains("Pass")) {
			this.reporting.put("performanceStatus", "Pass");
		}

		if (reportTitle != null) {
			this.reportingResults.put(reportTitle, this.reporting);
		} else {
			this.reportingResults.put("PerfectoTest", this.reporting);
		}

		// converts the maps to a final readable json string
		String jsonReport = gson.toJson(this.reportingResults);

		// submits the values to splunk unless the splunk host is Null
		// setting splunk host to null allows for the generation of the Json
		// without the need of connecting to splunk
		if (splunk.getSplunkHost() != null) {
			this.splunk.splunkFeed(jsonReport);
		}

		// returns the json string for logging or additional tasks
		return jsonReport;
	}

}
