import cs132.vapor.ast.*;
import java.util.*;
import java.util.stream.*;
import java.io.PrintStream.*;

import cs132.util.ProblemException;
import cs132.vapor.parser.VaporParser;
import cs132.vapor.ast.VaporProgram;
import cs132.vapor.ast.VBuiltIn.Op;

import cs132.util.*;
import cs132.vapor.ast.*;
import cs132.vapor.parser.*;
import cs132.vapor.ast.VInstr.Visitor;

import java.io.*;
//import java.util.*;

class Register 
{
    // Callee-saved
    public static final Register s0 = new Register("s0");
    public static final Register s1 = new Register("s1");
    public static final Register s2 = new Register("s2");
    public static final Register s3 = new Register("s3");
    public static final Register s4 = new Register("s4");
    public static final Register s5 = new Register("s5");
    public static final Register s6 = new Register("s6");
    public static final Register s7 = new Register("s7");
    
    // Caller-saved
    public static final Register t0 = new Register("t0");
    public static final Register t1 = new Register("t1");
    public static final Register t2 = new Register("t2");
    public static final Register t3 = new Register("t3");
    public static final Register t4 = new Register("t4");
    public static final Register t5 = new Register("t5");
    public static final Register t6 = new Register("t6");
    public static final Register t7 = new Register("t7");
    public static final Register t8 = new Register("t8");
    
    // Argument passing
    public static final Register a0 = new Register("a0");
    public static final Register a1 = new Register("a1");
    public static final Register a2 = new Register("a2");
    public static final Register a3 = new Register("a3");
    
    // Return value/Temporary loading
    public static final Register v0 = new Register("v0");
    public static final Register v1 = new Register("v1");
    
    private final String reg;
    
    private Register(String r) 
    {
        reg = r;
    }
    
    public boolean isCallerSaved() 
    {
        return reg.startsWith("t");
    }
    
    public boolean isCalleeSaved() 
    {
        return reg.startsWith("s");
    }
    
    public boolean isArgumentPassing() 
    {
        return reg.startsWith("a");
    }
    
    public boolean isReturnOrLoading() 
    {
        return reg.startsWith("v");
    }
    
    @Override
    public String toString() 
    {
        return "$" + reg;
    }
    
    @Override
    public int hashCode() 
    {
        return reg.hashCode();
    }
    
    @Override
    public boolean equals(Object obj) 
    {
        if (obj == null || !(obj instanceof Register))
            return false;
    
        Register rhs = (Register) obj;
        return reg.equals(rhs.reg);
    }
}



class RegisterPool 
{
    private Set<Register> all = new LinkedHashSet<>();
    private Set<Register> use = new HashSet<>();
    
    private RegisterPool(Register[] regs) 
    {
        Collections.addAll(all, regs);
    }
    
    // We only use t0~t7 and s0~s7. a0~a3, v0 and v1 are reserved.
    public static RegisterPool CreateGlobalPool() 
    {
        Register[] regs = 
        {
            // Caller-saved
            Register.t0, Register.t1, Register.t2, Register.t3,
            Register.t4, Register.t5, Register.t6, Register.t7,
            Register.t8,
            // Callee-saved
            Register.s0, Register.s1, Register.s2, Register.s3,
            Register.s4, Register.s5, Register.s6, Register.s7
        };
    
        return new RegisterPool(regs);
    }
    
    // Local pool used for retrieving values from `local` stack
    public static RegisterPool CreateLocalPool() 
    {
        Register[] regs = 
        {
            Register.v0, Register.v1,
            Register.a0, Register.a1, Register.a2, Register.a3
        };
    
        return new RegisterPool(regs);
    }
    
    public boolean contains(Register reg) 
    {
        return all.contains(reg);
    }
    
    public boolean inUse(Register reg) 
    {
        return use.contains(reg);
    }
    
    public boolean hasFree() 
    {
        return all.size() > use.size();
    }
    
