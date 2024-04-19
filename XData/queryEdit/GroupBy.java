package queryEdit;

import java.util.ArrayList;
import java.util.List;

import parsing.Node;
import parsing.QueryStructure;
import parsing.Pair;
import util.Utilities;

public class GroupBy implements QueryComponent {

	@Override
	public List<Pair<QueryStructure,Float>> edit(QueryStructure student, QueryStructure instructor) throws Exception{
		List<Pair<QueryStructure,Float>> a = new ArrayList <Pair<QueryStructure,Float>>();
		QueryStructure stu_not_matched = (QueryStructure)Utilities.copy(student);
		QueryStructure stu_matched = (QueryStructure)Utilities.copy(student);	
		QueryStructure ins_not_matched = (QueryStructure)Utilities.copy(instructor);
		for(Node t:instructor.getLstGroupByNodes())
		{
			if(student.getLstGroupByNodes().contains(t))
			{
				ins_not_matched.getLstGroupByNodes().remove(t);
			}
		}
		for(Node t:student.getLstGroupByNodes())
		{
			if(instructor.getLstGroupByNodes().contains(t))
			{
				stu_not_matched.getLstGroupByNodes().remove(t);
			}
			else
			{
				stu_matched.getLstGroupByNodes().remove(t);
			}
		}
		for(Node st:stu_not_matched.getLstGroupByNodes())
		{
			for(Node t: ins_not_matched.getLstGroupByNodes())
			{
				QueryStructure temp = (QueryStructure)Utilities.copy(student);
				temp.getLstGroupByNodes().remove(st);
				//temp.getLstProjectedCols().remove(st);
				temp.getLstGroupByNodes().add(t);
				Pair<QueryStructure,Float> tempCost= new Pair<QueryStructure,Float> ();
				tempCost.setFirst(temp);
				tempCost.setSecond((float) EditWeightConfig.groupby);
				a.add(tempCost);
			}
		}
		return a;
	}

	@Override
	public List<Pair<QueryStructure,Float>> add(QueryStructure student, QueryStructure instructor) throws Exception {
		List<Pair<QueryStructure,Float>> a = new ArrayList <Pair<QueryStructure,Float>>();
		for(Node t:instructor.getLstGroupByNodes())
		{
			if(!student.getLstGroupByNodes().contains(t))
			{
				QueryStructure temp = (QueryStructure)Utilities.copy(student);
				temp.getLstGroupByNodes().add(t);
				Pair<QueryStructure,Float> tempCost= new Pair<QueryStructure,Float> ();
				tempCost.setFirst(temp);
				tempCost.setSecond((float) EditWeightConfig.groupby);
				a.add(tempCost);
			}
		}
		return a;
	}

	@Override
	public List<Pair<QueryStructure,Float>> remove(QueryStructure student, QueryStructure instructor) throws Exception{
		
		List<Pair<QueryStructure,Float>> a = new ArrayList <Pair<QueryStructure,Float>>();
		for(Node t:student.getLstGroupByNodes())
		{
			if(!instructor.getLstGroupByNodes().contains(t))
			{
				QueryStructure temp = (QueryStructure)Utilities.copy(student);
				temp.getLstGroupByNodes().remove(t);
				Pair<QueryStructure,Float> tempCost= new Pair<QueryStructure,Float> ();
				tempCost.setFirst(temp);
				tempCost.setSecond((float) EditWeightConfig.groupby);
				a.add(tempCost);
			}	
		}
		
		return a;
	}

}
