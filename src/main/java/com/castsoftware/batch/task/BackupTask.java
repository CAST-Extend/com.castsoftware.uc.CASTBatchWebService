package com.castsoftware.batch.task;

import java.io.File;
import java.util.Date;
import java.util.List;

import com.castsoftware.taskengine.ICallBack;

public class BackupTask extends BatchTask
{
	public BackupTask(int taskId, ICallBack callback)
	{
		super(taskId, callback);
	}

	public BackupTask(int taskId, ICallBack callback, String appName, String versionName, Date releaseDate,
			String stepIdentifier)
	{
		super(taskId, callback, appName, versionName, releaseDate, stepIdentifier);
	}

	protected boolean validate(ProcessBuilder pb)
	{
		List<String> cmdList = pb.command();
		boolean found=false;
		int index=0;
		for (int ii=0;ii<cmdList.size();ii++)
		{
			if (cmdList.get(ii).equals("-file"))
			{
				found=true;
				index=ii+1;
				break;
			}
		}
		
		if (found)
		{
			if ( !(new File ((String) cmdList.get(index)).exists()) )
			{
				return false;
			}
		}
		return true;
	}	
}
