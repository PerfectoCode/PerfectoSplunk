package com.perfecto.splunk;

import java.net.Proxy;

//Creating the Thread local instance of SplunkReporting
public class ReportingFactory {
	
	private static ThreadLocal<SplunkReporting> reporting = new ThreadLocal<SplunkReporting>();

	public static SplunkReporting getReporting() {
		return reporting.get();
	}

	public static void setReporting(SplunkReporting report) {
		reporting.set(report);
	}
	
	public static SplunkReporting createInstance(long sla, String splunkScheme, String splunkHost, String splunkPort,  String splunkToken) {
		SplunkReporting reporting = new SplunkReporting(sla, splunkScheme, splunkHost, splunkPort,  splunkToken);
		return reporting;
	}
	
	public static SplunkReporting createInstance(long sla, String splunkScheme, String splunkHost, String splunkPort,  String splunkToken, Proxy proxy) {
		SplunkReporting reporting = new SplunkReporting(sla, splunkScheme, splunkHost, splunkPort,  splunkToken, proxy);
		return reporting;
	}
}
