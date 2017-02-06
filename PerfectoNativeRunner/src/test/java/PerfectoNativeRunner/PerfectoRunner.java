package PerfectoNativeRunner;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.Proxy;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

public class PerfectoRunner {
	private Proxy proxy = null;

	private String hostLocal;
	private String usernameLocal;
	private String passwordLocal;
	private String vitalsFileLocal;
	private String reportKeyLocal;
	private String pcapFileLocal;
	private String recordingFLVLocal;
	private String recordingMP4Local;
	private String fileOutputVideoLocal;
	private String fileOutputVitalsLocal;
	private String fileOutputPcapLocal;
	private String fileOutputXmlLocal;
	private String fileOutputPdfLocal;
	private String xmlFileLocal;
	private String pdfFileLocal;
	

	// proxy constructor
	public PerfectoRunner(Proxy proxy) {
		this.proxy = proxy;
	}

	// default constructor
	public PerfectoRunner() {
	}

	public enum availableReportOptions {
		scriptTimerElapsed, scriptTimerDevice, scriptTimerSystem, scriptTimerUx, scriptStartTime, scriptEndTime, executionId, reportId, scriptName, scriptStatus, deviceId, location, manufacturer, model, firmware, description, os, osVersion, transactions, reportUrl, xmlReport, variables, exception, recordingStreamFlvUrl, recordingDownloadFlvUrl, recordingStreamMp4Url, recordingDownloadMp4Url, vitalsUrl, pcapUrl, recordingFlvFileName, recordingMp4FileName, vitalsFileName, pcapFileName, pdfFileName, pdfUrl,xmlFileName, xmlUrl
	}

	public String getXMLReport(String host, String username, String password, String reportKey)
			throws IOException, URISyntaxException {
		HttpClient hc;
		if (proxy != null) {
			hc = new HttpClient(proxy);
		} else {
			hc = new HttpClient();
		}

		String response = hc.sendRequest("https://" + host + "/services/reports/" + reportKey.replace(" ", "%20")
				+ "?operation=download&user=" + username + "&password=" + password + "&responseformat=xml");
		return response;
	}
	
	

	// executes the script and generates the response data
	public Map<availableReportOptions, Object> executeScript(String host, String username, String password,
			String scriptKey, String deviceId, String additionalParams, int cycles, long waitBetweenCycles,
			String fileOutputVideo, String fileOutputVitals, String fileOutputPcap, String fileOutputXml, String fileOutputPdf) throws DOMException, Exception {
		HttpClient hc;
		if (proxy != null) {
			hc = new HttpClient(proxy);
		} else {
			hc = new HttpClient();
		}

		hostLocal = host;
		usernameLocal = username;
		passwordLocal = password;
		fileOutputVideoLocal = fileOutputVideo;
		fileOutputVitalsLocal = fileOutputVitals;
		fileOutputPcapLocal = fileOutputPcap;
		fileOutputXmlLocal= fileOutputXml;
		fileOutputPdfLocal = fileOutputPdf;

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
						reportKeyLocal=reportId;
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

		Map<availableReportOptions, Object> testResults = new HashMap<availableReportOptions, Object>();

		testResults = parseReport(response, host);

		return testResults;
	}

