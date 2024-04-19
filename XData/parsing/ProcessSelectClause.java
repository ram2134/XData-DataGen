/** @author mathew
 * 
 */

package parsing;

import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.jsqlparser.expression.AliasWithArgs;
import net.sf.jsqlparser.expression.AllComparisonExpression;
import net.sf.jsqlparser.expression.AnyComparisonExpression;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.CaseExpression;
import net.sf.jsqlparser.expression.DateTimeLiteralExpression;
import net.sf.jsqlparser.expression.DateValue;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExtractExpression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.IntervalExpression;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.SignedExpression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.TimeKeyExpression;
import net.sf.jsqlparser.expression.TimeValue;
import net.sf.jsqlparser.expression.TimestampValue;
import net.sf.jsqlparser.expression.WhenClause;
import net.sf.jsqlparser.expression.operators.arithmetic.Addition;
import net.sf.jsqlparser.expression.operators.arithmetic.Division;
import net.sf.jsqlparser.expression.operators.arithmetic.Multiplication;
import net.sf.jsqlparser.expression.operators.arithmetic.Subtraction;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.DoubleAnd;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExistsExpression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.expression.operators.relational.LikeExpression;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SubJoin;
import net.sf.jsqlparser.statement.select.SubSelect;
import parsing.AppTest_Parameters;
import parsing.AggregateFunction;
import parsing.CaseCondition;
import parsing.JoinClauseInfo;
import parsing.Node;
import parsing.Query;
import parsing.Table;
import parsing.Util;

public class ProcessSelectClause{
	private static  Logger logger = Logger.getLogger(ProcessSelectClause.class.getName());

	
	/** @author mathew on 1st october 2016 
	 * 
	 * 
	 * @param plainSelect
	 * @param debug
	 * @param qStruct
	 * @throws Exception
	 * 
	 * processes the  plainSelect clause, calls the respective methods for processing its components/sub-clauses, which 
	 * in turn stores the objects that represent the sub-clauses  in the appropriate data structures in the 
	 * qStruct object (3rd argument) . 
	 * 
	 */

	public static void ProcessSelect(PlainSelect plainSelect, QueryStructure qStruct,AppTest_Parameters dbAppParameters) throws Exception {
		logger.log(Level.FINE,"processing select query"+plainSelect.toString());
		Vector<Node> joinConditions=new Vector<Node>();		
		//Get the Node hierarchy
				qStruct.topLevelRelation = generateRelationHierarchyJSQL(plainSelect);
		/* processes the from clause and extracts the join conditions, also the components such as tables,
		 * subqueries, and subjoins are stored in qStruct.fromListElements
		 */
		ProcessSelectClause.processFromClause(plainSelect,qStruct,joinConditions, dbAppParameters);
		for(Node n:joinConditions){
			logger.log(Level.FINE,"joinCondition "+n);
		}
		//		display(qStruct.fromListElements);

		if(plainSelect.getDistinct()!=null){
			qStruct.setIsDistinct(true);
		}
		/* processes the where clause and extracts the expression of nodes (stores an atomic condition), 
		 * and stores it in qStruct.allConds
		 */
		ProcessSelectClause.processWhereClause(plainSelect,qStruct, dbAppParameters);

		if(!joinConditions.isEmpty()){
			if(!qStruct.allConds.isEmpty()) {
				Vector<Node> allCondsDups=(Vector<Node>) qStruct.allConds.clone();
				Node NewCond = new Node();
				NewCond.setType(Node.getAndNodeType());
				NewCond.setLeft(ProcessResultSetNode.getHierarchyOfJoinNode(joinConditions));
				
				NewCond.setRight(qStruct.allConds.remove(0));
				qStruct.allConds.add(NewCond);
			}
			else {
				qStruct.allConds.addAll(joinConditions);
			}
		}

		ProcessSelectClauseAbstract.modifyTreeForCompareSubQ(qStruct);		
		
		/* takes the possibly complex expression of nodes stored in qStruct.allConds, splits it into atomic conditions, 
		 * disjunct of atomic conditions, separates selection conditions, join conditions, is null conditions, subQuery conditions,
		 *  like conditions etc., stores each conjunct in a disjunct in list qStruct.conjuncts		 */
		QueryStructure.flattenAndSeparateAllConds(qStruct);

		for(ConjunctQueryStructure conjunct:qStruct.conjuncts)			
			conjunct.createEqClass();
		
		//compute foreign keys from tableMap 
		parsing.Util.foreignKeyClosure(qStruct);
		
		// now processes projection list, group by list, having conditions, and order by list
		ProcessSelectClause.processProjectionList(plainSelect,qStruct, dbAppParameters);
		ProcessSelectClause.processGroupByList(plainSelect,qStruct, dbAppParameters);
		ProcessSelectClause.processHavingClause(plainSelect,qStruct, dbAppParameters);
		ProcessSelectClause.processOrderByList(plainSelect,qStruct, dbAppParameters);
		
		//initializes the populates the list structures (eg: lstSelectionConditions) used by Canonicalization step subsequently
		qStruct.initializeQueryListStructures();
		for(Vector<Node> conds: qStruct.dnfCond)
		{
			Node composite=conds.firstElement();
			if(composite.getLeft() != null && composite.getLeft().getComponentNodes()!=null && composite.getLeft().getComponentNodes().size()>1)
			{
				for(int i=0;i<composite.getLeft().getComponentNodes().size();i++)
				{
					Node n=new Node();
					n.setLeft(composite.getLeft().getComponentNodes().get(i));
					n.setRight(composite.getRight().getComponentNodes().get(i));
					n.setOperator(composite.getOperator());
					if(! qStruct.getLstSelectionConditions().contains(n))
					qStruct.getLstSelectionConditions().add(n);
				}
			}
		}
		qStruct.reAdjustJoins();
	}


	/**	 method copied and adapted by mathew for query structure from parsing.Util.java
	 * 
	 * @param qStruct
	 */
	public static void modifyTreeForCompareSubQ(QueryStructure qStruct) {
		try{
			for (Node n: qStruct.allConds)   
				Util.modifyTreeForComapreSubQ(n);
		}catch(Exception e){
			logger.log(Level.SEVERE,"Error in modifyTreeForCompareSubQ : "+e.getMessage(),e);			
		}
	}

	/** method copied and adapted by mathew for query structure from parsing.QueryParser.java
	 * 
	 * This method gets the case statements in where part of the query and adds it to query parser.
	 * Removes the where condition from the whereclause so that the where clause predicates are not generated
	 * for the same. 
	 * 
	 * @param whereClause
	 * @param colExpression
	 * @param qStruct
	 * @return
	 * @throws Exception
	 */
	/** method copied and adapted by mathew for query structure from parsing.QueryParser.java
	 * 
	 * This method gets the case statements in where part of the query and adds it to query parser.
	 * Removes the where condition from the whereclause so that the where clause predicates are not generated
	 * for the same. 
	 * 
	 * @param whereClause
	 * @param colExpression
	 * @param qStruct
	 * @return
	 * @throws Exception
	 */
	public static boolean caseInWhereClause(Expression whereClause, Expression colExpression, QueryStructure qStruct, PlainSelect plainSelect,AppTest_Parameters dbAppParameters) throws Exception{

		Vector<CaseCondition> caseConditionsVector = new Vector<CaseCondition>();
		boolean isCaseExpr = false;
		boolean isCaseExists = false;
		try{
			if(whereClause instanceof CaseExpression){
				parsing.Column nodeColumnValue = null;
				List<Expression> whenClauses = ((CaseExpression) whereClause).getWhenClauses();
				for(int i=0;i < whenClauses.size();i++ ){

					CaseCondition cC = new CaseCondition();
					Node n = processExpression(((WhenClause)((CaseExpression) whereClause).getWhenClauses().get(i)).getWhenExpression(), qStruct.fromListElements,qStruct,plainSelect,null, dbAppParameters);
					qStruct.getCaseConditionMap().put(2,n.getCaseExpression());
					isCaseExists = true;
				}
			
/*					cC.setCaseConditionNode(n);
					cC.setCaseCondition(n.toString());
					cC.setConstantValue(((WhenClause)((CaseExpression) whereClause).getWhenClauses().get(i)).getThenExpression().toString());
					if(colExpression!= null && colExpression instanceof Column){
						Node n1 = ((processExpression((colExpression), qStruct.fromListElements,qStruct,plainSelect,null)));
						cC.setColValueForConjunct(UtilsRelatedToNode.getColumn(n1));
						nodeColumnValue = UtilsRelatedToNode.getColumn(n1);
						cC.setCaseOperator("=");
					}
					
					caseConditionsVector.add(cC);
					
				}
				isCaseExpr = true;
				//Add the else clause if present as the last item
				if(((CaseExpression) whereClause).getElseExpression() != null){
					CaseCondition cC = new CaseCondition();
					cC.setCaseConditionNode(n);
					cC.setCaseCondition("else");
					cC.setConstantValue(((CaseExpression) whereClause).getElseExpression().toString());
					if(colExpression != null && colExpression instanceof Column){
						Node n1 = ((processExpression((colExpression), qStruct.fromListElements,qStruct,plainSelect,null)));
					}
					
					caseConditionsVector.add(cC);
					
				}*/
				//Add Case conditions to queryparser
				
				return isCaseExists;
			}
			else if(whereClause instanceof BinaryExpression){
				Expression binaryLeftExp = ((BinaryExpression)whereClause).getLeftExpression();
				Expression binaryRightExp = ((BinaryExpression)whereClause).getRightExpression();
				if(binaryLeftExp != null){
					isCaseExists= caseInWhereClause(binaryLeftExp,binaryRightExp,qStruct,plainSelect, dbAppParameters);
					//If Case stmnt exists, rearrange Where clause to omit CASE condition
					if(isCaseExists){
						((BinaryExpression) whereClause).setLeftExpression(null);
						((BinaryExpression) whereClause).setRightExpression(null);
					}
				}

				if(binaryRightExp != null){
					isCaseExists = caseInWhereClause(binaryRightExp,binaryLeftExp,qStruct,plainSelect, dbAppParameters);
					//If Case stmnt exists, rearrange Where clause to omit CASE condition
					if(isCaseExists){
						((BinaryExpression) whereClause).setLeftExpression(null);
						((BinaryExpression) whereClause).setRightExpression(null);
					}
				}	
			}
			else if( whereClause instanceof Parenthesis){
				Expression caseExpr = ((Parenthesis)whereClause).getExpression();

				if(caseExpr instanceof CaseExpression){
					isCaseExists = caseInWhereClause(caseExpr,colExpression,qStruct,plainSelect, dbAppParameters);
					//If Case stmnt exists, rearrange Where clause to omit CASE condition
					if(isCaseExists){
						((Parenthesis) whereClause).setExpression(null);
					}
					
				}
			}
			return isCaseExists;
		}catch(Exception e){
			logger.log(Level.SEVERE,"Error in Processing case condition in where clause: "+e.getMessage(),e);
			throw e;
		}
	}
	//Modified for JSQL Exists - Start
	
	/** @author mathew 
	 * processes the where clause and extracts the expression of nodes (stores an atomic condition), 
	 * and stores it in qStruct.allConds
	 */
	private static void processWhereClause(PlainSelect plainSelect, QueryStructure qStruct,AppTest_Parameters dbAppParameters) throws Exception{
		// TODO Auto-generated method stub
		
		Expression whereClauseExpression = plainSelect.getWhere();
		//Application Testing -- modified to consider database application constraints on query result
		if(whereClauseExpression==null && (dbAppParameters == null || (dbAppParameters != null && ( dbAppParameters.getDbridge_Constraints()==null || dbAppParameters.getDbridge_Constraints()==""))))
			return;
		else if(whereClauseExpression==null && (dbAppParameters != null && (dbAppParameters.getDbridge_Constraints()!=null || dbAppParameters.getDbridge_Constraints()!=""))){
			whereClauseExpression = CCJSqlParserUtil.parseCondExpression(dbAppParameters.getDbridge_Constraints());
		}
		else if(whereClauseExpression!=null && dbAppParameters != null && dbAppParameters.getDbridge_Constraints()!=""){
			whereClauseExpression = CCJSqlParserUtil.parseCondExpression(whereClauseExpression.toString()+" AND "+dbAppParameters.getDbridge_Constraints());
		}
		
		
		caseInWhereClause(whereClauseExpression,null,qStruct,plainSelect, dbAppParameters);
		Node whereClause=ProcessSelectClause.processExpression(whereClauseExpression,qStruct.fromListElements, qStruct,plainSelect,null, dbAppParameters);
		logger.log(Level.FINE," where clause "+whereClause);
	
		if( whereClause != null) 
			qStruct.allConds.add(whereClause);
	}

	/** @author mathew 
	 * processes the having clause and extracts the expression of nodes (stores an atomic condition), 
	 * and stores it in qStruct.havingClause
	 */

	private static void processHavingClause(PlainSelect plainSelect, QueryStructure qStruct,AppTest_Parameters dbAppParameters) throws Exception{
		// TODO Auto-generated method stub
		// get having clause
		Expression hc = plainSelect.getHaving();
		if(hc==null||hc.toString().isEmpty()){
			qStruct.setHavingClause(null);
			return;
		}
		Node havingClause=ProcessSelectClause.processExpression(hc,qStruct.fromListElements, qStruct,plainSelect,null, dbAppParameters);
		qStruct.setHavingClause(havingClause);
		logger.log(Level.FINE,hc+" having clause "+havingClause);
	}


	/** @author mathew on 4th october 2016 
	 * 
	 * 
	 * @param plainSelect
	 * @param qStruct
	 * @throws Exception
	 * 
	 * processes the group by list of plainSelect, and stores the elements as column nodes in groupByNodes list in 
	 * qStruct object (2nd argument) . If the order by element is an alias or an unqualified column, it resolves the 
	 * table name and column name of the element.
	 * 
	 */