    public Register acquire() 
    {
        Register ret = null;

        Set<Register> diff = new LinkedHashSet<>(all);
        diff.removeAll(use);
    
        if (!diff.isEmpty()) 
        {
            ret = diff.iterator().next();
            use.add(ret);
        }
            
        return ret;
    }
    
    public void release(Register reg) 
    {
        use.remove(reg);
    }
}



class AllocationMap 
{
    private final Map<String, Register> register;
    private final List<String> stack;
    private final int stackReserved;

    public AllocationMap(Map<String, Register> r, String[] s) 
    {
        register = r;
        stack = Arrays.asList(s);
        stackReserved = usedCalleeRegister().size();
    }

    public List<Register> usedCalleeRegister() 
    {
        return register.values().stream().filter(Register::isCalleeSaved).distinct().collect(Collectors.toList());
    }

    public Register lookupRegister(String s) 
    {
        return register.getOrDefault(s, null);
    }

    public int lookupStack(String s) 
    {
        int offset = stack.indexOf(s);
        return offset == -1 ? -1 : offset + stackReserved;
    }

    public int stackSize() 
    {
        return stack.size() + stackReserved;
    }
}



class Interval 
{
    private final String var;
    private int start;
    private int end;
    
    public Interval(String v, int s, int e) 
    {
        var = v;
        start = s;
        end = e;
    }
    
    public int getStart() 
    {
        return start;
    }
    
    public int getEnd() 
    {
        return end;
    }
    
    public void setEnd(int e) 
    {
        end = e;
    }
    
    public String getVar() 
    {
        return var;
    }
}



class Liveness 
{
    private final List<Set<String>> in;
    private final List<Set<String>> out;
    private final List<Set<String>> def;
    private final List<Set<String>> use;
    
    public Liveness(List<Set<String>> lsi, List<Set<String>> lso, List<Set<String>> lsd, List<Set<String>> lsu) 
    {
        in = lsi;
        out = lso;
        def = lsd;
        use = lsu;
    }
    
    public List<Set<String>> getIn() 
    {
        return new ArrayList<>(in);
    }
    
    public List<Set<String>> getOut() 
    {
        return new ArrayList<>(out);
    }
    
    public List<Set<String>> getDef() 
    {
        return new ArrayList<>(def);
    }
        
    public List<Set<String>> getUse() 
    {
        return new ArrayList<>(use);
    }
}



class Allocator 
{
    private RegisterPool pool;
    private List<Interval> active;
    private Map<String, Register> register;
    private Set<String> unusedParams;
    private Set<String> stack;

    public AllocationMap computeAllocation(List<Interval> ci, VVarRef.Local[] params) 
    {
        pool = RegisterPool.CreateGlobalPool();
        active = new ArrayList<>();
        register = new LinkedHashMap<>();
        unusedParams = new HashSet<>();
        stack = new LinkedHashSet<>();

        List<Interval> intervals = new ArrayList<>(ci);

        // Sort by increasing start point
        intervals.sort(Comparator.comparingInt(Interval::getStart));

        // Map params to registers (in a0~a3 and `in` stack)
        for (int i = 0; i < params.length; i++) 
        {
            String arg = params[i].ident;

            // If parameter is used during the function
            if (intervals.stream().map(Interval::getVar).anyMatch(o -> o.equals(arg))) 
            {
                if (pool.hasFree()) 
                {
                    // For those args that are not able to be put into registers,
                    // we move them into `local` stack later (by spilling them).
                    register.put(arg, pool.acquire());
                    unusedParams.add(arg);
                }
            }
        }

        for (Interval i : intervals) 
        {
            expireOldInterval(i);

            // No need to allocate registers for the first parameters
            if (i.getStart() > 0 || !unusedParams.contains(i.getVar())) 
            {
                if (!pool.hasFree()) 
                {
                    spillAtInterval(i);
                } 
                else 
                {
                    register.put(i.getVar(), pool.acquire());
                    active.add(i);
                }
            }
        }

        return new AllocationMap(new LinkedHashMap<>(register), stack.toArray(new String[stack.size()]));
    }

