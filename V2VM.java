import cs132.util.ProblemException;
import cs132.vapor.parser.VaporParser;
import cs132.vapor.ast.VaporProgram;
import cs132.vapor.ast.VBuiltIn.Op;

import cs132.util.*;
import cs132.vapor.ast.*;
import cs132.vapor.parser.*;
import cs132.vapor.ast.VInstr.Visitor;

import java.io.*;
import java.util.*;

class CFG_Node 
{
    public int index = 0;
    public SortedSet<String> in;
    public SortedSet<String> out;
    public SortedSet<String> def;
    public SortedSet<String> use;
    public SortedSet<String> inPrime;
    public SortedSet<String> outPrime;
    public SortedSet<String> active;
    public List<Integer> succ;

    public CFG_Node(int i)
    {
        index = i;
        in = new TreeSet<>();
        out = new TreeSet<>();
        def = new TreeSet<>();
        use = new TreeSet<>();
        inPrime = new TreeSet<>();
        outPrime = new TreeSet<>();
        active = new TreeSet<>();
        succ = new ArrayList<>();
    }

    public void add_singleSucc(int i)
    {
        if (!succ.contains(i+1))
            succ.add(i+1);
    }

    public void print()
    {
        System.out.println("in = " + in);
        System.out.println("out = " + out);
        System.out.println("def = " + def);
        System.out.println("use = " + use);
        System.out.println("active = " + active);
    }
}



class LiveInterval 
{
    public int startPoint = 0;
    public int endPoint = 0;
    public int location = 0;
    public String identifier = null;
    public String register = null;

    public LiveInterval(int start, int end, String identifier)
    {
        this.startPoint = start;
        this.endPoint = end;
        this.identifier = identifier;
        register = "";
        location = -1;
    }

    public String getLocation()
    {
        if (location == -1)
            return register;
        else
            return "local[" + location + "]";
    }

    public void print()
    {
        System.out.println(identifier + "[" + startPoint + " , " + endPoint + "], location: " + location);
        System.out.println("Register: " + register + ", location: " + location);
        int i;

        for (i = 0; i < startPoint; i++)
            System.out.print(".");

        for (i = startPoint - 1; i < endPoint; i++)
            System.out.print("+");

        System.out.println();
    }
}



class LiveIntervals
{
    public List<LiveInterval> liveIntervals;

    public LiveIntervals(List<LiveInterval> liveIntervals) { this.liveIntervals = liveIntervals; }
    public LiveInterval get_liveInterval(int i) { return liveIntervals.get(i); }
    public int size() { return liveIntervals.size(); }

    public void print()
    {
        for (LiveInterval liveInterval : liveIntervals)
            liveInterval.print();
    }

    public void sortBy_increasingStart()
    {
        liveIntervals.sort((interval1, interval2) -> interval1.startPoint < interval2.startPoint ? -1 : 1);
    }

    public LiveInterval get_registerAllocation(int line, String identifier)
    {
        for (LiveInterval liveInterval : liveIntervals) 
        {
            if (inRange(liveInterval, line) && liveInterval.identifier.equals(identifier))
                return liveInterval;
        }

        return null;
    }

    private boolean inRange(LiveInterval liveInterval, int line)
    {
        return (liveInterval.startPoint <= line && line <= liveInterval.endPoint + 1);
    }
}



class RegisterAllocator 
{
    public String[] registers = {"$s0", "$s1", "$s2", "$s3", "$s4", "$s5", "$s6", "$s7",
                                 "$t0", "$t1", "$t2", "$t3", "$t4", "$t5", "$t6", "$t7", "$t8"};

    public int R = registers.length;

    VFunction vaporFunction;
    LiveIntervals liveIntervals;

    List<String> freeRegisters_pool;
    List<LiveInterval> active;
    int stackLocation;

    public RegisterAllocator(VFunction function, LiveIntervals intervals)
    {
        vaporFunction = function;
        liveIntervals = intervals;

        //active ← {}
        active = new ArrayList<>();

        freeRegisters_pool = new ArrayList<>();
        freeRegisters_pool.addAll(Arrays.asList(registers));

        stackLocation = 0;
    }

