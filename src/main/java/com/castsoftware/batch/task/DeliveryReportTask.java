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

public class DeliveryReportTask extends BatchTask
{
	private static final Logger logger = Logger.getLogger(DeliveryReportTask.class);
	// private List <String> packages;
	private Hashtable<String, String> package_hash;
	private PackageInfo pkgInfo;
	private String deliveryFolder = "";
	private String appName = "";
	private String version = "";
	private String previousversion = "";
	private String opDesc = "";
	
	public DeliveryReportTask(int taskId, ICallBack callback, String appName, String versionName, Date releaseDate,
			String stepIdentifier)
	{
		super(taskId, callback, appName, versionName, releaseDate, stepIdentifier);
	}

	@Override
	public void preRunTasks(List <String> cmd)
	{
		for (int ii = 0; ii < cmd.size(); ii++) {
			if ("-delivery".equalsIgnoreCase(cmd.get(ii))) deliveryFolder = cmd.get(ii + 1);
			if ("-application".equalsIgnoreCase(cmd.get(ii))) appName = cmd.get(ii + 1);
			if ("-version".equalsIgnoreCase(cmd.get(ii))) version = cmd.get(ii + 1);
			if ("-previousversion".equalsIgnoreCase(cmd.get(ii))) previousversion = cmd.get(ii + 1);
		}
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
			switch (showExtendedPackageDetails(deliveryFolder, appId, versionId, version, previousversion,
					packages)) {
				case -2:
					setExitStr("- EMPTY ERROR");
					retCode = -1;
					break;
				case -1:
					setExitStr("- NO FILE CHANGE ERROR");
					retCode = -2;
					break;
				case 0:
					setExitStr( "- OK");
					retCode = 0;
					break;
				case 1:
					setExitStr("- THRESHOLD ERROR");
					retCode = -3;
					break;
			}
			collectDMTLogFiles(deliveryFolder, appId, versionId,packages);	
		} catch (JDOMException | IOException | UnsupportedOperationException | InterruptedException | SOAPException e) {
			retCode = -1;
			setExitStr(" - UNKNOWN ERROR");
			logger.error("An exception occured during the delivery report generation",e);
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
				if (version.equals(node.getValue())) {
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
	private int showExtendedPackageDetails(String deliveryFolder, String appId, String versionId, String curVersion,
			String previousversion, List<String> packages) throws JDOMException, IOException, InterruptedException, UnsupportedOperationException, SOAPException
	{
		int retCode = 0;
		if (deliveryFolder.isEmpty() || appId.isEmpty() || versionId.isEmpty() || packages.isEmpty()) {
			return -2;
		}

		List<PackageInfo> pkgList = new ArrayList();
		String pkgName = "";

		int added = 0;
		int removed = 0;
		int changed = 0;
		int unchanged = 0;
		int alerts = 0;
		int totals = 0;

		String str_ext = "";
		String str_add = "";
		String str_rem = "";
		String str_chg = "";
		String str_unc = "";
		String str_tot = "";
		String str_alrt = "";

		output.add(" ");
		output.add("Delivery Summary");
		output.add(String.format("Comparing %s to %s", curVersion, previousversion));

		for (String pkgId : packages) {
			SAXBuilder builder = new SAXBuilder();
			builder.setValidation(false);
			builder.setEntityResolver(new NoOpEntityResolver());
			String packageFolder = String.format("%s\\data\\{%s}\\{%s}\\{%s}", deliveryFolder, appId, versionId, pkgId);
			File dmtRptFile = new File(String.format("%s\\scan_report.pmx", packageFolder));
			File diffRptFile = new File(String.format("%s\\validation_diff_report.pmx", packageFolder));
			pkgName = package_hash.get(pkgId);

			if (diffRptFile.exists()) {
				Document document = (Document) builder.build(diffRptFile);
				Element rootNode = document.getRootElement();
				ElementFilter filter = new ElementFilter("items");
				Iterator<?> itr = rootNode.getDescendants(filter);

				int cAlerts = 0;
				while (itr.hasNext()) {
					Element node = (Element) itr.next();
					List<Element> itemList = node.getChildren();
					for (Element childItem : itemList) {
						if (childItem.getName().startsWith("delivery.")) {
							str_alrt = childItem.getAttribute("number").getValue();
							if (str_alrt != null && !str_alrt.isEmpty()) {
								cAlerts += Integer.parseInt(str_alrt);
							}
						}
					}
				}
				String tAlerts = Integer.toString(cAlerts);
				alerts+=cAlerts;
				pkgList.add(new PackageInfo(pkgName, tAlerts));
			}

			if (dmtRptFile.exists()) {
				Document document = (Document) builder.build(dmtRptFile);
				Element rootNode = document.getRootElement();
				ElementFilter filter = new ElementFilter("items");
				Iterator<?> itr = rootNode.getDescendants(filter);

				while (itr.hasNext()) {
					Element element = (Element) itr.next();
					List<Element> list = element.getChildren();

					for (int i = 0; i < list.size(); i++) {
						Element node = (Element) list.get(i);

						str_ext = node.getAttributeValue("extension", "0");
						str_add = node.getAttributeValue("added", "0");
						str_rem = node.getAttributeValue("removed", "0");
						str_chg = node.getAttributeValue("modifiedFiles", "0");
						str_tot = node.getAttributeValue("total", "0");

						added += Integer.parseInt(str_add);
						removed += Integer.parseInt(str_rem);
						changed += Integer.parseInt(str_chg);
						unchanged += 0;
						totals += Integer.parseInt(str_tot);

						pkgList.add(new PackageInfo(pkgName, str_ext, str_add, str_rem, str_chg, str_tot));
					}
				}

			} else {
				output.add("*** The summary file for this delivery type is curently not available ***\n");
			}
		}

		String formatPkgLine = "|Package: %-55s %10s|";
		String formatDetail = "|%-20s|%10s|%10s|%10s|%10s|%10s|";
		output.add("+===========================================================================+");
		output.add(String.format(formatDetail, "Extension", "Added", "Removed", "Changed", "Total", "Alerts"));
		output.add("|---------------------------------------------------------------------------|");

		PackageInfo savePkgItem = null;
		for (PackageInfo pkgItem : pkgList) {
			if (savePkgItem == null || !pkgItem.getPkgName().equals(savePkgItem.getPkgName())) {
				if (savePkgItem != null)
					output.add("|                                                                           |");
				output.add(String.format(formatPkgLine, pkgItem.getPkgName().trim(), pkgItem.getAlerts()));
			} else {
				output.add(String.format(formatDetail, pkgItem.getExtn(), pkgItem.getAdded(), pkgItem.getRemoved(),
						pkgItem.getChanged(), pkgItem.getTotals(), ""));
			}
			savePkgItem = pkgItem;
		}

		output.add("|---------------------------------------------------------------------------|");

		unchanged = totals - (added + removed + changed);
		str_add = String.format("%,9d", added);
		str_rem = String.format(" %,9d", removed);
		str_chg = String.format(" %,9d", changed);
		str_tot = String.format(" %,9d", totals);
		str_alrt = String.format(" %,9d", alerts);
        
		output.add(String.format(formatDetail, "Total:", str_add, str_rem, str_chg, totals, str_alrt));

		output.add("+===========================================================================+");

		sendDeliveryReport(getAppName(), getReleaseDate(), pkgList);
		
		String failNoChange = globalProperties.getPropertyValue("dmt.fail.no.changes");
		String dmtChangePercentStr = globalProperties.getPropertyValue("dmt.change.percent");
		// no source code has changed for this release
		output.add(String.format("Stop build when there are no source code changes is set to %s or changes are > %s%%",
				failNoChange, dmtChangePercentStr));
		double totalChg = 0.0;
		double total = 0.0;
		double dmtChangePercent = (dmtChangePercentStr != null && !dmtChangePercentStr.isEmpty()) ? Double
				.parseDouble(dmtChangePercentStr) / 100 : 0.0;

				
		if (added == 0 && removed == 0 && changed == 0) 
		{
			output.add("Source code has not changed between this and the last delivery");
			retCode = -1;
			
		} 
		else
		{


			// test the change against the threashold amount
			if (dmtChangePercentStr != null && !dmtChangePercentStr.isEmpty()) {
				total = added + removed + changed + unchanged;

				double addedChg = (float) added / total;
				double removedChg = (float) removed / total;
				double changedChg = (float) changed / total;
				totalChg = addedChg + removedChg + changedChg;
				output.add(String.format("Total code changes: %s%% ", (totalChg * 100)));
				double intTotChanged = (totalChg * 100);
				double intFromProperties = (dmtChangePercent * 100) ;
				if (intTotChanged > intFromProperties) 
				{
					retCode = 1;
				}
			}
		}
		
		
				
		output.add(String.format("Fail on DMT change is set to %s", failNoChange));
		if (!Boolean.parseBoolean(failNoChange)) 
		{
			if (retCode != 0) 
			{
				String errStr = "";
				switch (retCode) {
					case -2:
						errStr = "Empty";
						break;
					case -1:
						errStr = "No Change";
						break;
					case 1:
						errStr = "Threshold Exceeded";
						break;
				}

				output.add(String.format(
						"If the dmt.fail.no.changes where set to true the build would have stopped here", errStr));
			}
			retCode = 0;
		}
		return retCode;
	}
	
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
