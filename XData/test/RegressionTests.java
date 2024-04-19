package test;

import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.ResultSetMetaData;
import java.util.*;
import dnl.utils.text.table.TextTable;

import parsing.QueryStructure;
import parsing.Table;
import partialMarking.PartialMarker;
import partialMarking.TestPartialMarking;
import testDataGen.GenerateCVC1;
import testDataGen.GenerateDataSet;
import testDataGen.PopulateTestData;
import util.Configuration;
import util.TableMap;
import util.Utilities;
public class RegressionTests {

	String basePath;
	String schema;
	String sampleData;

	public RegressionTests(String basePath, String schemaFile, String sampleDataFile) {
		super();
		this.basePath = basePath;
		this.schema = Utilities.readFile(new File(schemaFile));
		this.sampleData = Utilities.readFile(new File(sampleDataFile));
	}

	private Connection getTestConn() throws Exception{
		
		//added by rambabu
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
		return conn;
	}

	/**
	 * Load queries from the queries.txt file
	 * @return Map of queryId,query
	 * @throws IOException
	 */
	private Map<Integer,String> getQueries()	throws IOException {
		Map<Integer,String> queryMap=new HashMap<Integer,String>();
		
		String fullPath=basePath+File.separator+"queries.txt";
		
		FileReader fileReader = new FileReader(new File(fullPath));
		BufferedReader bufferedReader = new BufferedReader(fileReader);
		String line;
		while ((line = bufferedReader.readLine()) != null) {
			if(line.trim().startsWith("--"))
				continue;
			//The queries file contains entries for queries in the format id|query
			String[] lineArr=line.split("\\|", 2);
			if(lineArr.length<2)
				continue;
			if(lineArr[1]==null || lineArr[1].trim().equals(""))
				continue;
			Integer queryId=0;
			try {
				queryId=Integer.parseInt(lineArr[0].trim());
			} catch(NumberFormatException nfe) {
				continue;
			}
			queryMap.put(queryId, lineArr[1]);
		}
		fileReader.close();
		
		
		return queryMap;
	}
	
	/**
	 * Gets mutants from the mutants.txt file
	 * @return map of queryId, list of mutants
	 * @throws IOException
	 */
	private Map<Integer,List<String>> getMutants() throws IOException	{
		Map<Integer,List<String>> mutantMap=new HashMap<Integer,List<String>>();
		
		String fullPath=basePath+File.separator+"mutants.txt";
		
		FileReader fileReader = new FileReader(new File(fullPath));
		BufferedReader bufferedReader = new BufferedReader(fileReader);
		String line;
		while ((line = bufferedReader.readLine()) != null) {
			if(line.trim().startsWith("--"))
				continue;
			//The mutants file contains entries for queries in the format id|query. The id should match the query
			String[] lineArr=line.split("\\|", 2);
			if(lineArr.length<2)
				continue;
			if(lineArr[1]==null || lineArr[1].trim().equals(""))
				continue;
			Integer queryId=0;
			try {
				queryId=Integer.parseInt(lineArr[0].trim());
			} catch(NumberFormatException nfe) {
				continue;
			}
			List<String> mutantList= mutantMap.get(queryId);
			if(mutantList==null)	{
				mutantList=new ArrayList<String>();
			}
			mutantList.add(lineArr[1]);
			mutantMap.put(queryId, mutantList);
		}
		fileReader.close();
		
		
		return mutantMap;
	}
	
	
	private List<String> generateDataSets(Integer queryId, String query)	{
		
		try(Connection conn =getTestConn()){

			boolean orderDependent=false;
			String tempFilePath=File.separator+queryId;
			GenerateDataSet d=new GenerateDataSet();
			List<String> datasets=d.generateDatasetForQuery(conn,queryId,query,  schema,  sampleData,  orderDependent,  tempFilePath, null);
			return datasets;
		} catch(Exception e) {
			e.printStackTrace();
		}

		return null;
		
	}
	