    private void expireOldInterval(Interval interval) 
    {
        // Sort by increasing end point
        active.sort(Comparator.comparingInt(Interval::getEnd));

        for (Iterator<Interval> iter = active.iterator(); iter.hasNext();) 
        {
            Interval i = iter.next();

            if (i.getEnd() >= interval.getStart())
                return;

            iter.remove();
            pool.release(register.get(i.getVar()));

            // release the interval of first parameters
            if (unusedParams.contains(i.getVar()))
                unusedParams.remove(i.getVar());
        }
    }

    private void spillAtInterval(Interval interval) 
    {
        // Sort by increasing end point
        active.sort(Comparator.comparingInt(Interval::getEnd));

        // Intervals for function parameters are marked as fixed. (They are not spilled)
        Interval spill = null;

        if (!active.isEmpty()) 
        {
            int idx = active.size() - 1;

            do 
            {
                spill = active.get(idx--);
            } 
            while (idx >= 0 && unusedParams.contains(spill.getVar()));
            
            spill = idx < 0 ? null : spill;
        }

        if (spill != null && spill.getEnd() > interval.getEnd()) 
        {
            register.put(interval.getVar(), register.get(spill.getVar()));
            register.remove(spill.getVar());
            stack.add(spill.getVar());
            active.remove(spill);
            active.add(interval);
        } 
        else 
        {
            stack.add(interval.getVar());
        }
    }
}



class Output 
{
    private static final String INDENT = "  "; // two spaces

    private String indent = "";
    private PrintStream stream;
    private boolean newLine = true;

    public Output(PrintStream s) 
    {
        stream = s;
    }

    public void increaseIndent() 
    {
        indent += INDENT;
    }

    public void decreaseIndent() 
    {
        indent = indent.substring(0, indent.length() - INDENT.length());
    }

    public void setOutputStream(PrintStream s) 
    {
        stream = s;
    }

    public void write(String s) 
    {
        stream.print((newLine ? indent : "") + s);
        newLine = false;
    }

    public void writeLine(String s) 
    {
        stream.println((newLine ? indent : "") + s);
        newLine = true;
    }

    public void writeLine() 
    {
        stream.println();
    }
}



class FlowGraphNode 
{
    private final FlowGraph graph;
    private final int index;
    
    private final VInstr instr;
    private final Set<String> def;
    private final Set<String> use;
    
    private Set<FlowGraphNode> succ = new HashSet<>();
    private Set<FlowGraphNode> pred = new HashSet<>();
    
    public FlowGraphNode(FlowGraph g, int idx, VInstr vi, Set<String> d, Set<String> u) 
    {
        graph = g;
        index = idx;
        instr = vi;
        def = d;
        use = u;
    }
    
    public FlowGraph getGraph() 
    {
        return graph;
    }
    
    public int getIndex() 
    {
        return index;
    }
    
    public Set<FlowGraphNode> getSucc() 
    {
        return new HashSet<>(succ);
    }
    
    public Set<FlowGraphNode> getPred() 
    {
        return new HashSet<>(pred);
    }
    
    public VInstr getInstr() 
    {
        return instr;
    }
    
    public Set<String> getDef() 
    {
        return new HashSet<>(def);
    }
    
    public Set<String> getUse() 
    {     
        return new HashSet<>(use);
    }
    
    public void addSuccessor(FlowGraphNode gn) 
    {
        if (gn != null && this != gn)
            succ.add(gn);
    }
    
    public void addPredecessor(FlowGraphNode gn) 
    {
        if (gn != null && this != gn)
            pred.add(gn);
    }
}
    



class FlowGraph 
{
    private List<FlowGraphNode> nodes = new ArrayList<>();
    private Map<FlowGraphNode, Set<FlowGraphNode>> edges = new HashMap<>();
    
    public FlowGraphNode newNode(VInstr instr, Set<String> def, Set<String> use) 
    {
        FlowGraphNode gn = new FlowGraphNode(this, nodes.size(), instr, def, use);
        nodes.add(gn);
            
        return gn;
    }
    
