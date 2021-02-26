package Tree;
import Temp.*;

public class TEMP implements Exp 
{
    public Temp temp;
    public TEMP(Temp t) { temp=t; }
    public ExpList kids() {return null;}
    public Exp build(ExpList kids) { return this; }
}
