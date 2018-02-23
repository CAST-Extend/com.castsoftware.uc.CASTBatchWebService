package com.castsoftware.batch.util;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;

import com.castsoftware.batch.data.AnalysisInfo;
import com.castsoftware.jenkins.data.Snapshot;
import com.castsoftware.util.GlobalProperties;
import com.castsoftware.util.GlobalPropertiesManager;

public class ManagmentDB
{
	private static final Logger logger = Logger.getLogger(ManagmentDB.class);

	private static final GlobalProperties globalProperties = GlobalPropertiesManager.getGlobalProperties();

	public ManagmentDB()
	{
	}

	private Connection getConnection()  throws SQLException, InterruptedException, IOException
	{
		String url = globalProperties.getPropertyValue("cast.database");
		String user = globalProperties.getPropertyValue("db.user");
		String password = globalProperties.getPropertyValue("db.password");

		Properties props = new Properties();
		props.setProperty("user", user);
		props.setProperty("password", password);
		return DriverManager.getConnection(url, props);
	}

	private String generateAnalysisSQLfull(String dbName)
	{
		String [] languages = {"asp","bo","cobol","j2ee","net","ora","sap","sqlsrv","syb","ua","udb","vb","zos","cpp","forms","pb","ui"};
		
		StringBuffer sqlString = new StringBuffer();
		
		for (String lang: languages)
		{
			sqlString.append(generateAnalysisSQLpart(dbName,lang));
			if (!lang.equals(languages[languages.length-1]))
			{
				sqlString.append(String.format("UNION ALL\n"));
			}
		}
		return sqlString.toString();
	}
	
	private String generateAnalysisSQLpart(String dbName, String language)
	{
		String tblName = String.format("cms_%s_analysis", language);
		String anaUnitName = String.format("\t%s.object_name AS anaUnitName,", tblName);
		String status = String.format("\t%s.execstatus AS status,", tblName);
		String log = String.format("\t%s.execlog AS log", tblName);
		
		return String.format("SELECT\n\t%s\n\t%s\n\t%s\nFROM\n\t%s.%s\nWHERE active=1\n",anaUnitName,status,log,dbName,tblName);
	}
	
	public List <AnalysisInfo> getAnalysisRunInfo(String mngtDbName)
	{
		List <AnalysisInfo> results = new ArrayList();

		PreparedStatement st = null;
		ResultSet rs = null;
		Connection conn = null;
		try {
			conn = getConnection();
			st = conn.prepareStatement(generateAnalysisSQLfull(mngtDbName));
			rs = st.executeQuery();
			int cntr = 0;
			while (rs.next()) {
				results.add(new AnalysisInfo(rs.getString(
						AnalysisInfo.FLD_ANA_UNIT_NAME), 
						rs.getString(AnalysisInfo.FLD_STATUS), 
						rs.getString(AnalysisInfo.FLD_LOG)));
				cntr++;
			}
			logger.info(String.format("%d records found",cntr));
		} catch (SQLException | InterruptedException | IOException e) {
			logger.error(String.format("Error getting the application id for %s database",  mngtDbName), e);
		} finally {
			try {
				if (rs != null) rs.close();
				if (st != null) st.close();
			} catch (SQLException e) {
				logger.error("Error closing the database", e);
			} finally {
				try {
					conn.close();
				} catch (SQLException e) {
					logger.error(String.format("Error closing database connection: %s database",  mngtDbName), e);
				}
			}
		}
		return results;
	}
}
