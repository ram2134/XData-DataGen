package testDataGen;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import util.MyConnection;
import util.Configuration;
import util.TableMap;
import util.Utilities;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import parsing.AppTest_Parameters;
import partialMarking.CanonicalizeQuery;

public class GenerateDataSet {
	
	private static Logger logger = Logger.getLogger(PopulateTestData.class.getName());
		/*
		 * This function generates test datasets for a query
		 * @param conn Database connection to use
		 * @param queryId The query id
		 * @param query The query for which dataset needs to be generated
		 * @param schemaFile File containing the schema against which the query has been written
		 * @param sampleDataFile File containing sample data to generate realistic values
		 * @param orderDependent Whether the order of tuples in the result matter. Set this to true for queries that have ORDER BY
		 * @param tempFilePath File path to create temporary files and datasets
		 * @return List of dataset ids that have been generated
		 * @throws Exception
		 */
		public List<String> generateDatasetForQuery(Connection conn,int queryId,String query, File schemaFile, File sampleDataFile, boolean orderDependent, String tempFilePath, AppTest_Parameters obj) throws Exception{
			String line,schema="",sampleData="";			
			
			schema=Utilities.readFile(schemaFile);
			
			sampleData=Utilities.readFile(sampleDataFile);
			
			return generateDatasetForQuery(conn, queryId, query, schema, sampleData, orderDependent, tempFilePath, obj);
		}
		public GenerateCVC1 processCanonicalize(Connection conn,int queryId,String query, String schema, String sampleData, boolean orderDependent, String tempFilePath, AppTest_Parameters appTestParams) throws Exception{
			if(tempFilePath==null | tempFilePath.equals("")){
				tempFilePath="/tmp/"+queryId;
			}
			
			GenerateCVC1 cvc=new GenerateCVC1();
			cvc.setFilePath(tempFilePath);
			cvc.setFne(false); 
			cvc.setIpdb(false);
			cvc.setOrderindependent(orderDependent);	
			
			
			loadSchema(conn,schema);
			loadSampleData(conn,sampleData);
			
			cvc.setSchemaFile(schema);
			cvc.setDataFileName(sampleData);
			
			TableMap.clearAllInstances();
			cvc.setTableMap(TableMap.getInstances(conn, 1));
			cvc.setConnection(conn);
			
			deletePreviousDatasets(cvc);
			//Application Testing
			if(appTestParams==null)
				appTestParams=new AppTest_Parameters();
			cvc.setDBAppparams(appTestParams);
			//end
			FileWriter fw=new FileWriter(Configuration.homeDir+"/temp_smt" +cvc.getFilePath()+"/queries.txt");
			fw.write(query);
			fw.close();
			
//			System.out.println("1-Inside GenerateDataSets->generatedatasetforQuery");
			
			PreProcessingActivity.preProcessingActivity(cvc);
			
			CanonicalizeQuery.Canonicalize(cvc.getqStructure());
			return cvc;
		}
		/**
		 * This function generates test datasets for a query
		 * @param conn Database connection to use
		 * @param queryId The query id
		 * @param query The query for which dataset needs to be generated
		 * @param schema The schema against which the query has been written
		 * @param sampleData Sample data to generate realistic values
		 * @param orderDependent Whether the order of tuples in the result matter. Set this to true for queries that have ORDER BY
		 * @param tempFilePath File path to create temporary files and datasets
		 * @return List of dataset ids that have been generated
		 * @throws Exception
		 */
		public List<String> generateDatasetForQuery(Connection conn,int queryId,String query, String schema, String sampleData, boolean orderDependent, String tempFilePath, AppTest_Parameters appTestParams) throws Exception{
			
			if(tempFilePath==null | tempFilePath.equals("")){
				tempFilePath="/tmp/"+queryId;
			}
			
			GenerateCVC1 cvc=new GenerateCVC1();
			cvc.setFilePath(tempFilePath);
			cvc.setFne(false); 
			cvc.setIpdb(false);
			cvc.setOrderindependent(orderDependent);	
			
			
			loadSchema(conn,schema);
			loadSampleData(conn,sampleData);
			
			cvc.setSchemaFile(schema);
			cvc.setDataFileName(sampleData);
			
			TableMap.clearAllInstances();
			cvc.setTableMap(TableMap.getInstances(conn, 1));
			cvc.setConnection(conn);
			
			deletePreviousDatasets(cvc);
			//Application Testing
			if(appTestParams==null)
				appTestParams=new AppTest_Parameters();
			cvc.setDBAppparams(appTestParams);
			//end
			FileWriter fw=new FileWriter(Configuration.homeDir+"/temp_smt" +cvc.getFilePath()+"/queries.txt");
			fw.write(query);
			fw.close();
			
//			System.out.println("1-Inside GenerateDataSets->generatedatasetforQuery");
			
			PreProcessingActivity.preProcessingActivity(cvc);
			//Added by Akanksha
//			System.out.println("2-Inside GenerateDataSets->generatedatasetforQuery");
			
			//Added by Akanksha end's
			return listOfDatasets(cvc);
				
		}
		