    public LiveInterval getAllocation(int line, String identifier)
    {
        return liveIntervals.get_registerAllocation(line, identifier);
    }

    public void print()
    {
        for (LiveInterval liveInterval : liveIntervals.liveIntervals) 
            liveInterval.print();
    }

    private int length(List<LiveInterval> active) { return active.size(); }

    public void LinearScanRegisterAllocation()
    {
        //foreach live interval i, in order of increasing start point
        liveIntervals.sortBy_increasingStart();
        for (LiveInterval i : liveIntervals.liveIntervals) 
        {
            //ExpireOldIntervals(i)
            ExpireOldIntervals(i);

            //if length(active) = R then
            if (length(active) == R)
                SpillAtInterval(i); //SpillAtInterval(i)
            else
            {
                //register[i] ← a register removed from pool of free registers
                i.register = getRegisterFromPool();

                //add i to active, sorted by increasing end point
                active.add(i);
                active.sort((liveInterval1, liveInterval2) -> liveInterval1.endPoint < liveInterval2.endPoint ? -1 : 1);
            }
        }
    }

    private void ExpireOldIntervals(LiveInterval i)
    {
        List<LiveInterval> expiredIntervals = new ArrayList<>();

        //foreach interval j in active, in order of increasing end point
        active.sort((liveInterval1, liveInterval2) -> liveInterval1.endPoint < liveInterval2.endPoint ? -1 : 1);
        for (LiveInterval j : active) 
        {
            //if endpoint[j] ≥ startpoint[i] then
            if (j.endPoint >= i.startPoint)
            {
                removeExpiredIntervals(expiredIntervals);
                return; //return
            }

            //add register[j] to pool of free registers
            addRegister_freePool(j.register);

            //remove j from active
            expiredIntervals.add(j);
        }

        removeExpiredIntervals(expiredIntervals);
    }

    private void SpillAtInterval(LiveInterval i)
    {
        //spill ← last interval in active
        LiveInterval spill = active.get(active.size() - 1);

        //if endpoint[spill] > endpoint[i] then
        if (spill.endPoint > i.endPoint)
        {
            //register[i] ← register[spill]
            i.register = spill.register;
            
            //location[spill] ← new stack location
            spill.location = ++stackLocation;

            //remove spill from active
            active.remove(spill);

            //add i to active, sorted by increasing end point
            active.add(i);
            active.sort((liveInterval1, liveInterval2) -> liveInterval1.endPoint < liveInterval2.endPoint ? -1 : 1);
        }
        else
        {
            //location[i] ← new stack location
            i.location = ++stackLocation;
        }
    }

    private String getRegisterFromPool()
    {
        String register = freeRegisters_pool.get(0);
        freeRegisters_pool.remove(0);

        return register;
    }

    private void removeExpiredIntervals(List<LiveInterval> expiredIntervals)
    {
        for (LiveInterval liveInterval : expiredIntervals) 
            active.remove(liveInterval);
    }

    private void addRegister_freePool(String register)
    {
        if (freeRegisters_pool.size() > R)
        {
            System.out.println("The free pool of registers is larger than available register");
            System.out.println("Free pool of registers size: " + freeRegisters_pool.size());
            System.out.println("Available registers: " + R);
        }

        freeRegisters_pool.add(register);
        freeRegisters_pool.sort((register1, register2) -> registerIndex(register1) < registerIndex(register2) ? -1 : 1);
    }

    private int registerIndex(String register)
    {
        int i = 0;
        for (; i < R; i++) 
        {
            if (registers[i].equals(register))
                return i;
        }

        return -1;
    }
}



class VaporFunctionVisitor <E extends Throwable> extends Visitor<E>
{
    VFunction vaporFunction;
    List<CFG_Node> controlFlowGraph;

