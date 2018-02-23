package com.castsoftware.batch.task;

import com.castsoftware.mail.MailHelper;
import com.castsoftware.reporting.ReportParams;
import com.castsoftware.taskengine.ICallBack;

public class ReportGeneratorTask extends BatchTask {
	private String recipients;
	private String templateName;
	private ReportParams reportParams;

	public ReportGeneratorTask(int taskId, ICallBack callback, String templateName, ReportParams reportParams) {
		super(taskId, callback);
		this.templateName = templateName;
		this.reportParams = reportParams;
	}
	
	@Override
	protected void runLogic()
	{
		super.runLogic();
		if ((exitVal == 0) && (recipients != null) && (!recipients.equals("")))
			sendMail();
	}
	
	private String getTitle()
	{
		return String.format("Report Generator via Jenkins - %s - %s", templateName, reportParams.getApplication());
	}
	
	private String getBody()
	{
		return String.format("Template: %s \n%s\n\nAttached File(s):%s", 
				templateName, 
				reportParams.formattedToString(""),
				getReportName());
	}
	
	private void sendMail()
	{
		output.add("Sending Reports by email");
		if (!MailHelper.sendMail(recipients, getTitle(), getBody(), getReportName()))
			exitVal = -1;
	}
	
	private String getReportName()
	{
		String reports = "";
		String fileNamePattern = "Report successfully generated in ";
		for(String s : output)
		{
			//Report successfully generated in C:/CAST/test-2014-12-19_06-58-22.docx
			if (s.contains(fileNamePattern))
			{
				if (!reports.equals(""))
					reports += ";";
				reports += s.substring(s.indexOf(fileNamePattern) + fileNamePattern.length());
			}
		}
		return reports;
	}
	
	public void setRecipients(String recipients) {
		this.recipients = recipients;
	}
	
	public String getRecipients() {
		return recipients;
	}

}
