package partialMarking;

import java.util.ArrayList;

import parsing.AggregateFunction;
import parsing.JoinClauseInfo;
import parsing.Node;
import parsing.QueryStructure;

public class QueryInfo {
	 
		
		
		//public ArrayList<ArrayList<String>> Predicates;
		
		public ArrayList<String> StudentPredicates;
		
		public ArrayList<String> InstructorPredicates;
		
		public float studentPredicateMarks;
		public float instructorPredicateMarks;
		
		//public ArrayList<ArrayList<String>> Relations;
		
		public ArrayList<String> StudentRelations;
		
		public ArrayList<String> InstructorRelations;
		public float studentRelationsMarks;
		public float instructorRelationMarks;
		
		//public ArrayList<ArrayList<String>> Projections;
		public ArrayList<String> StudentProjections;
		
		public ArrayList<String> InstructorProjections;
		public float studentProjectionMarks;
		public float instructorProjectionMarks;
		
		//public ArrayList<ArrayList<String>> GroupBy;
		public ArrayList<String> StudentGroupBy;
		
		public ArrayList<String> InstructorGroupBy;
		public float studentGroupbyMarks;
		public float instructorGroupbyMarks;
		
		
		//public ArrayList<ArrayList<String>> HavingClause;
		public ArrayList<String> InstructorHavingClause;
		
		public ArrayList<String> StudentHavingClause;
		public float studentHavingMarks;
		public float instructorHavingMarks;
		
		//public ArrayList<ArrayList<String>> SubQConnective;
		public ArrayList<String> InstructorSubQConnective;
		
		public ArrayList<String> StudentSubQConnective;
		public float studentSubqMarks;
		public float instructorSubqMarks;
		
		//public ArrayList<ArrayList<String>> Aggregates;
		public ArrayList<String> InstructorAggregates;
		
		public ArrayList<String> StudentAggregates;
		public float studentAggregateMarks;
		public float instructorAggregateMarks;
		
		//public ArrayList<ArrayList<String>> SetOperators;
		public ArrayList<String> InstructorSetOperators;
		
		public ArrayList<String> StudentSetOperators;
		public float instructorSetOperatorMarks;
		public float studentSetOperatorMarks;
		
		//public ArrayList<Integer> InnerJoins;
	//	public int StudentInnerJoins;
		//public int InstructorInnerJoins;
		public float studentInnerJoinMarks;
		public float instructorInnerJoinMarks;
		
		//public ArrayList<Integer> OuterJoins;
		//public int StudentOuterJoins;
		//public int InstructorOuterJoins;
		public float studentOuterJoinMarks;
		public float instructorOuterJoinMarks;
		
		
		public ArrayList<String> InstructorInnerJoins;
		public ArrayList<String> StudentInnerJoins;
		public ArrayList<String> InstructorOuterJoins;
		public ArrayList<String> StudentOuterJoins;
		
		//public ArrayList<Boolean> Distinct;
		public boolean studentDistinct;
		
		public boolean instructorDistinct;
		public float studentDistinctMarks;
		public float instructorDistinctMarks;
		
		public int Level;
		
		
		static QueryInfo getQueryInfo(QueryStructure instructorData, QueryStructure studentData, int level){

			//System.out.println("Under populateQueryInfo");
			QueryInfo qInfo = new QueryInfo();
			qInfo.Level = level;


			if(instructorData.getIsDistinct())
				qInfo.instructorDistinct=true;
			else qInfo.instructorDistinct=false;
			if(studentData.getIsDistinct())
				qInfo.studentDistinct=true;
			else qInfo.studentDistinct=false; 


			for(Node n: instructorData.getLstSelectionConditions()){
				qInfo.InstructorPredicates.add(n.toString());
			}
			//getLstSelectionConditions
			for(Node n: studentData.getLstSelectionConditions()){
				qInfo.StudentPredicates.add(n.toString());
			}

			for(Node n: instructorData.getLstProjectedCols()){
				qInfo.InstructorProjections.add(n.toString());
			}

			for(Node n: studentData.getLstProjectedCols()){
				qInfo.StudentProjections.add(n.toString());
			}

			for(Node n: instructorData.getLstGroupByNodes()){
				qInfo.InstructorGroupBy.add(n.toString());
			}

			for(Node n: studentData.getLstGroupByNodes()){
				qInfo.StudentGroupBy.add(n.toString());
			}

			for(String n: instructorData.getLstRelations()){
				qInfo.InstructorRelations.add(n);
			}

			for(String n: studentData.getLstRelations()){
				qInfo.StudentRelations.add(n);
			}

			for(Node n : instructorData.getLstHavingConditions()){
				qInfo.InstructorHavingClause.add(n.toString());
			}

			for(Node n : studentData.getLstHavingConditions()){
				qInfo.StudentHavingClause.add(n.toString());
			}

			for(String n : instructorData.getLstSubQConnectives()){
				qInfo.InstructorSubQConnective.add(n);
			}
			for(String n : studentData.getLstSubQConnectives()){
				qInfo.StudentSubQConnective.add(n);
			}

			for(AggregateFunction n : instructorData.getLstAggregateList()){
				qInfo.InstructorAggregates.add(n.toString());
			}
			for(AggregateFunction n : studentData.getLstAggregateList()){
				qInfo.StudentAggregates.add(n.toString());
			}

			ArrayList<String> instrInnerJoin =new ArrayList<String>();
			ArrayList<String> studentInnerJoin =new ArrayList<String>();
			ArrayList<String> instrOuterJoin =new ArrayList<String>();
			ArrayList<String> studentOuterJoin =new ArrayList<String>();

			if( (instructorData != null && instructorData.getLstJoinConditions()!=null && instructorData.getLstJoinConditions().size() > 0) || 
					(studentData != null &&  
					studentData.getLstJoinConditions()!=null &&  studentData.getLstJoinConditions().size() > 0)){


				for(Node n : instructorData.getLstJoinConditions()){
					if(n.getJoinType()==null) continue;
					if(n.getJoinType().equalsIgnoreCase(JoinClauseInfo.innerJoin)){
						instrInnerJoin.add(n.toString());
					}
					else if(n.getJoinType().equalsIgnoreCase(JoinClauseInfo.leftOuterJoin)
							|| n.getJoinType().equalsIgnoreCase(JoinClauseInfo.rightOuterJoin)
							||n.getJoinType().equalsIgnoreCase(JoinClauseInfo.fullOuterJoin)){
						instrOuterJoin.add(n.toString());
					}
				}
				for(Node n : studentData.getLstJoinConditions()){
					if(n.getJoinType()==null) continue;
					if(n.getJoinType().equalsIgnoreCase(JoinClauseInfo.innerJoin)){
						studentInnerJoin.add(n.toString());
					}else if(n.getJoinType().equalsIgnoreCase(JoinClauseInfo.leftOuterJoin)
							|| n.getJoinType().equalsIgnoreCase(JoinClauseInfo.rightOuterJoin)
							||n.getJoinType().equalsIgnoreCase(JoinClauseInfo.fullOuterJoin)){
						studentOuterJoin.add(n.toString());
					}
				}

			}

			qInfo.InstructorInnerJoins = instrInnerJoin;
			qInfo.StudentInnerJoins = studentInnerJoin;

			qInfo.InstructorOuterJoins = instrOuterJoin;
			qInfo.StudentOuterJoins = studentOuterJoin;

			return qInfo;
		}

		
		
