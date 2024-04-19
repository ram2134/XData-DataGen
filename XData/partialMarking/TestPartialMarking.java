
/**
 * 
 */
package partialMarking;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Properties;
import java.util.logging.Logger;

import parsing.ForeignKey;
import parsing.JoinClauseInfo;
import parsing.Node;
import parsing.QueryData;
import parsing.Util;
/**
 * @author mathew
 *
 */
public class TestPartialMarking {
	
	private static Logger logger = Logger.getLogger(TestPartialMarking.class.getName());
	// Details corresponding to the instructor query
	public QueryDetails InstructorQuery;
	
	// Details corresponding to the student query 
	public QueryDetails StudentQuery;	
	
	// Configuration values required for the scoring function
	public PartialMarkerConfig Configuration;
	
	static int assignNo=0;//for University Schema
//	static int assignNo=4; //for TPCH Schema
//	static int assignNo=13; //for Amol sirs Schema
		
	// Maximum marks
	int maxMarks=100;
	
	public void setMaxMarks(int marks){
		maxMarks=marks;
	}
	
	public TestPartialMarking(){
		this.Configuration = new PartialMarkerConfig();
		this.Configuration.Relation=1;
		this.Configuration.Predicate=1;
		this.Configuration.Projection=1;
		this.Configuration.Joins=1;
		this.Configuration.OuterQuery=2;
		this.Configuration.GroupBy=1;
		this.Configuration.HavingClause=1;
		this.Configuration.SubQConnective=1;
		this.Configuration.SetOperators=1;
		this.Configuration.Distinct=1;
		this.Configuration.Aggregates=1;
		this.Configuration.WhereSubQueries=1;
		this.Configuration.FromSubQueries=1;
		this.Configuration.OrderBy=1;
	}
	
	public TestPartialMarking(int assignmentId){
		assignNo=assignmentId;
		this.Configuration = new PartialMarkerConfig();
		this.Configuration.Relation=1;
		this.Configuration.Predicate=1;
		this.Configuration.Projection=1;
		this.Configuration.Joins=1;
		this.Configuration.OuterQuery=2;
		this.Configuration.GroupBy=1;
		this.Configuration.HavingClause=1;
		this.Configuration.SubQConnective=1;
		this.Configuration.SetOperators=1;
		this.Configuration.Distinct=1;
		this.Configuration.Aggregates=1;
		this.Configuration.WhereSubQueries=1;
		this.Configuration.FromSubQueries=1;
		this.Configuration.OrderBy=1;
	}
	
	public QueryDetails process(QueryDetails queryDetails, int questionId, String strQuery) throws Exception{
		queryDetails=new QueryDetails();
		queryDetails.startProcessing(assignNo, questionId, strQuery);	
		queryDetails.getQueryStructure().reAdjustJoins();/*required for separating 
		join conditions that are by default added to the list of selection conditions
		after parsing */
	
//		for(ConjunctQueryStructure c:queryDetails.qStructure.conjuncts){
//			for(Node n :c.getJoinCondsAllOther())
//				System.out.println("join Conditions : "+n+" jointype "+n.getJoinType());
//		}
		
//		for(Node n:queryDetails.qStructure.getLstJoinConditions()){
//			System.out.println(" join conditions"+n+" join type"+n.getJoinType());
//		}
		
		return queryDetails;
				
	}
	
