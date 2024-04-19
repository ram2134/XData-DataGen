package generateConstraints;

import com.microsoft.z3.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import parsing.AggregateFunction;
import parsing.Column;
import parsing.ConjunctQueryStructure;
import parsing.Node;
import parsing.Table;
import stringSolver.StringConstraint;
import testDataGen.DataType;
import testDataGen.GenerateCVC1;
import testDataGen.QueryBlockDetails;
import util.Configuration;
import util.ConstraintObject;
import util.TagDatasets.QueryBlock;
import util.Utilities;

//import static com.microsoft.z3.Constructor.of;
import com.microsoft.z3.enumerations.Z3_ast_print_mode;

/**
 * This class generates the constraints based on solver in XData.Properties file
 * 
 * @author shree
 *
 */
//FIXME:  Needs fine tuning - methods can be combined adding more parameters in constraint object.

public class ConstraintGenerator {
	private static Logger logger = Logger.getLogger(ConstraintGenerator.class.getName());
	private static boolean isCVC3 = false;
	private String constraintSolver;
	private static String solverSpecificCommentCharacter;
	private static boolean isTempJoin = false;

	public static Context ctx = new Context();
	private static Solver solver = ctx.mkSolver();
	// TODO: rename ctxSorts to something more meaningful; it has declarations other than sorts
	public static HashMap<String, Sort> ctxSorts = new HashMap<String, Sort>();  // for storing Z3 context sorts; not able to extract directly from ctx.
	private static HashMap<String, FuncDecl> ctxFuncDecls = new HashMap<String, FuncDecl>();  // for storing Z3 context function declarations
	public static HashMap<String, Expr> ctxConsts = new HashMap<String, Expr>();
	
	private static IntExpr intNull = ctx.mkIntConst("intNullVal");
	private static RealExpr realNull = ctx.mkRealConst("realNullVal");

	/**
	 * Constructor
	 */
	public ConstraintGenerator() {
		setConstraintSolver(Configuration.getProperty("smtsolver"));

		 if(Configuration.getProperty("smtsolver").equalsIgnoreCase("cvc3")){
			 this.isCVC3 = true;
			 solverSpecificCommentCharacter="%";
		 }else {
			 this.isCVC3 = false;
			 solverSpecificCommentCharacter = ";";
		 }
		 
		 if(Configuration.getProperty("tempJoins").equalsIgnoreCase("true")){
			 this.isTempJoin = true;
		 }else {
			 this.isTempJoin = false;
		 }
	}
	
	/*
	 * Returns the Z3 context
	 */
	public Context getCtx() {
		return ctx;
	}
	
	/*
	 * Returns the Z3 solver corresponding to context
	 */
	public Solver getSolver() {
		return solver;
	}
	
	/**
	 * This method takes in the col name, table name, offset and position for columns on which the constraint is to be generated.
	 * This also takes the operator that joins the constraints. Checks the ConstraintContext for CVC/SMT.
	 * If it is CVC, it returns CVC format constraint, otherwise return SMT format constraint.
	 * 
	 * @param cvc
	 * @param tableName1
	 * @param offset1
	 * @param pos1
	 * @param tableName2
	 * @param offset2
	 * @param pos2
	 * @param col1
	 * @param col2
	 * @param operator
	 * @return
	 */
	
	public ConstraintObject getConstraint(String tableName1, Integer offset1, Integer pos1, String tableName2, Integer offset2, Integer pos2,
			Column col1, Column col2,String operator){
		
		ConstraintObject con = new ConstraintObject();
		if(isCVC3){
			con.setLeftConstraint("O_"+tableName1+"[" + offset1 + "]."+pos1);
			con.setRightConstraint("O_"+tableName2+"["+ offset2 +"]."+pos2);
			con.setOperator(operator);
		}
		else{
										
			con.setLeftConstraint(tableName1+"_"+col1.getColumnName()+pos1+" (select O_"+tableName1+" " + offset1 +")");
			con.setRightConstraint(tableName2+"_"+col2.getColumnName()+pos2+" (select O_"+tableName2+" "+ offset2 +")");
			con.setOperator(operator.toLowerCase());
		}
		return con;
	}	
    
	
	/**
	 * This method returns an assert constraint statement for the passed in columns and tupleIndices based on the solver.
	 * 
	 * @param cvc
	 * @param c1
	 * @param index1
	 * @param c2
	 * @param index2
	 * @param operator
	 * @return
	 */
	public String getAssertConstraint(Column c1, Integer index1, Column c2, Integer index2, String operator){
		
		String constraint = "";
		if(isCVC3){
			constraint += "ASSERT (" +cvcMap(c1, index1 + "") +" "+ operator+" "+cvcMap(c2,index2 + "") +" );\n" ;
		}
		else{
			IntExpr smtIndex1 = (IntExpr) ctx.mkInt(index1);
			IntExpr smtIndex2 = (IntExpr) ctx.mkInt(index2);
			constraint += "(assert ("+(operator.trim().equals("/=")? "not (= ": operator) +" "+smtMap(c1, smtIndex1)+" "+smtMap(c2, smtIndex2)+(operator.trim().equals("/=")? ")":"")+")) \n";						
		}
		return constraint;
	}

	/**
	 * This method returns an assert constraint statement for the passed in columns and tupleIndices based on the solver.
	 * 
	 * @param cvc
	 * @param c1
	 * @param index1
	 * @param c2
	 * @param index2
	 * @param operator
	 * @return
	 */
	public String getAssertConstraint(String constraint){
		
		String constr = "";
		if(isCVC3){
			constr += "ASSERT (" +constraint+" );\n" ;
		}
		else{
			constr += "(assert "+constraint+") \n";						
		}
		return constr;
	}
	
	/**
	 * 
	 * @param constraint1
	 * @param operator
	 * @param constraint2
	 * @return
	 */
public String getAssertConstraint(String constraint1, String operator, String constraint2){
		String constraint = "";
		
		if(isCVC3){
			constraint += "ASSERT (" +constraint1 +" "+operator+" "+constraint2+" );\n" ;
			if(constraint.length() <= 9){
				return "";
			}
		}
		else{
			
			constraint += "(assert ("+ (operator.equals("/=")? "not (= ": operator) +"  "+constraint1+ "  "+constraint2+ (operator.equals("/=")? " )":" ") +" )) \n";
			if(constraint.length() < 11){
				return "";
			}
		}
		
		
		return constraint;
	}


/**
 * This method gets the Assert Constraint with MAX function call.
 * 
 * @param constraint1
 * @param operator
 * @param constraint2
 * @return
 */
public String  getMaxAssertConstraintForSubQ(String constraint1, String operator, String constraint2){
	String constraint = "";
	
	if(isCVC3){
		//returnStr+= "ASSERT (MAX_"+columnName+n.getOperator()+"+O_"+ ConstraintGenerator.cvcMap(n.getRight().getColumn(), outerTupleNo+"")+");\n";
		constraint += "ASSERT (MAX_"+constraint1 +" "+operator+" "+constraint2+" );\n" ;
	}
	else{
		
		constraint += "(assert ("+operator+" (getMAX_"+constraint1+ " "+constraint2+") true)) \n";						
	}
	return constraint;
}

/**
 * This method gets the Assert Constraint with MAX function call.
 * 
 * @param constraint1
 * @param operator
 * @param constraint2
 * @return
 */
public String  getMinAssertConstraintForSubQ(String constraint1, String operator, String constraint2){
	String constraint = "";
	
	if(isCVC3){
		//returnStr+= "ASSERT (MAX_"+columnName+n.getOperator()+"+O_"+ ConstraintGenerator.cvcMap(n.getRight().getColumn(), outerTupleNo+"")+");\n";
		constraint += "ASSERT (MIN_"+constraint1 +" "+operator+" "+constraint2+" );\n" ;
	}
	else{
		
		constraint += "(assert ("+operator+" (getMIN_"+constraint1+ " "+constraint2+") true)) \n";						
	}
	return constraint;
}

	/**
	 * This method returns an assert constraint statement for the passed in columns and tupleIndices based on the solver.
	 * 
	 * @param cvc
	 * @param c1
	 * @param index1
	 * @param c2
	 * @param index2
	 * @param operator
	 * @return
	 */
	public String getAssertConstraint(String tableName1,Column col1, Integer index1,Integer pos1,String tableName2, Column col2, Integer index2, Integer pos2, String operator){
		
		String constraint = "";
		if(isCVC3){
			constraint += "ASSERT (" +"O_"+tableName1+"[" + index1 + "]."+pos1 +" "+ operator+" "+"O_"+tableName2+"["+ index2 +"]."+pos2 +" );\n" ;
		}
		else{
		
			constraint += "(assert ("+(operator.equals("/=")? "not (= ": operator)  +
						" ("+tableName1+"_"+col1.getColumnName()+pos1+" (select O_"+tableName1+" " + index1 +"))"+" ("
							+tableName2+"_"+col2.getColumnName()+pos2+" (select O_"+tableName2+" "+ index2 +"))"
							+(operator.equals("/=")? ")":"")+")) \n";						
		}
		return constraint;
	}
	
	/**
	 * This method returns an DISTINCT constraint statement for the passed in columns and tupleIndices based on the solver.
	 * 
	 * @param cvc
	 * @param c1
	 * @param index1
	 * @param c2
	 * @param index2
	 * @param operator
	 * @return
	 */
	public String getDistinctConstraint(String tableName1,Column col1, Integer index1,Integer pos1, String tableName2, Column col2, Integer index2, Integer pos2){
		
		String constraint = "";
		if(isCVC3){
			constraint += "DISTINCT (O_"+tableName1+"["+index1+"]."+pos1+ ",  O_"+tableName2+"["+index2+"]."+pos2+");\n" ;
		}
		else {
			
			constraint += "not (= ("+tableName1+"_"+col1.getColumnName()+pos1+" (select O_"+tableName1+" " + index1 +")"+") ("
							+tableName2+"_"+col2.getColumnName()+pos2+" (select O_"+tableName2+" "+ index2 +")"+")) \n";						
		}
		return constraint;
	}
	/**
	 * This method returns an DISTINCT constraint statement for the passed in columns and tupleIndices based on the solver.
	 * 
	 * @param cvc
	 * @param c1
	 * @param index1
	 * @param c2
	 * @param index2
	 * @param operator
	 * @return
	 */
	public String getDistinctConstraints(String tableName1,Column col1, Integer totalno,Integer pos1){
		
		String constraint = "DISTINCT (";
		if(isCVC3){
			for(int index1 = 1; index1 <= totalno; index1++) {
				if(index1 != 1)
					constraint += ", ";
				constraint += "O_"+tableName1+"["+index1+"]."+pos1;
			}
			constraint += ")";
		}
		
		return constraint;
	}
	
	/**
	 * This method returns an DISTINCT constraint statement for the passed in columns and tupleIndices based on the solver.
	 * 
	 * @param cvc
	 * @param c1
	 * @param index1
	 * @param c2
	 * @param index2
	 * @param operator
	 * @return
	 */
	public String getDistinctConstraint(Node agg, Integer index){
		
		String constraint = "";
		if(isCVC3){
			constraint += "DISTINCT( "+ ConstraintGenerator.cvcMapNode(agg, (index)+"")+" , "+ 
										ConstraintGenerator.cvcMapNode(agg, (index)+"")+")";
		}
		
		else{
			constraint += "(not (= "+ smtMapNode(agg, index+"")+" "+smtMapNode(agg, index+"")+"))\n";						
		}
		return constraint;
	}
	
	/**
	 * This method returns an assert constraint statement for the passed in columns and tuple Indices based on the solver.
	 * 
	 * @param tableName1
	 * @param col1
	 * @param index1
	 * @param pos1
	 * @param tableName2
	 * @param col2
	 * @param index2
	 * @param pos2
	 * @return
	 */
public String getAssertDistinctConstraint(String tableName1,Column col1, Integer index1,Integer pos1,String tableName2, Column col2, Integer index2, Integer pos2){
		
		String constraint = "";
		if(isCVC3){
			constraint += "ASSERT DISTINCT (O_"+tableName1+"["+index1+"]."+pos1+ ",  O_"+tableName2+"["+index2+"]."+pos2+");\n" ;
		}
		else{
			constraint += "(assert (distinct ("+tableName1+"_"+col1.getColumnName()+pos1+" (select O_"+tableName1+" " + index1 +")"+") ("
							+tableName2+"_"+col2.getColumnName()+pos2+" (select O_"+tableName2+" "+ index2 +")"+"))) \n";						
		}
		return constraint;
	}

	/**
	 * This method returns ASSERT TRUE constraint based on the solver
	 * @return
	 */
	public String getAssertTrue(){
		String constraint = "";
		if(isCVC3){
			constraint += "ASSERT TRUE;\n" ;
		}
		else{
			constraint += "(assert true) \n";						
		}
		return constraint;
	}
	
	
	public String getNegatedConstraint(String constraint){
			String negConstraint = constraint;
			
			if(isCVC3){
				negConstraint = negConstraint.replaceFirst("ASSERT ", "ASSERT NOT(");
				negConstraint = negConstraint.replace(";", ");");
			}else{
				negConstraint = negConstraint.replace("(assert ","(assert (not " );
				negConstraint += ") \n";
			}
					
			return negConstraint;	
	}


/**
 * Generates positive CVC3 constraint for given nodes and columns
 * @param col1
 * @param n1
 * @param col2
 * @param n2
 * @return
 */
public static String getPositiveStatement(Column col1, Node n1, Column col2, Node n2){
	
	if(isCVC3){
			return "ASSERT "+cvcMap(col1, n1) +" = "+cvcMap(col2, n2)+";\n";
	}else{
		return "(assert (="+" "+smtMap(col1, n1)+" "+smtMap(col2, n2)+"))\n";			
	}
}

	/**
	 * This method returns a String with ISNULL constraint
	 * 
	 * @param cvc
	 * @param col
	 * @param offSet
	 * @return
	 */
	public String getIsNullCondition(String tableName, Column col,String offSet){

		String isNullConstraint = "";

		if(col.getCvcDatatype().equals("INT")|| col.getCvcDatatype().equals("REAL") || col.getCvcDatatype().equals("DATE") 
				|| col.getCvcDatatype().equals("TIME") || col.getCvcDatatype().equals("TIMESTAMP")) {
			isNullConstraint = "ISNULL_" + col.getColumnName() + "(" + cvcMap(col, offSet + "") + ")";
		} else {
			isNullConstraint = "ISNULL_" + col.getCvcDatatype() + "(" + cvcMap(col, offSet+ "") + ")";
		}
		return isNullConstraint;
	}
	
	/**
	 * This method returns an Expr with ISNULL constraint (for Z3)
	 * 
	 * @param cvc
	 * @param col
	 * @param offSet
	 * @return
	 */
	public BoolExpr getIsNullConditionZ3(String tableName, Column col,String offSet) {
		String suffix;
		if(col.getCvcDatatype().equalsIgnoreCase("INT")|| col.getCvcDatatype().equalsIgnoreCase("REAL") || col.getCvcDatatype().equalsIgnoreCase("DATE") 
				|| col.getCvcDatatype().equalsIgnoreCase("TIME") || col.getCvcDatatype().equalsIgnoreCase("TIMESTAMP")) {
			suffix = col.getColumnName();
		} else {
			suffix = col.getCvcDatatype();
		}
		
		FuncDecl isNullDecl = ctxFuncDecls.get("ISNULL_"+suffix);
		BoolExpr isNullApply = (BoolExpr) isNullDecl.apply(smtMap(col, (IntExpr) ctx.mkInt(offSet)));

		return isNullApply;
	}

	
	/**
	 * This method returns the constraint String that holds MAX constraint for SubQuery Aggregate condition.
	 * 
	 * @param aggNode
	 * @param index
	 * @param columnName
	 * @param myCount
	 * @return
	 */
	public static String getMaxConstraintForSubQ(Node aggNode, Integer index, Column column,Integer myCount){
		String returnStr = "";
		String maxStr="";
		
		if(isCVC3){
			
			maxStr = "MAX_"+column.getColumnName()+ ": "+column.getColumnName()+";\n ASSERT(";
			for(int i=1;i<=myCount;i++){
				maxStr+="("+ ConstraintGenerator.cvcMapNode(aggNode, index+"")+"=MAX_"+column.getColumnName()+ ") OR";
				returnStr+= "ASSERT ("+ ConstraintGenerator.cvcMapNode(aggNode,index+"")+"<=MAX_"+column.getColumnName()+ ");\n";
			}
			maxStr=maxStr.substring(0, maxStr.length()-3)+");\n";
			return maxStr+returnStr;
			
		}else{
			
			maxStr = "(define-sort MAX_"+column.getColumnName()+" "+column.getCvcDatatype()+")\n";
			for(int i=1;i<=myCount;i++){
				maxStr += "(define-fun getMAX"+column.getColumnName()+" ((MAX_"+column.getColumnName()+" "+column.getCvcDatatype()+")) Bool \n\t\t\t";
				maxStr +="(or (= "+ConstraintGenerator.cvcMapNode(aggNode, index+"")+" (MAX_"+column.getColumnName()+"))\n\t\t\t  "
							+"(<= "+ ConstraintGenerator.cvcMapNode(aggNode,index+"")+" "+" (MAX_"+column.getColumnName()+")))\n";
				maxStr += ")\n";
				
				returnStr += "(assert (= (getMAX"+column.getColumnName()+" "+ConstraintGenerator.cvcMapNode(aggNode, index+"")+") true))\n";
			}
			return maxStr+returnStr;
		}
	}
	
