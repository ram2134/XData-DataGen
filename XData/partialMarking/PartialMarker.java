package partialMarking;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;


import com.google.gson.Gson;
import parsing.AggregateFunction;
import parsing.ConjunctQueryStructure;
import parsing.Node;
import util.MyConnection;
import parsing.QueryStructure;
import queryEdit.*;
import parsing.Pair;
import util.Utilities;


public class PartialMarker {

	private static Logger logger = Logger.getLogger(PartialMarker.class.getName());
	private float  FULL_MARKS = 100;
	// The unique identifier of the assignment
	int assignmentId;

	// The unique identifier for the question within the assignment
	int questionId;

	// The unique identifier for the query within the question
	int queryId;

	//Unique identifier for the course
	String course_id;

	// The id of the student
	String studentId;

	// Maximum marks
	static int maxMarks=100;

	String guestStudentQuery;

	// Details corresponding to the instructor query
	public QueryDetails InstructorQuery;

	// Details corresponding to the student query 
	public QueryDetails StudentQuery;

	// Configuration values required for the scoring function
	public static PartialMarkerConfig Configuration;

	// Returns the assignment id
	public int getAssignmentId(){
		return this.assignmentId;
	}

	// Returns the question id
	public int getQuestionId(){
		return this.questionId;
	}

	// Returns the question id
	public int getQueryId(){
		return this.queryId;
	}

	// Returns the student id
	public String getStudentId(){
		return this.studentId;
	}

	// Sets the assignment id
	public void setAssignmentId(int aId){
		this.assignmentId = aId;
	}

	// Sets the question id
	public void setQuestionId(int aId){
		this.questionId = aId;
	}

	// Sets the question id
	public void setQueryId(int qId){
		this.queryId = qId;
	}

	// Sets the student id
	public void setStudentQuery(String query){
		this.studentId = query;
	}
	
	
	HashMap<String, Float> dp = new HashMap<>();

	// Returns an instance of the partial marker
	public PartialMarker(int assignmentId, int quesId, int queryId, String course_id, String rollNum){
		this.assignmentId = assignmentId;
		this.questionId = quesId;
		this.queryId = queryId;
		this.course_id = course_id;
		this.studentId = rollNum;
		this.InstructorQuery = new QueryDetails();
		this.StudentQuery = new QueryDetails();
		this.guestStudentQuery = null;
		//the following two members changed to by mathew (oct 20 2016)
		PartialMarker.Configuration = new PartialMarkerConfig();
		PartialMarker.maxMarks = 100;
		try{
			PartialMarker.Configuration.setConfigurationValues(assignmentId, quesId, queryId);
		} catch (Exception ex){
			logger.log(Level.SEVERE,ex.getMessage(),ex);
			//ex.printStackTrace();
		}
	}

	public PartialMarker(int assignmentId, int quesId, int queryId, String course_id, String rollNum,String studQuery){
		this.assignmentId = assignmentId;
		this.questionId = quesId;
		this.queryId = queryId;
		this.course_id = course_id;
		this.studentId = rollNum;
		this.InstructorQuery = new QueryDetails();
		this.StudentQuery = new QueryDetails();
		this.guestStudentQuery = studQuery;
		//the following two members changed to by mathew (oct 20 2016)
		PartialMarker.maxMarks = 100;  
		PartialMarker.Configuration = new PartialMarkerConfig();
		try{
			PartialMarker.Configuration.setConfigurationValues(assignmentId, quesId, queryId);
		} catch (Exception ex){
			logger.log(Level.SEVERE,ex.getMessage(),ex);
			//ex.printStackTrace();
		}
	}


	private void initialize(){
		try{		
			PartialMarker.maxMarks = this.InstructorQuery.InitializeInstructorQuery(this.assignmentId, this.questionId, this.queryId);
			this.StudentQuery.InitializeStudentQuery(this.assignmentId, this.questionId, this.studentId,this.guestStudentQuery);
		}
		catch(Exception ex){
			logger.log(Level.SEVERE,ex.getMessage(), ex);
			//ex.printStackTrace();
		}
	}
	private void flattenFromClauseInnerJoin(QueryStructure q)
	{
		boolean isInner=true;
		for(Node joinConds : q.getLstJoinConditions())
		{
			if(!joinConds.getJoinType().equalsIgnoreCase("INNER JOIN"))
			{
				isInner=false;
				break;
			}
		}
		if(isInner)
		{
			for(int i=0;i<q.getFromClauseSubqueries().size();i++)
			{
				QueryStructure temp = q.getFromClauseSubqueries().elementAt(i);
				q.getLstSelectionConditions().addAll(temp.getLstSelectionConditions());
				q.getLstRelationInstances().addAll(temp.getLstRelationInstances());
				q.getLstJoinConditions().addAll(temp.getLstJoinConditions());

				for(Node t:temp.getLstJoinConditions())
				{
					for(ConjunctQueryStructure conjunctElements : q.getConjuncts())
					{
						if(!conjunctElements.getJoinCondsForEquivalenceClasses().contains(t))
						{
							conjunctElements.getJoinCondsForEquivalenceClasses().add(t);
							conjunctElements.getEquivalenceClasses().removeAll(conjunctElements.getEquivalenceClasses());
							conjunctElements.createEqClass();	
						}
					}
				}

				Vector<Vector<Node>> NewEqClass=new Vector<Vector<Node>>();
				for(ConjunctQueryStructure conjunctElements : q.getConjuncts())
				{
					for(Vector<Node> EqClasses: conjunctElements.getEquivalenceClasses())
						NewEqClass.add(EqClasses);
				}
				//Changing the Equivalence Class (lstEqClasses) Each time
				q.getLstEqClasses().removeAll(q.getLstEqClasses());
				for(Vector<Node> EqClassElements : NewEqClass)
				{
					ArrayList<Node> EqClassArrayList = new ArrayList<Node>(EqClassElements);
					q.getLstEqClasses().add(EqClassArrayList);
				}
			}
			q.getFromClauseSubqueries().clear();
		}
	}
	private void handlingDistinctForSet(QueryStructure q)
	{
		if((q.setOperator!=null && !q.setOperator.isEmpty()))
		{
			q.leftQuery.setIsDistinct(false);
			q.rightQuery.setIsDistinct(false);
			flattenFromClauseInnerJoin(q.leftQuery);
			flattenFromClauseInnerJoin(q.rightQuery);
			handlingDistinctForSet(q.leftQuery);
			handlingDistinctForSet(q.rightQuery);
		}
	}
	private float getScaledMarks(float totalNodes,float totalOrderByNodes,float originalMarks,float orderByMarks,int maxMarks)
	{
		//float totalDeduct = (100 - originalMarks) + (100 - orderByMarks);
		float ans = originalMarks + orderByMarks;
		//		float ans= originalMarks*(totalNodes-totalOrderByNodes)/totalNodes ;
		//		ans+=orderByMarks*(totalOrderByNodes)/totalNodes;
		ans=(ans*maxMarks)/totalNodes;
		return ans;
	}
	public static float totalNodes(QueryStructure instructorData)
	{
		if((instructorData.setOperator!=null && !instructorData.setOperator.isEmpty()))
		{
			return totalNodes(instructorData.leftQuery) + totalNodes(instructorData.rightQuery) + 1;
		}
		float whereSubQueryPredicate = weightOfSubquery(instructorData.getWhereClauseSubqueries());
		float fromSubQueryPredicate = weightOfSubquery(instructorData.getFromClauseSubqueries());

		float uniquePredicates = instructorData.getLstSelectionConditions().size()*3;
		float uniqueRelations = instructorData.getLstRelationInstances().size();
		float uniqueProj = 0;
		if(instructorData.getQueryType().getType() != null && (instructorData.getQueryType().getType().equalsIgnoreCase("EXISTS") || instructorData.getQueryType().getType().equalsIgnoreCase("NOT EXISTS"))) 
			uniqueProj = 0;
		else
			uniqueProj = instructorData.getLstProjectedCols().size();
		float instructorJoin = getJoinCount(instructorData)*3;
		float uniqueGroupBy = instructorData.getLstGroupByNodes().size();
		float uniqueHavingClause = instructorData.getLstHavingConditions().size();
		//float uniqueSubQConnective = instructorData.getLstSubQConnectives().size();
		float uniqueSubQConnective=0;
		if(instructorData.getQueryType().getType() != null)
			uniqueSubQConnective=1;
		float uniqueAggregates = instructorData.getLstAggregateList().size(); 
		//float uniqueSetOperators = instructorData.getLstSetOpetators().size();
		float uniqueDistinct = 0;
		if(instructorData.getIsDistinct()) uniqueDistinct = 1;
		float orderByColumns = instructorData.getLstOrderByNodes().size();

		float totalWeightage = uniquePredicates + uniqueRelations + uniqueProj + instructorJoin + uniqueGroupBy + uniqueHavingClause + uniqueSubQConnective;
		totalWeightage += uniqueAggregates + uniqueDistinct + orderByColumns + whereSubQueryPredicate + fromSubQueryPredicate;

		//float WEIGHT = 100/ totalWeightage;
		return totalWeightage;
	}
	private float editOrderByScore(QueryStructure Instructor,QueryStructure Student,float maxMarks, float deductMarks) throws Exception
	{
		QueryStructure canonicalized_student = (QueryStructure)Utilities.copy(Student);
		CanonicalizeQuery.Canonicalize(canonicalized_student);
		float Marks =  Metric.LCS(canonicalized_student, Instructor);
		if(Marks == FULL_MARKS)
			return maxMarks;
		if(maxMarks <= 0)
			return 0;
		List<Pair<QueryStructure,Float> > edited_query_structure = new ArrayList <Pair<QueryStructure,Float>>();
		List<Pair<QueryStructure,Float> > orderby_deleted = new OrderBy().remove(Student,Instructor);
		List<Pair<QueryStructure,Float> > orderby_added = new OrderBy().add(Student,Instructor);
		List<Pair<QueryStructure,Float> > orderby_moved = new OrderBy().move(Student,Instructor);
		List<Pair<QueryStructure,Float> > orderby_edited = new OrderBy().edit(Student,Instructor);
		for(Pair<QueryStructure,Float> t:orderby_deleted)
		{
			edited_query_structure.add(t);
		}
		for(Pair<QueryStructure,Float> t:orderby_added)
		{
			edited_query_structure.add(t);
		}
		for(Pair<QueryStructure,Float> t:orderby_moved)
		{
			edited_query_structure.add(t);
		}
		for(Pair<QueryStructure,Float> t:orderby_edited)
		{
			edited_query_structure.add(t);
		}

		Pair<QueryStructure,QueryStructure> BestMatch = new Pair<QueryStructure,QueryStructure> ();
		float maxScore = 0;

		for(Pair<QueryStructure,Float> editedstudentqueries: edited_query_structure)
		{
			QueryStructure temp = (QueryStructure)Utilities.copy(editedstudentqueries);
			CanonicalizeQuery.Canonicalize(temp);
			float result1 = Metric.LCS(temp, Instructor);
			if(result1 > maxScore)
			{
				BestMatch.setFirst(Instructor);
				BestMatch.setSecond(editedstudentqueries.getFirst());
				maxScore=result1;
			}
		}
		if(maxScore == FULL_MARKS)
			return maxMarks - deductMarks;
		maxMarks = maxMarks - deductMarks;
		return editOrderByScore(BestMatch.getFirst(),BestMatch.getSecond(),maxMarks,deductMarks);
	}
	private int All_Pair_whereclauseExhaustive(QueryStructure Instructor,QueryStructure Student,boolean isWhere) throws Exception
	{
		int num_edit = 0;
		int result=10000;
		Vector<QueryStructure> master,slave;
		if(isWhere)
		{
			master = Instructor.getWhereClauseSubqueries();
			slave = Student.getWhereClauseSubqueries();
		}
		else
		{
			master = Instructor.getFromClauseSubqueries();
			slave = Student.getFromClauseSubqueries();
		}
		int masterCount = master.size();		
		int slaveCount = slave.size();
		ArrayList<ArrayList<Integer>> combinations = new ArrayList<ArrayList<Integer>>();

		if(masterCount < slaveCount){	
			Vector <Boolean> leftovers=new Vector<Boolean>(slaveCount);
			leftovers.setSize(slaveCount);
			generateCombinations(combinations, masterCount, slaveCount, new ArrayList<Integer>(), 0);
			for(ArrayList<Integer> combination : combinations){
				num_edit = 0;
				Collections.fill(leftovers, Boolean.FALSE);
				for(int i = 0; i < combination.size(); i++){	
					float num_nodes=totalNodes(master.get(i));
					float e = editScoreExhaustiveIterative(master.get(i), slave.get(combination.get(i)), num_nodes,1);
					num_edit += (num_nodes-e);
					leftovers.set(combination.get(i), true);
				}
				int count=-1;
				for(Boolean leftover:leftovers)
				{
					count++;
					if(leftover==true)
						continue;
					num_edit+= totalNodes(slave.get(count));
				}
				if(num_edit < result){
					result = num_edit;
				}
			}

		} else {	
			Vector <Boolean> leftovers=new Vector<Boolean>(masterCount);
			leftovers.setSize(masterCount);
			generateCombinations(combinations, slaveCount, masterCount, new ArrayList<Integer>(), 0);
			for(ArrayList<Integer> combination : combinations){
				num_edit = 0;
				Collections.fill(leftovers, Boolean.FALSE);
				for(int i = 0; i < combination.size(); i++){
					float num_nodes=totalNodes(master.get(combination.get(i)));
					float e = editScoreExhaustiveIterative(master.get(combination.get(i)), slave.get(i), num_nodes,1);
					num_edit +=  (num_nodes-e);
					leftovers.set(combination.get(i), true);
				}
				int count=-1;
				for(Boolean leftover:leftovers)
				{
					count++;
					if(leftover==true)
						continue;
					num_edit+= totalNodes(master.get(count));
				}
				if(num_edit < result){
					result = num_edit;
				}
			}
		}

		return result;
	}

