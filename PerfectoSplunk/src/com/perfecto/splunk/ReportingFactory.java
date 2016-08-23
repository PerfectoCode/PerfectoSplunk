package com.perfecto.splunk;

//Creating the Thread local instance of SplunkReporting
public class ReportingFactory {
	
	private static ThreadLocal<SplunkReporting> reporting = new ThreadLocal<SplunkReporting>();

	public static SplunkReporting getReporting() {
		return reporting.get();
	}

	public static void setReporting(SplunkReporting report) {
		reporting.set(report);
	}
	
	public static SplunkReporting createInstance(long sla, String splunkHost, int splunkPort, String splunkScheme, String splunkToken, String splunkUser, String splunkPassword) {
		SplunkReporting reporting = new SplunkReporting(sla, splunkHost, splunkPort, splunkScheme, splunkToken, splunkUser, splunkPassword);
		return reporting;
	}
}