		/**
		 * Creates tables provided in the schema for the given connection
		 * @param conn
		 * @param schema
		 * @throws Exception
		 */
		public static void loadSchema(Connection conn,String schema) throws Exception{
			
			byte[] dataBytes = null;
			String tempFile = "";
			FileOutputStream fos = null;
			ArrayList<String> listOfQueries = null;
			ArrayList<String> listOfDDLQueries = new ArrayList<String>();
			String[] inst = null;
			
			dataBytes = schema.getBytes();
			tempFile = "/tmp/dummy";
			
			fos = new FileOutputStream(tempFile);
			fos.write(dataBytes);
			fos.close();
			listOfQueries = Utilities.createQueries(tempFile);
			inst = listOfQueries.toArray(new String[listOfQueries.size()]);
			listOfDDLQueries.addAll(listOfQueries);
//			System.out.println("1-Entering GenerateDataSet->loadSchema");
			deleteAllTablesFromTestUser(conn);
//			System.out.println("2-Entering GenerateDataSet->loadSchema");
			for (int i = 0; i < inst.length; i++) {
				 
				if (!inst[i].trim().equals("") && ! inst[i].trim().contains("drop table")) {
					DatabaseMetaData dbmd=conn.getMetaData();      
					String dbType = dbmd.getDatabaseProductName(); 
					String temp = "";
					if (dbType.equalsIgnoreCase("MySql"))
					{
						temp = inst[i].trim();
					}
					else if(dbType.equalsIgnoreCase("PostgreSQL")) {
						temp = inst[i].trim().replaceAll("(?i)^\\s*create\\s+table\\s+", "create temporary table ");	
					}
					PreparedStatement stmt2 = conn.prepareStatement(temp);
						stmt2.executeUpdate();	
					stmt2.close();
				}
				
			}
		}
		
		/**
		 * Loads datasets for the given connection
		 * @param conn
		 * @param sampleData
		 * @throws Exception
		 */
		public static void loadSampleData(Connection conn, String sampleData) throws Exception{
			
			byte[] dataBytes = null;
			String tempFile = "/tmp/dummy";
			FileOutputStream fos = null;
			ArrayList<String> listOfQueries = null;
			String[] inst = null;
		
			dataBytes = sampleData.getBytes(); 
			fos = new FileOutputStream(tempFile);
			fos.write(dataBytes);
			fos.close();
//			System.out.println("1-Entering GenerateDataSet->loadSampleData");
			listOfQueries = Utilities.createQueries(tempFile);
			inst = listOfQueries.toArray(new String[listOfQueries.size()]);
//			System.out.println("2-Entering GenerateDataSet->loadSampleData");

			for (int i = 0; i < inst.length; i++) {
				if (!inst[i].trim().equals("") && !inst[i].contains("drop table") && !inst[i].contains("delete from")) {
					
					PreparedStatement stmt = conn.prepareStatement(inst[i]);
						stmt.executeUpdate();							
						stmt.close();
				}
			}
		}
		
		private List<String> listOfDatasets(GenerateCVC1 cvc) {
			ArrayList<String> fileListVector = new ArrayList<String>();		
			ArrayList<String> datasets = new ArrayList<String>();
			String fileList[]=new File(Configuration.homeDir+"/temp_smt" + cvc.getFilePath()).list();
			for(int k=0;k<fileList.length;k++){
				fileListVector.add(fileList[k]);
			}
			Collections.sort(fileListVector);	        
			for(int i=0;i<fileList.length;i++)
			{
				File f1=new File(Configuration.homeDir+"/temp_smt" + cvc.getFilePath() +"/"+fileListVector.get(i));	          
				
				if(f1.isDirectory() && fileListVector.get(i).startsWith("DS"))
				{
					datasets.add(fileListVector.get(i));
				}
			}
//			System.out.println(datasets);//added by ramachandra
			return datasets;
		}
		
		
		public static void deletePreviousDatasets(GenerateCVC1 cvc) throws IOException,InterruptedException {
//			System.out.println("1-Inside GenerateDataSet->deletePreviousDataSets");
			File f=new File(Configuration.homeDir+"/temp_smt"+cvc.getFilePath()+"/");
			
			if(f.exists()){		
				File f2[]=f.listFiles();
				if(f2 != null)
				for(int i=0;i<f2.length;i++){
					if(f2[i].isDirectory() && f2[i].getName().startsWith("DS")){
						
						Utilities.deletePath(Configuration.homeDir+"/temp_smt"+cvc.getFilePath()+"/"+f2[i].getName());
					}
				}
			}
			
			File dir= new File(Configuration.homeDir+"/temp_smt"+cvc.getFilePath());
			if(dir.exists()){
				for(File file: dir.listFiles()) {
					file.delete();
				}
			}
			else{
				dir.mkdirs();
			}
		}
		