	/**
	 * This method returns the constraint String that holds MIN constraint for SubQuery Aggregate condition.
	 * 
	 * @param aggNode
	 * @param index
	 * @param columnName
	 * @param myCount
	 * @return
	 */
	public static String getMinConstraintForSubQ(Node aggNode, Integer index, Column column,Integer myCount){
		String returnStr = "";
		String maxStr="";
		
		if(isCVC3){
			
			maxStr = "MIN_"+column.getColumnName()+ ": "+column.getColumnName()+";\n ASSERT(";
			for(int i=1;i<=myCount;i++){
				maxStr+="("+ ConstraintGenerator.cvcMapNode(aggNode, index+"")+"=MIN_"+column.getColumnName()+ ") OR";
				returnStr+= "ASSERT ("+ ConstraintGenerator.cvcMapNode(aggNode,index+"")+">=MIN_"+column.getColumnName()+ ");\n";
			}
			maxStr=maxStr.substring(0, maxStr.length()-3)+");\n";
			return maxStr+returnStr;
			
		}else{
			
			maxStr = "(define-sort MIN_"+column.getColumnName()+" "+column.getCvcDatatype()+")\n";
			for(int i=1;i<=myCount;i++){
				maxStr += "(define-fun getMIN"+column.getColumnName()+" ((MIN_"+column.getColumnName()+" "+column.getCvcDatatype()+")) Bool \n\t\t\t";
				maxStr +="(or (= "+ConstraintGenerator.cvcMapNode(aggNode, index+"")+" (MIN_"+column.getColumnName()+"))\n\t\t\t  "
							+"(>= "+ ConstraintGenerator.cvcMapNode(aggNode,index+"")+" "+" (MIN_"+column.getColumnName()+")))\n";
				maxStr += ")\n";
				
				returnStr += "(assert (= (getMIN"+column.getColumnName()+" "+ConstraintGenerator.cvcMapNode(aggNode, index+"")+") true))\n";
			}
			return maxStr+returnStr;
		}
	}
	
	
	/**
	 * This method defines the solver function for getting MAX value constraint in CVC/SMT format.
	 * 
	 * @param cvc
	 * @param queryBlock
	 * @param n
	 * @return
	 */
	public String getMaxConstraint(QueryBlockDetails queryBlock, Node n,Boolean isLeftConstraint){
		String returnStr = "";
		if(isLeftConstraint){
			if(isCVC3){
					returnStr = "MAX: TYPE = SUBTYPE(LAMBDA(x: INT): " + " x " + n.getOperator() + n.getRight().toCVCString(10, queryBlock.getParamMap());
					if(n.getOperator().equalsIgnoreCase("<") || n.getOperator().equalsIgnoreCase("<=")){ 
						returnStr += " AND x > 0 );";
					}
					else if(n.getOperator().equalsIgnoreCase(">") || n.getOperator().equalsIgnoreCase(">=")){
						returnStr += " AND x < 10000000 );";
					}
					else{//operator is = or /=
						returnStr += ");";
					}
			}
			else{
				String dataType = n.getLeft().getColumn().getCvcDatatype();
				/* if(dataType != null){
					returnStr = "(define-fun MAX_"+n.getLeft().getColumn()+" ((x "+dataType+")) "+dataType+" ("+n.getOperator()+" x "+n.getRight().toCVCString(10, queryBlock.getParamMap())+") (";
					if(n.getOperator().equalsIgnoreCase("<") || n.getOperator().equalsIgnoreCase("<=")){ 
						returnStr += "> x  0)";
					}
					else if(n.getOperator().equalsIgnoreCase(">") || n.getOperator().equalsIgnoreCase(">=")){
						returnStr += " < x 10000000 )";
					}else{
						returnStr += ")";
					}
					
					returnStr += ")";
				}		
				*/
				
				if(dataType != null){
					returnStr = "(declare-const MAX_"+n.getLeft().getColumn()+" "+dataType+")\n";
					returnStr += "(assert ("+n.getOperator()+" MAX_"+n.getLeft().getColumn()+" "+n.getRight().toCVCString(10, queryBlock.getParamMap())+"))\n ";
					if(n.getOperator().equalsIgnoreCase("<") || n.getOperator().equalsIgnoreCase("<=")){ 
						returnStr += "(assert (> MAX_"+n.getLeft().getColumn()+" 0))\n";
					}
					else if(n.getOperator().equalsIgnoreCase(">") || n.getOperator().equalsIgnoreCase(">=")){
						returnStr += "(assert (< MAX_"+n.getLeft().getColumn()+" 10000000))\n";
					}else{
						returnStr += ")";
					}
				}
			}
		}else{
			if(isCVC3){
				returnStr = "MAX: TYPE = SUBTYPE(LAMBDA(x: INT): " + n.getLeft().toCVCString(10, queryBlock.getParamMap()) + " " + n.getOperator() + " x ";
				if(n.getOperator().equalsIgnoreCase("<") || n.getOperator().equalsIgnoreCase("<=")){
					returnStr += " AND x < 10000000 );";
				}
				else if(n.getOperator().equalsIgnoreCase(">") || n.getOperator().equalsIgnoreCase(">=")){
					returnStr += " AND x > 0 );";
				}
				else{//operator is = or /=
					returnStr += ";";
				}
			}else{
				String dataType = n.getRight().getColumn().getCvcDatatype();
				if(dataType != null){
					returnStr = "(declare-const MAX_"+n.getRight().getColumn()+" "+dataType+")\n";
					returnStr = "(assert ("+n.getOperator()+" "+n.getLeft().toCVCString(10, queryBlock.getParamMap())+" MAX_"+n.getRight().getColumn()+"))\n ";
					//returnStr = "(define-fun MAX_"+n.getRight().getColumn()+" ((x "+dataType+")) "+dataType+" ("+n.getOperator()+" "+n.getLeft().toCVCString(10, queryBlock.getParamMap())+ "x) (";
					if(n.getOperator().equalsIgnoreCase("<") || n.getOperator().equalsIgnoreCase("<=")){ 
						returnStr += "< x 10000000 )";
					}
					else if(n.getOperator().equalsIgnoreCase(">") || n.getOperator().equalsIgnoreCase(">=")){
						returnStr += " > x  0)";
					}else{
						returnStr += ")";
					}
					
					returnStr += ")";
				}		
			}
			
		}
		return returnStr;
	}
	
	
	/**
	 * This method defines the solver function for getting MAX value constraint in CVC/SMT format.
	 * 
	 * @param cvc
	 * @param queryBlock
	 * @param n
	 * @return
	 */
	public String getMinConstraint(QueryBlockDetails queryBlock, Node n,Boolean isLeftConstraint){
		String returnStr = "";
		
		if(isLeftConstraint){
				if(isCVC3){
					returnStr = "MIN: TYPE = SUBTYPE(LAMBDA(x: INT): " + " x " + n.getOperator() + n.getRight().toCVCString(10, queryBlock.getParamMap());
					if(n.getOperator().equalsIgnoreCase("<") || n.getOperator().equalsIgnoreCase("<=")){ 
						returnStr += " AND x > 0 );";
					}
					else if(n.getOperator().equalsIgnoreCase(">") || n.getOperator().equalsIgnoreCase(">=")){
						returnStr += " AND x < 10000000 );";
					}
					else{//operator is = or /=
						returnStr += ");";
					}
				}
				else{
					String dataType = n.getLeft().getColumn().getCvcDatatype();
						
					if(dataType != null){
						returnStr = "(declare-const MIN_"+n.getLeft().getColumn()+" "+dataType+")\n";
						returnStr += "(assert ("+n.getOperator()+" MIN_"+n.getLeft().getColumn()+" "+n.getRight().toCVCString(10, queryBlock.getParamMap())+"))\n ";
						if(n.getOperator().equalsIgnoreCase("<") || n.getOperator().equalsIgnoreCase("<=")){ 
							returnStr += "(assert (> MIN_"+n.getLeft().getColumn()+" 0))\n";
						}
						else if(n.getOperator().equalsIgnoreCase(">") || n.getOperator().equalsIgnoreCase(">=")){
							returnStr += "(assert (< MIN_"+n.getLeft().getColumn()+" 10000000))\n";
						}else{
							returnStr += ")";
						}
		
					}
				}
		}
		else{
			if(isCVC3){
				returnStr = "MIN: TYPE = SUBTYPE(LAMBDA(x: INT): " + n.getLeft().toCVCString(10, queryBlock.getParamMap()) + " " + n.getOperator() + " x ";
				if(n.getOperator().equalsIgnoreCase("<") || n.getOperator().equalsIgnoreCase("<=")){
					returnStr += " AND x < 10000000 );";
				}
				else if(n.getOperator().equalsIgnoreCase(">") || n.getOperator().equalsIgnoreCase(">=")){
					returnStr += " AND x > 0 );";
				}
				else{//operator is = or /=
					returnStr += ");";
				}
			}else{
				
				String dataType = n.getRight().getColumn().getCvcDatatype();
				if(dataType != null){
					returnStr = "(declare-const MIN_"+n.getRight().getColumn()+" "+dataType+")\n";
					returnStr += "(assert ("+n.getOperator()+" "+n.getLeft().toCVCString(10, queryBlock.getParamMap())+" MIN_"+n.getRight().getColumn()+"))\n ";

					if(n.getOperator().equalsIgnoreCase(">") || n.getOperator().equalsIgnoreCase(">=")){ 
						returnStr += "(assert (> MIN_"+n.getRight().getColumn()+" 0))\n";
					}
					else if(n.getOperator().equalsIgnoreCase("<") || n.getOperator().equalsIgnoreCase("<=")){
						returnStr += "(assert (< MIN_"+n.getRight().getColumn()+" 10000000))\n";
					}else{
						returnStr += ")";
					}
					
				}		
			}
			
		}
	return returnStr;
	}
	
	/**
	 * This method is used to get the SMT / CVC constraints for aggregation function : AVG 
	 *  
	 * @param cvc
	 * @param myCount
	 * @param groupNumber
	 * @param multiples
	 * @param totalRows
	 * @param agg
	 * @param offset
	 * @return
	 */
	public String getAVGConstraint(Integer myCount,Integer groupNumber,Integer multiples,Integer totalRows,Node agg, Integer offset){

	String returnStr = "";
	int extras = totalRows%myCount;
	if(isCVC3){
		
		returnStr +="\n ASSERT ";
			for(int i=1,j=0;i<=myCount;i++,j++){
	
				int tuplePos=(groupNumber)*myCount+i;
	
				if(j<extras)
					returnStr += (multiples+1)+"*("+ getMapNode(agg, (tuplePos+offset-1)+"")+")";
				else
					returnStr += (multiples)+"*("+ getMapNode(agg, (tuplePos+offset-1)+"")+")";
	
				if(i<myCount){
					returnStr += "+";
				}
			}
			return returnStr + ") / "+totalRows + "; \n";
	}
	else{
		//Check if it is correct - 
		//returnStr += " (/ (";
		String str = "";
		
		for(int i=1,j=0;i<=myCount;i++,j++){
			
			String str1 = str;
			int tuplePos=(groupNumber)*myCount+i;

			if(i<=myCount && str1 != null && !str1.isEmpty()){
				returnStr += "(+ "+str+" ";
			}
			String function = "SUM_REPLACE_NULL_"+agg.getColumn().getCvcDatatype();

			if(j<extras)
				str = "\n \t (* "+(multiples+1)+" ("+function+getMapNode(agg, (tuplePos+offset-1)+"")+"))";
			else
				str = "\n \t (* "+(multiples)+" ("+function+ getMapNode(agg, (tuplePos+offset-1)+"")+"))";

			if(i<=myCount && str1 != null && !str1.isEmpty()){
				returnStr += str+ ") ";
			}
			if(returnStr!= null && !returnStr.isEmpty()){
				str = returnStr;
				if(i != myCount){
				   returnStr="";
				}
			}
			
			if(myCount==1 && i==myCount){
				returnStr = str;
			}
					
		}
		return "(/  \n "+returnStr+" \n"+myCount+")";
		
		/*
		for(int i=1,j=0;i<=myCount;i++,j++){
			
			int tuplePos=(groupNumber)*myCount+i;

			if(i<=myCount && str != null && !str.isEmpty()){
				returnStr += "(+ "+str+" (";
			}
			
			if(j<extras)
				str += "* "+(multiples+1)+" "+ getMapNode(agg, (tuplePos+offset-1)+"")+"";
			else
				str += "* "+(multiples)+" "+ getMapNode(agg, (tuplePos+offset-1)+"")+"";

			if(i<myCount){
				//returnStr += ")) ";
			}
			if(i==1 && j==0){
				returnStr += str;
			}
		}
		return returnStr + ") "+totalRows + ") \n";
		
	*/	
	}
	}
	
	
	/**
	 * This method is used to get the SMT / CVC constraints for aggregation function : AVG 
	 *  
	 * @param cvc
	 * @param myCount
	 * @param groupNumber
	 * @param multiples
	 * @param totalRows
	 * @param agg
	 * @param offset
	 * @return
	 */
	public String getAVGConstraintForSubQ(Integer myCount,Integer groupNumber,Integer multiples,Integer totalRows,Node agg, Integer offset){

	String returnStr = "";
	int extras = totalRows%myCount;
	int groupOffset = groupNumber * myCount;
	
	if(isCVC3){
		
		returnStr +="\n ASSERT ";
			for(int i=1,j=0;i<=myCount;i++,j++){
				if(j<extras)
					returnStr += (multiples+1)+"*("+ getMapNode(agg, (groupOffset+offset+i)+"")+")";
				else
					returnStr += (multiples)+"*("+ getMapNode(agg, (groupOffset+offset+i)+"")+")";
	
				if(i<myCount){
					returnStr += "+";
				}
			}
			return returnStr + ") / "+totalRows + "; \n";
	}
	else{
		//Check if it is correct - 
		returnStr += "(assert (/ (";
		for(int i=1,j=0;i<=myCount;i++,j++){
			String str = "";
			
			if(i<myCount && str != null && !str.isEmpty()){
				returnStr += "(+ "+str+" (";
			}
			
			if(j<extras)
				str += "* "+(multiples+1)+" "+ getMapNode(agg, (groupOffset+offset+i)+"")+"";
			else
				str += "* "+(multiples)+" "+ getMapNode(agg, (groupOffset+offset+i)+"")+"";

			if(i<myCount){
				returnStr += ")) ";
			}
			if(i==1 && j==0){
				returnStr += str;
			}
		}
		return returnStr + ") "+totalRows + ")) \n";
	}
	}
	
	
	/**
	 * This method is used to get the SMT / CVC constraints for aggregation function : MIN 
	 *  
	 * @param innerTableNo
	 * @param groupNumber
	 * @param totalRows
	 * @param cvc
	 * @param af
	 * @return
	 */
	public String getMinAssertConstraint(String innerTableNo,Integer groupNumber,int totalRows,GenerateCVC1 cvc,AggregateFunction af){
		
		String returnStr = "";
		

		int myCount = cvc.getNoOfTuples().get(innerTableNo);
		int offset = cvc.getRepeatedRelNextTuplePos().get(innerTableNo)[1];
		
		if(isCVC3){
			
			returnStr += "\nASSERT EXISTS(i: MIN): (";
	
			for(int i=1;i<=totalRows;i++){
				int tuplePos=(groupNumber)*myCount+i;
				returnStr += GenerateCVCConstraintForNode.cvcMapNode(af.getAggExp(), tuplePos+offset-1+"") + " >= " + "i ";
				if(i<totalRows){
					returnStr += " AND ";
				}
			}
			returnStr += ") AND (";
			for(int i=1;i<=totalRows;i++){
				int tuplePos=(groupNumber)*myCount+i;
				returnStr += GenerateCVCConstraintForNode.cvcMapNode(af.getAggExp(), tuplePos+offset-1+"") + " = " + "i ";
				if(i<totalRows){
					returnStr += " OR ";
				}
			}
			returnStr += ");";
		}
		else {
			
			String tableName = af.getAggExp().getColumn().getTable().getTableName();
			String columnName = af.getAggExp().getColumn().getColumnName();
			int index = af.getAggExp().getColumn().getTable().getColumnIndex(columnName);
			
			
			myCount = cvc.getNoOfTuples().get(innerTableNo);
			offset = cvc.getRepeatedRelNextTuplePos().get(innerTableNo)[1];
			String function = "MIN_REPLACE_NULL_"+af.getAggExp().getColumn().getCvcDatatype();
			String Nullfunction = "CHECKALL_NULL_"+af.getAggExp().getColumn().getCvcDatatype();

			
			
			returnStr += "\n(assert ";
			if(totalRows > 1)
				returnStr += "(and";
			for(int i=1;i<=totalRows;i++){
				int tuplePos=(groupNumber)*myCount+i;
				int row = tuplePos+offset-1;
				returnStr += "\n ( >= MIN_"+columnName+" ("+function+"("+tableName+"_"+columnName+index+"(select O_"+tableName+" "+row+")) ))"; 				
			}
			if(totalRows > 1)
				returnStr += ")";
			returnStr += ")\n";
			
			returnStr += "\n(assert ";
			if(totalRows > 1)
				returnStr += "(or";
			for(int i=1;i<=totalRows;i++){
				int tuplePos=(groupNumber)*myCount+i;
				int row = tuplePos+offset-1;
				returnStr += "\n ( = MIN_"+columnName+" ("+function+"("+tableName+"_"+columnName+index+"(select O_"+tableName+" "+row+") )))"; 
			}
			if(totalRows > 1)
				returnStr += ")";
			returnStr += ")\n";
			
			returnStr += "\n(assert ";
			if(totalRows > 1)
				returnStr += "(not (and";
			for(int i=1;i<=totalRows;i++){
				int tuplePos=(groupNumber)*myCount+i;
				int row = tuplePos+offset-1;
				returnStr += "\n ("+Nullfunction+"("+tableName+"_"+columnName+index+" (select O_"+tableName+" "+row+")) )"; 
			}
			if(totalRows > 1)
				returnStr += ")";
			returnStr += "))";
		}
		
		return returnStr;
		
		}
	
	
	/**
	 * This method is used to get the SMT / CVC constraints for aggregation function : MIN 
	 *  
	 * @param innerTableNo
	 * @param groupNumber
	 * @param totalRows
	 * @param cvc
	 * @param af
	 * @return
	 */
	public String getMaxAssertConstraint(String innerTableNo,Integer groupNumber,int totalRows,GenerateCVC1 cvc,AggregateFunction af){
		
		String returnStr = "";
		

		int myCount = cvc.getNoOfTuples().get(innerTableNo);
		int offset = cvc.getRepeatedRelNextTuplePos().get(innerTableNo)[1];
		
		if(isCVC3){
			returnStr += "\nASSERT EXISTS(i: MAX): (";

			
			for(int i=1;i<=totalRows;i++){
				int tuplePos=(groupNumber)*myCount+i;
				//Add the expression value to the other side of eqn - ie., to i value so that condition is satisfied
				//If Aggregate has an expression
				returnStr += GenerateCVCConstraintForNode.cvcMapNode(af.getAggExp(), tuplePos+offset-1+"") + " <= " +"i ";//+GenerateCVCConstraintForNode.cvcMapNode(getValueForExpressionsInAgg(af),tuplePos+offset-1+"");//+"i ";
				if(i<totalRows){
					returnStr += " AND ";
				}
			}
			returnStr += ") AND (";
			for(int i=1;i<=totalRows;i++){
				int tuplePos=(groupNumber)*myCount+i;
				returnStr += GenerateCVCConstraintForNode.cvcMapNode(af.getAggExp(), tuplePos+offset-1+"") + " = " +"i ";//+GenerateCVCConstraintForNode.cvcMapNode(getValueForExpressionsInAgg(af),tuplePos+offset-1+"");//"i ";
				if(i<totalRows){
					returnStr += " OR ";
				}
			}
			returnStr += "); ";
		}
		else {
			
			String tableName = af.getAggExp().getColumn().getTable().getTableName();
			String columnName = af.getAggExp().getColumn().getColumnName();
			int index = af.getAggExp().getColumn().getTable().getColumnIndex(columnName);
			String function = "MAX_REPLACE_NULL_"+af.getAggExp().getColumn().getCvcDatatype();
			String Nullfunction = "CHECKALL_NULL_"+af.getAggExp().getColumn().getCvcDatatype();
			
			myCount = cvc.getNoOfTuples().get(innerTableNo);
			offset = cvc.getRepeatedRelNextTuplePos().get(innerTableNo)[1];
	
			returnStr += "\n(assert ";
			if(totalRows > 1)
				returnStr += "(and";
			for(int i=1;i<=totalRows;i++){
				int tuplePos=(groupNumber)*myCount+i;
				int row = tuplePos+offset-1;
				returnStr += "\n ( <= MAX_"+columnName+" ("+function+"("+tableName+"_"+columnName+index+" (select O_"+tableName+" "+row+")) ))"; 				
			}
			if(totalRows > 1)
				returnStr += ")";
			returnStr += ")\n";
			
			returnStr += "\n(assert ";
			if(totalRows > 1)
				returnStr += "(or";
			for(int i=1;i<=totalRows;i++){
				int tuplePos=(groupNumber)*myCount+i;
				int row = tuplePos+offset-1;
				returnStr += "\n ( = MAX_"+columnName+" ("+function+"("+tableName+"_"+columnName+index+" (select O_"+tableName+" "+row+")) ))"; 
			}
			if(totalRows > 1)
				returnStr += ")";
			returnStr += ")\n";
			
			
			returnStr += "\n(assert ";
			if(totalRows > 1)
				returnStr += "(not (and";
			for(int i=1;i<=totalRows;i++){
				int tuplePos=(groupNumber)*myCount+i;
				int row = tuplePos+offset-1;
				returnStr += "\n ("+Nullfunction+"("+tableName+"_"+columnName+index+" (select O_"+tableName+" "+row+")) )"; 
			}
			if(totalRows > 1)
				returnStr += ")";
			returnStr += "))";
		}
		
		return returnStr;
	}
	
	
	
	
	/**
	 * 
	 * @param cvc
	 * @param myCount
	 * @param groupNumber
	 * @param multiples
	 * @param totalRows
	 * @param agg
	 * @param offset
	 * @return
	 */
	public String getSUMConstraint(Integer myCount,Integer groupNumber,Integer multiples,Integer totalRows,Node agg, Integer offset){

		String returnStr = "";
		int extras = totalRows%myCount;
		if(isCVC3){
			
			for(int i=1,j=0;i<=myCount;i++,j++){
				int tuplePos=(groupNumber)*myCount+i;
				
				if(j<extras)
					returnStr += (multiples+1)+"*("+ GenerateCVCConstraintForNode.cvcMapNode(agg, tuplePos+offset-1+"")+")";
				else
					returnStr += (multiples)+"*("+ GenerateCVCConstraintForNode.cvcMapNode(agg, tuplePos+offset-1+"")+")";

				if(i<myCount){
					returnStr += "+";
				}
			}
			return returnStr +"\n";
		}else{
			//returnStr += "(assert ";
			String str = "";
			for(int i=1,j=0;i<=myCount;i++,j++){
				
				String str1 = str;
				int tuplePos=(groupNumber)*myCount+i;

				if(i<=myCount && str1 != null && !str1.isEmpty()){
					returnStr += "(+ "+str+" ";
				}
				String function = "SUM_REPLACE_NULL_"+agg.getColumn().getCvcDatatype();

				if(j<extras)
					str = "\n \t (* "+(multiples+1)+" ("+function+getMapNode(agg, (tuplePos+offset-1)+"")+"))";
				else
					str = "\n \t (* "+(multiples)+" ("+function+ getMapNode(agg, (tuplePos+offset-1)+"")+"))";

				if(i<=myCount && str1 != null && !str1.isEmpty()){
					returnStr += str+ ") ";
				}
				if(returnStr!= null && !returnStr.isEmpty()){
					str = returnStr;
					if(i != myCount){
					   returnStr="";
					}
				}
				
				if(myCount==1 && i==myCount){
					returnStr = str;
				}
						
			}
			return returnStr+"\n";
		}
		
	}
	
