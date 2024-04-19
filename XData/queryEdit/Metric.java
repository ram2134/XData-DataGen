package queryEdit;
import java.util.*;

import parsing.Node;

import parsing.QueryStructure;

public class Metric {
	
	public static float LCS (QueryStructure student,QueryStructure Instructor)
	{
		ArrayList<Node> std=student.getLstOrderByNodes();
		ArrayList<Node> inst=Instructor.getLstOrderByNodes();
		int p= lcs(std,inst,std.size(),inst.size());
		return (p*100)/(float)max(std.size(),inst.size());
	}
	 private static int lcs( ArrayList<Node> X, ArrayList<Node> Y, int m, int n )
	  {
	    int L[][] = new int[m+1][n+1];
	 
	    /* Following steps build L[m+1][n+1] in bottom up fashion. Note
	         that L[i][j] contains length of LCS of X[0..i-1] and Y[0..j-1] */
	    for (int i=0; i<=m; i++)
	    {
	      for (int j=0; j<=n; j++)
	      {
	        if (i == 0 || j == 0)
	            L[i][j] = 0;
	        //else if (X[i-1].equals(Y[j-1]))
	        else if (X.get(i-1).equals(Y.get(j-1)))
	            L[i][j] = L[i-1][j-1] + 1;
	        else
	            L[i][j] = max(L[i-1][j], L[i][j-1]);
	      }
	    }
	  return L[m][n];
	  }
	 
	 private static int max(int a, int b)
	  {
	    return (a > b)? a : b;
	  }

}
