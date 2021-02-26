package Mips;
import Frame.*;
import Temp.*;
import Tree.*;

public class InReg extends Access 
{
    public Temp temp;

    InReg(Temp t) 
    {
        temp = t;
    }

    @Override
    public Exp exp(Exp e) 
    {
        return new TEMP(temp);
    }

    @Override
    public String toString() 
    {
        return "In register at location " + temp.toString() + "\n";
    }
}
