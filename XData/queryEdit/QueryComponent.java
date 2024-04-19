package queryEdit;

import java.util.List;
import parsing.Pair;
import parsing.QueryStructure;

public interface QueryComponent {
	public List<Pair<QueryStructure,Float>> edit(QueryStructure student, QueryStructure instructor)throws Exception;
	public List<Pair<QueryStructure,Float>> add(QueryStructure student, QueryStructure instructor)throws Exception;
	public List<Pair<QueryStructure,Float>> remove(QueryStructure student, QueryStructure instructor)throws Exception;
}