    public FlowGraphNode getNode(int index) 
    {
        return nodes.get(index);
    }
    
    public int getIndex(FlowGraphNode node) 
    {
        return nodes.indexOf(node);
    }
    
    public List<FlowGraphNode> getNodes() 
    {
        return new ArrayList<>(nodes);
    }
    
    public int nodesCount() 
    {
        return nodes.size();
    }
    
    public void addEdge(FlowGraphNode from, FlowGraphNode to) 
    {
        if (from != null && to != null && from != to && nodes.contains(from) && nodes.contains(to)) 
        {
            edges.computeIfAbsent(from, k -> new HashSet<>()).add(to);
            from.addSuccessor(to);
            to.addPredecessor(from);
        }
    }
    
    public Liveness computLiveness() 
    {
        Map<FlowGraphNode, Set<String>> in = new LinkedHashMap<>();
        Map<FlowGraphNode, Set<String>> out = new LinkedHashMap<>();
        boolean updated;
    
        for (FlowGraphNode n : nodes) 
        {
            in.put(n, new HashSet<>());
            out.put(n, new HashSet<>());
        }
    
        do 
        {
            updated = false;
    
            for (FlowGraphNode n : nodes) 
            {
                Set<String> oldin = new HashSet<>(in.get(n));
                Set<String> oldout = new HashSet<>(out.get(n));
    
                // in[n] = use[n]\/(out[n]-def[n])
                Set<String> newin = new HashSet<>(n.getUse());
                Set<String> diff = new HashSet<>(oldout);
                diff.removeAll(n.getDef());
                newin.addAll(diff);
    
                // out[n] = \/(s in succ[n]) in[s]
                Set<String> newout = new HashSet<>();
                for (FlowGraphNode s : n.getSucc())
                    newout.addAll(in.get(s));
    
                in.put(n, newin);
                out.put(n, newout);
    
                if (!newin.equals(oldin) || !newout.equals(oldout))
                    updated = true;
            }
        } 
        while (updated);
    
        return new Liveness(new ArrayList<>(in.values()), 
                            new ArrayList<>(out.values()), 
                            nodes.stream().map(FlowGraphNode::getDef).collect(Collectors.toList()), 
                            nodes.stream().map(FlowGraphNode::getUse).collect(Collectors.toList()));
    }
}



class RegAllocHelper 
{
    private RegAllocHelper() 
    {
        throw new IllegalStateException("Utility class");
    }

