package testDataGen;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.lang.Runtime;
import java.lang.reflect.Type;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;


import com.microsoft.z3.*;
import com.microsoft.z3.FuncInterp.Entry;

import parsing.*;
import util.*;

import generateConstraints.ConstraintGenerator;

class CallableProcess implements Callable {
	private Process p;

	public CallableProcess(Process process) {
		p = process;
	}

	@Override
	public Integer call() throws Exception {
		return p.waitFor();
	}
}

public class PopulateTestData {

	private static Logger logger = Logger.getLogger(PopulateTestData.class.getName());
	
	//Added by Akku
	
	private static Map<String, Integer> Referenced_table_names = new HashMap<String, Integer>();
	
	//Added by Akku ends

	public String getParameterMapping(HashMap<String,Node> paramConstraints, HashMap<String, String> paramMap){

		String retVal = "------------------------\nPARAMETER MAPPING\n------------------------\n";
		Iterator itr = paramConstraints.keySet().iterator();
		retVal += paramMap.toString() + "\n\n";
		while(itr.hasNext()){
			String key = (String)itr.next();
			retVal += "CONSTRAINT: "+paramConstraints.get(key)+"\n";
		}

		return retVal;
	}

	public void captureACPData(String cvcFileName, String filePath, HashMap<String, Node> constraintsWithParams, HashMap<String, String> paramMap) throws Exception{
		String outputFileName = generateCvcOutput(cvcFileName, filePath);
		String copystmt = "";
		BufferedReader input =null;
		try{
			input =  new BufferedReader(new FileReader(Configuration.homeDir+"/temp_smt"+filePath+"/" + outputFileName));
			String line = null; 
			File ACPFile = new File(Configuration.homeDir+"/temp_smt"+filePath+"/" + "PARAMETER_VALUES");
			if(!ACPFile.exists()){
				ACPFile.createNewFile();
			}
			copystmt = getParameterMapping(constraintsWithParams, paramMap);
			copystmt += "\n\n------------------------\nINSTANTIATIONS\n------------------------\n";
			setContents(ACPFile, copystmt+"\n", false);
			while (( line = input.readLine()) != null){
				if(line.contains("ASSERT (PARAM_")){//Output value for a parameterised aggregation
					String par = line.substring(line.indexOf("(PARAM_")+1, line.indexOf('=')-1);
					String val = line.substring(line.indexOf('=')+1,line.indexOf(')'));
					val = val.trim();
					copystmt = par + " = " + val;
					setContents(ACPFile, copystmt+"\n", true);
					//Now update the param map
					Iterator itr = paramMap.keySet().iterator();
					while(itr.hasNext()){
						String key = (String)itr.next();
						if(paramMap.get(key).equalsIgnoreCase(par)){
							paramMap.put(key, val);
						}
					}
				}				
			}
		}catch(Exception e){
			logger.log(Level.SEVERE,"PopulateTestData class - captureACPData Function:  "+e.getStackTrace(),e);
		}

		finally{
			if(input != null)
				input.close();
		}
	}


	public String generateCvcOutput(String cvcFileName, String filePath) throws Exception{
		int ch;
		try{
			//Executing the CVC file generated for given query
			Runtime r = Runtime.getRuntime();

			String command = Configuration.smtsolver + " " + Configuration.homeDir+"/temp_smt"+filePath+"/" + cvcFileName+ " " + Configuration.smtargs;
			
			ExecutorService service = Executors.newSingleThreadExecutor();
			Process myProcess = r.exec(command);	
			try {
				Callable<Integer> call = new CallableProcess(myProcess);
				Future<Integer> future = service.submit(call);
				int exitValue = future.get(5, TimeUnit.SECONDS); // reduced timeout to 5secs from 180secs
				
				if (myProcess.exitValue() != 0) {
					logger.log(Level.SEVERE," GenerateCvcOutput function :  Generating CVC Output failed.");
					myProcess.destroy();	
					service.shutdown();
				}
				
				InputStreamReader myIStreamReader = new InputStreamReader(myProcess.getInputStream());
								//Writing output to .out file
				BufferedWriter out = new BufferedWriter(new FileWriter(Configuration.homeDir+"/temp_smt"+filePath+"/" + cvcFileName.substring(0,cvcFileName.lastIndexOf(".smt")) + ".out"));
                				
				while ((ch = myIStreamReader.read()) != -1) { 
					out.write((char)ch); 
					
				} 	
                
				Utilities.closeProcessStreams(myProcess);

				out.close();

			} catch (ExecutionException e) {

				logger.log(Level.SEVERE,"ExecutionException in generateCvcOutput");
				Utilities.closeProcessStreams(myProcess);
				throw new Exception("Process failed to execute", e);
			} catch (TimeoutException e) {
				logger.log(Level.SEVERE,"TimeOutException in generateCvcOutput");
				Utilities.closeProcessStreams(myProcess);
				myProcess.destroy();		    	
				throw new Exception("Process timed out", e);
			} finally {
				service.shutdown();
			}			

		}catch(Exception e){
			logger.log(Level.SEVERE,e.getMessage(),e);
			//e.printStackTrace();
			throw new Exception("Process interrupted or timed out.", e);
		}

		return cvcFileName.substring(0,cvcFileName.lastIndexOf(".smt")) + ".out";
	}

	public String cutRequiredOutput(String cvcOutputFileName, String filePath){
		try {
			BufferedWriter out = new BufferedWriter(new FileWriter(Configuration.homeDir+"/temp_smt"+filePath+"/cut_" + cvcOutputFileName));
			out.close();
			File testFile = new File(Configuration.homeDir+"/temp_smt"+filePath+"/cut_" + cvcOutputFileName);
			BufferedReader input =  new BufferedReader(new FileReader(Configuration.homeDir+"/temp_smt"+filePath+"/" + cvcOutputFileName));
			try {
				String line = null; 
				while (( line = input.readLine()) != null){
					if(line.contains("ASSERT (O_") && line.contains("] = (") && !line.contains("THEN")){
						setContents(testFile, line+"\n", true);
					}
					//Application Testing - to include value of program parameters
					if(line.startsWith("ASSERT (parameter") || line.startsWith("ASSERT (PARAM_")){
						setContents(testFile, line+"\n", true);
					}
				}
			}catch(Exception e){
				logger.log(Level.SEVERE,"PopulateTestData-cutRequiredOutput :  "+e.getStackTrace(),e);
			}
			finally {
				input.close();
			}
		}
		catch (IOException ex){
			logger.log(Level.SEVERE,"PopulateTestData-cuteRequiredOutput :  "+ex.getMessage(),ex);
			//ex.printStackTrace();
		}
		return "cut_"+cvcOutputFileName;
	}


