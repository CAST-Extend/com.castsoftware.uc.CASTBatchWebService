package com.castsoftware.batch.data;

public class PackageInfo
{
	private String pkgName = "";
	private String extn = "";
	private String added = "";
	private String removed = "";
	private String changed = "";
	private String unchanged = "";
	private String totals = "";
	private String alerts = "";

	public PackageInfo (String pkgName, String alerts)
	{
		this.pkgName = pkgName;
		this.extn = "";
		this.added = "";
		this.removed = "";
		this.changed = "";
		this.unchanged = "";
		this.totals = "";
		this.alerts = alerts;
	}
		
	public PackageInfo (String pkgName, String extn, String added, String removed, String changed,  String totals)
	{
		this.pkgName = pkgName;
		this.extn = extn;
		this.added = added;
		this.removed = removed;
		this.changed = changed;
		this.unchanged = unchanged;
		this.totals = totals;
		this.alerts = "";
	}

	public String getPkgName()
	{
		return pkgName;
	}

	public String getExtn()
	{
		return extn;
	}

	public String getAdded()
	{
		return added;
	}

	public String getRemoved()
	{
		return removed;
	}

	public String getChanged()
	{
		return changed;
	}

	public String getUnchanged()
	{
		return unchanged;
	}

	public String getTotals()
	{
		return totals;
	}

	public String getAlerts()
	{
		return alerts;
	}

}