	public QueryDetails processCanonicalize(QueryDetails queryDetails, int questionId, String strQuery) throws Exception{
		queryDetails=new QueryDetails();
		
		queryDetails.startProcessing(assignNo, questionId, strQuery);
		CanonicalizeQuery.Canonicalize(queryDetails.qStructure);
//		for(JoinClauseInfo fk:queryDetails.getQueryStructure().getForeignKeyVector()){
//			System.out.println(fk.getJoinTable1()+ ": "+fk.getJoinAttribute1()+" -->"+fk.getJoinTable2()+" :"+fk.getJoinAttribute2());
//		}
//		for(Node n:queryDetails.qStructure.getLstJoinConditions()){
//			System.out.println(" join conditions"+n+" join type"+n.getJoinType());
//		}

		
		return queryDetails;
				
	}
	
	
	public void copyData() throws Exception{
		Class.forName("org.postgresql.Driver");			
		Properties prop=new Properties();
		prop.setProperty("user", "testing1");
		prop.setProperty("password", "password");
		Connection srcConn=DriverManager.getConnection("jdbc:postgresql://10.129.22.35:5432/xdata?searchpath=testing1", prop);
		Connection tarConn=DriverManager.getConnection("jdbc:postgresql://localhost:5432/xdata?searchpath=testing1", prop);
		//Util.copyDatabaseTables(srcConn, tarConn);
		Util.copyDatabaseTables(srcConn, tarConn, "xdata_database_connection");
		srcConn.close();
		tarConn.close();
	}
	
	
									
	
	/* method for testing parsing in batch. Assumption: queries are stored in column <querystring> from database <xdatat>, 
	 * the non-parsing queries and their associated roll numbers are
	 * stored at the end of execution in file <tarFileName>
	 */
	public static void readQueriesFromDBParseAndTest() throws Exception{
		String tarFileName="/home/mathew/Desktop/BadStudentQueries.txt";
		String goodTarFileName="/home/mathew/Desktop/goodStudentQueries.txt";
		PrintWriter writer = new PrintWriter(tarFileName);
		PrintWriter goodWriter=new PrintWriter(goodTarFileName);
		TestPartialMarking testObj=new TestPartialMarking();
		Class.forName("org.postgresql.Driver");			
		Properties prop=new Properties();
		prop.setProperty("user", "testing1");
		prop.setProperty("password", "password");
		Connection conn=DriverManager.getConnection("jdbc:postgresql://localhost:5432/xdatat?searchpath=testing1", prop);
		
		String selQuery="select  distinct rollnum, querystring, course_id, assignment_id, question_id, queryid from xdata_student_queries where querystring!='' AND rollnum like 'cs%' order by question_id";
		PreparedStatement selStmt=conn.prepareStatement(selQuery);
		ResultSet tableValues=selStmt.executeQuery();
		int count=0;
		int errCount=0;
		while(tableValues.next()){ 
			count++;
			String rollnum=tableValues.getString(1);
			String studQuery=tableValues.getString(2);
			String course_id=tableValues.getString(3);
			String assignment_id=tableValues.getString(4);
			String question_id=tableValues.getString(5);
				try{
					testObj.StudentQuery=testObj.process(testObj.StudentQuery,1, studQuery);
					System.out.println("serialNum "+count+" course_id: "+ course_id +" question_id: "+ question_id +
							" rollnum:"+ rollnum + "SQL query: "+studQuery);
					goodWriter.println("serialNum "+count+" course_id: "+ course_id +" question_id: "+ question_id +
							" rollnum:"+ rollnum + "SQL query: "+studQuery);
					goodWriter.println();
					goodWriter.flush();
				}
				catch(Exception e){
					errCount++;
					System.out.println(errCount+ " queryId "+count+" rollnum:"+ rollnum + " SQL query: "+studQuery);
					writer.println(errCount+ " actualId "+count+" course_id: "+ course_id +" question_id: "+ question_id +
							" rollnum:"+ rollnum + "SQL query: "+studQuery);
					writer.println();
					writer.flush();
					//				Scanner scan = new Scanner(System.in);
					//				String s = scan.next();
				}
		}
		System.out.println("count"+count);
		conn.close();
		writer.close();
		goodWriter.close();
	}
	
