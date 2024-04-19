package queryEdit;

import java.util.ArrayList;
import java.util.List;

import parsing.Node;
import parsing.QueryStructure;
import parsing.Pair;
import util.Utilities;

public class JoinRelation implements QueryComponent {
	public static float NodeDiff(String st,String ins)
	{
		return (float) 0.0;
	}
	private boolean isDependent(QueryStructure student,String s)
	{
		// Check dependency in ProjectionCol
		ArrayList<Node> projectionNodes = student.getLstProjectedCols();
		for(Node t: projectionNodes)
		{
			if(s.equals(t.getTableNameNo()))
			{
				return true;
			}
		}
		// Check dependency in groupBy clauses
		ArrayList<Node> groupByNodes = student.getLstGroupByNodes();
		for(Node t: groupByNodes)
		{
			if(s.equals(t.getTableNameNo()))
			{
				return true;
			}
		}
		// Check dependency in orderBy clauses
		ArrayList<Node> orderByNodes = student.getLstOrderByNodes();
		for(Node t: orderByNodes)
		{
			if(s.equals(t.getTableNameNo()))
			{
				return true;
			}
		}
		// Check dependency in selection clauses
		ArrayList<Node> selectionNodes = student.getLstSelectionConditions();
		for(Node t: selectionNodes)
		{
			if(s.equals(t.getLeft().getTableNameNo()) || s.equals(t.getRight().getTableNameNo()))
			{
				return true;
			}
		}
//		// Check dependency in having clauses
				ArrayList<Node> havingNodes = student.getLstHavingConditions();
				for(Node t: havingNodes)
				{
					if(s.equals(t.getLeft().getTableNameNo()) || s.equals(t.getRight().getTableNameNo()))
					{
						return true;
					}
				}
		return false;
	}
	@Override
	public List<Pair<QueryStructure,Float>> edit(QueryStructure student, QueryStructure instructor) throws Exception {
		List<Pair<QueryStructure,Float>> a = new ArrayList <Pair<QueryStructure,Float>>();	
		QueryStructure stu_not_matched = (QueryStructure)Utilities.copy(student);
		QueryStructure stu_matched = (QueryStructure)Utilities.copy(student);	
		QueryStructure ins_not_matched = (QueryStructure)Utilities.copy(instructor);
		for(String t:instructor.getLstRelationInstances())
		{
			if(student.getLstRelationInstances().contains(t))
			{
				ins_not_matched.getLstRelationInstances().remove(t);
			}
		}
		for(String t:student.getLstRelationInstances())
		{
			if(instructor.getLstRelationInstances().contains(t))
			{
				stu_not_matched.getLstRelationInstances().remove(t);
			}
			else
			{
				stu_matched.getLstRelationInstances().remove(t);
			}
		}
		for(String st:stu_not_matched.getLstRelationInstances())
		{
			if(isDependent(student,st))
				continue;
			for(String t: ins_not_matched.getLstRelationInstances())
			{
				QueryStructure temp = (QueryStructure)Utilities.copy(student);
				temp.getLstRelationInstances().remove(st);
				temp.getLstRelationInstances().add(t);
				Pair<QueryStructure,Float> tempCost= new Pair<QueryStructure,Float> ();
				tempCost.setFirst(temp);
				tempCost.setSecond((float) EditWeightConfig.relation);
				a.add(tempCost);
			}
		}
		return a;
	}

	@Override
	public  List<Pair<QueryStructure,Float>> add(QueryStructure student, QueryStructure instructor) throws Exception {
		
		List<Pair<QueryStructure,Float>> a = new ArrayList <Pair<QueryStructure,Float>>();
		for(String t:instructor.getLstRelationInstances())
		{
			if(!student.getLstRelationInstances().contains(t))
			{
				QueryStructure temp = (QueryStructure)Utilities.copy(student);
				//temp.getLstRelations().add(t);
				temp.getLstRelationInstances().add(t);
				Pair<QueryStructure,Float> tempCost= new Pair<QueryStructure,Float> ();
				tempCost.setFirst(temp);
				tempCost.setSecond((float) EditWeightConfig.relation);
				a.add(tempCost);;
			}
		}
		return a;
	}
	
	@Override
	public  List<Pair<QueryStructure,Float>> remove(QueryStructure student, QueryStructure instructor) throws Exception {
		List<Pair<QueryStructure,Float>> a = new ArrayList <Pair<QueryStructure,Float>>();
		for(String t:student.getLstRelationInstances())
		{
			if(!instructor.getLstRelationInstances().contains(t) && !isDependent(student,t))
			{
				QueryStructure temp = (QueryStructure)Utilities.copy(student);
				temp.getLstRelationInstances().remove(t);
				Pair<QueryStructure,Float> tempCost= new Pair<QueryStructure,Float> ();
				tempCost.setFirst(temp);
				tempCost.setSecond((float) EditWeightConfig.relation);
				a.add(tempCost);
			}	
		}
		return a;
	}

}
