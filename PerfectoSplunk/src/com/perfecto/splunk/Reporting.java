package com.perfecto.splunk;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

public abstract class Reporting {

	private long testexecutionstartmilli;
	private long testexecutionendmilli;
	public HashMap<String, Object> steps = new HashMap<String, Object>();
	public HashMap<String, Object> reporting = new HashMap<String, Object>();
	public HashMap<String, HashMap> reportingResults = new HashMap<String, HashMap>();
	public ArrayList<Object> stepCollector = new ArrayList<Object>();
	private long sla = 999999999;
	public SplunkConnect splunk = null;

	public Reporting(long sla, String splunkHost, int splunkPort, String splunkScheme, String splunkToken,
			String splunkUser, String splunkPassword) {
		setSla(sla);
		splunk = new SplunkConnect(splunkHost, splunkPort, splunkScheme, splunkToken, splunkUser, splunkPassword);
	}

	public long getSla() {
		return this.sla;
	}

	public void setSla(long sla) {
		this.sla = sla;
	}

	private long getTestExecutionStart() {
		return this.testexecutionstartmilli;
	}

	// sets test start time
	public void testExecutionStart() {
		this.testexecutionstartmilli = System.currentTimeMillis();
		this.reporting.put("testExecutionStart", new Date(this.testexecutionstartmilli));
	}

	private long getTestExecutionEnd() {
		return this.testexecutionendmilli;
	}

	// sets test end time and calculates the test duration
	public void testExecutionEnd() {
		this.testexecutionendmilli = System.currentTimeMillis();
		this.reporting.put("testExecutionEnd", new Date(getTestExecutionEnd()));
		this.reporting.put("testExecutionDuration", (this.getTestExecutionEnd() - this.getTestExecutionStart()) / 1000);
	}

	// sets the values for the start of a transaction
	public void startTransaction(String step, String text) {
		String status = "Fail";
		HashMap<String, Object> stepDetails = new HashMap<String, Object>();
		stepDetails.put("step", step);
		stepDetails.put("stepStatus", status);
		stepDetails.put("stepDescription", text);
		stepDetails.put("stepTimer", 0);
		stepDetails.put("stepStartTimestamp", new Date(System.currentTimeMillis()));
		stepCollector.add(stepDetails);
	}

	// sets the values for the end of the transaction and adds the values to the
	// step collector
	public void endTransaction(String step, Long time) throws Exception {
		Object stepDesription = "";
		Object stepStartTimestamp = "";
		String status = "";
		if (time != null) {
			if (time > this.getSla()) {
				status = "Fail";
			} else {
				status = "Pass";
			}
		}

		int initialSize = stepCollector.size();
		int finalSize = 0;
		for (Object object : stepCollector) {
			HashMap<String, Object> obj = (HashMap<String, Object>) object;

			if (obj.get("step").equals(step)) {
				stepDesription = obj.get("stepDescription");
				stepStartTimestamp = obj.get("stepStartTimestamp");
				stepCollector.remove(obj);
				finalSize = stepCollector.size();
				break;
			}
		}

		if (initialSize == finalSize) {
			throw new Exception("Transaction Not Found");
		} else {

			HashMap<String, Object> stepDetails = new HashMap<String, Object>();
			stepDetails.put("step", step);
			stepDetails.put("stepStatus", status);
			stepDetails.put("stepDescription", stepDesription);
			stepDetails.put("stepTimer", time);
			stepDetails.put("stepStartTimestamp", stepStartTimestamp);
			stepDetails.put("stepEndTimestamp", new Date(System.currentTimeMillis()));
			stepCollector.add(stepDetails);
		}
	}

	// override to allow setting of the SLA
	public void endTransaction(long sla, String step, Long time) throws Exception {
		setSla(sla);
		endTransaction(step, time);
	}

}