	// parser for the report and compiles the reporting map
	public Map<availableReportOptions, Object> parseReport(String xml, String host) throws DOMException, Exception {

		DocumentBuilder newDocumentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		Document parse = newDocumentBuilder.parse(new ByteArrayInputStream(xml.getBytes()));
		Map<availableReportOptions, Object> testResults = new HashMap<availableReportOptions, Object>();
		String nText = "";
		String executionId = getXPathValue(xml, "//execution/info/id");
		String reportId = getXPathValue(xml, "//execution/info/dataItems/dataItem[@label=\"report\"]/key");

		// Miscellaneous
		String scriptName = parse.getElementsByTagName("name").item(0).getTextContent();

		NodeList status = parse.getElementsByTagName("status");

		Element statusSub = (Element) status.item(0);

		String scriptStatus = "";

		if (statusSub.getElementsByTagName("success").item(0).getTextContent().equals("true")) {
			scriptStatus = "Pass";
			testResults.put(availableReportOptions.scriptStatus, scriptStatus);
		} else {
			scriptStatus = "Fail";
			testResults.put(availableReportOptions.scriptStatus, scriptStatus);

			try {
				NodeList nodeL8 = getXPathList(xml,
						"//code[text()=\"CompletedWithErrors\"]/following-sibling::description[text()=\"FLOW_CHANGE_REQUESTED: Flow change initiated by script command - EXIT\"]/ancestor::output/ancestor::flow[2]/step[last()]/flow//output/status/description");
				String exception = nodeL8.item(0).getTextContent();

				testResults.put(availableReportOptions.exception, exception);
			} catch (Exception ex) {

				NodeList nodeL8 = getXPathList(xml,
						"//output/status/code[text()=\"Failed\"]/following-sibling::description[text()!=\"Failed\"]|//output/status/code[text()=\"ResourcesUnavailable\"]/following-sibling::description[text()!=\"Failed\"]");
				String exception = nodeL8.item(0).getTextContent();

				testResults.put(availableReportOptions.exception, exception);
			}

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

		testResults.put(availableReportOptions.executionId, executionId);
		testResults.put(availableReportOptions.reportId, reportId);
		testResults.put(availableReportOptions.scriptStartTime,
				getXPathValue(xml, "//execution/info/times/flowTimes/start/millis"));
		testResults.put(availableReportOptions.scriptEndTime,
				getXPathValue(xml, "//execution/info/times/flowTimes/end/millis"));

		testResults.put(availableReportOptions.scriptTimerElapsed,
				getXPathValue(xml, "//execution/output/timers/timer/time[@label=\"elapsed\"]"));
		testResults.put(availableReportOptions.scriptTimerSystem,
				getXPathValue(xml, "//execution/output/timers/timer/time[@label=\"system\"]"));
		testResults.put(availableReportOptions.scriptTimerDevice,
				getXPathValue(xml, "//execution/output/timers/timer/time[@label=\"device\"]"));
		testResults.put(availableReportOptions.scriptTimerUx,
				getXPathValue(xml, "//execution/output/timers/timer/time[@label=\"ux\"]"));

		testResults.put(availableReportOptions.scriptName, scriptName);

		try {
			testResults.put(availableReportOptions.recordingDownloadMp4Url,
					getXPathValue(xml, "//dataItem[@label=\"recordingDownload\" and @format=\"mp4\"]/file"));
			recordingMP4Local = getXPathValue(xml, "//dataItem[@label=\"recordingDownload\" and @format=\"mp4\"]/file");
			testResults.put(availableReportOptions.recordingMp4FileName, recordingMP4Local.split("/")[5]);
		} catch (Exception ex) {

		}

		try {
			testResults.put(availableReportOptions.recordingDownloadFlvUrl,
					getXPathValue(xml, "//dataItem[@label=\"recordingDownload\" and @format=\"flv\"]/file"));
			recordingFLVLocal = getXPathValue(xml, "//dataItem[@label=\"recordingDownload\" and @format=\"flv\"]/file");
			testResults.put(availableReportOptions.recordingFlvFileName, recordingFLVLocal.split("/")[5]);
		} catch (Exception ex) {
		}

		try {

			pcapFileLocal = getXPathValue(xml, "//dataItem[@type=\"network\" and @format=\"pcap\"]/attachment");
			testResults.put(availableReportOptions.pcapFileName, pcapFileLocal.replace("network/", ""));
			testResults.put(availableReportOptions.pcapUrl,
					"https://" + hostLocal + "/services/reports/" + reportKeyLocal + "?operation=network&user="
							+ usernameLocal + "&password=" + passwordLocal + "&attachment=" + pcapFileLocal + "");

		} catch (Exception ex) {
		}

		try {

			vitalsFileLocal = getXPathValue(xml, "//dataItem[@type=\"monitor\" and @format=\"csv\"]/attachment");
			testResults.put(availableReportOptions.vitalsFileName, vitalsFileLocal.replace("vitals/", ""));
			testResults.put(availableReportOptions.vitalsUrl,
					"https://" + hostLocal + "/services/reports/" + reportKeyLocal + "?operation=monitor&user="
							+ usernameLocal + "&password=" + passwordLocal + "&attachment=" + vitalsFileLocal + "");

		} catch (Exception ex) {
		}
		
		try {
			xmlFileLocal = reportKeyLocal.split("/")[1].replace(".xml", "");
			pdfFileLocal = reportKeyLocal.split("/")[1].replace(".xml", "");
			testResults.put(availableReportOptions.xmlFileName, xmlFileLocal);
			testResults.put(availableReportOptions.xmlUrl, "https://" + hostLocal + "/services/reports/" + reportKeyLocal.replace(" ", "%20")
			+ "?operation=download&user=" + usernameLocal + "&password=" + passwordLocal + "&responseformat=xml");
			
			testResults.put(availableReportOptions.pdfFileName, xmlFileLocal);
			testResults.put(availableReportOptions.pdfUrl, "https://" + hostLocal + "/services/reports/" + reportKeyLocal.replace(" ", "%20")
			+ "?operation=download&user=" + usernameLocal + "&password=" + passwordLocal + "&format=pdf");
		} catch (Exception ex) {
			
		}

		try {
			testResults.put(availableReportOptions.deviceId, getXPathValue(xml,
					"(execution/input/handsets/handset)[1]/properties/property/name[@displayName='Id']/following-sibling::value"));
			testResults.put(availableReportOptions.location, getXPathValue(xml,
					"(execution/input/handsets/handset)[1]/properties/property/name[@displayName='Location']/following-sibling::value"));
			testResults.put(availableReportOptions.manufacturer, getXPathValue(xml,
					"(execution/input/handsets/handset)[1]/properties/property/name[@displayName='Manufacturer']/following-sibling::value"));
			testResults.put(availableReportOptions.model, getXPathValue(xml,
					"(execution/input/handsets/handset)[1]/properties/property/name[@displayName='Model']/following-sibling::value"));
			testResults.put(availableReportOptions.firmware, getXPathValue(xml,
					"(execution/input/handsets/handset)[1]/properties/property/name[@displayName='Firmware']/following-sibling::value"));
			testResults.put(availableReportOptions.description, getXPathValue(xml,
					"(execution/input/handsets/handset)[1]/properties/property/name[@displayName='Description']/following-sibling::value"));
			testResults.put(availableReportOptions.os, getXPathValue(xml,
					"(execution/input/handsets/handset)[1]/properties/property/name[@displayName='OS']/following-sibling::value"));
			testResults.put(availableReportOptions.osVersion, getXPathValue(xml,
					"(execution/input/handsets/handset)[1]/properties/property/name[@displayName='OS Version']/following-sibling::value"));

		} catch (Exception ex) {

		}

		testResults.put(availableReportOptions.xmlReport, xml);

		String user = getXPathAttribute(xml, "owner", "(//execution/info/dataItems/dataItem)[1]/key");

		testResults.put(availableReportOptions.reportUrl,
				"https://" + host + "/nexperience/Report.html?reportId=SYSTEM%3Adesigns%2Freport&key="
						+ reportId.replace(".xml", "") + "%2Exml&liveUrl=rtmp%3A%2F%2F" + host.replace(".", "%2E")
						+ "%2Fengine&appUrl=https%3A%2F%2F" + host.replace(".", "%2E") + "%2Fnexperience&username="
						+ user);

		// Transactions
		Table<String, String, String> transactions = HashBasedTable.create();
		String transName = "";
		String transTimer = "";
		String transSuccess = "";
		NodeList nodeL = getXPathList(xml,
				"//description[contains(text(),\"Validation\") and contains(text(),\"timer\")]/parent::status/parent::output/following-sibling::parameters/parameter/name[@displayName=\"Timer ID\"]/following-sibling::value");
		NodeList nodeL2 = getXPathList(xml,
				"//description[contains(text(),\"Validation\") and contains(text(),\"timer\")]/preceding-sibling::success");
		NodeList nodeL3 = getXPathList(xml,
				"//description[contains(text(),\"Validation\") and contains(text(),\"timer\")]/parent::status/parent::output/preceding-sibling::info/dataItems/dataItem[@label=\"actual\"]/value");

		for (int i = 0; i < nodeL.getLength(); i++) {

			transName = nodeL.item(i).getTextContent();
			transTimer = nodeL3.item(i).getTextContent();
			transSuccess = nodeL2.item(i).getTextContent();
			transactions.put(transName, transTimer, transSuccess);

		}

		NodeList nodeL4 = getXPathList(xml, "//step/info/transaction/preceding-sibling::name");
		NodeList nodeL5 = getXPathList(xml, "//step/info/transaction/preceding-sibling::times/flowTimes/start/millis");
		NodeList nodeL6 = getXPathList(xml, "//step/info/transaction/preceding-sibling::times/flowTimes/end/millis");
		NodeList nodeL7 = getXPathList(xml,
				"//step/info/transaction/parent::info/following-sibling::output/status/success");

		for (int i = 0; i < nodeL4.getLength(); i++) {

			transName = nodeL4.item(i).getTextContent();
			transTimer = Long.toString((Long.parseLong(nodeL6.item(i).getTextContent())
					- Long.parseLong(nodeL5.item(i).getTextContent())));
			transSuccess = nodeL7.item(i).getTextContent();
			transactions.put(transName, transTimer, transSuccess);

		}

		testResults.put(availableReportOptions.transactions, transactions);

		Map<String, String> variables = new HashMap<String, String>();

		nodeL = getXPathList(xml, "(execution/input/variables)[1]/variable/name");
		nodeL2 = getXPathList(xml, "(execution/input/variables)[1]/variable/value");
		String name = "";
		String value = "";
		for (int i = 0; i < nodeL.getLength(); i++) {
			name = nodeL.item(i).getTextContent();
			value = nodeL2.item(i).getTextContent();
			variables.put(name, value);
		}

		testResults.put(availableReportOptions.variables, variables);

		return testResults;
	}

	public String getXPathValue(String xml, String XpathString)
			throws ParserConfigurationException, SAXException, IOException, XPathExpressionException {

		NodeList result = getXPathList(xml, XpathString);

		if (result.item(0) == null) {
			throw new XPathExpressionException("Xpath not found");
		} else {
			return result.item(0).getTextContent();
		}
	}

	public String getXPathAttribute(String xml, String attribute, String XpathString)
			throws ParserConfigurationException, SAXException, IOException, XPathExpressionException {

		NodeList result = getXPathList(xml, XpathString);

		if (result.item(0) == null) {
			throw new XPathExpressionException("Xpath not found");
		} else {
			return result.item(0).getAttributes().getNamedItem(attribute).getTextContent();
		}
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

	public String CSVToJson(File in, File out) throws IOException {
		List<Map<?, ?>> data = readObjectsFromCsv(in);
		return writeAsJson(data, out);
	}

	public List<Map<?, ?>> readObjectsFromCsv(File file) throws IOException {
		CsvSchema bootstrap = CsvSchema.emptySchema().withHeader();
		CsvMapper csvMapper = new CsvMapper();
		MappingIterator<Map<?, ?>> mappingIterator = csvMapper.reader(Map.class).with(bootstrap).readValues(file);

		return mappingIterator.readAll();
	}

	public String writeAsJson(List<Map<?, ?>> data, File file) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		mapper.writeValue(file, data);
		return readAsJson(data);
	}

	public String readAsJson(List<Map<?, ?>> data) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		return mapper.writeValueAsString(data);
	}

