package PerfectoNativeRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Proxy;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.management.RuntimeErrorException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
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
		executionId, reportId, scriptName, scriptStatus, deviceId, location, manufacturer, model, firmware, description, os, osVersion, transactions, reportUrl
	}

	// executes the script and generates the response data
	public Map<String, Object> executeScript(String host, String username, String password, String scriptKey,
			String deviceId, String additionalParams, int cycles, long waitBetweenCycles)
			throws DOMException, Exception {
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
				+ password + additionalParams);

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
				if (i + 1 >= cycles) {
					String msg = "Exited checking script status early due to script execution exceeding cycles limit.  Cycles limit currently set to "
							+ cycles + ", it is recommended to increase your cycle count and try again.";
					System.out.println(msg);
					throw new Exception();
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

		testResults.put("executionId", executionId);
		testResults.put("reportId", reportId);
		testResults.put("scriptName", scriptName);
		testResults.put("scriptStatus", scriptStatus);
		testResults.put("deviceId", getXPathValue(xml, "//*[@displayName='Id']/following-sibling::value"));
		testResults.put("location", getXPathValue(xml, "//*[@displayName='Location']/following-sibling::value"));
		testResults.put("manufacturer",
				getXPathValue(xml, "//*[@displayName='Manufacturer']/following-sibling::value"));
		testResults.put("model", getXPathValue(xml, "//*[@displayName='Model']/following-sibling::value"));
		testResults.put("firmware", getXPathValue(xml, "//*[@displayName='Firmware']/following-sibling::value"));
		testResults.put("description", getXPathValue(xml, "//*[@displayName='Description']/following-sibling::value"));
		testResults.put("os", getXPathValue(xml, "//*[@displayName='OS']/following-sibling::value"));
		testResults.put("osVersion", getXPathValue(xml, "//*[@displayName='OS Version']/following-sibling::value"));

		// Transactions
		Map<String, String> transactions = new HashMap<String, String>();
		String transName = "";
		String transTimer = "";
		NodeList nodeL = getXPathList(xml, "//description[contains(text(),'Value of ux timer')]");

		for (int i = 0; i < nodeL.getLength(); i++) {
			nText = nodeL.item(i).getTextContent();
			transName = nText.split("Value of ux timer ")[1].split(" is ")[0];
			transTimer = nText.split(" is ")[1].split("milliseconds")[0];
			transactions.put(transName, transTimer);

		}

		testResults.put("transactions", transactions);

		return testResults;
	}

	public String getXPathValue(String xml, String XpathString)
			throws ParserConfigurationException, SAXException, IOException, XPathExpressionException {

		NodeList result = getXPathList(xml, XpathString);
		return result.item(1).getTextContent();
	}

	public NodeList getXPathList(String xml, String XpathString)
			throws ParserConfigurationException, SAXException, IOException, XPathExpressionException {
		DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
		domFactory.setNamespaceAware(true);
		DocumentBuilder builder = domFactory.newDocumentBuilder();
		Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes()));
		XPath xpath = XPathFactory.newInstance().newXPath();
		XPathExpression expr = xpath.compile(XpathString);
		return (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
	}
}
