package testDataGen;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.lang.reflect.Type;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import util.*;

public class PopulateTestDataGrading {

	private static Logger logger = Logger.getLogger(PopulateTestDataGrading.class.getName());

	

	public void fetchAndPopulateTestDatabase(Connection dbcon, Connection testCon, int assignment_id,int question_id, int query_id, String course_id, String dataset_id,TableMap tableMap) throws Exception{

		String dataset_query="select value from xdata_datasetvalue where datasetid =? and assignment_id=? and question_id=? and query_id=? and course_id = ?";
		String dataset= "";

		PreparedStatement dstmt = dbcon.prepareStatement(dataset_query);
		dstmt.setString(1,dataset_id);
		dstmt.setInt(2, assignment_id);
		dstmt.setInt(3,question_id);
		dstmt.setInt(4,query_id);
		dstmt.setString(5,course_id);
		logger.log(Level.FINE,"Dataset_id is :"+dataset_id);
		logger.log(Level.FINE,"Query_id is :"+query_id);
		ResultSet dset=dstmt.executeQuery();
		try{
			while(dset.next()){
				dataset = dset.getString("value");

				Map<String , ArrayList> tables=new HashMap<String , ArrayList>();
				Gson gson = new Gson();
				//ArrayList dsList = gson.fromJson(value,ArrayList.class);
				Type listType = new TypeToken<ArrayList<DataSetValue>>() {
				}.getType();
				List<DataSetValue> dsList = new Gson().fromJson(dataset, listType);
				logger.log(Level.FINE,"dsList.size() = "+ dsList.size());
				for(int i = 0 ; i < dsList.size();i++ ){
					DataSetValue dsValue = dsList.get(i);
					String tname,values; 
					if(dsValue.getFilename().contains(".ref.")){
						tname = dsValue.getFilename().substring(0,dsValue.getFilename().indexOf(".ref.copy"));
					}else{
						tname = dsValue.getFilename().substring(0,dsValue.getFilename().indexOf(".copy"));
					}
					logger.log(Level.FINE,"table String:::::::::::::::::::::::::::"+tname);
					//for(String dsv: dsValue.getDataForColumn()){
					tables.put(tname, dsValue.getDataForColumn());	
					//}
				}

				int size = tableMap.foreignKeyGraph.topSort().size();
				for (int i=(size-1);i>=0;i--){
					String tableName = tableMap.foreignKeyGraph.topSort().get(i).toString();
					String del="delete from "+tableName;
					logger.log(Level.FINE,"DELETE::::::::::::::::::::::::"+del);
					PreparedStatement stmt=testCon.prepareStatement(del);
					try{
						stmt.executeUpdate();
					}catch(Exception e){
						logger.log(Level.SEVERE,"PopulateTestData.fetchAndPopulateTestDatabase -> ERROR:" + del,e);
						//logger.log(Level.SEVERE, ""+e.getStackTrace(),e);
						//e.printStackTrace();
					}finally{
						stmt.close();
					}
				}
				//If tables contains foreign key relation they will be available in foreignKeyGraph
				for(int i=0;i<tableMap.foreignKeyGraph.topSort().size();i++){
					String tableName = tableMap.foreignKeyGraph.topSort().get(i).toString();
					if(tables.containsKey(tableName)){
						ArrayList <String> value=tables.get(tableName);

						for(String column: value)
						{
							String row=column.replaceAll("\\|", "','");
							String insert="insert into "+tableName+" Values ('"+row+"')";
							logger.log(Level.FINE,"Insert statement:::::::::::::::::::::::"+insert);

							PreparedStatement inst=testCon.prepareStatement(insert);
							try{
								inst.executeUpdate();
								tables.remove(tableName);
							}catch(Exception e){
								logger.log(Level.INFO,"PopulateTestData.fetchAndPopulateTestDatabase -> ERROR:" + insert);
								//logger.log(Level.SEVERE,""+e.getStackTrace(),e);
								//e.printStackTrace();
							}finally{
								inst.close();
							}
						}
					} 
				} 
				//Tables that are not in foreign key relation, just insert without any checks
				//Shree added this for relations not having foreign key
				Iterator it = tables.entrySet().iterator();
				while(it.hasNext()){

					java.util.Map.Entry<String,ArrayList> ent= (Map.Entry<String,ArrayList>)it.next();
					String tableName  = ent.getKey();
					ArrayList <String> value=ent.getValue();
					for(String column: value)
					{
						String row=column.replaceAll("\\|", "','");
						String insert="insert into "+tableName+" Values ('"+row+"')";
						logger.log(Level.INFO,"Insert statement:::::::::::::::::::::::"+insert);

						PreparedStatement inst=testCon.prepareStatement(insert);
						try{
							inst.executeUpdate(); 
							it.remove();//remove(tableName);
						}catch(Exception e){ 
							logger.log(Level.INFO,"PopulateTestData.fetchAndPopulateTestDatabase -> ERROR:" + insert);
							//logger.log(Level.SEVERE,""+e.getStackTrace(),e);
							//e.printStackTrace();
						}finally{
							inst.close();
						}
					}
				}


			}
		}catch(Exception e){
			logger.log(Level.SEVERE,""+e.getStackTrace(),e);
			dbcon.close();
			testCon.close();
			//e.printStackTrace();
			throw e;
		}finally{
			dset.close();
			dstmt.close();


		}
	}

