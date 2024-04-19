

package partialMarking;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import parsing.QueryStructure;
import testDataGen.GenerateCVC1;
import testDataGen.GenerateDataSet;
import util.MyConnection;
import util.Utilities;

public class QueryDetails {
	
	// Start Region - Private members
	

	private GenerateCVC1 data;
		
	// End Region - Private members
	
	// Start Region - Public members
	public String basePath;
	public String query;
	public String schema;
	public String sampleData;

	// End Region - Public members
	
	public GenerateCVC1 getData(){
		return this.data;
	}
	
	public void setData(GenerateCVC1 data){
		this.data = data;
	}
	
	public QueryDetails(){
	}
	
	public int InitializeInstructorQuery(int assignmentId, int questionId, int queryId) throws Exception {		
		String qry = "select * from xdata_instructor_query where assignment_id = ? and question_id = ? and query_id = ?";		
		//Connection conn = MyConnection.getExistingDatabaseConnection();
		try(Connection conn = MyConnection.getDatabaseConnection()){
			try(PreparedStatement pstmt = conn.prepareStatement(qry)){
				pstmt.setInt(1, assignmentId);
				pstmt.setInt(2, questionId);
				pstmt.setInt(3, queryId);
				
				try(ResultSet rs = pstmt.executeQuery()){
					int marks = 0;		
					
					String sqlQuery = null;
					if(rs.next()){
						sqlQuery = rs.getString("sql");
						marks = rs.getInt("marks");
					}
					this.query = sqlQuery;
					//this.initialize(assignmentId, questionId, sqlQuery);
					this.startProcessing(assignmentId, questionId, sqlQuery);
					return marks;
				}
			}
			}
			
		
	}
	
	public void InitializeStudentQuery(int aId, int qId, String rollNum, String guestStudentQuery) throws Exception{		
		String qry = "select * from xdata_student_queries where assignment_id = ? and question_id = ? and rollnum = ?";		
		if(guestStudentQuery == null){
		//Connection conn = MyConnection.getExistingDatabaseConnection();
		try(Connection conn = MyConnection.getDatabaseConnection()){
			try(PreparedStatement pstmt = conn.prepareStatement(qry)){
				pstmt.setInt(1, aId);
				pstmt.setInt(2, qId);
				pstmt.setString(3, rollNum);
				
				try(ResultSet rs = pstmt.executeQuery()){
					String sqlQuery = null;
					if(rs.next()){	
						sqlQuery = rs.getString("querystring");
					}else{
						sqlQuery = guestStudentQuery;
					}
					this.query = sqlQuery;
					//this.initialize(aId, qId, sqlQuery);
					this.startProcessing(aId, qId, sqlQuery);
		}
		}
		}
	}else{
		String sqlQuery = guestStudentQuery;
		this.query = sqlQuery;
		//this.initialize(aId, qId, sqlQuery);
		this.startProcessing(aId, qId, sqlQuery);
	}
		
	}
	
	QueryStructure qStructure;
	
	public QueryStructure getQueryStructure(){
		return qStructure;
	}
	
	//added by rama chandra
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
		
		try(Connection conn =MyConnection.getTesterConn()){

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
public Map<Integer,List<String>> runPartialMarkingTest() {
		
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
			
			
		}
		
		return testResult;
	}
	//adding ends by rama chandra

	public void startProcessing(int assignmentId, int questionId, String query) throws Exception {

		GenerateCVC1 cvc = new GenerateCVC1();
//		cvc.setFne(false); 
//		cvc.setIpdb(false);
//		cvc.setFilePath(this.getFilePath());
		cvc.setAssignmentId(assignmentId);
		cvc.setQuestionId(questionId);
		cvc.setQueryId(1);
		cvc.setCourseId("");
		String basePath="test/universityTest";
		//Path of file containing schema
		String schemaFile="test/universityTest/DDL.sql";
		//Path of file containing sampleData
		String sampleDataFile="test/universityTest/sampleData.sql";
		this.basePath = basePath;
		this.schema = Utilities.readFile(new File(schemaFile));
		this.sampleData = Utilities.readFile(new File(sampleDataFile));

//		GenerateDataSet preProcess = new GenerateDataSet();
		System.out.println(query);
		/* preProcess.initializeConnectionDetails(cvc); */
		runPartialMarkingTest();
				
		qStructure=new QueryStructure(cvc.getTableMap());
				
		if(cvc!= null && cvc.getConnection() != null){
			 cvc.closeConn();
			}

		qStructure.buildQueryStructure("1",query);
		
		
	}


}

