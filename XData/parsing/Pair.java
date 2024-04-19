package parsing;

import java.io.Serializable;

import parsing.Node;

//
//public class Pair implements Serializable{
//	
//	private static final long serialVersionUID = -5712147089838421706L;
//
//	public Node first;
//	
//	public Node second;
//	
//	public Pair(Node n1, Node n2){
//		this.first = n1;
//		this.second = n2;
//	}
//	
//	@Override
//	public String toString(){
//		return this.first.toString() + "->" + this.second.toString();
//	}
//
//}
public class Pair<F, S> implements Serializable {
    public F first; //first member of pair
    public S second; //second member of pair
    public Pair()
    {
    	
    }
    public Pair(F first, S second) {
        this.first = first;
        this.second = second;
    }

    public void setFirst(F first) {
        this.first = first;
    }

    public void setSecond(S second) {
        this.second = second;
    }

    public F getFirst() {
        return first;
    }

    public S getSecond() {
        return second;
    }
}