	private int All_Pair_whereclause(QueryStructure Instructor,QueryStructure Student,boolean isWhere) throws Exception
	{
		int num_edit = 0;
		int result=10000;
		Vector<QueryStructure> master,slave;
		if(isWhere)
		{
			master = Instructor.getWhereClauseSubqueries();
			slave = Student.getWhereClauseSubqueries();
		}
		else
		{
			master = Instructor.getFromClauseSubqueries();
			slave = Student.getFromClauseSubqueries();
		}
		int masterCount = master.size();		
		int slaveCount = slave.size();
		ArrayList<ArrayList<Integer>> combinations = new ArrayList<ArrayList<Integer>>();

		if(masterCount < slaveCount){	
			Vector <Boolean> leftovers=new Vector<Boolean>(slaveCount);
			leftovers.setSize(slaveCount);
			generateCombinations(combinations, masterCount, slaveCount, new ArrayList<Integer>(), 0);
			for(ArrayList<Integer> combination : combinations){
				num_edit = 0;
				Collections.fill(leftovers, Boolean.FALSE);
				for(int i = 0; i < combination.size(); i++){	
					float num_nodes=totalNodes(master.get(i));
					float e = editScore(master.get(i), slave.get(combination.get(i)), num_nodes,1);
					num_edit += (num_nodes-e);
					leftovers.set(combination.get(i), true);
				}
				int count=-1;
				for(Boolean leftover:leftovers)
				{
					count++;
					if(leftover==true)
						continue;
					num_edit+= totalNodes(slave.get(count));
				}
				if(num_edit < result){
					result = num_edit;
				}
			}

		} else {	
			Vector <Boolean> leftovers=new Vector<Boolean>(masterCount);
			leftovers.setSize(masterCount);
			generateCombinations(combinations, slaveCount, masterCount, new ArrayList<Integer>(), 0);
			for(ArrayList<Integer> combination : combinations){
				num_edit = 0;
				Collections.fill(leftovers, Boolean.FALSE);
				for(int i = 0; i < combination.size(); i++){
					float num_nodes=totalNodes(master.get(combination.get(i)));
					float e = editScore(master.get(combination.get(i)), slave.get(i), num_nodes,1);
					num_edit +=  (num_nodes-e);
					leftovers.set(combination.get(i), true);
				}
				int count=-1;
				for(Boolean leftover:leftovers)
				{
					count++;
					if(leftover==true)
						continue;
					num_edit+= totalNodes(master.get(count));
				}
				if(num_edit < result){
					result = num_edit;
				}
			}
		}

		return result;
	}

	private float editScoreSetoperator(QueryStructure Instructor,QueryStructure Student,float maxMarks, float deductMarks) throws Exception
	{
		float marks=0;
		boolean tag=false;
		// both have set operator
		if((Instructor.setOperator!=null&&!Instructor.setOperator.isEmpty()) && (Student.setOperator!=null&&!Student.setOperator.isEmpty()))
		{
			if(!Instructor.setOperator.toString().equalsIgnoreCase(Student.setOperator.toString()))
			{
				tag=true;
				Student.setOperator=Instructor.setOperator;
			}
			if(Instructor.setOperator.toString().equalsIgnoreCase("EXCEPT"))
			{
				marks =  editScore(Instructor.leftQuery,Student.leftQuery,totalNodes(Instructor.leftQuery),deductMarks) + editScore(Instructor.rightQuery,Student.rightQuery,totalNodes(Instructor.rightQuery),deductMarks);
			}
			else
			{
				float marks1 = editScore(Instructor.leftQuery,Student.leftQuery,totalNodes(Instructor.leftQuery),deductMarks) + editScore(Instructor.rightQuery,Student.rightQuery,totalNodes(Instructor.rightQuery),deductMarks);
				float marks2 = editScore(Instructor.leftQuery,Student.rightQuery,totalNodes(Instructor.leftQuery),deductMarks) + editScore(Instructor.rightQuery,Student.leftQuery,totalNodes(Instructor.rightQuery),deductMarks);
				marks=max(marks1,marks2);
			}
			if(tag) maxMarks-=deductMarks;
			else marks++;
		}
		return marks;
	}
	private float editScoreSetoperatorExhaustive(QueryStructure Instructor,QueryStructure Student,float maxMarks, float deductMarks) throws Exception
	{
		float marks=0;
		boolean tag=false;
		// both have set operator
		if((Instructor.setOperator!=null&&!Instructor.setOperator.isEmpty()) && (Student.setOperator!=null&&!Student.setOperator.isEmpty()))
		{
			if(!Instructor.setOperator.toString().equalsIgnoreCase(Student.setOperator.toString()))
			{
				tag=true;
				Student.setOperator=Instructor.setOperator;
			}
			if(Instructor.setOperator.toString().equalsIgnoreCase("EXCEPT"))
			{
				marks =  editScoreExhaustiveIterative(Instructor.leftQuery,Student.leftQuery,totalNodes(Instructor.leftQuery),deductMarks) + editScore(Instructor.rightQuery,Student.rightQuery,totalNodes(Instructor.rightQuery),deductMarks);
			}
			else
			{
				float marks1 = editScoreExhaustiveIterative(Instructor.leftQuery,Student.leftQuery,totalNodes(Instructor.leftQuery),deductMarks) + editScore(Instructor.rightQuery,Student.rightQuery,totalNodes(Instructor.rightQuery),deductMarks);
				float marks2 = editScoreExhaustiveIterative(Instructor.leftQuery,Student.rightQuery,totalNodes(Instructor.leftQuery),deductMarks) + editScore(Instructor.rightQuery,Student.leftQuery,totalNodes(Instructor.rightQuery),deductMarks);
				marks=max(marks1,marks2);
			}
			if(tag) maxMarks-=deductMarks;
			else marks++;
		}
		return marks;
	}
	private float max(float marks1, float marks2) {
		return marks1>marks2?marks1:marks2;
	}

	private float editScore(QueryStructure Instructor,QueryStructure Student,float maxMarks, float deductMarks) throws Exception
	{
		// If there exits set operator
		if((Instructor.setOperator!=null&&!Instructor.setOperator.isEmpty())||(Student.setOperator!=null&&!Student.setOperator.isEmpty()))
		{
			//Only student has a set op
			if(Instructor.setOperator==null || Instructor.setOperator.isEmpty())
			{
				if(! Student.setOperator.toString().equalsIgnoreCase("EXCEPT"))
				{
					float temp1= editScore(Instructor,Student.leftQuery,maxMarks,deductMarks)-totalNodes(Student.rightQuery)-1; //set op score is 1
					float temp2=editScore(Instructor,Student.rightQuery,maxMarks,deductMarks)-totalNodes(Student.leftQuery)-1;
					return max(temp1,temp2);
				}
				else
				{
					return editScore(Instructor,Student.leftQuery,maxMarks,deductMarks)-totalNodes(Student.rightQuery)-1;
				}
			}
			//Only instructor has a set op
			else if(Student.setOperator==null || Student.setOperator.isEmpty())
			{
				if(! Instructor.setOperator.toString().equalsIgnoreCase("EXCEPT"))
				{
					float temp1= editScore(Instructor.leftQuery,Student,totalNodes(Instructor.leftQuery),deductMarks);
					float temp2=editScore(Instructor.rightQuery,Student,totalNodes(Instructor.rightQuery),deductMarks);
					return max(temp1,temp2);
				}
				else
					return editScore(Instructor.leftQuery,Student,totalNodes(Instructor.leftQuery),deductMarks);
			}
			else
				return editScoreSetoperator(Instructor,Student,maxMarks,deductMarks);
		}

		float old_marks = maxMarks;
		QueryStructure canonicalized_instructor = (QueryStructure)Utilities.copy(Instructor);
		CanonicalizeQuery.Canonicalize(canonicalized_instructor);
		FULL_MARKS=totalNodes(canonicalized_instructor);
		QueryStructure canonicalized_student = (QueryStructure)Utilities.copy(Student);
		QueryStructure student_without_where_subq=(QueryStructure)Utilities.copy(Student);
		CanonicalizeQuery.Canonicalize(canonicalized_student);
		student_without_where_subq.setLstLstSubQConnectives (canonicalized_instructor.getLstSubQConnectives());
		student_without_where_subq.getWhereClauseSubqueries().clear();
		student_without_where_subq.getFromClauseSubqueries().clear();
		student_without_where_subq.getWhereClauseSubqueries().addAll(canonicalized_instructor.getWhereClauseSubqueries());
		student_without_where_subq.getFromClauseSubqueries().addAll(canonicalized_instructor.getFromClauseSubqueries());
		CanonicalizeQuery.Canonicalize(student_without_where_subq);
		if(calculateScore(canonicalized_instructor,student_without_where_subq , 0).Marks>= FULL_MARKS)
		{
			int numEdit=All_Pair_whereclause(canonicalized_instructor,Student,true);
			maxMarks -= numEdit*deductMarks;
			numEdit=All_Pair_whereclause(canonicalized_instructor,Student,false);
			return maxMarks - numEdit*deductMarks;
		}
		if(canonicalized_student.getQueryType().getType() != null || canonicalized_instructor.getQueryType().getType() != null)
		{
			if(canonicalized_student.getQueryType().getType() != null)
			{
				if(!canonicalized_student.getQueryType().getType().equalsIgnoreCase(canonicalized_instructor.getQueryType().getType()))
					maxMarks -=deductMarks;

			}
			else if(canonicalized_instructor.getQueryType().getType() != null)
			{
				if(!canonicalized_student.getQueryType().getType().equalsIgnoreCase(canonicalized_instructor.getQueryType().getType()))
					maxMarks -=deductMarks;
			}
		}
		canonicalized_student.getQueryType().setType((canonicalized_instructor.getQueryType().getType()));
		Student.getQueryType().setType((canonicalized_instructor.getQueryType().getType()));
		MarkInfo result = calculateScore(canonicalized_instructor, canonicalized_student, 0);
		//System.out.println("Marks: "+ result.Marks);
		if(result.Marks >= FULL_MARKS)
			return maxMarks;
		if(maxMarks <= 0)
			return 0;
		List<Pair<QueryStructure,Float> > single_edit_student = SingleEdit.single_edit(Student, canonicalized_instructor);
		Pair<QueryStructure,QueryStructure> BestMatch = new Pair<QueryStructure,QueryStructure> ();
		float maxScore = 0;
		BestMatch.setFirst(canonicalized_instructor);
		BestMatch.setSecond(canonicalized_student);
		// If mismatch occurs only in distinct/querytype part
		if(single_edit_student.size()==0 && old_marks != maxMarks) 
			return editScore(BestMatch.getFirst(),BestMatch.getSecond(),maxMarks,deductMarks);

		float bestMatchCost=0;
		for(Pair<QueryStructure,Float> editedstudentqueries: single_edit_student)
		{
			QueryStructure temp = (QueryStructure)Utilities.copy(editedstudentqueries.getFirst());
			CanonicalizeQuery.Canonicalize(temp);
			MarkInfo result1 = calculateScore(canonicalized_instructor,temp, 0);
			if(result1.Marks - editedstudentqueries.getSecond()> maxScore)
			{
				BestMatch.setFirst(Instructor);
				BestMatch.setSecond(editedstudentqueries.getFirst());
				maxScore = result1.Marks - editedstudentqueries.getSecond();
				bestMatchCost = editedstudentqueries.getSecond();
			}
		}
		if(maxScore >= FULL_MARKS)
			return normalizeNegativeValuesToZero(maxMarks - bestMatchCost*deductMarks);
		if(bestMatchCost==0) return 0;
		maxMarks = maxMarks - bestMatchCost*deductMarks;
		return editScore(BestMatch.getFirst(),BestMatch.getSecond(),maxMarks,deductMarks);
	}

