package com.castsoftware.batch.task;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import javax.xml.soap.SOAPException;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.filter.ElementFilter;
import org.jdom.input.SAXBuilder;

import com.castsoftware.batch.NoOpEntityResolver;
import com.castsoftware.batch.data.DeliveryLogInfo;
import com.castsoftware.batch.data.PackageInfo;
import com.castsoftware.taskengine.ICallBack;
import com.castsoftware.vps.ValidationProbesService;
import com.google.gson.Gson;
//import hudson.FilePath;
//import hudson.model.AbstractBuild;

public class DMTLogsTask extends BatchTask
{
	private static final Logger logger = Logger.getLogger(DMTLogsTask.class);
	// private List <String> packages;
	private Hashtable<String, String> package_hash;
	private PackageInfo pkgInfo;
	private String deliveryFolder = "";
	private String appName = "";
	private String version = "";
	private String previousversion = "";
	private String opDesc = "";
	
	public DMTLogsTask(int taskId, ICallBack callback, String appName, String versionName, Date releaseDate,
			String stepIdentifier)
	{
		super(taskId, callback, appName, versionName, releaseDate, stepIdentifier);
		this.appName = appName;
		this.version = versionName;
		this.deliveryFolder = stepIdentifier;
	}


	@Override
	public int postRunTasks() 
	{
		int retCode = 0;
		String appId;
		try {
			appId = getAppId(deliveryFolder, appName);
			String versionId = getVersionId(deliveryFolder, appId, version);
			List<String> packages = getPackages(deliveryFolder, appId, versionId);
			
			collectDMTLogFiles(deliveryFolder, appId, versionId,packages);	
		} 
		catch (JDOMException | IOException | UnsupportedOperationException | InterruptedException | SOAPException e) 
		{
			//collectDMTLogFiles(deliveryFolder, appId, versionId,packages);
			retCode = -1;
			setExitStr(" - UNKNOWN ERROR");
			logger.error("An exception occured while sending logs to AOP",e);
			e.printStackTrace();
		}
		return retCode;
	}

	private String getAppId(String deliveryFolder, String appName) throws JDOMException, IOException
	{
		String appId = "";
		if (deliveryFolder.isEmpty()) return appId;

		SAXBuilder builder = new SAXBuilder();
		builder.setValidation(false);
		builder.setEntityResolver(new NoOpEntityResolver());
		File xmlFile = new File(deliveryFolder + "\\data\\index.xml");

		Document document = (Document) builder.build(xmlFile);
		Element rootNode = document.getRootElement();
		List<Element> list = rootNode.getChildren("entry");

		for (Element node : list) {
			String attributeValue = node.getAttributeValue("key");
			String[] attribParts = attributeValue.split("_");

			if (attribParts.length > 1 && "name".equals(attribParts[1])) {
				String value = node.getValue();
				if (appName.equals(value)) {
					appId = attribParts[0];
					break;
				}
			}
		}
		return appId;
	}

	private String getVersionId(String deliveryFolder, String appId, String version) throws JDOMException, IOException
	{
		if (deliveryFolder.isEmpty() || appId.isEmpty()) {
			return "";
		}

		String versionId = "";

		SAXBuilder builder = new SAXBuilder();
		builder.setValidation(false);
		builder.setEntityResolver(new NoOpEntityResolver());
		File xmlFile = new File(new StringBuffer().append(deliveryFolder).append("\\data").append("\\{").append(appId)
				.append("}").append("\\index.xml").toString());

		Document document = (Document) builder.build(xmlFile);
		Element rootNode = document.getRootElement();
		List<Element> list = rootNode.getChildren("entry");

		for (Element node : list) {
			String attributeValue = node.getAttributeValue("key");
			String[] attribParts = attributeValue.split("_");

			if (attribParts.length > 1 && "name".equals(attribParts[1])) {
				if (version.equals(node.getValue())) 
				{
					versionId = attribParts[0];
					break;
				}
			}
		}
		return versionId;
	}

