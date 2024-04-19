package partialMarking;

import java.io.Serializable;
import java.util.ArrayList;


public class MarkInfo implements Serializable {
	
	private static final long serialVersionUID = 1L;

	public float Marks;
	
	public int AssignmentId;
	
	public int QuestionId;
	
	public String StudentQuery;
	
	public String InstructorQuery;
	
	public ArrayList<QueryInfo> SubqueryData;
	
	public PartialMarkerConfig Configuration;
	
	/*Check if these are required for display*/
	
	public float studentPredicateMarks;
	public float instructorPredicateMarks;
	public float studentRelationsMarks;
	public float instructorRelationMarks;
	public float studentProjectionMarks;
	public float instructorProjectionMarks;
	public float studentGroupbyMarks;
	public float instructorGroupbyMarks;
	public float studentHavingMarks;
	public float instructorHavingMarks;
	public float studentSubqMarks;
	public float instructorSubqMarks;
	public float studentAggregateMarks;
	public float instructorAggregateMarks;
	public float instructorSetOperatorMarks;
	public float studentSetOperatorMarks;
	public float studentInnerJoinMarks;
	public float instructorInnerJoinMarks;
	public float studentOuterJoinMarks;
	public float instructorOuterJoinMarks;
	public float studentDistinctMarks;
	public float instructorDistinctMarks;
	
	
	public MarkInfo(){
		this.Marks = 0;
		this.SubqueryData = new ArrayList<QueryInfo>();
		this.Configuration = new PartialMarkerConfig();
		this.instructorPredicateMarks = 0;
		this.studentPredicateMarks = 0;
		this.instructorRelationMarks = 0;
		this.studentRelationsMarks = 0;
		this.instructorProjectionMarks = 0;
		this.studentProjectionMarks = 0;
		this.instructorGroupbyMarks = 0;
		this.studentGroupbyMarks = 0;
		this.instructorHavingMarks = 0 ;
		this.studentHavingMarks = 0;
		this.instructorSubqMarks = 0 ;
		this.studentSubqMarks = 0;
		this.instructorAggregateMarks = 0;
		this.studentAggregateMarks = 0;
		this.instructorSetOperatorMarks = 0;
		this.studentSetOperatorMarks = 0;
		this.studentDistinctMarks = 0;
		this.studentDistinctMarks = 0;
		
	}
}