	public String cutRequiredOutputForSMT(String cvcOutputFileName, String filePath){
		try {
			BufferedWriter out = new BufferedWriter(new FileWriter(Configuration.homeDir+"/temp_smt"+filePath+"/cut_" + cvcOutputFileName));
			out.close();
			File testFile = new File(Configuration.homeDir+"/temp_smt"+filePath+"/cut_" + cvcOutputFileName);
			BufferedReader input =  new BufferedReader(new FileReader(Configuration.homeDir+"/temp_smt"+filePath+"/" + cvcOutputFileName));
			try {
                String line = null; 
                while ((line = input.readLine()) != null) {
                	
                    if(line.contains("(define-fun ")  && line.contains("_TupleType")) {
                        //setContents(testFile,line, true);
                        while ((line = input.readLine()) != null) {
                            if(!line.contains("(ite") && !line.contains("!")) {
                                    if(line.contains("define-fun")) {
                                        //input.mark(1000);
                                        input.reset();
                                        break;
                                    } else if (line.startsWith(")")) {
                                        break;
                                    }
                                    // setContents(testFile,line+"\n", true);
                                    setContents(testFile,line, true); // TEMPCODE : Rahul Sharma : Removed "\n" from previous statement
                                    
                            }
                            setContents(testFile,"\n",true); // TEMPCODE : Rahul Sharma
                            input.mark(100);
                        }
                        //if(!line.contains("!"))
                        //setContents(testFile,line+"\n", true);
                    }
                    //Application Testing - to include value of program parameters
                    if(line.startsWith("ASSERT (parameter")){
                        setContents(testFile, line+"\n", true);
                    }
                }
            } catch (Exception e) {
				logger.log(Level.SEVERE,"PopulateTestData-cutRequiredOutput :  "+e.getStackTrace(),e);
			}
			finally {
				input.close();
			}
		}
		catch (IOException ex){
			logger.log(Level.SEVERE,"PopulateTestData-cuteRequiredOutput :  "+ex.getMessage(),ex);
			//ex.printStackTrace();
		}
		return "cut_"+cvcOutputFileName;
	}
///************** API CODE ******************/

	public String cutRequiredOutputForSMTWithAPI(String SMTFileName, String filePath,TableMap tableMap) throws Exception {
		
		String sqlFileName = "DS" + SMTFileName.substring(3, SMTFileName.lastIndexOf(".smt")) + ".sql";
		
		String cutstr="",sqlOutput="";
		
		Context ctx = ConstraintGenerator.ctx;
		Params p = ctx.mkParams();
		p.add("smt.macro_finder", true);
		p.add("model.compact", false);

		Solver s = ctx.mkSolver();
		s.setParameters(p);

		// Read the entire SMT file as string and add model_compress param (adding it in file causes errors)
		File smtFile = new File(Configuration.homeDir+"/temp_smt"+filePath+"/" + SMTFileName);
		FileInputStream fis = new FileInputStream(smtFile);
		byte[] data = new byte[(int) smtFile.length()];
		fis.read(data);
		fis.close();

		String smtStr = new String(data, "UTF-8");

		BoolExpr[] exprs = ctx.parseSMTLIB2String(smtStr, null, null, null, null);
		s.add(exprs);
		
		if (s.check() != Status.SATISFIABLE) {
			//throw Exception() TODO
			return sqlFileName;
		}

		Model m = s.getModel();
		
		for (FuncDecl decl : m.getFuncDecls()) {
			if (decl.getRange().getSExpr().endsWith("_TupleType")) {
				FuncInterp interp = m.getFuncInterp(decl);
				if (interp.getNumEntries() == 0) 
					continue;
				
				for (Entry ent : interp.getEntries()) 
					cutstr+=ent.getValue().getSExpr();

				if(!cutstr.isEmpty()) 
					cutstr=cleanseContents(cutstr);//cleanse
			}
		}
		 
		sqlOutput=requiredSqlOutput(cutstr,tableMap);
		//sqlOutput = removeRepeatedqueries(sqlOutput);
		String sqlFileFullPath = Configuration.homeDir+"/temp_smt"+filePath+"/" + sqlFileName;
		FileWriter file = new FileWriter (sqlFileFullPath);
		file.write(sqlOutput);
		file.close();
		
		//Connection testCon=getTestConn();
		//loadDataset(testCon, sqlFileName, Configuration.homeDir+"/temp_smt"+filePath);
		
		return sqlFileName;
		
	}
	
//**********************************************************************************************************/	
	
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

    public String removeRepeatedqueries(String sqlOutput) {
    	
    	String[] tokens = sqlOutput.split("\n");
    	StringBuilder resultBuilder = new StringBuilder();
    	Set<String> alreadyPresent = new HashSet<String>();

    	boolean first = true;
    	for(String token : tokens) {
    	    if(!alreadyPresent.contains(token)) {
    	        if(first) first = false;
    	        else resultBuilder.append("\n");

    	        if(!alreadyPresent.contains(token))
    	            resultBuilder.append(token);
    	    }
    	    alreadyPresent.add(token);
    	}
    	sqlOutput = resultBuilder.toString();
    	
    	return sqlOutput;
    }
	
