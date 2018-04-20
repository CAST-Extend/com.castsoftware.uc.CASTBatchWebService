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

import com.castsoftware.jenkins.data.Snapshot;
import com.castsoftware.util.GlobalProperties;
import com.castsoftware.util.GlobalPropertiesManager;

public class CentralDB {
  private static final Logger           logger           = Logger.getLogger(CentralDB.class);

  private static final GlobalProperties globalProperties = GlobalPropertiesManager.getGlobalProperties();

  public CentralDB()
  {
  }

  private Connection getConnection() throws SQLException, InterruptedException, IOException
  {
    String url = globalProperties.getPropertyValue("cast.database");
    String user = globalProperties.getPropertyValue("db.user");
    String password = globalProperties.getPropertyValue("db.password");

    Properties props = new Properties();
    props.setProperty("user", user);
    props.setProperty("password", password);
    return DriverManager.getConnection(url, props);
  }

  public long getSiteId(String centralDbName)
  {
    long siteId = 0;

    PreparedStatement st = null;
    ResultSet rs = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      st = conn.prepareStatement(String.format("select distinct site_id from %s.sys_site", centralDbName));
      rs = st.executeQuery();
      if (rs.next())
      {
        siteId = rs.getLong("site_id");
      }
    } catch (SQLException | InterruptedException | IOException e)
    {
      siteId = -1;
      logger.error(String.format("Error getting the site id for %s database", centralDbName), e);
    } finally
    {
      try
      {
        if (rs != null)
          rs.close();
        if (st != null)
          st.close();
      } catch (SQLException e)
      {
        logger.error("Error closing the database", e);
      } finally
      {
        try
        {
          conn.close();
        } catch (SQLException e)
        {
          logger.error(String.format("Error closing database connection: %s database", centralDbName), e);
        }
      }

    }
    return siteId;
  }

  public int getApplicationId(String centralDbName, String applicationName)
  {
    int appId = 0;

    PreparedStatement st = null;
    ResultSet rs = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      st = conn.prepareStatement(
          String.format("select distinct app_id from %s.csv_portf_tree where app_name=?", centralDbName));
      st.setString(1, applicationName);
      rs = st.executeQuery();
      if (rs.next())
      {
        appId = rs.getInt("app_id");
      }
    } catch (SQLException | InterruptedException | IOException e)
    {
      appId = -1;
      logger.error(String.format("Error getting the application id for %s database", centralDbName), e);
    } finally
    {
      try
      {
        if (rs != null)
          rs.close();
        if (st != null)
          st.close();
      } catch (SQLException e)
      {
        logger.error("Error closing the database", e);
      } finally
      {
        try
        {
          conn.close();
        } catch (SQLException e)
        {
          logger.error(String.format("Error closing database connection: %s database", centralDbName), e);
        }
      }
    }
    return appId;
  }

  public int renameSnapshot(String centralDbName, int snapshotId, String newName)
  {
    int rslt = 0;
    
    PreparedStatement st = null;
    ResultSet rs = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      st = conn.prepareStatement(
          String.format("update %s.dss_snapshots set snapshot_name=?  where snapshot_id=?", centralDbName));
      st.setString(1, newName);
      st.setInt(2, snapshotId);
      rslt = st.executeUpdate();
      st.close();
      
      st = conn.prepareStatement(
          String.format("update %s.dss_snapshot_info set object_version=?  where snapshot_id=?", centralDbName));
      st.setString(1, newName);
      st.setInt(2, snapshotId);
      return st.executeUpdate() + rslt;
      
    } catch (SQLException | InterruptedException | IOException e)
    {
      logger.error(String.format("Error getting the application id for %s database", centralDbName), e);
    } finally
    {
      try
      {
        if (rs != null)
          rs.close();
        if (st != null)
          st.close();
      } catch (SQLException e)
      {
        logger.error("Error closing the database", e);
      } finally
      {
        try
        {
          conn.close();
        } catch (SQLException e)
        {
          logger.error(String.format("Error closing database connection: %s database", centralDbName), e);
        }
      }
    }
    return rslt;
  }

  public List<Snapshot> getSnapshotInfo(String centralDbName, String appName)
  {
    List<Snapshot> results = new ArrayList();

    PreparedStatement st = null;
    ResultSet rs = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      st = conn.prepareStatement(String.format("select * from %s.dss_snapshots", centralDbName));
      // st = conn.prepareStatement(String.format("select * from
      // %s.dss_snapshots where application_id=?",centralDbName));
      // int appId = getApplicationId(centralDbName, appName);
      // st.setInt(1, appId);
      rs = st.executeQuery();
      int cntr = 0;
      while (rs.next())
      {
        results.add(new Snapshot(rs.getInt(Snapshot.FLD_SNAPSHOT_ID), rs.getInt(Snapshot.FLD_APPLICATION_ID),
            rs.getTimestamp(Snapshot.FLD_FUNCTIONAL_DATE), rs.getInt(Snapshot.FLD_SNAPSHOT_TYPE_ID),
            rs.getString(Snapshot.FLD_SNAPSHOT_NAME), rs.getString(Snapshot.FLD_SNAPSHOT_DESCRIPTION),
            rs.getTimestamp(Snapshot.FLD_SNAPSHOT_DATE), rs.getTimestamp(Snapshot.FLD_COMPUTE_START_DATE),
            rs.getTimestamp(Snapshot.FLD_COMPUTE_END_DATE), rs.getInt(Snapshot.FLD_SNAPSHOT_STATUS)));
        cntr++;
      }

      logger.info(String.format("%d snapshots found", cntr));
    } catch (SQLException | InterruptedException | IOException e)
    {
      logger.error(String.format("Error getting the application id for %s database", centralDbName), e);
    } finally
    {
      try
      {
        if (rs != null)
          rs.close();
        if (st != null)
          st.close();
      } catch (SQLException e)
      {
        logger.error("Error closing the database", e);
      } finally
      {
        try
        {
          conn.close();
        } catch (SQLException e)
        {
          logger.error(String.format("Error closing database connection: %s database", centralDbName), e);
        }
      }
    }

    return results;
  }
}
