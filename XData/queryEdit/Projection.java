package queryEdit;

import java.util.ArrayList;
import java.util.List;

import parsing.Node;
import parsing.QueryStructure;
import parsing.Pair;
import util.Utilities;

public class Projection implements QueryComponent {

	@Override
	public List<Pair<QueryStructure,Float>> edit(QueryStructure student, QueryStructure instructor) throws Exception {
		List<Pair<QueryStructure,Float>> a = new ArrayList <Pair<QueryStructure,Float>>();
		QueryStructure stu_not_matched = (QueryStructure)Utilities.copy(student);
		QueryStructure stu_matched = (QueryStructure)Utilities.copy(student);	
		QueryStructure ins_not_matched = (QueryStructure)Utilities.copy(instructor);
		for(Node t:instructor.getLstProjectedCols())
		{
			if(student.getLstProjectedCols().contains(t))
			{
				ins_not_matched.getLstProjectedCols().remove(t);
			}
		}
		for(Node t:student.getLstProjectedCols())
		{
			if(instructor.getLstProjectedCols().contains(t))
			{
				stu_not_matched.getLstProjectedCols().remove(t);
			}
			else
			{
				stu_matched.getLstProjectedCols().remove(t);
			}
		}
		for(Node st:stu_not_matched.getLstProjectedCols())
		{
			for(Node t: ins_not_matched.getLstProjectedCols())
			{
				QueryStructure temp = (QueryStructure)Utilities.copy(student);
				temp.getLstProjectedCols().remove(st);
				temp.getLstProjectedCols().add(t);
				Pair<QueryStructure,Float> tempCost= new Pair<QueryStructure,Float> ();
				tempCost.setFirst(temp);
				tempCost.setSecond((float) EditWeightConfig.projection);
				a.add(tempCost);
			}
		}
		return a;
	}

	@Override
	public List<Pair<QueryStructure,Float>> add(QueryStructure student, QueryStructure instructor) throws Exception {
		List<Pair<QueryStructure,Float>> a = new ArrayList <Pair<QueryStructure,Float>>();
		for(Node t:instructor.getLstProjectedCols())
		{
			if(!student.getLstProjectedCols().contains(t))
			{
				QueryStructure temp = (QueryStructure)Utilities.copy(student);
				temp.getLstProjectedCols().add(t);
				Pair<QueryStructure,Float> tempCost= new Pair<QueryStructure,Float> ();
				tempCost.setFirst(temp);
				tempCost.setSecond((float) EditWeightConfig.projection);
				a.add(tempCost);
			}
		}
		return a;
	}

	@Override
	public List<Pair<QueryStructure,Float>> remove(QueryStructure student, QueryStructure instructor) throws Exception {
		List<Pair<QueryStructure,Float>> a = new ArrayList <Pair<QueryStructure,Float>>();
		for(Node t:student.getLstProjectedCols())
		{
			if(!instructor.getLstProjectedCols().contains(t))
			{
				QueryStructure temp = (QueryStructure)Utilities.copy(student);
				temp.getLstProjectedCols().remove(t);
				Pair<QueryStructure,Float> tempCost= new Pair<QueryStructure,Float> ();
				tempCost.setFirst(temp);
				tempCost.setSecond((float) EditWeightConfig.projection / 2);
				a.add(tempCost);
			}	
		}
		
		return a;
	}

}
