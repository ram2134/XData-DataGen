package evaluation;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.*;
import java.text.*;
import java.sql.*;
//import database.CommonFunctions;
//import database.DatabaseProperties;
import net.sf.jsqlparser.parser.CCJSqlParserManager;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;

import com.google.gson.Gson;

import util.Configuration;
import util.DataSetValue;
//import util.DatabaseConnection;
import util.DatabaseHelper;
import util.MyConnection;
import util.TableMap;
import util.Utilities;
import java.util.*;

import testDataGen.GenerateCVC1;
import testDataGen.PopulateTestDataGrading;
import testDataGen.GenerateDataSet;
import partialMarking.MarkInfo;
import partialMarking.PartialMarker;

import java.lang.reflect.Type;
import com.google.gson.reflect.TypeToken;

import evaluation.QueryStatusData.QueryStatus;

import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.postgresql.util.PSQLException;

//MD5 HASH for table :  SELECT md5(CAST((array_agg(f.*))AS text)) FROM xdata_users f; 

public class TestAnswer {

	private static Logger logger = Logger.getLogger(TestAnswer.class.getName());
	public boolean isView=false;
	public boolean isCreateView=false;

	Vector<String>  datasets;
	public TestAnswer() {

	}
	public static void Dataset(String mut, String ds,String filePath, String query){		
	}


	@Deprecated
	public static String preParseQuery(String queryString) throws Exception{

		StringTokenizer st=new StringTokenizer(queryString.trim());
		String token=st.nextToken();

		if(!token.equalsIgnoreCase("with")){
			return queryString;
		}
		int numberOfAlias=0;
		String aliasname[]=new String[10];
		String subquery[]=new String[10];

		while(true){

			String columnname="";
			aliasname[numberOfAlias]=st.nextToken();

			if(aliasname[numberOfAlias].contains("(")){

				columnname=aliasname[numberOfAlias].substring(aliasname[numberOfAlias].indexOf("("));
				columnname=columnname.substring(1,columnname.length()-1);	//remove ( & )

				aliasname[numberOfAlias]=aliasname[numberOfAlias].substring(0,aliasname[numberOfAlias].indexOf("("));           	

			}
			token=st.nextToken();   	// should be AS key word or should start with (

			if(token.startsWith("(")){
				while(!token.contains(")")){
					columnname+=token;
					token=st.nextToken();
				}
				columnname+=token;            	
				token=st.nextToken();	// should be AS key word
			}

			if(!token.equalsIgnoreCase("as")){            	
				Exception e= new Exception("Error while preparsing the with clause AS expected");
				throw e;
			}

			subquery[numberOfAlias]="(";
			queryString=queryString.substring(queryString.indexOf("(")+1);
			if(columnname.length()!=0){
				queryString=queryString.substring(queryString.indexOf("(")+1);
			}

			int count=1,i=0;
			while(count!=0){
				if(queryString.charAt(i)=='('){
					count++;
				}else if(queryString.charAt(i)==')'){
					count--;
				}
				subquery[numberOfAlias]+=queryString.charAt(i);
				i++;
			}
			queryString=queryString.substring(i).trim();

			if(columnname.length()!=0){
				columnname=columnname.substring(1,columnname.length()-1);
				String columnlist[]=columnname.split(",");
				int ctr=0;
				String temp=subquery[numberOfAlias];
				subquery[numberOfAlias]="";            	
				String tok=temp.substring(0,temp.indexOf("from"));
				for(int j=0;j<tok.length();j++){
					if(tok.charAt(j)==','){
						subquery[numberOfAlias]+=" as "+columnlist[ctr++]+" , ";
					}else{
						subquery[numberOfAlias]+=tok.charAt(j);
					}

				}            	            	
				subquery[numberOfAlias]+=" as "+columnlist[ctr]+" "+temp.substring(temp.indexOf("from"));
			}

			numberOfAlias++;
			if(queryString.charAt(0)!=','){            	
				break;
			}else{
				st=new StringTokenizer(queryString.substring(1).trim());
			}

		}

		String newquery="";
		/*Add the select part to new query */
		st=new StringTokenizer(queryString);                    

		while(st.hasMoreTokens()){

			token=st.nextToken();

			if(token.toLowerCase().equals("from")){
				newquery+=token+ " ";
				newquery = parseFromPart(st, newquery, numberOfAlias, subquery, aliasname);				
			}
			else{			
				newquery+=token+ " ";
			}
		}

		return newquery;
	}

	private static String parseFromPart(StringTokenizer st, String newquery, int numberOfAlias, String subquery[], String aliasname[]){

		String token;

		while(st.hasMoreTokens()){
			token=st.nextToken();            

			if(token.equalsIgnoreCase("where")||token.equalsIgnoreCase("group")){
				newquery+=token+ " ";
				break;
			}			

			if(token.equals(",")){
				newquery+=token+ " ";
			}
			if(token.contains(",")){
				token+=" ";
				String tablenames[]=token.split(",");
				for(int j=0;j<tablenames.length;j++){
					boolean isPresent=false;
					for(int k=0;k<numberOfAlias;k++){
						if(tablenames[j].equals(aliasname[k])){
							newquery+=subquery[k] + " " + aliasname[k]+" ";
							isPresent=true;
						}
					}
					if(!isPresent){
						newquery+=tablenames[j]+" ";
					}
					newquery+=",";
				}
				newquery=newquery.substring(0,newquery.length()-1);

			}else if(token.contains(")")){
				String relationName = token.substring(0, token.length() - 1);				

				boolean isPresent=false;
				for(int k=0;k<numberOfAlias;k++){
					if(relationName.equals(aliasname[k])){
						newquery+=subquery[k] + " " + aliasname[k]+" ";
						isPresent=true;
					}
				}
				if(!isPresent){
					newquery+=relationName + " ";
				}
				newquery+=")";

			}else{
				boolean isPresent=false;
				for(int k=0;k<numberOfAlias;k++){
					if(token.equals(aliasname[k])){
						newquery+=subquery[k] + " " + aliasname[k]+" ";
						isPresent=true;
					}
				}
				if(!isPresent){
					newquery+=token+" ";
				}
			}

		}

		return newquery;		
	}

	public static Vector<String> checkAgainstOriginalQuery(HashMap<String,String> mutants, String datasetName, String queryString, String filePath, 
			boolean orderIndependent, Vector<String> columnmismatch, Connection conn) {
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		String temp="";
		boolean isInsertquery=false;
		boolean isUpdatequery=false;
		boolean isDeletequery=false; 
		boolean next1 = true;
		boolean next2 = true;
		Vector<String> queryIds = new Vector<String>();		
		//System.out.println(">>>>>>>>>>>>>>>>QUERY STRING:::"+queryString);
		try{

			try{
				pstmt = conn.prepareStatement("drop table if exists dataset;");
				pstmt.execute();
				//Create temporary tables based on the dataset (data generated by Xdata) that is passed
				pstmt = conn.prepareStatement("CREATE TEMPORARY TABLE dataset(name varchar(20))");
				//"AS (SELECT * FROM dataset WHERE (1=0))");
				pstmt.executeUpdate();
			}
			catch(SQLException ex){
				int errorCode = ex.getErrorCode();
				logger.log(Level.SEVERE,"SQL Exception: "+ex.getMessage(),ex);
			} 

			pstmt = conn.prepareStatement("insert into dataset values('" + datasetName + "')");
			pstmt.executeUpdate();
			int i=1;			

			logger.log(Level.INFO,"queryString" + queryString);
			queryString=queryString.trim().replaceAll("\n+", " ");
			queryString=queryString.trim().replaceAll(" +", " ");	
			queryString = queryString.trim().replace(";", " ");

			String mutant_query="";
			Collection c = mutants.keySet();
			Iterator itr = c.iterator();
			while(itr.hasNext()){
				Object Id = itr.next();
				String mutant_qry = mutants.get(Id);
				mutant_qry=mutant_qry.trim().replace(';', ' ');				

				if(isInsertquery){					
					mutant_qry=convertInsertQueryToSelect(mutant_qry);
				}else if(isDeletequery){
					mutant_qry=convertDeleteQueryToSelect(mutant_qry);
				}if(isUpdatequery){
					mutant_qry=convertUpdateQueryToSelect(mutant_qry);
				}

				//TODO:Parse the instructor query to get number of projected columns
				//CCJSqlParserManager pm = new CCJSqlParserManager();
				//Statement stmnt = pm.parse(new StringReader(queryString));
				//PlainSelect plainSelect =  (PlainSelect)((Select) stmnt).getSelectBody();
				//List<SelectItem> rcList = plainSelect.getSelectItems();
				//Parse the instructor query to get number of projected columns
				/*CCJSqlParserManager pm = new CCJSqlParserManager();
				Statement stmnt = pm.parse(new StringReader(queryString));
				PlainSelect plainSelect =  (PlainSelect)((Select) stmnt).getSelectBody();
				List<SelectItem> rcList = plainSelect.getSelectItems();*/


				PreparedStatement pstmt11 = conn.prepareStatement(queryString);
				PreparedStatement pstmt22 = conn.prepareStatement(mutant_qry);
				logger.log(Level.FINE,"****************************************");
				logger.log(Level.FINE,"Instructor query ----" +queryString.toString());
				logger.log(Level.FINE,"****************************************");
				logger.log(Level.FINE,"Mutant query ----" + mutant_qry.toString());
				logger.log(Level.FINE,"*****************************************");
				if(orderIndependent){
					try{				
						//Run both the queries against the temporary data set that is generated.

						pstmt = conn.prepareStatement("with x1 as (" + queryString + ")," +
								" x2 as (" + mutant_qry + ") select 'Q" + i 
								+ " was killed by ' as const,dataset.name from dataset " +
								"where exists ((select * from x1) except all (select * from x2)) " +
								"or exists ((select * from x2) except all (select * from x1))");
						logger.log(Level.FINE,"Comparing Query *** " +pstmt.toString());		
						rs = pstmt.executeQuery();
						ResultSet rs11 = pstmt22.executeQuery();
						while(rs11.next()){
							//logger.log(Level.INFO,rs11.getString(1));
						}
						ResultSet rs22 = pstmt11.executeQuery();
						while(rs22.next()){
							//logger.log(Level.INFO,rs22.getString(1));
						}		
						//logger.log(Level.INFO,"--------------------------------------------- ");
					}catch(SQLException s){
						if(s instanceof PSQLException && s.getMessage().trim().equalsIgnoreCase("No results were returned by the query.".trim())){

						}else{
							queryIds.add((String)Id);
							columnmismatch.add((String)Id);
							logger.log(Level.SEVERE,s.getMessage(), s);
							//throw s;
						}
						logger.log(Level.FINE," SQL EXCEPTION"+s.getMessage(),s);
					}
					catch(Exception ex){
						logger.log(Level.SEVERE,ex.getMessage(), ex);
						//ex.printStackTrace();
						try{
							pstmt = conn.prepareStatement(mutant_qry);
							rs = pstmt.executeQuery();
							ResultSetMetaData rsmd=rs.getMetaData();
							Vector<String> projectedCols = new Vector<String>();
							pstmt =conn.prepareStatement(queryString);
							rs =pstmt.executeQuery();
							ResultSetMetaData orgRsmd=rs.getMetaData();
							for(int k=1;k<=orgRsmd.getColumnCount();k++){
								projectedCols.add(orgRsmd.getColumnName(k));
							}
							System.out.println(">>>>>>>>>>>>>>>>Col count:::"+orgRsmd.getColumnCount()+"::AND::"+rsmd.getColumnCount());
							if(orgRsmd.getColumnCount()!=rsmd.getColumnCount()){
								columnmismatch.add((String)Id);
							}
							else{
								for(int k=1;k<=rsmd.getColumnCount();k++){
									if(!projectedCols.contains(rsmd.getColumnName(k))){
										columnmismatch.add((String)Id);
										break;
									}
								}
							}
							queryIds.add((String)Id);
						}catch(Exception e){
							logger.log(Level.SEVERE,e.getMessage(), e);
							//e.printStackTrace();
							queryIds.add((String)Id);
						}
						finally{

							pstmt = conn.prepareStatement("drop table dataset;");
							pstmt.execute();
						}
					}finally{

						pstmt = conn.prepareStatement("drop table dataset;");
						pstmt.execute();
					}
					if(rs==null){
						//						logger.log(Level.INFO,"rs is null");
					}

					else if(rs!=null && rs.next()){

						//logger.log(Level.INFO,"Adding Query Id = "+(String)Id);
						queryIds.add((String)Id);

					}else{
						//logger.log(Level.INFO,"rs is empty");
					}
				}
				else{
					PreparedStatement pstmt1 = conn.prepareStatement(queryString);
					PreparedStatement pstmt2 = conn.prepareStatement(mutant_qry);
					ResultSet rs1 = pstmt1.executeQuery();
					ResultSet rs2 = pstmt2.executeQuery();
					boolean outputEqual = true;
					int k = 1;
					while(rs1!=null && rs1.next() && rs2!=null && rs2.next()){

						while(rs1!=null && rs1.next() && rs2!=null && rs2.next()){
							if(rs1.equals(rs2)){

							}
							if(rs1.getString(k).equals(rs2.getString(k))){

							}
							else{
								outputEqual = false;
							}
							k++;
						}
					}

					if((rs1!=null && rs2 == null) || (rs1== null && rs2 !=null)){	
						outputEqual = false;
					}
					if(!outputEqual){
						queryIds.add((String)Id);
					}

					pstmt1.close();
					pstmt2.close();
					rs1.close();
					rs2.close();
				}

			}
		}catch(Exception e){
			logger.log(Level.SEVERE, "TestAnswer : ", e);
			//e.printStackTrace();
		}
		logger.log(Level.INFO,"--------------------------------------------- ");
		logger.log(Level.INFO,"Dataset: "+datasetName+" Killed mutants: "+queryIds);

		logger.log(Level.INFO,"--------------------------------------------- ");

		try {
			pstmt.close();
			//rs.close();
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "TestAnswer : ", e);
			// TODO Auto-generated catch block
			//e.printStackTrace();
		}


