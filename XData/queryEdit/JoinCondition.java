package queryEdit;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import parsing.ConjunctQueryStructure;
import parsing.Node;
import parsing.QueryStructure;
import parsing.Pair;
import util.Utilities;

public class JoinCondition implements QueryComponent {

	public static List<Pair<QueryStructure,Float>> JoinConditionEdit(QueryStructure I, QueryStructure S,Node ins, Node st) throws Exception
	{
		List<Pair<QueryStructure,Float>> a = new ArrayList <Pair<QueryStructure,Float>>();
		if(!ins.getOperator().equals(st.getOperator()))
		{
			QueryStructure temp = (QueryStructure)Utilities.copy(S);
			temp.getLstJoinConditions().remove(st);
			Node n =  st;
			n.setOperator(ins.getOperator());
			temp.getLstJoinConditions().add(n);
			
			for(ConjunctQueryStructure conjunctElements : temp.getConjuncts())
			{
				conjunctElements.getJoinCondsForEquivalenceClasses().remove(st);
				conjunctElements.getJoinCondsForEquivalenceClasses().add(n);
				conjunctElements.getEquivalenceClasses().removeAll(conjunctElements.getEquivalenceClasses());
				conjunctElements.createEqClass();	
			}
			Vector<Vector<Node>> NewEqClass=new Vector<Vector<Node>>();
			for(ConjunctQueryStructure conjunctElements : temp.getConjuncts())
			{
				for(Vector<Node> EqClasses: conjunctElements.getEquivalenceClasses())
					NewEqClass.add(EqClasses);
			}
			//Changing the Equivalence Class (lstEqClasses) Each time
			temp.getLstEqClasses().removeAll(temp.getLstEqClasses());
			for(Vector<Node> EqClassElements : NewEqClass)
			{
				ArrayList<Node> EqClassArrayList = new ArrayList<Node>(EqClassElements);
				temp.getLstEqClasses().add(EqClassArrayList);
			}
			
			
			Pair<QueryStructure,Float> tempCost= new Pair<QueryStructure,Float> ();
			tempCost.setFirst(temp);
			tempCost.setSecond((float)EditWeightConfig.joinOpertor);
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
			temp.getLstJoinConditions().remove(st);
			Node n =  st;
			n.setLeft(ins.getLeft());
			temp.getLstJoinConditions().add(n);
			for(ConjunctQueryStructure conjunctElements : temp.getConjuncts())
			{
				conjunctElements.getJoinCondsForEquivalenceClasses().remove(st);
				conjunctElements.getJoinCondsForEquivalenceClasses().add(n);
				conjunctElements.getEquivalenceClasses().removeAll(conjunctElements.getEquivalenceClasses());
				conjunctElements.createEqClass();	
			}
			Vector<Vector<Node>> NewEqClass=new Vector<Vector<Node>>();
			for(ConjunctQueryStructure conjunctElements : temp.getConjuncts())
			{
				for(Vector<Node> EqClasses: conjunctElements.getEquivalenceClasses())
					NewEqClass.add(EqClasses);
			}
			//Changing the Equivalence Class (lstEqClasses) Each time
			temp.getLstEqClasses().removeAll(temp.getLstEqClasses());
			for(Vector<Node> EqClassElements : NewEqClass)
			{
				ArrayList<Node> EqClassArrayList = new ArrayList<Node>(EqClassElements);
				temp.getLstEqClasses().add(EqClassArrayList);
			}
			
			
			Pair<QueryStructure,Float> tempCost= new Pair<QueryStructure,Float> ();
			tempCost.setFirst(temp);
			tempCost.setSecond((float)EditWeightConfig.joinLeft);
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
			temp.getLstJoinConditions().remove(st);
			Node n =  st;
			n.setRight(ins.getRight());
			temp.getLstJoinConditions().add(n);
			
			for(ConjunctQueryStructure conjunctElements : temp.getConjuncts())
			{
				conjunctElements.getJoinCondsForEquivalenceClasses().remove(st);
				conjunctElements.getJoinCondsForEquivalenceClasses().add(n);
				conjunctElements.getEquivalenceClasses().removeAll(conjunctElements.getEquivalenceClasses());
				conjunctElements.createEqClass();	
			}
			Vector<Vector<Node>> NewEqClass=new Vector<Vector<Node>>();
			for(ConjunctQueryStructure conjunctElements : temp.getConjuncts())
			{
				for(Vector<Node> EqClasses: conjunctElements.getEquivalenceClasses())
					NewEqClass.add(EqClasses);
			}
			//Changing the Equivalence Class (lstEqClasses) Each time
			temp.getLstEqClasses().removeAll(temp.getLstEqClasses());
			for(Vector<Node> EqClassElements : NewEqClass)
			{
				ArrayList<Node> EqClassArrayList = new ArrayList<Node>(EqClassElements);
				temp.getLstEqClasses().add(EqClassArrayList);
			}
			
			
			Pair<QueryStructure,Float> tempCost= new Pair<QueryStructure,Float> ();
			tempCost.setFirst(temp);
			tempCost.setSecond((float)EditWeightConfig.joinRight);
			a.add(tempCost);
		}
		return a;
	}
	@Override
	public List<Pair<QueryStructure,Float>> edit(QueryStructure student, QueryStructure instructor) throws Exception {
		List<Pair<QueryStructure,Float>> a = new ArrayList <Pair<QueryStructure,Float>>();
		QueryStructure stu_not_matched = (QueryStructure)Utilities.copy(student);
		QueryStructure stu_matched = (QueryStructure)Utilities.copy(student);	
		QueryStructure ins_not_matched = (QueryStructure)Utilities.copy(instructor);
		for(Node t:instructor.getLstJoinConditions())
		{
			if(student.getLstJoinConditions().contains(t))
			{
				ins_not_matched.getLstJoinConditions().remove(t);
			}
		}
		for(Node t:student.getLstJoinConditions())
		{
			if(instructor.getLstJoinConditions().contains(t))
			{
				stu_not_matched.getLstJoinConditions().remove(t);
			}
			else
			{
				stu_matched.getLstJoinConditions().remove(t);
			}
		}
		for(Node st:stu_not_matched.getLstJoinConditions())
		{
			for(Node t: ins_not_matched.getLstJoinConditions())
			{
				List<Pair<QueryStructure,Float>> tempCost = JoinConditionEdit(instructor,student,t,st);
				a.addAll(tempCost);
			}
		}
		return a;
	}