	public String requiredSqlOutput(String cutstr,TableMap tableMap) throws Exception {
		
		String copystmt="",insertQuery="", extraTables=""; 
		Map<String,List<String>> queryMap=new HashMap<String,List<String>>();
		String lines[]=cutstr.split("\\r?\\n");
		for(int j=0; j<lines.length;j++)
		{
			if(lines[j].equals(")"))
				continue;
			try {
				String tableName = lines[j].substring(lines[j].indexOf("(")+1,lines[j].indexOf("TupleType")-1);
				String tempData = lines[j].substring(lines[j].indexOf("_TupleType ")+11,lines[j].indexOf(")"));
				tempData=cleanseCopyString(tempData);
				String[] copyTemp=tempData.split("\\ ");

				Table t=tableMap.getTable(tableName.toUpperCase());
				if(t != null) {
					for(int i=0;i<copyTemp.length;i++){
						insertQuery = "";
						String cvcDataType=t.getColumn(i).getCvcDatatype();
						if(cvcDataType.equalsIgnoreCase("INT") )
							continue;
						else if(cvcDataType.equalsIgnoreCase("REAL")){
							/*
							 * Sometimes Solver assigns values in the format (/ x y)
							 *  "(/" is at ith index, x at i+1 and y at i+2 
							 */
							if(copyTemp[i].equals("(/")) {
								double num=Double.parseDouble(copyTemp[i+1]); // x
								double den=Double.parseDouble(copyTemp[i+2]); // y
								copyTemp[i]=(num/den)+"";
								copyTemp[i+1] = "";
								copyTemp[i+2] = "";
								i+=2; //
							}
							else {
								String str[]=copyTemp[i].trim().split("/");
								if(str.length==1)
									continue;
							}


						}
						else if(cvcDataType.equalsIgnoreCase("TIMESTAMP")){
							long l=Long.parseLong(copyTemp[i].trim())*1000;
							java.sql.Timestamp timeStamp=new java.sql.Timestamp(l);
							copyTemp[i]=timeStamp.toString();
						}
						else if(cvcDataType.equalsIgnoreCase("TIME")){

							int time=Integer.parseInt(copyTemp[i].trim());
							int sec=time%60;
							int min=((time-sec)/60)%60;
							int hr=(time-sec+min*60)/3600;
							copyTemp[i]=hr+":"+min+":"+sec;
						}
						else if(cvcDataType.equalsIgnoreCase("DATE")){
							long l=Long.parseLong(copyTemp[i].trim())*86400000;

							java.sql.Date date=new java.sql.Date(l);
							copyTemp[i]=date.toString();

						}
						else {

							String copyStr=copyTemp[i].trim();

							if(copyStr.endsWith("__"))
								copyStr = "";
							else if(copyStr.contains("__"))
								copyStr = copyStr.split("__")[1];

							copyStr = copyStr.replace("_p", "%");
							copyStr = copyStr.replace("_s", "+");
							copyStr = copyStr.replace("_d", ".");
							copyStr = copyStr.replace("_m", "-");
							copyStr = copyStr.replace("_s", "*");
							copyStr = copyStr.replace("_u", "_");
							copyStr = URLDecoder.decode(copyStr,"UTF-8");
							copyTemp[i]=copyStr.replace("_b", " ");
						}
					}
					for(String k:copyTemp){
						if(!k.isEmpty())
							copystmt+= (k.startsWith("(") ? null : "'"+k+"'")+",";
					}
					copystmt = copystmt.replaceAll("'null'","null");

					copystmt=copystmt.substring(0, copystmt.length()-1);
					insertQuery += "insert into "+tableName+" values "+"("+copystmt+")\n"; 
					copystmt="";			

					//hashmap
					String key = tableName;
					if(queryMap.containsKey(key)) {
						queryMap.get(key).add(insertQuery);
					}
					else {
						List<String> queryList=new ArrayList<String>(); ;
						queryList.add(insertQuery);
						queryMap.put(key, queryList);
					}
				}
				else {
					extraTables += "-- "+lines[j]+"\n"; 
				}
			}
			catch(Exception e) {
				logger.log(Level.SEVERE,"PopulateTestData-requiredSqlOutput :  "+e.getStackTrace(),e);
			}
		}
		
		return generateSqlFile(queryMap,tableMap,extraTables);
}
	public String generateSqlFile(Map<String,List<String>> queryMap, TableMap tableMap, String extraTables) {
		
		/**Delete existing entries in Temp tables **/
		String finalQueries="";
		int size = tableMap.foreignKeyGraph.topSort().size();
		for (int fg=(size-1);fg>=0;fg--){
			String tableName = tableMap.foreignKeyGraph.topSort().get(fg).toString();
			finalQueries +="delete from "+tableName+"\n";
		}
		//Vector<String> addedTables = new Vector<String>();
		Vector<String> queryList = new Vector<String>();
		
		//This part helps in identifying the order of foreign key dependence and helps in
		//populating the data accordingly.
		for(int f=0;f<tableMap.foreignKeyGraph.topSort().size();f++){
			String tableName = tableMap.foreignKeyGraph.topSort().get(f).toString();
			String tName="";

			if(queryMap.containsKey(tableName) ){
				//addedTables.add(tableName);
				//System.out.println(queryMap.get(tableName));	
				for(String insert:queryMap.get(tableName)) {
						//if(!queryList.contains(insert)) {
							finalQueries += insert ;
							queryList.add(insert);
							//System.out.println(insert);
						//}
					}
			}
		}
		queryMap.clear();
//		for(Map.Entry<String, List<String>> entry: queryMap.entrySet()) {
//			if(!addedTables.contains(entry.getKey())) {
//				addedTables.add(entry.getKey());
//				for(String insert: entry.getValue()) {
//					if(!queryList.contains(insert)) {
//						finalQueries += insert ;
//						queryList.add(insert);
//					}				}
//			}
//		}
		
		finalQueries += extraTables;
		
		return finalQueries;
	}
	
	public String cleanseContents(String cutstr) {
		cutstr = cutstr.replaceAll("\\s", " "); // extra tabs and all new lines
		cutstr = cutstr.replace(")", ")\n");    // required new lines 
		cutstr = cutstr.replaceAll(" +", " ");  // extra spaces
		cutstr = cutstr.replaceAll("\n ", "\n");
		cutstr = cutstr.replaceAll("\\- 9999[6789].0", "null");
		return cutstr;

	}
	
	public void setContents(File aFile, String aContents, boolean append)throws FileNotFoundException, IOException {
		if (aFile == null) {
			logger.log(Level.WARNING,"PopulateTesData.setContents : File is null");
			throw new IllegalArgumentException("File should not be null.");
		}
		if (!aFile.exists()) {
			logger.log(Level.WARNING,"PopulateTesData.setContents : File does not exists");

			throw new FileNotFoundException ("File does not exist: " + aFile);
		}
		if (!aFile.isFile()) {
			logger.log(Level.WARNING,"PopulateTesData.setContents : Should not be a directory");

			throw new IllegalArgumentException("Should not be a directory: " + aFile);
		}
		if (!aFile.canWrite()) {
			logger.log(Level.WARNING,"PopulateTesData.setContents : File Cannot be written");

			throw new IllegalArgumentException("File cannot be written: " + aFile);
		}

		Writer output = new BufferedWriter(new FileWriter(aFile,append));
		try {
			output.write( aContents );
		}
		catch(Exception e){
			logger.log(Level.SEVERE,"PopulateTestData.setContents(): "+e.getStackTrace(),e);
		}
		finally {
			output.flush();
			output.close();
		}
	}

