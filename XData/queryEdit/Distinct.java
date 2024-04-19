package queryEdit;

import java.util.ArrayList;
import java.util.List;

import parsing.QueryStructure;
import parsing.Pair;
import util.Utilities;

public class Distinct implements QueryComponent {

	@Override
	public List<Pair<QueryStructure,Float>> edit(QueryStructure student, QueryStructure instructor) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Pair<QueryStructure,Float>> add(QueryStructure student, QueryStructure instructor) throws Exception {
		List<Pair<QueryStructure,Float>> a = new ArrayList <Pair<QueryStructure,Float>>();
		if(!student.getLstProjectedCols().containsAll(instructor.getLstProjectedCols())) return a;
		if(instructor.getIsDistinct()==true && student.getIsDistinct()==false)
		{
			QueryStructure temp = (QueryStructure)Utilities.copy(student);
			temp.setIsDistinct(true);
			Pair<QueryStructure,Float> tempCost= new Pair<QueryStructure,Float> ();
			tempCost.setFirst(temp);
			tempCost.setSecond((float) EditWeightConfig.distinct);
			a.add(tempCost);
		}
		return a;
	}

	@Override
	public List<Pair<QueryStructure,Float>> remove(QueryStructure student, QueryStructure instructor) throws Exception {
		List<Pair<QueryStructure,Float>> a = new ArrayList <Pair<QueryStructure,Float>>();
		if(!student.getLstProjectedCols().containsAll(instructor.getLstProjectedCols())) return a;
		if(instructor.getIsDistinct()==false && student.getIsDistinct()==true)
		{
			QueryStructure temp = (QueryStructure)Utilities.copy(student);
			temp.setIsDistinct(false);
			Pair<QueryStructure,Float> tempCost= new Pair<QueryStructure,Float> ();
			tempCost.setFirst(temp);
			tempCost.setSecond((float) EditWeightConfig.distinct);
			a.add(tempCost);
		}
		return a;
	}

}