    public static FlowGraph generateFlowGraph(VFunction func) 
    {
        FlowGraph graph = new FlowGraph();
        List<FlowGraphNode> nodes = new ArrayList<>();

        for (VInstr instr : func.body) 
        {
            Set<String> def = new HashSet<>();
            Set<String> use = new HashSet<>();

            instr.accept(new VInstr.Visitor<RuntimeException>() 
            {
                @Override
                public void visit(VAssign vAssign) 
                {
                    def.add(vAssign.dest.toString());

                    if (vAssign.source instanceof VVarRef) 
                    {
                        use.add(vAssign.source.toString());
                    }
                }

                @Override
                public void visit(VCall vCall) 
                {
                    def.add(vCall.dest.toString());

                    if (vCall.addr instanceof VAddr.Var) 
                    {
                        use.add(vCall.addr.toString());
                    }

                    for (VOperand arg : vCall.args) 
                    {
                        if (arg instanceof VVarRef) 
                        {
                            use.add(arg.toString());
                        }
                    }
                }

                @Override
                public void visit(VBuiltIn vBuiltIn) 
                {
                    if (vBuiltIn.dest != null)
                        def.add(vBuiltIn.dest.toString());

                    for (VOperand arg : vBuiltIn.args) 
                    {
                        if (arg instanceof VVarRef) 
                        {
                            use.add(arg.toString());
                        }
                    }
                }

                @Override
                public void visit(VMemWrite vMemWrite) 
                {
                    VMemRef.Global ref = (VMemRef.Global) vMemWrite.dest;
                    use.add(ref.base.toString()); // not def but use

                    if (vMemWrite.source instanceof VVarRef) 
                    {
                        use.add(vMemWrite.source.toString());
                    }
                }

                @Override
                public void visit(VMemRead vMemRead) 
                {
                    def.add(vMemRead.dest.toString());

                    VMemRef.Global ref = (VMemRef.Global) vMemRead.source;
                    use.add(ref.base.toString());
                }

                @Override
                public void visit(VBranch vBranch) 
                {
                    if (vBranch.value instanceof VVarRef) 
                    {
                        use.add(vBranch.value.toString());
                    }

                    // the branch target is label, thus no use produced.
                }

                @Override
                public void visit(VGoto vGoto) 
                {
                    if (vGoto.target instanceof VAddr.Var) 
                    {
                        use.add(vGoto.target.toString());
                    }
                }

                @Override
                public void visit(VReturn vReturn) 
                {
                    if (vReturn.value != null) 
                    {
                        if (vReturn.value instanceof VVarRef) 
                        {
                            use.add(vReturn.value.toString());
                        }
                    }
                }
            });

            nodes.add(graph.newNode(instr, def, use));
        }

        for (int i = 0; i < func.body.length; i++) 
        {
            VInstr instr = func.body[i];
            FlowGraphNode prev = i > 0 ? nodes.get(i - 1) : null;
            FlowGraphNode cur = nodes.get(i);

            // Edge from the prev instr to current instr.
            if (prev != null)
                graph.addEdge(prev, cur);

            if (instr instanceof VBranch) 
            {
                VLabelRef<VCodeLabel> target = ((VBranch) instr).target;
                FlowGraphNode to = nodes.get(target.getTarget().instrIndex);
                graph.addEdge(cur, to);
            } 
            else if (instr instanceof VGoto) 
            {
                // For gotos, we only allow goto labels.
                VLabelRef<VCodeLabel> target = ((VAddr.Label<VCodeLabel>) ((VGoto) instr).target).label;
                FlowGraphNode to = nodes.get(target.getTarget().instrIndex);
                graph.addEdge(cur, to);
            }
        }

        return graph;
    }

    public static List<Interval> generateLiveIntervals(FlowGraph graph, Liveness liveness) 
    {
        Map<String, Interval> intervals = new HashMap<>();
        List<Set<String>> actives = new ArrayList<>();

        for (FlowGraphNode n : graph.getNodes()) 
        {
            // active[n] = def[n] \/ in[n]
            Set<String> active = new HashSet<>(n.getDef());
            active.addAll(liveness.getIn().get(n.getIndex()));
            actives.add(active);
        }

        for (int i = 0; i < actives.size(); i++) 
        {
            for (String var : actives.get(i)) 
            {
                if (intervals.containsKey(var)) // update end
                { 
                    intervals.get(var).setEnd(i);
                } 
                else // create new interval
                { 
                    intervals.put(var, new Interval(var, i, i));
                }
            }
        }

        return new ArrayList<>(intervals.values());
    }

    public static String in(int offset) 
    {
        return "in[" + Integer.toString(offset) + "]";
    }

    public static String out(int offset) 
    {
        return "out[" + Integer.toString(offset) + "]";
    }

    public static String local(int offset) 
    {
        return "local[" + Integer.toString(offset) + "]";
    }

    public static String memoryReference(Register reg, int offset) 
    {
        if (offset > 0)
            return "[" + reg.toString() + "+" + Integer.toString(offset) + "]";
        else
            return "[" + reg.toString() + "]";
    }
}




class Converter 
{
    private Output out = new Output(System.out);
    private RegisterPool localPool = RegisterPool.CreateLocalPool();

    public Output getOutput() 
    {
        return out;
    }

    public void outputConstSegment(VDataSegment[] segments) 
    {
        // Treat all data segment as const segment
        for (VDataSegment seg : segments) 
        {
            out.writeLine("const " + seg.ident);
            out.increaseIndent();

            for (VOperand.Static label : seg.values) 
            {
                out.writeLine(label.toString());
            }

            out.decreaseIndent();
            out.writeLine();
        }
    }