	/**
	 * This method returns Constraint String for Aggregate node SUM in the subquery
	 * 
	 * @param myCount
	 * @param groupNumber
	 * @param multiples
	 * @param totalRows
	 * @param agg
	 * @param offset
	 * @return
	 */
	public String getSUMConstraintForSubQ(Integer myCount,Integer groupNumber,Integer multiples,Integer totalRows,Node agg, Integer offset){

		String returnStr = "";
		int extras = totalRows%myCount;
		boolean isDistinct = agg.isDistinct();
		int groupOffset = groupNumber * myCount;
		
		if(isCVC3){
	
			if(isDistinct && myCount>1){//mahesh: add
					//there will be three elements in the group
					int ind=0;
					for(int m=0;m<2;m++){
						returnStr +="(";
						for(int i=0,j=0;i<myCount-1;i++,j++){
							if(j<extras)
								returnStr += (multiples+1)+"*("+ ConstraintGenerator.cvcMapNode(agg, (i+offset+ind+groupOffset)+"")+")";
							else
								returnStr += (multiples)+"*("+ ConstraintGenerator.cvcMapNode(agg, (i+offset+ind+groupOffset)+"")+")";
							//" DISTINCT (O_"+ cvcMap(col,group+offset-1+"") +", O_"+ cvcMap(col,(group+aliasCount-1+offset)+"") +") "
							if(i<myCount-2){
								returnStr += "+";
							}
						}
						//add contsraint for distinct
						returnStr+=" )) AND ";
						for(int i=0,j=0;i<myCount-2;i++,j++)
						{
							returnStr +="DISTINCT( "+ ConstraintGenerator.cvcMapNode(agg, (i+offset+ind+groupOffset)+"")+" , "+ ConstraintGenerator.cvcMapNode(agg, (i+offset+ind+1+groupOffset)+"")+") AND ";
						}
						returnStr = returnStr.substring(0,returnStr.lastIndexOf("AND")-1);
						ind++;
						returnStr +=") OR ";
					}
					if(returnStr.contains("OR"))
						returnStr = returnStr.substring(0,returnStr.lastIndexOf("OR")-1);
					return returnStr;
				}
				for(int i=0,j=0;i<myCount;i++,j++){
					if(j<extras)
						returnStr += (multiples+1)+"*("+ ConstraintGenerator.cvcMapNode(agg, (i+offset+groupOffset)+"")+")";
					else
						returnStr += (multiples)+"*("+ ConstraintGenerator.cvcMapNode(agg, (i+offset+groupOffset)+"")+")";
			
					if(i<myCount-1){
						returnStr += "+";
					}
				}
				return returnStr;
		}
		else{
			ConstraintObject constrObj = new ConstraintObject();
			ArrayList<ConstraintObject> constrList = new ArrayList<ConstraintObject>();
			
			ConstraintObject constrObj1 = new ConstraintObject();
			ArrayList<ConstraintObject> constrList1 = new ArrayList<ConstraintObject>();
			
			
			if(isDistinct && myCount>1){//mahesh: add
				//there will be three elements in the group.
				constrObj1 = new ConstraintObject();
				int ind=0;
				for(int m=0;m<2;m++){
				//	returnStr +="(and (";
					for(int i=0,j=0;i<myCount-1;i++,j++){
						
						if(i<myCount-2){
							returnStr += "(+ (";
						}
						
						constrObj = new ConstraintObject();
						if(j<extras)
							returnStr += "(* "+ (multiples+1)+" "+ ConstraintGenerator.cvcMapNode(agg, (i+offset+ind+groupOffset)+"")+")";
						else
							returnStr += "(* "+(multiples)+" "+ ConstraintGenerator.cvcMapNode(agg, (i+offset+ind+groupOffset)+"")+")";
						//" DISTINCT (O_"+ cvcMap(col,group+offset-1+"") +", O_"+ cvcMap(col,(group+aliasCount-1+offset)+"") +") "
						if(i<myCount-2){
							returnStr += "))";
						}
						constrObj.setLeftConstraint(returnStr);
						constrList.add(constrObj);
					}
					returnStr += "( assert " + generateANDConstraints(constrList) +" )\n";
					//add contsraint for distinct
					//returnStr+=" )) ";
					constrList = new ArrayList<ConstraintObject>();
					for(int i=0,j=0;i<myCount-2;i++,j++)
					{
						String ret ="";
						constrObj = new ConstraintObject();
						ret += getDistinctConstraint(agg, i+offset+ind+groupOffset);
						
						constrObj.setLeftConstraint(ret);
						constrList.add(constrObj);
					//"DISTINCT( "+ ConstraintGenerator.cvcMapNode(agg, (i+offset+ind+groupOffset)+"")+" , "+ ConstraintGenerator.cvcMapNode(agg, (i+offset+ind+1+groupOffset)+"")+") AND ";
					}
					returnStr += "( assert " +generateANDConstraints(constrList)+ " )\n";
					ind++;
					
					constrObj1.setLeftConstraint(returnStr);
					constrList1.add(constrObj1);
					//returnStr +=") OR ";
				}
				returnStr = generateOrConstraints(constrList1);
				//if(returnStr.contains("OR"))
				//	returnStr = returnStr.substring(0,returnStr.lastIndexOf("OR")-1);
				return returnStr;
			}
			for(int i=0,j=0;i<myCount;i++,j++){
				if(i<myCount-1){
					returnStr += "(+ ";
				}
				if(j<extras)
					returnStr += "(* "+(multiples+1)+" "+ ConstraintGenerator.cvcMapNode(agg, (i+offset+groupOffset)+"")+")";
				else
					returnStr += "(* "+(multiples)+" "+ ConstraintGenerator.cvcMapNode(agg, (i+offset+groupOffset)+"")+")";
		
				if(i<myCount-1){
					returnStr += ")";
				}
			}
			return returnStr;
			
		}
}
	
	
	/**
	 * This method takes list of constraints of type String and returns AND'ed constraint String based on the solver used.
	 * 
	 * @param cvc
	 * @param constraintList
	 * @return
	 */
	public String getNullConditionConjuncts(ArrayList<String> constraintList){
		String constraint = "";
		if(isCVC3){
			for(String con : constraintList){
				constraint += con +" AND ";
			}
			if(constraint != null && !constraint.isEmpty() && constraint.endsWith(" AND ")){
				constraint = constraint.substring(0,constraint.length()-5);
			}
		}else{
			String constr1 ="";
			for(String  con : constraintList){
				constr1 = getNullConditionForStrings(con,constr1);
				constraint = constr1;
			}
			
		}
		return constraint;
	}
	
	/**
	 * This method gets tow string constraints and returns a single OR'red constraint for foreign keys 
	 * 
	 * @param cvc
	 * @param fkConstraint
	 * @param nullConstraint
	 * @return
	 */
	public String getFKConstraint(String fkConstraint, String nullConstraint){
		
		String fkConstraints = "";
		if(isCVC3){
			if(nullConstraint != null && !nullConstraint.isEmpty()){
				fkConstraints += "ASSERT (" + fkConstraint + ") OR (" + nullConstraint + ");\n";
			}else{
				fkConstraints += "ASSERT (" + fkConstraint + ");\n";
			}
		}else{
			if(nullConstraint != null && !nullConstraint.isEmpty()){
				fkConstraints = "(assert (or "+fkConstraint + " "+nullConstraint+"))\n";
			}else{
				fkConstraints = "(assert "+ fkConstraint +" ) \n";
			}
		}
		return fkConstraints;
	}
	/**
	 * This method will return SMT constraints of the form (or (StringValue) (ISNULL_COLNAME (colName tableNameNo_colName)) )
	 * StringValue holds the previous constraint of same form thus forming nested structure as required for SMT.
	 * 	
	 * @param con
	 * @param s1
	 * @return
	 */
	public String getNullConditionForStrings(String con, String s1){
		
		String cvcStr ="";
		
		if(s1 != null && !s1.isEmpty()){
			cvcStr += " (and ";
			cvcStr += s1;
		}		
		if(con != null){
			cvcStr += con;
		}
		if(s1 != null && !s1.isEmpty()){
			cvcStr +=")  ";
		}
		return cvcStr;
	}

	
	/**
	 * This method takes in the ConstraintObject List as input and generates a constraint String AND + OR conditions.
	 * The returned string holds the AND + ORconstraints in SMT or CVC format. 
	 * 
	 * @param cvc
	 * @param constraintList
	 * @return
	 */
	public String generateAndOrConstraints(ArrayList<ConstraintObject> AndConstraintList,ArrayList<ConstraintObject> OrConstraintList){
		String constraint = "";
		if(isCVC3){
			constraint += "\nASSERT ";
			constraint += generateCVCAndConstraints(AndConstraintList);
			if(constraint != null && !constraint.isEmpty() && constraint.endsWith(" AND ")){
				constraint = constraint.substring(0,constraint.length()-5);
			}
			constraint += generateCVCOrConstraints(OrConstraintList);
			if(constraint != null && !constraint.isEmpty() && constraint.endsWith(" OR ")){
				constraint = constraint.substring(0,constraint.length()-4);
			}
			constraint +=";\n";
			
		}else{
			constraint += "\n (assert "; 
			constraint += generateSMTAndConstraints(AndConstraintList,null);
			constraint += generateSMTOrConstraints(OrConstraintList,constraint);
			constraint += " ) \n";
		}
		
		return constraint;
	}
	
	/**
	 * This method takes in the ConstraintObject List as input and generates a constraint String OR + AND all conditions.
	 * The returned string holds the OR + AND constraints in SMT or CVC format. 
	 * 
	 * @param cvc
	 * @param constraintList
	 * @return
	 */
	public String generateOrAndConstraints(ArrayList<ConstraintObject> AndConstraintList,ArrayList<ConstraintObject> OrConstraintList){
		String constraint = "";
		if(isCVC3){
			constraint += "\nASSERT ";
			constraint += generateCVCOrConstraints(OrConstraintList);
			if(constraint != null && !constraint.isEmpty() && constraint.endsWith(" OR ")){
				constraint = constraint.substring(0,constraint.length()-4);
			}
			constraint += generateCVCAndConstraints(AndConstraintList);
			if(constraint != null && !constraint.isEmpty() && constraint.endsWith(" AND ")){
				constraint = constraint.substring(0,constraint.length()-5);
			}
			constraint +=";\n";
			
		}else{
			constraint += "\n (assert "; 
			constraint += generateSMTOrConstraints(OrConstraintList,null);
			constraint += generateSMTAndConstraints(AndConstraintList,constraint);
			constraint += " ) \n";
		}
		
		return constraint;
	}
	
	
	/**
	 * This method takes in the ConstraintObject List as input and generates a constraint String AND'ing all conditions.
	 * The returned string holds the AND'ed constraints in SMT or CVC format. 
	 * 
	 * @param cvc
	 * @param constraintList
	 * @return
	 */
	public String generateANDConstraints(ArrayList<ConstraintObject> constraintList){
		String constraint = "";
		if(isCVC3){
			constraint = generateCVCAndConstraints(constraintList);
		}else{
			constraint = generateSMTAndConstraints(constraintList,null);
		}
		return constraint;
	}
	
	/**
	 * This method takes in the ConstraintObject List as input and generates an ASSERT constraint String AND'ing all conditions.
	 * The returned string holds the AND'ed constraints in SMT or CVC format. 
	 * 
	 * @param cvc
	 * @param constraintList
	 * @return
	 */
	
	public String generateANDConstraintsWithAssert(ArrayList<ConstraintObject> constraintList){
	String constraint = "";
	if(isCVC3){
		constraint = "\n ASSERT " + generateCVCAndConstraints(constraintList)+"; \n";
	}else{
		constraint = "\n (assert "+generateSMTAndConstraints(constraintList,null)+") \n";
	}
	
	return constraint;
}
	
	
	public String  revertAndToOR(String constraintString){
		String constraint = "";
		String constraint1 = "";
		
		if(isCVC3){
		/*	for(String str: constraintString.split(" AND ") )	
				if( str.length() >= 7)
					constraint1 += str.substring(7, str.length()) + " OR ";
			
			if(constraint1.length() >= 4 )
				constraint += "ASSERT " + constraint1.substring(0, constraint1.length() - 3) + ";\n";*/
			
			
			for(String str: constraintString.split(" AND ") ){
				constraint1 += str.substring(7, str.length()) + " OR ";
				constraint += "ASSERT " + constraint1.substring(0, constraint1.length() - 3) + ";\n";
			}
			
		}else{
			if(constraintString.contains("(and "))
				{	
				constraint1 += constraintString.replaceAll("\\(and", "(or");
				
				constraint += "(assert "+constraint1+") \n";
				}
			else
				constraint = constraintString;
				
		}
		return constraint;
	}
	
	public String replaceOrByOperator(String right,Node n,String left){
		String constraint = "";
		String constraint1 = "";
		String returnValue="";//ASSERT (";
		
		if(isCVC3){		
			returnValue="ASSERT (";
			if( right.contains(" OR ") ){
				String split[]=right.split(" OR ");
				for(int i=0;i<split.length;i++)
					returnValue +="("+left+""+n.getOperator()+split[i]+" OR ( ";
				returnValue=returnValue.substring(0, returnValue.lastIndexOf("OR")-1)+";\n";
				return returnValue;
			}
			
		}else{
			if(right.contains("(or")|| right.contains("( or ")){
				
				
			}			
			constraint += "(assert "+constraint1+") \n";
		}
		return constraint;
	}
	/**
	 * This method returns a constraint for null value in the query depending on solver.
	 * 
	 * @param cvc
	 * @param c
	 * @param index
	 * @param nullVal
	 * @return
	 */
	public String getAssertNullValue(Column c, String index, String nullVal){
		if(isCVC3){
			return "\nASSERT "+cvcMap(c, index)+" = "+nullVal+"; \n";
		}else{
			return "\n (assert (= "+smtMap(c, (IntExpr) ctx.mkInt(index))+" "+nullVal+"  )) \n";
		}
	}
	
	/**
	 * This method returns constraintString for primary keys with =>. It takes left and right constraint strings and adds => on them and returns new string. 
	 * 
	 * @param cvc
	 * @param impliedConObj
	 * @param isImplied
	 * @return
	 */
	public String getImpliedConstraints(ConstraintObject impliedConObj, boolean isImplied){
		String constrString = "";
		if(isCVC3){
			if(isImplied){
				constrString = "ASSERT ("+impliedConObj.getLeftConstraint() + " "+impliedConObj.getOperator()+" "+impliedConObj.getRightConstraint()+");\n";
			}else{
				constrString = "ASSERT ("+impliedConObj.getLeftConstraint() + ") "+impliedConObj.getOperator()+" TRUE; \n";
			}
		}else{
			if(isImplied){
				constrString = "\n (assert (=> \n\t"+impliedConObj.getLeftConstraint()+" "+ impliedConObj.getRightConstraint()+"\n))";
			}else{
				constrString = "\n (assert (=> \n\t"+impliedConObj.getLeftConstraint()+" true)) ";
			}
		}
		return constrString;
	}
	/**
	 * This method takes in the ConstraintObject List as input and generates a constraint String OR'ing all conditions.
	 * The returned string holds the OR'ed constraints in SMT or CVC format.
	 *  
	 * @param cvc
	 * @param constraintList
	 * @return
	 */
	public String generateOrConstraints(ArrayList<ConstraintObject> constraintList){
		String constraint = "";
		if(isCVC3){
			constraint = generateCVCOrConstraints(constraintList);
		}else{
			constraint = generateSMTOrConstraints(constraintList,null);
		}
		return constraint;
	}
	