	//Modified by Bhupesh
	public Vector<String> generateCopyFile (String cut_cvcOutputFileName, String filePath, 
			HashMap<String, Integer> noOfOutputTuples, TableMap tableMap,Vector<Column> columns,
			Set existingTableNames, AppTest_Parameters dbAppParameters) throws Exception {
		Vector<String> listOfCopyFiles = new Vector();
		List <String> copyFileContents = new ArrayList<String>(); 
		String currentCopyFileName = "";
		File testFile = null;
		BufferedReader input = null;
		try{
			input =  new BufferedReader(new FileReader(Configuration.homeDir+"/temp_smt"+filePath+"/" + cut_cvcOutputFileName));
			String line = null,copystmt=null; 
			while (( line = input.readLine()) != null){
				//Application Testing -- added check for the presence of parameter
				if(line.startsWith("ASSERT (parameter") || line.startsWith("ASSERT (PARAM_")){

					currentCopyFileName = line.substring(line.indexOf("(")+1,line.indexOf("=")-1);
					testFile = new File(Configuration.homeDir+"/temp_smt"+filePath+"/" + currentCopyFileName + ".copy");
					if(!testFile.exists() || !listOfCopyFiles.contains(currentCopyFileName + ".copy")){
						if(testFile.exists()){
							testFile.delete();
						}
						testFile.createNewFile();
						listOfCopyFiles.add(currentCopyFileName + ".copy");
					}
					copystmt = line.substring(line.indexOf("=")+2,line.indexOf(")"));

					HashMap<String,String> param_Datatype_Map = dbAppParameters.getParameters_Datatype_Copy();
					String param_type="";
					if(param_Datatype_Map.containsKey(currentCopyFileName))
						param_type= param_Datatype_Map.get(currentCopyFileName);
					if(param_type.toLowerCase().contains("string")){
						if(copystmt.endsWith("__"))
							copystmt = "";
						else if(copystmt.contains("__"))
							copystmt = copystmt.split("__")[1];						

						copystmt = copystmt.replace("_p", "%");
						copystmt = copystmt.replace("_s", "+");
						copystmt = copystmt.replace("_d", ".");
						copystmt = copystmt.replace("_m", "-");
						copystmt = copystmt.replace("_s", "*");
						copystmt = copystmt.replace("_u", "_");
						copystmt = URLDecoder.decode(copystmt,"UTF-8");
						copystmt=copystmt.replace("_b", " ");
					}
					setContents(testFile, copystmt+"\n", true);

				}
				else{
					String tableName = line.substring(line.indexOf("_")+1,line.indexOf("["));
					if(!noOfOutputTuples.containsKey(tableName.toUpperCase()) && !noOfOutputTuples.containsKey(tableName.toLowerCase())){
						continue;
					}
					int index = Integer.parseInt(line.substring(line.indexOf('[')+1, line.indexOf(']')));
					/*
					 * Temp Code: Pooja
					 */
					int numOpTuples = noOfOutputTuples.containsKey(tableName.toLowerCase()) ? noOfOutputTuples.get(tableName.toLowerCase()) : noOfOutputTuples.get(tableName.toUpperCase() ) ;
					
					if((index > numOpTuples) || (index <= 0)){
						continue;
					}
					currentCopyFileName = line.substring(line.indexOf("_")+1,line.indexOf("["));
					//Shree added to show 'tables in query' and 'reference tables' separately
					if( !(existingTableNames.contains(currentCopyFileName.toUpperCase()))){
						//If table name is not in existingTablename Set, it means it is a reference Table
						currentCopyFileName = currentCopyFileName+".ref";
					}
					testFile = new File(Configuration.homeDir+"/temp_smt"+filePath+"/" + currentCopyFileName + ".copy");
					if(!testFile.exists() || !listOfCopyFiles.contains(currentCopyFileName + ".copy")){
						if(testFile.exists()){
							testFile.delete();
						}
						testFile.createNewFile();
						listOfCopyFiles.add(currentCopyFileName + ".copy");
					}
					copystmt = getCopyStmtFromCvcOutput(line);

					copyFileContents.add(copystmt);
					//Putting back string values in CVC

					//Table t=tableMap.getTable(tableName);
					Table t=tableMap.getTable(tableName.toUpperCase()); // added by rambabu

					String[] copyTemp=copystmt.split("\\|");
					copystmt="";
					String out="";

					for(int i=0;i<copyTemp.length;i++){

						String cvcDataType=t.getColumn(i).getCvcDatatype();
						if(cvcDataType.equalsIgnoreCase("INT") )
							continue;
						else if(cvcDataType.equalsIgnoreCase("REAL")){
							String str[]=copyTemp[i].trim().split("/");
							if(str.length==1)
								continue;
							double num=Integer.parseInt(str[0]);
							double den=Integer.parseInt(str[1]);
							copyTemp[i]=(num/den)+"";
						}
						else if(cvcDataType.equalsIgnoreCase("TIMESTAMP")){
							long l=Long.parseLong(copyTemp[i].trim())*1000;
							java.sql.Timestamp timeStamp=new java.sql.Timestamp(l);
							copyTemp[i]=timeStamp.toString();
						}
						else if(cvcDataType.equalsIgnoreCase("TIME")){

							int time=Integer.parseInt(copyTemp[i].trim());
							int sec=time%60;
							int min=((time-sec)/60)%60;
							int hr=(time-sec+min*60)/3600;
							copyTemp[i]=hr+":"+min+":"+sec;
						}
						else if(cvcDataType.equalsIgnoreCase("DATE")){
							long l=Long.parseLong(copyTemp[i].trim())*86400000;

							java.sql.Date date=new java.sql.Date(l);
							copyTemp[i]=date.toString();

						}
						else {

							String copyStr=copyTemp[i].trim();


							if(copyStr.endsWith("__"))
								copyStr = "";
							else if(copyStr.contains("__"))
								copyStr = copyStr.split("__")[1];


							/*&copyStr = copyStr.replace("_p", "+");
							copyStr = copyStr.replace("_m", "-");
							copyStr = copyStr.replace("_a", "&");
							copyStr = copyStr.replace("_s", " ");
							copyStr = copyStr.replace("_d", ".");
							copyStr = copyStr.replace("_c", ",");
							copyStr = copyStr.replace("_u", "_");*/

							copyStr = copyStr.replace("_p", "%");
							copyStr = copyStr.replace("_s", "+");
							copyStr = copyStr.replace("_d", ".");
							copyStr = copyStr.replace("_m", "-");
							copyStr = copyStr.replace("_s", "*");
							copyStr = copyStr.replace("_u", "_");
							copyStr = URLDecoder.decode(copyStr,"UTF-8");
							copyTemp[i]=copyStr.replace("_b", " ");

						}


					}
					for(String s:copyTemp){
						copystmt+=s+"|";
					}
					copystmt=copystmt.substring(0, copystmt.length()-1);



					setContents(testFile, copystmt+"\n", true);
				}
			}
		}catch(Exception e){
			logger.log(Level.SEVERE,"PopulateTestData.generateCopyFile() : "+e.getStackTrace(),e);
		}
		finally{
			if(input != null)
				input.close();
		}
		return listOfCopyFiles;
	}

