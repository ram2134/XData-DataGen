package queryEdit;

import java.util.ArrayList;
import java.util.List;

import parsing.Node;
import parsing.QueryStructure;
import util.Utilities;
import parsing.Pair;
public class Selection implements QueryComponent {

	public static float NodeDiff(Node n1,Node n2)
	{
		float total_score=0;
		if(n1.getOperator().equals(n2.getOperator()))
			total_score++;		

		//if left node of n1 is a column reference
		boolean tag=true;
		if(n1.getLeft().getNodeType().equals(Node.getColRefType())){
			if(!n2.getLeft().getNodeType().equals(Node.getColRefType()))
				tag=  false;
			if(!n1.getLeft().getTable().getTableName().equals(n2.getLeft().getTable().getTableName()))
				tag=  false;

			if(!n1.getLeft().getTableNameNo().equals(n2.getLeft().getTableNameNo()))
				tag=  false;
			if(!n1.getLeft().getColumn().getColumnName().equals(n2.getLeft().getColumn().getColumnName()))
				tag=  false;
		}
		//if left node of n1 is a constant value
		if(n1.getLeft().getNodeType().equals(Node.getValType())){
			if(!n2.getLeft().getNodeType().equals(Node.getValType()))
				tag=   false;
			if(!n1.getLeft().getStrConst().equals(n2.getLeft().getStrConst()))
				tag=  false;
		}
		if(tag)
			total_score++;
		tag=true;
		if(n1.getRight().getNodeType().equals(Node.getColRefType())){

			if(!n2.getRight().getNodeType().equals(Node.getColRefType()))
				tag=   false;

			if(!n1.getRight().getTable().getTableName().equals(n2.getRight().getTable().getTableName()))
				tag=  false;

			if(!n1.getRight().getTableNameNo().equals(n2.getRight().getTableNameNo()))
				tag=  false;

			if(!n1.getRight().getColumn().getColumnName().equals(n2.getRight().getColumn().getColumnName()))
				tag=  false;
		}

		if(n1.getRight().getNodeType().equals(Node.getValType())){
			if(!n2.getRight().getNodeType().equals(Node.getValType()))
				tag=   false;

			if(!n1.getRight().getStrConst().equals(n2.getRight().getStrConst()))
				tag=  false;
		}

		if(tag)
			total_score++;

			
		return (float) total_score;
	}
	public static List<Pair<QueryStructure,Float>> selectionClauseEdit(QueryStructure I, QueryStructure S,Node ins, Node st) throws Exception
	{
		List<Pair<QueryStructure,Float>> a = new ArrayList <Pair<QueryStructure,Float>>();
		if(!ins.getOperator().equals(st.getOperator()))
		{
			QueryStructure temp = (QueryStructure)Utilities.copy(S);
			temp.getLstSelectionConditions().remove(st);
			Node n =  (Node)Utilities.copy(st);
			n.setOperator(ins.getOperator());
			temp.getLstSelectionConditions().add(n);
			Pair<QueryStructure,Float> tempCost= new Pair<QueryStructure,Float> ();
			tempCost.setFirst(temp);
			tempCost.setSecond((float)EditWeightConfig.selectOpertor);
			a.add(tempCost);
		}
		boolean tag=true;
		if(ins.getLeft().getNodeType().equals(Node.getColRefType())){
			if(!st.getLeft().getNodeType().equals(Node.getColRefType()))
				tag=  false;
			if(!ins.getLeft().getTable().getTableName().equals(st.getLeft().getTable().getTableName()))
				tag=  false;

			if(!ins.getLeft().getTableNameNo().equals(st.getLeft().getTableNameNo()))
				tag=  false;
			if(!ins.getLeft().getColumn().getColumnName().equals(st.getLeft().getColumn().getColumnName()))
				tag=  false;
		}
		if(ins.getLeft().getNodeType().equals(Node.getValType())){
			if(!st.getLeft().getNodeType().equals(Node.getValType()))
				tag=   false;
			if(!ins.getLeft().getStrConst().equals(st.getLeft().getStrConst()))
				tag=  false;
		}
		if(!tag)  // left of ins not matched with left of st
		{
			QueryStructure temp = (QueryStructure)Utilities.copy(S);
			temp.getLstSelectionConditions().remove(st);
			Node n =  (Node)Utilities.copy(st);
			n.setLeft(ins.getLeft());
			temp.getLstSelectionConditions().add(n);
			Pair<QueryStructure,Float> tempCost= new Pair<QueryStructure,Float> ();
			tempCost.setFirst(temp);
			tempCost.setSecond((float)EditWeightConfig.selectLeft);
			a.add(tempCost);
		}
		
		tag=true;
		if(ins.getRight().getNodeType().equals(Node.getColRefType())){

			if(!st.getRight().getNodeType().equals(Node.getColRefType()))
				tag=   false;

			if(!ins.getRight().getTable().getTableName().equals(st.getRight().getTable().getTableName()))
				tag=  false;

			if(!ins.getRight().getTableNameNo().equals(st.getRight().getTableNameNo()))
				tag=  false;

			if(!ins.getRight().getColumn().getColumnName().equals(st.getRight().getColumn().getColumnName()))
				tag=  false;
		}

		if(ins.getRight().getNodeType().equals(Node.getValType())){
			if(!st.getRight().getNodeType().equals(Node.getValType()))
				tag=   false;

			if(!ins.getRight().getStrConst().equals(st.getRight().getStrConst()))
				tag=  false;
		}
		if(!tag)  // right of ins not matched with right of st
		{
			QueryStructure temp = (QueryStructure)Utilities.copy(S);
			temp.getLstSelectionConditions().remove(st);
			Node n =  (Node)Utilities.copy(st);
			n.setRight(ins.getRight());
			temp.getLstSelectionConditions().add(n);
			Pair<QueryStructure,Float> tempCost= new Pair<QueryStructure,Float> ();
			tempCost.setFirst(temp);
			tempCost.setSecond((float)EditWeightConfig.selectRight);
			a.add(tempCost);
		}
		return a;
	}
	public List<Pair<QueryStructure,Float>> edit(QueryStructure student, QueryStructure instructor) throws Exception {
		List<Pair<QueryStructure,Float>> a = new ArrayList <Pair<QueryStructure,Float>>();
		QueryStructure stu_not_matched = (QueryStructure)Utilities.copy(student);
		QueryStructure stu_matched = (QueryStructure)Utilities.copy(student);	
		QueryStructure ins_not_matched = (QueryStructure)Utilities.copy(instructor);
		for(Node t:instructor.getLstSelectionConditions())
		{
			if(student.getLstSelectionConditions().contains(t))
			{
				ins_not_matched.getLstSelectionConditions().remove(t);
			}
		}
		for(Node t:student.getLstSelectionConditions())
		{
			if(instructor.getLstSelectionConditions().contains(t))
			{
				stu_not_matched.getLstSelectionConditions().remove(t);
			}
			else
			{
				stu_matched.getLstSelectionConditions().remove(t);
			}
		}
		for(Node st:stu_not_matched.getLstSelectionConditions())
		{
			for(Node t: ins_not_matched.getLstSelectionConditions())
			{
				List<Pair<QueryStructure,Float>> tempCost = selectionClauseEdit(instructor,student,t,st);
				a.addAll(tempCost);
			}
		}
		return a;
	}