	private static void processGroupByList(PlainSelect plainSelect, QueryStructure qStruct,AppTest_Parameters dbAppParameters) throws Exception{
		// TODO Auto-generated method stub
		if (plainSelect.getGroupByColumnReferences() == null||plainSelect.getGroupByColumnReferences().isEmpty()) 
			return;

		List<Expression> gbl = plainSelect.getGroupByColumnReferences();
		for (int i = 0; i < gbl.size(); i++) {
			Column gbc;
			Expression groupExpression=gbl.get(i);

			if (groupExpression instanceof Column){
				gbc = (Column)groupExpression;
			} 				
			else {
				continue;
			}

			Node groupByColumn=processExpression(groupExpression,qStruct.fromListElements, qStruct,plainSelect,null, dbAppParameters);
			if(groupByColumn.getTableNameNo()==null||groupByColumn.getTableNameNo().isEmpty()){
				for(Node n:parsing.Util.getAllProjectedColumns(qStruct.fromListElements, qStruct)){
					if(groupByColumn.getColumn()!=null && n.getColumn()!=null&& n.getColumn().getColumnName().equalsIgnoreCase(groupByColumn.getColumn().getColumnName())){
						groupByColumn.setTable(n.getTable());
						groupByColumn.setTableNameNo(n.getTableNameNo());
						break;
					}
					else if(groupByColumn.getColumn()!=null && n.getType().equals(Node.getValType())&&n.getAliasName().equals(groupByColumn.getColumn().getColumnName())){
						groupByColumn=new Node(n);
						break;
					}
					else if(groupByColumn.getColumn()!=null && n.getType().equals(Node.getCaseNodeType())&&n.getAliasName().equals(groupByColumn.getColumn().getColumnName())){
						groupByColumn=new Node(n);
						break;
					}
					else if(groupByColumn.getColumn()!=null && n.getType().equals(Node.getExtractFuncType())&&n.getAliasName().equals(groupByColumn.getColumn().getColumnName())){
						groupByColumn=new Node(n);
						break;
					}
				}
			}
			if(groupByColumn.getType().equals(Node.getValType())){
				qStruct.groupByNodes.addElement(groupByColumn);
				logger.log(Level.FINE,groupExpression.toString()+ " group by column "+groupByColumn);
				continue;
			}
			else if(groupByColumn.getType().equals(Node.getCaseNodeType())){
				qStruct.groupByNodes.addElement(groupByColumn);
				logger.log(Level.FINE,groupExpression.toString()+ " group by column "+groupByColumn);
				continue;
			}
			else if(groupByColumn.getType().equals(Node.getExtractFuncType())){
				qStruct.groupByNodes.addElement(groupByColumn);
				logger.log(Level.FINE,groupExpression.toString()+ " group by column "+groupByColumn);
				continue;
			}
			else if(groupByColumn.getTableNameNo()==null||groupByColumn.getTableNameNo().isEmpty()){
				List<SelectItem> projectedItems=plainSelect.getSelectItems();
				for(int j=0;j<projectedItems.size();j++){
					SelectItem projectedItem=projectedItems.get(j);
					if(projectedItem instanceof net.sf.jsqlparser.statement.select.SelectExpressionItem){
						SelectExpressionItem selExpItem=(net.sf.jsqlparser.statement.select.SelectExpressionItem)projectedItem;
						Expression e=selExpItem.getExpression();
						if(e instanceof net.sf.jsqlparser.expression.Parenthesis){
							net.sf.jsqlparser.expression.Parenthesis p=(net.sf.jsqlparser.expression.Parenthesis) e;
							e=p.getExpression();
						}
						if(selExpItem.getAlias()!=null){
							if(groupByColumn.getColumn().getColumnName().equalsIgnoreCase(selExpItem.getAlias().getName())){
								groupByColumn =processExpression(e,qStruct.fromListElements, qStruct,plainSelect,null,dbAppParameters);
								logger.log(Level.FINE,groupByColumn+" alias name resolved " +selExpItem.getAlias().getName());
								break;
							}
						}
					}
				}

			}
			qStruct.groupByNodes.addElement(groupByColumn);
			logger.log(Level.FINE,groupExpression.toString()+ " group by column "+groupByColumn);

		}

	}

	/** @author mathew on 1st october 2016 
	 * 
	 * 
	 * @param plainSelect
	 * @param qStruct
	 * @throws Exception
	 * 
	 * processes the order by list of plainSelect, and stores the elements as column nodes in orderByElements list in 
	 * qStruct object (2nd argument) . If the order by element is an alias or an unqualified column, it resolves the 
	 * table name and column name of the element.
	 * 
	 */
	private static void processOrderByList(PlainSelect plainSelect, QueryStructure qStruct,AppTest_Parameters dbAppParameters) throws Exception {
		if (plainSelect.getOrderByElements() == null||plainSelect.getOrderByElements().isEmpty()) 
			return;

		List<OrderByElement> obl = plainSelect.getOrderByElements();
		for (int i = 0; i < obl.size(); i++) {
			Column obc;
			Expression orderExpression=obl.get(i).getExpression();

			if (orderExpression instanceof Column){
				obc = (Column)orderExpression;
			} else {
				continue;
			}

			Node orderByColumn=processExpression(orderExpression,qStruct.fromListElements, qStruct,plainSelect,null, dbAppParameters);
			if(orderByColumn.getTableNameNo()==null||orderByColumn.getTableNameNo().isEmpty()){
				for(Node n:parsing.Util.getAllProjectedColumns(qStruct.fromListElements, qStruct)){
					if(n.getColumn().getColumnName().equalsIgnoreCase(orderByColumn.getColumn().getColumnName())){
						orderByColumn.setTable(n.getTable());
						orderByColumn.setTableNameNo(n.getTableNameNo());
						break;
					}
				}
			}
			if(orderByColumn.getTableNameNo()==null||orderByColumn.getTableNameNo().isEmpty()){
				List<SelectItem> projectedItems=plainSelect.getSelectItems();
				for(int j=0;j<projectedItems.size();j++){
					SelectItem projectedItem=projectedItems.get(j);
					if(projectedItem instanceof net.sf.jsqlparser.statement.select.SelectExpressionItem){
						SelectExpressionItem selExpItem=(net.sf.jsqlparser.statement.select.SelectExpressionItem)projectedItem;
						Expression e=selExpItem.getExpression();
						if(e instanceof net.sf.jsqlparser.expression.Parenthesis){
							net.sf.jsqlparser.expression.Parenthesis p=(net.sf.jsqlparser.expression.Parenthesis) e;
							e=p.getExpression();
						}
						if(selExpItem.getAlias()!=null){
							if(orderByColumn.getColumn().getColumnName().equalsIgnoreCase(selExpItem.getAlias().getName())){
								orderByColumn =processExpression(e,qStruct.fromListElements, qStruct,plainSelect,null,dbAppParameters);
								logger.log(Level.FINE,orderByColumn+" alias name resolved " +selExpItem.getAlias().getName());
								break;
							}
						}
					}
				}

			}
			qStruct.orderByNodes.addElement(orderByColumn);
			logger.log(Level.FINE,orderExpression.toString()+ " order by column "+orderByColumn);
		}

	}

	/** @author mathew on 3rd October 2016
	 * 
	 * @param plainSelect
	 * @param qStruct
	 * @param joinConditions
	 * @throws Exception
	 * 
	 * Processes the From Clause of the first argument, plainSelect. From list elements in the from clause are extracted and stored 
	 * respecting their  hierarchy/nestedness in the fromListElements list in the qStruct object (2nd argument), also the join conditions involved are 
	 * extracted and stored in the 3rd argument, joinConditions
	 * 
	 */
	private static void processFromClause(PlainSelect plainSelect, QueryStructure qStruct, Vector<Node> joinConditions,AppTest_Parameters dbAppParameters) throws Exception{
				// TODO Auto-generated method stub
		FromItem firstFromItem=plainSelect.getFromItem();

		FromClauseElement leftFLE=null, rightFLE=null;


		if(firstFromItem instanceof net.sf.jsqlparser.schema.Table){
			net.sf.jsqlparser.schema.Table jsqlTable=(net.sf.jsqlparser.schema.Table)firstFromItem;
			leftFLE = new FromClauseElement();
			ProcessSelectClause.processFromListTable(jsqlTable, leftFLE, qStruct);
			qStruct.fromListElements.addElement(leftFLE);
		}
		else if(firstFromItem instanceof SubJoin){
			SubJoin subJoin=(SubJoin) firstFromItem;
			Join join=subJoin.getJoin();			
			Vector<FromClauseElement> tempElements=new Vector<FromClauseElement>();
			ProcessSelectClause.processFromListSubJoin(subJoin, tempElements, joinConditions, qStruct,plainSelect, dbAppParameters);
			leftFLE=new FromClauseElement();
			if(subJoin.getAlias()!=null){
				leftFLE.setAliasName(subJoin.getAlias().getName());
			}
			leftFLE.setBag(tempElements);
			qStruct.fromListElements.addElement(leftFLE);
		}
		else if(firstFromItem instanceof SubSelect){
			SubSelect subSelect=(SubSelect) firstFromItem;
			SelectBody selBody=subSelect.getSelectBody();
			QueryStructure subQueryParser=new QueryStructure(qStruct.getTableMap());
			leftFLE=new FromClauseElement();
			leftFLE.setSubQueryStructure(subQueryParser);
			if(subSelect.getAlias()!=null){
				leftFLE.setAliasName(subSelect.getAlias().getName());
			}
			qStruct.fromListElements.addElement(leftFLE);					
			ProcessSelectClause.processFromListSubSelect(subSelect,subQueryParser,qStruct, dbAppParameters);
			/* The following if block enables resolvement of subquery aliases with projection list arguments.
			 * eg: (Select col1, col2, .. from ... ) as subQAlias( alias1, alias2, ..). While the complex alias
			 * is processed, the arugment aliasi is set as the alias name for the corresponding projected column coli
			 */
			if(subSelect.getAlias()!=null && (subSelect.getAlias() instanceof AliasWithArgs)){
				AliasWithArgs aliasWithArgs= (AliasWithArgs)subSelect.getAlias();
				List<String> aliasNames=aliasWithArgs.getColNames();
				if(aliasNames.size()>0){
					Vector<Node> subQueryProjColumns=subQueryParser.getProjectedCols();
					if(aliasNames.size()!= subQueryProjColumns.size()){
						logger.log(Level.WARNING," Number of Aliass' Arguments does not match the number of subquery projected columns, "
								+ "exception thrown, query: "+plainSelect.toString());
						throw new Exception(" Number of Alias Arguments does not match the number of subquery projected columns"
								+ ", exception thrown");
					}
					else{
						for(int i=0;i<aliasNames.size();i++){
							String alName=aliasNames.get(i);
							subQueryProjColumns.get(i).setAliasName(alName);
						}
					}
				}
			}

		}

		if(plainSelect.getJoins()!=null && !plainSelect.getJoins().isEmpty()){
			for(Join join:plainSelect.getJoins()){

				FromItem fromItem=join.getRightItem();
				if(fromItem instanceof net.sf.jsqlparser.schema.Table){
					net.sf.jsqlparser.schema.Table jsqlTable=(net.sf.jsqlparser.schema.Table)fromItem;
					rightFLE=new FromClauseElement();
					processFromListTable(jsqlTable, rightFLE, qStruct);
					qStruct.fromListElements.addElement(rightFLE);
				}
				else if(fromItem instanceof SubJoin){
					SubJoin subJoin=(SubJoin) fromItem;
					Vector<FromClauseElement> tempElements=new Vector<FromClauseElement>();
					ProcessSelectClause.processFromListSubJoin(subJoin, tempElements, joinConditions, qStruct,plainSelect, dbAppParameters);
					rightFLE=new FromClauseElement();
					if(subJoin.getAlias()!=null){
						rightFLE.setAliasName(subJoin.getAlias().getName());
					}				
					rightFLE.setBag(tempElements);
					qStruct.fromListElements.addElement(rightFLE);

				}
				else if(fromItem instanceof SubSelect){
					SubSelect subSelect=(SubSelect) fromItem;					
					QueryStructure subQueryParser=new QueryStructure(qStruct.getTableMap());
					rightFLE=new FromClauseElement();
					rightFLE.setSubQueryStructure(subQueryParser);
					if(subSelect.getAlias()!=null){
						rightFLE.setAliasName(subSelect.getAlias().getName());
					}
					qStruct.fromListElements.addElement(rightFLE);

					processFromListSubSelect(subSelect,subQueryParser,qStruct, dbAppParameters);				
					
				}
				Expression e=join.getOnExpression();
				if(e!=null){/*Handling joins with ON Conditions*/
					Node joinCondition=null;
					if(join.isLeft())
						joinCondition=ProcessSelectClause.processExpression(e,qStruct.fromListElements, qStruct,plainSelect,JoinClauseInfo.leftOuterJoin, dbAppParameters);
					else if(join.isRight())
						joinCondition=ProcessSelectClause.processExpression(e,qStruct.fromListElements, qStruct,plainSelect,JoinClauseInfo.rightOuterJoin, dbAppParameters);
					else if(join.isFull())
						joinCondition=ProcessSelectClause.processExpression(e,qStruct.fromListElements, qStruct,plainSelect,JoinClauseInfo.fullOuterJoin, dbAppParameters);
					else
						joinCondition=ProcessSelectClause.processExpression(e,qStruct.fromListElements, qStruct,plainSelect,JoinClauseInfo.innerJoin, dbAppParameters);

					joinConditions.add(joinCondition);
				}		
				else if(join.getUsingColumns()!=null && !join.getUsingColumns().isEmpty()){/*Handling joins with using Conditions*/
					Vector<FromClauseElement> leftFLEVector=new Vector<FromClauseElement>();
					leftFLEVector.add(leftFLE);
					Vector<FromClauseElement> rightFLEVector=new Vector<FromClauseElement>();
					rightFLEVector.add(rightFLE);
					Vector<Node> leftColumns=parsing.Util.getAllProjectedColumns(leftFLEVector, qStruct);
					Vector<Node> rightColumns=parsing.Util.getAllProjectedColumns(rightFLEVector, qStruct);
					for(Column column:join.getUsingColumns()){
						Node matchedLeftColumn=null;
						for(Node leftColumn:leftColumns){
							if(leftColumn.getColumn().getColumnName().equalsIgnoreCase(column.getColumnName())){
								matchedLeftColumn=leftColumn;
								break;
							}
						}
						Node matchedRightColumn=null;
						for(Node rightColumn:rightColumns){
							if(rightColumn.getColumn().getColumnName().equalsIgnoreCase(column.getColumnName())){
								matchedRightColumn=rightColumn;
								break;
							}
						}
						if(matchedLeftColumn!=null && matchedRightColumn!=null){
							Node equiJoinNode=new Node();
							equiJoinNode.setType(Node.getBroNodeType());	
							equiJoinNode.setOperator(QueryStructure.cvcRelationalOperators[1]);
							equiJoinNode.setLeft(matchedLeftColumn);
							equiJoinNode.setRight(matchedRightColumn);
							if(join.isLeft())
								equiJoinNode.setJoinType(JoinClauseInfo.leftOuterJoin);
							else if(join.isRight())
								equiJoinNode.setJoinType(JoinClauseInfo.rightOuterJoin);
							else if(join.isFull())
								equiJoinNode.setJoinType(JoinClauseInfo.leftOuterJoin);
							else
								equiJoinNode.setJoinType(JoinClauseInfo.innerJoin);
							joinConditions.add(equiJoinNode);
							logger.log(Level.FINE," join condition added for join with using condition: "+equiJoinNode);
						}
					}
				}
				else if(join.isNatural()){/*Handling natural joins*/
					Vector<FromClauseElement> leftFLEVector=new Vector<FromClauseElement>();
					leftFLEVector.add(leftFLE);
					Vector<FromClauseElement> rightFLEVector=new Vector<FromClauseElement>();
					rightFLEVector.add(rightFLE);
					Vector<Node> leftColumns=parsing.Util.getAllProjectedColumns(leftFLEVector, qStruct);
					Vector<Node> rightColumns=parsing.Util.getAllProjectedColumns(rightFLEVector, qStruct);
					for(Node leftColumn:leftColumns){
						for(Node rightColumn:rightColumns){
							if(leftColumn.getColumn().getColumnName().equals(rightColumn.getColumn().getColumnName())){
								Node equiJoinNode=new Node();
								equiJoinNode.setType(Node.getBroNodeType());	
								equiJoinNode.setOperator(QueryStructure.cvcRelationalOperators[1]);
								equiJoinNode.setLeft(leftColumn);
								equiJoinNode.setRight(rightColumn);
								if(join.isLeft())
									equiJoinNode.setJoinType(JoinClauseInfo.leftOuterJoin);
								else if(join.isRight())
									equiJoinNode.setJoinType(JoinClauseInfo.rightOuterJoin);
								else if(join.isFull())
									equiJoinNode.setJoinType(JoinClauseInfo.leftOuterJoin);
								else
									equiJoinNode.setJoinType(JoinClauseInfo.innerJoin);
								
								joinConditions.add(equiJoinNode);
								logger.log(Level.FINE," join condition added for natural join: "+equiJoinNode);
							}
						}
					}
				}

				leftFLE=rightFLE;//reset leftFLE to the previously visited FLE
			}
		}
	}