	/**
	 * This method takes in the ConstraintObject List as input and generates an ASSERT constraint String OR'ing all conditions.
	 * The returned string holds the OR'ed constraints in SMT or CVC format.
	 *  
	 * @param cvc
	 * @param constraintList
	 * @return
	 */
	public String generateOrConstraintsWithAssert(ArrayList<ConstraintObject> constraintList){
		String constraint = "";
		if(isCVC3){
			if(generateCVCOrConstraints(constraintList) != null && !generateCVCOrConstraints(constraintList).isEmpty()){
				constraint = "\n ASSERT"+generateCVCOrConstraints(constraintList)+"; \n";
			}
		}else{
			if(generateSMTOrConstraints(constraintList,null) != null && !generateSMTOrConstraints(constraintList,null).isEmpty()){
				/*if(isTempJoin) {
					String constr,declare="";
					int st_index=0,end_index=0;
					constr = generateSMTOrConstraints(constraintList,null);
					while(constr.indexOf("(declare-datatypes ()") != -1) {
					st_index = constr.indexOf("(declare-datatypes ()");
					end_index = constr.indexOf("_TupleType))")+12;
					if(!declare.contains(constr.substring(st_index, end_index)))
						declare += constr.substring(st_index, end_index) + " \n";
					constr = constr.substring(0, st_index)+constr.substring(end_index);
					}
					
					constraint =  declare + "\n (assert "+constr+") \n";
				} */

				 if(Configuration.getProperty("tempJoins").equalsIgnoreCase("true")){
					 isTempJoin = true;
				 }else {
					 isTempJoin = false;
				 }
				 
				if(isTempJoin) {
					String constr,declare="";
					int st_index=0,end_index=0;
					boolean inside = false;
					constr = generateSMTOrConstraints(constraintList,null);
					while(constr.indexOf("(declare-datatypes ()") != -1) {
						inside = true;
					st_index = constr.indexOf("(declare-datatypes ()");
					end_index = constr.indexOf("_TupleType))")+12;
					if(!declare.contains(constr.substring(st_index, end_index)))
						declare += constr.substring(st_index, end_index) + " \n";
					constr = constr.substring(0, st_index)+constr.substring(end_index);
					}
					if(constr.isEmpty())
						constraint = "";
					else
						constraint =  declare + "\n (assert "+constr+") \n";
				}
				else 
					constraint =  "\n (assert "+generateSMTOrConstraints(constraintList,null)+") \n"; 
			}
		}
		
		return constraint;
	}
	
	
	/**
	 * This method takes in the ConstraintObject List as input and generates a constraint String with NOT conditions.
	 * The returned string holds the NOT constraints in SMT or CVC format.
	 *  
	 * @param cvc
	 * @param constraintList
	 * @return
	
	public String generateNotConstraints(GenerateCVC1 cvc, ArrayList<ConstraintObject> constraintList){
		String constraint = "";
		if(cvc.getConstraintSolver().equalsIgnoreCase("cvc3")){
			constraint = generateCVCNotConstraints(constraintList);
		}else{
			constraint = generateSMTNotConstraints(cvc,constraintList);
		}
		return constraint;
	} */
	
	
/**
 * This method returns AND'ed constraints with Assert and ; as required for CVC solver. 
 * @param constraintList
 * @return
 */
public String generateCVCAndConstraints(ArrayList<ConstraintObject> constraintList){
	
	String constraint = "";
	//constraint += "(";
	for(ConstraintObject con : constraintList){
		constraint += "("+con.getLeftConstraint()+" "+con.getOperator()+" "+con.getRightConstraint()+") AND " ;
	}
	if(constraint != null && !constraint.isEmpty() && constraint.endsWith(" AND ")){
		constraint = constraint.substring(0,constraint.length()-5);
	}
	//constraint += ")";
	if(constraint == null || constraint ==""){
		return "";
	}
	return constraint;
}

/**
 * This method will return SMT constraints of the form (or (StringValue) 
 * (and (operator (colname tableNameNo_colName)(colName tableNameNo_colName)) (operator (colname tableNameNo_colName)(colName tableNameNo_colName)) )
 * StringValue holds the previous constraint of same form thus forming nested structure as required for SMT.
 * 
 */
/*public String generateSMTAndConstraints(ArrayList<ConstraintObject> constraintList){
	
	String constraintStr = "";
	String constr1 ="";
	for(ConstraintObject con : constraintList){
		constr1 =  getSMTAndConstraint(con,constr1);
		constraintStr = constr1;
	}
	return constraintStr;
}*/

public String generateSMTAndConstraints(ArrayList<ConstraintObject> constraintList,String constraintStr){
	String constr1 ="";
	for(ConstraintObject con : constraintList){
		constr1 =  getSMTAndConstraint(con,constr1,constraintList);
		constraintStr = constr1+"\t";
	
	}
	if(constraintStr == null || constraintStr == ""){
		return "";
	}
	else if(constraintList!= null) {
		constraintStr = " (and " + constraintStr + ")";
	}
	return constraintStr;
}


/**
 * This method will return SMT constraints of the form (or (StringValue) 
 * (or (operator (colname tableNameNo_colName)(colName tableNameNo_colName)) (operator (colname tableNameNo_colName)(colName tableNameNo_colName)) )
 * StringValue holds the previous constraint of same form thus forming nested structure as required for SMT.
 * 
 */
/*public String generateSMTOrConstraints(ArrayList<ConstraintObject> constraintList){
	
	String constraintStr = "";
	for(ConstraintObject con : constraintList){
		constraintStr += getSMTOrConstraint(con,constraintStr);
	}
	return constraintStr;
}*/

public String generateSMTOrConstraints(ArrayList<ConstraintObject> constraintList,String constraintStr){

	String constr1 ="";
	for(ConstraintObject con : constraintList){
		//constraintStr += getSMTOrConstraint(con,constraintStr);
		constr1 = getSMTOrConstraint(con, constr1,constraintList);
	    if(!constr1.isEmpty())
			constraintStr = constr1+"\n\t\t";
	}
	
	
	if(constraintStr == null || constraintStr == ""){
		constraintStr = "";
	}
	else if(constraintList!= null && constraintList.size() > 1) {
		constraintStr = " (or " + constraintStr + ")";
	}
	return constraintStr;
}
/**
 * This method will return SMT constraints of the form (or (StringValue) (distinct (colname tableNameNo_colName)(colName tableNameNo_colName)) )
 * StringValue holds the previous constraint of same form thus forming nested structure as required for SMT.
 * 	
 * @param con
 * @param s1
 * @return
 */
public String getSMTAndConstraint(ConstraintObject con, String s1,ArrayList<ConstraintObject> conList){
	
	String cvcStr ="";
	
	if(s1 != null && !s1.isEmpty() || (s1 != null && !s1.isEmpty() && conList!= null && conList.size() > 1)){
		//cvcStr += " (and ";
		cvcStr += s1;
	}	
	if(con != null){
		
		String Rightconstr = "";
		String Leftconstr = "";
		///
		if((con.getRightConstraint() != null && !con.getRightConstraint().isEmpty())) {
			Rightconstr = con.getRightConstraint().trim();
			
			int st_index = Rightconstr.indexOf("(assert");
			while(st_index != -1) {
				//Rightconstr.replaceFirst("(assert", " ");
				Leftconstr = Rightconstr.substring(0, st_index)+Leftconstr.substring(st_index+7);
				int count = 1;
				int end_index=st_index+1;
				for(; end_index < Rightconstr.length(); end_index++) {
					if(Rightconstr.charAt(end_index) == '(')
						count++;
					else if(Rightconstr.charAt(end_index) == ')')
						count--;
					if(count == 0) {
						Rightconstr = Rightconstr.substring(0, end_index)+Rightconstr.substring(end_index+1);
						st_index = Rightconstr.indexOf("(assert");
						break;
					}	
						
				}
				if(end_index == Rightconstr.length())
					break;
			}	
				
		}
		
		if((con.getLeftConstraint() != null && !con.getLeftConstraint().isEmpty())) {
			Leftconstr = con.getLeftConstraint().trim();
			int st_index = Leftconstr.indexOf("(assert");
			while(st_index != -1) {
				Leftconstr = Leftconstr.substring(0, st_index)+Leftconstr.substring(st_index+7);
				//Leftconstr.replaceFirst(" assert ", " ");
				int count = 1;
				int end_index=st_index+1;
				for(; end_index < Leftconstr.length(); end_index++) {
					if(Leftconstr.charAt(end_index) == '(')
						count++;
					else if(Leftconstr.charAt(end_index) == ')')
						count--;
					if(count == 0) {
						Leftconstr = Leftconstr.substring(0, end_index)+Leftconstr.substring(end_index+1);
						st_index = Leftconstr.indexOf("(assert");
						break;
					}	
						
				}
				if(end_index == Leftconstr.length())
					break;
			}	
				
		}
		
		//If else statements are added to get the matching brackets based on the operator and constraint
		
		if( (con.getOperator()!= null && ! con.getOperator().isEmpty()) && (con.getRightConstraint() != null && !con.getRightConstraint().isEmpty()) ){
			
			cvcStr += "";
			if((con.getOperator() != null && ! (con.getOperator().isEmpty()))){
				cvcStr += "(";
				if(con.getOperator().equals("/=")){
					cvcStr  += "not (= ";
				}else{
					cvcStr += con.getOperator()+" ";
				}
			}else{
				cvcStr+=" ";
			}
			
			
			if(con.getLeftConstraint() != null && !con.getLeftConstraint().isEmpty()){
				if(Leftconstr.trim().startsWith("(")){
					cvcStr+= Leftconstr+" " ;
				}else{
					cvcStr+= " ("+Leftconstr+") ";
				}
			}else{
				cvcStr += " ";
			}
			
			if(con.getRightConstraint() !=null && ! con.getRightConstraint().isEmpty() ){
				if(isIntOrReal(con.getRightConstraint()) || Rightconstr.trim().startsWith("(")){
					cvcStr +=Rightconstr;
				}else{
					cvcStr +="("+Rightconstr+")";
				}
			}else{
				cvcStr += " ";
			}
			
			if((con.getOperator() != null && ! (con.getOperator().isEmpty()))){
				if(con.getOperator().equals("/=")){
					cvcStr += "))";
				}else{
					cvcStr += ")";
				}
			}else{
				cvcStr += " ";
			}
			
			//cvcStr += ((con.getOperator() != null && ! (con.getOperator().isEmpty())) 
			//? "("+(con.getOperator().equals("/=")? "not (= ": con.getOperator()+" ") : " ")
			
				//	+ ((con.getLeftConstraint() != null && !con.getLeftConstraint().isEmpty())
			//? (con.getLeftConstraint().startsWith("(")? con.getLeftConstraint()+" " : " ("+con.getLeftConstraint()+") "):" ")
				
			
			//+(con.getRightConstraint() !=null && ! con.getRightConstraint().isEmpty() 
			//? ((isIntOrReal(con.getRightConstraint()) 
			//|| con.getRightConstraint().startsWith("(")) ? con.getRightConstraint() : "("+con.getRightConstraint()+")") :" ")
			
					//+ ((con.getOperator() != null && ! (con.getOperator().isEmpty())) ? (con.getOperator().equals("/=")? "))": ")"): " ");
			
		}
		else{
			if(!Leftconstr.isEmpty())
			    cvcStr += Leftconstr.trim().startsWith("(")? " "+Leftconstr+" " : " ("+Leftconstr+") ";
		}
	}
	if(s1 != null && !s1.isEmpty() || (s1 != null && !s1.isEmpty() && conList!= null && conList.size() > 1)){
		//cvcStr +=")  ";
	}
	return cvcStr;
}

/**
 * This method will return SMT constraints of the form (or (StringValue) (distinct (colname tableNameNo_colName)(colName tableNameNo_colName)) )
 * StringValue holds the previous constraint of same form thus forming nested structure as required for SMT.
 * 	
 * @param con
 * @param s1
 * @return
 */
public String getSMTOrConstraint(ConstraintObject con, String s1,ArrayList<ConstraintObject> conList){
	 
	String cvcStr ="";
	
	if(s1 != null && !s1.isEmpty() || (s1 != null && !s1.isEmpty() && conList!= null && conList.size() > 1)){
		//cvcStr += " (or ";
		cvcStr += s1;
	}
	
	if(con != null){
		
		String Rightconstr = "";
		String Leftconstr = "";
		/// Code to remove nested assert string 
		if((con.getRightConstraint() != null && !con.getRightConstraint().isEmpty())) {
			Rightconstr = con.getRightConstraint().trim();
			
			int st_index = Rightconstr.indexOf("(assert");
			while(st_index != -1) {
				//Rightconstr.replaceFirst("(assert", " ");
				Leftconstr = Rightconstr.substring(0, st_index)+Leftconstr.substring(st_index+7);
				int count = 1;
				int end_index=st_index+1;
				for(; end_index < Rightconstr.length(); end_index++) {
					if(Rightconstr.charAt(end_index) == '(')
						count++;
					else if(Rightconstr.charAt(end_index) == ')')
						count--;
					if(count == 0) {
						Rightconstr = Rightconstr.substring(0, end_index)+Rightconstr.substring(end_index+1);
						st_index = Rightconstr.indexOf("(assert");
						break;
					}	
						
				}
				if(end_index == Rightconstr.length())
					break;
			}	
				
		}
		
		if((con.getLeftConstraint() != null && !con.getLeftConstraint().isEmpty())) {
			Leftconstr = con.getLeftConstraint().trim();
			int st_index = Leftconstr.indexOf("(assert");
			while(st_index != -1) {
				Leftconstr = Leftconstr.substring(0, st_index)+Leftconstr.substring(st_index+7);
				//Leftconstr.replaceFirst(" assert ", " ");
				int count = 1;
				int end_index=st_index+1;
				for(; end_index < Leftconstr.length(); end_index++) {
					if(Leftconstr.charAt(end_index) == '(')
						count++;
					else if(Leftconstr.charAt(end_index) == ')')
						count--;
					if(count == 0) {
						Leftconstr = Leftconstr.substring(0, end_index)+Leftconstr.substring(end_index+1);
						st_index = Leftconstr.indexOf("(assert");
						break;
					}	
						
				}
				if(end_index == Leftconstr.length())
					break;
			}	
				
		}
		///

		if( (con.getOperator()!= null && ! con.getOperator().isEmpty()) && (con.getRightConstraint() != null 
				&& !con.getRightConstraint().isEmpty()) ){
			
			
			cvcStr += "";
			//If else statements are added to get the matching brackets based on the operator and constraint
			
			if(con.getOperator() != null && ! (con.getOperator().isEmpty())){
				cvcStr += "(";
				if(con.getOperator().equals("/=")){
					cvcStr +=  "not (= ";
				}else{
					cvcStr +=  con.getOperator()+" ";
				}
			}else{
				cvcStr +=" ";
			}
			
			if(con.getLeftConstraint() != null && !con.getLeftConstraint().isEmpty()){
				if(Leftconstr.trim().startsWith("(")){
					cvcStr += Leftconstr+" ";
				}
				else{
					cvcStr += " ("+Leftconstr+") ";
				}
			}else{
				cvcStr += " ";
			}
			
			if(con.getRightConstraint() !=null && ! con.getRightConstraint().isEmpty()){
				if((isIntOrReal(con.getRightConstraint()) || Rightconstr.trim().startsWith("("))){
					cvcStr += Rightconstr ;
				}else{
					cvcStr += "("+Rightconstr+")";
				}
			}else{
				cvcStr += " ";
			}
			
			if(con.getOperator() != null && ! (con.getOperator().isEmpty()) ){
				if(con.getOperator().equals("/=")){
					cvcStr += "))";
				}else{
					cvcStr +=  ")";
				}
			}else{
				cvcStr += " ";
			}
			
			//((con.getOperator() != null && ! (con.getOperator().isEmpty())) ? 
			//"("+(con.getOperator().equals("/=")? "not (= ": con.getOperator()+" ") : " ")
			
				//	+ ((con.getLeftConstraint() != null && !con.getLeftConstraint().isEmpty())
			//? (con.getLeftConstraint().trim().startsWith("(")? con.getLeftConstraint()+" " : " ("+con.getLeftConstraint()+") "):" ")
			
				//+(con.getRightConstraint() !=null && ! con.getRightConstraint().isEmpty() 
			//? ((isIntOrReal(con.getRightConstraint()) || con.getRightConstraint().trim().startsWith("(")) 
			//? con.getRightConstraint() : "("+con.getRightConstraint()+")") :" ")
			
			
				//+ ((con.getOperator() != null && ! (con.getOperator().isEmpty())) ? (con.getOperator().equals("/=")? "))": ")"): " ");
			
			
		}
		else{
			if(!Leftconstr.isEmpty())
				cvcStr += Leftconstr.trim().startsWith("(")? (" "+Leftconstr+" ") : " ("+con.getLeftConstraint()+") ";
		}
	}
	if(s1 != null && !s1.isEmpty() || (s1 != null && !s1.isEmpty() && conList!= null && conList.size() > 1)){
	//	cvcStr +=")  ";
	}

	//cvcStr = "(or "+cvcStr+")";
	return cvcStr;
}


/**
 * This method checks if the parameter passed is Integer or real.
 * 
 * @param constraint
 * @return
 */
public boolean isIntOrReal(String constraint){
	if (constraint.length() > 0 && constraint.matches("[0-9]+") ) {
		return true;
	}else if(constraint.length() > 0 && constraint.matches("[0-9]+.[0-9]+")){
		return true;
	}else{
		return false;
	}
}

/**
 * This method returns OR'ed constraints with Assert and ; as required for CVC3 solver. 
 * @param constraintList
 * @return
 */
public String generateCVCOrConstraints(ArrayList<ConstraintObject> constraintList){
	
	String constraint = "";
	
	for(ConstraintObject con : constraintList){
		constraint += "("+con.getLeftConstraint()+" "+con.getOperator()+" "+con.getRightConstraint()+") OR " ;
	}
	if(constraint != null && !constraint.isEmpty() && constraint.endsWith(" OR ")){
		constraint = constraint.substring(0,constraint.length()-4);
	}
	
	return constraint;
}

	/**
	 * Used to get CVC3 constraint for this column for the given tuple position
	 * @param col
	 * @param index
	 * @return
	 */
	public static String cvcMap(Column col, String index){
		Table table = col.getTable();
		String tableName = col.getTableName();
		String columnName = col.getColumnName();
		int pos = table.getColumnIndex(columnName);
		index=index.trim();
		return "O_"+tableName+"["+index+"]."+pos;	
	}

	/**
	 * Used to get CVC3 constraint for this column
	 * @param col
	 * @param n
	 * @return
	 */
	public static String cvcMap(Column col, Node n){
		Table table = col.getTable();
		String tableName = col.getTableName();
		String aliasName = col.getAliasName();
		String columnName = col.getColumnName();
		String tableNo = n.getTableNameNo();
		int index = Integer.parseInt(tableNo.substring(tableNo.length()-1));
		int pos = table.getColumnIndex(columnName);
		return "O_"+tableName+"["+index+"]."+pos;	
	}


	/**
	 * Used to get CVC3 constraint for the given node for the given tuple position
	 * @param n
	 * @param index
	 * @return
	 */
	public static String cvcMapNode(Node n, String index){
		if(n.getType().equalsIgnoreCase(Node.getValType())){
			return n.getStrConst();
		}
		else if(n.getType().equalsIgnoreCase(Node.getColRefType())){
			return cvcMap(n.getColumn(), index);
		}
		else if(n.getType().toString().equalsIgnoreCase("i")){
			return "i";
		}
		else if(n.getType().equalsIgnoreCase(Node.getBroNodeType()) || n.getType().equalsIgnoreCase(Node.getBaoNodeType())){
			return "("+cvcMapNode(n.getLeft(), index) + n.getOperator() + cvcMapNode(n.getRight(), index)+")";
		}
		else return "";
	}

	
	/**
	 * Used to get SMT LIB constraint for this column for the given tuple position
	 * 
	 * @param col
	 * @param fIndex
	 * @return
	 */
	public static Expr smtMap(Column col, IntExpr fIndex) {
		Table table = col.getTable();
		String tableName = col.getTableName();
		String columnName = col.getColumnName();
		int pos = table.getColumnIndex(columnName);
		
		// relations are represented as ArrayExpr's
		ArrayExpr relation = (ArrayExpr) ctxConsts.get("O_"+tableName);
		
		// tuples are represented as DatatypeExpr's
		DatatypeExpr tuple = (DatatypeExpr) ctx.mkSelect(relation, fIndex);
	    
		DatatypeSort tupSort = (DatatypeSort) tuple.getSort();
	    FuncDecl[] tupAccessors = tupSort.getAccessors()[0];  // tuples declared will have only one constructor, hence[0].
	    FuncDecl colAccessor = tupAccessors[pos];
	    Expr colValue = colAccessor.apply(tuple);

	    return colValue;
	}