	public void fetchAndPopulateTestDatabase(int assignment_id, int question_id, int query_id, String course_id, String dataset_id,TableMap tableMap) throws Exception{
		//Connection dbcon = MyConnection.getExistingDatabaseConnection();
		//Connection testCon = MyConnection.getTestDatabaseConnection();

		try(Connection dbcon = MyConnection.getDatabaseConnection()){
			try(Connection testCon = MyConnection.getTesterConn()){
				fetchAndPopulateTestDatabase(dbcon, testCon, assignment_id, question_id,query_id,course_id, dataset_id, tableMap);
			}
		}catch(Exception e){
			logger.log(Level.SEVERE,"PopulateTestData Class:  "+e.getStackTrace(),e);
		}

	}


	
	public void populateTestDataForTesting(Vector<String> listOfCopyFiles, String filePath, TableMap tableMap, Connection testConn, int assignmentId, int questionId){
		try{						
			deleteAllTempTablesFromTestUser(testConn);
			
			//deleteAllTablesFromTestUser(conn1);
			
			this.createTempTables(testConn, assignmentId, questionId);
			BufferedReader br = null;

			for(int i=0;i<tableMap.foreignKeyGraph.topSort().size();i++){
				try{
					String tableName = tableMap.foreignKeyGraph.topSort().get(i).toString();
					if(listOfCopyFiles.contains(tableName+".copy")){
						listOfCopyFiles.remove(tableName+".copy");
						String copyFile = tableName+".copy";

						//br = new BufferedReader(new FileReader(Configuration.homeDir+"/temp_cvc"+filePath+"/"+copyFile));
						br = new BufferedReader(new FileReader(Configuration.homeDir+"/temp_smt"+filePath+"/"+copyFile)); // added by ram 
						String str;
						String data="";
						while((str = br.readLine())!=null){
							data+=str+"@@";
						}

						uploadTestDataToTempTables(copyFile.substring(0, copyFile.indexOf(".copy")), data, filePath, testConn);
					}else if(listOfCopyFiles.contains(tableName+".ref.copy")){
						listOfCopyFiles.remove(tableName+".ref.copy");
						String copyFile = tableName+".ref.copy";
						//br = new BufferedReader(new FileReader(Configuration.homeDir+"/temp_cvc"+filePath+"/"+copyFile));
						br = new BufferedReader(new FileReader(Configuration.homeDir+"/temp_smt"+filePath+"/"+copyFile)); // added by ram
						String str;
						String data="";
						while((str = br.readLine())!=null){
							data+=str+"@@";
						}

						uploadTestDataToTempTables(copyFile.substring(0, copyFile.indexOf(".ref.copy")), data, filePath, testConn);
					}
				}catch(Exception e){
					logger.log(Level.SEVERE,"PopulateTestData.populateTestDataForTesting(): "+e.getStackTrace(),e);
				}finally{
					if(br != null){
						br.close();
					}
				}

			}
			for(int i=0;i<listOfCopyFiles.size();i++){
				try{
					String copyFile = listOfCopyFiles.get(i);
					// br = new BufferedReader(new FileReader(Configuration.homeDir+"/temp_cvc"+filePath+"/"+copyFile));
					br = new BufferedReader(new FileReader(Configuration.homeDir+"/temp_smt"+filePath+"/"+copyFile)); // added by ram
					String str;
					String data="";
					while((str = br.readLine())!=null){
						data+=str+"@@";
					}

					uploadTestDataToTempTables(copyFile.substring(0,copyFile.indexOf(".copy")), data, filePath, testConn);
				}catch(Exception e){
					logger.log(Level.SEVERE,"PopulateTestData.populateTestDataForTesting(): "+e.getStackTrace(),e);
				}
				finally{
					if(br != null)
						br.close();
				}
			}
		}catch(Exception e){
			logger.log(Level.SEVERE,"PopulateTestData.populateTestDataForTesting: "+e.getStackTrace(),e);
			//e.printStackTrace();
		}finally{

			//conn.close();

		}


	}


