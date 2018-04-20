package com.castsoftware.batch.task;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.soap.SOAPException;

import com.castsoftware.batch.data.PackageInfo;
import com.castsoftware.taskengine.ICallBack;
import com.castsoftware.taskengine.Task;
import com.castsoftware.util.GlobalProperties;
import com.castsoftware.util.GlobalPropertiesManager;
import com.castsoftware.vps.ValidationProbesService;
import com.google.gson.Gson;


public class BatchTask extends Task
{
	protected static final GlobalProperties globalProperties = GlobalPropertiesManager.getGlobalProperties();
	
	public static final DateFormat castDateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

	private final List<ProcessBuilder> pbList = new ArrayList<ProcessBuilder>();
	private final List<String> taskDesc = new ArrayList<String>();

	private String exitStr = " - OK";
	private String appName = "";
	private String versionName = "";
	private Date releaseDate;
	private String stepIdentifier;
	private int FinalMessage=0;

	public List<ProcessBuilder> getPbList()
	{
		return pbList;
	}

	public BatchTask(int taskId, ICallBack callback)
	{
		super(taskId, callback);
	}

	public BatchTask(int taskId, ICallBack callback, String appName, String versionName, Date releaseDate,
			String stepIdentifier)
	{
		super(taskId, callback);

		this.appName = appName;
		this.versionName = versionName;
		this.releaseDate = releaseDate;
		this.stepIdentifier = stepIdentifier;
		this.FinalMessage = taskId;
	}

	public void AddProcess(ProcessBuilder pb)
	{
		pbList.add(pb);
		taskDesc.add("");
	}

	public void AddProcess(ProcessBuilder pb, String opStr)
	{
		pbList.add(pb);
		taskDesc.add(opStr);
	}

	protected boolean validate(ProcessBuilder pb)
	{
		return true;
	}

	public void setExitStr(String exitStr)
	{
		this.exitStr = exitStr;
	}

	public String getExitStr()
	{
		return exitStr;
	}
	
	