		public static void deleteAllTablesFromTestUser(Connection conn) throws Exception{
			try{
				DatabaseMetaData dbm = conn.getMetaData();

				// added by rambabu
				String dbType = dbm.getDatabaseProductName(); 
				//System.out.println(dbType);
				
				if (dbType.equalsIgnoreCase("MySql"))
				{
					String[] types = {"TABLE"};
					ResultSet rs = dbm.getTables(conn.getCatalog(), null, "%", types);	
				
//					ResultSet rs = dbm.getTables(null, null, "%", types);
//					while (rs.next()) {
//					  System.out.println(rs.getString(3));
//					}
					String query= "SET FOREIGN_KEY_CHECKS = 0";
					PreparedStatement pstmt = conn.prepareStatement(query);
					pstmt.executeUpdate();
					pstmt.close();
					
					while(rs.next()){
						String table=rs.getString("TABLE_NAME");		
						if(!table.equalsIgnoreCase("dataset") 
								&& !table.equalsIgnoreCase("xdata_temp1")
								&& !table.equalsIgnoreCase("xdata_temp2")){
							System.out.println("drop table if exists "+table +" cascade");
							//PreparedStatement pstmt = conn.prepareStatement("delete from "+table);						
//							PreparedStatement pstmt = conn.prepareStatement("drop table if exists "+table +" cascade");
//							pstmt.executeUpdate();
//							pstmt.close();
							
							query = "drop table if exists "+table;
							PreparedStatement pstmt1 = conn.prepareStatement(query);
							pstmt1.executeUpdate();
							pstmt1.close();
						}

					}
					
					query= "SET FOREIGN_KEY_CHECKS = 1";
					PreparedStatement pstmt2 = conn.prepareStatement(query);
					pstmt2.executeUpdate();
					pstmt2.close();
					
					rs.close();
					
				}
				
				else if(dbType.equalsIgnoreCase("postgreSQL"))
				{
					String[] types = {"TEMPORARY TABLE"};
					ResultSet rs = dbm.getTables(conn.getCatalog(), null, "%", types);		  
		
					while(rs.next()){
						String table=rs.getString("TABLE_NAME");	
						
					
					if(!table.equalsIgnoreCase("dataset") 
								&& !table.equalsIgnoreCase("xdata_temp1")
								&& !table.equalsIgnoreCase("xdata_temp2")){
							//PreparedStatement pstmt = conn.prepareStatement("delete from "+table);
//						System.out.println("1-Inside GenerateDataSet->deleteAllTablesFromTestUser");
							PreparedStatement pstmt = conn.prepareStatement("Truncate table "+table +" cascade");
//							System.out.println("2-Inside GenerateDataSet->deleteAllTablesFromTestUser");
							pstmt.executeUpdate();
//							System.out.println("3-Inside GenerateDataSet->deleteAllTablesFromTestUser");
							pstmt.close();
//							System.out.println("4-Inside GenerateDataSet->deleteAllTablesFromTestUser");
						}
		
					} 
				
					rs.close();
				}
				
			}catch(Exception e){
				System.out.println("5-Inside GenerateDataSet->deleteAllTablesFromTestUser");
				//System.out.println(e.printStackTrace());
				//e.printStackTrace();
				//Added by Akku
				logger.log(Level.SEVERE,"Error executing statement :  "+e.getMessage(),e);
				//Added by Akku ends
				
			}

		}
		
		public static void main(String[] args) throws Exception {
			
			//TEMPCODE START : Rahul Sharma
			// REGRESSION TEST
			int regression_test = 0;
			if(regression_test==1) {
				int start = 1;
				int end = 54;
//				end = start = 27;
				for(int i=start;i<=end;i++) {
					int queryId = i;
					String query = readQueryFromFile("test/universityTest/queries.txt", queryId+"");
					String tempDatabaseType = Configuration.getProperty("tempDatabaseType");
					String loginUrl = "";
					Connection conn = null;
					
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
						conn=DriverManager.getConnection(loginUrl, Configuration.getProperty("testDatabaseUser"), Configuration.getProperty("testDatabaseUserPasswd"));;				
					}
					try {
						File schemaFile=new File("test/universityTest/DDL.sql");
						File sampleDataFile=new File("test/universityTest/sampleData.sql");
						boolean orderDependent=false;
						/* runtime analysis for regression test */
						long startTime = System.currentTimeMillis();
						String tempFilePath=File.separator +queryId;
						
						GenerateDataSet d=new GenerateDataSet();
						//Application Testing
						AppTest_Parameters obj = new AppTest_Parameters();
			
			
						//end
						List<String> dataset = d.generateDatasetForQuery(conn,queryId,query,  schemaFile,  sampleDataFile,  orderDependent,  tempFilePath, obj);
						for(String s:dataset) {
							System.out.println(s);
						}
						
						long stopTime = System.currentTimeMillis();
						long elapsedTime = stopTime - startTime;
				        System.out.println("Total time taken for data generation of the query " +queryId+" is : ");
				        System.out.println(elapsedTime);
					}
					catch(Exception e) {
						e.printStackTrace();
						System.out.println("Query : "+query+"\n"+e);
					}
				}
				//TEMPCODE END : Rahul Sharma
			}
			else {
			
			
				String tempDatabaseType = Configuration.getProperty("tempDatabaseType");
				String loginUrl = "";
				Connection conn = null;
				
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
					conn=DriverManager.getConnection(loginUrl, Configuration.getProperty("testDatabaseUser"), Configuration.getProperty("testDatabaseUserPasswd"));;				
				}
				
				
				//String query = "select id, name from student where tot_cred>=30";
	