	@Override
	public List<Pair<QueryStructure,Float>> add(QueryStructure student, QueryStructure instructor) throws Exception {
		List<Pair<QueryStructure,Float>> a = new ArrayList <Pair<QueryStructure,Float>>();
		for(Node t:instructor.getLstJoinConditions())
		{
			if(!student.getLstJoinConditions().contains(t))
			{
				QueryStructure temp = (QueryStructure)Utilities.copy(student);
				temp.getLstJoinConditions().add(t);
				for(ConjunctQueryStructure conjunctElements : temp.getConjuncts())
				{
					if(!conjunctElements.getJoinCondsForEquivalenceClasses().contains(t))
					{
						conjunctElements.getJoinCondsForEquivalenceClasses().add(t);
						conjunctElements.getEquivalenceClasses().removeAll(conjunctElements.getEquivalenceClasses());
						conjunctElements.createEqClass();	
					}
				}
				Vector<Vector<Node>> NewEqClass=new Vector<Vector<Node>>();
				for(ConjunctQueryStructure conjunctElements : temp.getConjuncts())
				{
					for(Vector<Node> EqClasses: conjunctElements.getEquivalenceClasses())
						NewEqClass.add(EqClasses);
				}
				//Changing the Equivalence Class (lstEqClasses) Each time
				temp.getLstEqClasses().removeAll(temp.getLstEqClasses());
				for(Vector<Node> EqClassElements : NewEqClass)
				{
					ArrayList<Node> EqClassArrayList = new ArrayList<Node>(EqClassElements);
					temp.getLstEqClasses().add(EqClassArrayList);
				}
				Pair<QueryStructure,Float> tempCost= new Pair<QueryStructure,Float> ();
				tempCost.setFirst(temp);
				tempCost.setSecond((float) EditWeightConfig.joinLeft+ EditWeightConfig.joinRight+EditWeightConfig.joinOpertor);
				a.add(tempCost);
			}
		}
		return a;
	}

	@Override
	public List<Pair<QueryStructure,Float>> remove(QueryStructure student, QueryStructure instructor) throws Exception {
		List<Pair<QueryStructure,Float>> a = new ArrayList <Pair<QueryStructure,Float>>();
		for(Node t:student.getLstJoinConditions())
		{
			if(!instructor.getLstJoinConditions().contains(t))
			{
				QueryStructure temp = (QueryStructure)Utilities.copy(student);
				temp.getLstJoinConditions().remove(t);
				for(ConjunctQueryStructure conjunctElements : temp.getConjuncts())
				{
					if(conjunctElements.getJoinCondsForEquivalenceClasses().contains(t))
					{
						conjunctElements.getJoinCondsForEquivalenceClasses().remove(t);
						conjunctElements.getEquivalenceClasses().removeAll(conjunctElements.getEquivalenceClasses());
						conjunctElements.createEqClass();
						
					}
				}
				Vector<Vector<Node>> NewEqClass=new Vector<Vector<Node>>();
				for(ConjunctQueryStructure conjunctElements : temp.getConjuncts())
				{
					for(Vector<Node> EqClasses: conjunctElements.getEquivalenceClasses())
						NewEqClass.add(EqClasses);
				}
				
				//Changing the Equivalence Class (lstEqClasses) Each time
				temp.getLstEqClasses().removeAll(temp.getLstEqClasses());
				for(Vector<Node> EqClassElements : NewEqClass)
				{
					ArrayList<Node> EqClassArrayList = new ArrayList<Node>(EqClassElements);
					temp.getLstEqClasses().add(EqClassArrayList);
				}
				Pair<QueryStructure,Float> tempCost= new Pair<QueryStructure,Float> ();
				tempCost.setFirst(temp);
				tempCost.setSecond((float) EditWeightConfig.joinLeft+ EditWeightConfig.joinRight+EditWeightConfig.joinOpertor);
				a.add(tempCost);
			}	
		}	
		return a;
	}

}