    public void outputAssignment(String lhs, String rhs) 
    {
        out.writeLine(lhs + " = " + rhs);
    }

    private void outputFunctionSignature(String func, int inStack, int outStack, int localStack) 
    {
        out.write("func " + func + " ");
        out.write("[in " + Integer.toString(inStack) + ", ");
        out.write("out " + Integer.toString(outStack) + ", ");
        out.writeLine("local " + Integer.toString(localStack) + "]");
    }

    private Register loadVariable(AllocationMap map, String var, boolean dst) 
    {
        Register reg = map.lookupRegister(var);

        // var in register
        if (reg != null) 
        { 
            return reg;
        } 
        else // var on `local` stack
        {
            int offset = map.lookupStack(var);
            Register load = localPool.acquire();

            if (!dst) // for dest's, they only want a register.
                outputAssignment(load.toString(), RegAllocHelper.local(offset));
            
            return load;
        }
    }

    private void writeVariable(Register reg, AllocationMap map, String var) 
    {
        int offset = map.lookupStack(var);

        if (offset != -1) 
        {
            outputAssignment(RegAllocHelper.local(offset), reg.toString());
        }
    }

    private void releaseLocalRegister(Register reg) 
    {
        if (localPool.contains(reg))
            localPool.release(reg);
    }