				int queryId=1000;
				//String query = "select count(distinct ID) from takes where (course_id , sec_id , semester , year) in (select course_id , sec_id , semester , year from teaches where teaches.ID = '10101')";
				//String query = "SELECT course_id, title FROM course INNER JOIN section USING(course_id)\n"
						//+ "WHERE year = 2010 and \n"
						//+ "NOT EXISTS (SELECT * FROM prereq JOIN teaches USING(course_id)\n"
						//+ "WHERE prereq_id='CS-201' and prereq.course_id = course.course_id);\n"
						//+ "";
				
				//String query = "with dept_count(dept_name, cnt) as (select  dept_name, count(*) from takes, course where takes.course_id = course.course_id group by dept_name), maxcnt(cnt) as (select max(cnt) from dept_count) select dept_name from dept_count, maxcnt where dept_count.cnt = maxcnt.cnt";
				
				//String query = "select course_id from section as S where semester = 'Fall' and year = 2009 and exists (select * from section as T where semester = 'Spring' and year = 2010 and S.course_id = T.course_id)";
				
			/*
			 * String query = "SELECT course_id, title FROM course " +
			 * "inner join section WHERE year = 2010 and course.course_id = 'CS-203' " +
			 * "and  EXISTS (SELECT * FROM prereq WHERE prereq_id='CS-201' " +
			 * "AND prereq.course_id = course.course_id) ";
			 */
						// + ")";
				 /* ---->>>problem with this particular query
				 */
	
				  // String query="select id,name from student";
				 //*/
				//String query = "SELECT course_id, title FROM course inner join section WHERE year = 2010 and  EXISTS (SELECT * FROM prereq WHERE prereq_id='CS-201' AND prereq.course_id = course.course_id) ";
				 //* ---->>>problem with this particular query
				 //*/
	//			String query = "select name from instructor where salary is null";
				// Query to test in clause in nested subqueries
	//			String query = "SELECT course_id, title FROM course "
	//					+ "inner join section WHERE course.course_id = 'CS-203' "
	//					+ "and course_id in (SELECT prereq.course_id FROM prereq WHERE prereq_id='CS-201' "
	//					// + "AND prereq.course_id = course.course_id) ";
	//					+ ")";
				
	//			String query = "SELECT course_id FROM section";
	//			String query = "SELECT course_id FROM section where semester='Summer'";
	//			String query = "SELECT course_id FROM section natural join course";
				
	//			String query="SELECT course.course_id, title FROM course inner join section ON course.course_id = section.course_id WHERE course.course_id = 'CS-203' and section.course_id in (SELECT prereq.course_id FROM prereq WHERE prereq_id='CS-201' AND prereq.course_id = course.course_id)";
				
				
	//			String query = "SELECT course_id, title FROM course "
	//					+ "inner join section WHERE year = 2010 and course.course_id = 'CS-203' "
	//					+ "and course_id in (SELECT prereq.course_id FROM prereq WHERE prereq_id='CS-201' "
	//					// + "AND prereq.course_id = course.course_id) ";
	//					+ ")";
				
	//			String query = "SELECT course_id, title FROM course "
	//					+ "inner join section WHERE year = 2010 and course.course_id = 'CS-203'";
	//					// + "AND prereq.course_id = course.course_id) ";
				
				
				// queries TEMPCODE : Rahul Sharma
				
//				String query = "SELECT * FROM instructor "
//							 + "WHERE dept_name in (SELECT dept_name FROM department "
//							 + 						"WHERE building = 'Watson')";
//				
//				queryId = 1000;
//				String query = "SELECT course_id, title "
//							 + "FROM course INNER JOIN section USING(course_id) "
//							 + "WHERE year = 2010 AND "
//							 + "EXISTS (SELECT * FROM prereq "
//							 + 			"WHERE prereq_id='CS-201' AND "
//							 + 			"prereq.course_id = course.course_id)";
//				
//				
	//			String query = "SELECT takes.course_id "
	//					+ "FROM student INNER JOIN takes "
	//					+ "ON(student.id=takes.id) "
	//					+ "WHERE student.id = '12345'";
				