	private List<String> getPackages(String deliveryFolder, String appId, String versionId) throws JDOMException,
			IOException
	{
		List<String> packages = new ArrayList();
		package_hash = new Hashtable<String, String>();

		if (deliveryFolder.isEmpty() || appId.isEmpty() || versionId.isEmpty()) {
			return packages;
		}

		SAXBuilder builder = new SAXBuilder();
		builder.setValidation(false);
		builder.setEntityResolver(new NoOpEntityResolver());
		File xmlFile = new File(new StringBuffer().append(deliveryFolder).append("\\data").append("\\{").append(appId)
				.append("}").append("\\{").append(versionId).append("}").append("\\index.xml").toString());

		Document document = (Document) builder.build(xmlFile);
		Element rootNode = document.getRootElement();
		List<Element> list = rootNode.getChildren("entry");

		for (Element node : list) {
			String attributeValue = node.getAttributeValue("key");
			String[] attribParts = attributeValue.split("_");
			String[] attrSplit = attributeValue.split("_name\">");

			if (attribParts.length > 1 && "name".equals(attribParts[1])) {
				packages.add(attribParts[0]);

				String pkg_name = node.getText();

				package_hash.put(attribParts[0], pkg_name);
			}
		}

		return packages;
	}

	/**
	 * Display the delivery report
	 * 
	 * @return -1 = no files have changed 0 = files have changed, but within the
	 *         delivery threshold amount 1 = exceeded the delivery threshold -2
	 *         = the delivery is empty
	 * @throws SOAPException 
	 * @throws UnsupportedOperationException 
	 * 
	 */
	
	private void collectDMTLogFiles(String deliveryFolder, String appId, String versionId, List<String> packages) throws UnsupportedOperationException, InterruptedException, IOException, SOAPException
	{
		List <DeliveryLogInfo> logInfoList = new ArrayList();
		
		for (String pkgId : packages) {
			String pkgName = package_hash.get(pkgId);
			File packageFolder = new File(String.format("%s\\data\\{%s}\\{%s}\\{%s}", deliveryFolder, appId, versionId, pkgId));
			String[] logFiles=packageFolder.list(new FilenameFilter() {
				public boolean accept(File dir, String filename)
	              {  return filename.endsWith(".CastLog2"); }	 
			} );
			for (String logFileName: logFiles)
			{
				String logName = String.format("%s\\%s", packageFolder,logFileName);
				logInfoList.add(new DeliveryLogInfo(pkgName, logName));
			}
		}	
		sendDeliveryLogs(getAppName(),getReleaseDate(),logInfoList);
	}

	protected void sendDeliveryLogs(String appName, Date releaseDate, List<DeliveryLogInfo> dmtLogs)
			throws InterruptedException, IOException, UnsupportedOperationException, SOAPException
	{
		String castDate = castDateFormat.format(releaseDate);
		Gson gson = new Gson();

		logger.info(" ");
		logger.info("Sending Delivery Logs to Application Operations Portal: ");
		logger.info(String.format(" -appName: %s ", appName));
		logger.info(String.format(" -releaseDate: %s ", castDate));
		logger.info(" ");

		String validationProbService = globalProperties.getPropertyValue("validation.prob.service");
		if (validationProbService != null && validationProbService.isEmpty()) {
			output.add("Warning: Connection to AOP is not configured");
		} else {
			ValidationProbesService vps = new ValidationProbesService(validationProbService);
			output.add(String.format("Sending DMT Logs: \"%s\" \"%s\"", appName, castDate));
			String json = gson.toJson(dmtLogs);

			vps.sendDeliveryLogs(appName, castDate, json);
		}
		output.add(" ");
	}
	
	protected void sendDeliveryReport(String appName, Date releaseDate, List<PackageInfo> dmtRpt)
			throws InterruptedException, IOException, UnsupportedOperationException, SOAPException
	{
		String castDate = castDateFormat.format(releaseDate);
		Gson gson = new Gson();

		logger.info(" ");
		logger.info("Sending Delivery Report to Application Operations Portal: ");
		logger.info(String.format(" -appName: %s ", appName));
		logger.info(String.format(" -releaseDate: %s ", castDate));
		logger.info(" ");

		String validationProbService = globalProperties.getPropertyValue("validation.prob.service");
		if (validationProbService != null && validationProbService.isEmpty()) 
		{
			output.add("Warning: Connection to AOP is not configured");
		} 
		else 
		{
			ValidationProbesService vps = new ValidationProbesService(validationProbService);
			output.add(String.format("Sending DMT Report: \"%s\" \"%s\"", appName, castDate));
			String json = gson.toJson(dmtRpt);

			vps.sendDeliveryReport(appName, castDate, json);
		}
		output.add(" ");
	}

}