	/**
	 * Used to get SMT LIB constraint for this column
	 * @param col
	 * @param n
	 * @return
	 */
	public static String smtMap(Column col, Node n){
		Table table = col.getTable();
		String tableName = col.getTableName();
		String columnName = col.getColumnName();
		String tableNo = n.getTableNameNo();
		
		int index = Integer.parseInt(tableNo.substring(tableNo.length()-1));
		int pos = table.getColumnIndex(columnName);
		
		String smtCond = "";
		//String colName =tableName+"_"+columnName;
		String colName = tableName+"_"+columnName+pos;
		smtCond = "("+colName+" "+"(select O_"+tableName+" "+index +") )";
		return smtCond;
	}
	
	/**
	 * This method gets the SMT / CVC constraint String mapping the table name, column name, index, position.
	 * 
	 * @param cvc
	 * @param col
	 * @param n
	 * @return
	 */
	public static String getSolverMapping(Column col,Node n){
		if(isCVC3){
			return cvcMap(col, n);
		}else{
			return smtMap(col, n);
		}
	}

	/**
	 * This method gets the SMT/CVC constraint string mapping for table name, column name, index, position.
	 * 
	 * @param cvc
	 * @param col
	 * @param index
	 * @return
	 */
	public static String getSolverMapping(Column col, String index){
		if(isCVC3){
			return cvcMap(col, index);
		}else{
			return smtMap(col, (IntExpr) ctx.mkInt(Integer.parseInt(index))).toString();
		}
	}
	/**
	 * Used to get SMT constraint for the given node for the given tuple position
	 * @param n
	 * @param index
	 * @return
	 */
	public static String smtMapNode(Node n, String index){
		if(n.getType().equalsIgnoreCase(Node.getValType())){
			return n.getStrConst();
		}
		else if(n.getType().equalsIgnoreCase(Node.getColRefType())){
			return smtMap(n.getColumn(), (IntExpr) ctx.mkInt(Integer.parseInt(index))).toString();
		}
		else if(n.getType().toString().equalsIgnoreCase("i")){
			return "i";
		}
		else if(n.getType().equalsIgnoreCase(Node.getBroNodeType()) || n.getType().equalsIgnoreCase(Node.getBaoNodeType())){
			
			return "("+ (n.getOperator().equals("/=")? "not (= ": n.getOperator())+" "+smtMapNode(n.getLeft(), index) +" " + smtMapNode(n.getRight(), index)+(n.getOperator().equals("/=")? ")": "")+")";
		}
		else return "";
	}

	/**
	 * This method returns the constraint for the node and index position based on solver
	 * 
	 * @param cvc
	 * @param n
	 * @param index
	 * @return
	 */
	public static String getMapNode(Node n, String index){
		if(isCVC3){
			if(cvcMapNode(n, index) != null && !cvcMapNode(n, index).isEmpty()){
				return cvcMapNode(n, index);
			}else
				return "";
		}else{
			if(smtMapNode(n, index) != null && !smtMapNode(n, index).isEmpty()){
				return smtMapNode(n, index);
			}else{
				return "";
			}
		}
	}
	//Specific methods for each datatype in solver file
	/**
	 * This method returns the header value for the Solver depending on whether solver is CVC / SMT
	 * If solver is CVC - it returns nothing
	 * If solver is SMT - it returns all options required for producing required output.
	 * 
	 * @param cvc
	 * @return
	 */
	public String getHeader(){
		String header = "";
		if(isCVC3){
			return header;
		}
		else {
			Params p = ctx.mkParams();
			p.add("produce-models", true); // other options invalid in z3 API
			p.add("smt.macro_finder", true); // check whether this is the right option name
			//p.add("model_compress", false); <-- Gives error for some reason, however this param is set successfully in the .smt file
			solver.setParameters(p); // NOTE: these should be the settings for all solvers

			//header = "(set-logic ALL_SUPPORTED)";
			header += "(set-option :produce-models true)\n (set-option :smt.macro_finder true) \n";
			
			BoolExpr assertionIntNull = ctx.mkEq(ConstraintGenerator.intNull, ctx.mkInt(-99996));
			BoolExpr assertionRealNull = ctx.mkEq(ConstraintGenerator.realNull, ctx.mkReal("-99996.0"));

			Solver dummySolver = ctx.mkSolver();
			dummySolver.add(assertionIntNull);
			dummySolver.add(assertionRealNull);
			
			
			// Setting the right order for macro detection
			String[] tempArr = dummySolver.toString().split("\n");
			String declaration = "\n" + tempArr[1];
			tempArr[1] = tempArr[2];
			tempArr[2] = declaration;
			
			
			header += "\n" + String.join("\n", tempArr);

		}
		return header +"\n\n";
	}
	
	/**
	 * This method returns the Constraint String for defining and declaring integer type data
	 * 
	 * @param cvc
	 * @param col
	 * @param minVal
	 * @param maxVal
	 * @return
	 */
//	public String getIntegerDatatypes(Column col, int minVal, int maxVal){
//		
//		String constraint ="";
//		if (isCVC3) {
//			constraint = "\n"+col+" : TYPE = SUBTYPE (LAMBDA (x: INT) : (x > "+(minVal-4)+" AND x < "+(maxVal+4)+") OR (x > -100000 AND x < -99995));\n";
//		}
//		else {
//			String funcName = "get"+col;
//			FuncDecl getCol = ctx.mkFuncDecl(funcName, ctx.mkIntSort(), ctx.mkBoolSort());
//			ctxFuncDecls.put(funcName, getCol);
//			constraint = getCol.toString() + "\n";
//			IntExpr[] iColArray = new IntExpr[]{ctx.mkIntConst("i_"+col)};
//			Expr getColCall = getCol.apply(iColArray);
//			//Expr condition = ctx.mkAnd(ctx.mkGt(iColArray[0], ctx.mkInt((minVal-4)>0?(minVal-4):0)), ctx.mkLt(iColArray[0], ctx.mkInt(maxVal+4)));
//			//Expr condition = ctx.mkAnd(ctx.mkGt(iColArray[0], ctx.mkInt((minVal-4)>0?(minVal-4):0)), ctx.mkLt(iColArray[0], ctx.mkInt(maxVal == 2147483647 ? maxVal : maxVal+4)));
//			Expr condition = ctx.mkAnd(ctx.mkGt(iColArray[0], ctx.mkInt((minVal-4)>0?(minVal-4):0)), ctx.mkLt(iColArray[0], ctx.mkInt("9223372036854775807")));
//			
//			Expr body = ctx.mkEq(getColCall, condition);
//			Expr funcQuantifier = ctx.mkForall(iColArray, body, 1, null, null, null, null);
//			constraint += "(assert " + funcQuantifier.toString() + ")";
//		}
//		return constraint +"\n\n";
//	}
	/**
	 * This method returns the Constraint String for defining and declaring int, long and BigInt type data
	 * 
	 * @param cvc
	 * @param col
	 * @param minVal
	 * @param maxVal
	 * @return
	 */
	public String getIntegerDatatypes(Column col, long minVal, long maxVal){
		
		String constraint ="";
		if (isCVC3) {
			constraint = "\n"+col+" : TYPE = SUBTYPE (LAMBDA (x: INT) : (x > "+(minVal-4)+" AND x < "+(maxVal+4)+") OR (x > -100000 AND x < -99995));\n";
		}
		else {
			String funcName = "check"+col;
			FuncDecl getCol = ctx.mkFuncDecl(funcName, ctx.mkIntSort(), ctx.mkBoolSort());
			ctxFuncDecls.put(funcName, getCol);
			constraint = getCol.toString() + "\n";
			IntExpr[] iColArray = new IntExpr[]{ctx.mkIntConst("i_"+col)};
			Expr getColCall = getCol.apply(iColArray);
			
			minVal = minVal > Long.MIN_VALUE ? minVal-1 : minVal;  // TEST CODE: Pooja
			maxVal = maxVal < Long.MAX_VALUE ? maxVal+1 : maxVal;
			
			//Expr condition = ctx.mkAnd(ctx.mkGt(iColArray[0], ctx.mkInt(minVal)), ctx.mkLt(iColArray[0], ctx.mkInt(maxVal)));
			
			//Testing.....
			Expr condition = ctx.mkOr( ctx.mkAnd(ctx.mkGt(iColArray[0], ctx.mkInt(minVal)), ctx.mkLt(iColArray[0], ctx.mkInt(maxVal))),
					                     ctx.mkEq(iColArray[0],ctx.mkInt(-99996)) );
			
			Expr body = ctx.mkEq(getColCall, condition);
			Expr funcQuantifier = ctx.mkForall(iColArray, body, 1, null, null, null, null);
			constraint += "(assert " + funcQuantifier.toString() + ")";
		}
		return constraint +"\n\n";
	}
	
	/**
	 * This method returns a constraint string that holds the allowed null values for the integer data defined
	 * 
	 * @param cvc
	 * @param col
	 * @return
	 */
	public String getIntegerNullDataValues(Column col){
		String constraint ="";
		String isNullMembers = "";
		if(isCVC3){
			
			constraint = "ISNULL_" + col +" : "+ col + " -> BOOLEAN;\n";
			for(int k=-99996;k>=-99999;k--){
				isNullMembers += "ASSERT ISNULL_"+col+"("+k+");\n";
			}
			constraint += isNullMembers;
		}else{
			HashMap<Expr, Integer> nullValuesInt = new HashMap<Expr, Integer>();
			/*Removing NUll enumerations*/
			/*for(int k=-99996;k>=-99999;k--){
				nullValuesInt.put(k+"",0);
			}*/
			nullValuesInt.put(ConstraintGenerator.intNull, 0);
			constraint += defineIsNull(nullValuesInt, col);			
		}
		return constraint +"\n\n";
	}
	
	/**
	 * This method returns the Constraint String for defining and declaring Real type data
	 * 
	 * @param cvc
	 * @param col
	 * @param minVal
	 * @param maxVal
	 * @return
	 */
	public String getRealDatatypes(Column col, double minVal, double maxVal){
		String constraint ="";
		if(isCVC3){
			String maxStr=util.Utilities.covertDecimalToFraction(maxVal+"");
			String minStr=util.Utilities.covertDecimalToFraction(minVal+"");
			constraint = "\n"+col+" : TYPE = SUBTYPE (LAMBDA (x: REAL) : (x >= "+(minStr)+" AND x <= "+(maxStr)+") OR (x > -100000 AND x < -99995));\n";			
		}
		else{
			String funcName = "check"+col;
			FuncDecl getCol = ctx.mkFuncDecl(funcName, ctx.mkRealSort(), ctx.mkBoolSort());
			ctxFuncDecls.put(funcName, getCol);
			constraint = getCol.toString() + "\n";
			RealExpr[] rColArray = new RealExpr[]{ctx.mkRealConst("r_"+col)};
			Expr getColCall = getCol.apply(rColArray);
//			Expr condition = ctx.mkAnd(ctx.mkGe(rColArray[0], ctx.mkReal(String.valueOf(minVal))), ctx.mkLe(rColArray[0], ctx.mkReal(String.valueOf(maxVal))));
			Expr condition =ctx.mkOr( ctx.mkAnd(ctx.mkGe(rColArray[0], ctx.mkReal(String.valueOf(minVal))), ctx.mkLe(rColArray[0], ctx.mkReal(String.valueOf(maxVal)))),
					 ctx.mkEq(rColArray[0],ctx.mkReal(-99996)));
			
			Expr body = ctx.mkEq(getColCall, condition);
			Expr funcQuantifier = ctx.mkForall(rColArray, body, 1, null, null, null, null);

			constraint += "(assert " + funcQuantifier.toString() + ")";
		}
		return constraint +"\n\n";
	}
	
	/**
	 * This method returns a constraint string that holds the allowed null values for the Real data defined
	 * 
	 * @param cvc
	 * @param col
	 * @return
	 */
	public String getRealNullDataValues(Column col){
		String constraint ="";
		String isNullMembers = "";
		if(isCVC3){
			
			constraint += "ISNULL_" + col +" : "+ col + " -> BOOLEAN;\n";
			for(int k=-99996;k>=-99999;k--){
				isNullMembers += "ASSERT ISNULL_"+col+"("+k+");\n";
				
			}
			constraint += isNullMembers;			
		} else {
			HashMap<Expr, Integer> nullValuesReal = new HashMap<Expr, Integer>();
			/*Removing NUll enumerations*/
			/*for(int k=-99996;k>=-99999;k--){
				nullValuesInt.put(k+"",0);
			}
			*/
			nullValuesReal.put(ConstraintGenerator.realNull, 0);
			constraint +=defineIsNull(nullValuesReal, col);			
		}
		return constraint+"\n\n";
	}
	
	/**
	 * This method returns the <b>SMT Constraint</b> that holds the allowed Null values for given column
	 * 
	 * @param colValueMap
	 * @param col
	 * @return
	 */

	public static String defineIsNull(HashMap<Expr, Integer> colValueMap, Column col) {
		
		String IsNullValueString = "";
		Expr colNull = null;
		if (col.getCvcDatatype() != null && col.getCvcDatatype().equalsIgnoreCase("Int")) {
			colNull = ctx.mkConst(col.getColumnName().toLowerCase(), ctx.getIntSort());
		} else if (col.getCvcDatatype() != null && col.getCvcDatatype().equalsIgnoreCase("Real")) {
			colNull = ctx.mkConst(col.getColumnName().toLowerCase(), ctx.getRealSort());
		} else {
			colNull = ctx.mkConst(col.getColumnName().toLowerCase(), ctxSorts.get(col.getColumnName()));
		}

		String funcName = "ISNULL_" + col;
		FuncDecl isNullCol = ctx.mkFuncDecl(funcName, colNull.getSort(), ctx.mkBoolSort());
		ctxFuncDecls.put(funcName, isNullCol);
		IsNullValueString += isNullCol.toString() + "\n";
		
		Expr[] nullColArray = new Expr[]{colNull};
		Expr isNullColCall = isNullCol.apply(nullColArray);
		
		BoolExpr[] nullEqualityConds = colValueMap.keySet().stream().map(
				colValue -> ctx.mkEq(nullColArray[0], colValue)).toArray(
						size -> new BoolExpr[size]);
		BoolExpr nullValsOrCond = ctx.mkOr(nullEqualityConds);

		Expr body = ctx.mkEq(isNullColCall, nullValsOrCond);
		Expr funcQuantifier = ctx.mkForall(nullColArray, body, 1, null, null, null, null);
		IsNullValueString += "(assert " + funcQuantifier.toString() + ")\n";

		return IsNullValueString +"\n\n";
	}

	/**
	 * This method returns the <b>SMT Constraint</b> that holds the allowed Not-Null values for given column
	 * @param colValueMap
	 * @param col
	 * @return
	 */
	public static String defineNotIsNull(HashMap<String, Integer> colValueMap, Column col){
		String NotIsNullValueString = "";

		if (col.getCvcDatatype() != null && col.getCvcDatatype().equalsIgnoreCase("Int")) {
			NotIsNullValueString +="\n(declare-const notnull_"+col+" i_"+col+")"; //declare constant of form   (declare-const null_column_name ColumnName)
			NotIsNullValueString += "\n(define-fun NOTISNULL_"+col+" ((notnull_"+col+" i_"+col+")) Bool ";
		}else if(col.getCvcDatatype() != null && col.getCvcDatatype().equalsIgnoreCase("Real")){
			NotIsNullValueString +="\n(declare-const notnull_"+col+" r_"+col+")"; //declare constant of form   (declare-const null_column_name ColumnName)
			NotIsNullValueString += "\n(define-fun NOTISNULL_"+col+" ((notnull_"+col+" r_"+col+")) Bool ";
		}else{
			NotIsNullValueString +="\n(declare-const notnull_"+col+" "+col+")"; //declare constant of form   (declare-const null_column_name ColumnName)
			NotIsNullValueString += "\n(define-fun NOTISNULL_"+col+" ((notnull_"+col+" "+col+")) Bool ";
		}

		NotIsNullValueString += getOrForNullDataTypes("notnull_"+col, colValueMap.keySet(), "");;//Get OR of all non-null columns
		
		NotIsNullValueString += ")";
		return NotIsNullValueString +"\n";
	}

	/**
	 * This method returns the <b>SMT Constraint</b> that holds the allowed Null values for a column concatenated with OR
	 * @param colconst
	 * @param columnValues
	 * @param tempString
	 * @return
	 */
	
	public static String getOrForNullDataTypes(String colconst, Set<String> columnValues, String tempString) {
		
		
		Iterator it = columnValues.iterator();
		int index = 0;
		while(it.hasNext()){
			index++;
			tempString = getIsNullOrString(colconst,((String)it.next()),tempString);
		}
		return tempString;
	}