	/*private float editScoreExhaustive(QueryStructure Instructor,QueryStructure Student,float maxMarks, float deductMarks) throws Exception
	{
		if(dp.containsKey(Student.toString()) && dp.get(Student.toString())>=maxMarks) return dp.get(Student.toString());
		// If there exits set operator
		if((Instructor.setOperator!=null&&!Instructor.setOperator.isEmpty())||(Student.setOperator!=null&&!Student.setOperator.isEmpty()))
		{
			//Only student has a set op
			if(Instructor.setOperator==null || Instructor.setOperator.isEmpty())
			{
				if(! Student.setOperator.toString().equalsIgnoreCase("EXCEPT"))
				{
					float temp1= editScoreExhaustive(Instructor,Student.leftQuery,maxMarks,deductMarks)-totalNodes(Student.rightQuery)-1; //set op score is 1
					float temp2=editScoreExhaustive(Instructor,Student.rightQuery,maxMarks,deductMarks)-totalNodes(Student.leftQuery)-1;
					return max(temp1,temp2);
				}
				else
				{
					return editScoreExhaustive(Instructor,Student.leftQuery,maxMarks,deductMarks)-totalNodes(Student.rightQuery)-1;
				}
			}
			//Only instructor has a set op
			else if(Student.setOperator==null || Student.setOperator.isEmpty())
			{
				if(! Instructor.setOperator.toString().equalsIgnoreCase("EXCEPT"))
				{
					float temp1= editScoreExhaustive(Instructor.leftQuery,Student,totalNodes(Instructor.leftQuery),deductMarks);
					float temp2=editScoreExhaustive(Instructor.rightQuery,Student,totalNodes(Instructor.rightQuery),deductMarks);
					return max(temp1,temp2);
				}
				else
					return editScoreExhaustive(Instructor.leftQuery,Student,totalNodes(Instructor.leftQuery),deductMarks);
			}
			else
				return editScoreSetoperatorExhaustive(Instructor,Student,maxMarks,deductMarks);
		}

		float old_marks = maxMarks;
		QueryStructure canonicalized_instructor = (QueryStructure)Utilities.copy(Instructor);
		CanonicalizeQuery.Canonicalize(canonicalized_instructor);
		FULL_MARKS=totalNodes(canonicalized_instructor);
		QueryStructure canonicalized_student = (QueryStructure)Utilities.copy(Student);
		QueryStructure student_without_where_subq=(QueryStructure)Utilities.copy(Student);
		CanonicalizeQuery.Canonicalize(canonicalized_student);
		student_without_where_subq.setLstLstSubQConnectives (canonicalized_instructor.getLstSubQConnectives());
		student_without_where_subq.getWhereClauseSubqueries().clear();
		student_without_where_subq.getFromClauseSubqueries().clear();
		student_without_where_subq.getWhereClauseSubqueries().addAll(canonicalized_instructor.getWhereClauseSubqueries());
		student_without_where_subq.getFromClauseSubqueries().addAll(canonicalized_instructor.getFromClauseSubqueries());
		CanonicalizeQuery.Canonicalize(student_without_where_subq);
		if(calculateScore(canonicalized_instructor,student_without_where_subq , 0).Marks>= FULL_MARKS)
		{
			int numEdit=All_Pair_whereclauseExhaustive(canonicalized_instructor,Student,true);
			maxMarks -= numEdit*deductMarks;
			numEdit=All_Pair_whereclauseExhaustive(canonicalized_instructor,Student,false);
			return maxMarks - numEdit*deductMarks;
		}
		if(canonicalized_student.getQueryType().getType() != null || canonicalized_instructor.getQueryType().getType() != null)
		{
			if(canonicalized_student.getQueryType().getType() != null)
			{
				if(!canonicalized_student.getQueryType().getType().equalsIgnoreCase(canonicalized_instructor.getQueryType().getType()))
					maxMarks -=deductMarks;

			}
			else if(canonicalized_instructor.getQueryType().getType() != null)
			{
				if(!canonicalized_student.getQueryType().getType().equalsIgnoreCase(canonicalized_instructor.getQueryType().getType()))
					maxMarks -=deductMarks;
			}
		}
		canonicalized_student.getQueryType().setType((canonicalized_instructor.getQueryType().getType()));
		Student.getQueryType().setType((canonicalized_instructor.getQueryType().getType()));
		MarkInfo result = calculateScore(canonicalized_instructor, canonicalized_student, 0);
		//System.out.println("Marks: "+ result.Marks);
		if(result.Marks >= FULL_MARKS)
		{
			dp.put(Student.toString(), maxMarks);
			return maxMarks;
		}

		if(maxMarks <= 0)
		{
			dp.put(Student.toString(), (float)0);
			return 0;
		}
		List<Pair<QueryStructure,Float> > single_edit_student = SingleEdit.single_edit(Student, canonicalized_instructor);
		Pair<QueryStructure,QueryStructure> BestMatch = new Pair<QueryStructure,QueryStructure> ();
		float maxScore = 0;
		BestMatch.setFirst(canonicalized_instructor);
		BestMatch.setSecond(canonicalized_student);
		// If mismatch occurs only in distinct/querytype part
		if(single_edit_student.size()==0 && old_marks != maxMarks) 
			return editScoreExhaustive(BestMatch.getFirst(),BestMatch.getSecond(),maxMarks,deductMarks);
		for(Pair<QueryStructure,Float> editedstudentqueries: single_edit_student)
		{
			float marks=editScoreExhaustive(canonicalized_instructor,editedstudentqueries.getFirst(),normalizeNegativeValuesToZero(maxMarks - editedstudentqueries.getSecond()*deductMarks),deductMarks);
			if(marks>maxScore)
				maxScore=marks;
			//if(maxScore >= FULL_MARKS) break;
		}
		dp.put(Student.toString(), maxScore);
		return maxScore;
		
	}  **/
	
	
	private float editScoreExhaustiveIterative(QueryStructure CQ,QueryStructure SQ,float maxMarks, float deductMarks) throws Exception
	{

		if((CQ.setOperator!=null&&!CQ.setOperator.isEmpty())||(SQ.setOperator!=null&&!SQ.setOperator.isEmpty()))
		{
			//Only student has a set op
			if(CQ.setOperator==null || CQ.setOperator.isEmpty())
			{
				if(! SQ.setOperator.toString().equalsIgnoreCase("EXCEPT"))
				{
					float temp1= editScoreExhaustiveIterative(CQ,SQ.leftQuery,maxMarks,deductMarks)-totalNodes(SQ.rightQuery)-1; //set op score is 1
					float temp2=editScoreExhaustiveIterative(CQ,SQ.rightQuery,maxMarks,deductMarks)-totalNodes(SQ.leftQuery)-1;
					return max(temp1,temp2);
				}
				else
				{
					return editScoreExhaustiveIterative(CQ,SQ.leftQuery,maxMarks,deductMarks)-totalNodes(SQ.rightQuery)-1;
				}
			}
			//Only instructor has a set op
			else if(SQ.setOperator==null || SQ.setOperator.isEmpty())
			{
				if(! CQ.setOperator.toString().equalsIgnoreCase("EXCEPT"))
				{
					float temp1= editScoreExhaustiveIterative(CQ.leftQuery,SQ,totalNodes(CQ.leftQuery),deductMarks);
					float temp2=editScoreExhaustiveIterative(CQ.rightQuery,SQ,totalNodes(CQ.rightQuery),deductMarks);
					return max(temp1,temp2);
				}
				else
					return editScoreExhaustiveIterative(CQ.leftQuery,SQ,totalNodes(CQ.leftQuery),deductMarks);
			}
			else
				return editScoreSetoperatorExhaustive(CQ,SQ,maxMarks,deductMarks);
		}
		
		HashMap<QueryStructure, Float> eq = new HashMap<>();		
		
		eq.put(SQ, maxMarks);
		
		while(!eq.isEmpty()) {
			
			
			float maxTempScore=Float.MIN_VALUE;
			QueryStructure editedQuery=null;
			for(QueryStructure key:eq.keySet()) {
				if(eq.get(key)>maxTempScore) {
					maxTempScore=eq.get(key);
					editedQuery=key;
				}
			}
			eq.remove(editedQuery);
			float curMarks=maxTempScore;
			
			
			QueryStructure canonicalized_instructor = (QueryStructure)Utilities.copy(CQ);
			CanonicalizeQuery.Canonicalize(canonicalized_instructor);
			FULL_MARKS=totalNodes(canonicalized_instructor);
			
			QueryStructure canonicalized_student = (QueryStructure)Utilities.copy(editedQuery);
			CanonicalizeQuery.Canonicalize(canonicalized_student);
			MarkInfo result = calculateScore(canonicalized_instructor, canonicalized_student, 0);
			if(result.Marks >= FULL_MARKS) {
				return curMarks;
			}
			
			float old_marks = curMarks;
			
			canonicalized_student.getQueryType().setType((canonicalized_instructor.getQueryType().getType()));
			editedQuery.getQueryType().setType((canonicalized_instructor.getQueryType().getType()));
			
			canonicalized_student = (QueryStructure)Utilities.copy(editedQuery);
			QueryStructure student_without_where_subq=(QueryStructure)Utilities.copy(editedQuery);
			CanonicalizeQuery.Canonicalize(canonicalized_student);
			student_without_where_subq.setLstLstSubQConnectives (canonicalized_instructor.getLstSubQConnectives());
			student_without_where_subq.getWhereClauseSubqueries().clear();
			student_without_where_subq.getFromClauseSubqueries().clear();
			student_without_where_subq.getWhereClauseSubqueries().addAll(canonicalized_instructor.getWhereClauseSubqueries());
			student_without_where_subq.getFromClauseSubqueries().addAll(canonicalized_instructor.getFromClauseSubqueries());
			CanonicalizeQuery.Canonicalize(student_without_where_subq);
			if(calculateScore(canonicalized_instructor,student_without_where_subq , 0).Marks>= FULL_MARKS)
			{
				int numEdit=All_Pair_whereclauseExhaustive(canonicalized_instructor,editedQuery,true);
				curMarks -= numEdit*deductMarks;
				numEdit=All_Pair_whereclauseExhaustive(canonicalized_instructor,editedQuery,false);
				return curMarks - numEdit*deductMarks;
			}
			if(canonicalized_student.getQueryType().getType() != null || canonicalized_instructor.getQueryType().getType() != null)
			{
				if(canonicalized_student.getQueryType().getType() != null)
				{
					if(!canonicalized_student.getQueryType().getType().equalsIgnoreCase(canonicalized_instructor.getQueryType().getType()))
						curMarks -=deductMarks;

				}
				else if(canonicalized_instructor.getQueryType().getType() != null)
				{
					if(!canonicalized_student.getQueryType().getType().equalsIgnoreCase(canonicalized_instructor.getQueryType().getType()))
						curMarks -=deductMarks;
				}
			}
			
			
			List<Pair<QueryStructure,Float> > single_edit_student = SingleEdit.single_edit(editedQuery, canonicalized_instructor);
			Pair<QueryStructure,QueryStructure> BestMatch = new Pair<QueryStructure,QueryStructure> ();
			
			BestMatch.setFirst(canonicalized_instructor);
			BestMatch.setSecond(canonicalized_student);
			// If mismatch occurs only in distinct/querytype part
			if(single_edit_student.size()==0 && old_marks != curMarks) 
				return editScoreExhaustiveIterative(BestMatch.getFirst(),BestMatch.getSecond(),curMarks,deductMarks);
			for(Pair<QueryStructure,Float> editedstudentqueries: single_edit_student) {
				
				
				float genMarks=curMarks - editedstudentqueries.getSecond()*deductMarks;
				if(genMarks<=0)
					continue;
				QueryStructure curEditedQuery=editedstudentqueries.getFirst();
				if(eq.containsKey(curEditedQuery)) {
					if(eq.get(curEditedQuery)<genMarks) {
						eq.put(curEditedQuery, genMarks);
					}
				} else {
					eq.put(curEditedQuery, genMarks);
				}
				
				
			}
			
		}
		
		return 0;
		
	}

	
	
	
	
	
	

	// Returns the marks corresponding to the query of the student in comparison to the instructor query
	public MarkInfo getMarksForQueryStructures() throws Exception{
		dp.clear();
		this.initialize();
		handlingDistinctForSet(this.InstructorQuery.getQueryStructure());
		handlingDistinctForSet(this.StudentQuery.getQueryStructure());
		CanonicalizeQuery.Canonicalize(this.InstructorQuery.getQueryStructure());
		QueryStructure instructorNoOrderBy = (QueryStructure)Utilities.copy(this.InstructorQuery.getQueryStructure());
		QueryStructure studentNoOrderBy = (QueryStructure)Utilities.copy(this.StudentQuery.getQueryStructure());
		studentNoOrderBy.setLstOrderByNodes(this.InstructorQuery.getQueryStructure().getLstOrderByNodes());
		studentNoOrderBy.setOrderByNodes(this.InstructorQuery.getQueryStructure().getOrderByNodes());
		if(studentNoOrderBy.getLstOrderByNodes()==null)
			studentNoOrderBy.setLstOrderByNodes(new ArrayList<Node>());
		if(studentNoOrderBy.getOrderByNodes()==null)
			studentNoOrderBy.setOrderByNodes(new Vector<Node>());
		float totalNodes = totalNodes(instructorNoOrderBy);
		float totalOrderByNodes = 0;
		if(instructorNoOrderBy.getLstOrderByNodes()!=null)
			totalOrderByNodes = instructorNoOrderBy.getLstOrderByNodes().size();
		float deduct=100/totalNodes;


		//float originalMarks = editScore(instructorNoOrderBy,studentNoOrderBy,totalNodes-totalOrderByNodes,1);

		// To run the exhaustive version uncomment below and comment above
		float originalMarks = editScoreExhaustiveIterative(instructorNoOrderBy,studentNoOrderBy,totalNodes-totalOrderByNodes,1);


		QueryStructure instructorOrderBy = (QueryStructure)Utilities.copy(this.InstructorQuery.getQueryStructure());
		QueryStructure studentOrderBy = (QueryStructure)Utilities.copy(this.InstructorQuery.getQueryStructure());
		studentOrderBy.setLstOrderByNodes(this.StudentQuery.getQueryStructure().getLstOrderByNodes());
		float orderByMarks = 0;
		if(!((this.InstructorQuery.getQueryStructure().setOperator!=null&&!this.InstructorQuery.getQueryStructure().setOperator.isEmpty())||(this.StudentQuery.getQueryStructure().setOperator!=null&&!this.StudentQuery.getQueryStructure().setOperator.isEmpty())))
		{
			orderByMarks = editOrderByScore(instructorOrderBy,studentOrderBy,totalOrderByNodes,1);
		}


		float total_marks=getScaledMarks(totalNodes,totalOrderByNodes,originalMarks,orderByMarks,maxMarks);

		// Canonicalizing the queries
		CanonicalizeQuery.Canonicalize(this.StudentQuery.getQueryStructure());

		//Check for distinct
		//boolean evaluateDistinct = EvaluateDistinct.evaluate(this.InstructorQuery,this.StudentQuery,this.assignmentId, this.questionId, this.queryId, this.course_id);		

		float maxMainQueryScore = PartialMarker.calculateScore(this.InstructorQuery.getQueryStructure(), this.InstructorQuery.getQueryStructure(), 0).Marks /100 * maxMarks;

		MarkInfo result = calculateScore(this.InstructorQuery.getQueryStructure(), this.StudentQuery.getQueryStructure(), 0);
		result.Marks = result.Marks /100 * maxMarks;
		float studentQueryScore=result.Marks;

		result.Configuration = Configuration;

		float mainQueryScore = result.Marks;

		// Setting the negative scores to zero
		if(mainQueryScore < 0) 
			mainQueryScore = 0;

		if(maxMainQueryScore<=0.0001f){
			result.Marks=0.0f;
		}
		else
			result.Marks = mainQueryScore/maxMainQueryScore * PartialMarker.maxMarks ;
		result.Marks = result.Marks<result.Configuration.maxPartialMarks?result.Marks:result.Configuration.maxPartialMarks;
		//System.out.println("Computed Marks="+result.Marks+ " student score="+studentQueryScore +" mainqueryScore="+maxMainQueryScore);
		result.Marks = total_marks;
		return result;
	}

