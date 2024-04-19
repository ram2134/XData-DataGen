/**
 * 
 */
package queryEdit;

import java.util.ArrayList;
import java.util.List;

import parsing.Node;
import parsing.QueryStructure;
import parsing.Pair;
import util.Utilities;

/**
 * @author udbhas
 *
 */
public class OrderBy implements QueryComponent {

	/* (non-Javadoc)
	 * @see partialMarking.queryEdit.QueryComponent#edit(parsing.QueryStructure, parsing.QueryStructure)
	 */
	@Override
	public  List<Pair<QueryStructure,Float>> edit(QueryStructure student, QueryStructure instructor) throws Exception {
		List<Pair<QueryStructure,Float>> a = new ArrayList <Pair<QueryStructure,Float>>();
		QueryStructure stu_not_matched = (QueryStructure)Utilities.copy(student);
		QueryStructure stu_matched = (QueryStructure)Utilities.copy(student);	
		QueryStructure ins_not_matched = (QueryStructure)Utilities.copy(instructor);
		for(Node t:instructor.getLstOrderByNodes())
		{
			if(student.getLstOrderByNodes().contains(t))
			{
				ins_not_matched.getLstOrderByNodes().remove(t);
			}
		}
		for(Node t:student.getLstOrderByNodes())
		{
			if(instructor.getLstOrderByNodes().contains(t))
			{
				stu_not_matched.getLstOrderByNodes().remove(t);
			}
			else
			{
				stu_matched.getLstOrderByNodes().remove(t);
			}
		}
		int c=0;
		int size = student.getLstOrderByNodes().size();
		for(Node st:student.getLstOrderByNodes())
		{
			if(stu_not_matched.getLstOrderByNodes().contains(st))
			{
				for(Node t: ins_not_matched.getLstOrderByNodes())
				{
					QueryStructure temp = (QueryStructure)Utilities.copy(student);
					if(c==size-1)
						temp.getLstOrderByNodes().add(t);
					else
						temp.getLstOrderByNodes().add(c,t);
					temp.getLstOrderByNodes().remove(st);
					Pair<QueryStructure,Float> tempCost= new Pair<QueryStructure,Float> ();
					tempCost.setFirst(temp);
					tempCost.setSecond((float) EditWeightConfig.orderby);
					a.add(tempCost);
				}
			}
			c++;
		}
		return a;
	}

	/* (non-Javadoc)
	 * @see partialMarking.queryEdit.QueryComponent#add(parsing.QueryStructure, parsing.QueryStructure)
	 */
	@Override
	public  List<Pair<QueryStructure,Float>> add(QueryStructure student, QueryStructure instructor) throws Exception {
		List<Pair<QueryStructure,Float>> a = new ArrayList <Pair<QueryStructure,Float>>();
		for(Node t:instructor.getLstOrderByNodes())
		{
			if(!student.getLstOrderByNodes().contains(t))
			{
				int size=student.getLstOrderByNodes().size();
				int i=0;
				while(i<size)
				{
					QueryStructure temp = (QueryStructure)Utilities.copy(student);
					temp.getLstOrderByNodes().add(i,t);
					Pair<QueryStructure,Float> tempCost= new Pair<QueryStructure,Float> ();
					tempCost.setFirst(temp);
					tempCost.setSecond((float) EditWeightConfig.orderby);
					a.add(tempCost);
					i++;
				}
				QueryStructure temp = (QueryStructure)Utilities.copy(student);
				temp.getLstOrderByNodes().add(t);
				Pair<QueryStructure,Float> tempCost= new Pair<QueryStructure,Float> ();
				tempCost.setFirst(temp);
				tempCost.setSecond((float) EditWeightConfig.orderby);
				a.add(tempCost);
			}
		}
		return a;
	}

	/* (non-Javadoc)
	 * @see partialMarking.queryEdit.QueryComponent#remove(parsing.QueryStructure, parsing.QueryStructure)
	 */
	@Override
	public  List<Pair<QueryStructure,Float>> remove(QueryStructure student, QueryStructure instructor) throws Exception {
		List<Pair<QueryStructure,Float>> a = new ArrayList <Pair<QueryStructure,Float>>();
		for(Node t:student.getLstOrderByNodes())
		{
			if(!instructor.getLstOrderByNodes().contains(t))
			{
				QueryStructure temp = (QueryStructure)Utilities.copy(student);
				temp.getLstOrderByNodes().remove(t);
				Pair<QueryStructure,Float> tempCost= new Pair<QueryStructure,Float> ();
				tempCost.setFirst(temp);
				tempCost.setSecond((float) EditWeightConfig.orderby);
				a.add(tempCost);
			}	
		}
		return a;
	}
	public  List<Pair<QueryStructure,Float>> move(QueryStructure student, QueryStructure instructor) throws Exception {
		List<Pair<QueryStructure,Float>> a = new ArrayList <Pair<QueryStructure,Float>>();
		int c=0;
		for(Node t:student.getLstOrderByNodes())
		{
			QueryStructure temp = (QueryStructure)Utilities.copy(student);
			temp.getLstOrderByNodes().remove(t);
			int size=temp.getLstOrderByNodes().size();
			int i=0;
			while(i<size)
			{
				if(c!=i)
				{
					QueryStructure temp1 = (QueryStructure)Utilities.copy(temp);
					temp1.getLstOrderByNodes().add(i,t);
					Pair<QueryStructure,Float> tempCost= new Pair<QueryStructure,Float> ();
					tempCost.setFirst(temp);
					tempCost.setSecond((float) EditWeightConfig.orderby);
					a.add(tempCost);
				}
				i++;
			}
			if(c!=i)
			{
				QueryStructure temp1 = (QueryStructure)Utilities.copy(temp);
				temp1.getLstOrderByNodes().add(t);
				Pair<QueryStructure,Float> tempCost= new Pair<QueryStructure,Float> ();
				tempCost.setFirst(temp);
				tempCost.setSecond((float) EditWeightConfig.orderby);
				a.add(tempCost);
			}
			c++;
		}
		return a;
	}
	
}