    public void set_vaporFunction(VFunction vFunction)
    {
        vaporFunction = vFunction;
        controlFlowGraph = new ArrayList<>();

        for (int i = 0; i < vaporFunction.body.length + vaporFunction.labels.length; i++) 
        {
            CFG_Node cfgNode = new CFG_Node(i);

            if (i != (vaporFunction.body.length + vaporFunction.labels.length) - 1)
                cfgNode.add_singleSucc(i);

            controlFlowGraph.add(cfgNode);
        }
    }

    public void dump()
    {
        System.out.println(vaporFunction.ident);

        for (CFG_Node cfgNode : controlFlowGraph) 
        {
            System.out.println("node " + cfgNode.index + ": ");
            cfgNode.print();
        }
    }

    public void print()
    {
        System.out.println(vaporFunction.ident);

        for (CFG_Node cfgNode : controlFlowGraph) 
        {
            for (int succ : cfgNode.succ) 
                System.out.println(cfgNode.index + " -> " + succ);
        }
    }

    public int getPosition(int sourcePosition)
    {
        return (sourcePosition - vaporFunction.sourcePos.line) - 1;
    }

    public CFG_Node get_cfgNode(int i)
    {
        for (CFG_Node cfgNode : controlFlowGraph) 
        {
            if (cfgNode.index == i)
                return cfgNode;    
        }

        return null;
    }

    //ALGORITHM 10.4. Computation of liveness by iteration.
    private void computeLiveness_byIteration()
    {
        CFG_Node functionHeader = new CFG_Node(-1);

        for (int i = 0; i < vaporFunction.params.length; i++) 
            functionHeader.def.add(vaporFunction.params[i].ident);

        functionHeader.add_singleSucc(-1);
        controlFlowGraph.add(0, functionHeader);

        do 
        {
            for (CFG_Node cfgNode : controlFlowGraph)
            {
                //in′[n] ← in[n];
                cfgNode.inPrime = new TreeSet<>();
                cfgNode.inPrime.addAll(cfgNode.in);

                //out′[n] ← out[n]
                cfgNode.outPrime = new TreeSet<>();
                cfgNode.outPrime.addAll(cfgNode.out);

                //out[n] − def [n]
                SortedSet<String> setDifference = new TreeSet<>(cfgNode.out);
                setDifference.removeAll(cfgNode.def);

                //in[n] ← use[n] ∪ (out[n] − def [n]) = in[n] ← use[n] ∪ setDifference
                SortedSet<String> in = new TreeSet<>(cfgNode.use);
                in.addAll(setDifference);
                cfgNode.in = new TreeSet<>();
                cfgNode.in.addAll(in);

                //out[n] ← (U s E succ[n]) in[s]
                SortedSet<String> out = new TreeSet<>(cfgNode.out);
                for (int i = 0; i < cfgNode.succ.size(); i++) 
                {
                    out.addAll(get_cfgNode(cfgNode.succ.get(i)).in);
                }
                cfgNode.out= new TreeSet<>();
                cfgNode.out.addAll(out);
            }
        } 
        while (!reached_fixedPoint());
    }

    private boolean reached_fixedPoint()
    {
        for (CFG_Node cfgNode : controlFlowGraph) 
        {
            if (!(cfgNode.inPrime.equals(cfgNode.in) && cfgNode.outPrime.equals(cfgNode.out)))
                return false;
        }

        return true;
    }

    public LiveInterval get_liveInterval(String identifier, List<LiveInterval> liveIntervals)
    {
        for (LiveInterval liveInterval : liveIntervals) 
        {
            if (liveInterval.identifier.equals(identifier))
                return liveInterval;
        }

        return null;
    }