	//			String query = "SELECT takes.course_id "
	//					+ "FROM student INNER JOIN takes "
	//					+ "ON(student.id=takes.id) INNER JOIN course "
	//					+ "ON(course.course_id=takes.course_id) "
	//					+ "WHERE student.id = '12345'";
	//			
	//			String query = "SELECT course_id, title FROM course "
	//					+ "inner join section WHERE year = 2010 and course.course_id = 'CS-203' "
	//					+ "and course_id in (SELECT prereq.course_id FROM prereq WHERE prereq_id='CS-201' )";
	//			
	//			String query = "SELECT course_id, title FROM course "
	//					+ "inner join section WHERE year = 2010 and course.course_id = 'CS-203' "
	//					+ "and course_id in (SELECT prereq.course_id FROM prereq WHERE prereq_id='CS-201' "
	//					 + "AND prereq.course_id = course.course_id)";
				
//				String query="SELECT course_id, title \n" + 
//						"    FROM course inner join section \n" + 
//						"    WHERE year = 2010 and course.course_id='CS-203' and course_id in \n" + 
//						"    (SELECT prereq.course_id FROM prereq WHERE prereq_id='CS-201');";
				
//				String query = "SELECT id,course_id "
//							 + "FROM student LEFT OUTER JOIN "
//							 + "(SELECT * FROM takes WHERE takes.year=2018) "
//							 + "USING(id)";
				
//				String query = "SELECT course_id, title \n" + 
//						"    FROM course INNER JOIN section USING(course_id) \n" + 
//						"    WHERE year = 2010 \n" + 
//						"    AND EXISTS (SELECT * FROM prereq \n" + 
//						"                WHERE prereq_id='CS-201' AND \n" + 
//						"                      prereq.course_id = course.course_id)";
//				
//				String query = "SELECT name FROM instructor " + 
//							   "WHERE EXISTS (SELECT * FROM teaches t1 "
//							   + 			 "WHERE instructor.ID = t1.ID and "
//							   + 			 "EXISTS (SELECT * FROM teaches t2 "
//							   + 					 "WHERE t2.ID=t1.ID and t2.year=2010))";
				
//				String query = "SELECT name FROM instructor "
//						     + "WHERE EXISTS (SELECT * FROM teaches t1 "
//						     + 				 "WHERE instructor.ID = t1.ID and "
//						     + 				 "NOT EXISTS (SELECT * FROM teaches t2 "
//						     + 							 "WHERE t2.ID=t1.ID and t2.year=2010))\n" + 
//						"";
				
//				
				// TEMPCODE Rahul Sharma : QUERIES (FOR SUBQUERY TABLE)
//				queryId = 500;
//				String query = "SELECT DISTINCT t1.ID, name, t1.year "
//					     + "FROM takes t1 , student s2 "
//					     + "WHERE t1.ID=s2.ID and "
//					     + "EXISTS ( SELECT s1.course_id from takes t2 inner join section s1"
//					     + 			" ON t2.course_id=s1.course_id WHERE t2.ID=s2.ID )";
//				
//				queryId=501;
//				String query = "SELECT DISTINCT t1.ID, name, t1.year "
//						     + "FROM takes t1 , student s2 "
//						     + "WHERE t1.ID=s2.ID and "
//						     + "EXISTS ( SELECT s1.course_id, min(t2.year) from takes t2 inner join section s1"
//						     + 			" ON t2.course_id=s1.course_id WHERE t2.ID=s2.ID group by s1.course_id )";
//				
				
//				queryId=502;
//				String query = "SELECT * FROM takes t1 ,student s2 " 
//						 +" WHERE t1.ID=s2.ID and "
//						 +" t1.year = (SELECT min(t2.year) FROM takes t2 INNER JOIN section s1 "
//						 +"            ON t2.course_id=s1.course_id "
//						 +"            WHERE t2.ID=s2.ID)";
//				
			
//				
//				queryId=503;
//				String query = "SELECT course_id, title FROM course "
//						 + "inner join section WHERE year = 2010 and course.course_id = 'CS-203' "
//					     + "and "
//					     + " exists (SELECT prereq.course_id FROM prereq join teaches "
//					     		+ "on teaches.course_id = prereq.course_id "
//					     		+ "WHERE prereq_id = 'CS-201' and course.course_id = prereq.course_id )";
//				
//				queryId=504;
//				String query = "SELECT course_id, title FROM course "
//						 + "inner join section WHERE year = 2010 and course.course_id = 'CS-203' "
//					     + "and "
//					     + "not exists (SELECT prereq.course_id FROM prereq join teaches "
//					     		+ "on teaches.course_id = prereq.course_id "
//					     		+ "WHERE prereq_id = 'CS-201' and course.course_id = prereq.course_id )";
				
//				queryId=505;
//				String query = "SELECT DISTINCT t1.ID, s1.name, t1.year "
//					     + "FROM takes t1 , student s1 "
//					     + "WHERE t1.ID=s1.ID and "
//					     + "EXISTS (SELECT * from takes t2 inner join section s2 "
//					     + 			"ON t2.course_id=s2.course_id WHERE t2.year > t1.year)"; 
//				
//				
//				queryId=506;
//				String query="SELECT course.course_id,title "
//						+ "FROM course INNER JOIN section "
//						+ "ON course.course_id=section.course_id "
//						+ "WHERE year=2010 "
//						+ "and course.course_id='CS-203' and "
//						+ "EXISTS "
//						+ "(SELECT prereq.prereq_id,count(*) "
//						+ "FROM prereq join teaches "
//						+ "ON teaches.course_id=prereq.course_id "
//						+ "WHERE prereq_id='CS-201' and "
//						+ "course.course_id=prereq.course_id "
//						+ "GROUP BY prereq.prereq_id)";
//				
//				queryId = 507;
//				String query="SELECT * FROM takes t1 ,student s2 " + 
//						"    WHERE t1.ID=s2.ID and " + 
//						"          t1.year = (SELECT min(t2.year) " + 
//						"                     FROM takes t2 INNER JOIN section s1 " + 
//						"                     ON t2.course_id=s1.course_id " + 
//						"                     WHERE t2.ID=s2.ID and " + 
//						"                           EXISTS (SELECT c1.building " + 
//						"                                   FROM classroom c1 INNER JOIN section s3 " + 
//						"                                   ON c1.building=s3.building " + 
//						"                                   WHERE c1.capacity>50))";
//				
//				queryId = 508;
//				String query = "SELECT name FROM instructor "
//						+ "WHERE EXISTS (SELECT * FROM teaches t1 "
//						+ "       WHERE instructor.ID = t1.ID  and EXISTS (select * from teaches t2 where t2.ID=t1.ID and t1.year=2010))";
//						
				