	/**
	 * This method returns the <b>SMT Constraint</b> that holds the allowed Not-Null values for a column concatenated with OR
	 * @param colconst
	 * @param colValue
	 * @param tempstring
	 * @return
	 */
	public static String getIsNullOrString(String colconst,String colValue,String tempstring){
		
		String tStr = "";
		
		if(tempstring != null && !tempstring.isEmpty()){
			tStr = "(or "+tempstring;	
		}
		if(colValue != null && colValue.startsWith("-")){
			tStr +=" (= "+colconst+" -"+colValue.substring(1,colValue.length())+")";
		}else{
			tStr +=" (= "+colconst+" "+colValue+")";
		}
		
		if(tempstring != null && !tempstring.isEmpty()){
			tStr += ")";	
		}
		
		return tStr;
	}
	/**
	 * This method returns the Constraint String for defining and declaring String / VARCHAR type data
	 * @param cvc
	 * @param columnValue
	 * @param col
	 * @param unique
	 * @return
	 * @throws Exception
	 */
	public String getStringDataTypes(Vector<String> columnValue,Column col,boolean unique) throws Exception{
		String constraint = "";
		String colValue = "";
		HashSet<String> uniqueValues = new HashSet<String>();
		String isNullMembers = "";
		checkAndRemoveDuplicateColumns(col);
		if(isCVC3){
			//If CVC Solver
			constraint = "\nDATATYPE \n"+col+" = ";
			if(columnValue.size()>0){
				if(!unique || !uniqueValues.contains(columnValue.get(0))){
					colValue =  Utilities.escapeCharacters(col.getColumnName())+"__"+Utilities.escapeCharacters(columnValue.get(0));//.trim());
					constraint += "_"+colValue;
					isNullMembers += "ASSERT NOT ISNULL_"+col+"(_"+colValue+");\n";
					uniqueValues.add(columnValue.get(0));
				}				
				colValue = "";
				for(int j=1; j<columnValue.size() || j < 4; j++){
					if(j<columnValue.size())
					{
						if(!unique || !uniqueValues.contains(columnValue.get(j))){
							colValue =  Utilities.escapeCharacters(col.getColumnName())+"__"+Utilities.escapeCharacters(columnValue.get(j));
						}
					}
					else {
						if(!uniqueValues.contains(((Integer)j).toString())){
							colValue =  Utilities.escapeCharacters(col.getColumnName())+"__"+j;
						}else{
							continue;
						}
					}					 
					if(!colValue.isEmpty()){
						constraint = constraint+" | "+"_"+colValue;
						isNullMembers += "ASSERT NOT ISNULL_"+col+"(_"+colValue+");\n";
					}
				}
			}			
			//Adding support for NULLs
			if(columnValue.size()!=0){
				constraint += " | ";
			}
			for(int k=1;k<=4;k++){
				constraint += "NULL_"+col+"_"+k;
				if(k < 4){
					constraint += " | ";
				}
			}
			constraint = constraint+" END\n;";
			constraint += "ISNULL_" + col +" : "+ col + " -> BOOLEAN;\n";
			HashMap<String, Integer> nullValuesChar = new HashMap<String, Integer>();
			for(int k=1;k<=4;k++){
				isNullMembers += "ASSERT ISNULL_" + col+"(NULL_"+col+"_"+k+");\n";
				nullValuesChar.put("NULL_"+col+"_"+k, 0);
			}						
			constraint += isNullMembers;
			
		}
		else { // if another SMT SOLVER
			HashMap<Expr, Integer> nullValuesChar = new HashMap<Expr, Integer>();
			HashMap<Expr, Integer> notnullValuesChar = new HashMap<Expr, Integer>();
			Vector<String> colValues = new Vector<String>();

			if(columnValue.size()>0) {
				if(!unique || !uniqueValues.contains(columnValue.get(0))){
					colValue =  Utilities.escapeCharacters(col.getColumnName())+"__"+Utilities.escapeCharacters(columnValue.get(0));//.trim());
					colValues.add("_"+colValue);
					uniqueValues.add(columnValue.get(0));
				}
				colValue = "";
				for(int j=1; j<columnValue.size() || j < 4; j++) {
					colValue = "";
					if (j<columnValue.size()) {
						if (!unique || !uniqueValues.contains(columnValue.get(j))) {
							if(columnValue.get(j) != null)
							colValue =  Utilities.escapeCharacters(col.getColumnName()) + "__" + Utilities.escapeCharacters(columnValue.get(j));
						}
					}
					else {
						if (!uniqueValues.contains(((Integer)j).toString())) {
							colValue =  Utilities.escapeCharacters(col.getColumnName()) + "__" + j;
						} else {
							continue;
						}
					}

					if (!colValue.isEmpty()) {
						colValues.add("_"+colValue);
					}
					//System.out.println(colValue);				
					}
			}

			String nullVal = "NULL_"+col+"_1";
			colValues.add(nullVal);

			EnumSort colSort = ctx.mkEnumSort(col.getColumnName(), colValues.toArray(new String[colValues.size()]));
			
			for (int i=0; i < colSort.getConsts().length-1; i++) { // all but last value, which is for null
				notnullValuesChar.put(colSort.getConsts()[i], 0);
			}
			
			nullValuesChar.put(colSort.getConst(colSort.getConsts().length-1), 0);  // put the null one in nullValuesChar
			
			ctxSorts.put(col.getColumnName(), colSort);
			solver = ctx.mkSolver();
			solver.push();

			// as of this writing, the API doesn't serialize unused declarations, therefore dummy assertions are used
			Expr dummyVal = ctx.mkConst("dummy", colSort);
			BoolExpr dummyAssert = ctx.mkDistinct(dummyVal);
			solver.add(dummyAssert);
			String z3APIString = solver.toString();
			solver.pop(1); // pop out dummyVal and dummyAssert
			constraint = z3APIString.substring(0, z3APIString.indexOf("(declare-fun")) + "\n";

			constraint +=defineIsNull(nullValuesChar, col)+"\n";
		}
		return constraint;
	}
	/**
	 * TEMPCODE Rahul Sharma
	 * TO check and remove duplicate entries from the column values
	 * @param col : A column of a table
	 */

	private void checkAndRemoveDuplicateColumns(Column col) {
		Vector<String> columnValues = col.getColumnValues();
		Vector<String> uniqueColumnValues = new Vector<String>();
		uniqueColumnValues.addAll(columnValues);
//		columnValues.clear();
//		columnValues.addAll(uniqueColumnValues);
		col.getColumnValues().clear();
		for(String s : uniqueColumnValues) {
			if(!col.getColumnValues().contains(s))
				col.addColumnValues(s);
		}
			
		}

	/**
	 * This method returns the null integer values for CVC data type.
	 * @param cvc
	 * @param col
	 * @return
	 */
	public String getNullMembers(Column col){
	String isNullMembers = "";
		
		if(isCVC3){
			for(int k=-99996;k>=-99999;k--){
				isNullMembers += "ASSERT ISNULL_"+col+"("+k+");\n";
			}
		}else{
			isNullMembers ="";
		}
		return isNullMembers;
	}
	
	/**
	 * This method returns the Footer Constraints based on the solver 
	 * @param cvc
	 * @return
	 */
	public String getFooter(GenerateCVC1 cvc){
		
		String temp = "";
		if(isCVC3){
			temp += "\n\nQUERY FALSE;";			// need to right generalize one
			temp += "\nCOUNTERMODEL;";
		}
		else{
			temp += "\n\n(check-sat)";			// need to right generalize one
//			for(Table t : cvc.getResultsetTables()){
//				temp+= "\n (get-value (O_"+t.getTableName()+"))";
//			}
			
			temp += "\n(get-model)";
			
		}
		return temp;
	}
	
	/**
	 * This method returns the Footer Constraints based on the solver 
	 * @param cvc
	 * @return
	 */
	public String getFooterForAgg(GenerateCVC1 cvc){
		
		String temp = "";
		if(isCVC3){
			temp += "\n\nQUERY FALSE;";			// need to right generalize one
			temp += "\nCOUNTERMODEL;";
		}
		else{
			temp += "\n\n(check-sat)\n";			// need to right generalize one
			
		}
		return temp;
	}
	
	/**
	 * This method returns the constraint String that holds the Tuple Types based on solver
	 * 
	 * @param cvc
	 * @param col
	 * @return
	 */
	public String getTupleTypesForSolver(GenerateCVC1 cvc){
		
		String tempStr = "";
		Table t;
		String temp;
		Vector<String> tablesAdded = new Vector<String>();
		tempStr += addCommentLine(" Tuple Types for Relations\n ");	
		
		if(cvc.getConstraintSolver().equalsIgnoreCase("cvc3")){
			Column c;

			tempStr += ConstraintGenerator.addCommentLine(" Tuple Types for Relations\n ");			 
			for(int i=0;i<cvc.getResultsetTables().size();i++){
				t = cvc.getResultsetTables().get(i);
				temp = t.getTableName();
				if(!tablesAdded.contains(temp)){
					tempStr += temp + "_TupleType: TYPE = [";
				}
				for(int j=0;j<cvc.getResultsetColumns().size();j++){
					c = cvc.getResultsetColumns().get(j);
					if(c.getTableName().equalsIgnoreCase(temp)){
						String s=c.getCvcDatatype();
						if(s!= null && (s.equalsIgnoreCase("INT") || s.equalsIgnoreCase("REAL") || s.equalsIgnoreCase("TIME") || s.equalsIgnoreCase("DATE") || s.equalsIgnoreCase("TIMESTAMP")))
							tempStr += c.getColumnName() + ", ";
						else
							tempStr+=c.getCvcDatatype()+", ";
					}
				}
				tempStr = tempStr.substring(0, tempStr.length()-2);
				tempStr += "];\n";
				/*
				 * Now create the Array for this TupleType
				 */
				tempStr += "O_" + temp + ": ARRAY INT OF " + temp + "_TupleType;\n";
			}
		} else {
			Solver dummySol = ctx.mkSolver();  // for getting string form of z3 context declarations
			
			String[] tablenames = new String[cvc.getResultsetTables().size()];
			for(int i=0;i<cvc.getResultsetTables().size();i++){
				tablenames[i] = cvc.getResultsetTables().get(i).getTableName();
			}
			
			for (int i=0;i<cvc.getResultsetTables().size();i++) {
				int index = 0;
				t = cvc.getResultsetTables().get(i);
				temp = t.getTableName();

				String[] attrNames = new String[t.getNoOfColumn()];
				Sort[] attrTypes = new Sort[t.getNoOfColumn()];
				
				checkResultSetColumns(cvc,cvc.getResultsetColumns()); // TEMPCODE Rahul Sharma // added to check if the resultsetcolumns contains duplicate columns
				
				for(Column c : cvc.getResultsetColumns()) {
					if(c.getTableName().equalsIgnoreCase(temp)) {						
						String s = c.getCvcDatatype();
						if (s!=null && (s.equalsIgnoreCase("Int") || s.equals("TIME") || s.equals("DATE") || s.equals("TIMESTAMP"))) {  // TODO: check datetime types
							attrTypes[index] = ctx.getIntSort();
						}
						else if (s!=null && (s.equalsIgnoreCase("Real"))) {
							attrTypes[index] = ctx.getRealSort();
						}
						else {
							attrTypes[index] = ctxSorts.get(s);
						}

						attrNames[index] = temp+"_"+c+index;
						index++;
					}
				}
				
				String tupleTypeName = temp+"_TupleType";
				Constructor[] cons = new Constructor[] {ctx.mkConstructor(tupleTypeName, "is_"+tupleTypeName, attrNames, attrTypes, null)};
				DatatypeSort tupleType = ctx.mkDatatypeSort(tupleTypeName, cons);
				ctxSorts.put(tupleTypeName, tupleType);
				ArraySort asort = ctx.mkArraySort(ctx.getIntSort(), tupleType);
				String arrName = "O_"+temp;
				Expr aex = ctx.mkConst(arrName, asort);
				ctxSorts.put(arrName, asort);
				ctxConsts.put(arrName, aex);
				// adding dummy asserts so that solver has relevant declarations in string returned by toString() 
				BoolExpr dummyAssert = ctx.mkDistinct(aex);
				dummySol.add(dummyAssert);
			}

			// Temporary procedure to extract relevant declarations from the solver string
			String[] z3Statements = dummySol.toString().split("\n");
			Vector<String> includedStatements = new Vector<String>();
			
			for (String statement : z3Statements) {
				if (statement.contains("_TupleType ") || statement.contains("declare-fun O_")) {
					includedStatements.add(statement);
				}
				if (statement.contains("(assert")) {
					break;
				}
			}
			tempStr += String.join("\n\n", includedStatements);
		}
		return tempStr;
	}
	
	/**
	* TEMPCODE Rahul Sharma : to check if there is any duplicate columns in resultsetcolumns
	* @param cvc
	* @param resultsetColumns
	*/
     private void checkResultSetColumns(GenerateCVC1 cvc,Vector<Column> resultsetColumns) {
    	 LinkedHashSet<Column> hashSet = new LinkedHashSet<Column>(resultsetColumns);
         resultsetColumns.clear();
         resultsetColumns.addAll(hashSet);
         cvc.setResultsetColumns(resultsetColumns); 
     }

	public static String getAssertNotCondition(QueryBlockDetails queryBlock, Node n, int index){
		
		String subQueryConstraints = "";//"ASSERT NOT ";
		
		if(isCVC3){
			subQueryConstraints += "ASSERT NOT " + genPositiveCondsForPred(queryBlock,n,index);			
		}else{
			subQueryConstraints += "(assert (not "+genPositiveCondsForPred(queryBlock,n,index);			
		}
		
		if(subQueryConstraints.endsWith("ASSERT NOT ("))
			subQueryConstraints+= "(1 /= 1) ";
		else if(subQueryConstraints.endsWith("(assert (not "))
			subQueryConstraints+= "(/= 1 1) ";
		
		
		if(isCVC3){
			subQueryConstraints += ";\n";
			}
		else{
			subQueryConstraints += "))\n";
		}
		
		return subQueryConstraints;
	}
	
	/**
	 *  
	 * @param constr1
	 * @param operator
	 * @param constr2
	 * @return
	 */
public static String getAssertNotCondition(String constr1, String operator,String constr2){
		
		String subQueryConstraints = "";//"ASSERT NOT ";
		
		if(isCVC3){
			subQueryConstraints += "ASSERT NOT (" + constr1 + " " +operator +" "+constr2;			
		}else{
			
			subQueryConstraints += "(assert (not "+(operator.equals("/=")? "not (= ": operator) +" "+constr1 +" "+constr2+(operator.equals("/=")? ")": "");		
		}
		
		if(subQueryConstraints.endsWith("ASSERT NOT ("))
			subQueryConstraints+= "(1 /= 1) ";
		else if(subQueryConstraints.endsWith("(assert (not "))
			subQueryConstraints+= "(/= 1 1) ";
		
		
		if(isCVC3){
			subQueryConstraints += "; \n";
			}
		else{
			subQueryConstraints += "))\n";
		}
		
		return subQueryConstraints;
	}

	/*
	 * Generate not null check constraints in assert statements
	 * 
	 */
	public static String genNULLCheckConstraints(Node n, int index) {
		/*Removing NUll enumerations*/
		String constraint="";
		Column col = n.getColumn();
		if(!isCVC3) {
			Table table = col.getTable();
			String type = col.getCvcDatatype();
			String tableName = col.getTableName();
			String columnName = col.getColumnName();
			int pos = table.getColumnIndex(columnName);
			String datatype = col.getCvcDatatype();
			
			String smtCond = "";
			String colName = tableName+"_"+columnName+pos;
			if(datatype!= null && (datatype.equalsIgnoreCase("INT") || datatype.equalsIgnoreCase("REAL") || datatype.equalsIgnoreCase("TIME") || datatype.equalsIgnoreCase("DATE") || datatype.equalsIgnoreCase("TIMESTAMP")))
				constraint = "check"+n.getColumn().getColumnName() + "("+colName+" "+"(select O_"+tableName+" "+index +") )";
			else
				constraint = "not (ISNULL_"+n.getColumn().getColumnName()+" ("+colName+" "+"(select O_"+tableName+" "+index +") ))";
		}
		
		return constraint;
	}


	/**
	 * Generate CVC3 constraints for the given node and its tuple position
	 * @param queryBlock
	 * @param n
	 * @param index
	 * @return
	 */
	public static String genPositiveCondsForPred(QueryBlockDetails queryBlock, Node n, int index){
		if(n.getType().equalsIgnoreCase(Node.getColRefType())){			
			if(isCVC3){
				return cvcMap(n.getColumn(), index+" ");
			}else{				
				return smtMap(n.getColumn(), (IntExpr) ctx.mkInt(index)).toString();
			}
		}
		else if(n.getType().equalsIgnoreCase(Node.getValType())){
			if(!n.getStrConst().contains("$"))
				return n.getStrConst();
			else
				return queryBlock.getParamMap().get(n.getStrConst());
		}
		else if(n.getType().equalsIgnoreCase(Node.getBroNodeType()) || n.getType().equalsIgnoreCase(Node.getBaoNodeType()) || n.getType().equalsIgnoreCase(Node.getLikeNodeType()) ||
				n.getType().equalsIgnoreCase(Node.getAndNodeType()) || n.getType().equalsIgnoreCase(Node.getOrNodeType())){
			if(isCVC3){
					return "( "+ genPositiveCondsForPred(queryBlock, n.getLeft(), index) +" "+ n.getOperator() +" "+ 
							genPositiveCondsForPred(queryBlock, n.getRight(), index) +")";
			}else{
				/* Removing NUll enumerations */
				
				return "( "+ (n.getOperator().equals("/=")? "not (= ": n.getOperator()) + " " +genPositiveCondsForPred(queryBlock, n.getLeft(), index) +" "+ 
				genPositiveCondsForPred(queryBlock, n.getRight(), index) 
				+(n.getOperator().equals("/=")? " )": "")+" )";
				
				/*---------------
				if(col != null && col.getCvcDatatype() != null && (col.getCvcDatatype().equalsIgnoreCase("INT") || col.getCvcDatatype().equalsIgnoreCase("REAL") || col.getCvcDatatype().equalsIgnoreCase("TIME")
						||col.getCvcDatatype().equalsIgnoreCase("TIMESTAMP") || col.getCvcDatatype().equalsIgnoreCase("DATE")))
				{
					if(isCVC3){
						constraint = "\nASSERT NOT ISNULL_"+col.getColumnName()+"("+cvcMap(col, index+"")+");";
					}else{
						//	constraint += "\n (assert NOTISNULL_"+col.getColumnName()+" "+smtMap(col,Integer.toString(index))+")";
						constraint = "\n (assert (get"+col.getColumnName()+" "+smtMap(col,Integer.toString(index))+"))";
					}
				
				}
				else{
					if(isCVC3){
						constraint = "\nASSERT NOT ISNULL_"+col.getCvcDatatype()+"("+cvcMap(col, index+"")+");";
					}else{
						constraint = "\n (assert (NOTISNULL_"+col.getCvcDatatype()+" " +smtMap(col,Integer.toString(index))+"))";
					}
				}
				
				---------------*/
				
			}
		}	
		return null;
	}
	
	
	public static String genPositiveCondsForPredF(QueryBlockDetails queryBlock, Node n, String index) {
		if(n.getType().equalsIgnoreCase(Node.getColRefType())) {
			if(isCVC3) {
				return cvcMap(n.getColumn(), index+" ");
			} else {		
				return smtMap(n.getColumn(), ctx.mkIntConst(index)).toString();
			}
		}
		else if(n.getType().equalsIgnoreCase(Node.getValType())){
			if(!n.getStrConst().contains("$"))
				return n.getStrConst();
			else
				return queryBlock.getParamMap().get(n.getStrConst());
		}
		else if(n.getType().equalsIgnoreCase(Node.getBroNodeType()) || n.getType().equalsIgnoreCase(Node.getBaoNodeType()) || n.getType().equalsIgnoreCase(Node.getLikeNodeType()) ||
				n.getType().equalsIgnoreCase(Node.getAndNodeType()) || n.getType().equalsIgnoreCase(Node.getOrNodeType())){
			if(isCVC3){
					return "( "+ genPositiveCondsForPredF(queryBlock, n.getLeft(), index) +" "+ n.getOperator() +" "+ 
							genPositiveCondsForPredF(queryBlock, n.getRight(), index) +")";
			}else{
				
				return "( "+ (n.getOperator().equals("/=")? "not (= ": n.getOperator()) + " " +genPositiveCondsForPredF(queryBlock, n.getLeft(), index) +" "+ 
						genPositiveCondsForPredF(queryBlock, n.getRight(), index) 
						+(n.getOperator().equals("/=")? " )": "")+" )";
			}
		}	
		return null;
	}
	