	/**
	 * Tests if the basic dataset produces a non-empty result
	 * @param queryId queryId of the dataset
	 * @param query query
	 * @return
	 */
	private boolean testBasicDataset(Integer queryId, String query)	{
		
		try(Connection testConn=getTestConn()){
			String filePath=queryId+"";
			
			PopulateTestData.deleteAllTablesFromTestUser(testConn);
			GenerateDataSet.loadSchema(testConn, schema);
			GenerateDataSet.loadSampleData(testConn, sampleData);
			
			TableMap tableMap=TableMap.getInstances(testConn, 1);
			//System.out.println("Testing BASIC Dataset >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
			PopulateTestData.loadCopyFileToDataBase(testConn, "DS0", filePath, tableMap);
			//PopulateTestData.loadSQLFilesToDataBase(testConn, "DS0.sql", filePath);
			
			PreparedStatement ptsmt=testConn.prepareStatement(query);
			ResultSet rs=ptsmt.executeQuery();
			if(rs.next()) {
				return true;
			}
		}catch(Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	private boolean testMutantKilling(Integer queryId, List<String> datasets, String query, String mutant) {
		//System.out.println("1-Entering RegressionTest.java->testMutantKilling");
		for(String datasetId:datasets) {
			
			try(Connection testConn=getTestConn()){
				String filePath=queryId+"";
				
                //System.out.println("2-Entering RegressionTest.java->testMutantKilling");
				GenerateDataSet.loadSchema(testConn, schema);
				GenerateDataSet.loadSampleData(testConn, sampleData);
				//System.out.println("3-Entering RegressionTest.java->testMutantKilling");
				TableMap tableMap=TableMap.getInstances(testConn, 1);
				//System.out.println("MUTANT TESTING: dataset id "+datasetId+" >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
				PopulateTestData.loadCopyFileToDataBase(testConn, datasetId, filePath, tableMap);
				//PopulateTestData.loadSQLFilesToDataBase(testConn, datasetId+".sql", filePath);
				//System.out.println("4-Entering RegressionTest.java->testMutantKilling");
				//Added by Akku
				Map<String, Integer> check_tables = PopulateTestData.getNamesOfReferencedTables();
				
				//Added by Akku end's
				
				
				
				//String testQuery= "with q as ("+query+") , m as("+mutant+") (select * from q EXCEPT ALL m) UNION ALL (select * from m EXCEPT ALL q)";
				
				String testQuery="(("+query+") EXCEPT ALL ("+mutant+")) UNION (("+mutant+") EXCEPT ALL ("+query+"))";
				   
				
				PreparedStatement ptsmt=testConn.prepareStatement(testQuery);
				ResultSet rs=ptsmt.executeQuery();
				
				//added by Akanksha
				ResultSetMetaData rsmd = rs.getMetaData();
				int columnsNumber = rsmd.getColumnCount();
				//Added by Akanksha ends
				
				if(rs.next()) {
					//Added by Akanksha
					//System.out.println("");
					System.out.println("Your Submission Failed On Following DataSet : ");
				
					//Added by Akku
					
					for(int f=0;f<tableMap.foreignKeyGraph.topSort().size();f++){
						String tableName = tableMap.foreignKeyGraph.topSort().get(f).toString();
						
						if (check_tables.containsKey(tableName)) {
							continue;
						} 
						
						String selectQuery = "SELECT * FROM " + tableName;
                          try (PreparedStatement stmt = testConn.prepareStatement(selectQuery)) {
				            try (ResultSet rs1 = stmt.executeQuery()) {
				            	if(rs1.next()) {
				            	System.out.print(tableName);
				            	ResultSetMetaData rsmd1 = rs1.getMetaData();
				                int columnCount = rsmd1.getColumnCount();
				                String colnames[]=new String[columnCount];
				                for (int i = 0; i < columnCount; i++) { 
			                        String columnName = rsmd1.getColumnName(i+1);
			                        colnames[i]=columnName;
			                      
				                }
                               String colvalues[][] =new String[0][columnCount];
				                do{
				                	String rowadd[]=new String[columnCount];
				                    for (int i = 0; i < columnCount; i++) {
				                       
				                        String columnValue = rs1.getString(i+1);
				                        rowadd[i]=columnValue;
				                       
				                    } 
				                    colvalues = Arrays.copyOf( colvalues,  colvalues.length + 1); // increase the array size by 1
				                    colvalues[ colvalues.length - 1] = rowadd;
				                   
				                }while (rs1.next());
				             
				                TextTable tt = new TextTable(colnames, colvalues);
				                
				        		tt.printTable();
				        		System.out.println("");
				               
				            }
				            }
				        }
				    }
					//Added by Akku ends
					System.out.println("Your Result");
					//Populate result of mutant on console -> Added by Akanksha
					PreparedStatement mutant_ptsmt=testConn.prepareStatement(mutant);
					ResultSet mutant_rs=mutant_ptsmt.executeQuery();
					
					ResultSetMetaData mutant_rsmd = rs.getMetaData();
					int colNum = mutant_rsmd.getColumnCount();
					
					if(mutant_rs.next())
					{
					
					String colnames[]=new String[colNum];
	                for (int i = 0; i < colNum; i++) { 
                        String columnName = mutant_rsmd.getColumnName(i+1);
                        colnames[i]=columnName;
                      
	                }
				
					
					  String colvalues[][] =new String[0][colNum];
		                do{
		                	String rowadd[]=new String[colNum];
		                    for (int i = 0; i < colNum; i++) {
		                       
		                        String columnValue = mutant_rs.getString(i+1);
		                        rowadd[i]=columnValue;
		                       
		                    } 
		                    colvalues = Arrays.copyOf( colvalues,  colvalues.length + 1); // increase the array size by 1
		                    colvalues[ colvalues.length - 1] = rowadd;
		                   
		                }while (mutant_rs.next());
		             
		                TextTable tt = new TextTable(colnames, colvalues);
		                
		        		tt.printTable();
		        		System.out.println("\n");
					}
					
					else {
						System.out.println("|" + " Empty "+"|\n");
						
					}
		        		
		        		//populate correct query result ->Added by Akanksha
		        		System.out.println("Expected Result");
					
						PreparedStatement query_ptsmt=testConn.prepareStatement(query);
						ResultSet query_rs=query_ptsmt.executeQuery();
						
						ResultSetMetaData query_rsmd = rs.getMetaData();
						int colNum1 = query_rsmd.getColumnCount();
						
						if(query_rs.next()) {
						
						String q_colnames[]=new String[colNum1];
		                for (int i = 0; i < colNum1; i++) { 
	                        String columnName = query_rsmd.getColumnName(i+1);
	                        q_colnames[i]=columnName;
	                      
		                }
					
						
						  String q_colvalues[][] =new String[0][colNum1];
			                do{
			                	String rowadd[]=new String[colNum1];
			                    for (int i = 0; i < colNum1; i++) {
			                       
			                        String columnValue = query_rs.getString(i+1);
			                        rowadd[i]=columnValue;
			                       
			                    } 
			                    q_colvalues = Arrays.copyOf( q_colvalues,  q_colvalues.length + 1); // increase the array size by 1
			                    q_colvalues[ q_colvalues.length - 1] = rowadd;
			                   
			                }while (query_rs.next());
			             
			                TextTable tt1 = new TextTable(q_colnames, q_colvalues);
			                
			        		tt1.printTable();
						}
						else {
							System.out.println("|" + " Empty "+"|");
							
						}
					//Added by Akanksha end's,changed below return value to false.
					return false;
				}
			}
			catch(SQLException e) {
				//Added by Akanksha
				//System.out.println("got exception->");

				//e.printStackTrace();
				
				//Modifying the exception message to show only the content on the console instead of using e.printStackTrace()
				String errorMessage = e.getMessage();
			    
			    if (errorMessage != null) {
			        int colonIndex = errorMessage.indexOf(':');
			        if (colonIndex != -1) {
			            String errorPart = errorMessage.substring(colonIndex + 1).trim();
			            System.out.println("Error: " + errorPart);
			        }
			    }

				
				//Added by Akanksha ends,changed below return value to false.
				return false;
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
		return true;
	}
	public QueryStructure partialMarkingTesting(Integer queryId,String query) {
		try(Connection conn =getTestConn()){

			boolean orderDependent=false;
			String tempFilePath=File.separator+queryId;
			GenerateDataSet d=new GenerateDataSet();
			GenerateCVC1 cvc=d.processCanonicalize(conn,queryId,query,  schema,  sampleData,  orderDependent,  tempFilePath, null);
			return cvc.getqStructure();
		} catch(Exception e) {
			e.printStackTrace();
		}

		return null;
		
	}
	public void TestPartialMarking() {
		Map<Integer,String> queryMap=null;
		Map<Integer,List<String>> mutantMap=null;
		
		Map<Integer,List<String>> testResult=new LinkedHashMap<Integer,List<String>>();
		
		try {	
			queryMap = getQueries();
			mutantMap = getMutants();
		}catch(Exception e) {
			System.out.println("Error reading queries or mutants");
			e.printStackTrace();
			
		}
		
		for(Integer queryId:queryMap.keySet()) {
			
			String query=queryMap.get(queryId);
			
			QueryStructure InstructorQuery=partialMarkingTesting(queryId,query);
//			System.out.println("INSTRUCTOR QUERY:"+query);
			
			
			//Check mutation killing
			for(String mutant:mutantMap.get(queryId))	{
				try {
					QueryStructure StudentQuery=partialMarkingTesting(queryId,mutant);
//					System.out.println("STUDENT QUERY:"+mutant);
					Float normalMarks=PartialMarker.calculateScore(InstructorQuery, InstructorQuery, 0).Marks;
					Float studentMarks=PartialMarker.calculateScore(InstructorQuery, StudentQuery, 0).Marks;
					System.out.println("Total Marks:"+normalMarks+" "+"StudentMarks:"+studentMarks+" "+"Partial Marks:"+studentMarks*100/normalMarks);
						
				}catch (Exception e)	{
					e.printStackTrace();
					
				}
			}
			
			
			
		}
		
		
		
	}
	public Map<Integer,List<String>> runRegressionTests() {
		
		Map<Integer,String> queryMap;
		Map<Integer,List<String>> mutantMap;
		
		Map<Integer,List<String>> testResult=new LinkedHashMap<Integer,List<String>>();
		
		try {	
			queryMap = getQueries();
			mutantMap = getMutants();
		}catch(Exception e) {
			System.out.println("Error reading queries or mutants");
			e.printStackTrace();
			return null;
		}
		
		for(Integer queryId:queryMap.keySet()) {
			List<String> errors=new ArrayList<String>();
			
			String query=queryMap.get(queryId);
			//System.out.println(query);
			List<String> datasets;
			//Generate datasets
			datasets=generateDataSets(queryId,query);
			
			if(datasets==null || datasets.isEmpty()) {
				System.out.println("************************Empty dataset");
				errors.add("Exception in generating datasets");
				testResult.put(queryId, errors);
				continue;
			}
				
			//Check if DS0 works
			//System.out.println("Correct Query : " + query);
			try {
				if(testBasicDataset(queryId,query)==false) {
					errors.add("Basic datasets failed");
					System.out.println("BASIC dataset failed: "+queryId);
				}
					
			} catch (Exception e)	{
				e.printStackTrace();
				errors.add("Exception in running query on basic test case");
				testResult.put(queryId, errors);
				System.out.println("EXCEPTION: (query: "+queryId+" ) Exception in running query on basic test case");
				continue;
			}
			
			
			//Check mutation killing
			for(String mutant:mutantMap.get(queryId))	{
				try {
					if(testMutantKilling(queryId, datasets, query, mutant)==false) {
						errors.add(mutant);
						//Below LINE COMMENTED BY akanksha
						//System.out.println(" FAILED FOR MUTANT (query: "+queryId+" )"+mutant);
					}
						
				}catch (Exception e)	{
					e.printStackTrace();
					errors.add("Exception in killing mutant query:"+mutant);
					testResult.put(queryId, errors);
				}
			}
			
			if(!errors.isEmpty())
				testResult.put(queryId, errors);
			
			//added by rambabu for testing
			//Below line commented by Akanksha
			//System.out.println("query id done: "+ queryId);
			
		}
		
		return testResult;
	}
	
	public static void main(String[] args)	throws Exception{
		
		String basePath="test/universityTest";
		//Path of file containing schema
		String schemaFile="test/universityTest/DDL.sql";
		//Path of file containing sampleData
		String sampleDataFile="test/universityTest/sampleData.sql";
		/* runtime analysis for regression test */
		long startTime = System.currentTimeMillis();
		//System.out.println("Starting time of regression test is:");
        //System.out.println(startTime);
		
		//Added by Akanksha to change content of mutant.txt file according to prutor input
		
		try {
	            // Read the content of the file
			    String mutantPath = "test/universityTest/mutants.txt";
			    
			    BufferedReader reader = new BufferedReader(new FileReader(mutantPath));
	            StringBuilder content = new StringBuilder();
	            String line;
	            while ((line = reader.readLine()) != null) {
	                // Remove trailing semicolon
	                line = line.replaceAll(";$", "");
	                content.append(line).append(System.lineSeparator());
	            }
	            reader.close();

	            // Modify the text
	            String modifiedText = "1| " + content.toString();

	            // Write the modified content back to the file
	            BufferedWriter writer = new BufferedWriter(new FileWriter(mutantPath));
	            writer.write(modifiedText);
	            writer.close();

	           // System.out.println("File content modified successfully.");
	        } catch (IOException e) {
	            e.printStackTrace();
	        }
		//modify queries.txt file,append 1| in the beginning of each query and remove trailing ';'
		try {
            // Read the content of the file
		    String queryPath = "test/universityTest/queries.txt";
		    
		    BufferedReader reader = new BufferedReader(new FileReader(queryPath));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                // Remove trailing semicolon
                line = line.replaceAll(";$", "");
                content.append(line).append(System.lineSeparator());
            }
            reader.close();

            // Modify the text
            String modifiedText = "1| " + content.toString();

            // Write the modified content back to the file
            BufferedWriter writer = new BufferedWriter(new FileWriter(queryPath));
            writer.write(modifiedText);
            writer.close();

           // System.out.println("File content modified successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
	
		
		//Added by Akanksha Ends
		
		
		
		RegressionTests r=new RegressionTests(basePath,schemaFile,sampleDataFile);
		Map<Integer,List<String>> errorsMap=r.runRegressionTests();
		
		String errors=""; 
		if(errorsMap==null)
			System.out.println("Exception......");
		else if(errorsMap.isEmpty()) {
			errors="All Test Cases Passed";
		} else {
			r.TestPartialMarking();
		}
		
//		//Added by Akku
		/*else {
			errors+="Following Test Cases Failed\n";
			for(Integer key:errorsMap.keySet()) {
				errors+=key+"|";
				for(String err:errorsMap.get(key)) {
					errors+=err+"|";
				}
				errors+="\n";
			}
		}*/
		
		//Added by Akku end's(commented above else part)
		Utilities.writeFile(basePath+File.separator+"test_result.log", errors);
		//Added by Akanksha,commented the below print statement
	 	System.out.println(errors);
		
		//Added by Akanksha ends
		long stopTime = System.currentTimeMillis();
		//System.out.println("Stopping time of regression test is: ");
	    //System.out.println(stopTime);
        long elapsedTime = stopTime - startTime;
        System.out.println("Total time taken by regression test is: ");
        System.out.print(elapsedTime);
		
	}
	
}