		return queryIds;
	}

	public static ArrayList<FailedDataSetValues> newCheckAgainstOriginalQuery(ArrayList<String> studentRollnums,ArrayList<String> studentQueries,
			String instrQueryId, String datasetName, String queryString, String filePath, 
			boolean orderIndependent, Vector<String> columnmismatch, Connection testConn,Vector<String> resultOnDsetMap,
			int assignmentId,int questionId, String course_id,Connection mainConn,TableMap tm,Vector<String> vs) {

		PreparedStatement pstmt = null;
		ResultSet rs = null;
		Vector<String> queryIds = new Vector<String>();	
		ArrayList<FailedDataSetValues> fdValueList = new ArrayList<FailedDataSetValues>();
		resultOnDsetMap = new Vector<String>();
		try{
			String hashValueOfInstructorQueryTable = "";
			try{
				pstmt = testConn.prepareStatement("drop table if exists xdata_temp1;");
				pstmt.execute();
				pstmt = testConn.prepareStatement("drop table if exists xdata_temp2;");
				pstmt.execute();
				pstmt = testConn.prepareStatement("drop table if exists dataset;");
				pstmt.execute();
				//Create temporary tables based on the dataset (data generated by Xdata) that is passed
				pstmt = testConn.prepareStatement("CREATE TEMPORARY TABLE dataset(name varchar(20))");
				//"AS (SELECT * FROM dataset WHERE (1=0))");
				pstmt.executeUpdate();
			}
			catch(SQLException ex){
				int errorCode = ex.getErrorCode();
				logger.log(Level.SEVERE,"SQL Exception: "+ex.getMessage(),ex);
			} 

			pstmt = testConn.prepareStatement("insert into dataset values('" + datasetName + "')");
			pstmt.executeUpdate();
			int i=1;			

			logger.log(Level.INFO,"queryString: " + queryString);
			queryString=queryString.trim().replaceAll("\n+", " ").trim();
			//queryString=queryString.trim().replaceAll(" +", " ");	
			while(queryString.endsWith(";"))
				queryString=queryString.substring(0, queryString.length()-1);
			//queryString = queryString.trim().replace(";", " ");

			try{
				//PreparedStatement pstm1 = conn.prepareStatement("create temporary table xdata_temp1  as ("+queryString+");");
				//PreparedStatement pstm1 = conn.prepareStatement("with x1 as ("+queryString+") SELECT md5(CAST((array_agg(f.*))AS text)) FROM x1 as f;");
				//pstm1.execute();
			}catch(Exception e){
				logger.log(Level.SEVERE,"Error in Instructor Query :" +e.getMessage(),e);	
			}

			//Calculate the MD5 HAsh for xdata_temp1 : using SELECT md5(CAST((array_agg(f.*))AS text)) FROM xdata_temp1 f; 
			//PreparedStatement hashStmnt = conn.prepareStatement("SELECT md5(CAST((array_agg(f.*))AS text)) FROM xdata_temp1 f");
			
			// added by rambabu for mysql
			DatabaseMetaData dbmd=testConn.getMetaData();      
			String dbType = dbmd.getDatabaseProductName(); 
			PreparedStatement hashStmnt = null;
			if (dbType.equalsIgnoreCase("MySql"))
			{
				//hashStmnt = testConn.prepareStatement("with x1 as ("+queryString+") SELECT md5(GROUP_CONCAT(f.*)) FROM x1 as f;");
				hashStmnt = testConn.prepareStatement(queryString);
				ResultSet temp_rs = hashStmnt.executeQuery();
				ResultSetMetaData rsmd=temp_rs.getMetaData();
				String col_names = "";
				for(int k=1; k<=rsmd.getColumnCount();k++)
				{
					col_names+=rsmd.getColumnName(k);
					if(k!=rsmd.getColumnCount())
						col_names+=", ";
				}
				
				System.out.println(col_names);
				
				hashStmnt = testConn.prepareStatement("with x1 as ("+queryString+") SELECT md5(GROUP_CONCAT("+col_names+")) FROM x1 as f;");
			}
			else if(dbType.equalsIgnoreCase("PostgreSQL")) {
				hashStmnt = testConn.prepareStatement("with x1 as ("+queryString+") SELECT md5(CAST((array_agg(f.* order by f.*))AS text)) FROM x1 as f;");
			}
			
			ResultSet hashResult = hashStmnt.executeQuery();
			if(hashResult.next()){
				hashValueOfInstructorQueryTable = hashResult.getString(1);
			}
			logger.log(Level.FINE, "Hash of xdata_temp1 : "+hashValueOfInstructorQueryTable);
			logger.log(Level.FINE,"DATASET ID : "+ datasetName);
			logger.log(Level.FINE,"Instructor query -" +queryString.toString());

			for(int l = 0 ; l < studentQueries.size() ; l++){
				String mutant_qry = studentQueries.get(l);
				String hashValueOfInstructorQueryTableAfterExecution = "";
				if(mutant_qry != null && ! mutant_qry.isEmpty()){
					mutant_qry=mutant_qry.trim().replace(';', ' ');				

					//Parse the instructor query to get number of projected columns
					//CCJSqlParserManager pm = new CCJSqlParserManager();
					//Statement stmnt = pm.parse(new StringReader(queryString));
					//PlainSelect plainSelect =  (PlainSelect)((Select) stmnt).getSelectBody();
					//List<SelectItem> rcList = plainSelect.getSelectItems();


					PreparedStatement pstmt11 = testConn.prepareStatement(queryString);
					PreparedStatement pstmt22 = testConn.prepareStatement(mutant_qry);
					
					logger.log(Level.FINE,"Mutant query -" + mutant_qry.toString());
					
					if(orderIndependent){
						try{
							//added by rambabu for mysql
							// it is equivalent to the query given in postgreSQL case
							if (dbType.equalsIgnoreCase("MySql"))
							{
								
								String tmp_query = "create temporary table xdata_S1 as " + mutant_qry;
								PreparedStatement pstmt_temp = testConn.prepareStatement(tmp_query);
								pstmt_temp.executeUpdate();
								
								ResultSet rs_temp = testConn.prepareStatement("select * from xdata_S1").executeQuery();
								
								ResultSetMetaData rsmd_temp = rs_temp.getMetaData();
								ArrayList<String> SQColList = new ArrayList<String>();
								String SQColList_String = "";
								for(int k=1; k<=rsmd_temp.getColumnCount();k++)
								{
									SQColList.add(rsmd_temp.getColumnName(k));
									//SQColList_String+=rsmd_temp.getColumnName(k);
									if(k!=rsmd_temp.getColumnCount())
										SQColList_String+="C"+String.valueOf(k)+", ";
									else
										SQColList_String+="C"+String.valueOf(k);
								}
								
								String SQselect_String = "";
								
								for(int k=0; k<SQColList.size(); k++) {
									SQselect_String+= SQColList.get(k)+" as C"+ String.valueOf(k+1);
									if(k!=SQColList.size()-1)
										SQselect_String+=", ";
								}
								
								
								tmp_query = "create temporary table xdata_I1 as "+queryString;
								pstmt_temp = testConn.prepareStatement(tmp_query);
								pstmt_temp.executeUpdate();
								rs_temp = testConn.prepareStatement("select * from xdata_I1").executeQuery();
								
								rsmd_temp = rs_temp.getMetaData();
								
								ArrayList<String> IQColList = new ArrayList<String>();
								String IQColList_String = "";
								for(int k=1; k<=rsmd_temp.getColumnCount();k++)
								{
									IQColList.add(rsmd_temp.getColumnName(k));
									//IQColList_String+=rsmd_temp.getColumnName(k);
									if(k!=rsmd_temp.getColumnCount())
										IQColList_String+="C"+String.valueOf(k)+", ";
									else
										IQColList_String+="C"+String.valueOf(k);
								}
								
								String IQselect_String = "";
								
								for(int k=0; k<IQColList.size(); k++) {
									IQselect_String+= IQColList.get(k)+" as C"+ String.valueOf(k+1);
									if(k!=IQColList.size()-1)
										IQselect_String+=", ";
									}
								
								
								tmp_query = "create temporary table xdata_S2 as select "+ SQselect_String + 
											", count(*) as S_cnt from xdata_S1 group by " +SQColList_String;
								pstmt_temp = testConn.prepareStatement(tmp_query);
								pstmt_temp.executeUpdate();
								
								tmp_query = "create temporary table xdata_I2 as select "+ IQselect_String + 
										", count(*) as I_cnt from xdata_I1 group by " +IQColList_String;
								pstmt_temp = testConn.prepareStatement(tmp_query);
								pstmt_temp.executeUpdate();
								// duplicate copy of xdata_I2 and xdata_S2 as temp tables can't be accessed two times in single query in MySql
								pstmt_temp = testConn.prepareStatement("create temporary table xdata_I2_copy as select * from xdata_I2");
								pstmt_temp.executeUpdate();
								
								pstmt_temp = testConn.prepareStatement("create temporary table xdata_S2_copy as select * from xdata_S2");
								pstmt_temp.executeUpdate();
								
								
								tmp_query = "select * from xdata_I2 natural left join xdata_S2_copy "+
											"where S_cnt is null "+
											"union "+
											"select * from xdata_S2 natural left join xdata_I2_copy "+
											"where I_cnt is null";
								
								pstmt = testConn.prepareStatement(tmp_query);
								
								logger.log(Level.FINE,"Student Id : "+studentRollnums.get(l)+" evaluated");
								
								//pstmt.setQueryTimeout(60);
								rs = pstmt.executeQuery();
								
								pstmt_temp = testConn.prepareStatement("drop table xdata_S2, xdata_S1, xdata_I2, xdata_I1, xdata_I2_copy, xdata_S2_copy");
								pstmt_temp.executeUpdate();
							}
							else if(dbType.equalsIgnoreCase("PostgreSQL")) {
							
							pstmt = testConn.prepareStatement("with x1 as (" + queryString + ")," +
									" x2 as (" + mutant_qry + ") select 'Q" + i 
									+ " was killed by ' as const "+//,dataset.name from dataset " +
									"where exists ((select * from x1) except all (select * from x2)) " +
									"or exists ((select * from x2) except all (select * from x1))");
							
							logger.log(Level.FINE,"Student Id : "+studentRollnums.get(l)+" evaluated");
							
							//pstmt.setQueryTimeout(60);
							rs = pstmt.executeQuery();
							}
							
							
							ResultSet rs11 = pstmt22.executeQuery(); 
							while(rs11.next()){ 
								//logger.log(Level.INFO,rs11.getString(1));
							}
							ResultSet rs22 = pstmt11.executeQuery();
							while(rs22.next()){
								//logger.log(Level.INFO,rs22.getString(1));
							}		

							//Check the MD5 HASH of instructor table with newly calculated HASH - if it does not match, mark student query as incorrect
							//Failed - and re-run the populateData method to re-load the dataset again.
							//	PreparedStatement hashStmnt1 = conn.prepareStatement("SELECT md5(CAST((array_agg(f.*))AS text)) FROM xdata_temp1 f");
							
							// added by rambabu for mysql
							PreparedStatement hashStmnt1 = null;
							if (dbType.equalsIgnoreCase("MySql"))
							{
								//hashStmnt = testConn.prepareStatement("with x1 as ("+queryString+") SELECT md5(GROUP_CONCAT(f.*)) FROM x1 as f;");
								
								hashStmnt = testConn.prepareStatement(queryString);
								ResultSet temp_rs = hashStmnt.executeQuery();
								ResultSetMetaData rsmd=temp_rs.getMetaData();
								String col_names = "";
								for(int k=1; k<=rsmd.getColumnCount();k++)
								{
									col_names+=rsmd.getColumnName(k);
									if(k!=rsmd.getColumnCount())
										col_names+=", ";
								}
								
								System.out.println(col_names);
								
								hashStmnt1 = testConn.prepareStatement("with x1 as ("+queryString+") SELECT md5(GROUP_CONCAT("+col_names+")) FROM x1 as f;");
							}
							else if(dbType.equalsIgnoreCase("PostgreSQL")) {
								hashStmnt1 = testConn.prepareStatement("with x1 as ("+queryString+") SELECT md5(CAST((array_agg(f.* order by f.*))AS text)) FROM x1 as f;");
							}
							
							ResultSet hashResult1 = hashStmnt1.executeQuery();
							if(hashResult1.next()){
								hashValueOfInstructorQueryTableAfterExecution = hashResult1.getString(1);
							} 


							if(hashValueOfInstructorQueryTableAfterExecution!= null && hashValueOfInstructorQueryTable != null 
									&& !(hashValueOfInstructorQueryTableAfterExecution.equals(hashValueOfInstructorQueryTable))){
								//Student query fails as it has changed some existing table data 
								resultOnDsetMap.add(studentRollnums.get(l));
								queryIds.add(instrQueryId);
								//Re-Load the data set
								PopulateTestDataGrading p = new PopulateTestDataGrading();
								if(! filePath.equals("NoPath")){
									//Load the DataSet 
									p.populateTestDataForTesting(vs, filePath+"/"+datasetName, tm, testConn, assignmentId, questionId);
								}else{
									//Load DefaultDataSet
									String dsName = p.createTempTableWithDefaultData(mainConn,testConn,assignmentId,questionId,course_id,
											datasetName);
								}
							}
							//if mustant_query not empty
						}

						catch(SQLException s){
							if(s instanceof PSQLException && s.getMessage().trim().equalsIgnoreCase("No results were returned by the query.".trim())){

							}else{
								queryIds.add(instrQueryId);
								columnmismatch.add(instrQueryId);
								resultOnDsetMap.add(studentRollnums.get(l));							
								//logger.log(Level.INFO,"Error in query of "+studentRollnums.get(l)+":"+s.getMessage(), s);
								//throw s;
							}
							logger.log(Level.INFO,"Error in query of "+studentRollnums.get(l)+":"+s.getMessage());
						}
						catch(Exception ex){
							logger.log(Level.INFO,"Error in query of "+studentRollnums.get(l)+":"+ex.getMessage());
							//ex.printStackTrace();
							try{
								pstmt = testConn.prepareStatement(mutant_qry);
								rs = pstmt.executeQuery();
								ResultSetMetaData rsmd=rs.getMetaData();
								Vector<String> projectedCols = new Vector<String>();
								pstmt =testConn.prepareStatement(queryString);
								rs =pstmt.executeQuery();
								ResultSetMetaData orgRsmd=rs.getMetaData();
								for(int k=1;k<=orgRsmd.getColumnCount();k++){
									projectedCols.add(orgRsmd.getColumnName(k));
								}
								if(orgRsmd.getColumnCount()!=rsmd.getColumnCount()){
									columnmismatch.add(instrQueryId);
								}
								else{
									for(int k=1;k<=rsmd.getColumnCount();k++){
										if(!projectedCols.contains(rsmd.getColumnName(k))){
											columnmismatch.add(instrQueryId);
											break;
										}
									}
								}
								queryIds.add(instrQueryId);
								resultOnDsetMap.add(studentRollnums.get(l));
							}catch(Exception e){
								logger.log(Level.INFO,"Error in query of "+studentRollnums.get(l)+":"+e.getMessage());
								//e.printStackTrace();
								queryIds.add(instrQueryId);
								resultOnDsetMap.add(studentRollnums.get(l));
							}
							finally{

								pstmt = testConn.prepareStatement("drop table if exists xdata_temp2;");
								pstmt.execute();
								pstmt = testConn.prepareStatement("drop table  if exists dataset;");
								pstmt.execute();
							}
						}finally{						
							pstmt = testConn.prepareStatement("drop table  if exists xdata_temp2;");
							pstmt.execute();
							pstmt = testConn.prepareStatement("drop table  if exists dataset;");
							pstmt.execute();
						}

						if(rs==null){
							//						logger.log(Level.INFO,"rs is null");
						}

						else if(rs!=null && rs.next()){						
							//logger.log(Level.INFO,"Adding Query Id = "+(String)Id);
							queryIds.add(instrQueryId);
							resultOnDsetMap.add(studentRollnums.get(l));
							logger.log(Level.INFO, rs.getMetaData().toString()+" "+ rs.getString(1));
						}else{
							//logger.log(Level.INFO,"rs is empty");
						}
					}
					else{
						PreparedStatement pstmt1 = testConn.prepareStatement(queryString);
						PreparedStatement pstmt2 = testConn.prepareStatement(mutant_qry);
						ResultSet rs1 = pstmt1.executeQuery();
						ResultSet rs2 = pstmt2.executeQuery();
						boolean outputEqual = true;
						int k = 1;
						while(rs1!=null && rs1.next() && rs2!=null && rs2.next()){

							while(rs1!=null && rs1.next() && rs2!=null && rs2.next()){
								if(rs1.equals(rs2)){

								}
								if(rs1.getString(k).equals(rs2.getString(k))){

								}
								else{
									outputEqual = false;
								}
								k++;
							}
						}

						if((rs1!=null && rs2 == null) || (rs1== null && rs2 !=null)){	
							outputEqual = false;
						}
						if(!outputEqual){
							queryIds.add(instrQueryId);
							resultOnDsetMap.add(studentRollnums.get(l));
						}

						pstmt1.close();
						pstmt2.close();
						rs1.close();
						rs2.close();
					}
				}//if mutant_query is not null
				else{
					//If student_query is null, add it as failed
					queryIds.add(instrQueryId);
					resultOnDsetMap.add(studentRollnums.get(l));
				}

				//Call the methods for storing the failed student_id and table data information in FailedDataSet object and return the list of FailesDSObject
				TestAnswer ta = new TestAnswer();
				if(resultOnDsetMap.contains(studentRollnums.get(l))){
					FailedDataSetValues fdv = new FailedDataSetValues();
					//Set failed Student details
					fdv.setStudentRollNo(studentRollnums.get(l));
					fdv.setDataSetName(datasetName);
					fdv.setInstrQuery(queryString);
					//fdv.setDataSetId();
					fdv.setStatus("Failed");
					Map<String, Map<String, ArrayList<String>>> failedStudDataMap = new HashMap<String, Map<String, ArrayList<String>>>();
					Map<String, Map<String, ArrayList<String>>> failedInstrDataMap = new HashMap<String, Map<String, ArrayList<String>>>();
					ArrayList<FailedColumnValues> failedList = new ArrayList<FailedColumnValues>();

					fdv.setDataSetId(datasetName);
					if(filePath.equals("NoPath")){
						fdv =  ta.getStudentOutput(testConn, datasetName,  studentQueries.get(l),
								fdv, failedStudDataMap, true);

						fdv = ta.getInstructorOutput(testConn, datasetName,
								fdv.getInstrQuery(), fdv,
								failedInstrDataMap, true);
					}
					else{
						fdv = ta.getStudentOutput(testConn, datasetName,  studentQueries.get(l),
								fdv, failedStudDataMap, false);

						fdv = ta.getInstructorOutput(testConn, datasetName,
								fdv.getInstrQuery(), fdv,
								failedInstrDataMap, false);
					}
					//fdv.getDsValueMap().put(datasetName, failedList);
					fdv.setStudentQueryString(mutant_qry);
					fdValueList.add(fdv);
				}

			}

		}catch(Exception e){
			logger.log(Level.SEVERE, "TestAnswer : ", e);

		}finally{
			try{

				pstmt = testConn.prepareStatement("drop table xdata_temp1;");
				pstmt.execute();

			}catch(SQLException e){
				logger.log(Level.INFO,"TestAnswer : newCheckAgainstQuery : ",e.getMessage());
			}
		}
		logger.log(Level.INFO,"--------------------------------------------- ");
		logger.log(Level.INFO,"Dataset: "+datasetName+" Killed mutants: "+queryIds);		
		logger.log(Level.INFO,"--------------------------------------------- ");

		try {
			pstmt.close();

		} catch (SQLException e) {
			logger.log(Level.SEVERE, "TestAnswer : ", e);

		}		
		//	return resultOnDsetMap;
		return fdValueList;
	}
	/**
	 * 
	 * @param mutants
	 * @param datasetName
	 * @param queryString
	 * @param filePath
	 * @param orderIndependent
	 * @param columnmismatch
	 * @param conn
	 * @return
	 * @throws IOException
	 * @Deprecated
	 */
	@Deprecated
	public static Vector<String> mutantsKilledByDataset1(HashMap<String,String> mutants, String datasetName, String queryString, String filePath, boolean orderIndependent, Vector<String> columnmismatch,Connection conn) throws IOException{
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		//Connection conn = null;
		String temp="";
		BufferedReader input=null;
		boolean isInsertquery=false;
		boolean isUpdatequery=false;
		boolean isDeletequery=false;

		Set<String> queryIds = new HashSet<String>();
		try{
			int i=1;
			Collection c = mutants.keySet();
			Iterator itr = c.iterator();
			while(itr.hasNext()){
				Object Id = itr.next();
				String mutant_qry = mutants.get(Id);
				mutant_qry=mutant_qry.trim().replace(';', ' ');				

				if(isInsertquery){					
					mutant_qry=convertInsertQueryToSelect(mutant_qry);
				}else if(isDeletequery){
					mutant_qry=convertDeleteQueryToSelect(mutant_qry);
				}if(isUpdatequery){
					mutant_qry=convertUpdateQueryToSelect(mutant_qry);
				}

				PreparedStatement pstmt11 = conn.prepareStatement(queryString);
				PreparedStatement pstmt22 = conn.prepareStatement(mutant_qry);

				if(orderIndependent){
					try{			
						pstmt = conn.prepareStatement("with x1 as (" + queryString + ")," +
								" x2 as (" + mutant_qry + ") " +
								"select 'Q" + i + " was killed by ' as const,dataset.name from " +
								"dataset where " +
								"exists ((select * from x1) except all (select * from x2)) " +
								"or exists ((select * from x2) except all (select * from x1))");
						rs = pstmt.executeQuery();

						ResultSet rs11 = pstmt22.executeQuery();

						while(rs11.next()){
							//logger.log(Level.INFO,rs11.getString(1) );
						}
						//logger.log(Level.INFO,"--------------------------------------------- ");
						ResultSet rs22 = pstmt11.executeQuery();
						while(rs22.next()){
							//logger.log(Level.INFO,rs22.getString(1));
						}
						//logger.log(Level.INFO,"--------------------------------------------- ");

					}catch(Exception ex){
						//ex.printStackTrace();
						//logger.log(Level.SEVERE,"Adding Query Id = "+(Integer)Id,ex);
						try{
							pstmt = conn.prepareStatement(mutant_qry);
							rs = pstmt.executeQuery();
							ResultSetMetaData rsmd=rs.getMetaData();
							Vector<String> projectedCols = new Vector<String>();
							pstmt =conn.prepareStatement(queryString);
							rs =pstmt.executeQuery();
							ResultSetMetaData orgRsmd=rs.getMetaData();
							for(int k=1;k<=orgRsmd.getColumnCount();k++){
								projectedCols.add(orgRsmd.getColumnName(k));
							}
							if(orgRsmd.getColumnCount()!=rsmd.getColumnCount()){
								//columnmismatch.add((String)Id);
								queryIds.add((String)Id);

							}
							else{
								for(int k=1;k<=rsmd.getColumnCount();k++){
									if(!projectedCols.contains(rsmd.getColumnName(k))){
										//columnmismatch.add((String)Id);
										queryIds.add((String)Id);
										break;
									}
								}
							}
						}catch(Exception e){
							logger.log(Level.SEVERE, "Message", e);
							//e.printStackTrace();
							queryIds.add((String)Id);
						}
					}
					if(rs==null){
						//						logger.log(Level.INFO,"rs is null");
					}

					else if(rs!=null && rs.next()){

						//logger.log(Level.INFO,"Adding Query Id = "+(String)Id);
						queryIds.add((String)Id);

					}else{
						//logger.log(Level.INFO,"rs is empty");
					}
				}
				else{// order by clause is dere
					// check output of both the queries row by row
					PreparedStatement pstmt1 = conn.prepareStatement(queryString);
					PreparedStatement pstmt2 = conn.prepareStatement(mutant_qry);
					//logger.log(Level.INFO,"Mutant query "+mutant_qry);
					//logger.log(Level.INFO,"Query String query "+queryString);
					ResultSet rs1 = pstmt1.executeQuery();
					ResultSet rs2 = pstmt2.executeQuery();
					boolean outputEqual = true;
					while(rs1!=null && rs1.next() && rs2!=null && rs2.next()){
						if(rs1.equals(rs2)){

						}
						else{
							outputEqual = false;
						}
					}
					if((rs1!=null && rs2 == null) || (rs1== null && rs2 !=null)){	
						outputEqual = false;
					}
					if(outputEqual){
						queryIds.add((String)Id);
					}
				}

			}
		}catch(Exception e){
			logger.log(Level.SEVERE, "Message", e);
			//e.printStackTrace();
		}finally {
			if(input!=null)
				input.close();
		}
		Vector<String> res = new Vector<String>();

		for(String s : queryIds){
			res.add(s);
		}
		return res;
	}

	/**
	 * 
	 * @param queryString
	 * @return
	 * @throws Exception
	 */
	public static String convertUpdateQueryToSelect(String queryString) throws Exception {

		String out="SELECT ";
		String tablename=queryString.trim().replaceAll("\\s+", " ").split(" ")[1];
		String st[]=queryString.split("=");		
		for(int i=1;i<st.length;i++){

			if(st[i].contains(",")){
				out+=" "+st[i].substring(0,st[i].indexOf(","))+",";
			}else if(st[i].toLowerCase().contains("where")){
				out+=" "+st[i].substring(0,st[i].toLowerCase().indexOf("where"))+" FROM "+tablename+" ";
				out+=st[i].substring(st[i].toLowerCase().indexOf("where"));
			}else{
				out+=" "+st[i]+" FROM "+tablename;
			}

		}
		return out;
	}
	/**
	 * 
	 * @param queryString
	 * @return
	 */
	public static String convertDeleteQueryToSelect(String queryString) {
		String out=queryString;
		out="SELECT * "+queryString.substring(queryString.toLowerCase().indexOf("from"),queryString.length());		
		return out;
	}
	public static String convertInsertQueryToSelect(String queryString) {
		String out=queryString;
		out=out.substring(queryString.toLowerCase().indexOf("select"), queryString.length());
		return out;
	}
	/**
	 * Download the datasets for all the queries for a question
	 * 
	 * @param assignmentId
	 * @param questionId
	 * @param queryId
	 * @param course_id
	 * @param conn
	 * @param dataSetForQueries
	 * @return
	 * @throws Exception
	 */
	public static Map<Integer,Vector<String>> downloadDataSetForAllQueries(int assignmentId, int questionId,int queryId,  
			String course_id, Connection conn,Map <Integer,Vector<String>> dataSetForQueries) throws Exception{

		Vector <String>datasets=new Vector<String>();
		String getDatasetQuery = null;
		getDatasetQuery = "Select * from xdata_datasetvalue where assignment_id = ? and question_id = ? and query_id = ? and course_id=?";

		PreparedStatement smt;
		smt=conn.prepareStatement(getDatasetQuery);
		smt.setInt(1, assignmentId);
		smt.setInt(2,questionId);
		smt.setInt(3,queryId);
		smt.setString(4,course_id);
		ResultSet rs=smt.executeQuery();
		while(rs.next()){

			String datasetid=rs.getString("datasetid");
			String datasetvalue=rs.getString("value");

			datasets.add(datasetid);
			dataSetForQueries.put(rs.getInt("query_id"), datasets);
		}
		return dataSetForQueries;
	}
	/**
	 * Download datasets for single query
	 * 
	 * @param assignmentId
	 * @param questionId
	 * @param queryId
	 * @param course_id
	 * @param conn
	 * @param filePath
	 * @param onlyFirst
	 * @return
	 * @throws Exception
	 */
	public static Map<Integer,Vector<String>> downloadDatasets(int assignmentId, int questionId,int queryId,  String course_id, Connection conn, String filePath, boolean onlyFirst) throws Exception{

		Vector <String>datasets=new Vector<String>();
		Map <Integer,Vector<String>> dataSetForQueries = new HashMap<Integer,Vector<String>>();
		String getDatasetQuery = null;

		if(!onlyFirst){
			getDatasetQuery = "Select * from xdata_datasetvalue where assignment_id = ? and question_id = ? and query_id = ? and course_id=?";
		}
		else{
			getDatasetQuery = "Select * from xdata_datasetvalue where assignment_id = ? and question_id = ?  and query_id = ? and course_id=? and datasetid = 'DS0'";
		}

		PreparedStatement smt;
		smt=conn.prepareStatement(getDatasetQuery);
		smt.setInt(1, assignmentId);
		smt.setInt(2,questionId);
		smt.setInt(3,queryId);
		smt.setString(4,course_id);
		ResultSet rs=smt.executeQuery();
		while(rs.next()){

			String datasetid=rs.getString("datasetid");
			String datasetvalue=rs.getString("value");

			datasets.add(datasetid);
			dataSetForQueries.put(rs.getInt("query_id"), datasets);
			
			//String dsPath=Configuration.homeDir+"/temp_cvc"+filePath+"/"+datasetid;
			String dsPath=Configuration.homeDir+"/temp_smt"+filePath+"/"+datasetid; // added by rambabu
				File f=new File(dsPath);
				if(!f.exists()){
					f.mkdirs();
				}
				else{
					
					//Utilities.deletePath(Configuration.homeDir+"/temp_cvc"+filePath+"/"+datasetid+"/*");
					Utilities.deletePath(Configuration.homeDir+"/temp_smt"+filePath+"/"+datasetid+"/*"); // added by rambabu
					//continue;
				}
				//JSON implementation reqd and test here.
				//It holds JSON obj tat has list of Datasetvalue class

					Gson gson = new Gson();
					//ArrayList dsList = gson.fromJson(value,ArrayList.class);
					Type listType = new TypeToken<ArrayList<DataSetValue>>() {
	                }.getType();

					List<DataSetValue> dsList = new Gson().fromJson(datasetvalue, listType);
					for(int i = 0 ; i < dsList.size();i++ ){
						DataSetValue dsValue = dsList.get(i);
						String tname,values;
						String tablename = dsValue.getFilename().substring(0,dsValue.getFilename().indexOf(".copy"));

						//String tablename=copyfile[i].split(".copy")[0];
						//String tabledata=copyfile[i].split(".copy")[1];
						FileWriter fos = new FileWriter(new File(dsPath, tablename+".copy").toString());
						BufferedWriter brd = new BufferedWriter(fos);
						String writedata="";
						for(int j=0;j<dsValue.getDataForColumn().size();j++){
							writedata+=dsValue.getDataForColumn().get(j)+"\n";					
						}
						//writedata=writedata.substring(0,writedata.length()-1);
						brd.write(writedata);
						brd.close();		
						fos.close();
					}
			 		
		}
		rs.close();
		smt.close();

		return dataSetForQueries;
	}

	/** 
	 * This method test the student query against DS0 when student submits edited query in 
	 * non-interactive mode
	 * 
	 * @param assignmentId
	 * @param questionId
	 * @param query
	 * @param user
	 * @param filePath
	 * @return
	 * @throws Exception
	 */
	public QueryStatus testQueryAnswer(int assignmentId, int questionId,String course_id, String query, String user, String filePath) throws Exception{

		int queryId = 1;
		String qId = "A"+assignmentId+"Q"+questionId+"S"+queryId;
		Map <Integer,Boolean> resultOfDatasetMatchForEachQuery  = new HashMap<Integer,Boolean>();
		ArrayList <Boolean> isDataSetVerified  = null;
		boolean isMatchAllQueryIncorrect = false;
		String matchAllFailedForDS = "";
		QueryStatus status = QueryStatus.Error;

		boolean isQueryPass = false;
		PopulateTestDataGrading p = new PopulateTestDataGrading();
		boolean orderIndependent = false;
		boolean isMatchAll = false;

		try(Connection conn = MyConnection.getDatabaseConnection()){
			try(Connection testConn = MyConnection.getTesterConn()){

				String getMatchAllOption = "select matchallqueries,orderIndependent from xdata_qinfo where assignment_id = ? and question_id = ? and course_id= ?";
				try(PreparedStatement pst = conn.prepareStatement(getMatchAllOption)){
					pst.setInt(1,assignmentId);
					pst.setInt(2,questionId);
					pst.setString(3,course_id);
					try(ResultSet rset = pst.executeQuery()){
						rset.next();
						isMatchAll = rset.getBoolean("matchallqueries");
						orderIndependent = rset.getBoolean("orderIndependent");
					}
				}
				String qry = "select * from xdata_instructor_query where assignment_id = ? and question_id = ? and course_id=?";
				try(PreparedStatement pstmt = conn.prepareStatement(qry)){
					pstmt.setInt(1,assignmentId);
					pstmt.setInt(2,questionId);
					pstmt.setString(3,course_id);

					try(ResultSet rs = pstmt.executeQuery()){

						while(rs.next()){
							//If Instructor has chosen match All queries option, get all datasets
							// match student query against all and return true only if all ds matches
							String sqlQuery=rs.getString("sql");
							queryId = rs.getInt("query_id");
							qId = "A"+assignmentId +"Q"+questionId+"S"+queryId;
							query=checkForViews(query,user);
							HashMap<String,String> mutants = new HashMap<String,String>();
							mutants.put(qId, query);
							boolean incorrect=false;
							isDataSetVerified = new ArrayList<Boolean>();
							Map <Integer,Vector<String>>  datasetForQueryMap =  downloadDatasets(assignmentId,questionId, queryId,course_id, conn, filePath, true);
							if(datasetForQueryMap.isEmpty()){
								//Load the default sample data file
								boolean flag=true;
								Vector<String> cmismatch = new Vector<String>();
								try{
									p.deleteAllTempTablesFromTestUser(testConn);
								}catch(Exception e){
									logger.log(Level.INFO,"Temporary Table does not exist",e);										
								}
								try{
									p.createTempTableData(conn,testConn,assignmentId,questionId,course_id);
								}catch(Exception e){
									logger.log(Level.SEVERE,"Temp Table creation error. Table already exists",e);
									throw e;
								}

								Vector<String> killedMutants = checkAgainstOriginalQuery(mutants, "DS_Default", sqlQuery,"NoPath", orderIndependent, cmismatch, testConn);
								logger.log(Level.FINE,"******************");
								logger.log(Level.FINE,"Default dataset Loaded : " + " " + killedMutants.size());
								logger.log(Level.FINE,"******************");
								for(int l=0;l<killedMutants.size();l++){
									if(mutants.containsKey(killedMutants.get(l))){
										flag = false;
										incorrect = true;
									}
								}
								///TODO: FIX: In case of no dataset
								//incorrect=false;
								if(!incorrect){
									//isDataSetVerified.add(true);
								}	
								else{
									resultOfDatasetMatchForEachQuery.put(queryId,false);
								}	

								if(!(resultOfDatasetMatchForEachQuery.containsKey(queryId))){
									resultOfDatasetMatchForEachQuery.put(queryId,true);
								}
							}else{
								for(Integer id : datasetForQueryMap.keySet()){
									Vector<String> datasets = datasetForQueryMap.get(id);
									if(datasets.size() == 0){
										conn.close();
										testConn.close();
										return QueryStatus.NoDataset;
									}
									boolean flag=true;
									for(int i=0;i<datasets.size();i++){

										String dsPath = Configuration.homeDir+"/temp_cvc"+filePath+"/"+datasets.get(i);
										File ds=new File(dsPath);
										//GenerateCVC1 c = new GenerateCVC1();
										String copyFiles[] = ds.list();

										Vector<String> vs = new Vector<String>();
										if(copyFiles!=null){
											for(int m=0;m<copyFiles.length;m++){
												vs.add(copyFiles[m]);		    
											}
										}


										// query output handling
										GenerateCVC1 cvc = new GenerateCVC1();
										Pattern pattern = Pattern.compile("^A([0-9]+)Q[0-9]+");
										Matcher matcher = pattern.matcher(qId);
										int assignId = 1;

										if (matcher.find()) {
											assignId = Integer.parseInt(matcher.group(1));
										} 
										GenerateDataSet preProcess = new GenerateDataSet();

										cvc.setAssignmentId(assignmentId);
										cvc.setQuestionId(questionId);
										cvc.setQueryId(queryId);
										cvc.setCourseId(course_id);
										preProcess.initializeConnectionDetails(cvc);

										TableMap tm = cvc.getTableMap();
										p.populateTestDataForTesting(vs, filePath+"/"+datasets.get(i), tm, testConn, assignId, questionId);


										Vector<String> cmismatch = new Vector<String>();
										logger.log(Level.FINE,datasets.get(i));


										Vector<String> killedMutants = checkAgainstOriginalQuery(mutants, datasets.get(i), sqlQuery, dsPath, orderIndependent, cmismatch, testConn);
										logger.log(Level.FINE,"******************");
										logger.log(Level.FINE,datasets.get(i) + " " + killedMutants.size());
										logger.log(Level.FINE,"******************");
										for(int l=0;l<killedMutants.size();l++){
											if(mutants.containsKey(killedMutants.get(l))){
												flag = false;
												incorrect = true;
											}
										}
										if(!incorrect){
											//isDataSetVerified.add(true);
										}	
										else{

											resultOfDatasetMatchForEachQuery.put(queryId,false);
											logger.log(Level.INFO,"");
										}
										cvc.closeConn();									
									}

									if(!(resultOfDatasetMatchForEachQuery.containsKey(queryId))){
										resultOfDatasetMatchForEachQuery.put(queryId,true);
									}
								}//For each query
							}//else If dataset exists part ends 
						}//While next ResultSet
					}//close resultset try block
				}//close stmnt try blk
				/*******Check for Match all or match One Option Start******/	
				for(int i : resultOfDatasetMatchForEachQuery.keySet()){
					//Get first query's result as default
					if(i == 1){
						isQueryPass = resultOfDatasetMatchForEachQuery.get(i);
					}//For second query, get the query result
					boolean isCorrect = resultOfDatasetMatchForEachQuery.get(i);
					//If instructor selects match All result sets while creating the question,
					//then all query results should hold true for the student query to pass
					if(isMatchAll){
						isQueryPass = isCorrect && isQueryPass;	
					}
					else{
						//This executes if instructor selects Match any result set option
						//If even one query result holds true, the student query is considered as pass
						if(isCorrect){
							isQueryPass = true;
							break;
						}
					}
				}
				if(isQueryPass){
					status = QueryStatus.Correct;
				}
				else{
					status = QueryStatus.Incorrect;
				}		
			}//close connection
		}//close connection
		return status;
	}


	/**
	 * This method tests the student query against instructor query datasets and verifies 
	 * against match all result sets or match any one result set.
	 * This method is triggered during student submission in interactive mode.
	 * 
	 * @param assignmentId
	 * @param questionId
	 * @param query
	 * @param user
	 * @param filePath
	 * @return String - passed if query passes and Failed::datasets failed will be returned.
	 * @throws Exception
	 */
	@Deprecated	
	//public String testAnswerMatchAllOption(int assignmentId,int questionId, String query, String user, String filePath) throws Exception{
	public FailedDataSetValues testAnswerMatchAllOption(int assignmentId,int questionId, String course_id,String query, String user, String filePath) throws Exception{

		int queryId = 1;
		String out="";
		Map <Integer,Boolean> resultOfDatasetMatchForEachQuery  = new HashMap<Integer, Boolean>();
		ArrayList <Boolean> isDataSetVerified  = null;
		boolean isMatchAll = false;
		boolean orderIndependent = false;
		boolean isMatchAllQueryIncorrect = false;
		String matchAllFailedForDS = "";
		String [] defaultDSIdsAssignment = new String[25];
		boolean isQueryPass = false;
		FailedDataSetValues failedDataSets = new FailedDataSetValues();
		int index = 0;
		//First instructor answer will be considered to show failed datasets
		String instrQuery = "";
		//Connection conn = MyConnection.getExistingDatabaseConnection();
		//Connection testConn = MyConnection.getTestDatabaseConnection();
		try(Connection conn = MyConnection.getDatabaseConnection()){
			try(Connection testConn = MyConnection.getTesterConn()){
				GenerateCVC1 cvc = new GenerateCVC1();											
				GenerateDataSet preProcess = new GenerateDataSet();

				cvc.setAssignmentId(assignmentId);
				cvc.setQuestionId(questionId);
				cvc.setQueryId(queryId);
				cvc.setCourseId(course_id);
				preProcess.initializeConnectionDetails(cvc);

				TableMap tm = cvc.getTableMap();
				String getMatchAllOption = "select matchallqueries,orderIndependent from xdata_qinfo where assignment_id = ? and question_id = ? and course_id = ?";
				try(PreparedStatement pst = conn.prepareStatement(getMatchAllOption)){

					pst.setInt(1,assignmentId);
					pst.setInt(2,questionId);
					pst.setString(3,course_id);
					try(ResultSet rset = pst.executeQuery()){
						rset.next();
						isMatchAll = rset.getBoolean("matchallqueries");
						orderIndependent = rset.getBoolean("orderIndependent");
					}
				}
				//Get default dataset Ids from assignment table
				String getDefaultDataSets = "select defaultDSetId from xdata_assignment where assignment_id = ? and course_id=?";
				try(PreparedStatement pst =conn.prepareStatement(getDefaultDataSets) ){
					pst.setInt(1,assignmentId);
					pst.setString(2,course_id);
					try(ResultSet rset = pst.executeQuery()){
						rset.next();
						String dsIds = rset.getString("defaultDSetId");
						Gson gson = new Gson();
						Type listType = new TypeToken<String[]>() {}.getType();
						defaultDSIdsAssignment = new Gson().fromJson(dsIds, listType);

					}
				}
				String [] defaultDSIdsPerQuestion=new String[25];
				String getDefaultDataSetsForQuestion = "select default_sampledataid from xdata_qinfo where assignment_id = ? and course_id=? and question_id=?";
				try(PreparedStatement pst =conn.prepareStatement(getDefaultDataSetsForQuestion) ){
					pst.setInt(1,assignmentId);
					pst.setString(2,course_id);
					pst.setInt(3, questionId);
					try(ResultSet rset = pst.executeQuery()){
						rset.next();
						String dsIds = rset.getString("default_sampledataid");
						Gson gson = new Gson();
						Type listType = new TypeToken<String[]>() {}.getType();
						defaultDSIdsPerQuestion = new Gson().fromJson(dsIds, listType);

					}
				}
				PopulateTestDataGrading p = new PopulateTestDataGrading();

				String qry = "select * from xdata_instructor_query where assignment_id = ? and question_id = ? and course_id = ?";
				try(PreparedStatement pstmt = conn.prepareStatement(qry)){
					pstmt.setInt(1,assignmentId);
					pstmt.setInt(2,questionId);
					pstmt.setString(3,course_id);
					try(ResultSet rs = pstmt.executeQuery()){
						while(rs.next()){

							//If Instructor has chosen match All queries option, get all datasets
							// match student query against all and return true only if all matches
							String sqlQuery=rs.getString("sql");
							if(index == 0){
								instrQuery = sqlQuery;
							}
							queryId = rs.getInt("query_id");							
							String qId = "A"+assignmentId +"Q"+questionId+"S"+queryId;
							query=checkForViews(query,user);
							HashMap<String,String> mutants = new HashMap<String,String>();
							mutants.put(qId, query);
							boolean incorrect=false;
							isDataSetVerified = new ArrayList<Boolean>();
							index++;

							//For the instructor given data sets during assignment creation
							// test student and instructor query options 

							// First get sample data sets for question, if it is not there, then get
							// it from assignment table
							// If both are not there, don't set anythng - proceed with other code

							if(defaultDSIdsPerQuestion != null){
								try{
									p.deleteAllTempTablesFromTestUser(testConn);
								}catch(Exception e){
									logger.log(Level.INFO,"Temporary Table does not exist",e);

								}
								try{
									p.createTempTableData(conn,testConn,assignmentId,questionId,course_id);
								}catch(Exception e){
									logger.log(Level.SEVERE,"Temp Table creation error. Table already exists",e);
									throw e;
								}
								for(int dId= 0; dId < defaultDSIdsPerQuestion.length;dId++){
									Vector<String> cmismatch = new Vector<String>();
									logger.log(Level.INFO,"******************");
									logger.log(Level.INFO,"Default dataset "+defaultDSIdsPerQuestion[dId]+" Loaded : ");
									logger.log(Level.INFO,"******************");

									String dsName = p.createTempTableWithDefaultData(conn,testConn,assignmentId,questionId,course_id,defaultDSIdsPerQuestion[dId].toString());
									Vector<String> killedMutants = checkAgainstOriginalQuery(mutants,defaultDSIdsPerQuestion[dId].toString(), sqlQuery,"NoPath", orderIndependent, cmismatch, testConn);

									for(int l=0;l<killedMutants.size();l++){
										if(mutants.containsKey(killedMutants.get(l))){
											logger.log(Level.INFO,"false" +killedMutants.get(l));
											incorrect = true;
											//Get failed datasets
											//matchAllFailedForDS = datasets.get(i);	
											failedDataSets.setStatus("Failed");
											failedDataSets.getDataSetIdList().add(defaultDSIdsPerQuestion[dId].toString());
											failedDataSets.setInstrQuery(instrQuery);
										}
									}

									if(!incorrect){
										//isDataSetVerified.add(true);
									}	
									else{											
										resultOfDatasetMatchForEachQuery.put(queryId,false);
										logger.log(Level.INFO,"");
									}	

									if(!(resultOfDatasetMatchForEachQuery.containsKey(queryId))){
										resultOfDatasetMatchForEachQuery.put(queryId,true);
									}
									p.deleteAllTablesFromTestUser(testConn);

								}
								p.deleteAllTempTablesFromTestUser(testConn);
							}
							if((defaultDSIdsPerQuestion == null || (defaultDSIdsPerQuestion != null && defaultDSIdsPerQuestion.length == 0))
									&&  defaultDSIdsAssignment != null){
								p.createTempTableData(conn,testConn,assignmentId,questionId,course_id);
								for(int dId= 0; dId < defaultDSIdsAssignment.length;dId++){
									Vector<String> cmismatch = new Vector<String>();
									logger.log(Level.INFO,"******************");
									logger.log(Level.INFO,"Default dataset "+defaultDSIdsAssignment[dId]+" Loaded : ");
									logger.log(Level.INFO,"******************");
									String dsName = p.createTempTableWithDefaultData(conn,testConn,assignmentId,questionId,course_id,defaultDSIdsAssignment[dId].toString());
									Vector<String> killedMutants = checkAgainstOriginalQuery(mutants,defaultDSIdsAssignment[dId].toString(), sqlQuery,"NoPath", orderIndependent, cmismatch, testConn);

									for(int l=0;l<killedMutants.size();l++){
										if(mutants.containsKey(killedMutants.get(l))){
											logger.log(Level.INFO,"false" +killedMutants.get(l));
											incorrect = true;
											//Get failed datasets
											//matchAllFailedForDS = datasets.get(i);	
											failedDataSets.setStatus("Failed");
											failedDataSets.getDataSetIdList().add(defaultDSIdsAssignment[dId].toString());
											failedDataSets.setInstrQuery(instrQuery);
										}
									}

									if(!incorrect){
										//isDataSetVerified.add(true);
									}	
									else{											
										resultOfDatasetMatchForEachQuery.put(queryId,false);
										logger.log(Level.INFO,"");
									}	

									if(!(resultOfDatasetMatchForEachQuery.containsKey(queryId))){
										resultOfDatasetMatchForEachQuery.put(queryId,true);
									}

									p.deleteAllTablesFromTestUser(testConn);
								}
								p.deleteAllTempTablesFromTestUser(testConn);
							}
							Map <Integer,Vector<String>>  datasetForQueryMap =  downloadDatasets(assignmentId,questionId, queryId,course_id, conn, filePath, false);
							//For given query, fetch datasets, if data sets are empty - load default sample data 1 and run the query
							if(datasetForQueryMap.isEmpty()  && defaultDSIdsAssignment == null && defaultDSIdsPerQuestion == null){

								//Load the default sample data file
								boolean flag=true;
								Vector<String> cmismatch = new Vector<String>();
								try{
									p.deleteAllTempTablesFromTestUser(testConn);
								}catch(Exception e){
									logger.log(Level.INFO,"Temporary Table does not exist",e);

								}
								try{
									p.createTempTableData(conn,testConn,assignmentId,questionId,course_id);
								}catch(Exception e){
									logger.log(Level.SEVERE,"Temp Table creation error. Table already exists",e);
									throw e;
								}
								Vector<String> killedMutants = checkAgainstOriginalQuery(mutants, "DS_Default", sqlQuery,"NoPath", orderIndependent, cmismatch, testConn);
								logger.log(Level.INFO,"******************");
								logger.log(Level.INFO,"Default dataset Loaded : " + " " + killedMutants.size());
								logger.log(Level.INFO,"******************");
								for(int l=0;l<killedMutants.size();l++){
									if(mutants.containsKey(killedMutants.get(l))){
										logger.log(Level.INFO,"false" +killedMutants.get(l));
										incorrect = true;
										//Get failed datasets
										//matchAllFailedForDS = datasets.get(i);	
										failedDataSets.setStatus("Failed");
										failedDataSets.getDataSetIdList().add("DS_Default");
										failedDataSets.setInstrQuery(instrQuery);
									}
								}

								if(!incorrect){
									//isDataSetVerified.add(true);
								}	
								else{

									resultOfDatasetMatchForEachQuery.put(queryId,false);
									logger.log(Level.INFO,"");
								}	

								if(!(resultOfDatasetMatchForEachQuery.containsKey(queryId))){
									resultOfDatasetMatchForEachQuery.put(queryId,true);
								}
							}else{
								for(Integer id : datasetForQueryMap.keySet()){

									Vector<String> datasets = datasetForQueryMap.get(id);
									//For each Xdata generated datasets, load them in temp table and test student and instructor query 
									for(int i = 0; i < datasets.size(); i++){
										boolean flag = true;

										//load the contents of DS
										String dsPath = Configuration.homeDir + "/temp_cvc" + filePath + "/" + datasets.get(i);
										File ds=new File(dsPath);
										//GenerateCVC1 c = new GenerateCVC1();
										String copyFiles[] = ds.list();

										Vector<String> vs = new Vector<String>();
										for(int m=0;m<copyFiles.length;m++){
											vs.add(copyFiles[m]);
										}

										Pattern pattern = Pattern.compile("^A([0-9]+)Q[0-9]+");
										Matcher matcher = pattern.matcher(qId);
										int assignId = 1;

										if (matcher.find()) {
											assignId = Integer.parseInt(matcher.group(1));
										}


										p.populateTestDataForTesting(vs, filePath + "/" + datasets.get(i), tm, testConn, assignId, questionId);
										Vector<String> cmismatch=new Vector<String>();
										Vector<String> killedMutants = checkAgainstOriginalQuery(mutants, datasets.get(i), sqlQuery, dsPath, orderIndependent, cmismatch, testConn);

										for(int l=0;l<killedMutants.size();l++){
											if(mutants.containsKey(killedMutants.get(l))){
												logger.log(Level.INFO,"false" +killedMutants.get(l));
												incorrect = true;
												//Get failed datasets
												//matchAllFailedForDS = datasets.get(i);	
												failedDataSets.setStatus("Failed");
												failedDataSets.getDataSetIdList().add(datasets.get(i));
												failedDataSets.setInstrQuery(instrQuery);
											}
										}

										if(!incorrect){
											//isDataSetVerified.add(true);
										}	
										else{
											//isDataSetVerified.add(false);
											//If query fails, add the status of query to false
											resultOfDatasetMatchForEachQuery.put(queryId,false);
											//  out+= matchAllFailedForDS+"::Failed:::";
											logger.log(Level.INFO,"");			
										}

										cvc.closeConn();
									}

								}
								//resultOfDatasetMatchForEachQuery.put(queryId,isDataSetVerified);
								//If there are no items in resultOfDatasetMatchForEachQuery for given queryId
								//then the query is "Correct and passed" - so set TRUE
								if(!(resultOfDatasetMatchForEachQuery.containsKey(queryId))){
									resultOfDatasetMatchForEachQuery.put(queryId,true);
								}
							}//Else if data exists part ends



						}//While next ResultSet

					}//close resultset try block
					//  *******Check for Match all or match One Option Start //
					int query_id = 0;

					for(int i : resultOfDatasetMatchForEachQuery.keySet()){
						//Get first query's result as default
						if(i == 1){
							isQueryPass = resultOfDatasetMatchForEachQuery.get(i);
						}//For second query, get the query result
						boolean isCorrect = resultOfDatasetMatchForEachQuery.get(i);
						//If instructor selects match All result sets while creating the question,
						//then all query results should hold true for the student query to pass
						if(isMatchAll){
							isQueryPass = isCorrect && isQueryPass;	
						}
						else{
							//This excutes if instructor selects Match any result set option
							//If even one query result holds true, the student query is considered as pass
							if(isCorrect){
								isQueryPass = true;
								break;
							}
						}			
					} 
					if(isQueryPass){
						logger.log(Level.INFO,"Question passed the datasets expected");
						String qryUpdate = "update xdata_student_queries set verifiedcorrect = true where assignment_id ='"+assignmentId+"' and question_id = '"+questionId+"' and rollnum = '"+user+"' and course_id='"+course_id+"'";
						PreparedStatement pstmt3 = conn.prepareStatement(qryUpdate);
						pstmt3.executeUpdate();
						pstmt3.close();
						out+="Passed:::";
					}
					else{
						String qryUpdate = "update xdata_student_queries set verifiedcorrect = false where assignment_id ='"+ assignmentId+"' and question_id = '"+questionId+"' and rollnum = '"+user+"' and course_id='"+course_id+"'";
						PreparedStatement pstmt2 = conn.prepareStatement(qryUpdate);
						//pstmt2.setString(1,out.trim());
						pstmt2.executeUpdate();

					}
				}
			}//close connection try block
		}//close connection try block
		return failedDataSets;


	}

	/**
	 * This method gets the default Datasets at assignment level
	 * 
	 * @param assignmentId
	 * @param course_id
	 * @param conn
	 * @return
	 * @throws Exception
	 */
	public String [] getDefaultDSForAssignment(int assignmentId,String course_id,Connection conn) throws Exception{
		String [] defaultDSIdsAssignment = new String[25];
		//Get default dataset Ids from assignment table
		String getDefaultDataSets = "select defaultDSetId from xdata_assignment where assignment_id = ? and course_id=?";
		try(PreparedStatement pst =conn.prepareStatement(getDefaultDataSets) ){
			pst.setInt(1,assignmentId);
			pst.setString(2,course_id);
			try(ResultSet rset = pst.executeQuery()){
				rset.next();
				String dsIds = rset.getString("defaultDSetId");
				//MODIFIED by ANURAG
				//dsIds = "[\""+dsIds+"\"]";
				Gson gson = new Gson();
				Type listType = new TypeToken<String[]>() {}.getType();
				defaultDSIdsAssignment = new Gson().fromJson(dsIds, listType);

			}
		}catch(Exception e){
			logger.log(Level.SEVERE,"Exception caught here: "+e.getMessage(),e);
			e.printStackTrace();
			throw e;
		}
		return defaultDSIdsAssignment;

	}

	/**
	 * This method gets the default datasets that are at each instructor question level
	 * 
	 * @param assignmentId
	 * @param questionId
	 * @param course_id
	 * @param conn
	 * @return
	 * @throws Exception
	 */
	public String[] getDefaultDSForQuestion(int assignmentId,int questionId,String course_id,Connection conn) throws Exception{
		String[] defaultDSIdsPerQuestion = new String[25];
		String getDefaultDataSetsForQuestion = "select default_sampledataid from xdata_qinfo where assignment_id = ? and course_id=? and question_id=?";
		try(PreparedStatement pst =conn.prepareStatement(getDefaultDataSetsForQuestion) ){
			pst.setInt(1,assignmentId);
			pst.setString(2,course_id);
			pst.setInt(3, questionId);
			try(ResultSet rset = pst.executeQuery()){
				rset.next();
				String dsIds = rset.getString("default_sampledataid");

				Gson gson = new Gson();
				Type listType = new TypeToken<String[]>() {}.getType();
				defaultDSIdsPerQuestion = new Gson().fromJson(dsIds, listType);

			}
		}
		catch(Exception e){
			logger.log(Level.SEVERE,"Exception caught here: "+e.getMessage(),e);
			e.printStackTrace();
			throw e;
		}
		return defaultDSIdsPerQuestion;
	}

	/**
	 * This method evaluates all the student queries against each instructor query and calculates the marks for each student. 
	 * For each instructor query, load datasets, run each student query on the loaded dataset. Store results for failed students, calculate partial marks.
	 * For passed student query, assign marks based on the instructor query that it has passed. 
	 * 
	 *  Add them to final result list and return.
	 * 
	 * @param assignmentId
	 * @param questionId
	 * @param course_id
	 * @param studentRollNums - student roll numbers.
	 * @param studentQueries - student's answer corresponding to roll number. If student has not answered, it will have null as value.
	 * @param filePath
	 * @param studRole
	 * @return
	 * @throws Exception
	 */
	public ArrayList<FailedDataSetValues> newTestAnswer(int assignmentId,int questionId, String course_id,
			ArrayList<String> studentQueries,ArrayList<String> studentRollNums, String filePath, String studRole) throws Exception{

		//These holds instructor_query_id as Key and arraylist of student Id's who have failed / passed that query
		Map<Integer,ArrayList<String>> instrQueryVsFailedStudentList = new HashMap<Integer, ArrayList<String>>();
		Map<Integer,ArrayList<String>> instrQueryVsPassedStudentList = new HashMap<Integer, ArrayList<String>>();
		//List that holds final result of all students
		ArrayList<FailedDataSetValues> finalFailedDsList = new ArrayList<FailedDataSetValues>();
		//Holds Roll number of students who have failed 
		ArrayList<String> failedStudentRollNumList = new ArrayList<String>();
		ArrayList<String> passedStudentRollNumList = new ArrayList<String>();
		String [] defaultDSIdsAssignment=new String[25];
		String [] defaultDSIdsPerQuestion=new String[25];
		int queryId = 1;
		int maxMarks = 0;
		int reduceLateSubmissionMarks = 0;
		boolean isMatchAll = false;
		boolean orderIndependent = false;
		String instrQuery = null;
		Map <Integer,Integer> maxMarksPerInstrQueryMap = new HashMap<Integer,Integer>();
		try(Connection conn = MyConnection.getDatabaseConnection()){
			try(Connection testConn = MyConnection.getTesterConn()){
				String getMatchAllOption = "select matchallqueries,orderIndependent,latesubmissionmarks from xdata_qinfo where assignment_id = ? and question_id = ? and course_id=?";
				try(PreparedStatement pst = conn.prepareStatement(getMatchAllOption)){
					pst.setInt(1,assignmentId);
					pst.setInt(2,questionId);
					pst.setString(3,course_id);
					try(ResultSet rset = pst.executeQuery()){
						rset.next(); 
						isMatchAll = rset.getBoolean("matchallqueries");
						orderIndependent = rset.getBoolean("orderIndependent");
						reduceLateSubmissionMarks = rset.getInt("latesubmissionmarks");
					}///close resultset try block
				}//close stmt try blck
				String qry = "select * from xdata_instructor_query a inner join xdata_qinfo b on a.assignment_id = b.assignment_id and " +
						"a.question_id = b.question_id  where a.assignment_id = ?" +
						" and a.question_id = ? and a.course_id=?";
				defaultDSIdsAssignment = this.getDefaultDSForAssignment(assignmentId,course_id,conn);
				defaultDSIdsPerQuestion = this.getDefaultDSForQuestion(assignmentId,questionId,course_id,conn);			
				try(PreparedStatement pstmt = conn.prepareStatement(qry)){
					pstmt.setInt(1,assignmentId);
					pstmt.setInt(2,questionId); 
					pstmt.setString(3,course_id);
					try(ResultSet rs = pstmt.executeQuery()){
						PopulateTestDataGrading p = new PopulateTestDataGrading();
						GenerateCVC1 cvc = new GenerateCVC1();											
						GenerateDataSet preProcess = new GenerateDataSet();
						cvc.setAssignmentId(assignmentId);
						cvc.setQuestionId(questionId);
						cvc.setQueryId(queryId);
						cvc.setCourseId(course_id);
						preProcess.initializeConnectionDetails(cvc);
						TableMap tm = cvc.getTableMap();
						cvc.closeConn();	
						try{
							// Check for views in student query
							int counter = 0;
							int tot_time=0;
							for(int i = 0 ; i < studentRollNums.size(); i++){
								String rollnum = studentRollNums.get(i);
								String query = studentQueries.get(i);
								if(query!= null && !query.isEmpty()){
									query=checkForViews(query,rollnum);
								}//set updated query to student query
								studentQueries.set(i,query);
								
								// For the experiment
//								long startTime = System.currentTimeMillis();
//								float studentMarks = tempgetMarkDetails(conn,rollnum,assignmentId,questionId,course_id,query,rollnum,false,100,0);
//								long endTime   = System.currentTimeMillis();
//								long totalTime = endTime - startTime;
//								if(studentMarks >0 && studentMarks<100)
//								{
//									System.out.println("Student No > "+counter+" Roll No > "+rollnum+" Marks> "+ studentMarks + " Time> "+ totalTime);
//									counter++;
//									tot_time+=totalTime;
//								}
//								if(counter>=10) 
//								{
//									break;
//								}
							}
//							System.out.println("Total time> "+tot_time + " Avg time> " + (float)tot_time/counter);
						}
						catch(Exception e)
						{
							e.printStackTrace();
							
						}
						
						//For each instructor answer loop to compare datasets
						while(rs.next()){

							boolean incorrect=false;
							Map <String,Vector<String>> resultPerStudentPerInstructorQuery =new HashMap<String,Vector<String>>();
							String sqlQuery=rs.getString("sql");
							queryId = rs.getInt("query_id");
							maxMarks = rs.getInt("marks");
							//FIXME we change the filepath to hold the query_id 
							filePath = filePath.substring(0,filePath.length()-1)+queryId;
							String instrQueryId = "A"+assignmentId +"Q"+questionId+"S"+queryId;
							instrQuery = sqlQuery;
							failedStudentRollNumList = new ArrayList<String>();
							passedStudentRollNumList = new ArrayList<String>();
							maxMarksPerInstrQueryMap.put(queryId,maxMarks);
							Vector <String> resultOnDataSet = new Vector<String>();
							Vector<String> dataSetIdListFailed = new Vector<String>(); 
							//Get default dataset for question - run all stud queries on that.

							try{								
								if(defaultDSIdsPerQuestion != null){
									for(int dId= 0; dId < defaultDSIdsPerQuestion.length;dId++){
										Vector<String> cmismatch = new Vector<String>();
										
										logger.log(Level.FINE,"Default dataset "+defaultDSIdsPerQuestion[dId]+" Loaded : ");
										

										String dsName = p.createTempTableWithDefaultData(conn,testConn,assignmentId,questionId,course_id, defaultDSIdsPerQuestion[dId].toString());
										ArrayList<FailedDataSetValues> fdvFailedList = newCheckAgainstOriginalQuery
												(studentRollNums, studentQueries, instrQueryId, defaultDSIdsPerQuestion[dId].toString(), instrQuery, 
														"NoPath", orderIndependent, cmismatch, testConn, resultOnDataSet, assignmentId,questionId,course_id,conn,tm,null);

										//get the fdvList - get failed studentId List and if it is already there in the finalFailedStudentId list, update the datasets it has failed and result on dataset
										//If the student ID is not there already, just add it to the failedStudentRollNumList and finalFailedDSList									
										for(int fl = 0 ; fl< fdvFailedList.size() ; fl++){													 
											String stdRoll = fdvFailedList.get(fl).getStudentRollNo();
											if(failedStudentRollNumList.contains(stdRoll)){
												processFailedDSList(fdvFailedList.get(fl), finalFailedDsList);
											}else{
												failedStudentRollNumList.add(stdRoll);
												fdvFailedList.get(fl).getDataSetIdList().add(fdvFailedList.get(fl).getDataSetId() );
												finalFailedDsList.add(fdvFailedList.get(fl));
											}
										}
									}
								}
								p.deleteAllTempTablesFromTestUser(testConn);								
							}catch(Exception e){
								logger.log(Level.SEVERE,"Exception caught here: "+e.getMessage(),e);
								e.printStackTrace();
							}

							//Get default dataset for assignment - run all student queries on that
							try{
								if((defaultDSIdsPerQuestion == null || (defaultDSIdsPerQuestion != null && defaultDSIdsPerQuestion.length == 0))
										&&  defaultDSIdsAssignment != null){

									for(int dId= 0; dId < defaultDSIdsAssignment.length;dId++){
										Vector<String> cmismatch = new Vector<String>();
										
										logger.log(Level.FINE,"Default dataset "+defaultDSIdsAssignment[dId]+" Loaded : ");
										

										String dsName = p.createTempTableWithDefaultData(conn,testConn,assignmentId,questionId,course_id,
												defaultDSIdsAssignment[dId].toString());
										ArrayList<FailedDataSetValues> fdvFailedList = newCheckAgainstOriginalQuery
												(studentRollNums, studentQueries, instrQueryId, defaultDSIdsAssignment[dId].toString(), instrQuery, 
														"NoPath", orderIndependent, cmismatch, testConn, resultOnDataSet, assignmentId,questionId,course_id,conn,tm,null);
										//get the fdvList - get failed studentId List and if it is already there in the finalFailedStudentId list, update the datasets it has failed and result on dataset
										//If the student ID is not there already, just add it to the failedStudentRollNumList and finalFailedDSList								
										for(int fl = 0 ; fl< fdvFailedList.size() ; fl++){
											String stdRoll = fdvFailedList.get(fl).getStudentRollNo();
											if(failedStudentRollNumList.contains(stdRoll)){
												processFailedDSList(fdvFailedList.get(fl), finalFailedDsList);
											}else{
												failedStudentRollNumList.add(stdRoll);
												fdvFailedList.get(fl).getDataSetIdList().add(fdvFailedList.get(fl).getDataSetId());
												finalFailedDsList.add(fdvFailedList.get(fl));
											}
										}
									}
									p.deleteAllTempTablesFromTestUser(testConn);
								}	
							}catch(Exception e){
								logger.log(Level.SEVERE,"Exception caught here: "+e.getMessage(),e);
								e.printStackTrace();
							}
							//Get generated datasets and loop on datasets.
							Map <Integer,Vector<String>>  datasetForQueryMap =	downloadDatasets(assignmentId,questionId,queryId,course_id,conn,filePath, false);
							//Even if no default data sets are there and no datasets are available for the query, , check against the sample Data file that the assignment uses.

							for(Integer id : datasetForQueryMap.keySet()){
								Vector<String> datasets = datasetForQueryMap.get(id);
								for(int i=0;i<datasets.size();i++){
									boolean flag=true;
									//load the contents of DS
									//String dsPath = Configuration.homeDir+"/temp_cvc"+filePath+"/"+datasets.get(i);
									String dsPath = Configuration.homeDir+"/temp_smt"+filePath+"/"+datasets.get(i); // added by rambabu 
									File ds=new File(dsPath);
									String copyFiles[] = ds.list();
									Vector<String> vs = new Vector<String>();
									for(int m=0;m<copyFiles.length;m++){
										vs.add(copyFiles[m]);
									}				 		
									// query output handling
									/*Pattern pattern = Pattern.compile("^A([0-9]+)Q[0-9]+");
									Matcher matcher = pattern.matcher(instrQueryId);
									int assignId = 1;
									if (matcher.find()) {
										assignId = Integer.parseInt(matcher.group(1));
									}*/
									//populate datasets for testing
									p.populateTestDataForTesting(vs, filePath+"/"+datasets.get(i), tm, testConn, assignmentId, questionId);
									Vector<String> cmismatch=new Vector<String>();
									//Check against the query	
									ArrayList<FailedDataSetValues> fdvFailedList = newCheckAgainstOriginalQuery(studentRollNums, studentQueries, instrQueryId, datasets.get(i), instrQuery, filePath, 
											orderIndependent, cmismatch, testConn, resultOnDataSet, assignmentId,questionId,course_id,conn,tm,vs);
									//get the fdvList - get failed studentId List and if it is already there in the finalFailedStudentId list, update the datasets it has failed and result on dataset
									//If the student ID is not there already, just add it to the failedStudentRollNumList and finalFailedDSList									
									for(int fl = 0 ; fl< fdvFailedList.size() ; fl++){
										String stdRoll = fdvFailedList.get(fl).getStudentRollNo();
										if(failedStudentRollNumList.contains(stdRoll)){
											processFailedDSList(fdvFailedList.get(fl), finalFailedDsList);
										}else{
											failedStudentRollNumList.add(stdRoll);	
											fdvFailedList.get(fl).getDataSetIdList().add(fdvFailedList.get(fl).getDataSetId());
											finalFailedDsList.add(fdvFailedList.get(fl));
										}
									}
								}
							}
							//Put failedStudentsRollNumList against the instructor_query_id
							instrQueryVsFailedStudentList.put(queryId,failedStudentRollNumList);	
							//Get passed student roll number list for that query
							for(String passedRollNum : studentRollNums){
								if(! failedStudentRollNumList.contains(passedRollNum)){
									//Add the roll num to passed student roll number
									passedStudentRollNumList.add(passedRollNum);
								}
							}
							instrQueryVsPassedStudentList.put(queryId,passedStudentRollNumList);
						}//End of Instructor queries loop 	
					}
				} 
				int cnt = 0 ;
				ArrayList<String> failedRollNum = new  ArrayList<String>();
				if(isMatchAll){
					//then update all students in FailedDsList as fail and calculate partial marks - update xdata_Student_queries table with updated data
					Iterator<Integer> it = instrQueryVsFailedStudentList.keySet().iterator();
					//Store the students who have failed against all the instructor queries. Even if student queries fails against one instructor query, one DataSet,
					//Query Fails as it is mathc All option
					ArrayList<String> allFailedIds = new  ArrayList<String>();
					while(it.hasNext()){
						int qry_id = (Integer)it.next();
						ArrayList<String> failedIds =  instrQueryVsFailedStudentList.get(qry_id);
						for(String fsId : failedIds){
							//Get partial marks for the students who have failed
							for(FailedDataSetValues fdFailed : finalFailedDsList){
								if(fdFailed.getStudentRollNo().equals(fsId)){
									if(!allFailedIds.contains(fsId)){
										allFailedIds.add(fsId);
									}
									fdFailed = getMarkDetails(conn, fdFailed, false, studRole, assignmentId, questionId, course_id, fdFailed.getStudentQueryString(), fsId, false,maxMarks, 
											reduceLateSubmissionMarks);
									//finalFailedDsList.add(fdFailed);
								}
							}	
						}
					}
					//All other students with roll nums other than the ones in the allFailedIds will have status as Passed
					//Passed students will have to send the maxmarks for the query in which they have passed. - If one instructor question has 2-3 answers each with different max marks, then this applies.
					Iterator<Integer> it1 = instrQueryVsPassedStudentList.keySet().iterator();				
					while(it1.hasNext()){
						int qry_id = (Integer)it1.next();
						ArrayList<String> passedIds = instrQueryVsPassedStudentList.get(qry_id);
						for(String psId : passedIds){
							if(! allFailedIds.contains(psId)){
								FailedDataSetValues passedDataSetObject = new FailedDataSetValues();
								passedDataSetObject.setStatus("Passed");
								//No failed datasets
								passedDataSetObject.setDataSetIdList(null);
								passedDataSetObject.setInstrQuery(instrQuery);								
								passedDataSetObject = getMarkDetails(conn, passedDataSetObject, true, studRole, assignmentId, questionId, course_id, instrQuery,psId, false,maxMarksPerInstrQueryMap.get(qry_id), 
										reduceLateSubmissionMarks);

								for(FailedDataSetValues passedDS : finalFailedDsList){
									if(passedDataSetObject.getStudentRollNo().equalsIgnoreCase(passedDS.getStudentRollNo())){
										finalFailedDsList.add(passedDataSetObject);
									}
								}
							}
						}
					}
				}
				else{ // Match Any 
					//If the lists have common student Id, mark those students as FAILED, else mark them as Passed as they have passed atleast one answer					
					Iterator it = instrQueryVsFailedStudentList.keySet().iterator();				
					while(it.hasNext()){

						if(cnt ==0){
							int qry_id = (Integer)it.next(); 
							failedRollNum = instrQueryVsFailedStudentList.get(qry_id);
						}
						//If there are more than one instructor query, find the students who have failed in all - intersection of all failed Students list
						if(it.hasNext()){
							ArrayList<String> failedRollNumNxt = instrQueryVsFailedStudentList.get(it.next());//2nd elmnt
							//RetainAll will give the elements that are common in both lists
							failedRollNum.retainAll(failedRollNumNxt);
							cnt++;
						}					
					}				
					//List failedRollnum will have the Student Id's that have failed all instuctor_queries  - calculate marks for the same
					for(String fdRoll: failedRollNum){
						for(FailedDataSetValues fdvs : finalFailedDsList){
							if(fdvs.getStudentRollNo().equalsIgnoreCase(fdRoll)){							    
								fdvs = getMarkDetails(conn, fdvs, false, studRole, assignmentId, questionId, course_id, fdvs.getStudentQueryString(), fdvs.getStudentRollNo(), false,maxMarks, 
										reduceLateSubmissionMarks);
								//finalFailedDsList.add(fdvs);
							}
						}
					}				
					//Loop through all passed students per query and set FailedDataSets Object - calculate marks - update the DB
					Iterator it1 = instrQueryVsPassedStudentList.keySet().iterator();				
					while(it1.hasNext()){
						int qry_id = (Integer)it1.next();
						ArrayList<String> passedIds = instrQueryVsPassedStudentList.get(qry_id);
						for(String psId : passedIds){

							FailedDataSetValues passedDataSetObject = new FailedDataSetValues();
							passedDataSetObject.setStatus("Passed");
							//No failed datasets
							passedDataSetObject.setDataSetIdList(null);
							passedDataSetObject.setInstrQuery(instrQuery);								
							passedDataSetObject = getMarkDetails(conn, passedDataSetObject, true, studRole, assignmentId, questionId, course_id, instrQuery,psId, false,maxMarksPerInstrQueryMap.get(qry_id), 
									reduceLateSubmissionMarks);

							for(FailedDataSetValues passedDS : finalFailedDsList){
								if(passedDataSetObject.getStudentRollNo().equalsIgnoreCase(passedDS.getStudentRollNo())){
									finalFailedDsList.add(passedDataSetObject);
								}
							}				
						}
					}
				}//Match any else stmnt ends
			}
		}
		return finalFailedDsList;
	}

	/**
	 * This method adds the failed Student Object details to existing FailedDataSetValue Object in the final list. 
	 * It appends the new datasetid in which the student has failed, and the query output on that dataset.
	 * 
	 * @param fdvFailed - FailedDataSet which holds student roll number which is already present in final FailedList
	 * @param finalFailedDsList
	 * @return
	 */
	public static ArrayList<FailedDataSetValues> processFailedDSList(FailedDataSetValues fdvFailed, ArrayList<FailedDataSetValues> finalFailedDsList) {

		for(int ffd = 0 ; ffd < finalFailedDsList.size(); ffd++){

			FailedDataSetValues finalFailedDs = finalFailedDsList.get(ffd);

			if(finalFailedDs.getStudentRollNo().equalsIgnoreCase(fdvFailed.getStudentRollNo())){

				finalFailedDs.getDataSetIdList().add(fdvFailed.getDataSetId());

				Map<String, Map<String, ArrayList<String>>> ffdStudentMap = finalFailedDs.getStudentQueryOutput();
				ffdStudentMap.putAll(fdvFailed.getStudentQueryOutput());

				Map<String, Map<String, ArrayList<String>>> ffdInstrMap = finalFailedDs.getInstrQueryOutput();
				ffdInstrMap.putAll(fdvFailed.getInstrQueryOutput());

				finalFailedDs.getDsValueMap().putAll(fdvFailed.getDsValueMap());

			}
		}
		return finalFailedDsList;
	}

	/**
	 * This method return the <i>student query's</i> output for the loaded data set
	 * Used to get results of student query on the failed data set for
	 * displaying failed test cases
	 * 
	 * @param testCon
	 * @param dataSetId
	 * @param OriginalQry
	 * @param failedList
	 * @param failedStudDataMap
	 * @return
	 * @throws SQLException
	 */
	public FailedDataSetValues  getStudentOutput (Connection testCon, String dataSetId, String OriginalQry,
			FailedDataSetValues failedDs,
			Map<String, Map<String, ArrayList<String>>> failedStudDataMap, boolean isDefaultDS) {

		Map<String, ArrayList<String>> failedColMap = new HashMap<String, ArrayList<String>>();

		try {
			// execute student query on the failed DS and get the output
			try (PreparedStatement pp = testCon.prepareStatement(OriginalQry)) {
				try (ResultSet rr = pp.executeQuery()) {
					ResultSetMetaData metadata = rr.getMetaData();
					int no_of_columns = metadata.getColumnCount();
					String result = "";
					String columnName = "";
					ArrayList<String> existingColNames = new ArrayList<String>();  
					int index = 1;
					for(int cl=1;cl<=no_of_columns;cl++)
					{
						existingColNames.add(metadata.getColumnName(cl));
					}
					for (int cl = 1; cl <= no_of_columns; cl++) {
						ArrayList<String> values = new ArrayList<String>();
						FailedColumnValues failedColumns = new FailedColumnValues();
						columnName = metadata.getColumnName(cl);					
						existingColNames.remove(metadata.getColumnName(cl));					
						//If colName exists after removing, it is a duplicate column - suffix it with index.
						if(existingColNames.contains(metadata.getColumnName(cl))){
							columnName = metadata.getColumnName(cl)+index;
							index ++;
							existingColNames.add(metadata.getColumnName(cl));							
						}else{
							columnName = metadata.getColumnName(cl);							
						}

						try (ResultSet rr1 = pp.executeQuery()) {
							metadata = rr1.getMetaData();
							while (rr1.next()) {
								int type = metadata.getColumnType(cl);
								values.add(rr1.getString(cl));
							}												
							failedColumns.setColumnName(columnName);
							failedColumns.setValues(values);						
							//failedList.add(failedColumns);
							failedColMap.put(columnName, values);
						}
					}
				} // try block to close rr resultset
			} // try block for pp statement ends
		} catch (SQLException e) {
			logger.log(Level.SEVERE,
					"Student test case output generation fails because of syntax error :" + e.getMessage());		
		}
		failedStudDataMap.put(dataSetId, failedColMap);
		failedDs.setStudentQueryOutput(failedStudDataMap);
		return failedDs;
	}

	/**
	 * This method return the <i>instructor query's</i> output for the loaded dataset
	 * Used to get results of instructor query on the failed dataset for
	 * displaying failed test cases
	 * 
	 * @param testCon
	 * @param dataSetId
	 * @param OriginalQry
	 * @param failedList
	 * @param failedStudDataMap
	 * @return
	 * @throws SQLException
	 */
	public FailedDataSetValues  getInstructorOutput (Connection testCon, String dataSetId, String OriginalQry,
			FailedDataSetValues failedDs,
			Map<String, Map<String, ArrayList<String>>> failedInstrDataMap, boolean isDefaultDS) {

		Map<String, ArrayList<String>> failedInstrColMap = new HashMap<String, ArrayList<String>>();
		try {
			try (PreparedStatement pp = testCon.prepareStatement(OriginalQry)) {
				try (ResultSet rr = pp.executeQuery()) {
					ResultSetMetaData metadata = rr.getMetaData();
					int no_of_columns = metadata.getColumnCount();
					String result = "";
					String columnName = "";
					List<String> existingColNames = new ArrayList<String>();  
					int index = 1;
					for(int cl=1;cl<=no_of_columns;cl++)
					{
						existingColNames.add(metadata.getColumnName(cl));
					}				
					for (int cl = 1; cl <= no_of_columns; cl++) {
						ArrayList<String> values = new ArrayList<String>();
						FailedColumnValues failedColumns = new FailedColumnValues();
						columnName = metadata.getColumnName(cl);
						existingColNames.remove(metadata.getColumnName(cl));
						//If colName exists after removing, it is a duplicate column - suffix it with index.
						if(existingColNames.contains(metadata.getColumnName(cl))){						
							columnName = metadata.getColumnName(cl)+index;
							index ++;
							existingColNames.add(metadata.getColumnName(cl));						
						}else{
							columnName = metadata.getColumnName(cl);						
						}				
						try (ResultSet rr1 = pp.executeQuery()) {
							metadata = rr1.getMetaData();
							while (rr1.next()) {
								int type = metadata.getColumnType(cl);
								values.add(rr1.getString(cl));
							}
							failedColumns.setInstrColumnName(columnName);
							failedColumns.setInstrValues(values);
							failedInstrColMap.put(columnName, values);
						}
					}
				}
			}
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Instructor output fails because of syntax error :" + e.getMessage(), e);
		}
		failedInstrDataMap.put(dataSetId, failedInstrColMap);
		failedDs.setInstrQueryOutput(failedInstrDataMap);
		return failedDs;
	}


	/**
	 * This method tests the student queries against SQL query submitted by the instructor 
	 * for evaluation
	 * 
	 * Same method is used for evaluating in interactive mode also.
	 * 
	 * @param assignmentId
	 * @param questionId
	 * @param query
	 * @param user
	 * @param filePath
	 * @return
	 * @throws Exception
	 */
	//public String testAnswer(int assignmentId,int questionId, String query, String user, String filePath) throws Exception{
	public FailedDataSetValues testAnswer(int assignmentId,int questionId, String course_id,
			String query, String user, String filePath, boolean isLateSubmission, String studRole) throws Exception{
		System.out.println(">>>>>>>>>>>>>>STUDROLE#2"+studRole);
		FailedDataSetValues failedDataSets = new FailedDataSetValues();
		int queryId = 1;
		String qId = "A"+assignmentId +"Q"+questionId+"S"+queryId;
		ArrayList <Boolean> isDataSetVerified = null;
		boolean isMatchAll = false;
		boolean orderIndependent = false;
		String matchAllFailedForDS = "";
		boolean isQueryPass = false;
		String out="";
		int maxMarks = 0;
		int reduceLateSubmissionMarks = 0;
		int index = 0;
		String [] defaultDSIdsAssignment=new String[25];
		String [] defaultDSIdsPerQuestion=new String[25];
		//First instructor answer will be considered to show failed datasets
		String instrQuery = "";
		Map <Integer,Boolean> resultOfDatasetMatchForEachQuery  = new HashMap<Integer, Boolean>();
		System.out.println(">>>>>status1: "+failedDataSets.getStatus());
		try(Connection conn = MyConnection.getDatabaseConnection()){
			try(Connection testConn = MyConnection.getTesterConn()){

				String getMatchAllOption = "select matchallqueries,orderIndependent,latesubmissionmarks from xdata_qinfo where assignment_id = ? and question_id = ? and course_id=?";
				try(PreparedStatement pst = conn.prepareStatement(getMatchAllOption)){
					pst.setInt(1,assignmentId);
					pst.setInt(2,questionId);
					pst.setString(3,course_id);
					try(ResultSet rset = pst.executeQuery()){
						rset.next(); 
						isMatchAll = rset.getBoolean("matchallqueries");
						orderIndependent = rset.getBoolean("orderIndependent");
						reduceLateSubmissionMarks = rset.getInt("latesubmissionmarks");
					}///close resultset try block
				}//close stmt try blck

				defaultDSIdsAssignment = this.getDefaultDSForAssignment(assignmentId,course_id,conn);
				defaultDSIdsPerQuestion = this.getDefaultDSForQuestion(assignmentId,questionId,course_id,conn);

				String qry = "select * from xdata_instructor_query a inner join xdata_qinfo b on a.assignment_id = b.assignment_id and " +
						"a.question_id = b.question_id  where a.assignment_id = ?" +
						" and a.question_id = ? and a.course_id=?";
				try(PreparedStatement pstmt = conn.prepareStatement(qry)){
					pstmt.setInt(1,assignmentId);
					pstmt.setInt(2,questionId); 
					pstmt.setString(3,course_id);
					try(ResultSet rs = pstmt.executeQuery()){
						PopulateTestDataGrading p = new PopulateTestDataGrading();

						GenerateCVC1 cvc = new GenerateCVC1();											
						GenerateDataSet preProcess = new GenerateDataSet();

						cvc.setAssignmentId(assignmentId);
						cvc.setQuestionId(questionId);
						cvc.setQueryId(queryId);
						cvc.setCourseId(course_id);
						preProcess.initializeConnectionDetails(cvc);

						TableMap tm = cvc.getTableMap();
						cvc.closeConn();	
						//For each instructor answer loop to compare datasets
						while(rs.next()){

							boolean incorrect=false;
							isDataSetVerified = new ArrayList<Boolean>();

							String sqlQuery=rs.getString("sql");
							//sqlQuery is the instructor query
							queryId = rs.getInt("query_id");
							query=checkForViews(query,user);
							maxMarks = rs.getInt("totalmarks");

							if(index == 0){
								instrQuery = sqlQuery;
							}

							qId = "A"+assignmentId +"Q"+questionId+"S"+queryId;
							HashMap<String,String> mutants = new HashMap<String,String>();
							mutants.put(qId, query);
							index++;
							System.out.println(">>>>>status2: "+failedDataSets.getStatus());
							try{
								p.deleteAllTempTablesFromTestUser(testConn);
							}catch(Exception e){
								logger.log(Level.INFO,"Temporary Table does not exist",e);
								throw e;
							}
							try{
								p.createTempTableData(conn,testConn,assignmentId,questionId,course_id);
							}catch(Exception e){
								logger.log(Level.SEVERE,"Temp Table creation error. Table already exists",e);
								throw e;
							}

							//First get default sample data sets for the instructor answer, if it is not there, then get
							// it from assignment table. If both are not there, don't set anythng - proceed with other code

							try{
								if(defaultDSIdsPerQuestion != null){

									for(int dId= 0; dId < defaultDSIdsPerQuestion.length;dId++){
										Vector<String> cmismatch = new Vector<String>();
										logger.log(Level.INFO,"******************");
										logger.log(Level.INFO,"Default dataset "+defaultDSIdsPerQuestion[dId]+" Loaded : ");
										logger.log(Level.INFO,"******************");

										String dsName = p.createTempTableWithDefaultData(conn,testConn,assignmentId,questionId,course_id,defaultDSIdsPerQuestion[dId].toString());
										Vector<String> killedMutants = checkAgainstOriginalQuery(mutants,defaultDSIdsPerQuestion[dId].toString(), sqlQuery,"NoPath", orderIndependent, cmismatch, testConn);

										resultOfDatasetMatchForEachQuery = this.processResult(failedDataSets,killedMutants, mutants, incorrect, instrQuery,queryId,resultOfDatasetMatchForEachQuery,defaultDSIdsPerQuestion[dId].toString());
										if(!(resultOfDatasetMatchForEachQuery.containsKey(queryId))){
											resultOfDatasetMatchForEachQuery.put(queryId,true);
										}						
									}
									p.deleteAllTempTablesFromTestUser(testConn);
									System.out.println(">>>>>status3: "+failedDataSets.getStatus());
								}
							}catch(Exception e){
								logger.log(Level.SEVERE,"Exception caught here: "+e.getMessage(),e);
								e.printStackTrace();
							}

							//Load default datasets for assignment 				 
							try{
								if((defaultDSIdsPerQuestion == null || (defaultDSIdsPerQuestion != null && defaultDSIdsPerQuestion.length == 0))
										&&  defaultDSIdsAssignment != null){

									for(int dId= 0; dId < defaultDSIdsAssignment.length;dId++){
										logger.log(Level.INFO,"******************");
										Vector<String> cmismatch = new Vector<String>();
										logger.log(Level.INFO,"Default dataset "+defaultDSIdsAssignment[dId]+" Loaded : ");
										logger.log(Level.INFO,"******************");

										String dsName = p.createTempTableWithDefaultData(conn,testConn,assignmentId,questionId,course_id,defaultDSIdsAssignment[dId].toString());
										System.out.println("CALLED ???????????????????????");
										Vector<String> killedMutants = checkAgainstOriginalQuery(mutants,defaultDSIdsAssignment[dId].toString(), sqlQuery,"NoPath", orderIndependent, cmismatch, testConn);

										resultOfDatasetMatchForEachQuery = this.processResult(failedDataSets,killedMutants, mutants, incorrect, instrQuery,queryId,resultOfDatasetMatchForEachQuery,defaultDSIdsAssignment[dId].toString());
										System.out.println(">>>>>status31: "+failedDataSets.getStatus());
										if(!(resultOfDatasetMatchForEachQuery.containsKey(queryId))){
											resultOfDatasetMatchForEachQuery.put(queryId,true);
										}
									}
									p.deleteAllTempTablesFromTestUser(testConn);
								}	
							}catch(Exception e){
								logger.log(Level.SEVERE,"Exception caught here: "+e.getMessage(),e);
								e.printStackTrace();
							}

							try{
								Map <Integer,Vector<String>>  datasetForQueryMap =	downloadDatasets(assignmentId,questionId,queryId,course_id,conn,filePath, false);
								//Even if no default data sets are there and no datasets are available for the query, , check against the sample Data file that the assignment uses.
								System.out.println(">>>>>status311: "+failedDataSets.getStatus());
								for(Integer id : datasetForQueryMap.keySet()){

									Vector<String> datasets = datasetForQueryMap.get(id);
									for(int i=0;i<datasets.size();i++){

										boolean flag=true;
										//load the contents of DS
										String dsPath = Configuration.homeDir+"/temp_cvc"+filePath+"/"+datasets.get(i);
										File ds=new File(dsPath);
										String copyFiles[] = ds.list();

										Vector<String> vs = new Vector<String>();
										for(int m=0;m<copyFiles.length;m++){
											vs.add(copyFiles[m]);
										}				 		
										// query output handling
										Pattern pattern = Pattern.compile("^A([0-9]+)Q[0-9]+");
										Matcher matcher = pattern.matcher(qId);
										int assignId = 1;
										if (matcher.find()) {
											assignId = Integer.parseInt(matcher.group(1));
										}

										p.populateTestDataForTesting(vs, filePath+"/"+datasets.get(i), tm, testConn, assignmentId, questionId);
										Vector<String> cmismatch=new Vector<String>();
										Vector<String> killedMutants = checkAgainstOriginalQuery(mutants,datasets.get(i), sqlQuery, filePath,orderIndependent,cmismatch,testConn);	
										// System.out.println(">>>>>status312: "+failedDataSets.getStatus());
										resultOfDatasetMatchForEachQuery = this.processResult(failedDataSets,killedMutants, mutants, incorrect, instrQuery,queryId,resultOfDatasetMatchForEachQuery,datasets.get(i));
										System.out.println(">>>>>status32: "+failedDataSets.getStatus());
									}		   
								}
								//If there are no items in resultOfDatasetMatchForEachQuery for given queryId
								//then the query id
								if(!(resultOfDatasetMatchForEachQuery.containsKey(queryId))){
									resultOfDatasetMatchForEachQuery.put(queryId,true);
								}
								logger.log(Level.INFO,"******************");
								logger.log(Level.INFO,"Student Id : "+user+" evaluated against DS Id: ");
								logger.log(Level.INFO,"******************");

							}catch(Exception e){
								logger.log(Level.SEVERE,"Exception caught here: "+e.getMessage(),e);
								e.printStackTrace();
							}
						}
					}//close resultset try block
				}//close statement try block

				/*******Check for Match all or match One Option Start******/
				isQueryPass = this.getMatchForAllQueries(resultOfDatasetMatchForEachQuery,isQueryPass,isMatchAll);
				
				//Set Marks for the failedStudentQuery
				failedDataSets = this.getMarkDetails(conn,failedDataSets, isQueryPass,studRole,assignmentId,questionId,course_id,query,user,isLateSubmission,maxMarks,reduceLateSubmissionMarks);
				
				return failedDataSets;

			}// try block for TestConn ends
		}//try block for Conn ends 
	}

	/**
	 * This method checks if the student query passes all instructor answer datasets or any one intructor answer.
	 * The result depends on the 'Match all' or 'Match any' options selected by the instructor during 
	 * assignment question creation when more than one correct SQL answer exists for a instructor question. 
	 * 
	 * @param resultOfDatasetMatchForEachQuery
	 * @param isQueryPass
	 * @param isMatchAll
	 * @return
	 */
	public boolean getMatchForAllQueries(Map<Integer,Boolean> resultOfDatasetMatchForEachQuery, boolean isQueryPass, boolean isMatchAll){
		for(int i : resultOfDatasetMatchForEachQuery.keySet()){
			//Get first query's result as default
			if(i == 1){
				isQueryPass = resultOfDatasetMatchForEachQuery.get(i);
			}//For second query, get the query result
			boolean isCorrect = resultOfDatasetMatchForEachQuery.get(i);
			//If instructor selects match All result sets while creating the question,
			//then all query results should hold true for the student query to pass
			if(isMatchAll){
				isQueryPass = isCorrect && isQueryPass;	
			}
			else{ 
				//This excutes if instructor selects Match any result set option
				//If even one query result holds true, the student query is considered as pass
				if(isCorrect){
					isQueryPass = true;
					break;
				}
			}

		}
		return isQueryPass;
	}

	/**
	 * This method processes the result and updates the status of student query result in a map that contains the instructor query ID as key value.
	 * This returned result map is later used for manipulating the student result based on  'Match all' or 'Match any' option given by the instructor during 
	 * assignment question creation.
	 * 
	 * @param failedDataSets
	 * @param killedMutants
	 * @param mutants
	 * @param incorrect
	 * @param instrQuery
	 * @param queryId
	 * @param resultOfDatasetMatchForEachQuery
	 * @return
	 */
	public Map<Integer,Boolean> processResult(FailedDataSetValues failedDataSets,Vector<String> killedMutants, HashMap<String,String> mutants, 
			boolean incorrect, String instrQuery,int queryId, Map <Integer,Boolean> resultOfDatasetMatchForEachQuery, String dataSetId ){


		for(int l=0;l<killedMutants.size();l++){
			if(mutants.containsKey(killedMutants.get(l))){
				incorrect =true;
				logger.log(Level.INFO," Eavaluation Failed for queryId :" +killedMutants.get(l));
				//out+=datasets.get(i)+"::Failed:::";
				failedDataSets.setStatus("Failed");
				failedDataSets.getDataSetIdList().add(dataSetId);
				failedDataSets.setInstrQuery(instrQuery);
			}
		}
		if(!incorrect){
			//isDataSetVerified.add(true);
		}	
		else{
			//If query fails, add the status of query to false
			resultOfDatasetMatchForEachQuery.put(queryId,false);
			logger.log(Level.INFO,"");			
		}

		return resultOfDatasetMatchForEachQuery;
	}
	/**
	 * This method calculates the percentage of penalty for late submission
	 * @author Ananyo
	 * @param assignmentId
	 * @param questionId
	 * @param course_id
	 * @param user
	 * @return penalty factor
	 * @throws Exception
	 */

	private float lateSubmission_penalizer(Connection conn,int assignmentId, int questionId,String course_id,String user) throws Exception
	{
		float penalty=0;
		try(Connection conn1= MyConnection.getDatabaseConnection()){

			String qry1 = "select * from xdata_student_queries where assignment_id = ? and question_id = ? and course_id= ? and rollnum = ?";
			PreparedStatement pstmt1 = conn1.prepareStatement(qry1);
			pstmt1.setInt(1,assignmentId);
			pstmt1.setInt(2,questionId);
			pstmt1.setString(3,course_id);
			pstmt1.setString(4,user);
			ResultSet rs1 = pstmt1.executeQuery();
			Timestamp sub_time=null;
			while(rs1.next()) sub_time = rs1.getTimestamp("submissiontime"); 




			String qry2="select * from xdata_assignment where assignment_id = ? and course_id= ?";
			PreparedStatement pstmt2 = conn1.prepareStatement(qry2);
			pstmt2.setInt(1,assignmentId);
			pstmt2.setString(2,course_id);	
			ResultSet rs2 = pstmt2.executeQuery();
			Timestamp start=null,end=null,soft=null;
			float p=0;
			while(rs2.next()){ 
				start = rs2.getTimestamp("starttime");
				end = rs2.getTimestamp("endtime");
				soft = rs2.getTimestamp("softtime");
				if(soft==null)
					return 1;
				if(rs2.getString("penalty").compareTo("")==0)
				{

					p=10;
				}
				else
					p=Float.parseFloat(rs2.getString("penalty"));
				p=p/100;

			}
			//System.out.println("p: "+p);
			if(soft==null)
				return 1;
			SimpleDateFormat formatter = new SimpleDateFormat(
					"yyyy-MM-dd HH:mm:ss");
			formatter.setLenient(false);
			String ending = formatter.format(end);
			String starting = formatter.format(start);
			String softing = formatter.format(soft);
			String submitting = formatter.format(sub_time);


			java.util.Date endDate = formatter.parse(ending);
			java.util.Date startDate = formatter.parse(starting);
			java.util.Date softDate = formatter.parse(softing);
			java.util.Date subDate = formatter.parse(submitting);

			//System.out.println("enddate: "+endDate.toString());
			//System.out.println("startdate: "+startDate.toString());
			//System.out.println("softdate: "+softDate.toString());
			//System.out.println("subdate: "+subDate.toString());

			if(startDate.compareTo(subDate)<0 && softDate.compareTo(subDate) >0)
				penalty=0;
			else if(softDate.compareTo(subDate) <0 && endDate.compareTo(subDate) >0)
				penalty=p;
			else
				penalty=1;


		}
		catch(Exception ex){
			logger.log(Level.SEVERE,ex.getMessage(), ex);
			ex.printStackTrace();
		}	

		return 1-penalty;
	}
	
	float  tempgetMarkDetails(Connection conn,String studRole, 
			int assignmentId, int questionId,String course_id,String query,
			String user,boolean isLateSubmission, int maxMarks,
			float reduceLateSubmissionMarks) throws Exception
	{	
		MarkInfo markInfo = new MarkInfo();
		if(studRole==null || !studRole.equals("guest")){
			String qryUpdate = "update xdata_student_queries set verifiedcorrect = false where assignment_id ='"+ assignmentId+"' and question_id = '"+questionId+"' and rollnum = '"+user+"' and course_id='"+course_id+"'";		
			try(PreparedStatement pstmt2 = conn.prepareStatement(qryUpdate)){
				//pstmt2.setString(1,out.trim());
				pstmt2.executeUpdate(); 
			}
		}
		String studentQuery = "";
		  try( PreparedStatement stmt1 = conn.prepareStatement("select * from xdata_student_queries where rollnum = ? and assignment_id = ? and question_id = ?");)
		  {
			   stmt1.setString(1, user);
   		       stmt1.setInt(2, assignmentId);
   		       stmt1.setInt(3, questionId);
   		       try(ResultSet  rs = stmt1.executeQuery();)
   		       {
   		    	   if(rs.next())
   		    		   studentQuery = rs.getString("querystring");
   		       }
		  }
		       
		       
		// Initiate partial marking
		String qry = "select * from xdata_instructor_query where assignment_id = ? and question_id = ? and course_id= ?";
		try(PreparedStatement pstmt = conn.prepareStatement(qry)){
			pstmt.setInt(1,assignmentId);
			pstmt.setInt(2,questionId);
			pstmt.setString(3,course_id);
			try(ResultSet rs = pstmt.executeQuery()){
				while(rs.next()){	
					int queryId = rs.getInt("query_id");
					try{
						//PartialMarker marker = new PartialMarker(assignmentId, questionId, queryId,course_id,user,failedDataSets.getStudentQueryString());
						PartialMarker marker = new PartialMarker(assignmentId, questionId, queryId,course_id,user,studentQuery);
						if(studRole==null || !studRole.equals("guest")){

						}else{

						}
						MarkInfo result = marker.getMarksForQueryStructures();
						if(result.Marks > markInfo.Marks)
							markInfo = result;

						//markInfo.Marks = 0;
					}
					catch(Exception ex){
						logger.log(Level.SEVERE,ex.getMessage(), ex);
						ex.printStackTrace();
					}		
				}
			}//close resultset try
		}//close connection try
	
	Gson gson = new Gson();
	String info = gson.toJson(markInfo);
	if(!studRole.equals("guest")){
		float raw_marks=markInfo.Marks;
		markInfo.Marks= raw_marks * 1;
		//TODO URGENT : SCHEMA CHANGE !!!!!
		DatabaseHelper.InsertIntoScores(conn, assignmentId, questionId, 1, course_id, maxMarks, user, info, markInfo.Marks,raw_marks);
	}
	return  markInfo.Marks;
}

		
	

	/**
	 * This method calls the partial marking part to calculate partial marks for failed queries. 
	 * If the query is correct, it awards 100 marks and updates the DB with same.
	 * Marks for late submission are detected based on lateSubmissionFlag.
	 * 
	 * @param conn
	 * @param failedDataSets
	 * @param isQueryPass
	 * @param studRole
	 * @param assignmentId
	 * @param questionId
	 * @param course_id
	 * @param query
	 * @param user
	 * @param isLateSubmission
	 * @param maxMarks
	 * @param reduceLateSubmissionMarks
	 * @return
	 * @throws Exception
	 */
	public FailedDataSetValues getMarkDetails(Connection conn, FailedDataSetValues failedDataSets, boolean isQueryPass, String studRole, 
			int assignmentId, int questionId,String course_id,String query,
			String user,boolean isLateSubmission, int maxMarks,
			float reduceLateSubmissionMarks) throws Exception{

		MarkInfo markInfo = new MarkInfo();
		try{
			float lateSub_factor=lateSubmission_penalizer(conn,assignmentId,questionId,course_id,user);
			//System.out.println("fraction>>> "+ lateSub_factor);
			if(isQueryPass){
				logger.log(Level.FINE,"Question passed the datasets expected");
				if(studRole==null || !studRole.equals("guest")){
					String qryUpdate = "update xdata_student_queries set verifiedcorrect = true where assignment_id ='"+assignmentId+"' and question_id = '"+questionId+"' and rollnum = '"+user+"' and course_id='"+course_id+"'";
					try(PreparedStatement pstmt3 = conn.prepareStatement(qryUpdate)){
						pstmt3.executeUpdate();
					}
				}
				//out+="Passed:::";

				//If late submission, reduce marks as given in instructor query
				if(isLateSubmission){
					markInfo.Marks = maxMarks - reduceLateSubmissionMarks;
				}else{
					markInfo.Marks = maxMarks;
				}
				failedDataSets.setMaxMarks(maxMarks);
				failedDataSets.setMarks(markInfo.Marks);
			}
			else{

				if(studRole==null || !studRole.equals("guest")){
					String qryUpdate = "update xdata_student_queries set verifiedcorrect = false where assignment_id ='"+ assignmentId+"' and question_id = '"+questionId+"' and rollnum = '"+user+"' and course_id='"+course_id+"'";		
					try(PreparedStatement pstmt2 = conn.prepareStatement(qryUpdate)){
						//pstmt2.setString(1,out.trim());
						pstmt2.executeUpdate(); 
					}
				}
				String studentQuery = "";
				  try( PreparedStatement stmt1 = conn.prepareStatement("select * from xdata_student_queries where rollnum = ? and assignment_id = ? and question_id = ?");)
				  {
					   stmt1.setString(1, user);
		   		       stmt1.setInt(2, assignmentId);
		   		       stmt1.setInt(3, questionId);
		   		       try(ResultSet  rs = stmt1.executeQuery();)
		   		       {
		   		    	   if(rs.next())
		   		    		   studentQuery = rs.getString("querystring");
		   		       }
				  }
	   		       
	   		       
				// Initiate partial marking
				String qry = "select * from xdata_instructor_query where assignment_id = ? and question_id = ? and course_id= ?";
				try(PreparedStatement pstmt = conn.prepareStatement(qry)){
					pstmt.setInt(1,assignmentId);
					pstmt.setInt(2,questionId);
					pstmt.setString(3,course_id);
					try(ResultSet rs = pstmt.executeQuery()){
						while(rs.next()){	
							int queryId = rs.getInt("query_id");
							try{
								//PartialMarker marker = new PartialMarker(assignmentId, questionId, queryId,course_id,user,failedDataSets.getStudentQueryString());
								PartialMarker marker = new PartialMarker(assignmentId, questionId, queryId,course_id,user,studentQuery);
								if(studRole==null || !studRole.equals("guest")){

								}else{

								}
								try {
									MarkInfo result = marker.getMarksForQueryStructures();
									if(result.Marks > markInfo.Marks)
										markInfo = result;
								}catch(Exception e) {
									logger.log(Level.INFO, e.getMessage(), e);
								}

								//markInfo.Marks = 0;
							}
							catch(Exception ex){
								logger.log(Level.SEVERE,ex.getMessage(), ex);
								//ex.printStackTrace();
							}		
						}
					}//close resultset try
				}//close connection try
			}		
			Gson gson = new Gson();
			String info = gson.toJson(markInfo);
			if(!studRole.equals("guest")){
				float raw_marks=markInfo.Marks;
				markInfo.Marks= raw_marks * lateSub_factor;
				//TODO URGENT : SCHEMA CHANGE !!!!!
				DatabaseHelper.InsertIntoScores(conn, assignmentId, questionId, 1, course_id, maxMarks, user, info, markInfo.Marks,raw_marks);
			}
			failedDataSets.setMaxMarks(maxMarks);
			failedDataSets.setMarks(markInfo.Marks);
		}catch(Exception e){
			logger.log(Level.SEVERE, e.getMessage(),e);
		}
		return failedDataSets;
	}

	/**
	 * This method checks the multiple SQL queries for the equivalence and gives the dataset in which the 
	 * queries are not equivalent 
	 * 
	 */
	public Map<Integer,FailedDataSetValues> checkQueryEquivalence(int assignmentId,int questionId, String course_id) throws Exception{
		FailedDataSetValues failedDataSets = new FailedDataSetValues();
		Map<Integer,FailedDataSetValues> failedDataSetsPerQuery = new HashMap<Integer,FailedDataSetValues>();
		String filePath = "";
		String [] defaultDSIdsAssignment=new String[25];
		String [] defaultDSIdsPerQuestion=new String[25];
		ArrayList<Boolean> isDataSetVerified = null;
		HashMap<String,String> mutants = new HashMap<String,String>();
		boolean incorrect = false;
		String query =  "";
		int queryId = 0;
		String qId = "";
		int index =0;
		String out="";
		String instrQuery = "";
		//ArrayList<FailedDataSetValues> listOfFailedDS = new ArrayList<FailedDataSetValues>();
		Map <Integer,Vector<String>> dataSetForQueries = new HashMap<Integer,Vector<String>>();

		Map <Integer,Boolean> resultOfDatasetMatchForEachQuery  = new HashMap<Integer, Boolean>();

		try(Connection conn = MyConnection.getDatabaseConnection()){

			try(Connection testConn = MyConnection.getTesterConn()){

				//Get default dataset Ids from assignment table
				String getDefaultDataSets = "select defaultDSetId from xdata_assignment where assignment_id = ? and course_id=?";
				try(PreparedStatement pst =conn.prepareStatement(getDefaultDataSets) ){
					pst.setInt(1,assignmentId);
					pst.setString(2,course_id);
					try(ResultSet rset = pst.executeQuery()){
						rset.next();
						String dsIds = rset.getString("defaultDSetId");
						Gson gson = new Gson();
						Type listType = new TypeToken<String[]>() {}.getType();
						defaultDSIdsAssignment = new Gson().fromJson(dsIds, listType);

					}
				}

				String getDefaultDataSetsForQuestion = "select default_sampledataid from xdata_qinfo where assignment_id = ? and course_id=? and question_id=?";
				try(PreparedStatement pst =conn.prepareStatement(getDefaultDataSetsForQuestion) ){
					pst.setInt(1,assignmentId);
					pst.setString(2,course_id);
					pst.setInt(3, questionId);
					try(ResultSet rset = pst.executeQuery()){
						rset.next();
						String dsIds = rset.getString("default_sampledataid");
						Gson gson = new Gson();
						Type listType = new TypeToken<String[]>() {}.getType();
						defaultDSIdsPerQuestion = new Gson().fromJson(dsIds, listType);

					}
				}
				String qry = "select * from xdata_instructor_query a inner join xdata_qinfo b on a.assignment_id = b.assignment_id and " +
						"a.question_id = b.question_id where a.assignment_id = ?" +
						" and a.question_id = ? and a.course_id=?";
				try(PreparedStatement pstmt = conn.prepareStatement(qry)){
					pstmt.setInt(1,assignmentId);
					pstmt.setInt(2,questionId); 
					pstmt.setString(3,course_id);
					try(ResultSet rs = pstmt.executeQuery()){
						PopulateTestDataGrading p = new PopulateTestDataGrading();

						//For each SQL answer loop to compare datasets
						while(rs.next()){
							incorrect=false;
							isDataSetVerified = new ArrayList<Boolean>();

							String sqlQuery=rs.getString("sql");
							queryId = rs.getInt("query_id");
							qId = "A"+assignmentId +"Q"+questionId+"S"+queryId;
							filePath="4/"+course_id+"/"+qId;
							if(index ==0){
								instrQuery = sqlQuery;
							}else{
								mutants.put(qId, sqlQuery);
							}
							index++;
							dataSetForQueries = downloadDataSetForAllQueries(assignmentId, questionId, queryId, course_id, testConn, dataSetForQueries);
						}

						GenerateCVC1 cvc = new GenerateCVC1();											
						GenerateDataSet preProcess = new GenerateDataSet();

						cvc.setAssignmentId(assignmentId);
						cvc.setQuestionId(questionId);
						cvc.setQueryId(queryId);
						cvc.setCourseId(course_id);
						preProcess.initializeConnectionDetails(cvc);

						TableMap tm = cvc.getTableMap();
						cvc.closeConn();
						/********run the queries against defaut data set for the application ********/
						if(defaultDSIdsPerQuestion != null){
							try{
								p.deleteAllTempTablesFromTestUser(testConn);
							}catch(Exception e){
								logger.log(Level.INFO,"Temporary Table does not exist",e);

							}
							try{
								p.createTempTableData(conn,testConn,assignmentId,questionId,course_id);
							}catch(Exception e){
								logger.log(Level.SEVERE,"Temp Table creation error. Table already exists",e);
								throw e;
							}
							for(int dId= 0; dId < defaultDSIdsPerQuestion.length;dId++){
								Vector<String> cmismatch = new Vector<String>();
								logger.log(Level.INFO,"******************");
								logger.log(Level.INFO,"Default dataset "+defaultDSIdsPerQuestion[dId]+" Loaded : ");
								logger.log(Level.INFO,"******************");

								String dsName = p.createTempTableWithDefaultData(conn,testConn,assignmentId,questionId,course_id,defaultDSIdsPerQuestion[dId].toString());
								Vector<String> killedMutants = checkAgainstOriginalQuery(mutants,defaultDSIdsPerQuestion[dId].toString(), instrQuery,"NoPath",true, cmismatch, testConn);

								for(int l=0;l<killedMutants.size();l++){
									if(mutants.containsKey(killedMutants.get(l))){
										logger.log(Level.INFO,"false" +killedMutants.get(l));
										incorrect = true;
										//Get failed datasets
										//matchAllFailedForDS = datasets.get(i);

										failedDataSets.setStatus("Failed");
										failedDataSets.getDataSetIdList().add(defaultDSIdsPerQuestion[dId].toString());
										failedDataSets.setInstrQuery(instrQuery);
									}
								}

								if(!incorrect){
									//isDataSetVerified.add(true);
								}	
								else{											
									resultOfDatasetMatchForEachQuery.put(queryId,false);
									logger.log(Level.INFO,"");
								}	

								if(!(resultOfDatasetMatchForEachQuery.containsKey(queryId))){
									resultOfDatasetMatchForEachQuery.put(queryId,true);
								}
								//listOfFailedDS.add(failedDataSets);
								failedDataSetsPerQuery.put(0, failedDataSets);
								p.deleteAllTablesFromTestUser(testConn);

							}
							p.deleteAllTempTablesFromTestUser(testConn);
						}
						/******Run each query against the default data set for the question **********/
						if((defaultDSIdsPerQuestion == null || (defaultDSIdsPerQuestion != null && defaultDSIdsPerQuestion.length == 0))
								&&  defaultDSIdsAssignment != null){
							try{
								p.deleteAllTempTablesFromTestUser(testConn);
							}catch(Exception e){
								logger.log(Level.INFO,"Temporary Table does not exist",e);

							}
							try{
								p.createTempTableData(conn,testConn,assignmentId,questionId,course_id);
							}catch(Exception e){
								logger.log(Level.SEVERE,"Temp Table creation error. Table already exists",e);
								throw e;
							}
							for(int dId= 0; dId < defaultDSIdsAssignment.length;dId++){
								Vector<String> cmismatch = new Vector<String>();
								logger.log(Level.INFO,"******************");
								logger.log(Level.INFO,"Default dataset "+defaultDSIdsAssignment[dId]+" Loaded : ");
								logger.log(Level.INFO,"******************");

								String dsName = p.createTempTableWithDefaultData(conn,testConn,assignmentId,questionId,course_id,defaultDSIdsAssignment[dId].toString());
								Vector<String> killedMutants = checkAgainstOriginalQuery(mutants,defaultDSIdsAssignment[dId].toString(), instrQuery,"NoPath", true, cmismatch, testConn);

								for(int l=0;l<killedMutants.size();l++){
									if(mutants.containsKey(killedMutants.get(l))){
										logger.log(Level.INFO,"false" +killedMutants.get(l));
										incorrect = true;
										//Get failed datasets
										//matchAllFailedForDS = datasets.get(i);	
										failedDataSets.setStatus("Failed");
										failedDataSets.getDataSetIdList().add(defaultDSIdsAssignment[dId].toString());
										failedDataSets.setInstrQuery(instrQuery);
									}
								}
								if(!incorrect){
									//isDataSetVerified.add(true);
								}else{											
									resultOfDatasetMatchForEachQuery.put(queryId,false);
									logger.log(Level.INFO,"");
								}	
								if(!(resultOfDatasetMatchForEachQuery.containsKey(queryId))){
									resultOfDatasetMatchForEachQuery.put(queryId,true);
								}
								//listOfFailedDS.add(failedDataSets);
								failedDataSetsPerQuery.put(0, failedDataSets);
								p.deleteAllTablesFromTestUser(testConn);
							}
							p.deleteAllTempTablesFromTestUser(testConn);
						}	// Default data set for question level ends

						/************************************************************/
						/*******Get the datasets for each query and run them *******/
						Map <Integer,Vector<String>>  datasetForQueryMap =	downloadDataSetForAllQueries(assignmentId,questionId,queryId,course_id,conn,dataSetForQueries);
						if(datasetForQueryMap.isEmpty() && defaultDSIdsAssignment == null 
								&& defaultDSIdsPerQuestion == null ){
							//Load the default sample data file
							boolean flag=true;
							Vector<String> cmismatch = new Vector<String>();
							try{
								p.deleteAllTempTablesFromTestUser(testConn);
							}catch(Exception e){
								logger.log(Level.INFO,"Temporary Table does not exist",e);

							}
							try{
								p.createTempTableData(conn,testConn,assignmentId,questionId,course_id);
							}catch(Exception e){
								logger.log(Level.SEVERE,"Temp Table creation error. Table already exists",e);
								throw e;
							} 
							Vector<String> killedMutants = checkAgainstOriginalQuery(mutants, "DS_Default", instrQuery,"NoPath", true, cmismatch, testConn);
							logger.log(Level.INFO,"******************");
							logger.log(Level.INFO,"Default dataset Loaded : " + " " + killedMutants.size());
							logger.log(Level.INFO,"******************");
							for(int l=0;l<killedMutants.size();l++){
								if(mutants.containsKey(killedMutants.get(l))){
									incorrect =true;
									logger.log(Level.INFO,"false" +killedMutants.get(l));
									out+="Default Data"+"::Failed:::";
									failedDataSets.setStatus("Failed");
									failedDataSets.getDataSetIdList().add("DS_Default");
									failedDataSets.setInstrQuery(instrQuery);
								}
							}
							if(!incorrect){
								//isDataSetVerified.add(true);
							}else{
								resultOfDatasetMatchForEachQuery.put(queryId,false);
								logger.log(Level.INFO,"");
							}	
							//listOfFailedDS.add(failedDataSets);
							failedDataSetsPerQuery.put(0, failedDataSets);
							if(!(resultOfDatasetMatchForEachQuery.containsKey(queryId))){
								resultOfDatasetMatchForEachQuery.put(queryId,true);
							}
						}else{

							for(Integer id : datasetForQueryMap.keySet()){
								failedDataSets  = new FailedDataSetValues();
								Vector<String> datasets = datasetForQueryMap.get(id);
								filePath = "4/"+course_id+"/A"+assignmentId+"Q"+questionId+"S"+id;
								failedDataSets.setQuery_id(id);
								for(int i=0;i<datasets.size();i++){

									boolean flag=true;
									//load the contents of DS
									String dsPath = Configuration.homeDir+"/temp_cvc"+filePath+"/"+datasets.get(i);
									File ds=new File(dsPath);
									String copyFiles[] = ds.list();

									Vector<String> vs = new Vector<String>();
									for(int m=0;m<copyFiles.length;m++){
										vs.add(copyFiles[m]);
									}				 		
									// query output handling

									Pattern pattern = Pattern.compile("^A([0-9]+)Q[0-9]+");
									Matcher matcher = pattern.matcher(qId);
									int assignId = 1;

									if (matcher.find()) {
										assignId = Integer.parseInt(matcher.group(1));
									}

									p.populateTestDataForTesting(vs, filePath+"/"+datasets.get(i), tm, testConn, assignmentId, questionId);
									Vector<String> cmismatch=new Vector<String>();
									Vector<String> killedMutants = checkAgainstOriginalQuery(mutants,datasets.get(i), instrQuery, filePath,true,cmismatch,testConn);
									for(int l=0;l<killedMutants.size();l++){
										if(mutants.containsKey(killedMutants.get(l))){
											incorrect =true;
											logger.log(Level.INFO,"false" +killedMutants.get(l));
											out+=datasets.get(i)+"::Failed:::";
											failedDataSets.setStatus("Failed");
											failedDataSets.getDataSetIdList().add(datasets.get(i));
											failedDataSets.setInstrQuery(instrQuery);
										}
									}
									if(!incorrect){
										//isDataSetVerified.add(true);
									}	
									else{
										//If query fails, add the status of query to false
										resultOfDatasetMatchForEachQuery.put(queryId,false);
										logger.log(Level.INFO,"");			
									}

								}	
								//listOfFailedDS.add(failedDataSets);
								failedDataSetsPerQuery.put(id, failedDataSets);
							}
							//Used when more than one correct answer is specified
							//This will store the dataset verification result for each query 
							//resultOfDatasetMatchForEachQuery.put(queryId,isDataSetVerified);

							//If there are no items in resultOfDatasetMatchForEachQuery for given queryId
							//then the query id
							if(!(resultOfDatasetMatchForEachQuery.containsKey(queryId))){
								resultOfDatasetMatchForEachQuery.put(queryId,true);
							}

						}

					}//close for each SQL answer try resultset loop
				}
				HashMap<Integer, Boolean> finalMatchForQueries = new HashMap<Integer, Boolean>();
				boolean isMatchAll = false;
				boolean isQueryPass = false;


				for(int i : resultOfDatasetMatchForEachQuery.keySet()){
					//Get first query's result as default
					if(i == 1){
						isQueryPass = resultOfDatasetMatchForEachQuery.get(i);
					}//For second query, get the query result
					boolean isCorrect = resultOfDatasetMatchForEachQuery.get(i);
					//If instructor selects match All result sets while creating the question,
					//then all query results should hold true for the student query to pass
					if(isMatchAll){
						isQueryPass = isCorrect && isQueryPass;	
					}
					else{ 
						//This excutes if instructor selects Match any result set option
						//If even one query result holds true, the student query is considered as pass
						if(isCorrect){
							isQueryPass = true;
							break;
						}
					}

				}

				if(isQueryPass){

				}else{
					//return failedDataSets;
				}

			}
		}
		return failedDataSetsPerQuery;
	}

	/**
	 * This method checks if the query student query creates any views
	 * 
	 * @param query
	 * @param user
	 * @return
	 * @throws Exception
	 */
	public String checkForViews(String query, String user) throws Exception{

		try(Connection conn = MyConnection.getDatabaseConnection()){
			HashMap<String, String> hm=new HashMap<String,String>();

			String out=query;
			String qry=query.replaceAll("\n"," ").replaceAll("\\s+", " ").toLowerCase();
			if(qry.startsWith("create view ")){
				isCreateView=true;

				String vname=query.substring(12).split("\\s")[0];
				String vquery=query.substring(12).split("\\s")[2];
				vquery=vquery.replaceAll("\n", " ").replaceAll("\\s+", " ").replaceAll("'", "''");
				String ins = "INSERT INTO xdata_views VALUES ("+vname+",'"+user+"',"+vquery+");";			
				try(PreparedStatement smt = conn.prepareStatement(ins)){
					smt.executeUpdate();
				}catch(SQLException ex){
					logger.log(Level.SEVERE,"SQL Exception: "+ex.getMessage(),ex);
				} 
				conn.close();
				return out;			
			}
			
			try{

			String q="Select * from xdata_views where rollnum = ?";

			try(PreparedStatement smt = conn.prepareStatement(q)){
				smt.setString(1, user);
				try(ResultSet rs=smt.executeQuery()){
					while(rs.next()){
						hm.put(rs.getString("vname"), rs.getString("viewquery"));
					}
				}
			}

			String newquery="";
			/*Add the select part to new query */
			StringTokenizer st=new StringTokenizer(query);                    
			String token=st.nextToken();        
			while(!token.equalsIgnoreCase("from")){        
				newquery+=token+" ";
				token=st.nextToken();
			}

			newquery+="from ";
			/*Add the new from part*/
			while(st.hasMoreTokens()){
				token=st.nextToken();            
				if(token.equalsIgnoreCase("where")||token.equalsIgnoreCase("group")){
					newquery+=token+ " ";
					break;
				}
				if(token.equals(",")){
					newquery+=token+ " ";
				}
				if(token.contains(",")){
					String tablenames[]=token.split(",");
					for(int j=0;j<tablenames.length;j++){
						boolean isPresent=false;
						if(hm.containsKey(tablenames[j])){
							newquery+=hm.get(tablenames[j]) + " " + tablenames[j]+" ";
							isPresent=true;            
						}
						if(!isPresent){
							newquery+=tablenames[j]+" ";
						}
						newquery+=" ,";
					}
					newquery=newquery.substring(0,newquery.length()-1);

				}else{
					boolean isPresent=false;
					if(hm.containsKey(token)){
						newquery+=hm.get(token) + " " + token+" ";
						isPresent=true;            
					}
					if(!isPresent){
						newquery+=token+" ";
					}
				}

			}
			/*Add the remaning part of query*/
			while(st.hasMoreTokens()){
				token=st.nextToken();
				newquery+=token+ " ";
			}
			}
			catch(Exception e){
				e.printStackTrace();
			}
			return out;
		}//try block for connection ends

	}

	// This method is only used for experiments and makes some assumptions which might not be true always
	public void test(String filePath, int assignmentId, int questionId, int queryId, String course_id) throws Exception{

		String qry = "select * from xdata_instructor_query where assignment_id = ? and question_id = ? and query_id = ? and course_id = ?";    	

		try(Connection conn = MyConnection.getDatabaseConnection()){
			try(Connection testConn = MyConnection.getTesterConn()){

				try(PreparedStatement pstmt = conn.prepareStatement(qry)){
					pstmt.setInt(1, assignmentId);
					pstmt.setInt(2, questionId);
					pstmt.setInt(3, queryId);
					pstmt.setString(4,course_id);
					try(ResultSet rs = pstmt.executeQuery()){	
						// query output handling
						GenerateCVC1 cvc = new GenerateCVC1();											
						GenerateDataSet preProcess = new GenerateDataSet();

						cvc.setAssignmentId(assignmentId);
						cvc.setQuestionId(questionId);
						cvc.setQueryId(queryId);
						cvc.setCourseId(course_id);
						preProcess.initializeConnectionDetails(cvc);

						TableMap tm = cvc.getTableMap();
						cvc.closeConn();
						PopulateTestDataGrading p = new PopulateTestDataGrading();
						if(rs.next()){
							//delete the previous datasets		
							//Runtime r = Runtime.getRuntime();
							File f=new File(Configuration.homeDir+"/temp_cvc"+filePath+"/");
							File f2[]=f.listFiles();
							if(f2 != null){
								for(int i=0;i<f2.length;i++){
									if(f2[i].isDirectory() && f2[i].getName().startsWith("DS")){
										//Process proc = r.exec("rm -rf "+Configuration.homeDir+"/temp_cvc"+filePath+"/"+f2[i].getName());
										//Utilities.closeProcessStreams(proc);
										Utilities.deletePath(Configuration.homeDir+"/temp_cvc"+filePath+"/"+f2[i].getName());
									}					
								}
							}
							String sqlQuery = rs.getString("sql");
							String getMutant = "select * from xdata_student_queries where assignment_id = ? and question_id = ? and course_id= ?";
							HashMap<String,String> mutants = new HashMap<String,String>();

							try(PreparedStatement pstmt1 = conn.prepareStatement(getMutant)){
								pstmt1.setInt(1, assignmentId);
								pstmt1.setInt(2, questionId);
								pstmt1.setString(3, course_id);
								try(ResultSet rs1 = pstmt1.executeQuery()){
									while(rs1.next()){
										String queryvariantid = rs1.getString("rollnum");
										String qryString = rs1.getString("querystring");
										mutants.put(queryvariantid, qryString);
									}
								}//try block for resultset rs1 ends
							}//try block for pstmt1 ends

							Map <Integer,Vector<String>>  datasetForQueryMap =  downloadDatasets(assignmentId,questionId,queryId,course_id,conn,filePath, false);
							datasets = datasetForQueryMap.get(queryId);
							Vector<String> cmismatch=new Vector<String>();
							for(int i=0;i<datasets.size();i++){
								String dsPath = Configuration.homeDir+"/temp_cvc"+filePath+"/"+datasets.get(i);
								File ds=new File(dsPath);
								String copyFiles[] = ds.list();

								Vector<String> vs = new Vector<String>();
								for(int m=0;m<copyFiles.length;m++){
									vs.add(copyFiles[m]);
								}

								p.populateTestDataForTesting(vs, filePath+"/"+datasets.get(i), tm, testConn, assignmentId, questionId);

								Vector<String> killedMutants = mutantsKilledByDataset1(mutants, datasets.get(i), sqlQuery, dsPath, true,cmismatch, testConn);

								logger.log(Level.INFO,datasets.get(i)+" "+killedMutants.size());

								HashMap<String,String> mutantsdups=(HashMap<String, String>) mutants.clone();
								for(int l=0;l<killedMutants.size();l++){
									if(mutants.containsKey(killedMutants.get(l))){
										String qryUpdate = "update xdata_student_queries set verifiedcorrect = false where assignment_id = ? and question_id = ? and rollnum = ? and course_id=?";		
										try(PreparedStatement pstmt2 = conn.prepareStatement(qryUpdate)){
											pstmt2.setInt(1, assignmentId);
											pstmt2.setInt(2, questionId);
											pstmt2.setString(3, killedMutants.get(l));
											pstmt2.setString(4,course_id);
											pstmt2.executeUpdate();
										}
										mutants.remove(killedMutants.get(l));
									}
								}
								for(int l=0;l<cmismatch.size();l++){
									String qryUpdate = "update xdata_student_queries set columnmismatch = true where assignment_id = ? and question_id = ? and rollnum = ? and course_id=?";
									try(PreparedStatement pstmt2 = conn.prepareStatement(qryUpdate)){
										pstmt2.setInt(1, assignmentId);
										pstmt2.setInt(2, questionId);
										pstmt2.setString(3, cmismatch.get(l));
										pstmt2.setString(4,course_id);
										pstmt2.executeUpdate();
									}
								}
								cmismatch.clear();
							}

							Collection cl = mutants.keySet();
							Iterator itr = cl.iterator();
							int ctr=0;
							while(itr.hasNext()){
								String temp = (String) itr.next();
								String qryUpdate = "update xdata_student_queries set verifiedcorrect = true where assignment_id = ? and question_id = ? and rollnum = ? and course_id = ?";
								try(PreparedStatement pstmt3 = conn.prepareStatement(qryUpdate)){
									pstmt3.setInt(1, assignmentId);
									pstmt3.setInt(2, questionId);
									pstmt3.setString(3, temp);
									pstmt3.setString(4,course_id);
									pstmt3.executeUpdate();
								}
								ctr++;
							}
							logger.log(Level.INFO,""+ctr);
							//}
						}
					}//try block for resultset ends
				}//try block for statement ends
			}//try bloak for testcon ends
		}//try block for conn ends
	}

	//This method can be removed
	/*
	private Vector<String> mutantsKilledByQueryPlan(
			HashMap<String, String> mutants, String sqlQuery) {
		// TODO Auto-generated method stub
		Vector<String> queryIds = new Vector<String>();
		try {
			//Connection conn = MyConnection.getTestDatabaseConnection();
		//	Connection conn = MyConnection.getTestDatabaseConnection();

			String OriginalPlan="explain analyze "+sqlQuery;
			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(OriginalPlan);
			OriginalPlan="";
			while(rs.next()){
				OriginalPlan+=rs.getString(1)+"\n";
				int last=OriginalPlan.lastIndexOf("(actual time");
				if(last != -1)
					OriginalPlan=OriginalPlan.substring(0, last);
				last=OriginalPlan.lastIndexOf("Total runtime");
				if(last!= -1)
					OriginalPlan=OriginalPlan.substring(0, last);
			}

			String mutant_query="";
			Collection c = mutants.keySet();
			Iterator itr = c.iterator();
			while(itr.hasNext()){
				Object Id = itr.next();
				//logger.log(Level.INFO,"\nId="+Id);
				String mutant_qry = mutants.get(Id);

				//logger.log(Level.INFO,"Student: "+mutant_qry);
				mutant_qry=mutant_qry.trim().replace(';', ' ');				
				mutant_qry="explain analyze "+mutant_qry;
				Statement pstmt=conn.createStatement();
				try{
					ResultSet rset=pstmt.executeQuery(mutant_qry);
					String mutant_plan="";
					while(rset.next()){
						mutant_plan+=rset.getString(1)+"\n";
						int last=mutant_plan.lastIndexOf("(actual time");
						if(last != -1)
							mutant_plan=mutant_plan.substring(0, last);
						last=mutant_plan.lastIndexOf("Total runtime");
						if(last!= -1)
							mutant_plan=mutant_plan.substring(0, last);
					}
					if(!OriginalPlan.equalsIgnoreCase(mutant_plan)){
						queryIds.add((String)Id);
					}
				} catch (Exception e){
					queryIds.add((String)Id);
				}
			}

			//conn.close();

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return queryIds;
	}*/

	public void generateResults(String file, BufferedWriter bfrd) throws Exception {			//bfrd written to write no of incorrect queries. Delete if it gives errors

		//Connection conn = MyConnection.getExistingDatabaseConnection();
		try(Connection conn = MyConnection.getDatabaseConnection()){

			String filePath = "4/";			
			//String fileName="OldAssignment/Assign"+file;
			//String fileName="Assignment/Assign"+file;
			String fileName="NewAssignment/Assign"+file;
			//String strFile = Configuration.homeDir+"/temp_cvc"+filePath+"/CS_387_Quiz_0.csv";
			String strFile = Configuration.homeDir+"/temp_cvc"+filePath+"/"+fileName;	
			Scanner sc = new Scanner(new File(strFile));
			sc.useDelimiter("\\s*:\\s*");
			//sc.nextLine();
			sc.next();sc.next();sc.next();

			int rollno=1;
			String qID=sc.next();
			String quesDesc=sc.next();
			String quesID; 

			while(sc.hasNext()){
				int total=0,incorrect=0;
				quesID=qID.trim();
				//			logger.log(Level.INFO,"quesID " + quesID);
				if(quesID.startsWith("'extra'")){
					quesID=qID.substring(8);					
				}

				logger.log(Level.INFO,"Question Number "+quesID);
				logger.log(Level.INFO,"Question Description is "+quesDesc);
				sc.next();
				sc.next();
				sc.next();
				datasets = null;//downloadDatasets(Integer.parseInt(quesID.substring(1, quesID.length()-1)),1,conn,filePath, false);

				do{
					String query=sc.next();
					query=query.replaceAll(";", " ").trim();				
					query=query.substring(1,query.length()-1).replaceAll("\n", " ").replaceAll("''","'").trim();
					logger.log(Level.INFO,rollno+") : "+query);
					rollno++;
					/*try{
					PreparedStatement smt;
					Connection conn = (new MyConnection()).getExhistingDatabaseConnection();
					smt=conn.prepareStatement(query);
					smt.executeQuery();	
				}catch(Exception e){					
					e.printStackTrace();
				}*/
					try{
						total++;
						//					logger.log(Level.INFO,"total " + total);
						String res= null;//testAnswer(quesID.substring(1, quesID.length()-1),query,rollno+"",filePath);
						logger.log(Level.INFO,"Result "+res);
						if(res.contains("Failed")){
							incorrect++;
							logger.log(Level.INFO,"incorrect " + incorrect);
						}
						logger.log(Level.INFO,"");
					}catch(Exception e){
						logger.log(Level.SEVERE,"Result Failed",e);
						incorrect++;
						logger.log(Level.SEVERE,"incorrect " + incorrect,e);
						//e.printStackTrace();
					}

					if(sc.hasNext()){
						qID=sc.next();
						quesDesc=sc.next();
					}else{
						break;	//end of file
					}
				}while(qID.length()==0);	
				bfrd.write(quesID + " Total Queries :" + total + " ");
				int correct = total - incorrect;
				bfrd.write(quesID + " Correct Queries :" + correct + " ");
				bfrd.write(quesID + " Incorrect Queries :" + incorrect + "\n");
			}
		}//try block for connection ends

		//conn.close();
	}

	public static void main(String args[])throws Exception{

		int assignmentId = 8;
		int questionId = 14;
		String filePath = "4/A" + assignmentId + "Q" + questionId;
		long startTime = System.currentTimeMillis();
		(new TestAnswer()).test(filePath, assignmentId, questionId, 1,"CS631");
		long endTime   = System.currentTimeMillis();
		long totalTime = endTime - startTime;
		logger.log(Level.INFO,""+totalTime);

	}



	/** 
	 * This method test the student query against DS0 when student submits edited query in 
	 * non-interactive mode
	 * 
	 * @param assignmentId
	 * @param questionId
	 * @param query
	 * @param user
	 * @param filePath
	 * @return
	 * @throws Exception
	 */
	public QueryStatus testQueryAnswerForTestThreads(int assignmentId, int questionId,String course_id, String query, String user, String filePath) throws Exception{


		int queryId = 1;
		String qId = "A"+assignmentId+"Q"+questionId+"S"+queryId;
		Map <Integer,Boolean> resultOfDatasetMatchForEachQuery  = new HashMap<Integer,Boolean>();
		ArrayList <Boolean> isDataSetVerified  = null;
		boolean isMatchAllQueryIncorrect = false;
		String matchAllFailedForDS = "";
		QueryStatus status = QueryStatus.Error;

		boolean isQueryPass = false;
		PopulateTestDataGrading p = new PopulateTestDataGrading();
		boolean orderIndependent = false;
		boolean isMatchAll = false;
		TestAssignment tt = new TestAssignment();
		try(Connection conn = TestAssignment.getDBConnectionToTest()){
			try(Connection testConn = TestAssignment.getTesterConnectionToTest()){

				String getMatchAllOption = "select matchallqueries,orderIndependent from xdata_qinfo where assignment_id = ? and question_id = ? and course_id= ?";
				try(PreparedStatement pst = conn.prepareStatement(getMatchAllOption)){
					pst.setInt(1,assignmentId);
					pst.setInt(2,questionId);
					pst.setString(3,course_id);
					try(ResultSet rset = pst.executeQuery()){
						rset.next();
						isMatchAll = rset.getBoolean("matchallqueries");
						orderIndependent = rset.getBoolean("orderIndependent");
					}
				}
				String qry = "select * from xdata_instructor_query where assignment_id = ? and question_id = ? and course_id=?";
				try(PreparedStatement pstmt = conn.prepareStatement(qry)){
					pstmt.setInt(1,assignmentId);
					pstmt.setInt(2,questionId);
					pstmt.setString(3,course_id);

					try(ResultSet rs = pstmt.executeQuery()){

						while(rs.next()){
							//If Instructor has chosen match All queries option, get all datasets
							// match student query against all and return true only if all ds matches
							String sqlQuery=rs.getString("sql");
							queryId = rs.getInt("query_id");
							qId = "A"+assignmentId +"Q"+questionId+"S"+queryId;
							query=checkForViews(query,user);
							HashMap<String,String> mutants = new HashMap<String,String>();
							mutants.put(qId, query);
							boolean incorrect=false;
							isDataSetVerified = new ArrayList<Boolean>();
							Map <Integer,Vector<String>>  datasetForQueryMap =  downloadDatasets(assignmentId,questionId, queryId,course_id, conn, filePath, true);
							if(datasetForQueryMap.isEmpty()){
								//conn.close();
								//testConn.close();
								//return QueryStatus.NoDataset;
								//Load the default sample data file
								boolean flag=true;
								Vector<String> cmismatch = new Vector<String>();

								try{
									p.deleteAllTempTablesFromTestUser(testConn);
								}catch(Exception e){
									logger.log(Level.INFO,"Temporary Table does not exist",e);

								}
								try{
									p.createTempTableData(conn,testConn,assignmentId,questionId,course_id);
								}catch(Exception e){
									logger.log(Level.SEVERE,"Temp Table creation error. Table already exists",e);
									throw e;
								}
								Vector<String> killedMutants = checkAgainstOriginalQuery(mutants, "DS_Default", sqlQuery,"NoPath", orderIndependent, cmismatch, testConn);
								logger.log(Level.INFO,"******************");
								logger.log(Level.INFO,"Default dataset Loaded : " + " " + killedMutants.size());
								logger.log(Level.INFO,"******************");
								for(int l=0;l<killedMutants.size();l++){
									if(mutants.containsKey(killedMutants.get(l))){
										flag = false;
										incorrect = true;
									}
								}
								if(!incorrect){
									//isDataSetVerified.add(true);
								}	
								else{

									resultOfDatasetMatchForEachQuery.put(queryId,false);
									logger.log(Level.INFO,"");
								}	

								if(!(resultOfDatasetMatchForEachQuery.containsKey(queryId))){
									resultOfDatasetMatchForEachQuery.put(queryId,true);
								}
							}else{
								for(Integer id : datasetForQueryMap.keySet()){
									Vector<String> datasets = datasetForQueryMap.get(id);
									if(datasets.size() == 0){
										conn.close();
										testConn.close();
										return QueryStatus.NoDataset;
									}
									boolean flag=true;
									for(int i=0;i<datasets.size();i++){

										String dsPath = Configuration.homeDir+"/temp_cvc"+filePath+"/"+datasets.get(i);
										File ds=new File(dsPath);
										//GenerateCVC1 c = new GenerateCVC1();
										String copyFiles[] = ds.list();

										Vector<String> vs = new Vector<String>();
										for(int m=0;m<copyFiles.length;m++){
											vs.add(copyFiles[m]);		    
										}

										// query output handling
										GenerateCVC1 cvc = new GenerateCVC1();
										Pattern pattern = Pattern.compile("^A([0-9]+)Q[0-9]+");
										Matcher matcher = pattern.matcher(qId);
										int assignId = 1;

										if (matcher.find()) {
											assignId = Integer.parseInt(matcher.group(1));
										} 

										GenerateDataSet preProcess = new GenerateDataSet();

										cvc.setAssignmentId(assignmentId);
										cvc.setQuestionId(questionId);
										cvc.setQueryId(queryId);
										cvc.setCourseId(course_id);
										preProcess.initializeConnectionDetails(cvc);

										TableMap tm = cvc.getTableMap();
										p.populateTestDataForTesting(vs, filePath+"/"+datasets.get(i), tm, testConn, assignId, questionId);


										Vector<String> cmismatch = new Vector<String>();
										logger.log(Level.INFO,datasets.get(i));


										Vector<String> killedMutants = checkAgainstOriginalQuery(mutants, datasets.get(i), sqlQuery, dsPath, orderIndependent, cmismatch, testConn);
										logger.log(Level.INFO,"******************");
										logger.log(Level.INFO,datasets.get(i) + " " + killedMutants.size());
										logger.log(Level.INFO,"******************");
										for(int l=0;l<killedMutants.size();l++){
											if(mutants.containsKey(killedMutants.get(l))){
												flag = false;
												incorrect = true;
											}
										}
										if(!incorrect){
											//isDataSetVerified.add(true);
										}	
										else{

											resultOfDatasetMatchForEachQuery.put(queryId,false);
											logger.log(Level.INFO,"");
										}
										cvc.closeConn();									
									}

									if(!(resultOfDatasetMatchForEachQuery.containsKey(queryId))){
										resultOfDatasetMatchForEachQuery.put(queryId,true);
									}
								}//For each query
							}//else If dataset exists part ends 
						}//While next ResultSet
					}//close resultset try block
				}//close stmnt try blk
				/*******Check for Match all or match One Option Start******/	
				for(int i : resultOfDatasetMatchForEachQuery.keySet()){
					//Get first query's result as default
					if(i == 1){
						isQueryPass = resultOfDatasetMatchForEachQuery.get(i);
					}//For second query, get the query result
					boolean isCorrect = resultOfDatasetMatchForEachQuery.get(i);
					//If instructor selects match All result sets while creating the question,
					//then all query results should hold true for the student query to pass
					if(isMatchAll){
						isQueryPass = isCorrect && isQueryPass;	
					}
					else{
						//This executes if instructor selects Match any result set option
						//If even one query result holds true, the student query is considered as pass
						if(isCorrect){
							isQueryPass = true;
							break;
						}
					}
				}
				if(isQueryPass){
					status = QueryStatus.Correct;
				}
				else{
					status = QueryStatus.Incorrect;
				}	
				testConn.close();
			}//close connection
			conn.close();
		}//close connection
		return status;
	}



}