	@Override
	public List<Pair<QueryStructure,Float>> add(QueryStructure student, QueryStructure instructor) throws Exception {
		List<Pair<QueryStructure,Float>> a = new ArrayList <Pair<QueryStructure,Float>>();
		for(Node t:instructor.getLstSelectionConditions())
		{
			if(!student.getLstSelectionConditions().contains(t))
			{
				QueryStructure temp = (QueryStructure)Utilities.copy(student);
				temp.getLstSelectionConditions().add(t);
				Pair<QueryStructure,Float> tempCost= new Pair<QueryStructure,Float> ();
				tempCost.setFirst(temp);
				tempCost.setSecond((float) EditWeightConfig.selectOpertor + EditWeightConfig.selectRight + EditWeightConfig.selectLeft);
				a.add(tempCost);
			}
		}
		return a;
	}

	@Override
	public List<Pair<QueryStructure,Float>> remove(QueryStructure student, QueryStructure instructor) throws Exception {
		List<Pair<QueryStructure,Float>> a = new ArrayList <Pair<QueryStructure,Float>>();
		for(Node t:student.getLstSelectionConditions())
		{
			if(!instructor.getLstSelectionConditions().contains(t))
			{
				QueryStructure temp = (QueryStructure)Utilities.copy(student);
				temp.getLstSelectionConditions().remove(t);
				Pair<QueryStructure,Float> tempCost= new Pair<QueryStructure,Float> ();
				tempCost.setFirst(temp);
				tempCost.setSecond((float) EditWeightConfig.selectOpertor + EditWeightConfig.selectRight + EditWeightConfig.selectLeft);
				a.add(tempCost);
			}	
		}
		
		return a;
	}

}