	private static void insertIntoQinfo(String courseId, int asId, int qId, int queryId, String query, String desc, int maxMarks, int marks) throws Exception{		
		//Connection conn = MyConnection.getExistingDatabaseConnection();
		try(Connection conn = MyConnection.getDatabaseConnection()){

			try(PreparedStatement stmt = conn.prepareStatement("select * from xdata_qinfo where course_id = ? and assignment_id = ? and question_id = ?")){
				stmt.setString(1, courseId);
				stmt.setInt(2, asId);
				stmt.setInt(3, qId);		

				ResultSet rs = stmt.executeQuery();
				if(!rs.next()){		
					try(PreparedStatement stmt1 = conn.prepareStatement("INSERT INTO xdata_qinfo VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")){
						stmt1.setString(1, courseId);
						stmt1.setInt(2, asId);
						stmt1.setInt(3, qId);		
						stmt1.setString(4, desc); 
						stmt1.setString(5, "");
						stmt1.setInt(6, maxMarks); 
						stmt1.setBoolean(7, false);
						stmt1.setBoolean(8, false); 
						stmt1.setBoolean(9, true);
						stmt1.setInt(10, queryId);
						stmt1.setInt(11, 15);
						stmt1.setBoolean(12, true);
						stmt1.executeUpdate();
					}
				} else {
					try(PreparedStatement stmt1 = conn.prepareStatement("update xdata_qinfo set querytext = ?, totalmarks = ? where course_id = ? and assignment_id = ? and question_id = ?")){
						stmt1.setString(1, desc); 
						stmt1.setInt(2, maxMarks);
						stmt1.setString(3, courseId);
						stmt1.setInt(4, asId);
						stmt1.setInt(5, qId);
						stmt1.executeUpdate();
					}
				}

				try(PreparedStatement stmt1 = conn.prepareStatement("select * from xdata_instructor_query where course_id = ? and assignment_id = ? and question_id = ? and query_id = ?")){
					stmt1.setString(1, courseId);
					stmt1.setInt(2, asId);
					stmt1.setInt(3, qId);
					stmt1.setInt(4, queryId);

					rs = stmt1.executeQuery();
					if(!rs.next()){
						try(PreparedStatement stmt2 = conn.prepareStatement("insert into xdata_instructor_query Values (?,?,?,?,?,?)")){
							stmt2.setInt(1, asId); 
							stmt2.setInt(2, qId);
							stmt2.setString(3, query); 
							//stmt2.setString(4,""); 
							stmt2.setString(4, courseId); 
							stmt2.setInt(5, queryId);
							stmt2.setInt(6, marks);

							stmt2.executeUpdate();
						}
					} else {
						try(PreparedStatement stmt2 = conn.prepareStatement("update xdata_instructor_query set sql = ?, marks = ? where course_id = ? and assignment_id = ? and question_id = ? and query_id = ?")){
							stmt2.setString(1, query); 
							stmt2.setInt(2, marks);
							stmt2.setString(3, courseId);
							stmt2.setInt(4, asId);
							stmt2.setInt(5, qId);
							stmt2.setInt(6, queryId);
							stmt2.executeUpdate();
						}
					}	
				}
			}
		}
	}

	public static float getJoinCount(QueryStructure masterData){
		float score=0;
		if(masterData.getLstJoinConditions()!=null)
			score = masterData.getLstJoinConditions().size() ;
		return score;
	}
	// Calculates a score based on the relations involved in the join
	// Number of inner and outer joins are also compared
	public static float getJoinScore(QueryStructure masterData, QueryStructure slaveData){
		float score = SelectionScore(masterData.getLstJoinConditions(), slaveData.getLstJoinConditions());

		score = masterData.getNumberOfOuterJoins() == slaveData.getNumberOfOuterJoins() ? score + masterData.getNumberOfOuterJoins() : score - 0.5f;
		//score = masterData.getNumberOfInnerJoins() == slaveData.getNumberOfInnerJoins() ? score + masterData.getNumberOfInnerJoins() : score - 0.5f;

		return score;
	}

	public static float compare(ArrayList<String> master, ArrayList<String> slave) {
		float score = 0;
		for(String n1 : slave){
			Boolean found = false;
			for(String n2 : master){				
				if(n1.equals(n2)){
					found = true;
					break;
				}
			}

			if(found){
				score++;
			}
			else{
				score=score-0.5f;
			}
		}

		return score;
	}
	/** @author bharath, recoded by mathew
	 * 
	 * checks if two nodes that represents selection clauses are syntactically identical or not, 
	 * returns true iff if they are identical 
	 * 	
	 * @param n1
	 * @param n2
	 * @return boolean
	 */

	public static Boolean checkSelectionEquality(Node n1, Node n2){

		if(!n1.getOperator().equals(n2.getOperator()))
			return false;		

		//if left node of n1 is a column reference
		if(n1.getLeft().getNodeType().equals(Node.getColRefType())){
			if(!n2.getLeft().getNodeType().equals(Node.getColRefType()))
				return  false;
			if(!n1.getLeft().getTable().getTableName().equals(n2.getLeft().getTable().getTableName()))
				return false;

			if(!n1.getLeft().getTableNameNo().equals(n2.getLeft().getTableNameNo()))
				return false;
			if(!n1.getLeft().getColumn().getColumnName().equals(n2.getLeft().getColumn().getColumnName()))
				return false;
		}
		//if left node of n1 is a constant value
		if(n1.getLeft().getNodeType().equals(Node.getValType())){
			if(!n2.getLeft().getNodeType().equals(Node.getValType()))
				return  false;
			if(!n1.getLeft().getStrConst().equals(n2.getLeft().getStrConst()))
				return false;
		}

		if(n1.getRight().getNodeType().equals(Node.getColRefType())){

			if(!n2.getRight().getNodeType().equals(Node.getColRefType()))
				return  false;

			if(!n1.getRight().getTable().getTableName().equals(n2.getRight().getTable().getTableName()))
				return false;

			if(!n1.getRight().getTableNameNo().equals(n2.getRight().getTableNameNo()))
				return false;

			if(!n1.getRight().getColumn().getColumnName().equals(n2.getRight().getColumn().getColumnName()))
				return false;
		}

		if(n1.getRight().getNodeType().equals(Node.getValType())){
			if(!n2.getRight().getNodeType().equals(Node.getValType()))
				return  false;

			if(!n1.getRight().getStrConst().equals(n2.getRight().getStrConst()))
				return false;
		}

		return true;

	}

	public static Boolean checkProjectionEquality(Node n1, Node n2){
		if(n1.getNodeType().equals(Node.getAggrNodeType()) || n2.getNodeType().equals(Node.getAggrNodeType())){

			if(!n1.getNodeType().equals(Node.getAggrNodeType()))
				return false;
			if(!n2.getNodeType().equals(Node.getAggrNodeType()))
				return false;

			AggregateFunction agg1 = n1.getAgg();
			AggregateFunction agg2 = n2.getAgg();

			if(!agg1.getFunc().equals(agg2.getFunc()))
				return false;

			if(agg1.getAggExp().getColumn() != null && agg2.getAggExp().getColumn() != null
					&& agg1.getAggExp().getColumn().getTable() != null && agg2.getAggExp().getColumn().getTable() != null
					&& agg1.getAggExp().getColumn().getTable().getTableName() != null && agg2.getAggExp().getColumn().getTable().getTableName() != null
					&& !agg1.getAggExp().getColumn().getTable().getTableName().equals(agg2.getAggExp().getColumn().getTable().getTableName()))
				return false;

			if(agg1.getAggExp().getTableNameNo() != null  &&
					!agg1.getAggExp().getTableNameNo().equals(agg2.getAggExp().getTableNameNo()))
				return false;

			if(agg1.getAggExp().getColumn() != null && agg2.getAggExp().getColumn() != null && 
					agg1.getAggExp().getColumn().getColumnName()!= null && agg2.getAggExp().getColumn().getColumnName()!= null
					&& !agg1.getAggExp().getColumn().getColumnName().equals(agg2.getAggExp().getColumn().getColumnName()))
				return false;
		}

		if(n1.getNodeType().equals(Node.getColRefType())){

			if(!n1.getTable().getTableName().equals(n2.getTable().getTableName()))
				return false;

			if(!n1.getTableNameNo().equals(n2.getTableNameNo()))
				return false;

			if(!n1.getColumn().getColumnName().equals(n2.getColumn().getColumnName()))
				return false;
		}

		return true;
	}

	/* recoded by mathew on 12 May 2016, 
	 * 
	 * checks the syntactic equivalence of two nodes that represents atomic having clause expressions
	 */
	public static Boolean checkHavingClauseEquality(Node n1, Node n2){
		//check for the equivalence of operator
		if(!n1.getOperator().equals(n2.getOperator()))
			return false;

		//check for equivalence of right nodes

		//if right node of n1 is a column reference
		if(n1.getRight().getNodeType().equals(Node.getColRefType())){
			if(!n2.getRight().getNodeType().equals(Node.getColRefType()))
				return  false;

			if(!n1.getRight().getTableNameNo().equals(n2.getRight().getTableNameNo()))
				return false;
			if(!n1.getRight().getTable().getTableName().equals(n2.getRight().getTable().getTableName()))
				return false;
			if(!n1.getRight().getColumn().getColumnName().equals(n2.getRight().getColumn().getColumnName()))
				return false;
		}
		//if right node of n1 is a constant value
		if(n1.getRight().getNodeType().equals(Node.getValType())){
			if(!n2.getRight().getNodeType().equals(Node.getValType()))
				return  false;
			if(!n1.getRight().getStrConst().equals(n2.getRight().getStrConst()))
				return false;
		}
		//if right node of n1 is an aggregate expression
		if(n1.getRight().getNodeType().equals(Node.getAggrNodeType())){
			if(!n2.getRight().getNodeType().equals(Node.getAggrNodeType()))
				return false;
			AggregateFunction agg1 = n1.getRight().getAgg();
			AggregateFunction agg2 = n2.getRight().getAgg();
			if(!agg1.getFunc().equals(agg2.getFunc()))
				return false;
			if(agg1.getAggExp().getColumn() != null && agg2.getAggExp().getColumn() != null
					&& agg1.getAggExp().getColumn().getTable() != null && agg2.getAggExp().getColumn().getTable() != null
					&& agg1.getAggExp().getColumn().getTable().getTableName() != null && agg2.getAggExp().getColumn().getTable().getTableName() != null
					&& !agg1.getAggExp().getColumn().getTable().getTableName().equals(agg2.getAggExp().getColumn().getTable().getTableName()))
				return false;

			if(agg1.getAggExp().getTableNameNo() != null  &&
					!agg1.getAggExp().getTableNameNo().equals(agg2.getAggExp().getTableNameNo()))
				return false;

			if(agg1.getAggExp().getColumn() != null && agg2.getAggExp().getColumn() != null && 
					agg1.getAggExp().getColumn().getColumnName()!= null && agg2.getAggExp().getColumn().getColumnName()!= null
					&& !agg1.getAggExp().getColumn().getColumnName().equals(agg2.getAggExp().getColumn().getColumnName()))
				return false;
		}
		//check for equivalence of left nodes

		//if left node of n1 is a column reference
		if(n1.getLeft().getNodeType().equals(Node.getColRefType())){
			if(!n2.getLeft().getNodeType().equals(Node.getColRefType()))
				return  false;
			if(!n1.getLeft().getTable().getTableName().equals(n2.getLeft().getTable().getTableName()))
				return false;

			if(!n1.getLeft().getTableNameNo().equals(n2.getLeft().getTableNameNo()))
				return false;
			if(!n1.getLeft().getColumn().getColumnName().equals(n2.getLeft().getColumn().getColumnName()))
				return false;
		}
		//if left node of n1 is a constant value
		if(n1.getLeft().getNodeType().equals(Node.getValType())){
			if(!n2.getLeft().getNodeType().equals(Node.getValType()))
				return  false;
			if(!n1.getLeft().getStrConst().equals(n2.getLeft().getStrConst()))
				return false;
		}
		//if left node of n1 is a aggregate expression
		if(n1.getLeft().getNodeType().equals(Node.getAggrNodeType())){
			if(!n2.getLeft().getNodeType().equals(Node.getAggrNodeType()))
				return false;
			AggregateFunction agg1 = n1.getLeft().getAgg();
			AggregateFunction agg2 = n2.getLeft().getAgg();
			if(!agg1.getFunc().equals(agg2.getFunc()))
				return false;
			if(agg1.getAggExp().getColumn() != null && agg2.getAggExp().getColumn() != null 
					&& agg1.getAggExp().getColumn().getTable() != null && agg2.getAggExp().getColumn().getTable() != null
					&& agg1.getAggExp().getColumn().getTable().getTableName() != null && agg2.getAggExp().getColumn().getTable().getTableName() != null 
					&&!agg1.getAggExp().getColumn().getTable().getTableName().equals(agg2.getAggExp().getColumn().getTable().getTableName()))
				return false;

			if(agg1.getAggExp().getTableNameNo() != null && !agg1.getAggExp().getTableNameNo().equals(agg2.getAggExp().getTableNameNo()))
				return false;
			if(agg1.getAggExp().getColumn() != null && agg2.getAggExp().getColumn() != null
					&& agg1.getAggExp().getColumn().getColumnName() != null && agg2.getAggExp().getColumn().getColumnName() != null
					&& !agg1.getAggExp().getColumn().getColumnName().equals(agg2.getAggExp().getColumn().getColumnName()))
				return false;
		}

		return true;
	}
	/**
	 * This method checks whether the aggregateFunction SUM,COUNT,etc., matches with student query
	 * 
	 * @param master
	 * @param slave
	 * @return
	 */
	public static Boolean checkAggregateName(AggregateFunction master, AggregateFunction slave){
		if(master.getFunc() != null && slave.getFunc() == null){
			return false;
		}
		if(master.getFunc() != null && slave.getFunc() == null){
			return false;
		}
		if(!(master.getFunc().equalsIgnoreCase(slave.getFunc()))){
			return false;
		}
		return true;
	}
	//Added by bikash for vldb2016 demo. Need to test this further
	public static float compareOrderBy(List<Node> instructorOrderBy, List<Node> studentOrderBy){

		int[][] distanceMetric=new int[instructorOrderBy.size()+1][studentOrderBy.size()+1];
		//distanceMetric[0][0]=0;

		//initialization of the first row and first column of the matrix required,
		// the following two for loops accomplishes this
		//added by mathew on 16 Sep 16
		for(int i=0;i<=instructorOrderBy.size();i++)
			distanceMetric[i][0]=i;

		for(int j=0;j<=studentOrderBy.size();j++)
			distanceMetric[0][j]=j;

		for(int i=0;i<instructorOrderBy.size();i++)
			for(int j=0;j<studentOrderBy.size();j++){
				Node ins=instructorOrderBy.get(i);
				Node s=studentOrderBy.get(j);
				if(ins.getColumn().getColumnName().equalsIgnoreCase(s.getColumn().getColumnName())&&
						ins.getColumn().getTableName().equalsIgnoreCase(s.getColumn().getTableName())){
					distanceMetric[i+1][j+1]=distanceMetric[i][j];
				} else{
					int replace = distanceMetric[i][j] + 1;
					int insert = distanceMetric[i][j + 1] + 1;
					int delete = distanceMetric[i + 1][j] + 1;

					int min = replace > insert ? insert : replace;
					min = delete > min ? min : delete;
					distanceMetric[i + 1][j + 1] = min;
				}
			}

		int distance=distanceMetric[instructorOrderBy.size()][studentOrderBy.size()];

		return (instructorOrderBy.size()+studentOrderBy.size()-1.5f*distance)/2;
	}
	public static float compareHavingClause(ArrayList<Node> master, ArrayList<Node> slave){
		float score = 0;
		for(Node n1 : slave){
			Boolean found = false;
			for(Node n2 : master){				
				if(checkHavingClauseEquality(n1, n2)){
					found = true;
					break;
				}
			}

			if(found){
				score++;
			}
			else{
				score=score-1f;
			}
		}

		return score;

		/*if(master.getNodeType().equals(Node.getBroNodeType()) && slave.getNodeType().equals(Node.getBroNodeType())){
		this.uniqueHavingClause++;
		if(checkHavingClauseEquality(master,slave)){
			score ++;
		}else{ 
			score--;
		}
	}
	else if(master.getNodeType().equals(Node.getAndNodeType()) && slave.getNodeType().equals(Node.getAndNodeType())){
		compareHavingClause(master.getLeft(),slave.getLeft());
		compareHavingClause(master.getRight(),slave.getRight());

	}
	return score;*/
	}