	/**
	 * Generate SMT/CVC constraints for the given node
	 * @param queryBlock
	 * @param n
	 * @return
	 */
	public static String genPositiveCondsForPred(QueryBlockDetails queryBlock, Node n) {
		if(n.getType().equalsIgnoreCase(Node.getColRefType())){
			if(isCVC3){
				return cvcMap(n.getColumn(), n);
			}else{				
				return smtMap(n.getColumn(), n);
			}
		}
		else if(n.getType().equalsIgnoreCase(Node.getValType())){

			if(!n.getStrConst().contains("$"))
				return n.getStrConst();
			else
				return queryBlock.getParamMap().get(n.getStrConst());
		}
		else if(n.getType().equalsIgnoreCase(Node.getBroNodeType()) || n.getType().equalsIgnoreCase(Node.getBaoNodeType()) ||n.getType().equalsIgnoreCase(Node.getLikeNodeType()) ||
				n.getType().equalsIgnoreCase(Node.getAndNodeType()) || n.getType().equalsIgnoreCase(Node.getOrNodeType())){
			
			if(isCVC3){
				return "("+ genPositiveCondsForPred(queryBlock, n.getLeft()) + " "+n.getOperator() + " "+
						genPositiveCondsForPred(queryBlock, n.getRight())+")";
			}else{
				return "("+ (n.getOperator().equals("/=")? "not (= ": n.getOperator())+ " " +genPositiveCondsForPred(queryBlock, n.getLeft()) +" "+ 
						genPositiveCondsForPred(queryBlock, n.getRight()) +(n.getOperator().equals("/=")? ")": "")+")";
			}
			
		}
		return null;
	}

	/**
	 * 
	 * @param cvc
	 * @param queryBlock
	 * @param n
	 * @param index
	 * @param paramId
	 * @return
	 */
public static String genPositiveCondsForPred(QueryBlockDetails queryBlock, Node n, int index, String paramId){//For parameters
		
		ConstraintGenerator constraintGenerator = new ConstraintGenerator();
		return constraintGenerator.genPositiveCondsForPred( queryBlock, n, index);
	}


/**
 * 
 * @param cvc
 * @param queryBlock
 * @param n
 * @param index
 * @param paramId
 * @return
 */
public static String genPositiveCondsForPredWithAssert(QueryBlockDetails queryBlock, Node n, int index, String paramId){//For parameters
	
	ConstraintGenerator constraintGenerator = new ConstraintGenerator();
	if(isCVC3){
		return "ASSERT "+constraintGenerator.genPositiveCondsForPred(queryBlock, n, index)+";\n";
	}else{
		return "(assert "+constraintGenerator.genPositiveCondsForPred(queryBlock, n, index)+")\n";
	}
}


	/**
	 * 
	 * @param queryBlock
	 * @param n
	 * @param hm
	 * @return
	 */
	public static String genPositiveCondsForPred(QueryBlockDetails queryBlock, Node n, Map<String,Character> hm){
		Character index = null;
		if(n.getType().equalsIgnoreCase(Node.getColRefType())){
			if(hm.containsKey(n.getTable().getTableName())){
				index=hm.get(n.getTable().getTableName());
			}
			else
			{
				Iterator it = hm.entrySet().iterator();
				index='i';
				while(it.hasNext()){
					Map.Entry pairs = (Map.Entry)it.next();
					char temp=(Character) pairs.getValue();
					if(temp>index)
						index=temp;
				}
				index++;
				hm.put(n.getTable().getTableName(),index);
			}
			//return cvcMap(n.getColumn(), index+"");
			if(isCVC3){
				return cvcMap(n.getColumn(), index+"");
			}else{				
				return smtMap(n.getColumn(), ctx.mkIntConst(index+"")).toString();
			}
		}
		else if(n.getType().equalsIgnoreCase(Node.getValType())){
			if(!n.getStrConst().contains("$"))
				return n.getStrConst();
			else
				return queryBlock.getParamMap().get(n.getStrConst());
		}
		else if(n.getType().equalsIgnoreCase(Node.getBroNodeType()) || n.getType().equalsIgnoreCase(Node.getBaoNodeType()) || n.getType().equalsIgnoreCase(Node.getLikeNodeType()) ||
				n.getType().equalsIgnoreCase(Node.getAndNodeType()) || n.getType().equalsIgnoreCase(Node.getOrNodeType())){
			
			if(isCVC3){
				return "("+ genPositiveCondsForPred(queryBlock, n.getLeft(), hm) +" "+ n.getOperator() +" "+ 
						genPositiveCondsForPred(queryBlock, n.getRight(), hm)+")";
			}else{
				
				return "("+(n.getOperator().equals("/=")? "not (= ": n.getOperator())+ " " +genPositiveCondsForPred(queryBlock, n.getLeft(),hm) +" "+ 
						genPositiveCondsForPred(queryBlock, n.getRight(),hm) +(n.getOperator().equals("/=")? ")": "")+")";
			}
		}
		return "";
	}
	
	
	
	/**
	 * Generates negative constraints for the given string selection node
	 * @param queryBlock
	 * @param n
	 * @return
	 */
	public static String genNegativeStringCond(QueryBlockDetails queryBlock, Node n){
		logger.log(Level.INFO, "Node type: "+n.getType() + n.getLeft() + n.getOperator() + n.getRight());
		ConstraintGenerator constraintGenerator = new ConstraintGenerator();
		if(n.getType().equalsIgnoreCase(Node.getColRefType())){
			
			if(isCVC3){
				return cvcMap(n.getColumn(), n);
			}else{				
				return smtMap(n.getColumn(), n);
			}
			
		}
		else if(n.getType().equalsIgnoreCase(Node.getValType())){

			if(!n.getStrConst().contains("$"))
				return n.getStrConst();
			else
				return queryBlock.getParamMap().get(n.getStrConst());
		}
		else if(n.getType().equalsIgnoreCase(Node.getBroNodeType()) || n.getType().equalsIgnoreCase(Node.getBaoNodeType()) ||n.getType().equalsIgnoreCase(Node.getLikeNodeType()) ||
				n.getType().equalsIgnoreCase(Node.getAndNodeType()) || n.getType().equalsIgnoreCase(Node.getOrNodeType())){
			if(n.getOperator().equals("="))
				n.setOperator("/=");
			else if(n.getOperator().equals("/="))
				n.setOperator("=");
			else if(n.getOperator().equals(">"))
				n.setOperator("<=");
			else if(n.getOperator().equals("<"))
				n.setOperator(">=");
			else if(n.getOperator().equals("<="))
				n.setOperator(">");
			else if(n.getOperator().equals(">="))
				n.setOperator("<");
			else if(n.getOperator().equalsIgnoreCase("~"))
				n.setOperator("!i~");
			
		if(isCVC3){
			return "("+ genPositiveCondsForPred(queryBlock, n.getLeft()) + " "+n.getOperator() + " "+
					genPositiveCondsForPred(queryBlock, n.getRight()) +")";
		}else{
			
			return "("+(n.getOperator().equals("/=")? "not (= ": n.getOperator()) + " " +genPositiveCondsForPred(queryBlock, n.getLeft()) +" "+ 
					genPositiveCondsForPred(queryBlock, n.getRight()) +(n.getOperator().equals("/=")? ")": "")+")";
		}
			
			
			
		}
		return null;
	}
	
	/**
	 * Generate CVC3 constraints for the given node and its tuple position.
	 * 
	 * @param queryBlock
	 * @param n
	 * @param index
	 * @return
	 */
	public static String genPositiveCondsForPredAsString(GenerateCVC1 cvc,QueryBlockDetails queryBlock, Node n, int index){
		
		if(isCVC3){
				return ""+genPositiveCondsForPred(queryBlock, n, index)+ "";
			}else{				
				return ""+genPositiveCondsForPred(queryBlock, n, index)+"";
			}
	}
	
	/**
	 * 
	 * @param left
	 * @param right
	 * @return
	 */
	public static String processImpliedConstraints(String left, String right){
		String result = "";
		
		if(isCVC3){
			result = "ASSERT " + left + " => " + right + ";\n";
		}else{
			result = "(assert " + "(=> "+left+" "+right + ")) \n";
		}				
		return result;
	}
	
	/**
	 * 
	 * @param col
	 * @param index
	 * @param constraint
	 * @return
	 */
	public String getAndSetNotNullValuesBeforeFooter(Column col, Integer index){
		
		 String constraint;
		
		if(col != null && col.getCvcDatatype() != null && (col.getCvcDatatype().equalsIgnoreCase("INT") || col.getCvcDatatype().equalsIgnoreCase("REAL") || col.getCvcDatatype().equalsIgnoreCase("TIME")
				||col.getCvcDatatype().equalsIgnoreCase("TIMESTAMP") || col.getCvcDatatype().equalsIgnoreCase("DATE")))
		{
			if(isCVC3){
				constraint = "\nASSERT NOT ISNULL_"+col.getColumnName()+"("+cvcMap(col, index+"")+");";
			}else{
				//	constraint += "\n (assert NOTISNULL_"+col.getColumnName()+" "+smtMap(col,Integer.toString(index))+")";
				constraint = "\n (assert (get"+col.getColumnName()+" "+smtMap(col, (IntExpr) ctx.mkInt(index)).toString()+"))";
			}
		
		}
		else{
			if(isCVC3){
				constraint = "\nASSERT NOT ISNULL_"+col.getCvcDatatype()+"("+cvcMap(col, index+"")+");";
			}else{
				constraint = "\n (assert (NOTISNULL_"+col.getCvcDatatype()+" " + smtMap(col, (IntExpr) ctx.mkInt(index)).toString()+"))";
			}
		}
		return constraint;
	}
	
