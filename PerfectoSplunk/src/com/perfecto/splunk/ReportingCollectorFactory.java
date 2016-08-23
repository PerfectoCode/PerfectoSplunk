package com.perfecto.splunk;

//Creating the Thread local instance of SplunkReportingCollector
public class ReportingCollectorFactory {
	
	private static ThreadLocal<SplunkReportingCollector> reporting = new ThreadLocal<SplunkReportingCollector>();

	public static SplunkReportingCollector getCollector() {
		return reporting.get();
	}

	public static void setReporting(SplunkReportingCollector report) {
		reporting.set(report);
	}
	
	public static SplunkReportingCollector createInstance(long sla, String splunkHost, int splunkPort, String splunkScheme, String splunkToken, String splunkUser, String splunkPassword) {
		SplunkReportingCollector reporting = new SplunkReportingCollector(sla, splunkHost, splunkPort, splunkScheme, splunkToken, splunkUser, splunkPassword);
		return reporting;
	}
}