	/** @author mathew on 1st october 2016 
	 * 
	 * 
	 * @param plainSelect
	 * @param qStruct
	 * @throws Exception
	 * 
	 * processes the projection list of plainSelect, for each element of the project list checks for the various possibilities - 
	 * column expression , or an all column object. In case of the former, check if there is a  case expression is present,
	 * in which case the parser's case condition map is updated with the respective objects from the case condition
	 * 
	 */
	private static void processProjectionList(PlainSelect plainSelect, QueryStructure qStruct,AppTest_Parameters dbAppParameters) throws Exception{
		// TODO Auto-generated method stub
		List<SelectItem> projectedItems=plainSelect.getSelectItems();
		for(int i=0;i<projectedItems.size();i++){
			SelectItem projectedItem=projectedItems.get(i);
			//the case where projected column is described by * in plainSelect
			if(projectedItem instanceof net.sf.jsqlparser.statement.select.AllColumns){			
				for(Node n:parsing.Util.getAllProjectedColumns(qStruct.fromListElements, qStruct)){
					logger.log(Level.FINE," all column, select all columns... "+n);
					qStruct.projectedCols.add(n);
				}
			}
			// the case where set of columns are explicitly specified in plainSelect
			else if(projectedItem instanceof net.sf.jsqlparser.statement.select.SelectExpressionItem){
				SelectExpressionItem selExpItem=(net.sf.jsqlparser.statement.select.SelectExpressionItem)projectedItem;
				Expression e=selExpItem.getExpression();
				if(selExpItem.getAlias()!=null){
					logger.log(Level.FINE," alias present " +selExpItem.getAlias().getName());
				}
				// deals with the case when projected column is bounded by a pair of braces
				if(e instanceof net.sf.jsqlparser.expression.Parenthesis){
					net.sf.jsqlparser.expression.Parenthesis p=(net.sf.jsqlparser.expression.Parenthesis) e;
					Expression exp=p.getExpression();
					if(exp instanceof net.sf.jsqlparser.expression.CaseExpression)
						e=exp;
				}
				// deals with the case expression
				if(e instanceof net.sf.jsqlparser.expression.CaseExpression){
					logger.log(Level.FINE,"case expression: "+e);

					CaseExpression caseExpr=(CaseExpression) e;
					
					List<Expression> whenClauses = caseExpr.getWhenClauses();
					Expression switchExpression=caseExpr.getSwitchExpression();
					Node switchExpressionNode=processExpression(switchExpression, qStruct.fromListElements, qStruct,plainSelect,null, dbAppParameters);
					parsing.CaseExpression retCaseExpr=new parsing.CaseExpression();
					ArrayList<CaseCondition> whenConditionals=new ArrayList<CaseCondition>();
					
					for(Expression whenClauseExpr:whenClauses){
						WhenClause whenClause=(WhenClause)whenClauseExpr;
						Node antecedentNode=processExpression(whenClause.getWhenExpression(),qStruct.fromListElements, qStruct,plainSelect,null, dbAppParameters);
						Node consequentNode=processExpression(whenClause.getThenExpression(),qStruct.fromListElements, qStruct,plainSelect,null, dbAppParameters);
						if(switchExpression!=null){
							Node tempNode=new Node();
							tempNode.setType(Node.getBroNodeType());
							tempNode.setOperator(QueryStructure.cvcRelationalOperators[1]);
							tempNode.setLeft(switchExpressionNode);
							tempNode.setRight(antecedentNode);
							antecedentNode=tempNode;
						}
						CaseCondition cC=new CaseCondition();
						cC.setWhenNode(antecedentNode);
						cC.setThenNode(consequentNode);
						whenConditionals.add(cC);
						
					}
					retCaseExpr.setWhenConditionals(whenConditionals);
					
					if(caseExpr.getElseExpression()!=null){
						CaseCondition cC=new CaseCondition();
						Node consequestNode=processExpression(caseExpr.getElseExpression(),qStruct.fromListElements, qStruct,plainSelect,null, dbAppParameters);
						cC.setThenNode(consequestNode);
						retCaseExpr.setElseConditional(cC);
					}
					Node projectedColumn=new Node();
					projectedColumn.setType(Node.getCaseNodeType());
					if(selExpItem.getAlias()!=null)
						projectedColumn.setAliasName(selExpItem.getAlias().getName());
					projectedColumn.setCaseExpression(retCaseExpr);
					qStruct.projectedCols.add(projectedColumn);
					logger.log(Level.FINE," Case Expresssion Projected Column Added "+projectedColumn);
					
				}
				else{//case when projected column is not a case expression 
					Node projectedColumn=processExpression(e,qStruct.fromListElements, qStruct,plainSelect,null, dbAppParameters);
					//set aggregate alias if any (eg: count(id) as count_id) 
					if(projectedColumn.getAgg()!=null&&selExpItem.getAlias()!=null){
						projectedColumn.getAgg().setAggAliasName(selExpItem.getAlias().getName());
					}
					//set alias if any for simple columns
					else if(selExpItem.getAlias()!=null){
						projectedColumn.setAliasName(selExpItem.getAlias().getName());
					}
					qStruct.projectedCols.add(projectedColumn);
					logger.log(Level.FINE,"Select Expression"+projectedItem.toString()+ " "+projectedColumn+" type "+projectedColumn.getType());

					if(qStruct.setOperator==null||qStruct.setOperator.isEmpty()){
						//deals with the case when the table name of the projected column  cannot be resolved  
						if(!projectedColumn.getType().equals(Node.getBaoNodeType())&&!projectedColumn.getType().equals(Node.getValType()) 
							&&!projectedColumn.getType().equals(Node.getAggrNodeType())	&&!projectedColumn.getType().equals(Node.getCaseNodeType())
							&&!projectedColumn.getType().equals(Node.getExtractFuncType())
							&&(projectedColumn.getTableNameNo()==null||projectedColumn.getTableNameNo().isEmpty())){
							logger.log(Level.FINE," Column name could not be resolved, query parsing failed, exception thrown, query: "+plainSelect.toString());
							throw new Exception(" Column name could not be resolved, query parsing failed, exception thrown");
						}
					}


				}
			}
		}
	}

	
	/**
	 * @author mathew
	 * 
	 * displays the contents of a list of FromListElements, by hierachically traversing the iterating the list, 
	 * if any member fle represents is subquery, then it traverses into the fromListElements of the subquery,
	 * if any member fle represents a subjoin, then it traverses into it (by recursion on getTabs)
	 * 
	 * @param visitedFromListElements
	 */
	public static void newDisplay(Vector<FromClauseElement> visitedFromListElements) {
		for(FromClauseElement fle:visitedFromListElements){
			if(fle!=null && (fle.getTableName()!=null||fle.getTableNameNo()!=null))
				;
			else if(fle!=null && fle.getSubQueryStructure()!=null){
				
				newDisplay(fle.getSubQueryStructure().getFromListElements());
			}
			else if(fle!=null && fle.getBag()!=null && !fle.getBag().isEmpty()){
				newDisplay(fle.getBag());				
			}

		}
	}
	/** @author mathew
	 * 
	 * @param jsqlTable
	 * @param frmListElement
	 * @param qStruct
	 * 
	 * deals with the case when the fromListElement is a table, in which case 
	 * the frmListElement argument is used to encode its details  
	 * 
	 */
	public static void processFromListTable(net.sf.jsqlparser.schema.Table jsqlTable, FromClauseElement frmListElement, QueryStructure qStruct){
		String tableName = jsqlTable.getFullyQualifiedName().toUpperCase();// getWholeTableName();
		
		//String tableName = jsqlTable.getFullyQualifiedName();// getWholeTableName(); // added by ram for mysql
		Table table = qStruct.getTableMap().getTable(tableName.toUpperCase());
		String aliasName = "";
		if (jsqlTable.getAlias() == null) {
			aliasName = tableName;
		} else {
			//aliasName = jsqlTable.getAlias().getName().toUpperCase();// getAlias();
			aliasName = jsqlTable.getAlias().getName();// added by rambabu
		}
		if (qStruct.getQuery().getRepeatedRelationCount().get(tableName) != null) {
			qStruct.getQuery().putRepeatedRelationCount(tableName, qStruct.getQuery()
					.getRepeatedRelationCount().get(tableName) + 1);
		} else {
			qStruct.getQuery().putRepeatedRelationCount(tableName, 1);
		}
		String tableNameNo = tableName
				+ qStruct.getQuery().getRepeatedRelationCount().get(tableName);

		frmListElement.setAliasName(aliasName);
		frmListElement.setTableName(tableName);
		frmListElement.setTableNameNo(tableNameNo);
		frmListElement.setBag(null);	
		qStruct.addFromTable(frmListElement);
		
		qStruct.getQuery().addFromTable(table);
		qStruct.getQuery().putBaseRelation(aliasName, tableName);
		
		logger.fine("Table added"+frmListElement);
	}

	/** @author mathew
	 * 
	 * @param subJoin
	 * @param visitedFromListElements
	 * @param joinConditions
	 * @param qStruct
	 * @param plainSelect
	 * @throws Exception
 	 * deals with the case when the fromListElement is a sub join, in which case 
	 * the visitedFromListElements argument is used to encode details of it and its components, 
	 * join conditions if any are extracted in joinConditions argument
	 */
	public static void processFromListSubJoin(SubJoin subJoin, Vector<FromClauseElement> visitedFromListElements, Vector<Node> joinConditions,
			QueryStructure qStruct, PlainSelect plainSelect,AppTest_Parameters dbAppParameters) throws Exception{
		logger.log(Level.FINE,"processing subjoin"+ subJoin.toString());

		FromItem leftFromItem=subJoin.getLeft();
		FromClauseElement leftFLE=null, rightFLE=null;

		if(leftFromItem instanceof net.sf.jsqlparser.schema.Table){
			net.sf.jsqlparser.schema.Table jsqlTable=(net.sf.jsqlparser.schema.Table)leftFromItem;
			leftFLE=new FromClauseElement();
			ProcessSelectClause.processFromListTable(jsqlTable, leftFLE, qStruct);
			//logger.info(leftFLE.toString());
			visitedFromListElements.add(leftFLE);
		}
		else if(leftFromItem instanceof SubJoin){
			SubJoin leftSubJoin=(SubJoin) leftFromItem;
			Vector<FromClauseElement> tempElements=new Vector<FromClauseElement>();
			ProcessSelectClause.processFromListSubJoin(leftSubJoin, tempElements, joinConditions, qStruct,plainSelect, dbAppParameters);
			leftFLE=new FromClauseElement();
			if(leftSubJoin.getAlias()!=null){
				leftFLE.setAliasName(leftSubJoin.getAlias().getName());
			}
			leftFLE.setBag(tempElements);
			visitedFromListElements.add(leftFLE);
		}
		else if(leftFromItem instanceof SubSelect){
			SubSelect subSelect=(SubSelect) leftFromItem;					
			QueryStructure subQueryParser=new QueryStructure(qStruct.getTableMap());
			leftFLE=new FromClauseElement();
			leftFLE.setSubQueryStructure(subQueryParser);
			if(subSelect.getAlias()!=null){
				leftFLE.setAliasName(subSelect.getAlias().getName());
			}
			qStruct.fromListElements.addElement(leftFLE);

			ProcessSelectClause.processFromListSubSelect(subSelect,subQueryParser,qStruct, dbAppParameters);		

		}
		FromItem rightFromItem=subJoin.getJoin().getRightItem();
		if(rightFromItem instanceof net.sf.jsqlparser.schema.Table){
			net.sf.jsqlparser.schema.Table jsqlTable=(net.sf.jsqlparser.schema.Table)rightFromItem;
			rightFLE=new FromClauseElement();
			ProcessSelectClause.processFromListTable(jsqlTable, rightFLE, qStruct);
			//logger.info(rightFLE.toString());
			visitedFromListElements.add(rightFLE);
		}
		else if(rightFromItem instanceof SubJoin){
			SubJoin rightSubJoin=(SubJoin) rightFromItem;
			Vector<FromClauseElement> tempElements=new Vector<FromClauseElement>();
			ProcessSelectClause.processFromListSubJoin(rightSubJoin, tempElements, joinConditions, qStruct,plainSelect, dbAppParameters);
			rightFLE=new FromClauseElement();
			if(rightSubJoin.getAlias()!=null){
				rightFLE.setAliasName(rightSubJoin.getAlias().getName());
			}
			rightFLE.setBag(tempElements);
			visitedFromListElements.add(rightFLE);
		}
		else if(rightFromItem instanceof SubSelect){
			SubSelect subSelect=(SubSelect) rightFromItem;					
			QueryStructure subQueryParser=new QueryStructure(qStruct.getTableMap());
			rightFLE=new FromClauseElement();
			rightFLE.setSubQueryStructure(subQueryParser);
			if(subSelect.getAlias()!=null){
				rightFLE.setAliasName(subSelect.getAlias().getName());
			}
			qStruct.fromListElements.addElement(rightFLE);

			ProcessSelectClause.processFromListSubSelect(subSelect,subQueryParser,qStruct, dbAppParameters);									

		}
		Join join=subJoin.getJoin();
		Expression e=join.getOnExpression();
		if(e!=null){
			Node joinCondition=null;
			if(join.isLeft())
				joinCondition=ProcessSelectClause.processExpression(e,qStruct.fromListElements, qStruct,plainSelect,JoinClauseInfo.leftOuterJoin, dbAppParameters);
			else if(join.isRight())
				joinCondition=ProcessSelectClause.processExpression(e,qStruct.fromListElements, qStruct,plainSelect,JoinClauseInfo.rightOuterJoin, dbAppParameters);
			else if(join.isFull())
				joinCondition=ProcessSelectClause.processExpression(e,qStruct.fromListElements, qStruct,plainSelect,JoinClauseInfo.fullOuterJoin, dbAppParameters);
			else
				joinCondition=ProcessSelectClause.processExpression(e,qStruct.fromListElements, qStruct,plainSelect,JoinClauseInfo.innerJoin, dbAppParameters);

			joinConditions.add(joinCondition);
		}
		else if(join.isNatural()){
			Vector<FromClauseElement> leftFLEVector=new Vector<FromClauseElement>();
			leftFLEVector.add(leftFLE);
			Vector<FromClauseElement> rightFLEVector=new Vector<FromClauseElement>();
			rightFLEVector.add(rightFLE);
			Vector<Node> leftColumns=parsing.Util.getAllProjectedColumns(leftFLEVector, qStruct);
			Vector<Node> rightColumns=parsing.Util.getAllProjectedColumns(rightFLEVector, qStruct);
			for(Node leftColumn:leftColumns){
				for(Node rightColumn:rightColumns){
					if(leftColumn.getColumn().getColumnName().equals(rightColumn.getColumn().getColumnName())){
						Node equiJoinNode=new Node();
						equiJoinNode.setType(Node.getBroNodeType());	
						equiJoinNode.setOperator(QueryStructure.cvcRelationalOperators[1]);
						equiJoinNode.setLeft(leftColumn);
						equiJoinNode.setRight(rightColumn);

						if(join.isLeft())
							equiJoinNode.setJoinType(JoinClauseInfo.leftOuterJoin);
						else if(join.isRight())
							equiJoinNode.setJoinType(JoinClauseInfo.rightOuterJoin);
						else if(join.isFull())
							equiJoinNode.setJoinType(JoinClauseInfo.leftOuterJoin);
						else
							equiJoinNode.setJoinType(JoinClauseInfo.innerJoin);

						joinConditions.add(equiJoinNode);
						logger.log(Level.FINE," join condition added for natural join: "+equiJoinNode);
					}
				}
			}
		}

	}

