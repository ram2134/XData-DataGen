package killMutations.fromClauseNestedBlock;

import generateConstraints.ConstraintGenerator;
import generateConstraints.GenerateCommonConstraintsForQuery;
import generateConstraints.GenerateConstraintForUnintendedJoins;
import generateConstraints.GenerateConstraintsForConjunct;
import generateConstraints.GenerateConstraintsForHavingClause;
import generateConstraints.GenerateGroupByConstraints;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import parsing.ConjunctQueryStructure;
import parsing.Table;
import testDataGen.GenerateCVC1;
import testDataGen.QueryBlockDetails;
import testDataGen.RelatedToParameters;
import util.TagDatasets;

/**
 * This class generates data sets to kill un-intended join mutations due to common names of the tables in each from clause nested sub query block
 * @author mahesh
 *
 */
public class UnintendedJoinsInFromSubQuery {

	private static Logger logger = Logger.getLogger(UnintendedJoinsInFromSubQuery.class.getName());
	/**
	 * Generates constraints to kill unintended join mutations
	 * @param cvc
	 * @throws Exception
	 */

	public static void generateDataForkillingUnintendedJoinsInFromSubquery(	GenerateCVC1 cvc) throws Exception{

		/** keep a copy of this tuple assignment values */
		//HashMap<String, Integer> noOfOutputTuplesOrig = (HashMap<String, Integer>) cvc.getNoOfOutputTuples().clone();
		HashMap<String, Integer> noOfTuplesOrig = (HashMap<String, Integer>) cvc.getNoOfTuples().clone();
		HashMap<String, Integer[]> repeatedRelNextTuplePosOrig = (HashMap<String, Integer[]>)cvc.getRepeatedRelNextTuplePos().clone();

		cvc.inititalizeForDatasetQs();

		/** get the tuple assignment for this query
		 * If no possible assignment then not possible to kill this mutation*/
		if(GenerateCVC1.tupleAssignmentForQuery(cvc) == false)
			return ;

		/** Kill unintended join mutations in each from clause nested block of this query*/
		for(QueryBlockDetails qbt: cvc.getOuterBlock().getFromClauseSubQueries()){

			logger.log(Level.INFO,"\n----------------------------------");
			logger.log(Level.INFO,"GENERATE DATA FOR KILLING UNINTENDED JOINS DUE TO COMMON NAMES MUTATION IN FROM CLAUSE NESTED SUB QUERY BLOCK \n");
			logger.log(Level.INFO,"----------------------------------\n");
			
			/** we have to kill the mutations in each conjunct*/
			for( ConjunctQueryStructure con: qbt.getConjunctsQs()){

				logger.log(Level.INFO,"\n----------------------------------");
				logger.log(Level.INFO,"NEW CONJUNCT IN KILLING UNINTENDED JOIN MUTATION: \n");
				logger.log(Level.INFO,"----------------------------------\n");
				
				/** Initialize the data structures for generating the data to kill this mutation */
				cvc.setConstraints( new ArrayList<String>());
				cvc.setStringConstraints( new ArrayList<String>());

				/** Add constraints related to parameters*/
				cvc.getConstraints().add(RelatedToParameters.addDatatypeForParameters( cvc, qbt ));

				cvc.setResultsetTableColumns1( new HashMap<Table,Vector<String>>() );

				cvc.setCVCStr("");

				cvc.setTypeOfMutation("");
				

				/**set the type of mutation we are trying to kill*/
				cvc.setTypeOfMutation( TagDatasets.MutationType.UNINTENDED, TagDatasets.QueryBlock.FROM_SUBQUERY );
				
				
				/** get the constraints to kill this mutation*/
				String constraintString =  GenerateConstraintForUnintendedJoins.getConstraintsForUnintendedJoin( cvc, qbt, con);

				if( constraintString == "")/**means there are no extra columns with same column name*/
					continue ;
				
				cvc.getConstraints().add(ConstraintGenerator.addCommentLine("CONSTRAINTS TO KILL UNINTENDE JOINS IN FROM CLAUSE SUBQ QEURY BLOCK "));
				cvc.getConstraints().add( constraintString );
				cvc.getConstraints().add(ConstraintGenerator.addCommentLine("END OF CONSTRAINTS TO KILL UNINTENDE JOINS IN FROM CLAUSE SUBQ QEURY BLOCK "));
				
				/** get the constraints for each from clause nested sub query block except this from clause nested sub query block*/
				for(QueryBlockDetails qb: cvc.getOuterBlock().getFromClauseSubQueries()){

					if( !qb.equals(qbt)){
						
						cvc.getConstraints().add(ConstraintGenerator.addCommentLine("FROM CLAUSE SUBQUERY"));				
						cvc.getConstraints().add( QueryBlockDetails.getConstraintsForQueryBlock(cvc, qb) );				
						cvc.getConstraints().add(ConstraintGenerator.addCommentLine(" END OF FROM CLAUSE SUBQUERY"));
					}
				}

				/**add the negative constraints for all the other conjuncts of this query block */
				for(ConjunctQueryStructure inner: qbt.getConjunctsQs())
					if(inner != con)
						cvc.getConstraints().add( GenerateConstraintsForConjunct.generateNegativeConstraintsConjunct(cvc, qbt, inner) );	

				/**add positive constraints for all the conditions of this conjunct*/
				cvc.getConstraints().add( GenerateConstraintsForConjunct.getConstraintsForConjuct(cvc, qbt, con) );		

				/** get group by constraints */
				cvc.getConstraints().add(ConstraintGenerator.addCommentLine("GROUP BY CLAUSE CONSTRAINTS FOR OUTER QUERY BLOCK "));
				cvc.getConstraints().add( GenerateGroupByConstraints.getGroupByConstraints( cvc, qbt) );


				/** Generate havingClause constraints */
				cvc.getConstraints().add(ConstraintGenerator.addCommentLine("HAVING CLAUSE CONSTRAINTS FOR OUTER QUERY BLOCK "));
				for(int l=0; l< qbt.getNoOfGroups(); l++)
					for(int k=0; k < qbt.getAggConstraints().size();k++){
						cvc.getConstraints().add(GenerateConstraintsForHavingClause.getHavingClauseConstraints(cvc, qbt, qbt.getAggConstraints().get(k), qbt.getFinalCount(), l) );
					}
				cvc.getConstraints().add(ConstraintGenerator.addCommentLine("END OF HAVING CLAUSE CONSTRAINTS FOR OUTER QUERY BLOCK "));


				/** add constraints of outer query block */
				cvc.getConstraints().add( QueryBlockDetails.getConstraintsForQueryBlockExceptSubQuries(cvc, cvc.getOuterBlock()) );
				

				/** add other constraints of outer query block */
				cvc.getConstraints().add( QueryBlockDetails.getOtherConstraintsForQueryBlock(cvc, cvc.getOuterBlock()) );

				/** Call the method for the data generation*/
				GenerateCommonConstraintsForQuery.generateDataSetForConstraints(cvc);
			}

		}

		/** Revert back to the old assignment */
		cvc.setNoOfTuples( (HashMap<String, Integer>) noOfTuplesOrig.clone() );
	//	cvc.setNoOfOutputTuples( (HashMap<String, Integer>) noOfOutputTuplesOrig.clone() );
		cvc.setRepeatedRelNextTuplePos( (HashMap<String, Integer[]>)repeatedRelNextTuplePosOrig.clone() );
	}

}