	public void uploadTestDataToTempTables(String tablename,String copyFileData,String filePath, Connection conn) throws Exception{			
		String t[]=copyFileData.split("@@");
		for(int i=0;i<t.length;i++){			
			t[i]=t[i].replaceAll("\\|", "','");

			//PreparedStatement smt=conn.prepareStatement("Insert into "+ tablename+" Values ('"+t[i]+"')");
			//PreparedStatement smt=conn.prepareStatement("Insert into "+ tablename.toLowerCase()+" Values ('"+t[i]+"')"); // added by ram for mysql
			PreparedStatement smt=conn.prepareStatement("Insert into "+ tablename+" Values ('"+t[i]+"')"); // added by ram for mysql
			
			try{
				smt.executeUpdate();

			}catch(Exception e){
				//logger.log(Level.SEVERE,"PopulateTestData:uploadTestDataToTempTables->Error in "+tablename+"Insert into "+ tablename+" Values ('"+t[i]+"')",e);
				//logger.log(Level.SEVERE,e.getMessage(),e);
				//e.printStackTrace();
			}finally{
				smt.close();
			}

		}				
	}

	public void deleteAllTempTablesFromTestUser(Connection dbConn) throws Exception{
		// added by ram to know metadata
		DatabaseMetaData dbmd=dbConn.getMetaData();    
//		System.out.println("Driver Name: "+dbmd.getDriverName());  
//		System.out.println("Driver Version: "+dbmd.getDriverVersion());  
//		System.out.println("UserName: "+dbmd.getUserName());  
		String dbType = dbmd.getDatabaseProductName(); 
		System.out.println(dbType);
//		System.out.println("Database Product Version: "+dbmd.getDatabaseProductVersion());
		
		// metadata details ends here:ram
		if (dbType.equalsIgnoreCase("MySql"))
		{
			//when its mysql, delete all tables, we have not create temp tables.
			deleteAllTablesFromTestUser(dbConn);
			
		}
		
		else if(dbType.equalsIgnoreCase("postgreSQL"))
		{
			Statement st = dbConn.createStatement();
			st = dbConn.createStatement();
			st.executeUpdate("DISCARD TEMPORARY");
			st.close();
		}
	}

