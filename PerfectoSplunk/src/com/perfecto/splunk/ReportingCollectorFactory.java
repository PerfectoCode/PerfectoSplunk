package com.perfecto.splunk;

import java.net.Proxy;

//Creating the Thread local instance of SplunkReportingCollector
public class ReportingCollectorFactory {
	
	private static ThreadLocal<SplunkReportingCollector> reporting = new ThreadLocal<SplunkReportingCollector>();

	public static SplunkReportingCollector getCollector() {
		return reporting.get();
	}

	public static void setReporting(SplunkReportingCollector report) {
		reporting.set(report);
	}
	
	public static SplunkReportingCollector createInstance(long sla, String splunkScheme, String splunkHost, String splunkPort,  String splunkToken) {
		SplunkReportingCollector reporting = new SplunkReportingCollector(sla, splunkScheme, splunkHost, splunkPort,  splunkToken);
		return reporting;
	}
	
	public static SplunkReportingCollector createInstance(long sla, String splunkScheme, String splunkHost, String splunkPort,  String splunkToken, Proxy proxy) {
		SplunkReportingCollector reporting = new SplunkReportingCollector(sla, splunkScheme, splunkHost, splunkPort,  splunkToken, proxy);
		return reporting;
	}
}