	/** @author mathew - code modified and adapted from parsing.WhereClauseVectorJSQL.getWhereClauseVector
	 * 
	 * @param clause - expression to be processed 
	 * @param fle - set of fromListElements visited in the from clause of the plainSelect (4th argument)
	 * @param qStruct- this query structure in its  data structures encodes the plainSelect (4th argument)  
	 * @param plainSelect - select query of which the clause (1st parameter) was part of 
	 * @param joinType  - string for setting the join type (eg: right outer join), in case if the clause was part of a join expression
	 * @return Node (expression) that represents the expression 
	 * @throws Exception
	 */

	public static Node processExpression(Object clause, Vector<FromClauseElement> fle,
			QueryStructure qStruct, PlainSelect plainSelect, String joinType,AppTest_Parameters dbAppParameters) throws Exception {
		
		try{
			//System.out.println(clause);
			if (clause == null) {
				return null;
			} else if (clause instanceof Parenthesis){
				Parenthesis par=((Parenthesis) clause);
				return processExpressionForParenthesis(par, fle,qStruct, plainSelect, joinType,dbAppParameters);
			}
			else if (clause instanceof Function) {
				Function an = (Function)clause;
				return processExpressionForFunction(an, fle,qStruct, plainSelect, joinType,dbAppParameters);

			} else if (clause instanceof DoubleValue) {
				Node n = new Node();
				n.setType(Node.getValType());
				String s=((((DoubleValue)clause).getValue()))+"";
				//String str=(BigIntegerDecimal)((((NumericConstantNode) clause).getValue()).getDouble()).toString();
				s=util.Utilities.covertDecimalToFraction(s);
				n.setStrConst(s);
				n.setLeft(null);
				n.setRight(null);
				return n;

			}else if (clause instanceof LongValue){
				Node n = new Node();
				n.setType(Node.getValType());
				String s=((((LongValue)clause).getValue()))+"";
				s=util.Utilities.covertDecimalToFraction(s);
				n.setStrConst(s);
				n.setLeft(null);
				n.setRight(null);
				return n;
			}
			else if (clause instanceof StringValue) {
				Node n = new Node();
				n.setType(Node.getValType());
				n.setStrConst(((StringValue) clause).getValue());
				n.setLeft(null); 
				n.setRight(null); 
				return n; 
			}
			else if(clause instanceof TimeValue){
				TimeValue timeValue=(TimeValue) clause;
				Time t=timeValue.getValue();
				Node n=new Node();
				n.setType(Node.getValType());
				n.setStrConst(t.toString());
				n.setLeft(null); 
				n.setRight(null);
				logger.log(Level.FINE,"TimeValue"+clause);
			}
			else if(clause instanceof TimestampValue){
				TimestampValue timeStampValue=(TimestampValue) clause;
				Timestamp ts=timeStampValue.getValue();
				Node n=new Node();
				n.setType(Node.getValType());
				n.setStrConst(ts.toString());
				n.setLeft(null); 
				n.setRight(null);
				logger.log(Level.FINE,"TimestampValue"+clause);
				return n;
			}
			else if(clause instanceof TimeKeyExpression){
				TimeKeyExpression timeKey=(TimeKeyExpression) clause;
				Node n=new Node();
				n.setType(Node.getValType());
				n.setStrConst(timeKey.getStringValue());
				n.setLeft(null); 
				n.setRight(null); 

				return n;
			}
			else if(clause instanceof DateTimeLiteralExpression){
				DateTimeLiteralExpression dateTimeExpr=(DateTimeLiteralExpression) clause;
				Node n=new Node();
				n.setType(Node.getValType());
				n.setStrConst(dateTimeExpr.getValue());
				n.setLeft(null); 
				n.setRight(null); 
				logger.log(Level.FINE,"DateTimeLiteralExpression Processed"+n);
				return n; 
			}
			else if(clause instanceof DateValue){
				DateValue dateValue=(DateValue) clause;
				logger.log(Level.FINE,"its a date"+dateValue.getValue());
				Node n=new Node();
				n.setType(Node.getValType());
				n.setStrConst(dateValue.getValue().toString());
				n.setLeft(null); 
				n.setRight(null); 
				return n; 

			}
			else if(clause instanceof IntervalExpression){
				IntervalExpression dateTimeExpr=(IntervalExpression) clause;
				Node n=new Node();
				n.setType(Node.getValType());
				n.setStrConst(dateTimeExpr.toString());
				n.setLeft(null); 
				n.setRight(null); 
				logger.log(Level.FINE,"IntervalExpression Processed"+n);
				return n; 
			}
			else if(clause instanceof SignedExpression){
				SignedExpression sExpr=(SignedExpression) clause;
				logger.log(Level.FINE,"its a signed expression"+sExpr);
				Node n=processExpression(sExpr.getExpression(),qStruct.fromListElements, qStruct,plainSelect,joinType,dbAppParameters);
				if(n.getType().equals(Node.getValType())){
					n.setStrConst(sExpr.getSign()+n.getStrConst());
				}
				return n; 

			}
			else if (clause instanceof Column) {
				Column columnReference = (Column) clause;	
				return ProcessExpressionForColumn(columnReference, fle, qStruct, plainSelect, joinType,dbAppParameters);
			} else if (clause instanceof AndExpression) {
				BinaryExpression andNode = ((BinaryExpression) clause);
				return ProcessExpressionForAnd(andNode, fle, qStruct, plainSelect, joinType,dbAppParameters);

			} else if (clause instanceof OrExpression) {
				BinaryExpression orNode = ((BinaryExpression) clause);
				return ProcessExpressionForOr(orNode, fle, qStruct, plainSelect, joinType,dbAppParameters);

			} 
			//Added by Bikash ---------------------------------------------------------------------------------
			else if(clause instanceof LikeExpression){
				BinaryExpression likeNode=((BinaryExpression)clause);
				return ProcessExpressionForLike(likeNode, fle, qStruct, plainSelect, joinType,dbAppParameters);
			}

			else if(clause instanceof JdbcParameter){
				Node n = new Node();
				n.setType(Node.getValType());		
				n.setStrConst("$"+qStruct.paramCount);
				qStruct.paramCount++;
				n.setLeft(null);
				n.setRight(null);

				return n;
			}
			else if (clause instanceof Addition){
				BinaryExpression baoNode = ((BinaryExpression)clause);
				return ProcessExpressionForAddition(baoNode, fle, qStruct, plainSelect, joinType,dbAppParameters);
			}
			else if (clause instanceof Subtraction){
				BinaryExpression baoNode = ((BinaryExpression)clause);
				return ProcessExpressionForSubtraction(baoNode, fle, qStruct, plainSelect, joinType,dbAppParameters);
			}
			else if (clause instanceof Multiplication){
				BinaryExpression baoNode = ((BinaryExpression)clause);
				return ProcessExpressionForMultiplication(baoNode, fle, qStruct, plainSelect, joinType,dbAppParameters);
			}
			else if (clause instanceof Division){
				BinaryExpression baoNode = ((BinaryExpression)clause);
				return ProcessExpressionForDivision(baoNode, fle, qStruct, plainSelect, joinType,dbAppParameters);
			}

			else if (clause instanceof NotEqualsTo) {
				NotEqualsTo broNode = (NotEqualsTo)clause;
				return ProcessExpressionForNotEquals(broNode, fle, qStruct, plainSelect, joinType,dbAppParameters);
			} 
			else if (clause instanceof DoubleAnd) {
				DoubleAnd broNode = (DoubleAnd)clause;	
				return ProcessExpressionForDoubleAnd(broNode, fle, qStruct, plainSelect, joinType,dbAppParameters);
			} 
			else if (clause instanceof IsNullExpression) {
				IsNullExpression isNullNode = (IsNullExpression) clause;
				return ProcessExpressionForIsNull(isNullNode, fle, qStruct, plainSelect, joinType,dbAppParameters);
			} else if (clause instanceof InExpression){ 
				//handles NOT and NOT IN both
				InExpression sqn = (InExpression)clause;
				return ProcessExpressionForIn(sqn, fle, qStruct, plainSelect, joinType,dbAppParameters);
				
			} else if (clause instanceof ExistsExpression){
				ExistsExpression sqn = (ExistsExpression)clause;
				return ProcessExpressionForExists(sqn, fle, qStruct, plainSelect, joinType,dbAppParameters);
			}
			else if (clause instanceof SubSelect) {
				SubSelect sqn = (SubSelect) clause;				
				return ProcessExpressionForSubselect(sqn, fle, qStruct, plainSelect, joinType,dbAppParameters);				
			}
			else if(clause instanceof Between){
				Between bn=(Between)clause;
				return ProcessExpressionForBetween(bn, fle, qStruct, plainSelect, joinType,dbAppParameters);				

			} else if (clause instanceof EqualsTo){				
				BinaryExpression bne = (BinaryExpression)clause;
				return ProcessExpressionForEqualsTo(bne, fle, qStruct, plainSelect, joinType,dbAppParameters);

			} else if (clause instanceof GreaterThan){
				GreaterThan broNode = (GreaterThan)clause;
				return processExpressionForGreaterThan(broNode,  qStruct.fromListElements,qStruct,plainSelect,joinType,dbAppParameters);			
			}
			else if (clause instanceof GreaterThanEquals){
				GreaterThanEquals broNode = (GreaterThanEquals)clause;
				return processExpressionForGreaterThanEquals(broNode,  qStruct.fromListElements,qStruct,plainSelect,joinType,dbAppParameters);
			}
			else if (clause instanceof MinorThan){
				BinaryExpression bne = (BinaryExpression)clause;
				return processExpressionForMinorThan(bne,  qStruct.fromListElements,qStruct,plainSelect,joinType,dbAppParameters);								
			} else if (clause instanceof MinorThanEquals){
				BinaryExpression bne = (BinaryExpression)clause;
				return processExpressionForMinorThanEquals(bne,  qStruct.fromListElements,qStruct,plainSelect,joinType,dbAppParameters);								
			} 
			else if(clause instanceof CaseExpression){				
				CaseExpression caseExpr =  (CaseExpression)clause;			
				return processExpressionForCaseExpression(caseExpr,  qStruct.fromListElements,qStruct,plainSelect,dbAppParameters);										
			}
			else if (clause instanceof AllComparisonExpression){
				AllComparisonExpression ace = (AllComparisonExpression)clause;
				return processExpressionForAllComparison(ace,qStruct,dbAppParameters);				
			}
			else if (clause instanceof AnyComparisonExpression){
				AnyComparisonExpression ace = (AnyComparisonExpression)clause;
				return processExpressionForAnyComparison(ace,qStruct,dbAppParameters);
			}
			else if(clause instanceof ExtractExpression){
				ExtractExpression exp = (ExtractExpression)clause;
				return processExpressionForExtract(exp,fle, qStruct, plainSelect, joinType,dbAppParameters);						
			}
			else {
				logger.log(Level.SEVERE,"getWhereClauseVector needs more programming ");
				throw new Exception("getWhereClauseVector needs more programming ");
			}
		}catch(Exception e){
			logger.log(Level.SEVERE,e.getMessage(),e);
			throw e;
		}
		return null;
	}


	public static Node processExpressionForParenthesis(Parenthesis par, Vector<FromClauseElement> fle, QueryStructure qStruct,
			PlainSelect plainSelect, String joinType,AppTest_Parameters dbAppParameters) throws Exception{

		boolean isNot = par.isNot();
		Node n= processExpression(par.getExpression(), fle,  qStruct,plainSelect, joinType,dbAppParameters);
		if(isNot){
			Node left = n.getLeft();
			Node right = n.getRight();
			if(left != null && left.getNodeType() != null && 
					left.getNodeType().equals(Node.getBroNodeType())
					&& left.getOperator() != null && left.getOperator().equalsIgnoreCase("=")){
				left.setOperator("/=");
			}
			if(right != null && right.getNodeType() != null && 
					right.getNodeType().equals(Node.getBroNodeType())
					&& right.getOperator() != null && right.getOperator().equalsIgnoreCase("=")){
				right.setOperator("/=");
			}

			if(left != null && left.getNodeType() != null && 
					left.getNodeType().equals(Node.getBroNodeType())
					&& left.getOperator() != null && left.getOperator().equalsIgnoreCase("=")){
				left.setOperator("/=");
			}
			if(right != null && right.getNodeType() != null && 
					right.getNodeType().equals(Node.getBroNodeType())
					&& right.getOperator() != null && right.getOperator().equalsIgnoreCase("=")){
				right.setOperator("/=");
			}
		}
		return n;
	}

