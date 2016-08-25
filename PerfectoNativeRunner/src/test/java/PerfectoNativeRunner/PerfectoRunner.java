package PerfectoNativeRunner;

import java.awt.List;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Proxy;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class PerfectoRunner {
	private Proxy proxy = null;

	// proxy constructor
	public PerfectoRunner(Proxy proxy) {
		this.proxy = proxy;
	}

	// default constructor
	public PerfectoRunner() {
	}

	public enum availableReportOptions {
		executionId, reportId, scriptName, scriptStatus, deviceId, os, osVersion, model, transactions, reportUrl
	}

	// executes the script and generates the response data
	public Map<String, Object> executeScript(String host, String username, String password, String scriptKey,
			String deviceId, int cycles, long waitBetweenCycles) throws DOMException, Exception {
		HttpClient hc;
		if (proxy != null) {
			hc = new HttpClient(proxy);
		} else {
			hc = new HttpClient();
		}

		String executionId = "";
		String reportId = "";

		String response = hc.sendRequest("https://" + host + "/services/executions?operation=execute&scriptkey="
				+ scriptKey + ".xml&responseformat=xml&param.DUT=" + deviceId + "&user=" + username + "&password="
				+ password + "");

		if (response.contains("xml")) {
			executionId = hc.getXMLValue(response, "executionId");

			for (int i = 0; i < cycles; i++) {

				response = hc.sendRequest("https://" + host + "/services/executions/" + executionId
						+ "?operation=status&user=" + username + "&password=" + password + "");
				if (response.contains("xml")) {
					if (!hc.getJsonValue(response, "status").equals("Completed")) {
						Thread.sleep(waitBetweenCycles);
					} else {
						reportId = hc.getJsonValue(response, "reportKey");
						break;
					}
				}
			}
		}

		response = hc.sendRequest("https://" + host + "/services/reports/" + reportId + "?operation=download&user="
				+ username + "&password=" + password + "&responseformat=xml");

		Map<String, Object> testResults = new HashMap<String, Object>();

		testResults = parseReport(response, executionId, reportId);
		testResults.put("reportUrl",
				"https://" + host + "/nexperience/Report.html?reportId=SYSTEM%3Adesigns%2Freport&key="
						+ reportId.replace(".xml", "") + "%2Exml&liveUrl=rtmp%3A%2F%2F" + host.replace(".", "%2E")
						+ "%2Fengine&appUrl=https%3A%2F%2F" + host.replace(".", "%2E") + "%2Fnexperience&username="
						+ username);
		return testResults;
	}

	// parser for the report and compiles the reporting map
	public Map<String, Object> parseReport(String xml, String executionId, String reportId)
			throws DOMException, Exception {

		DocumentBuilder newDocumentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		Document parse = newDocumentBuilder.parse(new ByteArrayInputStream(xml.getBytes()));
		Map<String, Object> testResults = new HashMap<String, Object>();
		String nText = "";

		// Miscellaneous
		String scriptName = parse.getElementsByTagName("name").item(0).getTextContent();

		NodeList status = parse.getElementsByTagName("status");

		Element statusSub = (Element) status.item(0);

		String scriptStatus = "";

		if (statusSub.getElementsByTagName("success").item(0).getTextContent().equals("true")) {
			scriptStatus = "Pass";
			testResults.put("scriptStatus", scriptStatus);
		} else {
			scriptStatus = "Fail";
			testResults.put("scriptStatus", scriptStatus);
			if (!statusSub.getElementsByTagName("code").item(0).getTextContent().equals("CompletedWithErrors"))

			{
				if (!statusSub.getElementsByTagName("code").item(0).getTextContent().equals("Failed")) {

					if (!statusSub.getElementsByTagName("failedActions").equals("0")) {
						throw new Exception("ScriptName:" + scriptName + " ::: ExecutionId: " + executionId
								+ " ::: reportId: " + reportId + " ::: exception: Exeception "
								+ statusSub.getElementsByTagName("description").item(0).getTextContent());
					}
				}
			}
		}

		NodeList handsets = parse.getElementsByTagName("handset");

		Element handsetsSub = (Element) handsets.item(0);

		String deviceId = "";
		String os = "";
		String model = "";
		String osVersion = "";

		if (xml.contains("displayName=\"Phone Number\"")) {

			deviceId = handsetsSub.getElementsByTagName("value").item(1).getTextContent();
			os = handsetsSub.getElementsByTagName("value").item(16).getTextContent();
			model = handsetsSub.getElementsByTagName("value").item(12).getTextContent();
			osVersion = handsetsSub.getElementsByTagName("value").item(17).getTextContent();
		} else {
			deviceId = handsetsSub.getElementsByTagName("value").item(1).getTextContent();
			os = handsetsSub.getElementsByTagName("value").item(14).getTextContent();
			model = handsetsSub.getElementsByTagName("value").item(10).getTextContent();
			osVersion = handsetsSub.getElementsByTagName("value").item(15).getTextContent();
		}

		testResults.put("executionId", executionId);
		testResults.put("reportId", reportId);
		testResults.put("scriptName", scriptName);
		testResults.put("scriptStatus", scriptStatus);
		testResults.put("deviceId", deviceId);
		testResults.put("os", os);
		testResults.put("osVersion", osVersion);
		testResults.put("model", model);

		// Transactions
		Map<String, String> transactions = new HashMap<String, String>();
		String transName = "";
		String transTimer = "";
		NodeList nodeL = parse.getElementsByTagName("description");
		for (int i = 0; i < nodeL.getLength(); i++) {
			nText = nodeL.item(i).getTextContent();
			if (nText.contains("Value of ux timer")) {
				transName = nText.split("Value of ux timer ")[1].split(" is ")[0];
				transTimer = nText.split(" is ")[1].split("milliseconds")[0];
				transactions.put(transName, transTimer);
			}
		}
		testResults.put("transactions", transactions);

		return testResults;
	}
}