    public LiveIntervals get_liveIntervals()
    {
        List<LiveInterval> finalIntervals = new ArrayList<>();
        List<LiveInterval> incompleteIntervals = new ArrayList<>();

        computeLiveness_byIteration();

        //active[n] ← in[n] ∪ def[n]
        for (CFG_Node cfgNode : controlFlowGraph) 
        {
            cfgNode.active = new TreeSet<>();
            cfgNode.active.addAll(cfgNode.def);
        }

        for (CFG_Node cfgNode : controlFlowGraph)
        {
            for (String identifier : cfgNode.active) 
            {
                LiveInterval tempInterval = get_liveInterval(identifier, incompleteIntervals);

                if (tempInterval != null)
                    tempInterval.endPoint++;
                else
                    incompleteIntervals.add(new LiveInterval(cfgNode.index, cfgNode.index, identifier));
            }

            LiveInterval temp;
            for (int i = 0; i < incompleteIntervals.size(); i++) 
            {
                temp = incompleteIntervals.get(i);
                finalIntervals.add(temp);
                incompleteIntervals.remove(temp);
            }
        }

        for (LiveInterval liveInterval : incompleteIntervals) 
        {
            liveInterval.endPoint++;
            finalIntervals.add(liveInterval);
        }

        List<LiveInterval> clone_liveIntervals = new ArrayList<>();

        for (int i = 0; i < finalIntervals.size(); i++) 
        {
            LiveInterval liveInterval1 = finalIntervals.get(i);

            for (int j = i; j < finalIntervals.size(); j++) 
            {
                LiveInterval liveInterval2 = finalIntervals.get(j);

                if (liveInterval1 != liveInterval2 && liveInterval1.identifier.equals(liveInterval2.identifier))
                {
                    liveInterval1.startPoint = Math.min(liveInterval1.startPoint, liveInterval2.startPoint);
                    liveInterval1.endPoint = Math.max(liveInterval1.endPoint, liveInterval2.endPoint) + 1;
                    clone_liveIntervals.add(liveInterval2);
                }
            }
        }

        for (LiveInterval clone : clone_liveIntervals) 
            finalIntervals.remove(clone);

        return new LiveIntervals(finalIntervals);
    }

    public void visit(VAssign n) throws E
    {
        CFG_Node cfgNode = get_cfgNode(getPosition(n.sourcePos.line));

        cfgNode.def.add(n.dest.toString());

        if (n.source instanceof VVarRef)
            cfgNode.use.add(n.source.toString());

        cfgNode.add_singleSucc(getPosition(n.sourcePos.line));
    }

    public void visit(VCall n) throws E
    {
        CFG_Node cfgNode = get_cfgNode(getPosition(n.sourcePos.line));

        cfgNode.def.add(n.dest.toString());

        for (int i = 0; i < n.args.length; i++) 
        {
            if (n.args[i] instanceof VVarRef)
                cfgNode.use.add(n.args[i].toString());
        }

        cfgNode.use.add(n.addr.toString());

        cfgNode.add_singleSucc(getPosition(n.sourcePos.line));
    }

    public void visit(VBuiltIn n) throws E
    {
        CFG_Node cfgNode = get_cfgNode(getPosition(n.sourcePos.line));

        if (n.dest != null)
            cfgNode.def.add(n.dest.toString());

        for (int i = 0; i < n.args.length; i++) 
        {
            if (n.args[i] instanceof VVarRef)
                cfgNode.use.add(n.args[i].toString());
        }

        cfgNode.add_singleSucc(getPosition(n.sourcePos.line));
    }

    public void visit(VMemWrite n) throws E
    {
        CFG_Node cfgNode = get_cfgNode(getPosition(n.sourcePos.line));

        VMemRef.Global globalMemoryReference = (VMemRef.Global)n.dest;
        cfgNode.use.add(globalMemoryReference.base.toString());
        // if (n.dest instanceof VMemRef.Global && (((VMemRef.Global) n.dest).base instanceof VAddr.Var) )
        //     cfgNode.use.add(((VMemRef.Global) n.dest).base.toString());

        if (n.source instanceof VVarRef)
            cfgNode.use.add(n.source.toString());

        cfgNode.add_singleSucc(getPosition(n.sourcePos.line));
    }