	public static float SelectionScore(ArrayList<Node> master,ArrayList<Node> slave1)
	{
		ArrayList<Node> slave= new ArrayList<Node>();
		for (Node dupWord : slave1) {
			if (!slave.contains(dupWord)) {
				slave.add(dupWord);
			}
		}
		float score = 0;
		float result=0;
		int masterCount = master.size();		
		int slaveCount = slave.size();
		ArrayList<ArrayList<Integer>> combinations = new ArrayList<ArrayList<Integer>>();

		if(masterCount < slaveCount){	
			Vector <Boolean> leftovers=new Vector<Boolean>(slaveCount);
			leftovers.setSize(slaveCount);
			generateCombinations(combinations, masterCount, slaveCount, new ArrayList<Integer>(), 0);
			for(ArrayList<Integer> combination : combinations){
				score = 0;
				Collections.fill(leftovers, Boolean.FALSE);
				for(int i = 0; i < combination.size(); i++){
					score += Selection.NodeDiff(master.get(i), slave.get(combination.get(i)));
					leftovers.set(combination.get(i), true);
				}
				for(Boolean leftover:leftovers)
				{
					if(leftover==true)
						continue;
					score-= 3;
				}
				if(score > result){
					result = score;
				}
			}

		} else {	
			Vector <Boolean> leftovers=new Vector<Boolean>(masterCount);
			leftovers.setSize(masterCount);
			generateCombinations(combinations, slaveCount, masterCount, new ArrayList<Integer>(), 0);
			for(ArrayList<Integer> combination : combinations){
				score = 0;
				Collections.fill(leftovers, Boolean.FALSE);
				for(int i = 0; i < combination.size(); i++){
					score += Selection.NodeDiff(master.get(combination.get(i)), slave.get(i));
					leftovers.set(combination.get(i), true);
				}

				if(score > result){
					result = score;
				}
			}
		}

		return result;
	}

	public static float compareSelection(List<Node> master, List<Node> slave1){
		ArrayList<Node> slave= new ArrayList<Node>();
		for (Node dupWord : slave1) {
			if (!slave.contains(dupWord)) {
				slave.add(dupWord);
			}
		}
		float score = 0;
		for(Node n1 : slave){
			Boolean found = false;
			for(Node n2 : master){				
				if(checkSelectionEquality(n1, n2)){
					found = true;
					break;
				}
			}

			if(found){
				score++;
			}
			else{
				score=score-1f;
			}
		}

		return score;
	}
	public static float compareProjection(ArrayList<Node> master, ArrayList<Node> slave){
		float score = 0;
		for(Node n1 : slave){
			Boolean found = false;
			for(Node n2 : master){				
				if(checkProjectionEquality(n1, n2)){
					found = true;
					break;
				}
			}
			if(found){
				score++;
			}
			else{
				score=score-1f;
			}
		}		
		return score;
	}

	public static float compareAggregates(ArrayList<AggregateFunction> master, ArrayList<AggregateFunction> slave){
		float score = 0;
		for(AggregateFunction n1 : slave){
			Boolean found = false;
			for(AggregateFunction n2 : master){				
				//Aggregate Name should match and the column also should match
				if(checkAggregateName(n1,n2) && checkProjectionEquality(n1.getAggExp(), n2.getAggExp())){
					found = true;
					break;
				}
			}
			if(found){
				score++;
			}
			else{
				score=score-1f;
			}
		}		
		return score;
	}