	/**
	 * Gets output for each table in .copy file
	 * 
	 * @param cut_cvcOutputFileName
	 * @param filePath
	 * @param noOfOutputTuples
	 * @param tableMap
	 * @param columns
	 * @param existingTableNames
	 * @param dbAppParameters
	 * @return
	 * @throws Exception
	 */
	public Vector<String> generateCopyFileForSMT (String cut_cvcOutputFileName, String filePath, 
			HashMap<String, Integer> noOfOutputTuples, TableMap tableMap,Vector<Column> columns,
			Set existingTableNames, AppTest_Parameters dbAppParameters) throws Exception {
		Vector<String> listOfCopyFiles = new Vector();
		List <String> copyFileContents = new ArrayList<String>(); 
		String currentCopyFileName = "";
		File testFile = null;
		BufferedReader input = null;
		try{
			input =  new BufferedReader(new FileReader(Configuration.homeDir+"/temp_smt"+filePath+"/" + cut_cvcOutputFileName));
			String line = null,copystmt=null; 
			while (( line = input.readLine()) != null){
				//Application Testing -- added check for the presence of parameter
				if(line.startsWith("ASSERT (parameter")){

					currentCopyFileName = line.substring(line.indexOf("(")+1,line.indexOf("=")-1);
					testFile = new File(Configuration.homeDir+"/temp_smt"+filePath+"/" + currentCopyFileName + ".copy");
					if(!testFile.exists() || !listOfCopyFiles.contains(currentCopyFileName + ".copy")){
						if(testFile.exists()){
							testFile.delete();
						}
						testFile.createNewFile();
						listOfCopyFiles.add(currentCopyFileName + ".copy");
					}
					copystmt = line.substring(line.indexOf("=")+2,line.indexOf(")"));

					HashMap<String,String> param_Datatype_Map = dbAppParameters.getParameters_Datatype_Copy();
					String param_type="";
					if(param_Datatype_Map.containsKey(currentCopyFileName))
						param_type= param_Datatype_Map.get(currentCopyFileName);
					if(param_type.toLowerCase().contains("string")){
						if(copystmt.endsWith("__"))
							copystmt = "";
						else if(copystmt.contains("__"))
							copystmt = copystmt.split("__")[1];						

						copystmt = copystmt.replace("_p", "%");
						copystmt = copystmt.replace("_s", "+");
						copystmt = copystmt.replace("_d", ".");
						copystmt = copystmt.replace("_m", "-");
						copystmt = copystmt.replace("_s", "*");
						copystmt = copystmt.replace("_u", "_");
						copystmt = URLDecoder.decode(copystmt,"UTF-8");
						copystmt=copystmt.replace("_b", " ");
					}
					setContents(testFile, copystmt+"\n", true);

				}
				else{
					//String tableName = line.substring(line.indexOf("O_")+2,line.indexOf(" (store ("));
					if(line.contains("(") && line.contains("_TupleType")) {
						String tableName = line.substring(line.indexOf("(")+1,line.indexOf("_TupleType"));
						if(!noOfOutputTuples.containsKey(tableName.toUpperCase()) && !noOfOutputTuples.containsKey(tableName.toLowerCase())){
							continue;
						}
						
						//int index = Integer.parseInt(line.substring(line.indexOf('[')+1, line.indexOf(']')));
						//if((index > noOfOutputTuples.get(tableName)) || (index <= 0)){
						//	continue;
						//}
						currentCopyFileName = tableName;//line.substring(line.indexOf("_")+1,line.indexOf("["));
						//Shree added to show 'tables in query' and 'reference tables' separately
						if( !(existingTableNames.contains(currentCopyFileName.toUpperCase()))){
							//If table name is not in existingTablename Set, it means it is a reference Table
							currentCopyFileName = currentCopyFileName+".ref";
						}
						testFile = new File(Configuration.homeDir+"/temp_smt"+filePath+"/" + currentCopyFileName + ".copy");
						if(!testFile.exists() || !listOfCopyFiles.contains(currentCopyFileName + ".copy")){
							if(testFile.exists()){
								testFile.delete();
							}
							testFile.createNewFile();
							listOfCopyFiles.add(currentCopyFileName + ".copy");
						}
						copystmt = getCopyStmtFromCvcOutputForSMT(line);
						copyFileContents.add(copystmt);
						////Putting back string values in CVC
	
						//Table t=tableMap.getTable(tableName);
						Table t=tableMap.getTable(tableName.toUpperCase());//added by rambabu
						String[] copyvalues = copystmt.split("\n");
						
						for(int k=0; k<copyvalues.length; k++){
							String[] copyTemp=copyvalues[k].split("\\ ");
							copystmt="";
								
							for(int i=0;i<copyTemp.length;i++){
	
								String cvcDataType=t.getColumn(i).getCvcDatatype();
								if(cvcDataType.equalsIgnoreCase("INT") )
									continue;
								else if(cvcDataType.equalsIgnoreCase("REAL")){
									/*
									 * Sometimes Solver assigns values in the format (/ x y)
									 *  "(/" is at ith index, x at i+1 and y at i+2 
									 */
									if(copyTemp[i].equals("(/")) {
										double num=Double.parseDouble(copyTemp[i+1]); // x
										double den=Double.parseDouble(copyTemp[i+2]); // y
										copyTemp[i]=(num/den)+"";
										copyTemp[i+1] = "";
										copyTemp[i+2] = "";
										i+=2; //
									}
									else {
										String str[]=copyTemp[i].trim().split("/");
										if(str.length==1)
											continue;
									}
									
									
								}
								else if(cvcDataType.equalsIgnoreCase("TIMESTAMP")){
									long l=Long.parseLong(copyTemp[i].trim())*1000;
									java.sql.Timestamp timeStamp=new java.sql.Timestamp(l);
									copyTemp[i]=timeStamp.toString();
								}
								else if(cvcDataType.equalsIgnoreCase("TIME")){
	
									int time=Integer.parseInt(copyTemp[i].trim());
									int sec=time%60;
									int min=((time-sec)/60)%60;
									int hr=(time-sec+min*60)/3600;
									copyTemp[i]=hr+":"+min+":"+sec;
								}
								else if(cvcDataType.equalsIgnoreCase("DATE")){
									long l = Long.parseLong(copyTemp[i].trim())*86400000;
	
									java.sql.Date date=new java.sql.Date(l);
									copyTemp[i]=date.toString();
	
								}
								else {
	
									String copyStr=copyTemp[i].trim();
									
									if(copyStr.endsWith("__"))
										copyStr = "";
									else if(copyStr.contains("__"))
										copyStr = copyStr.split("__")[1];
										
	
							/*&copyStr = copyStr.replace("_p", "+");
							copyStr = copyStr.replace("_m", "-");
							copyStr = copyStr.replace("_a", "&");
							copyStr = copyStr.replace("_s", " ");
							copyStr = copyStr.replace("_d", ".");
							copyStr = copyStr.replace("_c", ",");
							copyStr = copyStr.replace("_u", "_");*/
	
									copyStr = copyStr.replace("_p", "%");
									copyStr = copyStr.replace("_s", "+");
									copyStr = copyStr.replace("_d", ".");
									copyStr = copyStr.replace("_m", "-");
									copyStr = copyStr.replace("_s", "*");
									copyStr = copyStr.replace("_u", "_");
									copyStr = URLDecoder.decode(copyStr,"UTF-8");
									copyTemp[i]=copyStr.replace("_b", " ");
	
								}
	
	
							}
							for(String s:copyTemp){
								if(!s.isEmpty())
									//copystmt+= (s.startsWith("-") ? null : s)+"|";
									copystmt+= ((s.startsWith("-")||s.startsWith("(")) ? null : s)+"|";
							}
							copystmt=copystmt.substring(0, copystmt.length()-1);
							
							setContents(testFile, copystmt+"\n", true);
						}
	
	
					}
				}
			}
			
		}catch(Exception e){
			logger.log(Level.SEVERE,"PopulateTestData.generateCopyFile() : "+e.getStackTrace(),e);
		}
		finally{
			if(input != null)
				input.close();
		}
		return listOfCopyFiles;
	}
	
//	public String generateSqlFile(String cutString)
//	{
//		
//	}