				// TEMPCODE Rahul Sharma : Queries end				
			
			 //     query ="SELECT team.team_name FROM team, match WHERE match.season_year = 2015 AND match.toss_winner != match.match_winner AND team.team_id = match.match_winner;\n";
				
//				queryId = 1002;     // Query not supported
//				String query = "SELECT s1 . course_id , s1 . building FROM section s1 WHERE\n"
//						+ "semester = 'Fall' and year = 2009 and ( s1 . course_id , s1 .\n"
//						+ "building ) IN ( SELECT s2 . course_id , s2 . building FROM\n"
//						+ "section s2 WHERE semester = 'Spring' and year = 2010)";
				
				//AGGREGATION operation
//				queryId = 1000;
				String query = "select dept_name, avg(salary) as avg_salary from instructor group by dept_name having count(name) > 4;";
				
//				queryId = 5;
//				String query = "select avg (salary) from instructor where dept_name= 'Comp. Sci.'";
				
				System.out.println("\n"+query+"\n");
				
				File schemaFile=new File("test/universityTest/DDL.sql");
				File sampleDataFile=new File("test/universityTest/sampleData.sql");
				boolean orderDependent=false;
				long startTime = System.currentTimeMillis();
				String tempFilePath=File.separator +queryId;
				
				GenerateDataSet d=new GenerateDataSet();
				//Application Testing
				AppTest_Parameters obj = new AppTest_Parameters();
	
	
				//end
				List<String> dataset = d.generateDatasetForQuery(conn,queryId,query,  schemaFile,  sampleDataFile,  orderDependent,  tempFilePath, obj);
				for(String s:dataset) {
					System.out.println(s);
				}
				System.out.println("\nNumber of datatsets: "+dataset.size()+"\n");
				long stopTime = System.currentTimeMillis();
				long elapsedTime = stopTime - startTime;
		        System.out.println("Total time taken for data generation of the query is : ");
		        System.out.print(elapsedTime);
			}
		}

		//TEMPCODE Rahul Sharma
		public static String readQueryFromFile(String fileName,String queryId) throws IOException {
			File file = new File(fileName); 
			@SuppressWarnings("resource")
			BufferedReader br = new BufferedReader(new FileReader(file)); 
			  String st; 
			  while ((st = br.readLine()) != null) {
				//System.out.println(st);
			if(st.length()>0) {
			    StringTokenizer stok = new StringTokenizer(st,"|");
				    if(stok.nextToken().contentEquals(queryId))
				    	return stok.nextToken();
				}
			}
		    return "";
		}