	/**
	 * This method is used by GenerateConstraintsRelatedToBranchQuery class for generating ProjectedColBranchQuery constraints
	 *  
	 * @param cvc
	 * @param tempTable
	 * @param index
	 * @param pos
	 * @param operator
	 * @param rightConstraint
	 * @param col
	 * @return
	 */
	public String getStringConstraints(Table tempTable, Integer index, Integer pos,String operator, String rightConstraint,Column col){
		
		String constraint ="";
		String smtCond = "";
		
		if(isCVC3){
			constraint = "\n ASSERT (O_" + tempTable + "[" + index + "]." + pos + " " + operator +" " + rightConstraint + ");\n";
			
		}else{
			String colName = col.getColumnName()+pos;
			smtCond = "("+colName+" "+"(select O_"+tempTable+" "+index +") )";
			
			constraint = "\n (assert ("+(operator.equals("/=")? "not (= ": operator)+" "+rightConstraint+" "+((smtCond!= null && !smtCond.isEmpty())?smtCond:"")+
					(operator.equals("/=")? ")": "")+"))\n";
			
		}
		
		return constraint;
	}
	
/**
 * This method is used by GenerateConstraintsRelatedToBranchQuery class for generating StringConstraintsForBranchQuery constraints
 *  
 * @param cvc
 * @param tempTable
 * @param index
 * @param pos
 * @param operator
 * @param tempTableRight
 * @param indexRight
 * @param posRight
 * @param col
 * @return
 */
public String getStringConstraints(Table tempTable, Integer index, Integer pos,String operator, Table tempTableRight, Integer indexRight, Integer posRight,Column leftCol,Column rightCol){
		
		String constraint ="";
		String smtCond1 = "";
		String smtCond2 = "";
		
		if(isCVC3){
			
			constraint += "\n ASSERT ";					
			if(operator.equals("!=")){
				constraint += " NOT " ;
				operator = "=";
			}			
			constraint += "(O_" + tempTable.getTableName() + "[" + index + "]." + pos;			
			constraint += operator + " O_" + tempTableRight.getTableName() + "[" + indexRight + "]." +posRight + ");";
			
			
		}else{
			String colName = leftCol.getColumnName()+pos;
			
			smtCond1 = "("+colName+" "+" (select O_"+tempTable+" "+index +") )";
			smtCond2 = "("+rightCol.getColumnName()+""+posRight+" "+" (select O_"+tempTableRight+" "+indexRight+") )";
		
			constraint = "\n (assert ("+(operator.equals("/=")? "not (= ": operator)+" "+smtCond1+" "+smtCond2+(operator.equals("/=")? ")": "")+"))";
			
		}
		
		return constraint;
	}

public String getConstraintsForSUMWithAssert(String operator, String opVal, String constraint, Table tempHavingTable, Integer j, Integer tempHavingColIndex,Column col){
	
	String addConstraint = "";
	if(isCVC3){
		
		if(constraint == null || constraint.isEmpty()){
				addConstraint = "\n ASSERT (";
			}
			
			addConstraint += constraint + " + " +"O_" + tempHavingTable.getTableName() + "[" + j + "]." + tempHavingColIndex;
			
			if(constraint == null || constraint.isEmpty()){
				addConstraint += ");";
			}
	}else{
		
		if(constraint == null || constraint.isEmpty()){
			addConstraint = "\n (assert (";
		}
		String colName = col.getColumnName()+tempHavingColIndex ;
		addConstraint += " + (" + constraint + ") ("+colName+" "+"(select O_"+tempHavingTable+" "+j +"))";
		
		if(constraint == null || constraint.isEmpty()){
			addConstraint += "));";
		}
		
	}
	
	
	return addConstraint;
	
}

public String getConstraintsForSUMWithoutAssert(String operator, String opVal, String constraint, Table tempHavingTable, Integer j, Integer tempHavingColIndex,Column col){
	
		String addConstraint = "";
		if(isCVC3){					
			addConstraint += constraint + " + " +"O_" + tempHavingTable.getTableName() + "[" + j + "]." + tempHavingColIndex;
		}else{		
			String colName = col.getColumnName()+tempHavingColIndex ;
			addConstraint += " + (" + constraint + ") ("+colName+" "+"(select O_"+tempHavingTable+" "+j +"))";
		}
	return addConstraint;
	
}

								
public String  getConstraintsForAVG(String constraint, int count, String operator,String value,Column col){
	String addConstraint = "";
	
	if(isCVC3){
		addConstraint += "\n ASSERT ((";
		addConstraint += constraint+ ") / " + count;
		addConstraint += " " + operator + " " + value+");";
		
	}else{
		
		addConstraint += "\n(assert ("+(operator.equals("/=")? "not (= ": operator)+" (/ "+ constraint+ " "+count+") "+value+(operator.equals("/=")? ")": "")+"))" ;	
	}	
	return addConstraint;
}


/**
 * 
 * @param str
 * @return
 */
public StringConstraint getStringConstraint(String str){
	
	StringConstraint s = null;
	if(isCVC3){
		s = new StringConstraint(str.substring(str.indexOf("(")+1, str.lastIndexOf(")"))); //removing brackets
	}else{
		s = new StringConstraint(str.substring(str.indexOf(" (")+1, str.lastIndexOf(")"))); //removing brackets
		s = new StringConstraint(str.substring(str.indexOf(" (")+1, str.lastIndexOf(")")));
	}
	return s;
}

/**
 * 
 * @param var
 * @return
 */
public String getTableName(String var){
	
	String table = "";
	String tableName = "";
	
	if(isCVC3){
		table= (var.split("\\["))[0];
		tableName=table.split("O_")[1];

	}else{
		tableName = var.substring(0,var.indexOf("_"));
	}
	return tableName;
}

/**
 * 
 * @param var
 * @return
 */
public int getColumnIndex(String var){
	
	int columnIndex = 0;
	if(isCVC3){
		columnIndex=Integer.parseInt((var.split("\\."))[1]);
	}else{
		String newStr = var.substring(0,var.indexOf(" (select "));
		columnIndex = Integer.parseInt(newStr.substring(newStr.length() - 1,newStr.length()));
		
	}
	return columnIndex;
}


/**
 * 
 * @param commentLine
 * @return
 */
public static String addCommentLine(String commentLine){
	return "\n\n"+solverSpecificCommentCharacter+"------------------------------------------------------------\n"
				+solverSpecificCommentCharacter+commentLine+
				"\n"+solverSpecificCommentCharacter+"------------------------------------------------------------\n";
}

public String getConstraintSolver() {
	return constraintSolver;
}

public void setConstraintSolver(String constraintSolver) {
	this.constraintSolver = constraintSolver;
}


/**
 * 
 * @param queryBlock
 * @param vn
 * @param c
 * @param countVal
 * @return
 */
public static String generateCVCForCNTForPositiveINT(QueryBlockDetails queryBlock, ArrayList<Node> vn, Column c, int countVal){
	String CVCStr = "";
			if(isCVC3){					
					
					int min = 0,min1=0, max=0,max1=0;
					
					CVCStr += "SUM: INT;\nMIN: INT;\nMAX: INT;\nAVG: REAL;\nCOUNT: INT;";
					CVCStr += "\nMIN1: INT;\nMAX1: INT;\n\n";

					if(countVal == 0){
						CVCStr += "ASSERT (COUNT <  32);\n";//30 because CNT is always CNT+2 = 32 (max in CVC)
					}
					else{
						CVCStr += "ASSERT (COUNT = " + countVal + ");\n";
					}		
					
					CVCStr += "\n\nASSERT (MIN1 <= MIN);\nASSERT (MAX1 >= MAX);\n";
					CVCStr += "ASSERT (COUNT > 0);\nASSERT (MIN <= MAX);\n";
					CVCStr += "ASSERT (MAX1 >= AVG);\nASSERT (AVG >= MIN1);\n";
					
					CVCStr += "ASSERT (SUM  >= MIN * COUNT);\n " +
							" ASSERT (SUM <= MAX * COUNT);\n";
					CVCStr += "ASSERT (AVG * COUNT = SUM);\n";
					
					DataType dt = new DataType();
				
							if((dt.getDataType(c.getDataType())==1 || dt.getDataType(c.getDataType())==2) && c.getMinVal() != -1){
								Vector< Node > selectionConds = new Vector<Node>();
								for(ConjunctQueryStructure conjunct : queryBlock.getConjunctsQs())
									selectionConds.addAll(conjunct.getSelectionConds());
					
								/** if there is a selection condition on c that limits the min val of c */
								min=(UtilsRelatedToNode.getMaxMinForIntCol(c, selectionConds))[1];
								CVCStr += "\nASSERT (MIN1 = " + min +");\n";
							}					
							if((dt.getDataType(c.getDataType())==1 || dt.getDataType(c.getDataType())==2) && c.getMaxVal() != -1){
					
								Vector< Node > selectionConds = new Vector<Node>();
								for(ConjunctQueryStructure conjunct : queryBlock.getConjunctsQs())
									selectionConds.addAll(conjunct.getSelectionConds());
					
								/**if there is a selection condition on c that limits the max val of c*/
								max=(UtilsRelatedToNode.getMaxMinForIntCol(c, selectionConds))[0];
								CVCStr += "\nASSERT (MAX1 = " + max +");\n";
							}							
							for(String s: queryBlock.getParamMap().values()){
								s = s.trim();
								if(s.contains("PARAM")){
									CVCStr += "\n"+s+": BITVECTOR(20);";
								}
					}
					 
					for(Node n: vn){
						if(n.getType().equalsIgnoreCase(Node.getAggrNodeType())
								|| n.getLeft().getType().equalsIgnoreCase(Node.getAggrNodeType())
										|| n.getRight().getNodeType().equalsIgnoreCase(Node.getAggrNodeType())){
							AggregateFunction agg = null;
							if(n.getLeft().getType().equalsIgnoreCase(Node.getAggrNodeType()))
								agg = n.getLeft().getAgg();
							else
								agg = n.getRight().getAgg();							
							
							if(agg.getAggExp().getLeft() != null && agg.getAggExp().getLeft().getType().equalsIgnoreCase(Node.getBaoNodeType())){
							
								if((dt.getDataType(c.getDataType())==1 || dt.getDataType(c.getDataType())==2) && c.getMinVal() != -1)
								{
								 	min1 = min+((UtilsRelatedToNode.getMaxMinForHaving(c, agg.getAggExp()))[1]);			
									CVCStr = CVCStr.replace("\nASSERT (MIN1 = "+ min +");\n","\nASSERT (MIN1 = " + min1 +");\n");
								}
								if((dt.getDataType(c.getDataType())==1 || dt.getDataType(c.getDataType())==2) && c.getMaxVal() != -1)
								{
									max1 = max+ ((UtilsRelatedToNode.getMaxMinForHaving(c, agg.getAggExp()))[0]);		
									
									CVCStr = CVCStr.replace("\nASSERT (MAX1 = " + max +");\n","\nASSERT (MAX1 = " + max1 +");\n");
								}
								
							}else if(agg.getAggExp().getRight() != null && agg.getAggExp().getRight().getType().equalsIgnoreCase(Node.getBaoNodeType())){
								//int constValue  = this.getConstantVal(n);
								if((dt.getDataType(c.getDataType())==1 || dt.getDataType(c.getDataType())==2) && c.getMinVal() != -1)
								{
									min1 = min+((UtilsRelatedToNode.getMaxMinForHaving(c, agg.getAggExp()))[1]);			
									CVCStr = CVCStr.replace("\nASSERT (MIN1 = "+ min +");\n","\nASSERT (MIN1 = " + min1 +");\n");
								}
								if((dt.getDataType(c.getDataType())==1 || dt.getDataType(c.getDataType())==2) && c.getMaxVal() != -1)
								{
									
									max1 = max+ ((UtilsRelatedToNode.getMaxMinForHaving(c, agg.getAggExp()))[0]);		
									CVCStr = CVCStr.replace("\nASSERT (MAX1 = " + max +");\n","\nASSERT (MAX1 = " + max1 +");\n");
								}
								
							}
						}
						CVCStr += "\nASSERT " + n.toCVCString(10, queryBlock.getParamMap()) + ";";
					}
					CVCStr += "\n\nQUERY FALSE;\nCOUNTEREXAMPLE;\nCOUNTERMODEL;";
					return CVCStr;
					
		}else{
			
			int min = 0,min1=0, max=0,max1=0;
			CVCStr += "(set-option:produce-models true)\n (set-option :smt.macro_finder true) \n";
			CVCStr += "(declare-const SUM Int) \n (declare-const MIN Int) \n (declare-const MAX Int) \n (declare-const AVG Real) \n (declare-const COUNT Int) \n";
			CVCStr += "(declare-const CNT Real) \n (declare-const MIN1 Int) \n (declare-const MAX1 Int) \n\n";

			if(countVal == 0){
				CVCStr += "(assert (< COUNT 32))\n";//30 because CNT is always CNT+2 = 32 (max in CVC)
			}
			else{
				CVCStr += "(assert (= COUNT " + countVal + "))\n";
			}		
			
			CVCStr += "\n\n(assert (<= MIN1 MIN)) \n ";
			CVCStr += "(assert (>= MAX1 MAX))\n";
			CVCStr += "(assert (> COUNT 0))\n";
			CVCStr += "(assert (<= MIN MAX))\n";
			CVCStr += "(assert (>= MAX1 AVG))\n";
			CVCStr += "(assert (>= AVG MIN1))\n";
			
			CVCStr += "(assert (>= SUM  (* MIN COUNT)))\n " +
					" (assert (<= SUM (* MAX COUNT)))\n";
			CVCStr += "(assert (= SUM (* AVG COUNT)))\n";

			
			DataType dt = new DataType();
			
			if((dt.getDataType(c.getDataType())==1 || dt.getDataType(c.getDataType())==2) && c.getMinVal() != -1){
				Vector< Node > selectionConds = new Vector<Node>();
				for(ConjunctQueryStructure conjunct : queryBlock.getConjunctsQs())
					selectionConds.addAll(conjunct.getSelectionConds());
	
				/** if there is a selection condition on c that limits the min val of c */
				min=(UtilsRelatedToNode.getMaxMinForIntCol(c, selectionConds))[1];
				CVCStr += "\n(assert (= MIN1 " + min +"))\n";
			}					
			if((dt.getDataType(c.getDataType())==1 || dt.getDataType(c.getDataType())==2) && c.getMaxVal() != -1){
	
				Vector< Node > selectionConds = new Vector<Node>();
				for(ConjunctQueryStructure conjunct : queryBlock.getConjunctsQs())
					selectionConds.addAll(conjunct.getSelectionConds());
	
				/**if there is a selection condition on c that limits the max val of c*/
				max=(UtilsRelatedToNode.getMaxMinForIntCol(c, selectionConds))[0];
				CVCStr += "\n(assert (= MAX1 " + max +"))\n";
			}							
			for(String s: queryBlock.getParamMap().values()){
				s = s.trim();
				if(s.contains("PARAM")){
					CVCStr += "\n (declare-const "+s+" (_ BitVec 32))";
				}
			}
	 
			for(Node n: vn){
				if(n.getType().equalsIgnoreCase(Node.getAggrNodeType())
						|| n.getLeft().getType().equalsIgnoreCase(Node.getAggrNodeType())
								|| n.getRight().getNodeType().equalsIgnoreCase(Node.getAggrNodeType())){
					AggregateFunction agg = null;
					if(n.getLeft().getType().equalsIgnoreCase(Node.getAggrNodeType()))
						agg = n.getLeft().getAgg();
					else
						agg = n.getRight().getAgg();							
					
					if(agg.getAggExp().getLeft() != null && agg.getAggExp().getLeft().getType().equalsIgnoreCase(Node.getBaoNodeType())){
					
						if((dt.getDataType(c.getDataType())==1 || dt.getDataType(c.getDataType())==2) && c.getMinVal() != -1)
						{
						 	min1 = min+((UtilsRelatedToNode.getMaxMinForHaving(c, agg.getAggExp()))[1]);			
							CVCStr = CVCStr.replace("\n(assert (= MIN1 "+ min +")) \n","\n (assert (= MIN1 " + min1 +"))\n");
						}
						if((dt.getDataType(c.getDataType())==1 || dt.getDataType(c.getDataType())==2) && c.getMaxVal() != -1)
						{
							max1 = max+ ((UtilsRelatedToNode.getMaxMinForHaving(c, agg.getAggExp()))[0]);		
							
							CVCStr = CVCStr.replace("\n(assert (= MAX1 " + max +"))\n","\n(assert (= MAX1 " + max1 +"))\n");
						}
						
					}else if(agg.getAggExp().getRight() != null && agg.getAggExp().getRight().getType().equalsIgnoreCase(Node.getBaoNodeType())){
						//int constValue  = this.getConstantVal(n);
						if((dt.getDataType(c.getDataType())==1 || dt.getDataType(c.getDataType())==2) && c.getMinVal() != -1)
						{
							min1 = min+((UtilsRelatedToNode.getMaxMinForHaving(c, agg.getAggExp()))[1]);			
							CVCStr = CVCStr.replace("\n(assert (= MIN1 "+ min +"))\n","\n(assert (= MIN1 " + min1 +"))\n");
						}
						if((dt.getDataType(c.getDataType())==1 || dt.getDataType(c.getDataType())==2) && c.getMaxVal() != -1)
						{
							
							max1 = max+ ((UtilsRelatedToNode.getMaxMinForHaving(c, agg.getAggExp()))[0]);		
							CVCStr = CVCStr.replace("\nassert (= MAX1 " + max +"))\n","\n(assert (= MAX1 " + max1 +"))\n");
						}
						
					}
				}
				CVCStr += "\n(assert (" + n.toSMTString(10, queryBlock.getParamMap()) + "))";
			}
			
			CVCStr += "\n (check-sat) \n (get-value (SUM)) \n (get-value (MIN)) \n (get-value (MAX)) \n (get-value (AVG)) \n (get-value (COUNT))  \n (get-value (CNT))"
					+ "\n (get-value (MIN1)) \n (get-value (MAX1)) \n \n" ;
			return CVCStr;
			
		}
}

/**
 * 
 * @param filePath
 * @param cmdString
 * @param cvc
 */
public static void getCountExeFile(String filePath, String cmdString, GenerateCVC1 cvc){
	Runtime r = Runtime.getRuntime();
	cmdString = "";
	cmdString = "#!/bin/bash\n";
	
	if(isCVC3){
		cmdString += Configuration.smtsolver+" "+ Configuration.homeDir+"/temp_smt" +filePath+ "/getCount.cvc > "+Configuration.homeDir+"/temp_smt" + filePath + "/COUNTCVC \n";
		cmdString +=Configuration.smtsolver+" "+ Configuration.homeDir+"/temp_smt" + filePath + "/getCount.cvc | grep -e 'Valid' > isNotValid \n";
		cmdString += "grep -e 'COUNT = ' "+ Configuration.homeDir+"/temp_smt" + filePath + "/COUNTCVC" +" | awk -F \" \" '{print $4}' | awk -F \")\" '{print $1}' > "+Configuration.homeDir+"/temp_smt" +filePath + "/COUNT\n";
	}else{
		
		cmdString += Configuration.smtsolver+" "+ Configuration.homeDir+"/temp_smt" +filePath+ "/getCount.smt > "+Configuration.homeDir+"/temp_smt" + filePath + "/COUNTCVC \n";
		cmdString += Configuration.smtsolver+" "+ Configuration.homeDir+"/temp_smt" +filePath+ "/getCount.smt | grep -e 'unsat' > isNotValid \n";

		//cmdString += Configuration.smtsolver+" --lang smtlib "+ Configuration.homeDir+"/temp_smt" +filePath+ "/getCount.cvc > "+Configuration.homeDir+"/temp_smt" + filePath + "/COUNTCVC \n";
		//cmdString +=Configuration.smtsolver+" --lang smtlib "+ Configuration.homeDir+"/temp_smt" + filePath + "/getCount.cvc | grep -e 'unsat' > isNotValid \n";
		cmdString += "grep -e '((COUNT' "+ Configuration.homeDir+"/temp_smt" + filePath + "/COUNTCVC" +" | awk -F \" \" '{print $2}' | awk -F \")\" '{print $1}' > "+Configuration.homeDir+"/temp_smt" +filePath + "/COUNT\n";
	}
	
	Utilities.writeFile(Configuration.homeDir+"/temp_smt" + cvc.getFilePath() + "/execCOUNT", cmdString);
} 

/**
 * 
 * @param filePath
 * @param cvc
 */
public static void getAggConstraintExeFile(String filePath,GenerateCVC1 cvc) {
	
	String cmdString = "";
	cmdString = "#!/bin/bash\n";
	if(isCVC3){
		cmdString += Configuration.smtsolver+" "+ Configuration.homeDir+"/temp_smt" +filePath+ "/checkAggConstraints.cvc | grep -e 'Invalid' > isValid \n";
	}else{
		cmdString += Configuration.smtsolver+" "+ Configuration.homeDir+"/temp_smt" +filePath+ "/checkAggConstraints.smt | grep -e 'sat' > isValid \n";
		//cmdString += Configuration.smtsolver+" --lang smtlib "+ Configuration.homeDir+"/temp_smt" +filePath+ "/checkAggConstraints.cvc | grep -e 'true' > isValid \n";
	}
	Utilities.writeFile(Configuration.homeDir+"/temp_smt" + cvc.getFilePath() + "/checkAggConstraints", cmdString);
}

/**
 * 
 * @param param
 * @param datatype
 * @param retVal
 * @return
 */
public static String getParamRelation(String param, String datatype,String retVal,String cvcDataType){
	String constr = "";
	if(isCVC3){
		constr = param + " : " + datatype +";\n" + retVal;
	}else{
		if(datatype.equalsIgnoreCase("INT")){
			constr += "(declare-const "+param + " Int"+") \n" + retVal;
		}else if(datatype.equalsIgnoreCase("REAL")){
			constr += "(declare-const "+param + " Real"+") \n" + retVal;
		}
		else{					
			if(cvcDataType != null && cvcDataType.equalsIgnoreCase("Int")){
				constr += "(declare-const "+param + " i_" + datatype +") \n" + retVal;
			}else if(cvcDataType != null && cvcDataType.equalsIgnoreCase("Real")){
				constr += "(declare-const "+param + " r_" + datatype +") \n" + retVal;
			}else{
				constr += "(declare-const "+param + " " + datatype +") \n" + retVal;
			}
		}
	}
	 return constr;
}


public String getStrConstWithScale(String strConstant, Integer epsilon,String operator){
	String strConst = "";
	if(isCVC3){
		strConst = "(" + strConstant + " "+operator+ " 1/" + epsilon + ")";
		
	}else{
		strConst = "("+operator+" "+ strConstant + " (/ 1 "+epsilon+" )"+")";
	}
	return strConst;
	
}

public String generateCVCForNullCheckInHaving() {
	if(isCVC3) {
		return "";
	}

	String returnStr="";
	String[] Datatypes = new String[] {"Real", "Int"};

	for (String Datatype : Datatypes) {
		String columnName = Datatype + "col";
		Expr[] nullArr = null;
		ArithExpr nullVal = null;
		ArithExpr zeroVal = null;
		Sort type = null;

		if (Datatype.endsWith("Real")) {
			nullArr = new Expr[] {ctx.mkRealConst(columnName)};
			type = ctx.getRealSort();
			nullVal = ConstraintGenerator.realNull;
			zeroVal = ctx.mkReal("0.0");
		}
		else {  // Int case
			nullArr = new Expr[] {ctx.mkIntConst(columnName)};
			type = ctx.getIntSort();
			nullVal = ConstraintGenerator.intNull;
			zeroVal = ctx.mkInt(0);
		}

		String checkAllNullName = "CHECKALL_NULL" + Datatype;
		FuncDecl checkAllNull = ctx.mkFuncDecl(checkAllNullName, type, ctx.mkBoolSort());
		ctxFuncDecls.put(checkAllNullName, checkAllNull);
		
		String maxRepNullName = "MAX_REPLACE_NULL_" + Datatype;
		FuncDecl maxRepNull = ctx.mkFuncDecl(maxRepNullName, type, type);
		ctxFuncDecls.put(maxRepNullName, maxRepNull);
		
		String sumRepNullName = "SUM_REPLACE_NULL_" + Datatype;
		FuncDecl sumRepNull = ctx.mkFuncDecl(sumRepNullName, type, type);
		ctxFuncDecls.put(sumRepNullName, sumRepNull);
		
		String minRepNullName = "MIN_REPLACE_NULL_" + Datatype;
		FuncDecl minRepNull = ctx.mkFuncDecl(minRepNullName, type, type);
		ctxFuncDecls.put(minRepNullName, minRepNull);
		
		
		// CHECKALL_NULL_*
		Expr checkAllNullCall = checkAllNull.apply(nullArr);
		Expr funcBody = ctx.mkEq(nullArr[0], nullVal);
		Expr quantBody = ctx.mkEq(checkAllNullCall, funcBody);
		Expr funcQuantifier = ctx.mkForall(nullArr, quantBody, 1, null, null, null, null);
		returnStr += "\n" + checkAllNull.toString() + "\n(assert " + funcQuantifier.toString() + ")\n";

		// MAX_REPLACE_NULL_* - why do we need this?
		Expr maxRepNullCall = maxRepNull.apply(nullArr);
		funcBody = ctx.mkITE(ctx.mkEq(nullArr[0], nullVal), nullVal, nullArr[0]);
		quantBody = ctx.mkEq(maxRepNullCall, funcBody);
		funcQuantifier = ctx.mkForall(nullArr, quantBody, 1, null, null, null, null);
		returnStr += "\n" + maxRepNull.toString() + "\n(assert " + funcQuantifier.toString() + ")\n";

		// SUM_REPLACE_NULL_*
		Expr sumRepNullCall = sumRepNull.apply(nullArr);
		funcBody = ctx.mkITE(ctx.mkEq(nullArr[0], nullVal), zeroVal, nullArr[0]);
		quantBody = ctx.mkEq(sumRepNullCall, funcBody);
		funcQuantifier = ctx.mkForall(nullArr, quantBody, 1, null, null, null, null);
		returnStr += "\n" + sumRepNull.toString() + "\n(assert " + funcQuantifier.toString() + ")\n";

		// MIN_REPLACE_NULL_*
		Expr minRepNullCall = minRepNull.apply(nullArr);
		funcBody = ctx.mkITE(ctx.mkEq(nullArr[0], nullVal), ctx.mkSub(zeroVal, nullVal), nullArr[0]);
		quantBody = ctx.mkEq(minRepNullCall, funcBody);
		funcQuantifier = ctx.mkForall(nullArr, quantBody, 1, null, null, null, null);
		returnStr += "\n" + minRepNull.toString() + "\n(assert " + funcQuantifier.toString() + ")\n";
	}

	return returnStr;
}

public Vector<Quantifier> getDomainConstraintsforZ3(GenerateCVC1 cvc) {
	
	Vector<Quantifier> domainConstraints = new Vector<Quantifier>();

	int turn = 0;
	
	for(int i=0; i < cvc.getResultsetTables().size(); i++){
		turn = 0;

		Table table = cvc.getResultsetTables().get(i);

		String tableName = table.getTableName();

		for(String col : table.getColumns().keySet()){
			if((table.getColumn(col).getCvcDatatype()).equalsIgnoreCase("INT") || (table.getColumn(col).getCvcDatatype()).equalsIgnoreCase("REAL")) {

				if (turn++ == 0) {
					IntExpr[] qVarArray = new IntExpr[1];
					IntExpr qVar = ctx.mkIntConst("i");  // i should not conflict with any global i
					BoolExpr ac1 = ctx.mkLe(ctx.mkInt("1"), qVar);
					//BoolExpr ac2 = ctx.mkLe(qVar, ctx.mkInt(Integer.toString(cvc.getNoOfOutputTuples(tableName))));
					// added by rambabu for temporary fix
 					BoolExpr ac2;
 					try {
 					    ac2 = ctx.mkLe(qVar, ctx.mkInt(Integer.toString(cvc.getNoOfOutputTuples(tableName))));
 					}
 					catch(Exception e){
 					    ac2 = ctx.mkLe(qVar, ctx.mkInt(Integer.toString(cvc.getNoOfOutputTuples(tableName.toUpperCase()))));
 					}
 					// added by rambabu ended here
					
					BoolExpr antecedant = ctx.mkAnd(ac1, ac2);

					FuncDecl getFuncDecl = ctxFuncDecls.get("check"+col);
					FuncDecl isNullFuncDecl = ctxFuncDecls.get("ISNULL_"+col);
					Expr selectExpr = ConstraintGenerator.smtMap(table.getColumn(col), qVar);
					BoolExpr con1 = ctx.mkOr((BoolExpr) getFuncDecl.apply(selectExpr), (BoolExpr) isNullFuncDecl.apply(selectExpr));
					BoolExpr consequent = ctx.mkAnd(con1);

					Expr body = ctx.mkImplies(antecedant, consequent);
					qVarArray[0] = qVar;
					Quantifier funcQuantifier = ctx.mkForall(qVarArray, body, 1, null, null, null, null);
					domainConstraints.add(funcQuantifier);
				}
			}
		}
	}

	return domainConstraints;
	}
}