	public String getCopyStmtFromCvcOutput(String cvcOutputLine){
		String queryString = "";
		String tableName = cvcOutputLine.substring(cvcOutputLine.indexOf("_")+1,cvcOutputLine.indexOf("["));
		String temp = cvcOutputLine.substring(cvcOutputLine.indexOf("(")+1);
		String insertTupleValues = temp.substring(temp.indexOf("(")+1,temp.indexOf(")"));
		insertTupleValues = cleanseCopyString(insertTupleValues);		
		return insertTupleValues;
	}

	public String getCopyStmtFromCvcOutputForSMT(String cvcOutputLine){
		String queryString = "";
		//String tableName = cvcOutputLine.substring(cvcOutputLine.indexOf("_")+1,cvcOutputLine.indexOf("["));
		//String tableName = cvcOutputLine.substring(cvcOutputLine.indexOf("O_")+2,cvcOutputLine.indexOf(" (store ("));
		String tableName = cvcOutputLine.substring(cvcOutputLine.indexOf("(")+1,cvcOutputLine.indexOf("_"));
		String temp = cvcOutputLine.substring(cvcOutputLine.indexOf("_TupleType ")+11);
		
		/* Test Code: Pooja */
		//String insertTupleValues = temp.substring(temp.indexOf("_"),temp.indexOf(")"));
		String insertTupleValues = temp.startsWith("_")? temp.substring(temp.indexOf("_"),temp.indexOf(")")) : temp.substring(0,temp.indexOf(")"));
		
		insertTupleValues = cleanseCopyString(insertTupleValues);
		insertTupleValues = insertTupleValues.trim().replaceAll(" +", " ");
		/*
		String temp1 = cvcOutputLine.substring((cvcOutputLine.indexOf(" 1 (")+2), 
				cvcOutputLine.lastIndexOf("))))"));
		//String temp = temp1.substring((temp1.indexOf("_TupleType) ("))+13, temp1.lastIndexOf("("));
		String insertTupleValues = "";
		if(temp1.contains("))")){
			String[] temp = temp1.split("[)]+ [0-9]+ [(]+");

			for(int i=0;i<temp.length; i++){
				if(temp[i].contains("))")){
					insertTupleValues += cleanseCopyString(temp[i].substring((temp[i].indexOf("_TupleType")+11),(temp[i].indexOf("))"))) )+"\n" ;
				}else{
					insertTupleValues += cleanseCopyString(temp[i].substring((temp[i].indexOf("_TupleType")+11),(temp[i].length())) )+"\n" ;
				}
			}
		}
		else if(temp1.startsWith("(") && temp1.contains("_TupleType") && !temp1.contains("))")){
			insertTupleValues += cleanseCopyString(temp1.substring((temp1.indexOf("_TupleType")+11),(temp1.length())) )+"\n" ;
		}else{
			insertTupleValues += cleanseCopyString(temp1.substring((temp1.indexOf("_TupleType")+11),temp1.length()))+"\n" ;
		}
		//String insertTupleValues = temp.substring((temp.indexOf("_TupleType"))+11,temp.lastIndexOf(")) "));
		//insertTupleValues = cleanseCopyStringSMT(insertTupleValues);		
		 * 
		 */
		return insertTupleValues;
	}

	public String cleanseCopyString(String copyStr){


		copyStr = copyStr.replaceAll("\\b_", "");
		copyStr = copyStr.replaceAll("\\bNULL_\\w+", "null");
		copyStr = copyStr.replaceAll("\\-9999[6789]", "null");
		copyStr = copyStr.replaceAll("\\- 9999[6789].0", "null");
		copyStr = copyStr.replace(",", "|");
		if(copyStr.contains("(- ")){
			copyStr = copyStr.replace("(- ", "-");
			copyStr = copyStr.replace(")", "");
		}

		return copyStr;
	}
	
	