//ramachandra

		public GenerateCVC1 initializeConnectionDetails(GenerateCVC1 cvc) throws Exception {
		    Connection testConn = MyConnection.getTesterConn();
		    
		    try(Connection conn = MyConnection.getDatabaseConnection()) {
		        int connId = 0, schemaId = 0, optionalSchemaId = 0;
		        
		        // Assuming the schemaFileContent and sampleDataContent are provided as parameters
		        String schemaFile="test/universityTest/DDL.sql";
				//Path of file containing sampleData
				String sampleDataFile="test/universityTest/sampleData.sql";
				String schemaFileContent = Utilities.readFile(new File(schemaFile));
				String sampleDataContent = Utilities.readFile(new File(sampleDataFile));
			
		        // Process schema file content
		        cvc.setSchemaFile(schemaFileContent);
		        byte[] schemaDataBytes = schemaFileContent.getBytes();
		        String tempSchemaFile = "/tmp/schema_dummy";
		        FileOutputStream schemaFos = new FileOutputStream(tempSchemaFile);
		        schemaFos.write(schemaDataBytes);
		        schemaFos.close();
		        
		        // Process sample data content
		        cvc.setDataFileName(sampleDataContent);
		        byte[] sampleDataBytes = sampleDataContent.getBytes();
		        String tempSampleDataFile = "/tmp/sample_data_dummy";
		        FileOutputStream sampleDataFos = new FileOutputStream(tempSampleDataFile);
		        sampleDataFos.write(sampleDataBytes);
		        sampleDataFos.close();
		        
		        // Set up tables using schema file content
		        ArrayList<String> listOfQueries = Utilities.createQueries(tempSchemaFile);
		        String[] inst = listOfQueries.toArray(new String[listOfQueries.size()]);
		        
		        for (String query : inst) {
		            if (!query.trim().equals("") && !query.trim().contains("drop table")) {
		                PreparedStatement stmt = testConn.prepareStatement(query);
		                stmt.executeUpdate();
		                stmt.close();
		            }
		        }
		        
		        // Insert sample data into tables
		        listOfQueries = Utilities.createQueries(tempSampleDataFile);
		        inst = listOfQueries.toArray(new String[listOfQueries.size()]);
		        
		        for (String query : inst) {
		            if (!query.trim().equals("") && !query.contains("drop table") && !query.contains("delete from")) {
		                PreparedStatement stmt = testConn.prepareStatement(query);
		                stmt.executeUpdate();
		                stmt.close();
		            }
		        }
		        
		        // Additional operations if needed
		        
		        cvc.setTableMap(TableMap.getInstances(testConn, schemaId));
		    } catch (Exception ex) {
		        logger.log(Level.SEVERE, ex.getMessage(), ex);
		        System.out.println(ex);
		        throw ex;
		    }
		    
		    cvc.setConnection(testConn);
		    return cvc;
		}

