package Mips;
import Frame.*;
import Tree.*;

public class InFrame extends Access 
{
    public int offset;

    public InFrame(int offset)
    {
        this.offset = offset;
    }

    @Override
    public Exp exp(Exp e) 
    {
        return new MEM(new BINOP(BINOP.PLUS, e, new CONST(offset)));
    }

    @Override
    public String toString() 
    {
        return "In frame at location " + offset + "\n";
    }
}