	/**
	 * Executes CVC3 Constraints specified in the file "cvcOutputFileName"
	 * Stores the data set values inside the directory "datasetName"
	 * @param cvcOutputFileName
	 * @param query
	 * @param datasetName
	 * @param queryString
	 * @param filePath
	 * @param noOfOutputTuples
	 * @param tableMap
	 * @param columns
	 * @return
	 * @throws Exception 
	 */
	public boolean killedMutants(String cvcOutputFileName, Query query, String datasetName, String queryString, String filePath, 
			HashMap<String, Integer> noOfOutputTuples, TableMap tableMap,Vector<Column> columns, Set existingTableNames, AppTest_Parameters dbAppParameters) throws Exception{
		String temp=""; 
		Process proc=null;
		boolean returnVal=false;
		String test = generateCvcOutput(cvcOutputFileName, filePath);
		BufferedReader br =  new BufferedReader(new FileReader(Configuration.homeDir+"/temp_smt"+filePath+"/"+test));
		String str = br.readLine();
		br.close();
		if((str == null || str.equals("") || str.equalsIgnoreCase("Valid."))) {
			return false;
		}
		String cutFile = cutRequiredOutput(test, filePath);
		
		Vector<String> listOfCopyFiles = generateCopyFile(cutFile, filePath, noOfOutputTuples, 
				tableMap,columns,existingTableNames, dbAppParameters);			
		Vector<String> listOfFiles = (Vector<String>) listOfCopyFiles.clone();

		File datasetDir = new File(Configuration.homeDir+"/temp_smt"+filePath+"/"+datasetName);
		boolean created = datasetDir.mkdirs();
		if(!created) {
			logger.log(Level.WARNING, "Could not create directory for dataset: "+datasetDir.getPath());
		}

		for(String i:listOfCopyFiles){				
			File src = new File(Configuration.homeDir+"/temp_smt"+filePath+"/"+i);
			File dest = new File(Configuration.homeDir+"/temp_smt"+filePath+"/"+datasetName+"/"+i);
			Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);

		}	
		//Connection conn = MyConnection.getTestDatabaseConnection();
		//try(Connection conn = (new DatabaseConnection().getTesterConnection(assignmentId)).getTesterConn()){
		//	populateTestDataForTesting(listOfCopyFiles, filePath, tableMap,conn, assignmentId, questionId);

		for(String i : listOfFiles){				
			File src = new File(Configuration.homeDir+"/temp_smt"+filePath+"/"+i);
			File dest = new File(Configuration.homeDir+"/temp_smt"+filePath+"/"+datasetName+"/"+i);
			Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}

		returnVal=true;

		/*}catch(Exception e){
			logger.log(Level.SEVERE,e.getMessage(),e);
			//e.printStackTrace();
			throw new Exception("Process exited", e);
		} finally {

			if (proc!=null)
				Utilities.closeProcessStreams(proc);
		}*/
		return returnVal;			
	}

	public void deleteAllTempTablesFromTestUser(Connection dbConn) throws Exception{
		Statement st = dbConn.createStatement();
		st = dbConn.createStatement();
		st.executeUpdate("DISCARD TEMPORARY");
		st.close();
	}


	/**
	 * Executes CVC3 Constraints specified in the file "cvcOutputFileName"
	 * Stores the data set values inside the directory "datasetName"
	 * @param cvcOutputFileName
	 * @param query
	 * @param datasetName
	 * @param queryString
	 * @param filePath
	 * @param noOfOutputTuples
	 * @param tableMap
	 * @param columns
	 * @return
	 * @throws Exception 
	 */
	public boolean killedMutantsForSMT(String cvcOutputFileName, Query query, String datasetName, String queryString, String filePath, 
			HashMap<String, Integer> noOfOutputTuples, TableMap tableMap,Vector<Column> columns, Set existingTableNames, AppTest_Parameters dbAppParameters) throws Exception{
		String temp=""; 
		Process proc=null;
		boolean returnVal=false;
		String test = generateCvcOutput(cvcOutputFileName, filePath);
		BufferedReader br =  new BufferedReader(new FileReader(Configuration.homeDir+"/temp_smt"+filePath+"/"+test));
		String str = br.readLine();
		br.close();

		if((str == null || str.equals("") || (str.equalsIgnoreCase("unsupported") && (br.readLine().equalsIgnoreCase("unsat"))))) {
			return false;
		}

		String cutFile = cutRequiredOutputForSMT(test, filePath);
		
		/************ TESTING API CODE *************/
		String apiCutFile = cutRequiredOutputForSMTWithAPI(cvcOutputFileName, filePath,tableMap);
		//System.out.println(apiCutFile);
		
		
		/**************************************************/
		
		cutFile = modifyCutFile(cutFile,filePath); // TEMPCODE Rahul Sharma // to handle tupleTypes present in multiple lines 
		Vector<String> listOfCopyFiles = generateCopyFileForSMT(cutFile, filePath, noOfOutputTuples, 
				tableMap,columns,existingTableNames, dbAppParameters);			
		Vector<String> listOfFiles = (Vector<String>) listOfCopyFiles.clone();
		if(listOfCopyFiles.size() > 0){
			File datasetDir = new File(Configuration.homeDir+"/temp_smt"+filePath+"/"+datasetName);
			boolean created = datasetDir.mkdirs();
			if(!created) {
				logger.log(Level.WARNING, "Could not create directory for dataset: "+datasetDir.getPath());
			}

			for(String i:listOfCopyFiles){				
				File src = new File(Configuration.homeDir+"/temp_smt"+filePath+"/"+i);
				File dest = new File(Configuration.homeDir+"/temp_smt"+filePath+"/"+datasetName+"/"+i);
				Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);

			}	
			//Connection conn = MyConnection.getTestDatabaseConnection();
			//try(Connection conn = (new DatabaseConnection().getTesterConnection(assignmentId)).getTesterConn()){
			//	populateTestDataForTesting(listOfCopyFiles, filePath, tableMap,conn, assignmentId, questionId);

			for(String i : listOfFiles){				
				File src = new File(Configuration.homeDir+"/temp_smt"+filePath+"/"+i);
				File dest = new File(Configuration.homeDir+"/temp_smt"+filePath+"/"+datasetName+"/"+i);
				Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}

			returnVal=true;
		}

		/*}catch(Exception e){
			logger.log(Level.SEVERE,e.getMessage(),e);
			//e.printStackTrace();
			throw new Exception("Process exited", e);
		} finally {

			if (proc!=null)
				Utilities.closeProcessStreams(proc);
		}*/
		return returnVal;			
	}

	/**
	 * This method handles the constraints splitted into multiple lines in the cut file 
	 * @param cutFileName : Z3 cut file name 
	 * @param filePath : file path of the cut file 
	 * @return : cutFileName
	 * @throws IOException
	 */
	private String modifyCutFile(String cutFileName, String filePath) throws IOException {
        String modifiedCutFile = "";
        BufferedReader br =  new BufferedReader(new FileReader(Configuration.homeDir+"/temp_smt"+filePath+"/" + cutFileName));
        String mergedLine = "",line;
        while((line = br.readLine()) != null) {
            line = line.replaceAll(" +", " ");
            line = line.replaceAll("\n+", "");
            if(line.contains(")")) {
                mergedLine+=line;
                modifiedCutFile+=mergedLine+"\n";
                mergedLine = "";
            }
            else
                mergedLine+=line;
        }
        OutputStream os = null;
        os = new FileOutputStream(new File(Configuration.homeDir+"/temp_smt"+filePath+"/" + cutFileName));
        os.write(modifiedCutFile.getBytes(), 0, modifiedCutFile.length());
        os.close();
        return cutFileName;
    }
	
	public static void deleteAllTablesFromTestUser(Connection conn) throws Exception{
		try{
			DatabaseMetaData dbm = conn.getMetaData();
			String[] types = {"TEMPORARY TABLE"};
			ResultSet rs = dbm.getTables(conn.getCatalog(), null, "%", types);		  

			while(rs.next()){
				String table=rs.getString("TABLE_NAME");		
				if(!table.equalsIgnoreCase("xdata_temp1")
						&& !table.equalsIgnoreCase("xdata_temp2")){
					//PreparedStatement pstmt = conn.prepareStatement("delete from "+table);						
					PreparedStatement pstmt = conn.prepareStatement("Truncate table "+table +" cascade");
					pstmt.executeUpdate();
					pstmt.close();
				}

			} 

			rs.close();
		}catch(SQLException e){
			logger.log(Level.SEVERE,e.getMessage(),e);
		}

	}

	public static void deleteAllTempTables(Connection dbConn) throws Exception{
		Statement st = dbConn.createStatement();
		st = dbConn.createStatement();
		st.executeUpdate("DISCARD TEMPORARY");
		st.close();
	}

