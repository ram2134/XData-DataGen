package queryEdit;

import java.util.ArrayList;
import java.util.List;

import parsing.QueryStructure;
import parsing.Pair;
public class SingleEdit {
	public static List<Pair<QueryStructure,Float> > single_edit(QueryStructure student, QueryStructure instructor) throws Exception
	{
		List<Pair<QueryStructure,Float>> projection_cond_deleted = new ArrayList<Pair<QueryStructure,Float>> ();
		List<Pair<QueryStructure,Float>> projection_cond_added = new ArrayList<Pair<QueryStructure,Float>> ();
		List<Pair<QueryStructure,Float>> projection_cond_edited = new ArrayList<Pair<QueryStructure,Float>> ();
		List<Pair<QueryStructure,Float>> edited_query_structure = new ArrayList <Pair<QueryStructure,Float>>();
		List<Pair<QueryStructure,Float>> selection_cond_deleted = new Selection().remove(student,instructor);
		List<Pair<QueryStructure,Float>> selection_cond_added = new Selection().add(student,instructor);
		List<Pair<QueryStructure,Float>> selection_cond_edited = new Selection().edit(student,instructor);
		
		if(student.getQueryType().getType() != null && (student.getQueryType().getType().equalsIgnoreCase("EXISTS") || student.getQueryType().getType().equalsIgnoreCase("NOT EXISTS")))
		{
			
		}
		else
		{
			projection_cond_deleted = new Projection().remove(student,instructor);
			projection_cond_added = new Projection().add(student,instructor);
			projection_cond_edited = new Projection().edit(student,instructor);
		}
		
		List<Pair<QueryStructure,Float>> groupby_cond_deleted = new GroupBy().remove(student,instructor);
		List<Pair<QueryStructure,Float>> groupby_cond_added = new GroupBy().add(student,instructor);
		List<Pair<QueryStructure,Float>> groupby_cond_edited = new GroupBy().edit(student,instructor);
		List<Pair<QueryStructure,Float>> rel_cond_added = new JoinRelation().add(student,instructor);
		List<Pair<QueryStructure,Float>> rel_cond_deleted = new JoinRelation().remove(student,instructor);
		List<Pair<QueryStructure,Float>> rel_cond_edited = new JoinRelation().edit(student,instructor);
		List<Pair<QueryStructure,Float>> join_cond_added = new JoinCondition().add(student,instructor);
		List<Pair<QueryStructure,Float>> join_cond_deleted = new JoinCondition().remove(student,instructor);
		List<Pair<QueryStructure,Float>> join_cond_edited = new JoinCondition().edit(student,instructor);
		List<Pair<QueryStructure,Float>> distinct_added = new Distinct().add(student,instructor);
		List<Pair<QueryStructure,Float>> distinct_deleted = new Distinct().remove(student,instructor);
		
		for(Pair<QueryStructure,Float> t:selection_cond_deleted)
		{
			edited_query_structure.add(t);
		}
		for(Pair<QueryStructure,Float> t:selection_cond_added)
		{
			edited_query_structure.add(t);
		}
		for(Pair<QueryStructure,Float> t:selection_cond_edited)
		{
			edited_query_structure.add(t);
		}
		for(Pair<QueryStructure,Float> t:projection_cond_deleted)
		{
			edited_query_structure.add(t);
		}
		for(Pair<QueryStructure,Float> t:projection_cond_added)
		{
			edited_query_structure.add(t);
		}
		for(Pair<QueryStructure,Float> t:projection_cond_edited)
		{
			edited_query_structure.add(t);
		}
		for(Pair<QueryStructure,Float> t:groupby_cond_deleted)
		{
			edited_query_structure.add(t);
		}
		for(Pair<QueryStructure,Float> t:groupby_cond_added)
		{
			edited_query_structure.add(t);
		}
		for(Pair<QueryStructure,Float> t:groupby_cond_edited)
		{
			edited_query_structure.add(t);
		}
		for(Pair<QueryStructure,Float> t:rel_cond_added)
		{
			edited_query_structure.add(t);
		}
		for(Pair<QueryStructure,Float> t:rel_cond_deleted)
		{
			edited_query_structure.add(t);
		}
		for(Pair<QueryStructure,Float> t:rel_cond_edited)
		{
			edited_query_structure.add(t);
		}
		for(Pair<QueryStructure,Float> t:join_cond_added)
		{
			edited_query_structure.add(t);
		}
		for(Pair<QueryStructure,Float> t:join_cond_deleted)
		{
			edited_query_structure.add(t);
		}
		for(Pair<QueryStructure,Float> t:join_cond_edited)
		{
			edited_query_structure.add(t);
		}
		for(Pair<QueryStructure,Float> t:distinct_added)
		{
			edited_query_structure.add(t);
		}
		for(Pair<QueryStructure,Float> t:distinct_deleted)
		{
			edited_query_structure.add(t);
		}
		return edited_query_structure;
	}
}