	public void downloadMP4Video() throws Exception {

		HttpClient hc = new HttpClient();
		File file = new File( fileOutputVideoLocal, recordingMP4Local.split("/")[5]);
		hc.download(recordingMP4Local,file);
	}

	public void downloadFLVVideo() throws Exception {
		HttpClient hc = new HttpClient();
		File file = new File(fileOutputVideoLocal, recordingFLVLocal.split("/")[5]);
		hc.download(recordingFLVLocal, file);
	}

	public void downloadVitals() throws Exception {
		HttpClient hc = new HttpClient();
		File file = new File(fileOutputVitalsLocal, vitalsFileLocal.replace("vitals/", ""));
		hc.download(
				"https://" + hostLocal + "/services/reports/" + reportKeyLocal + "?operation=monitor&user="
						+ usernameLocal + "&password=" + passwordLocal + "&attachment=" + vitalsFileLocal + "", file
				);
	}

	public void downloadPCAP() throws Exception {
		HttpClient hc = new HttpClient();
		File file = new File(fileOutputPcapLocal, pcapFileLocal.replace("network/", ""));
		hc.download(
				"https://" + hostLocal + "/services/reports/" + reportKeyLocal + "?operation=network&user="
						+ usernameLocal + "&password=" + passwordLocal + "&attachment=" + pcapFileLocal + "",
				file);
	}
	
	public void downloadXMLReport() throws Exception {
		HttpClient hc = new HttpClient();
		File file = new File(fileOutputXmlLocal, xmlFileLocal+".xml");
		
		hc.download("https://" + hostLocal + "/services/reports/" + reportKeyLocal.replace(" ", "%20")
				+ "?operation=download&user=" + usernameLocal + "&password=" + passwordLocal + "&responseformat=xml", file);
	}
	
	public void downloadPDFReport() throws Exception {
		HttpClient hc = new HttpClient();
		File file = new File(fileOutputPdfLocal, pdfFileLocal+".pdf");
		
		hc.download("https://" + hostLocal + "/services/reports/" + reportKeyLocal.replace(" ", "%20")
				+ "?operation=download&user=" + usernameLocal + "&password=" + passwordLocal + "&format=pdf", file);
	}
	
	
	
}