	public static Node ProcessExpressionForColumn(Column columnReference, Vector<FromClauseElement> fle,
			QueryStructure qStruct, PlainSelect plainSelect, String joinType,AppTest_Parameters dbAppParameters) throws Exception{
		String colName	= columnReference.getColumnName().toUpperCase();
		String tableName  = columnReference.getTable().getFullyQualifiedName();

		Node n = new Node();
		n.setTableNameNo(tableName);
		n.setColumn(new parsing.Column(colName, tableName));
		n.setType(Node.getColRefType());
		if (tableName != null) {
			n.setTableAlias(tableName);
		} else {
			n.setTableAlias("");
		} 

		if(n.getColumn() != null){
			n.setTable(n.getColumn().getTable());
		}

		n.setLeft(null);
		n.setRight(null); 


		if(n.getTableNameNo()==null||n.getTableNameNo().isEmpty()){
			for(Node m:parsing.Util.getAllProjectedColumns(qStruct.fromListElements, qStruct)){
				if(m.getColumn()!=null&&m.getColumn().getColumnName().equalsIgnoreCase(n.getColumn().getColumnName())){
					n.setTable(m.getTable());
					n.setTableNameNo(m.getTableNameNo());
					n.setColumn(m.getColumn());
					break;
				}
				else if(m.getType().equals(Node.getValType())&&m.getAliasName().equals(n.getColumn().getColumnName())){
					n=new Node(m);
					return n;
				}
				else if(m.getType().equals(Node.getCaseNodeType())&& m.getAliasName().equals(n.getColumn().getColumnName())){
					n=new Node(m);
					return n;
				}
				else if(m.getType().equals(Node.getExtractFuncType())&& m.getAliasName().equals(n.getColumn().getColumnName())){
					n=new Node(m);
					return n;
				}
			}
		}


		n=transformToAbsoluteTableNames(n,fle,false, qStruct);				

		if(n.getType().equals(Node.getValType()))
			return n;
		else if(n.getType().equals(Node.getExtractFuncType()))
			return n;
		else if(n.getType().equals(Node.getCaseNodeType()))
			return n;


		if(n.getTableNameNo()==null||n.getTableNameNo().isEmpty()){
			List<SelectItem> projectedItems=plainSelect.getSelectItems();
			for(int j=0;j<projectedItems.size();j++){
				SelectItem projectedItem=projectedItems.get(j);
				if(projectedItem instanceof net.sf.jsqlparser.statement.select.SelectExpressionItem){
					SelectExpressionItem selExpItem=(net.sf.jsqlparser.statement.select.SelectExpressionItem)projectedItem;
					Expression e=selExpItem.getExpression();
					if(e instanceof net.sf.jsqlparser.expression.Parenthesis){
						net.sf.jsqlparser.expression.Parenthesis p=(net.sf.jsqlparser.expression.Parenthesis) e;
						e=p.getExpression();
					}
					if(selExpItem.getAlias()!=null){
						if(n.getColumn().getColumnName().equalsIgnoreCase(selExpItem.getAlias().getName())){
							n =processExpression(e,qStruct.fromListElements, qStruct,plainSelect,joinType,dbAppParameters);
							break;
						}
					}
				}
			}

		}

		return n;
	}

	public static Node ProcessExpressionForOr(BinaryExpression orNode, Vector<FromClauseElement> fle, QueryStructure qStruct,
			PlainSelect plainSelect, String joinType,AppTest_Parameters dbAppParameters) throws Exception{
		if (orNode.getLeftExpression() != null
				&& orNode.getRightExpression() != null) {
			Node n = new Node();
			n.setType(Node.getOrNodeType());
			n.setOperator("OR");
			n.setLeft(processExpression(orNode.getLeftExpression(),  fle, qStruct,plainSelect,joinType,dbAppParameters));
			n.setRight(processExpression(orNode.getRightExpression(), fle, qStruct,plainSelect,joinType,dbAppParameters));

			n.setQueryType(2);
			n.setQueryIndex(qStruct.getWhereClauseSubqueries().size()-1);
			return n;
		}
		return null;
	}

	public static Node ProcessExpressionForLike(BinaryExpression likeNode, Vector<FromClauseElement> fle,
			QueryStructure qStruct, PlainSelect plainSelect, String joinType,AppTest_Parameters dbAppParameters) throws Exception{
		if (likeNode.getLeftExpression() !=null && likeNode.getRightExpression()!=null )
		{
			Node n=new Node();
			if(! likeNode.isNot()){
				n.setType(Node.getLikeNodeType());
				n.setOperator("~");
			}
			else{
				n.setType(Node.getLikeNodeType());
				n.setOperator("!~");
			}
			n.setLeft(processExpression(likeNode.getLeftExpression(),fle,qStruct,plainSelect,joinType,dbAppParameters));
			n.setRight(processExpression(likeNode.getRightExpression(),  fle,qStruct,plainSelect,joinType,dbAppParameters));

			n.setQueryType(2);
			n.setQueryIndex(qStruct.getWhereClauseSubqueries().size()-1);
			return n;
		}
		return null;
	}

	public static Node ProcessExpressionForAnd(BinaryExpression andNode, Vector<FromClauseElement> fle,
			QueryStructure qStruct, PlainSelect plainSelect, String joinType,AppTest_Parameters dbAppParameters) throws Exception{
		if (andNode.getLeftExpression() != null
				&& andNode.getRightExpression() != null) {
			Node n = new Node();
			Node left = new Node();
			Node right = new Node();
			n.setType(Node.getAndNodeType());
			n.setOperator("AND");
			left = processExpression(andNode.getLeftExpression(), fle, qStruct,plainSelect,joinType,dbAppParameters);
			right = processExpression(andNode.getRightExpression(), fle, qStruct,plainSelect,joinType,dbAppParameters);


			n.setQueryType(2);
			n.setQueryIndex(qStruct.getWhereClauseSubqueries().size()-1);
			n.setLeft(left);
			n.setRight(right);

			return n;
		}
		return null;
	}

	public static Node ProcessExpressionForAddition(BinaryExpression baoNode, Vector<FromClauseElement> fle,
			QueryStructure qStruct, PlainSelect plainSelect, String joinType,AppTest_Parameters dbAppParameters) throws Exception{
		Node n = new Node();
		n.setType(Node.getBaoNodeType());
		n.setOperator("+");
		n.setLeft(processExpression(baoNode.getLeftExpression(), fle, qStruct,plainSelect,joinType,dbAppParameters));
		n.setRight(processExpression(baoNode.getRightExpression(),fle, qStruct,plainSelect,joinType,dbAppParameters));
		n=WhereClauseVectorJSQL.getTableDetailsForArithmeticExpressions(n);
		return n;
	}

	public static Node ProcessExpressionForSubtraction(BinaryExpression baoNode, Vector<FromClauseElement> fle,
			QueryStructure qStruct, PlainSelect plainSelect, String joinType,AppTest_Parameters dbAppParameters) throws Exception{
		Node n = new Node();
		n.setType(Node.getBaoNodeType());
		n.setOperator("-");
		n.setLeft(processExpression(baoNode.getLeftExpression(), fle,qStruct,plainSelect,joinType,dbAppParameters));
		n.setRight(processExpression(baoNode.getRightExpression(), fle, qStruct,plainSelect,joinType,dbAppParameters));
		n=WhereClauseVectorJSQL.getTableDetailsForArithmeticExpressions(n);
		return n;
	}

	public static Node ProcessExpressionForMultiplication(BinaryExpression baoNode, Vector<FromClauseElement> fle,
			QueryStructure qStruct, PlainSelect plainSelect, String joinType,AppTest_Parameters dbAppParameters) throws Exception{
		Node n = new Node();
		n.setType(Node.getBaoNodeType());
		n.setOperator("*");
		n.setLeft(processExpression(baoNode.getLeftExpression(), fle, qStruct,plainSelect,joinType,dbAppParameters));
		n.setRight(processExpression(baoNode.getRightExpression(),fle, qStruct,plainSelect,joinType,dbAppParameters));
		n=WhereClauseVectorJSQL.getTableDetailsForArithmeticExpressions(n);
		return n;
	}

	public static Node ProcessExpressionForDivision(BinaryExpression baoNode, Vector<FromClauseElement> fle,
			QueryStructure qStruct, PlainSelect plainSelect, String joinType,AppTest_Parameters dbAppParameters) throws Exception{
		Node n = new Node();
		n.setType(Node.getBaoNodeType());
		n.setOperator("/");
		n.setLeft(processExpression(baoNode.getLeftExpression(),fle, qStruct,plainSelect,joinType,dbAppParameters));
		n.setRight(processExpression(baoNode.getRightExpression(),fle, qStruct,plainSelect,joinType,dbAppParameters));
		n=WhereClauseVectorJSQL.getTableDetailsForArithmeticExpressions(n);
		return n;
	}

	public static Node ProcessExpressionForNotEquals(NotEqualsTo broNode, Vector<FromClauseElement> fle,
			QueryStructure qStruct, PlainSelect plainSelect, String joinType,AppTest_Parameters dbAppParameters) throws Exception{
		Node n = new Node();
		n.setType(Node.getBroNodeType());
		n.setOperator(QueryStructure.cvcRelationalOperators[2]);
		n.setLeft(processExpression(broNode.getLeftExpression(), fle, qStruct,plainSelect,joinType,dbAppParameters));
		n.setRight(processExpression(broNode.getRightExpression(), fle, qStruct,plainSelect,joinType,dbAppParameters));

		n.setQueryType(2);
		n.setQueryIndex(qStruct.getWhereClauseSubqueries().size()-1);
		//the following added by mathew on 17 oct 2016
		if(n.getLeft().getType().equals(Node.getAllNodeType())||n.getRight().getType().equals(Node.getAllNodeType())||
				n.getLeft().getType().equals(Node.getAnyNodeType()) ||
				n.getRight().getType().equals(Node.getAnyNodeType()))
			n.setType(Node.getBroNodeSubQType());
		else if(n.getLeft().getType().equals(Node.getColRefType())&&n.getRight().getType().equals(Node.getColRefType())){
			if(joinType!=null)
				n.setJoinType(joinType);
			else
				n.setJoinType(JoinClauseInfo.innerJoin);
		}
		else if(n.getLeft().getType().equals(Node.getBroNodeSubQType())||n.getRight().getType().equals(Node.getBroNodeSubQType()))
			n.setType(Node.getBroNodeSubQType());
		return n;
	}

	public static Node ProcessExpressionForDoubleAnd(DoubleAnd broNode, Vector<FromClauseElement> fle, QueryStructure qStruct,
			PlainSelect plainSelect, String joinType,AppTest_Parameters dbAppParameters) throws Exception{
		Node n = new Node();
		n.setType(Node.getBroNodeType());
		n.setOperator(QueryStructure.cvcRelationalOperators[7]);
		n.setLeft(processExpression(broNode.getLeftExpression(), fle, qStruct,plainSelect,joinType,dbAppParameters));
		n.setRight(processExpression(broNode.getRightExpression(), fle,qStruct,plainSelect,joinType,dbAppParameters));

		return n;
	}

	public static Node ProcessExpressionForIsNull(IsNullExpression isNullNode, Vector<FromClauseElement> fle,
			QueryStructure qStruct, PlainSelect plainSelect, String joinType,AppTest_Parameters dbAppParameters) throws Exception{
		Node n = new Node();
		n.setType(Node.getIsNullNodeType());
		n.setLeft(processExpression(isNullNode.getLeftExpression(),fle,qStruct,plainSelect,joinType,dbAppParameters));
		if(isNullNode.isNot()){
			n.setOperator("!=");
		}else{
			n.setOperator("=");
		}
		n.setRight(null);
		return n;
	}

	public static Node ProcessExpressionForIn(InExpression sqn, Vector<FromClauseElement> fle, QueryStructure qStruct,
			PlainSelect plainSelect, String joinType,AppTest_Parameters dbAppParameters) throws Exception{
		SubSelect subS=null;
		Node inNode=new Node();
		inNode.setType(Node.getInNodeType());
		Node notNode = new Node();				   
		Node rhs = new Node();
		if(sqn.getRightItemsList() instanceof ExpressionList){			
			Node lhs = processExpression(sqn. getLeftExpression(), fle, qStruct,plainSelect,joinType,dbAppParameters);
			ExpressionList exprList=(ExpressionList)sqn.getRightItemsList();
			List<Expression> expList=exprList.getExpressions();
			rhs=new Node(true);
			rhs.isComposite=true;
			rhs.setType(Node.getCompositeNodeType());
			for(Expression exp:expList){
				Node tempNode = processExpression(exp,fle, qStruct,plainSelect,joinType,dbAppParameters);	
				rhs.addComponentNode(tempNode);				
				inNode.setLeft(lhs);
				inNode.setRight(rhs);
			}
			if(!sqn.isNot()){					
				logger.log(Level.FINE," Composite In node"+inNode);
				return inNode;
			}else{
				notNode.setType(Node.getNotNodeType());
				notNode.setRight(null);
				notNode.setLeft(inNode);
				logger.log(Level.FINE," Composite In node"+notNode);
				return notNode;

			}			
		}
		else if (sqn. getLeftItemsList() instanceof SubSelect){
			subS=(SubSelect)sqn.getLeftItemsList();
		}
		else if(sqn. getRightItemsList() instanceof SubSelect){ 
			subS=(SubSelect)sqn.getRightItemsList();
		}
		QueryStructure subQueryParser=new QueryStructure(qStruct.getTableMap());
		rhs.setSubQueryStructure(subQueryParser);	
		processWhereSubSelect(subS,subQueryParser,qStruct,dbAppParameters);

		Node lhs = processExpression(sqn. getLeftExpression(), fle, qStruct,plainSelect,joinType,dbAppParameters);

		inNode.setLeft(lhs);
		inNode.setRight(rhs);

		inNode.setQueryType(2);
		inNode.setQueryIndex(qStruct.getWhereClauseSubqueries().size()-1);
		if(!sqn.isNot()){					
			return inNode;
		}else{
			notNode.setType(Node.getNotNodeType());

			notNode.setQueryType(2);
			notNode.setQueryIndex(qStruct.getWhereClauseSubqueries().size()-1);
			notNode.setRight(null);
			notNode.setLeft(inNode);
			return notNode;

		}
	}

	public static Node ProcessExpressionForExists(ExistsExpression sqn, Vector<FromClauseElement> fle, QueryStructure qStruct,
			PlainSelect plainSelect, String joinType,AppTest_Parameters dbAppParameters) throws Exception{

		SubSelect subS = (SubSelect)sqn.getRightExpression();
		QueryStructure subQueryParser=new QueryStructure(qStruct.getTableMap());
		Node existsNode=new Node();
		existsNode.setSubQueryStructure(subQueryParser);
		existsNode.setType(Node.getExistsNodeType());
		existsNode.setSubQueryConds(null);
		processWhereSubSelect(subS,subQueryParser,qStruct,dbAppParameters);


		existsNode.setQueryType(2);
	    existsNode.setQueryIndex(qStruct.getWhereClauseSubqueries().size()-1);
		
		Node notNode = new Node();				   

		if(!sqn.isNot()){					
			return existsNode;
		}else{
			notNode.setType(Node.getNotNodeType());
			notNode.setRight(null);

			notNode.setQueryType(2);
			notNode.setQueryIndex(qStruct.getWhereClauseSubqueries().size()-1);
			notNode.setLeft(existsNode);
			return notNode;

		}
	}