public static void loadSQLFilesToDataBase(Connection testCon,String sqlFile,String filePath) {
	
	String sqlFileFullPath = Configuration.homeDir+"/temp_smt"+File.separator+filePath +File.separator+ sqlFile;
		try {
			BufferedReader br = new BufferedReader(new FileReader(sqlFileFullPath));
			String st="";
			while((st=br.readLine())!=null){
				try(PreparedStatement inst=testCon.prepareStatement(st)){
					try{
						inst.executeUpdate();
						//If constraint not violated, that means the record is encountered first time
					}
					catch(Exception e){
						// e.printStackTrace();
						//System.out.println(e);
					} 
					finally{
						inst.close();
					}
				}
			}
			br.close();
		}
		catch(Exception e) {
			System.out.println(e);
		}
	}

	public static String loadCopyFileToDataBase(Connection testCon,String dataset,String filePath, TableMap tableMap) throws Exception{

		String dsPath = Configuration.homeDir+"/temp_smt"+File.separator+filePath+File.separator+dataset;
		ArrayList<String> copyFileList=new ArrayList<String>();
		ArrayList <String> copyFilesWithFk = new ArrayList<String>();
		Pattern pattern = Pattern.compile("^DS([0-9]+)$");
		java.util.regex.Matcher matcher = pattern.matcher(dataset);
		
		File ds=new File(dsPath);
		String copyFiles[] = ds.list();

		String datasetvalue="",st="";
		if(copyFiles != null && copyFiles.length==0){
			return null;
		}else if(copyFiles == null){
			return null;
		}

		for(int j=0;j<copyFiles.length;j++){
			//	if(copyFiles[j].contains(".ref")){
			//	copyFileList.add(copyFiles[j].substring(0,copyFiles[j].indexOf(".ref")));
			//}else{

			copyFileList.add(copyFiles[j].substring(0,copyFiles[j].indexOf(".copy")));
			//}
		}


		/**Delete existing entries in Temp tables **/
		int size = tableMap.foreignKeyGraph.topSort().size();
		for (int fg=(size-1);fg>=0;fg--){
			String tableName = tableMap.foreignKeyGraph.topSort().get(fg).toString();
			String del="delete from "+tableName;
			try(PreparedStatement stmt=testCon.prepareStatement(del)){
				try{
					stmt.executeUpdate();

				}catch(Exception e){

					e.printStackTrace();
				}finally{

					stmt.close();
				}
			}
		}

		//This part helps in identifying the order of foreign key dependence and helps in
		//populating the data accordingly.
		for(int f=0;f<tableMap.foreignKeyGraph.topSort().size();f++){
			String tableName = tableMap.foreignKeyGraph.topSort().get(f).toString();
			String tName="";

			if(copyFileList.contains(tableName) || copyFileList.contains(tableName+".ref")){
				if(copyFileList.contains(tableName+".ref")){
					tName = tableName+".ref";
				}else
					tName = tableName;

				copyFilesWithFk.add(tName+".copy");
				BufferedReader br = new BufferedReader(new FileReader(dsPath+"/"+tName+".copy"));

				while((st=br.readLine())!=null){
					String row="'"+st.replaceAll("\\|", "','") +"'";
					row = row.replaceAll("'null'", "null");
					String insert="insert into "+tableName+" Values ("+row+")";

					try(PreparedStatement inst=testCon.prepareStatement(insert)){
						try{
							inst.executeUpdate();
							//If constraint not violated, that means the record is encountered first time

						}catch(Exception e){

							//System.out.println(e);
							// e.printStackTrace();
						} finally{
							inst.close();
						}
					}
				}

				br.close();
			}

		}


		for(int j=0;j<copyFiles.length;j++){

			String copyFileName = copyFiles[j];
			//Added By Akku
			int k = copyFileName.indexOf(".ref.copy");
			if(k!=-1)
			{
				String tname1 =copyFileName.substring(0,k);
				
				if (!Referenced_table_names.containsKey(tname1)) {
					Referenced_table_names.put(tname1,1);
				}

			//System.out.println(tname1);
			}
			
			//Addee by Akku ends
			
		 if(copyFilesWithFk.contains(copyFileName)){
				continue;
			}else{
				//Check for primary keys constraint and add the data to avoid duplicates


				String tname =copyFileName.substring(0,copyFileName.indexOf(".copy"));
				

				BufferedReader br = new BufferedReader(new FileReader(dsPath+"/"+copyFileName));
				while((st=br.readLine())!=null){

					//String row=st.replaceAll("\\|", "','");
					String row="'"+st.replaceAll("\\|", "','") +"'";
					row = row.replaceAll("'null'", "null");
					String insert="insert into "+tname+" Values ('"+row+"')";

					try(PreparedStatement inst=testCon.prepareStatement(insert)){
						try{
							inst.executeUpdate();

						}catch(Exception e){
							//If exception occurs, then this is duplicate column
							//System.out.println(e);
							//e.printStackTrace();
						} finally{
							inst.close();
						}
					}
					// dsValue.addData(st);
				}
				br.close();
			}
		}

		return null;
	}
	public static Map<String, Integer> getNamesOfReferencedTables()
	{
		return Referenced_table_names;
	}


}