		public QueryInfo(){
			//this.Predicates = new ArrayList<ArrayList<String>>();
			this.InstructorPredicates = new ArrayList<String>();
			this.StudentPredicates = new ArrayList<String>();
			this.instructorPredicateMarks = 0;
			this.studentPredicateMarks = 0;
			
			//this.Relations = new ArrayList<ArrayList<String>>();
			this.InstructorRelations = new ArrayList<String>();
			this.StudentRelations = new ArrayList<String>();
			this.instructorRelationMarks = 0;
			this.studentRelationsMarks = 0;
			
			//this.Projections = new ArrayList<ArrayList<String>>();
			this.InstructorProjections = new ArrayList<String>();
			this.StudentProjections = new ArrayList<String>();
			this.instructorProjectionMarks = 0;
			this.studentProjectionMarks = 0;
			
			//this.GroupBy = new ArrayList<ArrayList<String>>();
			this.InstructorGroupBy = new ArrayList<String>();
			this.StudentGroupBy = new ArrayList<String>();
			this.instructorGroupbyMarks = 0;
			this.studentGroupbyMarks = 0;
			
			//this.HavingClause= new ArrayList<ArrayList<String>>();
			this.InstructorHavingClause = new ArrayList<String>();
			this.StudentHavingClause = new ArrayList<String>();
			this.instructorHavingMarks = 0 ;
			this.studentHavingMarks = 0;
			
			//this.SubQConnective = new ArrayList<ArrayList<String>>();
			this.InstructorSubQConnective = new ArrayList<String>();
			this.StudentSubQConnective = new ArrayList<String>();
			this.instructorSubqMarks = 0 ;
			this.studentSubqMarks = 0;
			//this.Aggregates = new ArrayList<ArrayList<String>>();
			this.InstructorAggregates = new ArrayList<String>();
			this.StudentAggregates = new ArrayList<String>();
			this.instructorAggregateMarks = 0;
			this.studentAggregateMarks = 0;
			
			//this.SetOperators = new ArrayList<ArrayList<String>>();
			this.InstructorSetOperators = new ArrayList<String>();
			this.StudentSetOperators = new ArrayList<String>();
			this.instructorSetOperatorMarks = 0;
			this.studentSetOperatorMarks = 0;
			
			studentDistinct = false;
			instructorDistinct = false;
			this.studentDistinctMarks = 0;
			this.studentDistinctMarks = 0;
			
		}
	}

	
/*	public QueryInfo instructorInfo;
	public QueryInfo studentInfo;
	public int Level;
	public ArrayList<String> Predicates;
	public ArrayList<String> Relations;
	public ArrayList<String>  Projections;
	public ArrayList<String> GroupBy;
	public ArrayList<String> HavingClause;
	public ArrayList<String> SubQConnective;
	public ArrayList<String> Aggregates;
	public ArrayList<String> SetOperators;
	public int InnerJoins;
	public int OuterJoins;	
	public QueryInfo(){
		//this.InstructorQuery = new QueryData();
		//this.StudentQuery = new QueryData();
		this.Predicates = new ArrayList<String>();
		this.Relations = new ArrayList<String>();		
		this.Projections = new ArrayList<String>();
		this.GroupBy = new ArrayList<String>();
		this.HavingClause = new ArrayList<String>();
		this.SubQConnective = new ArrayList<String>();	
		this.Aggregates = new ArrayList<String>();		
		this.SetOperators = new ArrayList<String>();
	}
	}*/
	