    public void visit(VMemRead n) throws E
    {
        CFG_Node cfgNode = get_cfgNode(getPosition(n.sourcePos.line));

        cfgNode.def.add(n.dest.toString());

        VMemRef.Global globalMemoryReference = (VMemRef.Global)n.source;
        cfgNode.use.add(globalMemoryReference.base.toString());
        // if (n.dest instanceof VMemRef.Global && (((VMemRef.Global) n.dest).base instanceof VAddr.Var) )
        //     cfgNode.use.add(((VMemRef.Global) n.dest).base.toString());

        cfgNode.add_singleSucc(getPosition(n.sourcePos.line));
    }

    public void visit(VBranch n) throws E 
    {
        CFG_Node cfgNode = get_cfgNode(getPosition(n.sourcePos.line));
        cfgNode.use.add(n.value.toString());
        cfgNode.add_singleSucc(getPosition(n.sourcePos.line));
        cfgNode.succ.add(getPosition(n.target.getTarget().sourcePos.line));
    }

    public void visit(VGoto n) throws E
    {
        CFG_Node cfgNode = get_cfgNode(getPosition(n.sourcePos.line));
        cfgNode.succ.add(getPosition(((VAddr.Label) n.target).label.getTarget().sourcePos.line));
    }

    public void visit(VReturn n) throws E
    {
        CFG_Node cfgNode = get_cfgNode(getPosition(n.sourcePos.line));

        if (n.value instanceof VVarRef)
            cfgNode.use.add(n.value.toString());
    }
}



class VaporVisitor <E extends Throwable> extends Visitor<E>
{
    public RegisterAllocator registerAllocator;
    public VFunction vaporFunction;
    public List<String> buffer;
    public List<String> usedSXRegs;
    public List<String> usedTXRegs;
    public int indentLevel;
    public int outCount;

    public VaporVisitor() 
    {
        buffer = new ArrayList<>();
    }

    public void setBuffer(int pos, String str) 
    {
        StringBuilder strBuilder = new StringBuilder(str);

        for (int i = 0; i < indentLevel * 4; i++)
            strBuilder.insert(0, " ");

        str = strBuilder.toString();

        if (str.charAt(str.length() - 1) != '\n')
            buffer.set(pos, str + "\n");
        else
            buffer.set(pos, str);
    }

    public void printBuffer() 
    {
        for (String line : buffer)
            System.out.print(line);

        System.out.println();
    }

    public void insertLabels() 
    {
        for (int i = 0; i < vaporFunction.labels.length; i++) 
        {
            int pos = getRelativePos(vaporFunction.labels[i].sourcePos.line);
            setBuffer(pos, vaporFunction.labels[i].ident + ":\n");
        }

        // Set up function header
        //StringBuilder funcHeader = new StringBuilder();

        int inCount = Math.max(vaporFunction.params.length - 4, 0);

        // Save all $sx  registers
        int sxCount = usedSXRegs.size();
        int txCount = usedTXRegs.size();

        StringBuilder funcHeader = new StringBuilder("func " + vaporFunction.ident + " [in " + inCount + ", out " + outCount + ", local " + (sxCount+txCount) + "]\n");

        // Save $sx registers
        int stackLoc = 0;
        for (String reg : usedSXRegs) 
        {
            funcHeader.append("local[").append(stackLoc).append("] = ").append(reg).append("\n");
            stackLoc++;
        }

        // Unload argument registers onto local variables
        int argRegsUsed = 0;
        for (int i = 0; i < vaporFunction.params.length; i++) 
        {
            LiveInterval currArg = registerAllocator.getAllocation(-1, vaporFunction.params[i].ident);

            if (argRegsUsed < inCount + 1 || inCount == 0) 
            {
                if (currArg != null)
                    funcHeader.append(currArg.getLocation()).append(" = ").append("$a").append(i).append("\n");

                argRegsUsed++;
            } 
            else
                funcHeader.append(currArg.getLocation()).append(" = ").append("in[").append(i-argRegsUsed).append("]\n");
        }

        buffer.add(0, funcHeader.toString());
    }

