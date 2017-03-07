package com.perfecto.splunk;

import java.net.Proxy;
import java.util.ArrayList;
import java.util.HashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class SplunkReportingCollector extends Reporting {
	public static ArrayList<HashMap> reportCollector = new ArrayList<HashMap>();
	public HashMap<String, Object> reportFinal = new HashMap<String, Object>();

	public void addReport(Object o) {
		reportCollector.add((HashMap) o);
	}

	// initializing splunk connection values
	public SplunkReportingCollector(long sla, String splunkScheme, String splunkHost, String splunkPort,
			String splunkToken) {
		super(sla, splunkScheme, splunkHost, splunkPort, splunkToken);
	}
	
	public SplunkReportingCollector(long sla, String splunkScheme, String splunkHost, String splunkPort,
			String splunkToken, Proxy proxy) {
		super(sla, splunkScheme, splunkHost, splunkPort, splunkToken, proxy);
	}

	// merges and sorts the various maps to create the json and finally submit
	// them to splunk
	public String commitSplunk() throws Exception {
		int secondaryCount = 1;
		Gson gson = new GsonBuilder().enableComplexMapKeySerialization().disableHtmlEscaping().serializeNulls()
				.create();
		gson = new GsonBuilder().enableComplexMapKeySerialization().disableHtmlEscaping().serializeNulls().create();
		for (int i = 0; i < this.reportCollector.size(); i++) {
			String jsonCompare = gson.toJson(this.reportCollector.get(i));
			Object oldValue = this.reportCollector.get(i).get("PerfectoTest");

			if (i == (this.reportCollector.size() - 1)) {
				if (!reportFinal.containsKey("Primary")) {
					reportFinal.put("Primary", oldValue);
				} else {
					reportFinal.put("Secondary" + secondaryCount, oldValue);
					secondaryCount = secondaryCount + 1;
				}
			} else if (jsonCompare.contains("\"testStatus\":\"Fail\"")) {
				reportFinal.put("Secondary" + secondaryCount, oldValue);
				secondaryCount = secondaryCount + 1;
			} else if (jsonCompare.contains("\"testStatus\":\"Skip\"")) {
				reportFinal.put("Secondary" + secondaryCount, oldValue);
				secondaryCount = secondaryCount + 1;
			} else if (jsonCompare.contains("\"performanceStatus\":\"Fail\"")) {
				reportFinal.put("Secondary" + secondaryCount, oldValue);
				secondaryCount = secondaryCount + 1;
			} else if (!reportFinal.containsKey("Primary")) {
				reportFinal.put("Primary", oldValue);
			} else {
				reportFinal.put("Secondary", oldValue);
				secondaryCount = secondaryCount + 1;
			}
			
			if (jsonCompare.contains("\"testStatus\":\"Skip\"")) {
				reportFinal.put("Secondary" + secondaryCount, oldValue);
				secondaryCount = secondaryCount + 1;
			}
		}

		// converts the maps to a final readable json string
		String jsonReport = gson.toJson(reportFinal);

		// submits the values to splunk unless the splunk host is Null
		// setting splunk host to null allows for the generation of the Json
		// without the need of connecting to splunk
		if (splunk.getSplunkHost() != null) {
			this.splunk.splunkFeed(jsonReport);
		}

		// returns the json string for logging or additional tasks
		return jsonReport;
	}

	// stores the individual reports into the json collector
	public void submitReporting(String testName) {
		this.steps.put("Steps", this.stepCollector);
		Gson gson = new GsonBuilder().enableComplexMapKeySerialization().disableHtmlEscaping().serializeNulls()
				.create();

		HashMap<String, HashMap> methodDetails = new HashMap<String, HashMap>();
		methodDetails.put(testName, this.steps);

		this.reporting.put("methods", methodDetails);

		String stepsJson = gson.toJson(this.steps);
		if (stepsJson.contains("Fail")) {
			this.reporting.put("performanceStatus", "Fail");
		} else if (stepsJson.contains("Pass")) {
			this.reporting.put("performanceStatus", "Pass");
		}

		this.reportingResults.put("PerfectoTest", this.reporting);

		addReport(this.reportingResults);
	}
}
