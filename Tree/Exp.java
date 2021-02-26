package Tree;

public interface Exp 
{
    public abstract ExpList kids();
	public abstract Exp build(ExpList kids);
}