    public void setData(VFunction vaporFunction, RegisterAllocator registerAllocator) 
    {
        this.vaporFunction = vaporFunction;
        this.registerAllocator = registerAllocator;

        usedSXRegs = new ArrayList<>();
        for (LiveInterval lr : registerAllocator.liveIntervals.liveIntervals) 
        {
            if (lr.getLocation().contains("s") && !usedSXRegs.contains(lr.getLocation())) 
                usedSXRegs.add(lr.getLocation());
        }

        usedTXRegs = new ArrayList<>();
        for (LiveInterval tr : registerAllocator.liveIntervals.liveIntervals) 
        {
            if (tr.getLocation().contains("t") && !usedTXRegs.contains(tr.getLocation())) 
                usedTXRegs.add(tr.getLocation());
        }

        outCount = 0;

        // Set up buffer
        buffer = new ArrayList<>();
        for (int i = 0; i <= vaporFunction.body.length + vaporFunction.labels.length; i++)
            buffer.add("");
    }

    public int getRelativePos(int sourcePos) 
    {
        return (sourcePos - vaporFunction.sourcePos.line) - 1;
    }

    public void visit(VAssign a) throws E 
    {
        int sourcePos = getRelativePos(a.sourcePos.line);
        LiveInterval destAlloc = registerAllocator.getAllocation(sourcePos, a.dest.toString());
        LiveInterval srcAlloc = registerAllocator.getAllocation(sourcePos, a.source.toString());

        StringBuilder line = new StringBuilder();

        if (destAlloc != null) 
            line.append(destAlloc.getLocation());

        line.append(" = ");

        if (srcAlloc != null) 
            line.append(srcAlloc.getLocation());
        else 
            line.append(a.source.toString());

        setBuffer(sourcePos, line.toString());
    }

    public void visit(VCall c) throws E 
    {
        int sourcePos = getRelativePos(c.sourcePos.line);
        LiveInterval destAlloc = registerAllocator.getAllocation(sourcePos, c.dest.toString());

        StringBuilder line = new StringBuilder();

        // Save usedTs
        for (int i = 0; i < usedTXRegs.size(); i++)
            line.append("local[" + (usedSXRegs.size() + i) + "] = " + usedTXRegs.get(i) + "\n");

        // Set up arguments
        int argRegUsed = 0;
        for (int i = 0; i < c.args.length; i++) 
        {
            if (argRegUsed < 4) 
            {
                if (c.args[i] instanceof VVarRef) 
                {
                    LiveInterval currArgAlloc = registerAllocator.getAllocation(sourcePos, c.args[i].toString());
                    if (currArgAlloc != null)
                        line.append("$a" + i + " = " + currArgAlloc.getLocation() + "\n");
                } 
                else if (c.args[i] instanceof VOperand.Static) 
                    line.append("$a" + i + " = " + c.args[i].toString() + "\n");
                else if (c.args[i] instanceof VLitStr) 
                    line.append("\"" + ((VLitStr) c.args[i]).value + "\"");

                argRegUsed++;
            } 
            else 
            {
                if (c.args[i] instanceof VVarRef) 
                {
                    LiveInterval currArgAlloc = registerAllocator.getAllocation(sourcePos, c.args[i].toString());
                    if (currArgAlloc != null)
                        line.append("out[" + outCount + "] = " + currArgAlloc.getLocation() + "\n");
                } 
                else if (c.args[i] instanceof VOperand.Static) 
                    line.append("out[" + outCount + "] = " + c.args[i].toString() + "\n");
                else if (c.args[i] instanceof VLitStr) 
                    line.append("\"" + ((VLitStr) c.args[i]).value + "\"");

                outCount++;
            }
        }

        if (c.addr instanceof VAddr.Label) 
            line.append("call :" + ((VAddr.Label<VFunction>) c.addr).label.ident + "\n");
        else 
        {
            LiveInterval addrAlloc = registerAllocator.getAllocation(sourcePos, c.addr.toString());
            line.append("call " + addrAlloc.getLocation() + "\n");
        }

        // Restore Ts
        for (int i = 0; i < usedTXRegs.size(); i++) 
            line.append(usedTXRegs.get(i) + " = local[" + (usedSXRegs.size() + i) + "]\n");

        // Get the return value
        if (destAlloc != null)
            line.append(destAlloc.getLocation());

        line.append(" = $v0");

        setBuffer(sourcePos, line.toString());
    }