	public static Node ProcessExpressionForSubselect(SubSelect sqn, Vector<FromClauseElement> fle, QueryStructure qStruct,
			PlainSelect plainSelect, String joinType,AppTest_Parameters dbAppParameters) throws Exception{
		QueryStructure subQueryParser=new QueryStructure(qStruct.getTableMap());
		Node node=new Node();
		node.setSubQueryStructure(subQueryParser);
		node.setType(Node.getBroNodeSubQType());
		processWhereSubSelect(sqn,subQueryParser,qStruct,dbAppParameters);
		
		node.setQueryType(2);
		node.setQueryIndex(qStruct.getWhereClauseSubqueries().size()-1);
		

		List<SelectItem> rcList = ((PlainSelect)sqn.getSelectBody()).getSelectItems();	

		SelectExpressionItem rc = (SelectExpressionItem)rcList.get(0);
		node.setQueryIndex(qStruct.getWhereClauseSubqueries().size()-1);
		if(rc.getExpression() instanceof Function){ 
			return node;
		}
		else if(rc.getExpression() instanceof Parenthesis && (((Parenthesis)rc.getExpression()).getExpression()) instanceof Column){
			//the result of subquery must be a single tuple
			logger.log(Level.WARNING,"the result of subquery must be a single tuple");
		}

		else{
			//It is a scalar subquery that returns only one value
			Node retNode = new Node();
			retNode.setQueryType(2);
			retNode.setQueryIndex(qStruct.getWhereClauseSubqueries().size()-1);
		
			retNode.setType(Node.getBroNodeSubQType());
			retNode.setLhsRhs(node);
			
			if(node.getSubQueryStructure() != null && node.getSubQueryStructure().getAllDnfSelCond()  != null && !node.getSubQueryStructure().getAllDnfSelCond().isEmpty()){
				///Check .get(0) - again
				retNode.setSubQueryConds(node.getSubQueryStructure().getAllDnfSelCond().get(0));
			}
			
			if(qStruct.getWhereClauseSubqueries() != null && !qStruct.getWhereClauseSubqueries().isEmpty()){
				
				if(!qStruct.getWhereClauseSubqueries().get(0).getConjuncts().isEmpty() 
						&& qStruct.getWhereClauseSubqueries().get(0).getConjuncts().get(0).selectionConds != null 
						&& !qStruct.getWhereClauseSubqueries().get(0).getConjuncts().get(0).selectionConds.isEmpty()){
						qStruct.getWhereClauseSubqueries().get(0).getConjuncts().get(0).selectionConds.addAll(retNode.getSubQueryConds());
						
						for(Node nod: retNode.getSubQueryConds()){		
							
							if(ConjunctQueryStructure.isStringSelection(nod,1) ){										
								String str=nod.getRight().getStrConst();										
								qStruct.getWhereClauseSubqueries().get(0).getConjuncts().get(0).stringSelectionConds.add(nod);
								qStruct.getWhereClauseSubqueries().get(0).getConjuncts().get(0).selectionConds.remove(nod);
							}
							else if(ConjunctQueryStructure.isStringSelection(nod,0)){
								qStruct.getWhereClauseSubqueries().get(0).getConjuncts().get(0).stringSelectionConds.add(nod);
								qStruct.getWhereClauseSubqueries().get(0).getConjuncts().get(0).selectionConds.remove(nod);
							}
						}
					}
			}
				return retNode;
		}

		return node;
	}

	public static Node ProcessExpressionForBetween(Between bn, Vector<FromClauseElement> fle, QueryStructure qStruct,
			PlainSelect plainSelect, String joinType,AppTest_Parameters dbAppParameters) throws Exception{
		Node n=new Node();
		n.setType(Node.getAndNodeType());
		n.setQueryType(2);
		n.setQueryIndex(qStruct.getWhereClauseSubqueries().size()-1);
		

		Node l=new Node();
		l.setLeft(processExpression(bn.getLeftExpression(),fle,qStruct,plainSelect,joinType,dbAppParameters));
		l.setOperator(">=");
		l.setRight(processExpression(bn.getBetweenExpressionStart(),fle,qStruct,plainSelect,joinType,dbAppParameters));
		l.setType(Node.getBroNodeType());
		n.setLeft(l);

		Node r=new Node();
		r.setLeft(processExpression(bn.getLeftExpression(), fle,qStruct,plainSelect,joinType,dbAppParameters));
		r.setOperator("<=");
		r.setRight(processExpression(bn.getBetweenExpressionEnd(),fle,qStruct,plainSelect,joinType,dbAppParameters));
		r.setType(Node.getBroNodeType());
		n.setRight(r);
		
		return n;
	}

	public static Node processExpressionForGreaterThan(GreaterThan broNode, Vector<FromClauseElement> fle,
			QueryStructure qStruct, PlainSelect plainSelect, String joinType,AppTest_Parameters dbAppParameters) throws Exception{
		Node n = new Node();
		n.setType(Node.getBroNodeType());
		

		n.setOperator(QueryStructure.cvcRelationalOperators[3]);
		n.setLeft(processExpression(broNode.getLeftExpression(), fle, qStruct,plainSelect,joinType,dbAppParameters));
		n.setRight(processExpression(broNode.getRightExpression(),fle, qStruct,plainSelect,joinType,dbAppParameters));

		n.setQueryType(2);
		n.setQueryIndex(qStruct.getWhereClauseSubqueries().size()-1);
		
		if(n.getLeft() != null && n.getLeft().getSubQueryConds() != null && n.getLeft().getSubQueryConds().size() > 0 && n.getSubQueryConds()!=null){
			n.setSubQueryConds(n.getLeft().getSubQueryConds());
			n.getLeft().getSubQueryConds().clear();
		}
		else  { 
			if(n.getRight() != null && n.getRight().getSubQueryConds() != null && n.getRight().getSubQueryConds().size() >0 && n.getSubQueryConds()!=null){
				n.setSubQueryConds(n.getRight().getSubQueryConds());
				n.getRight().getSubQueryConds().clear();
			}
		}

		/* commented by mathew on 17 october 2016
		if(((GreaterThan) clause).getRightExpression() instanceof AllComparisonExpression ||
				((GreaterThan) clause).getLeftExpression() instanceof AllComparisonExpression){

			Node sqNode = new Node();
			sqNode.setType(Node.getAllNodeType());
			if(n.getLeft().getSubQueryConds() != null && n.getLeft().getSubQueryConds().size() > 0){
				sqNode.setSubQueryConds(n.getLeft().getSubQueryConds());
				n.getLeft().getSubQueryConds().clear();
		}
			else{
				if(n.getRight()!=null&&n.getRight().getSubQueryConds()!=null&&n.getRight().getSubQueryConds().size()>0 && sqNode.getSubQueryConds()!=null)
				{						sqNode.setSubQueryConds(n.getRight().getSubQueryConds());
				n.getRight().getSubQueryConds().clear();
				}
		} 
			sqNode.setLhsRhs(n);
			return sqNode;
		} 

		if(((GreaterThan) clause).getRightExpression() instanceof AnyComparisonExpression ||
				((GreaterThan) clause).getLeftExpression() instanceof AnyComparisonExpression){
			Node sqNode = new Node();
			sqNode.setType(Node.getAnyNodeType());
			if(n.getLeft().getSubQueryConds() != null && n.getLeft().getSubQueryConds().size() > 0){
				sqNode.setSubQueryConds(n.getLeft().getSubQueryConds());
				n.getLeft().getSubQueryConds().clear();
			} 
			else{ 
				if(n.getRight()!=null&&n.getRight().getSubQueryConds()!=null&&n.getRight().getSubQueryConds().size()>0 && sqNode.getSubQueryConds()!=null)
				{						sqNode.setSubQueryConds(n.getRight().getSubQueryConds());
				sqNode.setSubQueryConds(n.getRight().getSubQueryConds());
				n.getRight().getSubQueryConds().clear();
				}
			} 
			sqNode.setLhsRhs(n);
			return sqNode; 
		} */ 
		
		//the following added by mathew on 17 oct 2016
		if(n.getLeft().getType().equals(Node.getAllNodeType())||n.getRight().getType().equals(Node.getAllNodeType())||
				n.getLeft().getType().equals(Node.getAnyNodeType()) ||
				n.getRight().getType().equals(Node.getAnyNodeType()))
			n.setType(Node.getBroNodeSubQType());
		else if(n.getLeft().getType().equals(Node.getColRefType())&&n.getRight().getType().equals(Node.getColRefType())){
			if(joinType!=null)
				n.setJoinType(joinType);
			else
				n.setJoinType(JoinClauseInfo.innerJoin);
		}
		
		return n;	
		}

	public static Node processExpressionForGreaterThanEquals(GreaterThanEquals broNode,
			Vector<FromClauseElement> fles, QueryStructure qStruct, PlainSelect plainSelect,String joinType,AppTest_Parameters dbAppParameters) throws Exception{
		Node n = new Node();
		n.setType(Node.getBroNodeType());
		n.setOperator(QueryStructure.cvcRelationalOperators[4]);
		

		n.setLeft(processExpression(broNode.getLeftExpression(), fles, qStruct,plainSelect,joinType,dbAppParameters));
		n.setRight(processExpression(broNode.getRightExpression(), fles, qStruct,plainSelect,joinType,dbAppParameters));

		n.setQueryType(2);
		n.setQueryIndex(qStruct.getWhereClauseSubqueries().size()-1);
		
		//Storing sub query details
		//Code added for ALL / ANY subqueries - Start
		//FIXME ANy condition needs to be tested - IS this correct???
		if(n.getLeft() != null && n.getLeft().getSubQueryConds() != null && n.getLeft().getSubQueryConds().size() > 0){
			n.setSubQueryConds(n.getLeft().getSubQueryConds());
			n.getLeft().getSubQueryConds().clear();
		}
		else{ 
			if(n.getRight() != null &&n.getRight().getSubQueryConds() != null && n.getRight().getSubQueryConds().size() >0 ){
				n.setSubQueryConds(n.getRight().getSubQueryConds());
				n.getRight().getSubQueryConds().clear();
			}
		}

		//the following added by mathew on 17 oct 2016
		if(n.getLeft().getType().equals(Node.getAllNodeType())||n.getRight().getType().equals(Node.getAllNodeType())||
				n.getLeft().getType().equals(Node.getAnyNodeType()) ||
				n.getRight().getType().equals(Node.getAnyNodeType()))
			n.setType(Node.getBroNodeSubQType());
		else if(n.getLeft().getType().equals(Node.getColRefType())&&n.getRight().getType().equals(Node.getColRefType())){
			if(joinType!=null)
				n.setJoinType(joinType);
			else
				n.setJoinType(JoinClauseInfo.innerJoin);
		}

		return n;
	}

	public static Node processExpressionForMinorThan(BinaryExpression bne, Vector<FromClauseElement> fle,
			QueryStructure qStruct, PlainSelect plainSelect, String joinType,AppTest_Parameters dbAppParameters) throws Exception{
		Node n = new Node();
		n.setType(Node.getBroNodeType());
		n.setOperator(QueryStructure.cvcRelationalOperators[5]);
		
	
		n.setLeft(processExpression(bne.getLeftExpression(), fle,qStruct,plainSelect,joinType,dbAppParameters));
		n.setRight(processExpression(bne.getRightExpression(),fle,qStruct,plainSelect,joinType,dbAppParameters));

		n.setQueryType(2);
		n.setQueryIndex(qStruct.getWhereClauseSubqueries().size()-1);
		
		if(n.getLeft() != null && n.getLeft().getSubQueryConds() != null && n.getLeft().getSubQueryConds().size() > 0){
			n.setSubQueryConds(n.getLeft().getSubQueryConds());
			n.getLeft().getSubQueryConds().clear();
		}
		else{ 
			if(n.getRight() != null &&n.getRight().getSubQueryConds() != null && n.getRight().getSubQueryConds().size() >0 ){
				n.setSubQueryConds(n.getRight().getSubQueryConds());
				n.getRight().getSubQueryConds().clear();
			}
		}

		//the following added by mathew on 17 oct 2016
		if(n.getLeft().getType().equals(Node.getAllNodeType())||n.getRight().getType().equals(Node.getAllNodeType())||
				n.getLeft().getType().equals(Node.getAnyNodeType()) ||
				n.getRight().getType().equals(Node.getAnyNodeType()))
			n.setType(Node.getBroNodeSubQType());
		else if(n.getLeft().getType().equals(Node.getColRefType())&&n.getRight().getType().equals(Node.getColRefType())){
			if(joinType!=null)
				n.setJoinType(joinType);
			else
				n.setJoinType(JoinClauseInfo.innerJoin);
		}
		return n;
	}

	public static Node processExpressionForMinorThanEquals(BinaryExpression bne, Vector<FromClauseElement> fle,
			QueryStructure qStruct, PlainSelect plainSelect, String joinType,AppTest_Parameters dbAppParameters) throws Exception{
		Node n = new Node();
		n.setType(Node.getBroNodeType());
		

		n.setOperator(QueryStructure.cvcRelationalOperators[6]);
		n.setLeft(processExpression(bne.getLeftExpression(), fle,qStruct,plainSelect,joinType,dbAppParameters));
		n.setRight(processExpression(bne.getRightExpression(),fle, qStruct,plainSelect,joinType,dbAppParameters));

		n.setQueryType(2);
		n.setQueryIndex(qStruct.getWhereClauseSubqueries().size()-1);
		
		if(n.getLeft() != null && n.getLeft().getSubQueryConds() != null && n.getLeft().getSubQueryConds().size() > 0){
			n.setSubQueryConds(n.getLeft().getSubQueryConds());
			n.getLeft().getSubQueryConds().clear();
		}
		else{ 
			if(n.getRight() != null &&n.getRight().getSubQueryConds() != null && n.getRight().getSubQueryConds().size() >0 ){
				n.setSubQueryConds(n.getRight().getSubQueryConds());
				n.getRight().getSubQueryConds().clear();
			}
		}


		//the following added by mathew on 17 oct 2016
		if(n.getLeft().getType().equals(Node.getAllNodeType())||n.getRight().getType().equals(Node.getAllNodeType())||
				n.getLeft().getType().equals(Node.getAnyNodeType()) ||
				n.getRight().getType().equals(Node.getAnyNodeType()))
			n.setType(Node.getBroNodeSubQType());
		else if(n.getLeft().getType().equals(Node.getColRefType())&&n.getRight().getType().equals(Node.getColRefType())){
			if(joinType!=null)
				n.setJoinType(joinType);
			else
				n.setJoinType(JoinClauseInfo.innerJoin);
		}
		return n;
	}