    public void outputFunction(VFunction func, AllocationMap map, Liveness liveness) 
    {
        List<Register> callee = map.usedCalleeRegister();

        // Map instrIndex to a label
        Map<Integer, Set<String>> labels = new HashMap<>();

        for (VCodeLabel l : func.labels)
            labels.computeIfAbsent(l.instrIndex, k -> new LinkedHashSet<>()).add(l.ident);

        int inStack = Math.max(func.params.length - 4, 0);
        int outStack = 0; // calculated later
        int localStack = map.stackSize();

        for (int i = 0; i < func.body.length; i++) 
        {
            VInstr instr = func.body[i];

            if (instr instanceof VCall) 
            {
                VCall call = (VCall) instr;
                outStack = Math.max(call.args.length - 4, outStack);

                // Only save those live-out but not def in this node.
                Set<String> liveOut = liveness.getOut().get(i);
                liveOut.removeAll(liveness.getDef().get(i));

                // For saving $t before function call.
                // $t are saved on the high address of local stack.
                int saves = (int) liveOut.stream().map(map::lookupRegister).filter(o -> o != null && o.isCallerSaved()).distinct().count();
                localStack = Math.max(localStack, map.stackSize() + saves);
            }
        }

        outputFunctionSignature(func.ident, inStack, outStack, localStack);
        out.increaseIndent();

        // Save all $s registers
        for (int i = 0; i < callee.size(); i++) 
        {
            outputAssignment(RegAllocHelper.local(i), callee.get(i).toString());
        }

        // Load parameters into register or `local` statck
        Register[] argregs = { Register.a0, Register.a1, Register.a2, Register.a3 };

        for (int i = 0; i < func.params.length; i++) 
        {
            Register dst = map.lookupRegister(func.params[i].ident);

            if (dst != null) 
            {
                if (i < 4) // Params passed by registers
                { 
                    outputAssignment(dst.toString(), argregs[i].toString());
                } 
                else // Params passed by `in` stack
                { 
                    outputAssignment(dst.toString(), RegAllocHelper.in(i - 4));
                }
            } 
            else 
            {
                int offset = map.lookupStack(func.params[i].ident);

                if (offset != -1) // some parameters may never be used
                { 
                    // Move the remaining parameters into `local` stack
                    Register load = localPool.acquire();
                    outputAssignment(load.toString(), RegAllocHelper.in(i - 4));
                    outputAssignment(RegAllocHelper.local(offset), load.toString());
                    localPool.release(load);
                }
            }
        }

        for (int i = 0; i < func.body.length; i++) 
        {
            // Only save those live-out but not def in this node.
            final Set<String> liveOut = liveness.getOut().get(i);
            liveOut.removeAll(liveness.getDef().get(i));

            // Output labels
            if (labels.containsKey(i)) 
            {
                out.decreaseIndent();
                labels.get(i).forEach(l -> out.writeLine(l + ":"));
                out.increaseIndent();
            }

            func.body[i].accept(new VInstr.Visitor<RuntimeException>() 
            {
                @Override
                public void visit(VAssign vAssign) 
                {
                    Register dst = loadVariable(map, vAssign.dest.toString(), true);

                    if (vAssign.source instanceof VVarRef) 
                    {
                        Register src = loadVariable(map, vAssign.source.toString(), false);
                        outputAssignment(dst.toString(), src.toString());
                        releaseLocalRegister(src);
                    } 
                    else 
                    {
                        outputAssignment(dst.toString(), vAssign.source.toString());
                    }

                    writeVariable(dst, map, vAssign.dest.toString());
                    releaseLocalRegister(dst);
                }

                @Override
                public void visit(VCall vCall) 
                {
                    List<Register> save = liveOut.stream().map(map::lookupRegister).filter(o -> o != null && o.isCallerSaved()).distinct().collect(Collectors.toList());
                    save.sort(Comparator.comparing(Register::toString));

                    // Save all $t registers
                    for (int i = 0; i < save.size(); i++) 
                    {
                        outputAssignment(RegAllocHelper.local(map.stackSize() + i), save.get(i).toString());
                    }

                    Register[] argregs = { Register.a0, Register.a1, Register.a2, Register.a3 };

                    for (int i = 0; i < vCall.args.length; i++) 
                    {
                        String var = vCall.args[i].toString();

                        if (vCall.args[i] instanceof VVarRef) 
                        {
                            if (i < 4) // into registers
                            { 
                                Register reg = map.lookupRegister(var);

                                if (reg != null) 
                                {
                                    outputAssignment(argregs[i].toString(), reg.toString());
                                } 
                                else 
                                {
                                    int offset = map.lookupStack(var);
                                    outputAssignment(argregs[i].toString(), RegAllocHelper.local(offset));
                                }
                            } 
                            else // into `out` stack
                            { 
                                Register reg = loadVariable(map, var, false);
                                outputAssignment(RegAllocHelper.out(i - 4), reg.toString());
                                releaseLocalRegister(reg);
                            }
                        } 
                        else 
                        {
                            if (i < 4) //store into $a0~$a3
                            { 
                                outputAssignment(argregs[i].toString(), var);
                            } 
                            else // store into `out` stack
                            { 
                                outputAssignment(RegAllocHelper.out(i - 4), var);
                            }
                        }
                    }

                    if (vCall.addr instanceof VAddr.Label) 
                    {
                        out.writeLine("call " + vCall.addr.toString());
                    } 
                    else 
                    {
                        Register addr = loadVariable(map, vCall.addr.toString(), false);
                        out.writeLine("call " + addr.toString());
                        releaseLocalRegister(addr);
                    }

                    Register dst = loadVariable(map, vCall.dest.toString(), true);

                    if (dst != Register.v0)
                        outputAssignment(dst.toString(), Register.v0.toString());

                    writeVariable(dst, map, vCall.dest.toString());
                    releaseLocalRegister(dst);

                    // Restore all $t registers
                    for (int i = 0; i < save.size(); i++) 
                    {
                        outputAssignment(save.get(i).toString(), RegAllocHelper.local(map.stackSize() + i));
                    }
                }

                @Override
                public void visit(VBuiltIn vBuiltIn) 
                {
                    StringBuilder rhs = new StringBuilder(vBuiltIn.op.name + "(");
                    List<Register> srcregs = new ArrayList<>();

                    for (VOperand arg : vBuiltIn.args) 
                    {
                        if (arg instanceof VVarRef) 
                        {
                            Register src = loadVariable(map, arg.toString(), false);
                            srcregs.add(src);

                            rhs.append(src.toString());
                            rhs.append(" ");
                        } 
                        else 
                        {
                            rhs.append(arg.toString());
                            rhs.append(" ");
                        }
                    }
                    rhs.deleteCharAt(rhs.length() - 1);
                    rhs.append(")");

                    for (Register src : srcregs)
                        releaseLocalRegister(src);

                    if (vBuiltIn.dest == null) // no return value
                    { 
                        out.writeLine(rhs.toString());
                    } 
                    else 
                    {
                        Register dst = loadVariable(map, vBuiltIn.dest.toString(), true);
                        outputAssignment(dst.toString(), rhs.toString());

                        writeVariable(dst, map, vBuiltIn.dest.toString());
                        releaseLocalRegister(dst);
                    }
                }

                @Override
                public void visit(VMemWrite vMemWrite) 
                {
                    VMemRef.Global ref = (VMemRef.Global) vMemWrite.dest;
                    Register base = loadVariable(map, ref.base.toString(), false);

                    if (vMemWrite.source instanceof VVarRef) 
                    {
                        Register src = loadVariable(map, vMemWrite.source.toString(), false);
                        outputAssignment(RegAllocHelper.memoryReference(base, ref.byteOffset), src.toString());
                        releaseLocalRegister(src);
                    } 
                    else 
                    {
                        outputAssignment(RegAllocHelper.memoryReference(base, ref.byteOffset), vMemWrite.source.toString());
                    }

                    releaseLocalRegister(base);
                }

                @Override
                public void visit(VMemRead vMemRead) 
                {
                    Register dst = loadVariable(map, vMemRead.dest.toString(), true);

                    VMemRef.Global ref = (VMemRef.Global) vMemRead.source;
                    Register src = loadVariable(map, ref.base.toString(), false);
                    outputAssignment(dst.toString(), RegAllocHelper.memoryReference(src, ref.byteOffset));
                    releaseLocalRegister(src);

                    writeVariable(dst, map, vMemRead.dest.toString());
                    releaseLocalRegister(dst);
                }

                @Override
                public void visit(VBranch vBranch) 
                {
                    String cond = vBranch.value.toString();

                    if (vBranch.value instanceof VVarRef) 
                    {
                        Register src = loadVariable(map, vBranch.value.toString(), false);
                        cond = src.toString();
                        releaseLocalRegister(src);
                    }

                    out.write(vBranch.positive ? "if" : "if0");
                    out.write(" " + cond);
                    out.writeLine(" goto " + vBranch.target);
                }

                @Override
                public void visit(VGoto vGoto) 
                {
                    out.writeLine("goto " + vGoto.target.toString());
                }

                @Override
                public void visit(VReturn vReturn) 
                {
                    if (vReturn.value != null) 
                    {
                        if (vReturn.value instanceof VVarRef) 
                        {
                            Register src = loadVariable(map, vReturn.value.toString(), false);

                            if (src != Register.v0)
                                outputAssignment(Register.v0.toString(), src.toString());

                            releaseLocalRegister(src);
                        } 
                        else 
                        {
                            outputAssignment(Register.v0.toString(), vReturn.value.toString());
                        }
                    }

                    // Restore all $s registers
                    for (int i = 0; i < callee.size(); i++) 
                    {
                        outputAssignment(callee.get(i).toString(), RegAllocHelper.local(i));
                    }

                    out.writeLine("ret");
                }
            });
        }

        out.decreaseIndent();
    }
}



public class VM
{
    public static void main(String[] args) throws ProblemException, IOException 
    {
        Allocator allocator = new Allocator();
        Converter converter = new Converter();
        VaporProgram program = parseVapor(System.in, System.err);
    
        converter.outputConstSegment(program.dataSegments);
        for (VFunction func : program.functions) 
        {
            FlowGraph graph = RegAllocHelper.generateFlowGraph(func);
            Liveness liveness = graph.computLiveness();
    
            // Register allocation is applied to ech function separately.
            List<Interval> intervals = RegAllocHelper.generateLiveIntervals(graph, liveness);
            AllocationMap map = allocator.computeAllocation(intervals, func.params);
            converter.outputFunction(func, map, liveness);
            converter.getOutput().writeLine();
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
}