    public void visit(VBuiltIn c) throws E 
    {
        int sourcePos = getRelativePos(c.sourcePos.line);

        StringBuilder line = new StringBuilder();

        // Sometimes BuiltIn does not have a dest
        if (c.dest != null) 
        {
            LiveInterval destAlloc = registerAllocator.getAllocation(sourcePos, c.dest.toString());

            if (destAlloc != null) 
                line.append(destAlloc.getLocation());

            line.append(" = ");
        }

        line.append(c.op.name + "(");

        for (int i = 0; i < c.args.length; i++) 
        {
            if (i != 0) 
                line.append(" ");

            if (c.args[i] instanceof VVarRef) 
            {
                LiveInterval currArgAlloc = registerAllocator.getAllocation(sourcePos, c.args[i].toString());

                if (currArgAlloc != null)
                    line.append(currArgAlloc.getLocation());
            } 
            else if (c.args[i] instanceof VOperand.Static) 
                line.append(c.args[i].toString());
            else if (c.args[i] instanceof VLitStr)
                line.append("\""+ ((VLitStr)c.args[i]).value + "\"");
        }

        line.append(")");

        setBuffer(sourcePos, line.toString());
    }

    public void visit(VMemWrite w) throws E 
    {
        int sourcePos = getRelativePos(w.sourcePos.line);
        LiveInterval destAlloc = registerAllocator.getAllocation(sourcePos, ((VMemRef.Global) w.dest).base.toString());
        LiveInterval srcAlloc = registerAllocator.getAllocation(sourcePos, w.source.toString());

        StringBuilder line = new StringBuilder();

        if (destAlloc != null) 
            line.append("[" + destAlloc.getLocation() + "+" + ((VMemRef.Global) w.dest).byteOffset + "]");

        line.append(" = ");

        if (srcAlloc != null) 
            line.append(srcAlloc.getLocation());
        else 
            line.append(w.source.toString());

        setBuffer(sourcePos, line.toString());
    }

    public void visit(VMemRead r) throws E 
    {
        int sourcePos = getRelativePos(r.sourcePos.line);
        LiveInterval destAlloc = registerAllocator.getAllocation(sourcePos, r.dest.toString());
        LiveInterval srcAlloc = registerAllocator.getAllocation(sourcePos, ((VMemRef.Global) r.source).base.toString());

        StringBuilder line = new StringBuilder();

        if (destAlloc != null)
            line.append(destAlloc.getLocation());

        line.append(" = ");

        if (srcAlloc != null) 
            line.append("[" + srcAlloc.getLocation() + "+" + ((VMemRef.Global) r.source).byteOffset + "]");
        else
            line.append("[" +r.source.toString() + "]");

        setBuffer(sourcePos, line.toString());
    }

    public void visit(VBranch b) throws E 
    {
        int sourcePos = getRelativePos(b.sourcePos.line);
        LiveInterval destAlloc = registerAllocator.getAllocation(sourcePos, b.value.toString());

        StringBuilder line;

        if (destAlloc != null) 
        {
            line = new StringBuilder();

            if (b.positive) 
                line.append("if " + destAlloc.getLocation() + " goto :" + b.target.ident);
            else 
                line.append("if0 " + destAlloc.getLocation() + " goto :" + b.target.ident);

            setBuffer(sourcePos, line.toString());
        }
    }

    public void visit(VGoto g) throws E 
    {
        int sourcePos = getRelativePos(g.sourcePos.line);

        setBuffer(sourcePos, "goto " + g.target.toString());
    }