	public void deleteAllTablesFromTestUser(Connection conn) throws Exception{
		try{
			DatabaseMetaData dbm = conn.getMetaData();
			// added by ram
			String dbType = dbm.getDatabaseProductName(); 
			System.out.println(dbType);
			
			if (dbType.equalsIgnoreCase("MySql"))
			{
				String[] types = {"TABLE"};
				ResultSet rs = dbm.getTables(conn.getCatalog(), null, "%", types);	
			
//				ResultSet rs = dbm.getTables(null, null, "%", types);
//				while (rs.next()) {
//				  System.out.println(rs.getString(3));
//				}
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
//						PreparedStatement pstmt = conn.prepareStatement("drop table if exists "+table +" cascade");
//						pstmt.executeUpdate();
//						pstmt.close();
						
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
						PreparedStatement pstmt = conn.prepareStatement("Truncate table "+table +" cascade");
						pstmt.executeUpdate();
						pstmt.close();
					}
	
				} 
			
				rs.close();
			}
			
		}catch(Exception e){
			logger.log(Level.INFO,e.getMessage());
		}

	}

	public void deleteDatasets(String filePath) throws Exception{
		//Runtime r = Runtime.getRuntime();
		//File f=new File(Configuration.homeDir+"/temp_cvc"+filePath+"/");
		File f=new File(Configuration.homeDir+"/temp_smt"+filePath+"/"); // added by ram
		File f2[]=f.listFiles();
		for(int i=0;i<f2.length;i++){
			if(f2[i].isDirectory() && f2[i].getName().startsWith("DS")){
				//Utilities.deletePath(Configuration.homeDir+"/temp_cvc"+filePath+"/"+f2[i].getName());
				Utilities.deletePath(Configuration.homeDir+"/temp_smt"+filePath+"/"+f2[i].getName()); // added by ram
			}				
		}
	}

	//TODO:Refractor this method name and input prarameters. 
	//This is no longer used to check how many mutants were killed
	
	public void createTempTables(Connection conn, int assignId, int questionId) throws Exception {
		//Connection mainConn = MyConnection.getExistingDatabaseConnection();
		try(Connection mainConn = MyConnection.getDatabaseConnection()){	
			int schemaId = 0, optionalSchemaId=0;			

			try(PreparedStatement stmt = mainConn.prepareStatement("select defaultschemaid from xdata_assignment where assignment_id = ?")){
				stmt.setInt(1, assignId); 

				try(ResultSet result = stmt.executeQuery()){

					//Get optional Schema Id for this question
					try(PreparedStatement stmt1 = mainConn.prepareStatement("select optionalschemaid from xdata_qinfo where assignment_id = ? and question_id= ? ")){
						stmt1.setInt(1, assignId); 
						stmt1.setInt(2, questionId); 

						try(ResultSet resultSet = stmt1.executeQuery()){
							if(resultSet.next()){
								optionalSchemaId = resultSet.getInt("optionalschemaid");
							}
						}
					}
					if(result.next()){
						//If optional schema id exists and it is not same as default schema id, then set it as schemaId 
						if(optionalSchemaId != 0 && optionalSchemaId != result.getInt("defaultschemaid")){	
							schemaId = optionalSchemaId;
						} else{
							schemaId = result.getInt("defaultschemaid");
						}
					}

					if(schemaId != 0){				
						try(PreparedStatement stmt1 = mainConn.prepareStatement("select ddltext from xdata_schemainfo where schema_id = ?")){
							stmt1.setInt(1, schemaId);			
							try(ResultSet result1 = stmt1.executeQuery()){

								// Process the result			
								if(result1.next()){
									String fileContent= result1.getString("ddltext");
									byte[] dataBytes = fileContent.getBytes();
									String tempFile = "/tmp/dummy";

									FileOutputStream fos = new FileOutputStream(tempFile);
									fos.write(dataBytes);
									fos.close();

									ArrayList<String> listOfQueries = Utilities.createQueries(tempFile);
									String[] inst = listOfQueries.toArray(new String[listOfQueries.size()]);
									
									//deleteAllTablesFromTestUser(conn); // added by ram for mysql
									deleteAllTempTablesFromTestUser(conn); //added by ram for mysql
									
									for (int i = 0; i < inst.length; i++) {
										// we ensure that there is no spaces before or after the request string  
										// in order to not execute empty statements  
										if (!inst[i].trim().equals("") && ! inst[i].trim().contains("drop table")) {
											//added by ram for mysql
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
											try(PreparedStatement stmt2 = conn.prepareStatement(temp)){
												System.out.println(temp);// added by ram 
												stmt2.executeUpdate();					
											} catch (SQLException sqle){
												logger.log(Level.SEVERE, sqle.getMessage());
											}
										}
									}	
									
								}
							}//try-with-resource for ressultset result
						}//try-with-resource for stmt1		
					}	
				}//try-with-resource for ResultSet
			}//Try-with-resource for statement obj
		}//try-with-resource for Connection obj
		catch(Exception ex){
			logger.log(Level.SEVERE,ex.getMessage(),ex);
			//ex.printStackTrace();
			throw ex;
		}


	}

	public void createTempTablesForDemoUI(Connection conn, int assignId, int questionId) throws Exception {
		//Connection mainConn = MyConnection.getExistingDatabaseConnection();
		Connection mainConn = conn;	
			int schemaId = 0, optionalSchemaId=0;			

			try(PreparedStatement stmt = mainConn.prepareStatement("select defaultschemaid from xdata_assignment where assignment_id = ?")){
				stmt.setInt(1, assignId); 

				try(ResultSet result = stmt.executeQuery()){

					//Get optional Schema Id for this question
					try(PreparedStatement stmt1 = mainConn.prepareStatement("select optionalschemaid from xdata_qinfo where assignment_id = ? and question_id= ? ")){
						stmt1.setInt(1, assignId); 
						stmt1.setInt(2, questionId); 

						try(ResultSet resultSet = stmt1.executeQuery()){
							if(resultSet.next()){
								optionalSchemaId = resultSet.getInt("optionalschemaid");
							}
						}
					}
					if(result.next()){
						//If optional schema id exists and it is not same as default schema id, then set it as schemaId 
						if(optionalSchemaId != 0 && optionalSchemaId != result.getInt("defaultschemaid")){	
							schemaId = optionalSchemaId;
						} else{
							schemaId = result.getInt("defaultschemaid");
						}
					}

					if(schemaId != 0){				
						try(PreparedStatement stmt1 = mainConn.prepareStatement("select ddltext from xdata_schemainfo where schema_id = ?")){
							stmt1.setInt(1, schemaId);			
							try(ResultSet result1 = stmt1.executeQuery()){

								// Process the result			
								if(result1.next()){
									String fileContent= result1.getString("ddltext");
									byte[] dataBytes = fileContent.getBytes();
									String tempFile = "/tmp/dummy";

									FileOutputStream fos = new FileOutputStream(tempFile);
									fos.write(dataBytes);
									fos.close();

									ArrayList<String> listOfQueries = Utilities.createQueries(tempFile);
									String[] inst = listOfQueries.toArray(new String[listOfQueries.size()]);

									for (int i = 0; i < inst.length; i++) {
										// we ensure that there is no spaces before or after the request string  
										// in order to not execute empty statements  
										if (!inst[i].trim().equals("") && ! inst[i].trim().contains("drop table")) {
											String temp = inst[i].trim().replaceAll("(?i)^\\s*create\\s+table\\s+", "create temporary table ");
											try(PreparedStatement stmt2 = conn.prepareStatement(temp)){
												stmt2.executeUpdate();					
											}
										}
									}	
								}
							}//try-with-resource for ressultset result
						}//try-with-resource for stmt1		
					}	
				}//try-with-resource for ResultSet
			}//Try-with-resource for statement obj
	}

	public void createTempTablesForTestThread(Connection conn, int assignId, int questionId) throws Exception {
		//Connection mainConn = MyConnection.getExistingDatabaseConnection();
		try(Connection mainConn = MyConnection.getDatabaseConnection()){	
			int schemaId = 0, optionalSchemaId=0;			

			try(PreparedStatement stmt = mainConn.prepareStatement("select defaultschemaid from xdata_assignment where assignment_id = ?")){
				stmt.setInt(1, assignId); 

				try(ResultSet result = stmt.executeQuery()){

					//Get optional Schema Id for this question
					try(PreparedStatement stmt1 = mainConn.prepareStatement("select optionalschemaid from xdata_qinfo where assignment_id = ? and question_id= ? ")){
						stmt1.setInt(1, assignId); 
						stmt1.setInt(2, questionId); 

						try(ResultSet resultSet = stmt1.executeQuery()){
							if(resultSet.next()){
								optionalSchemaId = resultSet.getInt("optionalschemaid");
							}
						}
					}
					if(result.next()){
						//If optional schema id exists and it is not same as default schema id, then set it as schemaId 
						if(optionalSchemaId != 0 && optionalSchemaId != result.getInt("defaultschemaid")){	
							schemaId = optionalSchemaId;
						} else{
							schemaId = result.getInt("defaultschemaid");
						}
					}

					if(schemaId != 0){				
						try(PreparedStatement stmt1 = mainConn.prepareStatement("select ddltext from xdata_schemainfo where schema_id = ?")){
							stmt1.setInt(1, schemaId);			
							try(ResultSet result1 = stmt1.executeQuery()){

								// Process the result			
								if(result1.next()){
									String fileContent= result1.getString("ddltext");
									byte[] dataBytes = fileContent.getBytes();
									String tempFile = "/tmp/dummy";

									FileOutputStream fos = new FileOutputStream(tempFile);
									fos.write(dataBytes);
									fos.close();

									ArrayList<String> listOfQueries = Utilities.createQueries(tempFile);
									String[] inst = listOfQueries.toArray(new String[listOfQueries.size()]);

									for (int i = 0; i < inst.length; i++) {
										// we ensure that there is no spaces before or after the request string  
										// in order to not execute empty statements  
										if (!inst[i].trim().equals("") && ! inst[i].trim().contains("drop table")) {
											String temp = inst[i].trim().replaceAll("(?i)^\\s*create\\s+table\\s+", "create temporary table ");
											try(PreparedStatement stmt2 = conn.prepareStatement(temp)){
												stmt2.executeUpdate();					
											}
										}
									}	
								}
							}//try-with-resource for ressultset result
						}//try-with-resource for stmt1		
					}	
				}//try-with-resource for ResultSet
			}//Try-with-resource for statement obj
		}//try-with-resource for Connection obj
		catch(Exception ex){
			logger.log(Level.SEVERE,ex.getMessage(),ex);
			//ex.printStackTrace();
			throw ex;
		}


	}


	/**
	 * This method is used in TestAnswer class for evaluating the queries.
	 * If data generation fails, then this method is called while evaluation 
	 * and default sample data is loaded and is taken as dataset against which evaluation is done.
	 *  
	 * @param conn
	 * @param testConn
	 * @param assignmentId
	 * @param questionId
	 * @throws Exception
	 */
	public void createTempTableData(Connection conn, Connection testConn, int assignmentId, int questionId,String course_id) throws Exception{
		int connId = 2, schemaId = 15,optionalSchemaId=15;

		try(PreparedStatement stmt = conn.prepareStatement("select connection_id, defaultschemaid from xdata_assignment where assignment_id = ? and course_id = ?")){
			stmt.setInt(1, assignmentId);
			stmt.setString(2,course_id);
			try(ResultSet result = stmt.executeQuery()){


				//Get optional Schema Id for this question
				try(PreparedStatement statement = conn.prepareStatement("select optionalschemaid from xdata_qinfo where assignment_id = ? and question_id= ? and course_id = ?")){
					statement.setInt(1, assignmentId); 
					statement.setInt(2,questionId); 
					statement.setString(3,course_id);

					try(ResultSet resultSet = statement.executeQuery()){
						if(resultSet.next()){
							optionalSchemaId = resultSet.getInt("optionalschemaid");			
						}
					} //try-with-resources - ResultSet -resultSet obj
				}//try-with-resources -PreparedStatement statement obj
				if(result.next()){
					connId = result.getInt("connection_id");			
					//If optional schema id exists and it is not same as default schema id, then set it as schemaId 
					if(optionalSchemaId != 0 && optionalSchemaId != result.getInt("defaultschemaid")){	
						schemaId = optionalSchemaId;
					} else{
						schemaId = result.getInt("defaultschemaid");
					}
				} 
			}//try-with-resource - ResultSet obj
		}//try-with-resource - Preparedstmt obj
		byte[] dataBytes = null;
		String tempFile = "";
		FileOutputStream fos = null;
		ArrayList<String> listOfQueries = null;
		String[] inst = null;
		if(connId != 0 && schemaId != 0){
			try(PreparedStatement stmt = conn.prepareStatement("select ddltext from xdata_schemainfo where schema_id = ?")){
				stmt.setInt(1, schemaId);			
				try(ResultSet result = stmt.executeQuery()){

					// Process the result			
					if(result.next()){
						String fileContent= result.getString("ddltext");
						//String fr=fileContent.replace("\\\\","'");
						//String fc = fr.replace("\t", "    ");
						dataBytes = fileContent.getBytes();
						tempFile = "/tmp/dummy";

						fos = new FileOutputStream(tempFile);
						fos.write(dataBytes);
						fos.close();

						listOfQueries = Utilities.createQueries(tempFile);
						inst = listOfQueries.toArray(new String[listOfQueries.size()]);
						
						deleteAllTablesFromTestUser(testConn); // added by ram for mysql
						
						for (int i = 0; i < inst.length; i++) {
							// we ensure that there is no spaces before or after the request string  
							// in order to not execute empty statements 
							
							if (!inst[i].trim().equals("") && ! inst[i].trim().contains("drop table")) {
								
								//added by ram for mysql
								DatabaseMetaData dbmd=testConn.getMetaData();      
								String dbType = dbmd.getDatabaseProductName(); 
								String temp = "";
								if (dbType.equalsIgnoreCase("MySql"))
								{
									temp = inst[i].trim();
								}
								else if(dbType.equalsIgnoreCase("PostgreSQL")) {
									temp = inst[i].trim().replaceAll("(?i)^\\s*create\\s+table\\s+", "create temporary table ");	
								}
								try(PreparedStatement stmt2 = testConn.prepareStatement(temp)){
									stmt2.executeUpdate();	
								}
							}
						}
						
					}
				}
			}
			try(PreparedStatement stmt = conn.prepareStatement("select sample_data from xdata_sampledata where schema_id = ?")){
				stmt.setInt(1, schemaId);			
				try(ResultSet result = stmt.executeQuery()){

					// Process the result			
					if(result.next()){
						String sdContent= result.getString("sample_data");
						//String sdReplace=sdContent.replace("\\\\","'");
						//fc = sdReplace.replace("\t", "    ");
						dataBytes = sdContent.getBytes(); 
						fos = new FileOutputStream(tempFile);
						fos.write(dataBytes);
						fos.close();

						listOfQueries = Utilities.createQueries(tempFile);
						inst = listOfQueries.toArray(new String[listOfQueries.size()]);
						
						for (int i = 0; i < inst.length; i++) {
							// we ensure that there is no spaces before or after the request string  
							// in order to not execute empty statements  
							if (!inst[i].trim().equals("") && !inst[i].contains("drop table") && !inst[i].contains("delete from")) {
								//System.out.println(inst[i]);
								try(PreparedStatement stmt2 = testConn.prepareStatement(inst[i])){
									stmt2.executeUpdate();		
								}
							}
						}
						
						

					}//try-with-resource resultset obj
				}//try-with-resource statement obj	
			}
		}

	}

	/**For the instructor given data sets during assignment creation
test student and instructor query options */
	/**
	 * First get sample data sets for question, if it is not there, then get
	 * it from assignment table
	 * If both are not there, dont set anythng
	 * @throws SQLException 
	 * @throws FileNotFoundException 
	 * @throws IOException
	 */
	public String createTempTableWithDefaultData(Connection mainCon,Connection testConn,int assignmentId,
		int questionId,String course_id,String sampledata_id) throws Exception{
		String sampleDataName = "";
		byte[] dataBytes = null;
		String tempFile = "";
		FileOutputStream fos = null;
		ArrayList<String> listOfQueries = null;
		String[] inst = null;
		this.deleteAllTablesFromTestUser(testConn);
		createTempTables(testConn, assignmentId, questionId);
		try(PreparedStatement stmt = mainCon.prepareStatement("select sample_data_name,sample_data from xdata_sampledata where sampledata_id = ?")){
			stmt.setInt(1, Integer.parseInt(sampledata_id));			
			try(ResultSet result = stmt.executeQuery()){

				// Process the result			
				if(result.next()){
					sampleDataName = result.getString("sample_data_name");
					String sdContent= result.getString("sample_data");
					//String sdReplace=sdContent.replace("\\\\","'");
					//fc = sdReplace.replace("\t", "    ");
					tempFile = "/tmp/dummy";
					dataBytes = sdContent.getBytes(); 
					fos = new FileOutputStream(tempFile);
					fos.write(dataBytes);
					fos.close();

					listOfQueries = Utilities.createQueries(tempFile);
					inst = listOfQueries.toArray(new String[listOfQueries.size()]);

					for (int i = 0; i < inst.length; i++) {
						// we ensure that there is no spaces before or after the request string  
						// in order to not execute empty statements  
						if (!inst[i].trim().equals("") && !inst[i].contains("drop table") && !inst[i].contains("delete from")) {
							//System.out.println(inst[i]);
							try(PreparedStatement stmt2 = testConn.prepareStatement(inst[i])){
								stmt2.executeUpdate();		
							} catch (SQLException sqle){
								logger.log(Level.SEVERE, sqle.getMessage());
							}
						}
					}

				}//try-with-resource resultset obj
			}//try-with-resource statement obj	
		} catch (SQLException e) {
			logger.log(Level.INFO,"------PopulateTestData - Load default data sets------");
			logger.log(Level.SEVERE,e.getMessage(),e);
			//throw e;
		}
		catch (FileNotFoundException e) {
			logger.log(Level.INFO,"------PopulateTestData - Load default data sets------");
			logger.log(Level.SEVERE,e.getMessage(),e);
			//throw e;
		}
		catch (IOException e) {
			logger.log(Level.INFO,"------PopulateTestData - Load default data sets------");
			logger.log(Level.SEVERE,e.getMessage(),e);
			//throw e;
		}
		return sampleDataName;

	}
	public void populateDataset(int assignmentId, int questionId, int query_id, String course_id, String datasetId, Connection mainConn, Connection testConn) throws Exception{
		GenerateCVC1 cvc = new GenerateCVC1();		
		GenerateDataSet p = new GenerateDataSet();
		cvc.setAssignmentId(assignmentId);
		cvc.setQuestionId(questionId);
		cvc.setQueryId(1);
		cvc.setCourseId(course_id);
		
		p.initializeConnectionDetails(cvc);
		TableMap tm = cvc.getTableMap();
		this.fetchAndPopulateTestDatabase(mainConn, testConn, assignmentId, questionId, query_id, course_id,datasetId, tm);
		cvc.closeConn();
	}

	public static void entry(String args[]) throws Exception{

		String datasetid=args[0];
		String assignment_id=args[1];
		String question_id = args[2];
		int questionId=Integer.parseInt(question_id);
		String course_id = args[3];
		//String questionid = "A"+assignment_id+"Q"+question_id+"S"+queryid;
		int query_id=1;
		if(args.length>3){
			query_id=Integer.parseInt(args[2]);

		}

		logger.log(Level.INFO,"------PopulateTestData-----entry()------");
		logger.log(Level.INFO,"Datasetid :"+datasetid);
		logger.log(Level.INFO,"QuestionId :"+query_id);
		GenerateCVC1 cvc = new GenerateCVC1();
		int assignId = Integer.parseInt(assignment_id);
		
		GenerateDataSet pre = new GenerateDataSet();
		cvc.setAssignmentId(assignId);
		cvc.setQuestionId(questionId);
		cvc.setQueryId(query_id);
		cvc.setCourseId(course_id);
		
		pre.initializeConnectionDetails(cvc);
		
		TableMap tm = cvc.getTableMap();
		PopulateTestDataGrading p=new PopulateTestDataGrading();
		p.fetchAndPopulateTestDatabase(Integer.parseInt(assignment_id), questionId,query_id, course_id, datasetid, tm);
		cvc.closeConn();

	}

	public static void main(String args[]) throws Exception{
		/*
		PopulateTestData ptd = new PopulateTestData();
		String test = ptd.generateCvcOutput("cvc3_temp2.cvc");
		String cutFile = ptd.cutRequiredOutput(test);
		Vector<String> listOfCopyFiles = ptd.generateCopyFile(cutFile);
		ptd.populateTestDatabase(listOfCopyFiles);

		try{
			PreparedStatement pstmt = null;
			TableMap tableMap = TableMap.getInstances();
			Connection conn = (new MyConnection()).getExhistingDatabaseConnection();
			QueryParser qParser = new QueryParser(tableMap);
			qParser.parseQuery("q1", "select * from instructor inner join teaches using(instructor_id) inner join crse using(course_id)");
			Query query = qParser.getQuery();

			Vector<Mutant> mutants = ptd.generateJoinMutants(query);



		}catch(Exception e){
			e.printStackTrace();
		}

		ptd.killedMutants("cvc3_"+ count +".cvc", this.query, "DS"+count, queryString, filePath);
		 */
		/*String copyFile = "a.copy";
		System.out.println(copyFile.substring(0,copyFile.indexOf(".copy")));
		BufferedReader br = new BufferedReader(new FileReader(Configuration.homeDir+"/temp_cvc4/"+copyFile));
		String str;
		String data="";
		while((str = br.readLine())!=null){
			System.out.println(str);
			data+=str+"@@";
		}//Process proc = r.exec("sh "+Configuration.scriptsDir+"/upload.sh " + copyFile.substring(0,copyFile.indexOf(".copy")) + " " + copyFile + " " + filePath+" "+Configuration.databaseIP+" "+Configuration.databaseName+" "+Configuration.testDatabaseUser+" "+Configuration.testDatabaseUserPasswd);
		//int errVal = proc.waitFor();
		new PopulateTestData().uploadToTestUser(copyFile.substring(0,copyFile.indexOf(".copy")) ,data,"4");
		 */
		/*String datasetid=args[0];
		String questionid=args[1];
		System.out.println("------PopulateTestData------");
		System.out.println("Datasetid :"+datasetid);
		System.out.println("QuestionId :"+questionid);
		GenerateCVC1 cvc = new GenerateCVC1();
		cvc.initializeConnectionDetails(1);
		TableMap tm = cvc.getTableMap();
		PopulateTestData p=new PopulateTestData();
		p.fetechAndPopulateTestDatabase(questionid, datasetid, tm);*/

		PopulateTestDataGrading p=new PopulateTestDataGrading();
		//p.generateCvcOutput("cvc3_9.cvc", "4/A1Q23");

	}

}