	private static Node checkTableOccurence(Node n,ArrayList<Node> nodeList) {
		Node newNode = null;
		String tableNameNumber = null; 
		String num = null;
		int numValue=0;
		String newTableName = null;
		if(n != null && nodeList.size() >0){
			//if n is binary node - like bro node or and node, get left and right and call the same method
			if(n.getType().equalsIgnoreCase(Node.getBroNodeType()) || n.getType().equalsIgnoreCase(Node.getAndNodeType())){
				//newNode = this.checkTableOccurence(n, nodeList);
				if(n.getLeft() != null){
					newNode = checkTableOccurence(n.getLeft(),nodeList);
					n.setLeft(newNode);
				}
				if(n.getRight() != null){
					newNode = checkTableOccurence(n.getRight(), nodeList);
					n.setRight(newNode);
				}
				return n;
			}

			//if n is aggr node - chk node itself or get agg function and get the table name number
			if(n.getType().equalsIgnoreCase(Node.getAggrNodeType())){

				if(n.getTableNameNo() != null){
					tableNameNumber = n.getTableNameNo();
					num = tableNameNumber.substring(tableNameNumber.length()-1,tableNameNumber.length());
					if(isInteger(num)){
						numValue = (Integer.parseInt(num)+1);
						if(numValue == 1){
							newTableName = n.getTable().toString()+numValue;
							for(Node nn : nodeList){
								if(nn.toString().contains(newTableName)){
									//do nothing
								}
								else{
									n.setTableNameNo(n.getTable().toString());							
								}
							}
						}
					}
				}

				newNode = checkTableOccurence(n.getAgg().getAggExp(), nodeList);
				n.getAgg().setAggExp(newNode);
				return n;

			}
			//if n is column reference node, get the table name number
			//manipulate it to next number and search the list of nodes for the new table number

			if(n.getType().equalsIgnoreCase(Node.getColRefType())){
				tableNameNumber = n.getTableNameNo();
				num = tableNameNumber.substring(tableNameNumber.length()-1,tableNameNumber.length());
				if(isInteger(num)){
					numValue = (Integer.parseInt(num)+1);
					if(numValue == 1){
						newTableName = n.getTable().toString()+numValue;
						for(Node nn : nodeList){
							if(nn.toString().contains(newTableName)){
								return n;
							}
							else{
								try {
									newNode = n.clone();
								} catch (CloneNotSupportedException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
								newNode.setTableNameNo(n.getTable().toString());
								return newNode;
							}
						}
					}else{
						return n;
					}
				}else{
					return n;
				}
			}
			if(n.getType().equalsIgnoreCase(Node.getValType())){
				return n;

			}
		}
		//if result is null - ie., not tablename exists with new number,  change the input node and set to tablename.column name and return the new node
		//if result matches any, then return the node as it is. No changes reqd.
		return newNode;

	}

	/* The following method converts returns the parameter as 
	 * it is if its value is greater than or equal to zero, and
	 * otherwise returns zero if the 
	 * input parameter has a negative  value
	 * 
	 */
	public static float normalizeNegativeValuesToZero(float d){
		if(d>=0)
			return d;
		else
			return 0;
	}


	/** @author mathew
	 * 
	 * 
	 * @param instructorData
	 * @param studentData
	 * @param level
	 * @return
	 * 
	 *  Compares query structure corresponding to the instructor and student when one of it is a set operator query, 
	 *  considers 3 alternate cases. case i) when both instructor and student structures are set operator queries,
	 *  case ii) when only instructor query is a set operator query, case iii) when only student query is a set operator 
	 *  query
	 *  
	 */
	public static MarkInfo  newcalculateScoreForSetOperatorQueries( QueryStructure instructorData, QueryStructure studentData,
			int level) {
		MarkInfo marks = new MarkInfo();
		ArrayList<QueryInfo> currentInfo = null;
		ArrayList<QueryInfo> maxInfo;
		float result=0;
		// case when instructor query is a set operator query	

		if(instructorData.setOperator!=null&&!instructorData.setOperator.isEmpty()){
			// case when student query is also a set operator query
			if(studentData.setOperator!=null&&!studentData.setOperator.isEmpty()){
				float score = 0;
				currentInfo = new ArrayList<QueryInfo>();
				QueryInfo qInfo = new QueryInfo();
				if(instructorData.setOperator!=null&&!instructorData.setOperator.isEmpty())
				{
					qInfo.InstructorSetOperators.add(instructorData.setOperator);
				}
				if(studentData.setOperator!=null&&!studentData.setOperator.isEmpty())
				{
					qInfo.StudentSetOperators.add(studentData.setOperator);
				}
				qInfo.Level =  level;
				currentInfo.add(qInfo);
				//case 1, compare left operand with left operand of the set operator and right operand with right

				Vector<QueryStructure> leftPart = new Vector<QueryStructure> ();
				leftPart.add(instructorData.leftQuery);
				float NoOfNodesLeft= weightOfSubquery(leftPart);
				MarkInfo lefte = calculateScore(instructorData.leftQuery,studentData.leftQuery, level+1);
				currentInfo.addAll(lefte.SubqueryData);


				Vector<QueryStructure> rightPart = new Vector<QueryStructure> ();
				rightPart.add(instructorData.rightQuery);
				float NoOfNodesright= weightOfSubquery(rightPart);
				MarkInfo righte = calculateScore(instructorData.rightQuery,studentData.rightQuery, level+1);
				currentInfo.addAll(righte.SubqueryData);


				float WEIGHT = 100/(NoOfNodesLeft + NoOfNodesright + 1);
				float setOpNodeMatch = 0;
				if(instructorData.setOperator.equalsIgnoreCase(studentData.setOperator))
					setOpNodeMatch = 1;
				score = (lefte.Marks * NoOfNodesLeft + righte.Marks * NoOfNodesright)/100;
				score += setOpNodeMatch;
				score *= WEIGHT;
				result = score;
				maxInfo=currentInfo;

				//case 2, compare left operand of the instructor query with right operand of the student query  
				//and right operand of the instructor query  with left operand of the student query
				// Cross matching should not be used for EXCEPT operator
				if(instructorData.setOperator.toString().equalsIgnoreCase("EXCEPT")==false)
				{
					score = 0;
					currentInfo = new ArrayList<QueryInfo>();
					qInfo = new QueryInfo();
					if(instructorData.setOperator!=null&&!instructorData.setOperator.isEmpty())
					{
						qInfo.InstructorSetOperators.add(instructorData.setOperator);
					}
					if(studentData.setOperator!=null&&!studentData.setOperator.isEmpty())
					{
						qInfo.StudentSetOperators.add(studentData.setOperator);
					}
					qInfo.Level =  level;
					currentInfo.add(qInfo);
					//case 1, compare left operand with left operand of the set operator and right operand with right

					leftPart = new Vector<QueryStructure> ();
					leftPart.add(instructorData.leftQuery);
					NoOfNodesLeft= weightOfSubquery(leftPart);
					lefte = calculateScore(instructorData.leftQuery,studentData.leftQuery, level + 1);
					currentInfo.addAll(lefte.SubqueryData);


					rightPart = new Vector<QueryStructure> ();
					rightPart.add(instructorData.rightQuery);
					NoOfNodesright= weightOfSubquery(rightPart);
					righte = calculateScore(instructorData.rightQuery,studentData.rightQuery, level + 1);
					currentInfo.addAll(righte.SubqueryData);

					setOpNodeMatch = 0;
					if(instructorData.setOperator.equalsIgnoreCase(studentData.setOperator))
						setOpNodeMatch = 1;
					WEIGHT = 100/(NoOfNodesLeft + NoOfNodesright + 1);
					score = (lefte.Marks * NoOfNodesLeft + righte.Marks * NoOfNodesright) /100;
					score += setOpNodeMatch;
					score *= WEIGHT;

					if(score > result){
						result = score;
						maxInfo=currentInfo;
					}
				}

				marks.Marks=result;
				marks.SubqueryData=maxInfo;
			}
			else // student query is not a set operator query
			{
				float score = 0;
				currentInfo = new ArrayList<QueryInfo>();
				QueryInfo qInfo = new QueryInfo();
				if(instructorData.setOperator!=null&&!instructorData.setOperator.isEmpty())
				{
					qInfo.InstructorSetOperators.add(instructorData.setOperator);
				}
				if(studentData.setOperator!=null&&!studentData.setOperator.isEmpty())
				{
					qInfo.StudentSetOperators.add(studentData.setOperator);
				}
				qInfo.Level =  level;
				currentInfo.add(qInfo);
				//case 1, compare left operand of the instructor Query with the student query
				Vector<QueryStructure> leftPart = new Vector<QueryStructure> ();
				leftPart.add(instructorData.leftQuery);
				float NoOfNodesLeft= weightOfSubquery(leftPart);
				MarkInfo e = calculateScore(instructorData.leftQuery,studentData, level);
				currentInfo.addAll(e.SubqueryData);

				float WEIGHT = 100/(NoOfNodesLeft + 1);
				score = (e.Marks * NoOfNodesLeft) /100;
				score *= WEIGHT;


				result = score;;
				maxInfo = currentInfo;

				//case 2, compare right operand of the instructor Query with the student query
				currentInfo = new ArrayList<QueryInfo>();
				currentInfo.add(qInfo);
				Vector<QueryStructure> rightPart = new Vector<QueryStructure> ();
				rightPart.add(instructorData.rightQuery);
				float NoOfNodesRight= weightOfSubquery(rightPart);

				e = calculateScore(instructorData.rightQuery,studentData, level);					
				currentInfo.addAll(e.SubqueryData);

				WEIGHT = 100/(NoOfNodesRight + 1);
				score = (e.Marks * NoOfNodesRight) /100;
				score *= WEIGHT;
				if(score > result){
					result = score;
					maxInfo=currentInfo;
				}

				marks.Marks=result;
				marks.SubqueryData=maxInfo;
			}
		}
		// instructor query is not a set operator query, where as student query is a set operator query
		else if(studentData.setOperator!=null&& !studentData.setOperator.isEmpty())
		{
			currentInfo = new ArrayList<QueryInfo>();
			QueryInfo qInfo = new QueryInfo();
			if(instructorData.setOperator!=null&&!instructorData.setOperator.isEmpty())
			{
				qInfo.InstructorSetOperators.add(instructorData.setOperator);
			}
			if(studentData.setOperator!=null&&!studentData.setOperator.isEmpty())
			{
				qInfo.StudentSetOperators.add(studentData.setOperator);
			}
			qInfo.Level =  level;
			currentInfo.add(qInfo);
			//case 1, compare  the instructor Query with left operand of the student query

			MarkInfo e = calculateScore(instructorData,studentData.leftQuery, level);
			currentInfo.addAll(e.SubqueryData);
			result = e.Marks;;
			maxInfo = currentInfo;

			//case 2, compare right operand of the instructor Query with the student query
			currentInfo = new ArrayList<QueryInfo>();
			currentInfo.add(qInfo);
			e = calculateScore(instructorData,studentData.rightQuery, level);					
			currentInfo.addAll(e.SubqueryData);
			if(e.Marks > result){
				result = e.Marks;
				maxInfo=currentInfo;
			}

			marks.Marks=result;
			marks.SubqueryData=maxInfo;
		}


		return marks;
	}

	public static MarkInfo  calculateScoreForSetOperatorQueries( QueryStructure instructorData, QueryStructure studentData,
			int level) {
		MarkInfo marks = new MarkInfo();
		ArrayList<QueryInfo> currentInfo = null;
		ArrayList<QueryInfo> maxInfo;
		float result=0;
		// case when instructor query is a set operator query
		if(instructorData.setOperator!=null&&!instructorData.setOperator.isEmpty()){
			// case when student query is also a set operator query
			if(studentData.setOperator!=null&&!studentData.setOperator.isEmpty()){
				float score = 0;
				currentInfo = new ArrayList<QueryInfo>();

				//case 1, compare left operand with left operand of the set operator and right operand with right

				MarkInfo e = calculateScore(instructorData.leftQuery,studentData.leftQuery, level);
				currentInfo.addAll(e.SubqueryData);


				score += e.Marks;

				e=calculateScore(instructorData.rightQuery,studentData.rightQuery, level);
				currentInfo.addAll(e.SubqueryData);
				score += e.Marks;
				score/=2;

				result = score;
				maxInfo=currentInfo;

				//case 2, compare left operand of the instructor query with right operand of the student query  
				//and right operand of the instructor query  with left operand of the student query
				// Cross matching should not be used for EXCEPT operator
				if(instructorData.setOperator.toString().equalsIgnoreCase("EXCEPT")==false)
				{
					currentInfo = new ArrayList<QueryInfo>();
					score = 0;

					e = calculateScore(instructorData.leftQuery,studentData.rightQuery, level);
					currentInfo.addAll(e.SubqueryData);
					score += e.Marks;

					e=calculateScore(instructorData.rightQuery,studentData.leftQuery, level);
					currentInfo.addAll(e.SubqueryData);
					score += e.Marks;
					score/=2;

					if(score > result){
						result = score;
						maxInfo=currentInfo;
					}
				}
				if(!instructorData.setOperator.equalsIgnoreCase(studentData.setOperator))
					result-=result/3;

				marks.Marks=result;
				marks.SubqueryData=maxInfo;
			}
			else // student query is not a set operator query
			{

				//case 1, compare left operand of the instructor Query with the student query

				MarkInfo e = calculateScore(instructorData.leftQuery,studentData, level);

				result = e.Marks;;
				maxInfo = e.SubqueryData;

				//case 2, compare right operand of the instructor Query with the student query

				e = calculateScore(instructorData.rightQuery,studentData, level);					

				if(e.Marks > result){
					result = e.Marks;
					maxInfo=e.SubqueryData;
				}

				marks.Marks=result/3;
				marks.SubqueryData=maxInfo;
			}
		}
		// instructor query is not a set operator query, where as student query is a set operator query
		else if(studentData.setOperator!=null&& !studentData.setOperator.isEmpty())
		{
			//case 1, compare  the instructor Query with left operand of the student query

			MarkInfo e = calculateScore(instructorData,studentData.leftQuery, level);

			result =  e.Marks;;
			maxInfo = e.SubqueryData;

			//case 2, compare right operand of the instructor Query with the student query

			e = calculateScore(instructorData,studentData.rightQuery, level);					

			if(e.Marks > result){
				result = e.Marks;
				maxInfo=e.SubqueryData;
			}

			marks.Marks=result/3;
			marks.SubqueryData=maxInfo;
		}


		return marks;
	}


	public static void setConfigurationValues(PartialMarkParameters params){
		Configuration = new PartialMarkerConfig();
		if(params != null){
			Configuration.Relation=params.getRelation();
			Configuration.Predicate=params.getPredicate();
			Configuration.Projection=params.getProjection();
			Configuration.Joins=params.getJoins();
			Configuration.WhereSubQueries=params.getWhereSubQueries();
			Configuration.FromSubQueries=params.getFromSubQueries();
			Configuration.OuterQuery=params.getOuterQuery();
			Configuration.GroupBy=params.getGroupBy();
			Configuration.HavingClause=params.getHavingClause();
			Configuration.SubQConnective=params.getSubQConnective();
			Configuration.Aggregates=params.getAggregates();
			Configuration.SetOperators=params.getSetOperators();
			Configuration.Distinct=params.getDistinct();
		}
		//Configuration.OrderBy=params.getOrderBy();
	}


	/** @author mathew
	 * 
	 *  method to set Configuration parameters as a function of the input query structure
	 *  
	 *  For each configuration section S (projections, selection conditions, join conditions etc.), if S
	 *   in the outer query is not empty, it tries to assign a weight 
	 *  that is equal to the size of S in the outer query, otherwise it recursively traverses
	 *  from and where clause subqueries to assign a value that is equal to the maximum possible value of S 
	 * 
	 * @param qStruct
	 */

	public static void setConfigurationValues(QueryStructure qStruct) {
		// TODO Auto-generated method stub			
		Configuration = new PartialMarkerConfig();
		if(qStruct!=null){

			if(qStruct.getLstRelationInstances()!=null&&!qStruct.getLstRelationInstances().isEmpty()){
				Configuration.Relation=qStruct.getLstRelationInstances().size();
			}
			else{
				int tempMaxSize;
				if((tempMaxSize=maxRelationSize(qStruct))>0){
					Configuration.Relation=tempMaxSize;
				}
			}

			if(qStruct.getLstSelectionConditions()!=null&&!qStruct.getLstSelectionConditions().isEmpty()){
				Configuration.Predicate=qStruct.getLstSelectionConditions().size();
			}
			else{
				int tempMaxSize;
				if((tempMaxSize=maxPredicateSize(qStruct))>0){
					Configuration.Predicate=tempMaxSize;
				}
			}
			if(qStruct.getLstProjectedCols()!=null&&!qStruct.getLstProjectedCols().isEmpty()){
				Configuration.Projection=qStruct.getLstProjectedCols().size();
			}
			else{
				int tempMaxSize;
				if((tempMaxSize=maxProjectionSize(qStruct))>0){
					Configuration.Projection=tempMaxSize;
				}
			}

			if(qStruct.getLstJoinConditions()!=null&&!qStruct.getLstJoinConditions().isEmpty()){
				Configuration.Joins=qStruct.getLstJoinConditions().size();
			}
			else{
				int tempMaxSize;
				if((tempMaxSize=maxJoinSize(qStruct))>0){
					Configuration.Joins=tempMaxSize;
				}
			}

			if(qStruct.getWhereClauseSubqueries()!=null&&!qStruct.getWhereClauseSubqueries().isEmpty()){
				Configuration.WhereSubQueries=qStruct.getWhereClauseSubqueries().size();
			}
			else{
				int tempMaxSize;
				if((tempMaxSize=maxWhereSubQuerySize(qStruct))>0){
					Configuration.WhereSubQueries=tempMaxSize;
				}
			}

			if(qStruct.getFromClauseSubqueries()!=null&&!qStruct.getFromClauseSubqueries().isEmpty()){
				Configuration.FromSubQueries=qStruct.getFromClauseSubqueries().size();
			}
			else{
				int tempMaxSize;
				if((tempMaxSize=maxFromSubQuerySize(qStruct))>0){
					Configuration.FromSubQueries=tempMaxSize;
				}
			}

			Configuration.OuterQuery=Configuration.WhereSubQueries+Configuration.FromSubQueries;

			if(qStruct.getLstGroupByNodes()!=null&&!qStruct.getLstGroupByNodes().isEmpty()){
				Configuration.GroupBy=qStruct.getLstGroupByNodes().size();
			}
			else{
				int tempMaxSize;
				if((tempMaxSize=maxGroupBySize(qStruct))>0){
					Configuration.GroupBy=tempMaxSize;
				}
			}
			if(qStruct.getLstHavingConditions()!=null&&!qStruct.getLstHavingConditions().isEmpty()){
				Configuration.HavingClause=qStruct.getLstHavingConditions().size();
			}
			else{
				int tempMaxSize;
				if((tempMaxSize=maxHavingSize(qStruct))>0){
					Configuration.HavingClause=tempMaxSize;
				}
			}

			if(qStruct.getLstSubQConnectives()!=null&&!qStruct.getLstSubQConnectives().isEmpty()){
				Configuration.SubQConnective=qStruct.getLstSubQConnectives().size();
			}
			else{
				int tempMaxSize;
				if((tempMaxSize=maxSubQueryConnectiveSize(qStruct))>0){
					Configuration.SubQConnective=tempMaxSize;
				}
			}
			if(qStruct.getLstAggregateList()!=null&&!qStruct.getLstAggregateList().isEmpty()){
				Configuration.Aggregates=qStruct.getLstAggregateList().size();
			}
			else{
				int tempMaxSize;
				if((tempMaxSize=maxAggregateSize(qStruct))>0){
					Configuration.Aggregates=tempMaxSize;
				}
			}
			if(qStruct.getLstSetOpetators()!=null&&!qStruct.getLstSetOpetators().isEmpty()){
				Configuration.SetOperators=qStruct.getLstSetOpetators().size();
			}
			else{
				int tempMaxSize;
				if((tempMaxSize=maxSetOperatorSize(qStruct))>0){
					Configuration.SetOperators=tempMaxSize;
				}
			}

			Configuration.Distinct=1;
		}				

	}


	/** @author mathew
	 * 
	 * @param qStruct
	 * @return
	 */
	public static int maxPredicateSize(QueryStructure qStruct){
		int maxSize=0;
		if(qStruct==null)
			return 0;

		if(qStruct.getLstSelectionConditions()!=null&&!qStruct.getLstSelectionConditions().isEmpty()){
			maxSize=qStruct.getLstSelectionConditions().size();
		}

		if(qStruct.getFromClauseSubqueries()!=null&&!qStruct.getFromClauseSubqueries().isEmpty()){
			for(QueryStructure queryStruct:qStruct.getFromClauseSubqueries()){
				int tempMaxSize=maxPredicateSize(queryStruct);
				if(tempMaxSize>maxSize)
					maxSize=tempMaxSize;
			}
		}

		if(qStruct.getWhereClauseSubqueries()!=null&&!qStruct.getWhereClauseSubqueries().isEmpty()){
			for(QueryStructure queryStruct:qStruct.getWhereClauseSubqueries()){
				int tempMaxSize=maxPredicateSize(queryStruct);
				if(tempMaxSize>maxSize)
					maxSize=tempMaxSize;
			}
		}

		return maxSize;
	}

	/** @author mathew
	 * 
	 * @param qStruct
	 * @return
	 */
	public static int maxRelationSize(QueryStructure qStruct){
		int maxSize=0;
		if(qStruct==null)
			return 0;

		if(qStruct.getLstRelationInstances()!=null&&!qStruct.getLstRelationInstances().isEmpty()){
			maxSize=qStruct.getLstRelationInstances().size();
		}

		if(qStruct.getFromClauseSubqueries()!=null&&!qStruct.getFromClauseSubqueries().isEmpty()){
			for(QueryStructure queryStruct:qStruct.getFromClauseSubqueries()){
				int tempMaxSize=maxRelationSize(queryStruct);
				if(tempMaxSize>maxSize)
					maxSize=tempMaxSize;
			}
		}

		if(qStruct.getWhereClauseSubqueries()!=null&&!qStruct.getWhereClauseSubqueries().isEmpty()){
			for(QueryStructure queryStruct:qStruct.getWhereClauseSubqueries()){
				int tempMaxSize=maxRelationSize(queryStruct);
				if(tempMaxSize>maxSize)
					maxSize=tempMaxSize;
			}
		}

		return maxSize;
	}

	/** @author mathew
	 * 
	 * @param qStruct
	 * @return
	 */
	public static int maxProjectionSize(QueryStructure qStruct){
		int maxSize=0;
		if(qStruct==null)
			return 0;

		if(qStruct.getLstProjectedCols()!=null&&!qStruct.getLstProjectedCols().isEmpty()){
			maxSize=qStruct.getLstProjectedCols().size();
		}

		if(qStruct.getFromClauseSubqueries()!=null&&!qStruct.getFromClauseSubqueries().isEmpty()){
			for(QueryStructure queryStruct:qStruct.getFromClauseSubqueries()){
				int tempMaxSize=maxProjectionSize(queryStruct);
				if(tempMaxSize>maxSize)
					maxSize=tempMaxSize;
			}
		}

		if(qStruct.getWhereClauseSubqueries()!=null&&!qStruct.getWhereClauseSubqueries().isEmpty()){
			for(QueryStructure queryStruct:qStruct.getWhereClauseSubqueries()){
				int tempMaxSize=maxProjectionSize(queryStruct);
				if(tempMaxSize>maxSize)
					maxSize=tempMaxSize;
			}
		}

		return maxSize;
	}

	/** @author mathew
	 * 
	 * @param qStruct
	 * @return
	 */
	public static int maxJoinSize(QueryStructure qStruct){
		int maxSize=0;
		if(qStruct==null)
			return 0;

		if(qStruct.getLstJoinConditions()!=null&&!qStruct.getLstJoinConditions().isEmpty()){
			maxSize=qStruct.getLstJoinConditions().size();
		}

		if(qStruct.getFromClauseSubqueries()!=null&&!qStruct.getFromClauseSubqueries().isEmpty()){
			for(QueryStructure queryStruct:qStruct.getFromClauseSubqueries()){
				int tempMaxSize=maxJoinSize(queryStruct);
				if(tempMaxSize>maxSize)
					maxSize=tempMaxSize;
			}
		}

		if(qStruct.getWhereClauseSubqueries()!=null&&!qStruct.getWhereClauseSubqueries().isEmpty()){
			for(QueryStructure queryStruct:qStruct.getWhereClauseSubqueries()){
				int tempMaxSize=maxJoinSize(queryStruct);
				if(tempMaxSize>maxSize)
					maxSize=tempMaxSize;
			}
		}

		return maxSize;
	}


	/** @author mathew
	 * 
	 * @param qStruct
	 * @return
	 */
	public static int maxWhereSubQuerySize(QueryStructure qStruct){
		int maxSize=0;
		if(qStruct==null)
			return 0;

		if(qStruct.getWhereClauseSubqueries()!=null&&!qStruct.getWhereClauseSubqueries().isEmpty()){
			maxSize=qStruct.getWhereClauseSubqueries().size();
		}

		if(qStruct.getFromClauseSubqueries()!=null&&!qStruct.getFromClauseSubqueries().isEmpty()){
			for(QueryStructure queryStruct:qStruct.getFromClauseSubqueries()){
				int tempMaxSize=maxWhereSubQuerySize(queryStruct);
				if(tempMaxSize>maxSize)
					maxSize=tempMaxSize;
			}
		}

		if(qStruct.getWhereClauseSubqueries()!=null&&!qStruct.getWhereClauseSubqueries().isEmpty()){
			for(QueryStructure queryStruct:qStruct.getWhereClauseSubqueries()){
				int tempMaxSize=maxWhereSubQuerySize(queryStruct);
				if(tempMaxSize>maxSize)
					maxSize=tempMaxSize;
			}
		}

		return maxSize;
	}


	/** @author mathew
	 * 
	 * @param qStruct
	 * @return
	 */
	public static int maxFromSubQuerySize(QueryStructure qStruct){
		int maxSize=0;
		if(qStruct==null)
			return 0;

		if(qStruct.getFromClauseSubqueries()!=null&&!qStruct.getFromClauseSubqueries().isEmpty()){
			maxSize=qStruct.getFromClauseSubqueries().size();
		}

		if(qStruct.getFromClauseSubqueries()!=null&&!qStruct.getFromClauseSubqueries().isEmpty()){
			for(QueryStructure queryStruct:qStruct.getFromClauseSubqueries()){
				int tempMaxSize=maxFromSubQuerySize(queryStruct);
				if(tempMaxSize>maxSize)
					maxSize=tempMaxSize;
			}
		}

		if(qStruct.getWhereClauseSubqueries()!=null&&!qStruct.getWhereClauseSubqueries().isEmpty()){
			for(QueryStructure queryStruct:qStruct.getWhereClauseSubqueries()){
				int tempMaxSize=maxFromSubQuerySize(queryStruct);
				if(tempMaxSize>maxSize)
					maxSize=tempMaxSize;
			}
		}

		return maxSize;
	}


	/** @author mathew
	 * 
	 * @param qStruct
	 * @return
	 */
	public static int maxGroupBySize(QueryStructure qStruct){
		int maxSize=0;
		if(qStruct==null)
			return 0;

		if(qStruct.getLstGroupByNodes()!=null&&!qStruct.getLstGroupByNodes().isEmpty()){
			maxSize=qStruct.getLstGroupByNodes().size();
		}

		if(qStruct.getFromClauseSubqueries()!=null&&!qStruct.getFromClauseSubqueries().isEmpty()){
			for(QueryStructure queryStruct:qStruct.getFromClauseSubqueries()){
				int tempMaxSize=maxGroupBySize(queryStruct);
				if(tempMaxSize>maxSize)
					maxSize=tempMaxSize;
			}
		}

		if(qStruct.getWhereClauseSubqueries()!=null&&!qStruct.getWhereClauseSubqueries().isEmpty()){
			for(QueryStructure queryStruct:qStruct.getWhereClauseSubqueries()){
				int tempMaxSize=maxGroupBySize(queryStruct);
				if(tempMaxSize>maxSize)
					maxSize=tempMaxSize;
			}
		}

		return maxSize;
	}


	/** @author mathew
	 * 
	 * @param qStruct
	 * @return
	 */
	public static int maxHavingSize(QueryStructure qStruct){
		int maxSize=0;
		if(qStruct==null)
			return 0;

		if(qStruct.getLstHavingConditions()!=null&&!qStruct.getLstHavingConditions().isEmpty()){
			maxSize=qStruct.getLstHavingConditions().size();
		}

		if(qStruct.getFromClauseSubqueries()!=null&&!qStruct.getFromClauseSubqueries().isEmpty()){
			for(QueryStructure queryStruct:qStruct.getFromClauseSubqueries()){
				int tempMaxSize=maxHavingSize(queryStruct);
				if(tempMaxSize>maxSize)
					maxSize=tempMaxSize;
			}
		}

		if(qStruct.getWhereClauseSubqueries()!=null&&!qStruct.getWhereClauseSubqueries().isEmpty()){
			for(QueryStructure queryStruct:qStruct.getWhereClauseSubqueries()){
				int tempMaxSize=maxHavingSize(queryStruct);
				if(tempMaxSize>maxSize)
					maxSize=tempMaxSize;
			}
		}

		return maxSize;
	}

	/** @author mathew
	 * 
	 * @param qStruct
	 * @return
	 */
	public static int maxSubQueryConnectiveSize(QueryStructure qStruct){
		int maxSize=0;
		if(qStruct==null)
			return 0;

		if(qStruct.getLstSubQConnectives()!=null&&!qStruct.getLstSubQConnectives().isEmpty()){
			maxSize=qStruct.getLstSubQConnectives().size();
		}

		if(qStruct.getFromClauseSubqueries()!=null&&!qStruct.getFromClauseSubqueries().isEmpty()){
			for(QueryStructure queryStruct:qStruct.getFromClauseSubqueries()){
				int tempMaxSize=maxSubQueryConnectiveSize(queryStruct);
				if(tempMaxSize>maxSize)
					maxSize=tempMaxSize;
			}
		}

		if(qStruct.getWhereClauseSubqueries()!=null&&!qStruct.getWhereClauseSubqueries().isEmpty()){
			for(QueryStructure queryStruct:qStruct.getWhereClauseSubqueries()){
				int tempMaxSize=maxSubQueryConnectiveSize(queryStruct);
				if(tempMaxSize>maxSize)
					maxSize=tempMaxSize;
			}
		}

		return maxSize;
	}


	/** @author mathew
	 * 
	 * @param qStruct
	 * @return
	 */
	public static int maxSetOperatorSize(QueryStructure qStruct){
		int maxSize=0;
		if(qStruct==null)
			return 0;

		if(qStruct.getLstSetOpetators()!=null&&!qStruct.getLstSubQConnectives().isEmpty()){
			maxSize=qStruct.getLstSetOpetators().size();
		}

		if(qStruct.getFromClauseSubqueries()!=null&&!qStruct.getFromClauseSubqueries().isEmpty()){
			for(QueryStructure queryStruct:qStruct.getFromClauseSubqueries()){
				int tempMaxSize=maxSetOperatorSize(queryStruct);
				if(tempMaxSize>maxSize)
					maxSize=tempMaxSize;
			}
		}

		if(qStruct.getWhereClauseSubqueries()!=null&&!qStruct.getWhereClauseSubqueries().isEmpty()){
			for(QueryStructure queryStruct:qStruct.getWhereClauseSubqueries()){
				int tempMaxSize=maxSetOperatorSize(queryStruct);
				if(tempMaxSize>maxSize)
					maxSize=tempMaxSize;
			}
		}

		return maxSize;
	}

	/** @author mathew
	 * 
	 * @param qStruct
	 * @return
	 */
	public static int maxAggregateSize(QueryStructure qStruct){
		int maxSize=0;
		if(qStruct==null)
			return 0;

		if(qStruct.getLstAggregateList()!=null&&!qStruct.getLstAggregateList().isEmpty()){
			maxSize=qStruct.getLstAggregateList().size();
		}

		if(qStruct.getFromClauseSubqueries()!=null&&!qStruct.getFromClauseSubqueries().isEmpty()){
			for(QueryStructure queryStruct:qStruct.getFromClauseSubqueries()){
				int tempMaxSize=maxAggregateSize(queryStruct);
				if(tempMaxSize>maxSize)
					maxSize=tempMaxSize;
			}
		}

		if(qStruct.getWhereClauseSubqueries()!=null&&!qStruct.getWhereClauseSubqueries().isEmpty()){
			for(QueryStructure queryStruct:qStruct.getWhereClauseSubqueries()){
				int tempMaxSize=maxAggregateSize(queryStruct);
				if(tempMaxSize>maxSize)
					maxSize=tempMaxSize;
			}
		}

		return maxSize;
	}

	/* @author mathew
	 *   Compares query structure corresponding to the instructor and student. Depending on whether
	 *   the student/instructor query is a set operator query or not, calls the respective for 
	 *   matching set operator/plain select queries
	 *   
	 */
	public static MarkInfo calculateScore( QueryStructure instructorData, QueryStructure studentData,
			int level) {
		if((instructorData.setOperator!=null&&!instructorData.setOperator.isEmpty())||(studentData.setOperator!=null&&!studentData.setOperator.isEmpty()))
			return newcalculateScoreForSetOperatorQueries(instructorData, studentData, level);
		else
			return calculateScoreForPlainSelect(instructorData, studentData, level);
	}

	// Compares query structure corresponding to the instructor and student
	public static MarkInfo calculateScoreForPlainSelect( QueryStructure instructorData, QueryStructure studentData,
			int level) {
		// TODO Auto-generated method stub

		if(Configuration==null)
			PartialMarker.setConfigurationValues(instructorData);

		MarkInfo marks = new MarkInfo();
		//Set level 0 query details for display
		marks.SubqueryData.add(QueryInfo.getQueryInfo(instructorData,studentData,level));

		MarkInfo whereSubQuery = compareListOfQueries(instructorData.getWhereClauseSubqueries(), studentData.getWhereClauseSubqueries(), level + 1);
		marks.SubqueryData.addAll(whereSubQuery.SubqueryData);

		MarkInfo fromSubQuery = compareListOfQueries(instructorData.getFromClauseSubqueries(), studentData.getFromClauseSubqueries(), level + 1);
		marks.SubqueryData.addAll(fromSubQuery.SubqueryData);

		float whereSubQueryPredicate = weightOfSubquery(instructorData.getWhereClauseSubqueries());
		float fromSubQueryPredicate = weightOfSubquery(instructorData.getFromClauseSubqueries());

		float uniquePredicates = instructorData.getLstSelectionConditions().size()*3;
		float uniqueRelations = instructorData.getLstRelationInstances().size();
		float uniqueProj = instructorData.getLstProjectedCols().size();
		float instructorJoin = getJoinCount(instructorData)*3;
		float uniqueGroupBy = instructorData.getLstGroupByNodes().size();
		float uniqueHavingClause = instructorData.getLstHavingConditions().size();
		float uniqueAggregates = instructorData.getLstAggregateList().size(); 
		float uniqueSetOperators = instructorData.getLstSetOpetators().size();
		float uniqueDistinct = 0;
		float uniqueSubQConnective=0;
		if(instructorData.getQueryType().getType() != null)
			uniqueSubQConnective=1;
		if(instructorData.getIsDistinct()) uniqueDistinct = 1;
		float orderByColumns = instructorData.getLstOrderByNodes().size();
		//float uniqueWhereSubquery = instructorData.getWhereClauseSubqueries().size();
		//float uniqueFromSubquery = instructorData.getFromClauseSubqueries().size();

		float totalWeightage = uniquePredicates + uniqueRelations + uniqueProj + instructorJoin + uniqueGroupBy + uniqueHavingClause + uniqueSubQConnective;
		totalWeightage += uniqueAggregates + uniqueSetOperators + uniqueDistinct + orderByColumns + whereSubQueryPredicate + fromSubQueryPredicate;

		float WEIGHT = 1;


		float predicateScore = SelectionScore(instructorData.getLstSelectionConditions(), studentData.getLstSelectionConditions());

		float predicateScoreTotal = predicateScore * WEIGHT;

		float projectionScore = compareProjection(instructorData.getLstProjectedCols(), studentData.getLstProjectedCols());		
		//projectionScore = instructorData.getIsDistinct() == studentData.getIsDistinct() ? projectionScore : projectionScore*0.9f;
		float projectionScoreTotal = projectionScore * WEIGHT;		

		if(instructorData.getQueryType().getType() != null && (instructorData.getQueryType().getType().equalsIgnoreCase("EXISTS") || instructorData.getQueryType().getType().equalsIgnoreCase("NOT EXISTS")))
			projectionScoreTotal = 0;


		float relationScore = compare(instructorData.getLstRelationInstances(), studentData.getLstRelationInstances());
		float relationScoreTotal=relationScore * WEIGHT;

		float joinScore = getJoinScore(instructorData, studentData);
		float joinScoreTotal=joinScore * WEIGHT;	

		float groupByScore = compareProjection(instructorData.getLstGroupByNodes(), studentData.getLstGroupByNodes());
		float groupByScoreTotal=groupByScore * WEIGHT;

		float havingClauseScore = compareHavingClause(instructorData.getLstHavingConditions(), studentData.getLstHavingConditions());
		float havingClauseScoreTotal=havingClauseScore * WEIGHT;

		float subQConnectiveScore = 0;
		//compare(instructorData.getLstSubQConnectives(),studentData.getLstSubQConnectives());
		if(instructorData.getQueryType().getType() != null && studentData.getQueryType().getType() != null)
		{
			if(instructorData.getQueryType().getType().equalsIgnoreCase(studentData.getQueryType().getType()))
				subQConnectiveScore++;
		}
		float subQConnectiveScoreTotal=subQConnectiveScore * WEIGHT;

		float aggregateScore = compareAggregates(instructorData.getLstAggregateList(), studentData.getLstAggregateList());
		float aggregateScoreTotal=aggregateScore * WEIGHT;
		float setOperatorScore = compare(instructorData.getLstSetOpetators(),studentData.getLstSetOpetators());
		float setOperatorScoreTotal=setOperatorScore * WEIGHT;

		float distinctOperatorScore = 0;

		if(instructorData.getIsDistinct() && studentData.getIsDistinct()){
			distinctOperatorScore++;
		}
		else if(!instructorData.getIsDistinct() && studentData.getIsDistinct())
		{
			distinctOperatorScore=distinctOperatorScore-0.5f;
		}

		float distinctOperatorScoreTotal=distinctOperatorScore * WEIGHT;

		float orderByScore = compareOrderBy(instructorData.getLstOrderByNodes(),studentData.getLstOrderByNodes()); ///compute order by score

		float orderByOperatorScoreTotal=orderByScore * WEIGHT;

		//float NoOfwhereSubQuryScoreTotal = uniqueWhereSubquery * WEIGHT;
		//float NoOfFromSubQuryScoreTotal = uniqueFromSubquery * WEIGHT;

		float wheresubQueryScoreTotal = (whereSubQuery.Marks) * WEIGHT ;
		float fromsubQueryScoreTotal = (fromSubQuery.Marks) * WEIGHT ;
		float student =  predicateScoreTotal + relationScoreTotal + projectionScoreTotal 
				+ joinScoreTotal + groupByScoreTotal + havingClauseScoreTotal + subQConnectiveScoreTotal + 
				aggregateScoreTotal + setOperatorScoreTotal + distinctOperatorScoreTotal + orderByOperatorScoreTotal + wheresubQueryScoreTotal + fromsubQueryScoreTotal;


		float score = student;
		marks.Marks = score;
		//if(fromSubQuery!=null&&whereSubQuery!=null)
		//marks.Marks = Configuration.OuterQuery * score + Configuration.FromSubQueries * fromSubQuery.Marks + Configuration.WhereSubQueries * whereSubQuery.Marks ;				
		logger.log(Level.FINE,"partial mark="+marks.Marks);
		return marks;
	}


	private static float weightOfSubquery(Vector<QueryStructure> instructor)
	{
		if(instructor == null || instructor.isEmpty()) return 0;
		float nodeCount = 0;
		for(QueryStructure instructorData:instructor){
			if(instructorData.getLstSelectionConditions()!=null)
				nodeCount += instructorData.getLstSelectionConditions().size()*3;
			if(instructorData.getLstRelationInstances()!=null)
				nodeCount += instructorData.getLstRelationInstances().size();
			if(instructorData.getQueryType().getType() != null && (instructorData.getQueryType().getType().equalsIgnoreCase("EXISTS") || instructorData.getQueryType().getType().equalsIgnoreCase("NOT EXISTS")))
				nodeCount+=0;
			else if(instructorData.getLstProjectedCols()!=null)
				nodeCount += instructorData.getLstProjectedCols().size();
			nodeCount += getJoinCount(instructorData)*3;
			if(instructorData.getLstGroupByNodes() != null)
				nodeCount += instructorData.getLstGroupByNodes().size();
			if(instructorData.getLstHavingConditions()!=null)
				nodeCount += instructorData.getLstHavingConditions().size();
			if(instructorData.getQueryType().getType() != null)
				nodeCount++;
			if(instructorData.getLstAggregateList()!=null)
				nodeCount += instructorData.getLstAggregateList().size(); 
			if(instructorData.getLstSetOpetators()!=null)
				nodeCount += instructorData.getLstSetOpetators().size();
			if(instructorData.getIsDistinct()) nodeCount += 1;
			if(instructorData.getOrderByNodes()!=null)
				nodeCount += instructorData.getOrderByNodes().size();
			nodeCount += weightOfSubquery(instructorData.getWhereClauseSubqueries());
			nodeCount += weightOfSubquery(instructorData.getFromClauseSubqueries());
		}
		return nodeCount;

	}
	// Compares all permutations of the queries and allocates the maximum mark.
	public static MarkInfo compareListOfQueries( Vector<QueryStructure> master, Vector<QueryStructure> slave, int level){
		float result = -10000;

		MarkInfo marks = new MarkInfo();
		ArrayList<QueryInfo> currentInfo = null;
		ArrayList<QueryInfo> maxInfo = new ArrayList<QueryInfo>();

		int masterCount = master.size();		
		int slaveCount = slave.size();

		ArrayList<ArrayList<Integer>> combinations = new ArrayList<ArrayList<Integer>>();
		if(masterCount < slaveCount){
			Vector <Boolean> leftovers=new Vector<Boolean>(slaveCount);
			leftovers.setSize(slaveCount);
			generateCombinations(combinations, masterCount, slaveCount, new ArrayList<Integer>(), 0);
			for(ArrayList<Integer> combination : combinations){
				Collections.fill(leftovers, Boolean.FALSE);
				float score = 0;
				currentInfo = new ArrayList<QueryInfo>();
				for(int i = 0; i < combination.size(); i++){					
					MarkInfo e = calculateScore(master.get(i), slave.get(combination.get(i)), level);
					currentInfo.addAll(e.SubqueryData);
					float edit = e.Marks;
					//edit *= totalNodes(master.get(i));
					score += edit;
					leftovers.set(combination.get(i), true);
				}
				int count=-1;
				for(Boolean leftover:leftovers)
				{
					count++;
					if(leftover==true)
						continue;
					score-= 0.5 * totalNodes(slave.get(count));
				}
				if(score > result){
					result = score;
					maxInfo = currentInfo;
				}
			}
		} else {						
			generateCombinations(combinations, slaveCount, masterCount, new ArrayList<Integer>(), 0);

			result = 0;
			for(ArrayList<Integer> combination : combinations){
				float score = 0;
				currentInfo = new ArrayList<QueryInfo>();
				for(int i = 0; i < combination.size(); i++){
					MarkInfo e = calculateScore(master.get(combination.get(i)), slave.get(i), level);
					currentInfo.addAll(e.SubqueryData);
					float edit = e.Marks;
					//	edit *= totalNodes(master.get(combination.get(i)));
					score += edit;
				}

				if(score > result){
					result = score;
					maxInfo = currentInfo;
				}
			}
		}

		logger.log(Level.FINE,combinations.toString());
		logger.log(Level.FINE,"size ="+combinations.size());

		marks.SubqueryData = maxInfo;
		//marks.Marks = result/(Math.abs(masterCount - slaveCount) + 1);
		marks.Marks = result;
		return marks;
	}


	public static boolean isInteger( String input )
	{
		try 
		{
			Integer.parseInt( input );
			return true;
		}
		catch( Exception e)
		{
			return false;
		}
	}
	private static void generateCombinations(ArrayList<ArrayList<Integer>> combinations, int limit,  int total, Vector<Integer> temp, Vector<Boolean> mask,int index){

		if(index == limit){
			combinations.add(new ArrayList<Integer>(temp));
			return;
		}

		for(int j = 0; j < total; j++){
			if(!mask.elementAt(j))
			{
				temp.add(j);
				mask.setElementAt(true, j);
				generateCombinations(combinations, limit, total, temp, mask,index+1);
				temp.remove(temp.size()-1);
				mask.setElementAt(false, j);
			}
		}

	}

	// Generates all the combinations
	public static void generateCombinations(ArrayList<ArrayList<Integer>> combinations, int limit,  int total, ArrayList<Integer> temp, int index){
		Vector<Boolean> mask = new Vector<Boolean> (total);
		Vector<Integer> Temp = new Vector<Integer> ();
		mask.setSize(total);
		//Temp.setSize(limit);
		Collections.fill(mask, Boolean.FALSE);
		generateCombinations(combinations,limit,total,Temp, mask,0);	
	}

	private void cleanup(){
		try {
			this.InstructorQuery.getData().closeConn();
			this.StudentQuery.getData().closeConn();
		}
		catch(Exception ex){
			logger.log(Level.SEVERE,ex.getMessage(), ex);
			//ex.printStackTrace();
		}
	}
	
	
	public static void main1(String args[]) throws Exception{		
		//Connection conn = MyConnection.getExistingDatabaseConnection();
		try(Connection conn = MyConnection.getDatabaseConnection()){

			String instructorQuery=" SELECT distinct time_slot.day FROM teaches, section, time_slot where teaches.course_id=section.course_id AND teaches.semester=section.semester AND teaches.year=section.year AND teaches.sec_id=section.sec_id AND section.time_slot_id= time_slot.time_slot_id AND section.semester='Fall' AND section.year='2009' and teaches.id='22222'";
			String studentAnswer="select time_slot.time_slot_id FROM teaches, section, time_slot where teaches.course_id=section.course_id AND teaches.semester=section.semester AND teaches.year=section.year AND teaches.sec_id=section.sec_id AND section.time_slot_id= time_slot.time_slot_id AND section.semester='Fall' AND section.year='2009' and teaches.id='22222'";
			String courseId = "CS632";
			int assignmentId =11;
			int questionId = 1;
			String rollnum = "09005027";
			int queryId = 1;		
			int maxMarks = 100;
			String oldQueryId = "A" + assignmentId + "Q" + questionId + "S" + queryId;
			String desc = "DUMMY";

			insertIntoQinfo(courseId, assignmentId, questionId, queryId, instructorQuery, desc, maxMarks, maxMarks);

			//WriteFileAndUploadDatasets.updateQueryInfo(new GenerateDataset_new(""), assignmentId, questionId, queryId, instructorQuery, "Find, for each course, the number of distinct students who have taken the course");

			try(PreparedStatement stmt = conn.prepareStatement("select * from xdata_student_queries where assignment_id = ? and question_id = ? and rollnum = ?")){
				stmt.setInt(1, assignmentId);
				stmt.setInt(2, questionId);
				stmt.setString(3, rollnum);
				try(ResultSet rs = stmt.executeQuery()){

					if(!rs.next()){		
						String insertquery = "INSERT INTO xdata_student_queries (dbid, queryid, rollnum, querystring, assignment_id, question_id,course_id) VALUES (?,?,?,?,?,?,?)";			
						try(PreparedStatement stmt1 = conn.prepareStatement(insertquery)){
							stmt1.setString(1, "d1");
							stmt1.setString(2, oldQueryId);
							stmt1.setString(3, rollnum);
							stmt1.setString(4, studentAnswer);
							stmt1.setInt(5, assignmentId);
							stmt1.setInt(6, questionId);
							stmt1.setString(7,courseId);
							stmt1.executeUpdate();
						}
					} else {
						try(PreparedStatement stmt1 = conn.prepareStatement("update xdata_student_queries set querystring = ? where assignment_id = ? and question_id = ? and rollnum = ? and course_id=?")){
							stmt1.setString(1, studentAnswer);
							stmt1.setInt(2, assignmentId);
							stmt1.setInt(3, questionId);
							stmt1.setString(4, rollnum);			
							stmt1.setString(5,courseId);
							stmt1.executeUpdate();
						}
					}
				}	

				PartialMarker part = new PartialMarker(assignmentId, questionId, queryId, courseId, rollnum);
				//MarkInfo result = part.getMarks(); commented and line following added by mathew on 19 oct 2016
				MarkInfo result = part.getMarksForQueryStructures();
				Gson gson = new Gson();
				String json = gson.toJson(result);
				String updateScoreQuery = "update xdata_student_queries set score = ?,markinfo=?,max_marks=? where assignment_id=? and question_id=? and rollnum=?";
				try(PreparedStatement ps = conn.prepareStatement(updateScoreQuery)){
					ps.setFloat(1, result.Marks);
					ps.setString(2, json);
					ps.setInt(3, maxMarks);
					ps.setFloat(4, assignmentId);
					ps.setInt(5,questionId);
					ps.setString(6, rollnum);
					ps.executeUpdate();
				}

			}
		}
	}
	
	public static void main(String args[]) throws Exception{
		//int[] questionArr= {1,2,3,4,5,6};
		int[] questionArr= {4};
		for(int questionId:questionArr) {
			System.out.println("---------------"+questionId+"----------------");
			try {
				long timeTaken=Long.MAX_VALUE;
				int exp=0;
				int maxIter=1;
				for(int i=0;i<maxIter;i++) {
					long curTimeTaken=0;
					int assignmentId=13;
					//int questionId=6;
					//String courseId="cs387_5581";  //2017
					String courseId="cs387_3874"; //2016
					//String courseId="CS 387-2015-1"; //2015
					List<Integer> queryIds=new ArrayList<>();
					Connection conn = MyConnection.getDatabaseConnection();

					PreparedStatement pstmt=conn.prepareStatement("select query_id from xdata_instructor_query where assignment_id=? and question_id=? and course_id=? order by query_id");
					pstmt.setInt(1, assignmentId);pstmt.setInt(2, questionId);pstmt.setString(3, courseId);
					ResultSet rs=pstmt.executeQuery();
					while(rs.next()) {
						queryIds.add(rs.getInt(1));
					}
					pstmt.close();
					rs.close();
					
					HashSet<String> blockRoll=new HashSet<>(); //blockRoll.add("cs3874606");
					pstmt=conn.prepareStatement("select rollnum,querystring from xdata_student_queries "
							+ "where assignment_id=? and question_id=? and course_id=? and raw_score<100 "
							+ "order by rollnum");
					pstmt.setInt(1, assignmentId);pstmt.setInt(2, questionId);pstmt.setString(3, courseId);

					int studIter=0;
					rs=pstmt.executeQuery();
					
					while(rs.next()) {
						if(studIter++<=10)
							continue;
						String roll=rs.getString(1);
						String query=rs.getString(2);
						//System.out.println(roll);
						if(blockRoll.contains(roll))
							continue;
						double maxMarks=0;
						long startTime=System.currentTimeMillis();
						for(int queryId:queryIds) {	
							try {
								
								PartialMarker marker = new PartialMarker(assignmentId, questionId, queryId,courseId,roll,query);
								MarkInfo result=marker.getMarksForQueryStructures();
								maxMarks=Math.max(maxMarks, result.Marks); /* */
								
							}catch(NullPointerException e) {
								exp++;
							}catch(StringIndexOutOfBoundsException e) {
								exp++;
							}catch(IndexOutOfBoundsException e) {
								exp++;
							}
						}
						long endTime=System.currentTimeMillis();
						if(maxMarks!=100)
							curTimeTaken+=(endTime-startTime);
						if(i==0 && maxMarks!=100)
							System.out.println(Math.round(maxMarks)); /* */
					}
					if(i==0) System.out.println(queryIds.size());
					
					timeTaken=Math.min(timeTaken, curTimeTaken);

					pstmt.close();
					rs.close();
					conn.close();
					System.out.println("Curri"+i);

				} 

				System.out.println(timeTaken);
				System.out.println(exp/maxIter);
			}catch(Exception e) {
				System.out.println("Error in processing question id:"+questionId);
				e.printStackTrace();
			}
		}
	}



}