	@Override
	protected void runLogic()
	{
		Process p;
		String opDesc = "";
		exitStr = " - OK";
		int pid = 0;
		String pidd="";
		long piddProcess=0;
		try {
			try {
				for (int n = 0; n < pbList.size(); n++) {
					ProcessBuilder pb = pbList.get(n);
					opDesc = taskDesc.get(n);
					pb.redirectErrorStream(true);

					preRunTasks(pb.command());
					
					p = pb.start();
					//piddProcess = getProcessID(p);
					pidd = ManagementFactory.getRuntimeMXBean().getName();
					//updateCurrentPID(appName,pidd);

					BufferedReader br = null;
					BufferedWriter out = null;
					try {
						br = new BufferedReader(new InputStreamReader(p.getInputStream()));
						String line = null;
						
						out = new BufferedWriter(new OutputStreamWriter(p.getOutputStream()));
						while ((line = br.readLine()) != null) 
						{
							
							if (line.toUpperCase().contains("PASSWORD"))
							{
								continue;
							}

				            
				            
							output.add(line);
							out.newLine();
							out.flush();

						}
						out.close();
					} finally {
						if (br != null) br.close();
						if (out != null) out.close();
					}

					logger.debug(String.format("Waiting for Exit Value..."));
					exitVal = p.waitFor();

					output.add("******************************");

					if (exitVal != 0 || !validate(pb)) {
						if(opDesc.equals("Backup: Managment Database "))
						{
							exitVal = 1000;
						}
						exitStr = " - Error";
						pbList.clear();
					} 
					
					int postRunExitVal = postRunTasks();
					if (postRunExitVal !=0) exitVal = postRunExitVal;

					if (!appName.isEmpty()) 
					{
						if(FinalMessage != 999)
						{
						updateValidationService(appName, versionName, releaseDate, opDesc + exitStr, stepIdentifier);
						}
					}
					
					logger.debug(String.format("Exit Value: %d", exitVal));
				}
			} catch (IOException | InterruptedException e) 
			{
				
				
				exitStr = " - Exception";
				output.add(String.format("Internal Error:  %s", e.getMessage()));
				
				
				if (!appName.isEmpty()) {
					updateValidationService(appName, versionName, releaseDate, opDesc + exitStr, stepIdentifier);
				}
			}
		} catch (IOException | InterruptedException | UnsupportedOperationException | SOAPException e) 
		{
			ArrayList<String> cmds = new ArrayList<String>();
			cmds.add("taskkill");
		    cmds.add("/T");
		    cmds.add("/F");
		    cmds.add("/PID");
		    cmds.add("" + pidd);
		    ProcessBuilder  pb1 = new ProcessBuilder(cmds);
		    try {
				pb1.start();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	protected void updateValidationService(String appName, String versionName, Date releaseDate, String status,
			String stepIdentifier) throws InterruptedException, IOException, UnsupportedOperationException,
			SOAPException
	{
		String castDate = castDateFormat.format(releaseDate);

		logger.info(" ");
		logger.info("Sending message to Application Operations Portal: ");
		logger.info(String.format(" -appName: %s ", appName));
		logger.info(String.format(" -versionName: %s ", versionName));
		logger.info(String.format(" -releaseDate: %s ", castDate));
		logger.info(String.format(" -status: %s ", status));
		logger.info(String.format(" -stepIdentifier: %s ", stepIdentifier));
		logger.info(" ");

		String validationProbService = globalProperties.getPropertyValue("validation.prob.service");
		if (validationProbService != null && validationProbService.isEmpty()) {
			output.add("Warning: Connection to AOP is not configured");
		} else {
			ValidationProbesService vps = new ValidationProbesService(validationProbService);
			output.add(String.format("Sending completion message: \"%s\" \"%s\" \"%s\" \"%s\" \"%s\"", appName, versionName, castDate,
					stepIdentifier, status));
			vps.UpdateRescanStatus(appName, versionName, castDate, status, stepIdentifier);
		}
		output.add(" ");
	}
	
	protected void updateCurrentPID(String appName, String PID) throws InterruptedException, IOException, UnsupportedOperationException,
			SOAPException
	{ 

		logger.info(" ");
		logger.info("Sending PID to Application Operations Portal: ");
		logger.info(String.format(" -appName: %s ", appName));
		logger.info(String.format(" -PID: %s ", PID)); 
		logger.info(" ");

		String validationProbService = globalProperties.getPropertyValue("validation.prob.service");
		if (validationProbService != null && validationProbService.isEmpty()) {
			output.add("Warning: Connection to AOP is not configured");
		} else {
			ValidationProbesService vps = new ValidationProbesService(validationProbService);
			output.add(String.format("Sending current PID to AOP: \"%s\" \"%s\" ", appName, PID));
			vps.setCurrentPID_IN_AOP(appName, PID);
		}
		output.add(" ");
	}
	
	public void preRunTasks(List <String> cmd)
	{
	}

	public int postRunTasks()
	{
		return 0;
	}
	
	@Override
	public boolean equals(Object obj)
	{
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}

		final BatchTask other = (BatchTask) obj;

		return pbList.equals(other.pbList);
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((pbList == null) ? 0 : pbList.hashCode());
		return result;
	}


	
	public List<String> getTaskDesc()
	{
		return taskDesc;
	}

	public String getAppName()
	{
		return appName;
	}

	public void setAppName(String appName)
	{
		this.appName = appName;
	}

	public String getVersionName()
	{
		return versionName;
	}

	public void setVersionName(String versionName)
	{
		this.versionName = versionName;
	}

	public Date getReleaseDate()
	{
		return releaseDate;
	}

	public void setReleaseDate(Date releaseDate)
	{
		this.releaseDate = releaseDate;
	}

	public String getStepIdentifier()
	{
		return stepIdentifier;
	}

	public void setStepIdentifier(String stepIdentifier)
	{
		this.stepIdentifier = stepIdentifier;
	}

}
