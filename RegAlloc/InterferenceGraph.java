package RegAlloc;
import Graph.*;
import Temp.*;

public abstract class InterferenceGraph extends Graph 
{
    public abstract Node tnode(Temp temp);
    public abstract Temp gtemp(Node node);
    public abstract MoveList moves();
    public int spillCost(Node node) {return 1;}
}