	public static Node processExpressionForCaseExpression(CaseExpression caseExpr, Vector<FromClauseElement> fromListElements,
			QueryStructure qStruct, PlainSelect plainSelect,AppTest_Parameters dbAppParameters) throws Exception{

		List<Expression> whenClauses = caseExpr.getWhenClauses();
		Expression switchExpression=caseExpr.getSwitchExpression();
		Node switchExpressionNode=processExpression(switchExpression, qStruct.fromListElements, qStruct,plainSelect,null,dbAppParameters);
		parsing.CaseExpression retCaseExpr=new parsing.CaseExpression();
		ArrayList<CaseCondition> whenConditionals=new ArrayList<CaseCondition>();
		
		for(Expression whenClauseExpr:whenClauses){
			WhenClause whenClause=(WhenClause)whenClauseExpr;
			logger.log(Level.FINE," when exp: "+whenClause.getWhenExpression()+" then expression "+whenClause.getThenExpression());
			Node antecedentNode=processExpression(whenClause.getWhenExpression(),qStruct.fromListElements, qStruct,plainSelect,null,dbAppParameters);
			Node consequentNode=processExpression(whenClause.getThenExpression(),qStruct.fromListElements, qStruct,plainSelect,null,dbAppParameters);
			if(switchExpression!=null){
				Node tempNode=new Node();
				tempNode.setType(Node.getBroNodeType());
				tempNode.setOperator(QueryStructure.cvcRelationalOperators[1]);
				tempNode.setLeft(switchExpressionNode);
				tempNode.setRight(antecedentNode);
				antecedentNode=tempNode;
			}
			CaseCondition cC=new CaseCondition();
			cC.setWhenNode(antecedentNode);
			cC.setThenNode(consequentNode);
			whenConditionals.add(cC);
			
		}
		retCaseExpr.setWhenConditionals(whenConditionals);
		
		if(caseExpr.getElseExpression()!=null){
			CaseCondition cC=new CaseCondition();
			Node consequestNode=processExpression(caseExpr.getElseExpression(),qStruct.fromListElements, qStruct,plainSelect,null,dbAppParameters);
			cC.setThenNode(consequestNode);
			retCaseExpr.setElseConditional(cC);
		}
		Node retNode=new Node();
		retNode.setType(Node.getCaseNodeType());
		retNode.setCaseExpression(retCaseExpr);
		return retNode;
	}

	public static  Node processExpressionForAllComparison(AllComparisonExpression ace, QueryStructure qStruct,AppTest_Parameters dbAppParameters) throws Exception{
		SubSelect ss = ace.getSubSelect();

		QueryStructure subQueryParser=new QueryStructure(qStruct.getTableMap());
		Node allNode=new Node();
		allNode.setSubQueryStructure(subQueryParser);
		allNode.setType(Node.getAllNodeType());
		processWhereSubSelect(ss,subQueryParser,qStruct,dbAppParameters);

		allNode.setQueryType(2);
		allNode.setQueryIndex(qStruct.getWhereClauseSubqueries().size()-1);
		return allNode;				
	}

	public static Node processExpressionForAnyComparison(AnyComparisonExpression ace, QueryStructure qStruct,AppTest_Parameters dbAppParameters) throws Exception{
		SubSelect ss = ace.getSubSelect();

		QueryStructure subQueryParser=new QueryStructure(qStruct.getTableMap());
		Node anyNode=new Node();
		anyNode.setSubQueryStructure(subQueryParser);
		anyNode.setType(Node.getAnyNodeType());
		processWhereSubSelect(ss,subQueryParser,qStruct, dbAppParameters);

		anyNode.setQueryType(2);
		anyNode.setQueryIndex(qStruct.getWhereClauseSubqueries().size()-1);
		return anyNode;
	}

	public static Node processExpressionForExtract(ExtractExpression exp, Vector<FromClauseElement> fle,
			QueryStructure qStruct, PlainSelect plainSelect, String joinType,AppTest_Parameters dbAppParameters) throws Exception{
		
		Node n=new Node(); // Main node
		n.setType(Node.getExtractFuncType());
		if(exp.getName()!=null){
			n.setStrConst(exp.getName());
		}
		if(exp.getExpression()!=null){
			Node n1=processExpression(exp.getExpression(),fle, qStruct,plainSelect,joinType,dbAppParameters);
			n.setLeft(n1);
		}
		n.setRight(null);				
		logger.log(Level.FINE,"Extract expression processed"+n);
		return n;

	}

	public static Node ProcessExpressionForEqualsTo(BinaryExpression bne, Vector<FromClauseElement> fle, QueryStructure qStruct,
			PlainSelect plainSelect, String joinType,AppTest_Parameters dbAppParameters) throws Exception{
		Node n = new Node();
		n.setType(Node.getBroNodeType());
		n.setOperator("=");
		Node ndl = processExpression(bne.getLeftExpression(), fle, qStruct,plainSelect,joinType,dbAppParameters);
		if(ndl != null){
			n.setLeft(ndl);
		}
		Node ndr = processExpression(bne.getRightExpression(), fle, qStruct,plainSelect,joinType,dbAppParameters);
		if(ndr!= null){
			n.setRight(ndr);
		}


		if((ndl == null && ndr ==null)){
			return null;
		}

		//the following added by mathew on 17 oct 2016
		if(n.getLeft().getType().equals(Node.getAllNodeType())||n.getRight().getType().equals(Node.getAllNodeType())||
				n.getLeft().getType().equals(Node.getAnyNodeType()) ||
				n.getRight().getType().equals(Node.getAnyNodeType()))
			n.setType(Node.getBroNodeSubQType());
		else if(n.getLeft().getType().equals(Node.getColRefType())&&n.getRight().getType().equals(Node.getColRefType())){
			if(joinType!=null)
				n.setJoinType(joinType);
			else
				n.setJoinType(JoinClauseInfo.innerJoin);
		}

		n.setQueryType(2);
		n.setQueryIndex(qStruct.getWhereClauseSubqueries().size()-1);
		return n;
	}

	public static Node processExpressionForFunction(Function an, Vector<FromClauseElement> fle, QueryStructure qStruct,
			PlainSelect plainSelect, String joinType,AppTest_Parameters dbAppParameters) throws Exception{
		// TODO Auto-generated method stub
		String funcName = an.getName();


		//All these are string manipulation functions and not aggregate function
		if(funcName.equalsIgnoreCase("ROW")){
			Node n=new Node(true);
			n.isComposite=true;
			n.setType(Node.getCompositeNodeType());
			if (an.getParameters()!=null){
				ExpressionList anList = an.getParameters();
				List<Expression> expList = anList.getExpressions();
				for(Expression e:expList){
					Node tempNode = processExpression(e,fle, qStruct,plainSelect,joinType,dbAppParameters);	
					n.addComponentNode(tempNode);
				}
			}				
			return n;
		}
		else if(! (funcName.equalsIgnoreCase("Lower") || funcName.equalsIgnoreCase("substring") || funcName.equalsIgnoreCase("upper")
				||funcName.equalsIgnoreCase("trim") || funcName.equalsIgnoreCase("postion") || funcName.equalsIgnoreCase("octet_length")
				|| funcName.equalsIgnoreCase("bit_length") || funcName.equalsIgnoreCase("char_length") || funcName.equalsIgnoreCase("overlay"))){

			AggregateFunction af = new AggregateFunction();
			if (an.getParameters()!=null){
				ExpressionList anList = an.getParameters();
				List<Expression> expList = anList.getExpressions();//FIXME not only 1 expression but all expressions

				Node n = processExpression(expList.get(0), fle,  qStruct,plainSelect,joinType,dbAppParameters);
				af.setAggExp(n);

			} else {
				af.setAggExp(null);
			}

			af.setFunc(funcName.toUpperCase());
			af.setAggAliasName(funcName.toUpperCase());
			af.setDistinct(an.isDistinct());

			Node agg = new Node();
			agg.setAgg(af);
			agg.setType(Node.getAggrNodeType());
			//Shree added this to set Table, TableNameNo for Aggregate function node - @ node level
			if(af.getAggExp() != null){
				agg.setTable(af.getAggExp().getTable());
				agg.setTableNameNo(af.getAggExp().getTableNameNo());
				agg.setTableAlias(af.getAggExp().getTableAlias());
				agg.setColumn(af.getAggExp().getColumn());


			}//Added by Shree for count(*) 
			else if(af.getFunc().toUpperCase().contains("COUNT") && an.isAllColumns()){				
				if(af.getAggExp() == null){
					Node n1 = parsing.Util.getNodeForCount(fle, qStruct);
					af.setAggExp(n1);
					af.setFunc(funcName.toUpperCase());
					af.setDistinct(an.isDistinct());

					if(af.getAggExp()!=null){
						agg.setTable(af.getAggExp().getTable());
						agg.setTableNameNo(af.getAggExp().getTableNameNo());
						agg.setTableAlias(af.getAggExp().getTableAlias());
						agg.setColumn(af.getAggExp().getColumn());
					}

					agg.setLeft(null);
					agg.setRight(null);
				}
			}

			return agg;
		}
		else {
			//String function manipulation
			Node n=new Node();
			if (an.getParameters()!=null){
				ExpressionList anList = an.getParameters();
				List<Expression> expList = anList.getExpressions();//FIXME not only 1 expression but all expressions
				n = processExpression(expList.get(0),fle, qStruct,plainSelect,joinType,dbAppParameters);		
			}
			return n;
		}	
	}

	/** @author mathew
	 * 
	 * @param subSelect
	 * @param subQueryParser
	 * @param parentQueryParser
	 * @throws Exception
	 * 
	 * deals with the case when a subquery is encountered while processing the from clause 
	 */
	public static void processFromListSubSelect(SubSelect subSelect, QueryStructure subQueryParser,QueryStructure parentQueryParser,AppTest_Parameters dbAppParameters) throws Exception {
		// TODO Auto-generated method stub
		logger.log(Level.FINE," Processing subselect, selbody:"+subSelect.getSelectBody().toString());
		if(subSelect.getAlias()!=null)
			logger.log(Level.FINE," subselect alias "+subSelect.getAlias().getName());

		parentQueryParser.getFromClauseSubqueries().add(subQueryParser);
		
		subQueryParser.parentQueryParser=parentQueryParser;
		subQueryParser.setQuery(new Query("q2",subSelect.getSelectBody().toString()));
		subQueryParser.getQuery().setRepeatedRelationCount(parentQueryParser.getQuery().getRepeatedRelationCount());
		
		subQueryParser.buildQueryStructureJSQL("q2", subSelect.getSelectBody().toString(), true, dbAppParameters);

	}

/** @author mathew
 * 
 * @param subSelect
 * @param subQueryParser
 * @param parentQueryParser
 * @throws Exception
 * 
 * deals with the case when a subquery is encountered while processing the where clause
 */

	public static void processWhereSubSelect(SubSelect subSelect, QueryStructure subQueryParser,QueryStructure parentQueryParser,AppTest_Parameters dbAppParameters) throws Exception {
		logger.log(Level.FINE," Processing subselect, selbody:"+subSelect.getSelectBody().toString());

		parentQueryParser.getWhereClauseSubqueries().add(subQueryParser);
		subQueryParser.parentQueryParser=parentQueryParser;
		subQueryParser.setQuery(new Query("q2",subSelect.getSelectBody().toString()));
		subQueryParser.getQuery().setRepeatedRelationCount(parentQueryParser.getQuery().getRepeatedRelationCount());
		subQueryParser.buildQueryStructureJSQL("q2", subSelect.getSelectBody().toString(), true, dbAppParameters);

	}
	
	
	
	/** @author mathew 
	 * 
	 * @param n
	 * @param fleList - list of from list elements returned after processing the from clause 
	 * @param aliasNameFound
	 * @param qStruct
	 * @return
	 * @throws Exception
	 * 
	 * takes a node n (1st argument) for whose either table name is an alias or absent, resolves the alias/determines the table t
	 * to which the column belongs to  returns a node whose table, table name etc. are set with the respective values of t
	 */
	private static Node transformToAbsoluteTableNames(Node n, Vector<FromClauseElement> fleList, boolean aliasNameFound, QueryStructure qStruct) throws Exception {
		// note that for any FromListElement object its tableName ==null iff it represents a subjoin or a subSelect
		String oldTableNameNo=n.getTableNameNo();
		for(FromClauseElement fle:fleList){
			//iterates through the fleList, for each fle, tries to match its table name/alias name
			//with the the name of the input node n
			//case when fle under consideration is a table, in which case if there is a match, then fle's
			//table name is copied to n's and also the respective table 
			if(fle!=null&&fle.getTableName()!=null){
				if(fle.getTableName().equalsIgnoreCase(n.getTableAlias())){
					n.setTableNameNo(fle.getTableNameNo());
					Table table=qStruct.getTableMap().getTable(fle.getTableName());
					n.setTable(table);
					//n.setColumn(new parsing.Column(n.getColumn().getColumnName(),table));
					parsing.Column c=table.getColumn(n.getColumn().getColumnName());
					if(c==null){
						logger.log(Level.WARNING," Column name could not be resolved, query parsing failed, exception thrown, column name: "+n.getColumn().getColumnName());
						throw new Exception(" Column name could not be resolved, query parsing failed, exception thrown, column name: "+n.getColumn().getColumnName());
					}
					else{
					n.setColumn(table.getColumn(n.getColumn().getColumnName()));
					logger.log(Level.FINE, "table Name Found "+n);
					}
					return n;
				}
				else if(fle.getAliasName().equalsIgnoreCase(n.getTableAlias())){
					n.setTableNameNo(fle.getTableNameNo());
					Table table=qStruct.getTableMap().getTable(fle.getTableName());
					if(table!=null){
						n.setTable(table);
						parsing.Column c=table.getColumn(n.getColumn().getColumnName());
						n.setColumn(c);
						if(c==null){
							logger.log(Level.WARNING," Column name could not be resolved, query parsing failed, exception thrown, column name: "+n.getColumn().getColumnName());
							throw new Exception(" Column name could not be resolved, query parsing failed, exception thrown, column name: "+n.getColumn().getColumnName());
						}
					}
					logger.log(Level.FINE,"alias Name Found "+n);
					return n;
				}
				// case when there is table name of n does not match with table/alias name of fle
				// in which case fle's columns are searched for a column whose name is n's, if yes
				// then fle's table and table name is copied to n's
				else if(aliasNameFound){
					logger.log(Level.FINE,"alias Name Found but not n");
					Table table=qStruct.getTableMap().getTable(fle.getTableName());
					parsing.Column c;
					if((c=table.getColumn(n.getColumn().getColumnName().toUpperCase()))!=null){
						n.setTableNameNo(fle.getTableNameNo());
						n.setTable(table);
						//n.setColumn(c);
						n.setColumn(table.getColumn(n.getColumn().getColumnName()));
						return n;
					}
				}
			}
			// considers the case fle alias is provided and fle is not a table (hence, is a subquery or a subjoin)
			// and alias name matches with n's table name
			if(fle!=null&&fle.getTableName()==null && fle.getAliasName()!=null&&
				fle.getAliasName().equalsIgnoreCase(n.getTableAlias())){
				logger.log(Level.FINE," alias name is not null, but table name is null");
					
					// case when fle is a subquery, then its from list elements are recursively traversed and searched for a match
					if(fle.getSubQueryStructure()!=null){
						Node k= transformToAbsoluteTableNames(n,fle.getSubQueryStructure().getFromListElements(),true,fle.getSubQueryStructure());
						if(k!=null&&!n.getTableNameNo().equalsIgnoreCase(k.getTableNameNo()))
							return k;		
					}
					// case when fle is a sub join, then its tabs are recursively traversed and search for a match
					else {
						Node k= transformToAbsoluteTableNames(n,fle.getBag(),true, qStruct);
						if(k!=null&& !n.getTableNameNo().equalsIgnoreCase(k.getTableNameNo()))
							return k;		
					}									
			}
			if(aliasNameFound 
					&& fle.getTableName()==null){
				logger.log(Level.FINE,"alias name found"+n);
				if(fle.getBag()!=null&&!fle.getBag().isEmpty()){
					Node k= transformToAbsoluteTableNames(n,fle.getBag(),true,qStruct);
					if(!n.getTableNameNo().equalsIgnoreCase(k.getTableNameNo()))
						return k;
				}
				if(fle.getSubQueryStructure()!=null){
					Node k= transformToAbsoluteTableNames(n,fle.getSubQueryStructure().getFromListElements(),true,fle.getSubQueryStructure());
					if(k!=null&&!n.getTableNameNo().equalsIgnoreCase(k.getTableNameNo()))
						return k;					

				}
				logger.log(Level.FINE," Alias name found, but column name cannot be resolved");
			}

			// if fle represents a sub join then its tabs contains it's components, in which 
			// case its tabs are recursively traversed for finding a match
			if(fle!=null && fle.getBag()!=null && !fle.getBag().isEmpty()){
				logger.log(Level.FINE," tabs is not null");
				Node k= transformToAbsoluteTableNames(n,fle.getBag(),false,qStruct);
				if(!n.getTableNameNo().equalsIgnoreCase(k.getTableNameNo()))
					return k;
			}
			// if fle represents a subquery then its columns' names/alias names are also examined for a match with n's column name
			if(fle!=null && fle.getSubQueryStructure()!=null 
					&& (aliasNameFound||n.getTableNameNo()==null||n.getTableNameNo().isEmpty())){
				logger.log(Level.FINE," subQueryParser: checking projected cols");
				
				Node k=transformToAbsoluteNamesForAliasNameFoundSubquery(n,fle.getSubQueryStructure());
				if(!n.getTableNameNo().equalsIgnoreCase(k.getTableNameNo()))
					return k;

				k=transformToAbsoluteTableNames(n,fle.getSubQueryStructure().getFromListElements(),false, fle.getSubQueryStructure());
				if(!n.getTableNameNo().equalsIgnoreCase(k.getTableNameNo()))
					return k;

			}
		}
		
		if(n.getTableAlias().equalsIgnoreCase(oldTableNameNo) && qStruct.parentQueryParser!=null){
			Node k=traverseAncestorsForAbsoluteNameTransformations(n,qStruct.parentQueryParser.getFromListElements(),false,qStruct.parentQueryParser);
			if(!n.getTableNameNo().equalsIgnoreCase(k.getTableNameNo()))
				return k;

		}
		
		return n;
	}