//public GenerateCVC1 initializeConnectionDetails(GenerateCVC1 cvc) throws Exception {
//		
//	Connection testConn = MyConnection.getTesterConn();
//	
//	try(Connection conn = MyConnection.getDatabaseConnection()){
//		int connId = 0, schemaId = 0, optionalSchemaId=0;
//			
//		int assignmentId  = cvc.getAssignId();
//		int questionId = cvc.getQuestionId();
//		int queryId = cvc.getQueryId();
//		String course_id = cvc.getCourseId();
//		int q_id = questionId;
//		
//		try(PreparedStatement stmt = conn.prepareStatement("select connection_id, defaultschemaid from xdata_assignment where assignment_id = ?")){
//			stmt.setInt(1, assignmentId); 
//			 
//			try(ResultSet result = stmt.executeQuery()){			
//					//Get optional Schema Id for this question
//					try(PreparedStatement statement = conn.prepareStatement("select optionalschemaid from xdata_qinfo where assignment_id = ? and question_id= ? ")){
//						statement.setInt(1, assignmentId); 
//						statement.setInt(2,q_id); 
//						
//						try(ResultSet resultSet = statement.executeQuery()){
//							if(resultSet.next()){
//								optionalSchemaId = resultSet.getInt("optionalschemaid");
//							}
//						}
//					}			
//					if(result.next()){
//						connId = result.getInt("connection_id");
//						//If optional schema id exists and it is not same as default schema id, then set it as schemaId 
//						if(optionalSchemaId != 0 && optionalSchemaId != result.getInt("defaultschemaid")){	
//							schemaId = optionalSchemaId;
//						} else{
//							schemaId = result.getInt("defaultschemaid");
//						}
//					} 
//			}
//		}
//			if(connId != 0 && schemaId != 0){
//				 
//			
//						
//				
//				PopulateTestDataGrading p = new PopulateTestDataGrading();
//				p.deleteAllTempTablesFromTestUser(testConn);
//				byte[] dataBytes = null;
//				String tempFile = "";
//				FileOutputStream fos = null;
//				ArrayList<String> listOfQueries = null;
//				ArrayList<String> listOfDDLQueries = new ArrayList<String>();
//				String[] inst = null;
//				
//				if(testConn != null){
//				
//					try(PreparedStatement stmt1 = conn.prepareStatement("select ddltext from xdata_schemainfo where schema_id = ?")){
//					stmt1.setInt(1, schemaId);			
//					try(ResultSet result = stmt1.executeQuery()){
//					
//					    
//					// Process the result			
//					if(result.next()){
//						String fileContent= result.getString("ddltext");
//						cvc.setSchemaFile(fileContent);
//						// CCJSqlParserManager fleParser = new CCJSqlParserManager();
//							//Statement parsedStmt = fleParser.parse(new StringReader(fileContent));
//						dataBytes = fileContent.getBytes();
//						tempFile = "/tmp/dummy";
//						
//						 fos = new FileOutputStream(tempFile);
//						fos.write(dataBytes);
//						fos.close();
//						listOfQueries = Utilities.createQueries(tempFile);
//						inst = listOfQueries.toArray(new String[listOfQueries.size()]);
//						listOfDDLQueries.addAll(listOfQueries);
//						p.deleteAllTablesFromTestUser(testConn); // added by ram : delete tables if already exists.
//						
//						// to test the time taken for creating tables, added by ram
//						long startTime = System.currentTimeMillis();
//						
//						for (int i = 0; i < inst.length; i++) {
//							// we ensure that there is no spaces before or after the request string  
//							// in order to not execute empty statements  
//							if (!inst[i].trim().equals("") && ! inst[i].trim().contains("drop table")) {
//								//Changed for MSSQL testing
//								//String temp = inst[i].replaceAll("(?i)^[ ]*create[ ]+table[ ]+", "create table ##");
//								//stmt = assignmentConn.prepareStatement(temp);
//								//add by ram
//								DatabaseMetaData dbmd=testConn.getMetaData();      
//								String dbType = dbmd.getDatabaseProductName(); 
//								String temp = "";
//								if (dbType.equalsIgnoreCase("MySql"))
//								{
//									temp = inst[i].trim();
//								}
//								else if(dbType.equalsIgnoreCase("PostgreSQL")) {
//									temp = inst[i].trim().replaceAll("(?i)^\\s*create\\s+table\\s+", "create temporary table ");	
//								}
//								System.out.println(temp); // added by ram
//								PreparedStatement stmt2 = testConn.prepareStatement(temp);
//									stmt2.executeUpdate();	
//								stmt2.close();
//							
//								    
//							}
//						}
//						
//						// to test the time taken for creating tables, added by ram
//						long stopTime = System.currentTimeMillis();
//						long elapsedTime = stopTime - startTime;
//				        System.out.println("Total time taken for creating tables/temp tables is: ");
//				        System.out.print(elapsedTime/1000F);
//					}
//					}
//					}
//					try(PreparedStatement stmt2 = conn.prepareStatement("select sample_data from xdata_sampledata where schema_id = ?")){
//						stmt2.setInt(1, schemaId);			
//						try(ResultSet result = stmt2.executeQuery()){
//							if(result.next()){
//								String sdContent= result.getString("sample_data");
//								cvc.setDataFileName(sdContent);
//							//	String sdReplace=sdContent.replace("\\\\","'");
//								//fc = sdContent.replace("\t", "    ");
//								dataBytes = sdContent.getBytes(); 
//								fos = new FileOutputStream(tempFile);
//								fos.write(dataBytes);
//								fos.close();
//								
//								listOfQueries = Utilities.createQueries(tempFile);
//								inst = listOfQueries.toArray(new String[listOfQueries.size()]);
//								
//								// to test the time taken for creating tables, added by ram
//								long startTime = System.currentTimeMillis();
//								 
//								for (int i = 0; i < inst.length; i++) {
//									// we ensure that there is no spaces before or after the request string  
//									// in order to not execute empty statements  
//									if (!inst[i].trim().equals("") && !inst[i].contains("drop table") && !inst[i].contains("delete from")) {
//									//Changed for MSSQL TESTING
//										//String temp = inst[i].replaceAll("(?i)^[ ]*insert[ ]+into[ ]+", "insert into [xdata].[dbo].##");
//										//stmt = assignmentConn.prepareStatement(temp+";");
//										System.out.println(inst[i]);// add by RAM
//										PreparedStatement stmt3 = testConn.prepareStatement(inst[i]);
//											stmt3.executeUpdate();							
//											stmt3.close();
//									}
//								}
//								
//								// to test the time taken for creating tables, added by ram
//								long stopTime = System.currentTimeMillis();
//								long elapsedTime = stopTime - startTime;
//						        System.out.println("Total time taken for uploading sample data is: ");
//						        System.out.print(elapsedTime/1000F);
//							} 
//					}
//					}
//				} 
//				
//				cvc.setTableMap(TableMap.getInstances(testConn,schemaId));	
//			
//		}
//		}
//	catch(Exception ex){
//		//this.closeConn();
//		logger.log(Level.SEVERE,ex.getMessage(), ex);
//		//ex.printStackTrace();
//		System.out.println(ex);
//		throw ex;
//	}
//	cvc.setConnection(testConn);
//	return cvc;
//	}
//	


}

