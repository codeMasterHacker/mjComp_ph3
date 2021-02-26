package Tree;

public class CONST implements Exp 
{
    public int value;

    public CONST(int v) { value = v; }
    public ExpList kids() { return null; }
    public Exp build(ExpList kids) { return this; }
}
