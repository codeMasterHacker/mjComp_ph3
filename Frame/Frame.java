package Frame;
import Temp.*;
import Util.*;

public abstract class Frame 
{
    public Label name;
    public AccessList formals;

    public abstract Frame newFrame(Label name, BoolList formals);
    public abstract Access allocLocal(boolean escape);
    /* . . . other stuff, eventually . . . */
}
