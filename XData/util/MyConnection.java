package util;

import java.sql.Connection;
import java.sql.DriverManager;

public class MyConnection {
public static Connection getTesterConn() throws Exception{
		
		//added by rambabu
		String tempDatabaseType = Configuration.getProperty("tempDatabaseType");
		String loginUrl = "";
		Connection conn = null;
		
		if(tempDatabaseType.equalsIgnoreCase("postgresql"))
		{
			Class.forName("org.postgresql.Driver");
			
			loginUrl = "jdbc:postgresql://" + Configuration.getProperty("databaseIP") + ":" + Configuration.getProperty("databasePort") + "/" + Configuration.getProperty("databaseName");
			conn = DriverManager.getConnection(loginUrl, Configuration.getProperty("testDatabaseUser"), Configuration.getProperty("testDatabaseUserPasswd"));;
		}
		else if(tempDatabaseType.equalsIgnoreCase("mysql"))
		{
			Class.forName("com.mysql.cj.jdbc.Driver");
			
			loginUrl = "jdbc:mysql://" + Configuration.getProperty("databaseIP") + ":" + Configuration.getProperty("databasePort") + "/" + Configuration.getProperty("databaseName");
			conn = DriverManager.getConnection(loginUrl, Configuration.getProperty("testDatabaseUser"), Configuration.getProperty("testDatabaseUserPasswd"));;
		}		
		//choosing connection based on database type 
//		if(tempDatabaseType.equalsIgnoreCase("postgresql"))
//		{
//			Class.forName("org.postgresql.Driver");
//			
//			loginUrl = "jdbc:postgresql://" + Configuration.getProperty("databaseIP") + ":" + Configuration.getProperty("databasePort") + "/" + Configuration.getProperty("databaseName");
//			conn = DriverManager.getConnection(loginUrl, Configuration.getProperty("existingDatabaseUser"), Configuration.getProperty("existingDatabaseUserPasswd"));;
//		}
//		else if(tempDatabaseType.equalsIgnoreCase("mysql"))
//		{
//			Class.forName("com.mysql.cj.jdbc.Driver");
//			
//			loginUrl = "jdbc:mysql://" + Configuration.getProperty("databaseIP") + ":" + Configuration.getProperty("databasePort") + "/" + Configuration.getProperty("databaseName");
//			conn=DriverManager.getConnection(loginUrl, Configuration.getProperty("existingDatabaseUser"), Configuration.getProperty("existingDatabaseUserPasswd"));;				
//		}		
		return conn;
	}
public static Connection getDatabaseConnection() throws Exception{
	
	//added by rambabu
	String tempDatabaseType = Configuration.getProperty("tempDatabaseType");
	String loginUrl = "";
	Connection conn = null;
//	if(tempDatabaseType.equalsIgnoreCase("postgresql"))
//		{
//			Class.forName("org.postgresql.Driver");
//			
//			loginUrl = "jdbc:postgresql://" + Configuration.getProperty("databaseIP") + ":" + Configuration.getProperty("databasePort") + "/" + Configuration.getProperty("databaseName");
//			conn = DriverManager.getConnection(loginUrl, Configuration.getProperty("existingDatabaseUser"), Configuration.getProperty("existingDatabaseUserPasswd"));;
//		}
//		else if(tempDatabaseType.equalsIgnoreCase("mysql"))
//		{
//			Class.forName("com.mysql.cj.jdbc.Driver");
//			
//			loginUrl = "jdbc:mysql://" + Configuration.getProperty("databaseIP") + ":" + Configuration.getProperty("databasePort") + "/" + Configuration.getProperty("databaseName");
//			conn=DriverManager.getConnection(loginUrl, Configuration.getProperty("existingDatabaseUser"), Configuration.getProperty("existingDatabaseUserPasswd"));;				
//		}
	
	//choosing connection based on database type 
	if(tempDatabaseType.equalsIgnoreCase("postgresql"))
	{
		Class.forName("org.postgresql.Driver");
		
		loginUrl = "jdbc:postgresql://" + Configuration.getProperty("databaseIP") + ":" + Configuration.getProperty("databasePort") + "/" + Configuration.getProperty("databaseName");
		conn = DriverManager.getConnection(loginUrl, Configuration.getProperty("testDatabaseUser"), Configuration.getProperty("testDatabaseUserPasswd"));;
	}
	else if(tempDatabaseType.equalsIgnoreCase("mysql"))
	{
		Class.forName("com.mysql.cj.jdbc.Driver");
		
		loginUrl = "jdbc:mysql://" + Configuration.getProperty("databaseIP") + ":" + Configuration.getProperty("databasePort") + "/" + Configuration.getProperty("databaseName");
		conn = DriverManager.getConnection(loginUrl, Configuration.getProperty("testDatabaseUser"), Configuration.getProperty("testDatabaseUserPasswd"));;
	}		
	return conn;
}
}