	/* method for testing parsing in batch. Assumption: queries are stored in file <srcFileName>, the non-parsing queries are
	 * stored at the end of execution in file <tarFileName>
	 */
	public static void readQueriesFromFileParseAndTest(){
		String srcFileName="/home/mathew/Desktop/Non-parsingStudentQueries", tarFileName="/home/mathew/Desktop/BadQueries.txt";
	
		TestPartialMarking testObj=new TestPartialMarking();
		try{
		BufferedReader reader=new BufferedReader(new FileReader(srcFileName));
		PrintWriter writer = new PrintWriter(tarFileName);
		String line=null;
		String query="";
		
		while((line=reader.readLine())!=null){
			if(line.isEmpty()){
				if(query.trim()!=""){
					String serialNum=query.substring(0,query.indexOf(")")+1);
					String actualQuery=query.substring(query.indexOf(")")+1);
					System.out.println(serialNum);
					try{
						testObj.StudentQuery=testObj.processCanonicalize(testObj.StudentQuery,1, actualQuery);
						System.out.println("good query "+serialNum +" : "+ actualQuery);
					}
					catch(Exception e){
						System.out.println("Bad query "+serialNum +" : "+ actualQuery);
						writer.println(serialNum+actualQuery);
					}
				}
				query="";
			}
			else{
				query+=line;
				//System.out.println(query);
				}
			
		}
		reader.close();
		writer.close();}
		catch(Exception e){
			e.printStackTrace();
		}
	}
	public static void processStudentQueryFromKeyboard(TestPartialMarking testObj) throws Exception{
		BufferedReader reader=new BufferedReader(new InputStreamReader(System.in));
		String studentQuery="", instructorQuery="";
		String line="";
		System.out.println("Enter the instructorQuery followed by letter 'q' in the next line");
		while((line=reader.readLine())!=null){
			if(line.equals("q"))
				break;
			else
				instructorQuery+=(line+" ");
		}
		System.out.println("Enter the studentQuery followed by letter 'q' in the next line");
		while((line=reader.readLine())!=null){
			if(line.equals("q"))
				break;
			else
				studentQuery+=(line+" ");
		}

		testObj.StudentQuery=testObj.processCanonicalize(testObj.StudentQuery,1, studentQuery);
		testObj.InstructorQuery=testObj.processCanonicalize(testObj.InstructorQuery, 1, instructorQuery);
		Float normalMarks=PartialMarker.calculateScore(testObj.InstructorQuery.qStructure, testObj.InstructorQuery.qStructure, 0).Marks;
		Float studentMarks=PartialMarker.calculateScore(testObj.InstructorQuery.qStructure, testObj.StudentQuery.qStructure, 0).Marks;
		System.out.println("normal Marks"+normalMarks+ " studentMarks "+studentMarks+ " partial marks"+studentMarks*100/normalMarks);
	}
	/**
	 * @param args
	 */
	public static void mainRam(String[] args) {
		// TODO Auto-generated method stub		
//		String studentQuery= "SELECT COUNT(TI.NAME) AS Instr_name, TI.SALARY, TS.NAME, TS.grade FROM (TEACHES T INNER JOIN INSTRUCTOR I ON T.ID>I.ID) as "
//				+ " TI INNER JOIN (SELECT * from TAKES T, STUDENT S WHERE T.ID=S.ID) as TS ON TI.ID<>TS.ID "
//				+ " WHERE Instr_name > 3 OR TI.SALARY NOT IN (SELECT tot_cred from TAKES T1, STUDENT S1 WHERE T1.ID=S1.ID) "
//				+ " GROUP BY  Instr_name, TI.SALARY ";
		
		
//		String studentQuery=" with counts as  "
//				+ " (select course_id, sec_id, year, semester, count(ID) from takes "
//				+ " group by course_id, sec_id, year, semester), "
//				+ " max_cnt(cnt) as "
//				+ "(select max(count) from counts) "
//				+ " select distinct course_id, sec_id, year, semester, max_cnt.cnt as count "
//				+ " from counts, max_cnt where counts.count = max_cnt.cnt";
//		String studentQuery="With T1(ID,semester,year,time_slot_id) as (select ID,semester,year,time_slot_id from (select ID,semester,year from takes) as T4 natural join section), "
//				+ "T2(ID,semester,year,time_slot_id) as (select ID,semester,year,time_slot_id from (select ID,semester,year,course_id from takes) as T5  natural join section), "
//				+ "T3(ID,semester,year,time_slot_id) as (select ID,semester,year,time_slot_id from (select ID,semester,year,sec_id from takes) as T6  natural join section), "
//				+ "T7(ID,semester,year,time_slot_id) as ((select * from T2) union (select * from T3)) "
//				+ "select distinct ID from ((select * from T1) except (select * from T7)) as T";
//				String studentQuery="SELECT TEACHES.course_id FROM TEACHES INNER JOIN INSTRUCTOR "
//				+ " ON TEACHES.ID<=INSTRUCTOR.ID, DEPARTMENT WHERE INSTRUCTOR.dept_name<=DEPARTMENT.dept_name "
//				+ "AND 3<TEACHES.ID  ";
//				+ "GROUP BY TEACHES.ID, INSTRUCTOR.ID HAVING TEACHES.ID <= INSTRUCTOR.ID ";
		
//		String studentQuery="select course_id, title from course "
//				+ " where course_id not in (select section.course_id from section, time_slot "
//				+ " where section.time_slot_id = time_slot.time_slot_id and start_hr < 7)";
//		
	
//		String studentQuery="SELECT TEACHES.course_id FROM TEACHES NATURAL JOIN INSTRUCTOR, DEPARTMENT"
//				+ " WHERE INSTRUCTOR.dept_name>DEPARTMENT.dept_name AND 3>TEACHES.ID GROUP BY TEACHES.ID, INSTRUCTOR.ID HAVING 3>TEACHES.course_id";
//		String instructorQuery="SELECT c.dept_name, SUM(c.credits) FROM course c INNER JOIN department d ON (c.dept_name = d.dept_name) GROUP BY c.dept_name HAVING SUM(c.credits)>10 AND COUNT(c.credits)>1";
//		String studentQuery="with task0 as  (select * from takes), "
//				+ "task1 as ((select * from task0 UNION select * from task0) MINUS SELECT * from task0)"
//				+ "select * from takes minus select * from takes union select * from takes";
//		String studentQuery="WITH takes_time_slot(ID, course_id, sec_id, semester, year, time_slot_id) AS	(SELECT takes.ID, takes.course_id, takes.sec_id, takes.semester, takes.year, time_slot_id 	"
//				+ " FROM takes NATURAL JOIN section), time_slot_clash(id_1, id_2) "
//				+ " AS	(SELECT S1.time_slot_id, S2.time_slot_id FROM time_slot as S1, time_slot as S2  "
//				+ " WHERE S1.time_slot_id!=S2.time_slot_id and S1.day=S2.day and "
//				+ " numrange((60*S1.start_hr+S1.start_min), 60*S1.end_hr+S1.end_min) && numrange(60*S2.start_hr+S2.start_min, 60*S2.end_hr+S2.end_min)) "
//				+ " SELECT DISTINCT T.ID  FROM takes_time_slot as T, takes_time_slot as S, "
//				+ " time_slot_clash as C WHERE T.ID=S.ID AND T.semester=S.semester AND T.year=S.year  "
//				+ " AND (T.time_slot_id=S.time_slot_id OR (T.time_slot_id=C.id_1 AND S.time_slot_id=C.id_2)) "
//				+ " AND (T.course_id!=S.course_id OR T.sec_id!=S.sec_id)";
		
//		String studentQuery="select t.day from time_slot as t natural join section as s1 where t.time_slot_id  in (select time_slot_id from section as s, teaches as t where s.course_id=t.course_id and teaches.ID='22222' and section.semester='Fall' and section.year='2009')";
//		String studentQuery= " Select * from (Select d.id from department d) as sub, (Course as R INNER JOIN DEPARTMENT "+
//				" ON Course.dept_name<=DEPARTMENT.dept_name OR R.dept_Id=Department.dept_Id) as S INNER JOIN (INSTRUCTOR I NATURAL JOIN DEPARTMENT D) as K ON R.dept_name=I.dept_name";
		
//		String studentQuery = "with A(id,year) as  "
//				+ "(select id,year from takes,course where takes.course_id=course.course_id and dept_name='Comp. Sci.'), "
//				+ "B(less_id) as (select id from A where year<2010), "
//				+ "C(greater_id) as (select id from A where year>2010), "
//				+ "D(stud_id) as  ((select * from B) INTERSECT (select * from C)) "
//				+ "select id,name from student,D where id=stud_id";
		
//		
//	String	studentQuery="select	ps_partkey, "
//			+ "sum(ps_supplycost * ps_availqty) as value from 	"
//			+ "partsupp, supplier, nation where ps_suppkey = s_suppkey "
//			+ "and s_nationkey = n_nationkey and n_name = ':1' "
//			+ "group by ps_partkey having sum(ps_supplycost * ps_availqty) > "
//			+ "( select sum(ps_supplycost * ps_availqty)  from "
//			+ "partsupp, supplier, nation where ps_suppkey = s_suppkey "
//			+ "and s_nationkey = n_nationkey and n_name = ':1' ) order by value desc;";
	
	
	
	
	//studentQuery="SELECT extract(year from '2015-11-22') as temp from  supplier";
			//		studentQuery="WITH query as "
//				+ "	(WITH query as (select course_id,sec_id,year,semester,count(student.ID) as number "
//				+ " from section natural join takes,student  where takes.ID=student.ID and section.course_id=takes.course_id and section.sec_id = takes.sec_id and section.semester = takes.semester and section.year = takes.year group by section.course_id,section.sec_id,section.year,section.semester) "
//				+ "select max(number) from query) "
//				+ " select course_id,sec_id,year,semester,query.max as number from section natural join takes,student,query  where takes.ID=student.ID and section.course_id=takes.course_id and section.sec_id = takes.sec_id and section.semester = takes.semester and section.year = takes.year group by section.course_id,section.sec_id,section.year,section.semester,query.max";
		
//		String studentQuery="SELECT  DISTINCT DEPARTMENT.DEPT_NAME, TEACHES.course_id, TEACHES.SEC_ID, TEACHES.SEMESTER, TEACHES.YEAR,  INSTRUCTOR.ID "
//				+ "FROM  DEPARTMENT D, TEACHES INNER JOIN INSTRUCTOR ON  TEACHES.ID=INSTRUCTOR.ID "
//				+ " WHERE  INSTRUCTOR.dept_name=D.dept_name "
//				+ " AND INSTRUCTOR.SALARY=D.budget " 
//				+ " GROUP BY INSTRUCTOR.SALARY, TEACHES.ID, D.budget, INSTRUCTOR.ID, INSTRUCTOR.dept_name" +
//   " HAVING INSTRUCTOR.ID=TEACHES.ID AND TEACHES.ID=INSTRUCTOR.ID";
//		String studentQuery="SELECT  INSTRUCTOR.ID,  D.budget FROM  INSTRUCTOR INNER JOIN "
//				+ " DEPARTMENT D ON INSTRUCTOR.dept_name=D.dept_name, TEACHES"
//				+ " WHERE  INSTRUCTOR.ID=TEACHES.ID "+
//				" OR  D.budget = D.dept_name OR D.budget=3";
		
//		String studentQuery="SELECT TEACHES.course_id FROM TEACHES  WHERE "
//				+ " TEACHES.ID > ALL  (SELECT CLASSROOM.building FROM CLASSROOM WHERE CLASSROOM.room_number=3) AND "
//		+ " TEACHES.ID NOT IN (SELECT INSTRUCTOR.ID FROM INSTRUCTOR) "
//		+ " OR  TEACHES.course_id IN (SELECT INSTRUCTOR.ID FROM INSTRUCTOR) AND "
//		+ " TEACHES.course_id >= ANY (SELECT INSTRUCTOR.ID FROM INSTRUCTOR)";
		
//		String studentQuery="WITH takes_time_slot(ID, course_id, sec_id, semester, year, time_slot_id) AS	"
//				+ "(SELECT takes.ID, takes.course_id, takes.sec_id, takes.semester, takes.year, time_slot_id  FROM takes NATURAL JOIN section),  "
//				+ "time_slot_clash(id_1, id_2) AS (SELECT S1.time_slot_id, S2.time_slot_id FROM time_slot as S1, time_slot as S2  "
//				+ "WHERE S1.time_slot_id!=S2.time_slot_id and S1.day=S2.day and  "
//				+ "numrange((60*S1.start_hr+S1.start_min), 60*S1.end_hr+S1.end_min) && numrange(60*S2.start_hr+S2.start_min, 60*S2.end_hr+S2.end_min)) "
//				+ " SELECT DISTINCT T.ID  FROM takes_time_slot as T, takes_time_slot as S,  "
//				+ "time_slot_clash as C WHERE T.ID=S.ID AND T.semester=S.semester AND T.year=S.year AND (T.time_slot_id=S.time_slot_id OR (T.time_slot_id=C.id_1 AND S.time_slot_id=C.id_2))  AND (T.course_id!=S.course_id OR T.sec_id!=S.sec_id)";
//		String studentQuery="with A(id,year) as  (select id,year from takes,course where "
//				+ "takes.course_id=course.course_id and dept_name='Comp. Sci.'), "
//				+ "B(less_id) as (select id from A where year<2010), "
//				+ "C(greater_id) as (select id from A where year>2010), "
//				+ "D(stud_id) as ((select * from B) INTERSECT (select * from C)) "
//				+  "select id,name from student,D where id=stud_id";
		
//		String studentQuery="SELECT INSTRUCTOR.ID FROM  "
//		+ TEACHES  WHERE TEACHES.ID > ALL "
//		+ " (SELECT INSTRUCTOR.ID FROM INSTRUCTOR  WHERE INSTRUCTOR.ID NOT IN ( 1,2,3 ))";
//String instructorQuery="SELECT c.dept_name, SUM(c.credits) FROM course c INNER JOIN department d ON "
//				+ "(c.dept_name = d.dept_name) GROUP BY c.dept_name  HAVING SUM(c.credits)>10 AND COUNT(c.credits)>1 and COUNT(c.credits)<4";
		
		
//		String strQuery= " WITH R AS (SELECT * FROM TEACHES INNER JOIN INSTRUCTOR ON TEACHES.ID=INSTRUCTOR.ID)"
//				+ "SELECT R.course_id FROM  R "
//				+ " INNER JOIN  DEPARTMENT ON R.dept_name=DEPARTMENT.dept_name";
		TestPartialMarking testObj=new TestPartialMarking();
		try{
//			String instructorQuery = "";//"SELECT DISTINCT course_id, title FROM course NATURAL JOIN section WHERE semester = 'Spring' AND year = 2010 AND course_id NOT IN (SELECT course_id FROM prereq)";
			//"SELECT course_id, title FROM course NATURAL JOIN takes WHERE semester = 'Spring' AND year = '2010' AND course_id NOT IN (SELECT course_id FROM prereq)";
			String instructorQuery="SELECT Distinct  INSTRUCTOR.ID,  D.dept_name FROM  INSTRUCTOR, DEPARTMENT D WHERE INSTRUCTOR.dept_name=D.dept_name";
			String studentQuery="SELECT  INSTRUCTOR.ID,  INSTRUCTOR.dept_name FROM  INSTRUCTOR, DEPARTMENT D WHERE INSTRUCTOR.dept_name=D.dept_name";
			studentQuery="select distinct id from takes, course where takes.course_id=course.course_id";
			//readQueriesFromFileParseAndTest();
			//readQueriesFromDBParseAndTest();			
			processStudentQueryFromKeyboard(testObj);
			
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
	public static void mainTest(String[] args){
		//experiment();
	  String instQuery=" SELECT distinct time_slot.day FROM teaches, section, time_slot "
	  		+ " where teaches.course_id=section.course_id AND teaches.semester=section.semester AND "
	  		+ " teaches.year=section.year AND teaches.sec_id=section.sec_id AND section.time_slot_id= time_slot.time_slot_id "
	  		+ " AND section.semester='Fall' AND section.year='2009' and teaches.id='22222'";
		try{
			//ExperimentUtils.ExportStudentQueriesFromDBToFile("studentQueryFile.json","CS 387-2015-1", 1,1,1,1);
			//ExperimentUtils.importStudentQueriesFromJSONFile("studentQueryFile.json");
			//ExperimentUtils.processStudentInstructorQueries("studentQueryFile.json", "outStatsFile.json", instQuery,10);
			//ExperimentUtils.processStudentInstructorQueriesToText("studentQueryFile.json", "stats.txt", instQuery, 10);
			//ExperimentUtils.ExportInstructorQueriesFromDBToFile("instructorQueryFile.json","CS 387-2015-1", 3,1,1);
			//ExperimentUtils.importInstructorQueriesFromJSONFile("instructorQueryFile.json");			
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
	
//	public static void main(){
		public static void main(String[] args)	throws Exception{
	   	ArrayList<String> results=new ArrayList<String>();
		TestPartialMarking testObj=new TestPartialMarking();
		String intructorQuery="select distinct time_slot.day from section,time_slot,teaches where section.time_slot_id = time_slot.time_slot_id and section.course_id = teaches.course_id and teaches.id='22222' and section.semester='Fall' and section.year='2009'";
		String[][] studentQueries={
				{"10791", "select distinct time_slot.day from teaches,section,time_slot where teaches.Id = '22222' and section.semester = 'Fall' and section.year = '2009' and teaches.course_id = section.course_id and teaches.sec_id = section.sec_id and time_slot.time_slot_id = section.time_slot_id"},
				{ "5990", "select day from instructor natural join teaches natural join section natural join time_slot where ID='22222' and semester='Fall' and year ='2009'"},
				{ "6219", "select distinct day from section natural join teaches natural join time_slot where id='22222' and semester='Fall' and year=2009"},
				{ "6221", "select day from time_slot natural join section natural join teaches where ID='22222' and semester='Fall' and year=2009"},
				{ "8227", "select day from section natural join teaches natural join time_slot where ID ='22222' and semester = 'Fall' and Year='2009'"},	
				{ "6222", "select time_slot.day from (section inner join teaches on section.course_id = teaches.course_id) natural join time_slot where section.semester = 'Fall' and section.year ='2009' and teaches.ID = '22222'"},
				{ "8177", "select distinct day from time_slot where time_slot_id in (select time_slot_id from section where course_id in (select course_id from teaches where id='22222') and  year='2009'and semester='Fall'"},
                { "8149", "select day from teaches natural join section natural join time_slot  where ID='22222' and semester='Fall' and year = '2009'"},
                { "6571", "select day from time_slot natural join section natural join teaches where ID = '22222' and semester='Fall' and year = '2009'"},    
                { "7407", "select name from instructor"},
                { "6304", "select id, name from student natural join takes where dept_name = 'History' and (select count(*) from student natural join takes where dept_name = 'History' and semester = 'Fall' and year = 2010) > 3"},
                { "6302",  "select student.ID,name from student,takes   where student.ID=takes.ID AND dept_name='History' AND (select count (course_id) from takes where ID=student.ID AND year=2010 AND semester='Fall')>3"},
                {"6293", "select id, name from student natural join takes where semester='Fall' and year='2009' and dept_name='History' group by id having count(course_id)>3"},
                {"6291", "select id , name from takes natural join student where dept_name='History' and semester='Fall' and year='2000' group by id,name having count(*)>3" },  
                {"10791", "select student.ID, student.name from student,takes where student.dept_name = 'History' and (select count(*) from takes where takes.ID = student.ID and takes.semester = 'Fall' and takes.year = '2010') > 3"},
                {"5990",  "select student.ID,student.name from student natural join takes natural join section  where semester='fall' and year='2010' and dept_name='History' group by student.ID having count(takes.course_id)>3"},
                {"6219",  "select distinct id,name from  (select id from  (select id,course_id from student natural join takes where dept_name='History' and semester='Fall' and year=2010) as T  group by T.id having count(course_id)>3) as P natural join student"},
                {"6221",  "select  student.ID,name from student, takes where student.ID = takes.ID and student.dept_name = 'History' group by student.ID having count(course_id) > 3"},
                {"6229",  "select A.ID from (select count(course_id),ID from takes where year = '2010' and semester = 'Fall' group by ID) as B, student as A where A.ID = B.ID and B.count > 3 and dept_name = 'History'"}
				};
		for(int i=0;i<studentQueries.length;i++){
			try{
//				System.out.println("Ram");
				testObj.StudentQuery=testObj.processCanonicalize(testObj.StudentQuery,1, studentQueries[i][1]);
				testObj.InstructorQuery=testObj.processCanonicalize(testObj.InstructorQuery,1, intructorQuery);
				Float studentMarks=PartialMarker.calculateScore( testObj.InstructorQuery.getQueryStructure(), testObj.StudentQuery.getQueryStructure(), 0).Marks;
				int numRedundantRelations=testObj.StudentQuery.getQueryStructure().getLstRedundantRelations().size();
				results.add("\nRollno: "+studentQueries[i][0]+ " Student Query "+studentQueries[i][1]+"\n # of redundant relations="+numRedundantRelations+"\n Marks:"+studentMarks);
			}
			catch(Exception e){
				System.out.println(e);	
				}
		}
		for(String result:results)
			System.out.println(result);
		}
}