    public void visit(VReturn r) throws E 
    {
        int sourcePos = getRelativePos(r.sourcePos.line);

        StringBuilder retString = new StringBuilder();

        if (r.value != null) 
        {
            if (r.value instanceof VVarRef) 
            {
                LiveInterval retAlloc = registerAllocator.getAllocation(sourcePos, r.value.toString());
                if (retAlloc != null)
                    retString.append("$v0 = ").append(retAlloc.getLocation()).append("\n");
            } 
            else if (r.value instanceof VOperand.Static) 
                retString.append("$v0 = ").append(r.value.toString()).append("\n");
        }

        // Restore used sx registers
        int count = 0;
        for (String reg : usedSXRegs) 
        {
            retString.append(reg).append(" = local[").append(count).append("]\n");
            count++;
        }

        retString.append("ret");

        setBuffer(sourcePos, retString.toString());

        // Since this is the end of the function, insert
        // CF labels. This also set function header
        insertLabels();
    }
}



public class V2VM
{
    public static void main(String[] args) throws ProblemException, IOException 
    {
        VaporProgram vapProgAST = parseVapor(System.in, System.err);

        List<LiveIntervals> liveIntervals = new ArrayList<>();
        List<RegisterAllocator> registerAllocators = new ArrayList<>();
        List<List<String>> vaporMcode = new ArrayList<>();

        VaporFunctionVisitor<Exception> vaporFunctionVisitor = new VaporFunctionVisitor<>();
        VaporVisitor<Exception> vaporVisitor = new VaporVisitor<>();

        //print const data for class
        for (int i = 0; i < vapProgAST.dataSegments.length; i++) 
        {
            System.out.println("const " + vapProgAST.dataSegments[i].ident);

            for (int j = 0; j < vapProgAST.dataSegments[i].values.length; j++) 
                System.out.println("  " + vapProgAST.dataSegments[i].values[j]);

            System.out.println();
        }

        try 
        {
            //for each function in the vapor program AST
            for (int i = 0; i < vapProgAST.functions.length; i++) 
            {
                VFunction vaporFunction = vapProgAST.functions[i];

                vaporFunctionVisitor.set_vaporFunction(vaporFunction);

                //create CFG and calculate liveness
                for (int j = 0; j < vaporFunction.body.length; j++) 
                    vapProgAST.functions[i].body[j].accept(vaporFunctionVisitor);

                //vaporFunctionVisitor.print();//TODO
                //vaporFunctionVisitor.dump();//TODO

                liveIntervals.add(vaporFunctionVisitor.get_liveIntervals());

                //perform register allocation using linear search 
                RegisterAllocator registerAllocator = new RegisterAllocator(vaporFunction, liveIntervals.get(i));
                registerAllocator.LinearScanRegisterAllocation();
                registerAllocators.add(registerAllocator);

                //registerAllocator.print(); //TODO: bug free

                //convert vapor code to vaporM code
                vaporVisitor.setData(vaporFunction, registerAllocators.get(i));

                for (int j = 0; j <  vaporFunction.body.length; j++) 
                    vapProgAST.functions[i].body[j].accept(vaporVisitor);

                //vaporVisitor.printBuffer();//TODO: not reached

                vaporMcode.add(vaporVisitor.buffer);
            }
        
            print_vaporMcode(vaporMcode);
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
        }
    }

    public static VaporProgram parseVapor(InputStream in, PrintStream err) throws IOException 
    {
        Op[] ops = { Op.Add, Op.Sub, Op.MulS, Op.Eq, Op.Lt, Op.LtS, Op.PrintIntS, Op.HeapAllocZ, Op.Error };
        boolean allowLocals = true;
        String[] registers = null;
        boolean allowStack = false;

        VaporProgram tree;
        try 
        {
            tree = VaporParser.run(new InputStreamReader(in), 1, 1, Arrays.asList(ops), allowLocals, registers, allowStack);
        }
        catch (Exception ex) 
        {
            err.println(ex.getMessage());
            return null;
        }
    
        return tree;
    }

    public static void print_vaporMcode(List<List<String>> vaporMcode)
    {
        for (List<String> code : vaporMcode) 
        {
            for (String c  : code) 
                System.out.println(c);

            System.out.println();
        }
    }
}