	private static Node traverseAncestorsForAbsoluteNameTransformations(Node n,
			Vector<FromClauseElement> fleList, boolean aliasNameFound, QueryStructure qStruct) {
		// TODO Auto-generated method stub
		for(FromClauseElement fle:fleList){
			//iterates through the fleList, for each fle, tries to match its table name/alias name
			//with the the name of the input node n
			//case when fle under consideration is a table, in which case if there is a match, then fle's
			//table name is copied to n's and also the respective table 
			if(fle!=null&&fle.getTableName()!=null){
				if(!n.getTableNameNo().isEmpty() && fle.getTableName().equalsIgnoreCase(n.getTableAlias())){
					n.setTableNameNo(fle.getTableNameNo());
					Table table=qStruct.getTableMap().getTable(fle.getTableName());
					n.setTable(table);
					//n.setColumn(new parsing.Column(n.getColumn().getColumnName(),table));
					n.isCorrelated = true; //POOJA
					n.setColumn(table.getColumn(n.getColumn().getColumnName()));
					logger.log(Level.FINE,"table Name Found in ancestor"+n);
					return n;
				}
				else if( !n.getTableNameNo().isEmpty() && fle.getAliasName().equalsIgnoreCase(n.getTableAlias())){
					n.setTableNameNo(fle.getTableNameNo());
					Table table=qStruct.getTableMap().getTable(fle.getTableName());
					if(table!=null){
						n.setTable(table);
						//Set isCorrelated to true if the subquery holds tables that are correlated.
						n.isCorrelated = true;
						//n.setColumn(new parsing.Column(n.getColumn().getColumnName(),table));
						n.setColumn(table.getColumn(n.getColumn().getColumnName()));
					}
					logger.log(Level.FINE,"alias Name Found in ancestor"+n);
					return n;
				}
				// case when there is table name of n does not match with table/alias name of fle
				else if( !n.getTableNameNo().isEmpty() && fle.getAliasName().equalsIgnoreCase(n.getTableAlias())){
					n.setTableNameNo(fle.getTableNameNo());
					Table table=qStruct.getTableMap().getTable(fle.getTableName());
					if(table!=null){
						n.setTable(table);
						//Set isCorrelated to true if the subquery holds tables that are correlated.
						n.isCorrelated = true;
						//n.setColumn(new parsing.Column(n.getColumn().getColumnName(),table));
						n.setColumn(table.getColumn(n.getColumn().getColumnName()));
					}
					logger.log(Level.FINE,"alias Name Found in ancestor"+n);
					return n;
				}
				// case when there is table name of n that does not match with table/alias name of fle
				// in which case fle's columns are searched for a column whose name is n's, if yes
				// then fle's table and table name is copied to n's
				else if(aliasNameFound){
					logger.log(Level.FINE,"alias Name Found but not n in ancestor");
					Table table=qStruct.getTableMap().getTable(fle.getTableName());
					parsing.Column c;
					if((c=table.getColumn(n.getColumn().getColumnName().toUpperCase()))!=null){
						n.setTableNameNo(fle.getTableNameNo());
						n.setTable(table);
						//n.setColumn(c);
						n.setColumn(table.getColumn(n.getColumn().getColumnName()));
						return n;
					}
				}
				//Case where column name of ancestor table is used in a subquery without any table alias.
				else if(n.getTableNameNo().isEmpty() && n.getTableAlias().isEmpty() && n.getTable() == null){
					logger.log(Level.FINE,"No Table/Aliasdetails exists other than Column Name. So find the table in fle and check if column name matches any parent table. ");
					//Table table=qStruct.getTableMap().getTable(fle.getTableName());
					Table table=qStruct.getTableMap().getTable(fle.getTableName().toUpperCase()); // added by rambabu
					parsing.Column c;
					if((c=table.getColumn(n.getColumn().getColumnName().toUpperCase()))!=null){
						n.setTableNameNo(fle.getTableNameNo());
						n.setTable(table);
						//n.setColumn(c);
						n.setColumn(table.getColumn(n.getColumn().getColumnName()));
						return n;
					}
				}
			}
			// considers the case fle alias is provided and fle is not a table (hence, is a subquery or a subjoin)
			// and alias name matches with n's table name
			if(fle!=null&&fle.getTableName()==null && fle.getAliasName()!=null&&
				fle.getAliasName().equalsIgnoreCase(n.getTableNameNo())){
				logger.log(Level.FINE," alias name is not null, but table name is null");
					
					// case when fle is a subquery, then its from list elements are recursively traversed and searched for a match
					if(fle.getSubQueryStructure()!=null){
						Node k=transformToAbsoluteNamesForAliasNameFoundSubquery(n,fle.getSubQueryStructure());
						if(k!=null&&!n.getTableNameNo().equalsIgnoreCase(k.getTableNameNo()))
							return k;		
					}
					// case when fle is a sub join, then its tabs are recursively traversed and search for a match
					else {
						Node k= traverseAncestorsForAbsoluteNameTransformations(n,fle.getBag(),true, qStruct);
						if(k!=null&& !n.getTableNameNo().equalsIgnoreCase(k.getTableNameNo()))
							return k;		
					}									
			}

			// if fle represents a sub join then its tabs contains it's components, in which 
			// case its tabs are recursively traversed for finding a match
			if(fle!=null && fle.getBag()!=null && !fle.getBag().isEmpty()){
				logger.log(Level.FINE," tabs is not null");
				Node k= traverseAncestorsForAbsoluteNameTransformations(n,fle.getBag(),false,qStruct);
				if(!n.getTableNameNo().equalsIgnoreCase(k.getTableNameNo()))
					return k;
			}
			// if fle represents a subquery then its columns' names/alias names are also examined for a match with n's column name
			if(fle!=null && fle.getSubQueryStructure()!=null 
					&& (aliasNameFound||n.getTableNameNo()==null||n.getTableNameNo().isEmpty())){
				logger.log(Level.FINE," subQueryParser: checking projected cols");
				
				Node k=transformToAbsoluteNamesForAliasNameFoundSubquery(n,fle.getSubQueryStructure());
				if(!n.getTableNameNo().equalsIgnoreCase(k.getTableNameNo()))
					return k;

			}
		}
		return n;
	
}

	private static Node transformToAbsoluteNamesForAliasNameFoundSubquery(Node n,QueryStructure subQueryStructure){
		logger.log(Level.FINE," subQueryParser: checking projected cols");

		for(Node m:subQueryStructure.getProjectedCols()){
			if(m.getAgg()!=null && m.getAgg().getAggAliasName()!=null){
				if(n.getColumn().getColumnName().equalsIgnoreCase(m.getAgg().getAggAliasName())){
					logger.log(Level.FINE," agg alias Name "+m.getAgg().getAggAliasName()+" node "+m);
					return m;
				}
			}					
			if(m.getColumn()!=null&&m.getColumn().getColumnName().equalsIgnoreCase(n.getColumn().getColumnName())){	
				logger.log(Level.FINE," column Name found in subQueryParser "+m);
				return m;
			}
			if(m.getAliasName()!=null&&m.getAliasName().equalsIgnoreCase(n.getColumn().getColumnName())){
				logger.log(Level.FINE," column Name found as alias in subQueryParser "+m);
				return m;
			}
		}	

		return n;
	}
	
	 //Check if the following methods are needed and why are they added to avoid extra tuples
	 /**
		 * This method returns a Node with join conditions as node inside node for sending to conjuncts
		 * for conversion
		 * 
		 * @param joinNodeList
		 * @return
		 * @throws Exception
		 */
	 private static RelationHierarchyNode generateRelationHierarchyJSQL(FromItem frmItem) throws Exception{
			
			RelationHierarchyNode node = null; 
			
			if(frmItem instanceof net.sf.jsqlparser.schema.Table){
				node = new RelationHierarchyNode(((net.sf.jsqlparser.schema.Table)frmItem).getFullyQualifiedName().toUpperCase());
			} 
			
			else if(frmItem instanceof SelectBody){
			
				PlainSelect selectNode = (PlainSelect) frmItem;
				//Vector<QueryTreeNode> v = selectNode..getFromList().getNodeVector();
				
				//for(QueryTreeNode i : v){
					node = generateRelationHierarchyJSQL(selectNode.getFromItem());
				//}

				List<RelationHierarchyNode> l = new ArrayList<RelationHierarchyNode>();
				Expression w = selectNode.getWhere();
				getAllNotExistsQueriesJSQL(w, l); 
				if(node!=null) {
					node.setNotExistsSubQueries(l);
				}
			}
			//Modified for JSQL Exists - End
			return node;
		}
	 
	 private static RelationHierarchyNode generateRelationHierarchyJSQL(PlainSelect plainsel) throws Exception{
			RelationHierarchyNode node = null;
			FromItem prev = plainsel.getFromItem();
			if(plainsel.getWhere() != null){
				
				node = generateRelationHierarchyJSQL(prev);
				
				List<RelationHierarchyNode> l = new ArrayList<RelationHierarchyNode>();
				Expression w = plainsel.getWhere();
				getAllNotExistsQueriesJSQL(w, l); 
				if(node !=null) {
					node.setNotExistsSubQueries(l);
				}
			}
			
			else{
				node = generateRelationHierarchyJSQL(prev);
			}
			return node;
		}//Added for Exists Subqueries-End
	 
	 
		public static Node getHierarchyOfJoinNode(Vector<Node> joinNodeList) throws Exception{
			Node nd = new Node();
			nd.setType(Node.getAndNodeType());
			if(joinNodeList != null &&  joinNodeList.size() > 0 && !joinNodeList.isEmpty()){
			 nd.setLeft(joinNodeList.get(0));
			}else
				nd.setLeft(null);
			Vector<Node> newList = new Vector<Node>();
			for(int i = 1 ; i < joinNodeList.size() ; i ++){
				newList.add(joinNodeList.get(i));
			}
			if(newList != null && newList.size()>0 && !newList.isEmpty()){
				nd.setRight(getHierarchyOfJoinNode(newList));
			}else{
				nd.setRight(null);
			}
			return nd;
			
		}
		private static void getAllNotExistsQueriesJSQL(Expression w, List<RelationHierarchyNode> l) throws Exception{
			if(w instanceof BinaryExpression){
				BinaryExpression whereNode = (BinaryExpression) w;
				getAllNotExistsQueriesJSQL(whereNode.getRightExpression(), l);
				getAllNotExistsQueriesJSQL(whereNode.getLeftExpression(), l);
			} 
			else if(w instanceof Parenthesis){
				Expression temp = ((Parenthesis) w).getExpression();
				if(temp instanceof ExistsExpression || temp instanceof InExpression){
					getAllNotExistsQueriesJSQL(temp, l);
				}
			} 
			else if((w instanceof ExistsExpression && ((ExistsExpression)w).isNot()) ){
				ExistsExpression notOp = (ExistsExpression) w;
				Expression temp = notOp.getRightExpression();
				if(temp instanceof SubSelect){
					SubSelect subQueryNode = (SubSelect)temp;
					
					PlainSelect ps = (PlainSelect)subQueryNode.getSelectBody();
					if(ps.getJoins() != null && ps.getJoins().size() > 0){
						
						 l.add(generateRelationHierarchyJSQL(ps));
						 
					}else{
						FromItem frm = ps.getFromItem();
						//if(frm instanceof Table){
						if(frm instanceof net.sf.jsqlparser.schema.Table){ // added by rambabu 
							 l.add(generateRelationHierarchyJSQL(frm));
						}
					}
					
					//}
				}
			}else if(w instanceof InExpression && ((InExpression)w).isNot()){
				InExpression notOp = (InExpression) w;
				
				if(notOp.getRightItemsList() instanceof SubSelect){
					SubSelect subQueryNode =(SubSelect)notOp.getRightItemsList();
					
					PlainSelect ps = (PlainSelect)subQueryNode.getSelectBody();
					 
					if(ps.getJoins() != null && ps.getJoins().size() > 0){
						
						// l.add(generateRelationHierarchyJSQL(ps));
						 
					}else{
						FromItem frm = ps.getFromItem();
						//if(frm instanceof Table){
						if(frm instanceof net.sf.jsqlparser.schema.Table){ // added by rambabu 
							 l.add(generateRelationHierarchyJSQL(frm));
						}
					}
				}
				else if(notOp.getLeftItemsList() instanceof SubSelect){
					SubSelect subQueryNode =(SubSelect)notOp.getLeftItemsList();
					
					PlainSelect ps = (PlainSelect)subQueryNode.getSelectBody();
					
					if(ps.getJoins() != null && ps.getJoins().size() > 0){
						
						// l.add(generateRelationHierarchyJSQL(ps));
						 
					}else{
						FromItem frm = ps.getFromItem();
						//if(frm instanceof Table){
						if(frm instanceof net.sf.jsqlparser.schema.Table){ // added by rambabu 
							 l.add(generateRelationHierarchyJSQL(frm));
						}
					}
				}
			}
		}
}
