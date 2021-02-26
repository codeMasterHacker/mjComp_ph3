package ControlFlowGraph;
import Temp.*;
import Graph.*;

/**
 * A control flow graph is a directed graph in which each edge
 * indicates a possible flow of control.  Also, each node in
 * the graph defines a set of temporaries; each node uses a set of
 * temporaries; and each node is, or is not, a <strong>move</strong>
 * instruction.
 *
 * @see AssemFlowGraph
 */

public abstract class ControlFlowGraph extends Graph 
{
    /**
    * The set of temporaries defined by this instruction or block 
    */
	public abstract TempList def(Node node);

    /**
    * The set of temporaries used by this instruction or block 
    */
	public abstract TempList use(Node node);

    /**
    * True if this node represents a <strong>move</strong> instruction,
    * i.e. one that can be deleted if def=use. 
    */
	public abstract boolean isMove(Node node);

    /**
    * Print a human-readable dump for debugging.
    */
    @Override
    public void show(java.io.PrintStream out) 
    {
        for (NodeList p = nodes(); p != null; p = p.tail) 
        {
            Node n = p.head;

	        out.print(n.toString());
	        out.print(": ");
	  
            for(TempList q = def(n); q != null; q = q.tail) 
            {
                out.print(q.head.toString());
                out.print(" ");
	        }
	  
            out.print(isMove(n) ? "<= " : "<- ");
	  
            for(TempList q = use(n); q != null; q = q.tail) 
            {
                out.print(q.head.toString());
                out.print(" ");
	        }

	        out.print("; goto ");
	  
            for(NodeList q = n.succ(); q != null; q = q.tail) 
            {
                out.print(q.head.toString());
	            out.print(" ");
	        }
	  
            out.println();
        }
    }

    @Override
    public void show() 
    {
        for (NodeList p = nodes(); p != null; p = p.tail) 
        {
            Node n = p.head;

	        System.out.print(n.toString());
	        System.out.print(": ");
	  
            for(TempList q = def(n); q != null; q = q.tail) 
            {
                System.out.print(q.head.toString());
                System.out.print(" ");
	        }
	  
            System.out.print(isMove(n) ? "<= " : "<- ");
	  
            for(TempList q = use(n); q != null; q = q.tail) 
            {
                System.out.print(q.head.toString());
                System.out.print(" ");
	        }

	        System.out.print("; goto ");
	  
            for(NodeList q = n.succ(); q != null; q = q.tail) 
            {
                System.out.print(q.head.toString());
	            System.out.print(" ");
	        }
	  
            System.out.println();
        }
    }
}
