package interpreter;

import java.util.List;
import java.util.ArrayList;
import java.util.Stack;
import java.util.Scanner;

import NBT.NbtTag;
import NBT.TagCompound;
import NBT.TagList;

public class Program {
    
    private Command[] program;

    /**
     * the function stack
     */
    private Stack<Integer> stack;

    /**
     * the name of the functions on the stack
     * for debugging only
     */
    private Stack<String> function;

    /**
     * counts the number of if(/else) loops we are in for each function
     */
    private Stack<Integer> inIf;

    /**
     * stores the pointer position of the while loops
     */
    private Stack<Stack<Integer>> inWhile;

    private Memory mem;
    private int pointer;

    private boolean ok;
    
    public Program(TagList prg) {
        //setting up the program to run
        List<NbtTag> p = prg.getValue();
        program = new Command[1024];
        for (int i = 0; i < p.size(); i++) {
            TagCompound cmd = (TagCompound)p.get(i);
            program[i] = new Command(cmd);
        }
        //verifying and "compiling" the program
        ok = true;
        Stack<String> loops = new Stack<String>();
        Stack<Integer> loopLine = new Stack<Integer>();
        Stack<String> variables = new Stack<String>();
        List<String> functions = new ArrayList<String>();

        for (int i = 0; i < program.length; i++) {
            int cmd = program[i].getCommand();
            //int mod = program[i].getMod();

            try {
                //verifying the command is in a valid place
                if (cmd != 0 && cmd != 1 && cmd != 2 && cmd != 3 && loops.isEmpty()) {
                    throw new InterpreterException("Command must be in a function");
                }

                //check that variables exists
                for (Parameter parameter : program[i].getParameters()) {
                    if (parameter.getType() == 1) {
                        if (!variables.contains(parameter.getName()) && !parameter.getName().startsWith("*")) {
                            throw new InterpreterException("undefined variable " + parameter.getName());
                        }
                    }
                }

                switch (cmd) {
                    case 2: //decl
                        {
                            if (!loops.isEmpty())
                                throw new InterpreterException("decl statment within function");
                            variables.add(program[i].getParameters()[0].getName());
                        }
                        break;
                    case 3: //function
                        if (!loops.isEmpty())
                            throw new InterpreterException("cannot declare function inside a function");
                        loops.push("func-" + program[i].getParameters()[0].getName());
                        functions.add(program[i].getParameters()[0].getName());
                        break;
                    case 12: //return
                        if (loops.peek().startsWith("func-")) {
                            while (!variables.isEmpty()) //remove the local variables
                                variables.pop();
                            loops.pop();
                        } else {
                            //TODO remove statment when return in the middle of a function will be possible
                            throw new InterpreterException("return must be at the end of a function, not after a " + loops.peek());
                        }
                        break;
                    case 6: //if
                        loops.push("if");
                        loopLine.push(i);
                        break;
                    case 4: //else
                        {
                            String last = loops.peek();
                            if (last.startsWith("if")) {
                                if (last.equals("if-else")) {
                                    throw new InterpreterException("there can be only one else statment per if");
                                } else {
                                    int index = loopLine.pop();
                                    program[index].getParameters()[2].setValue(i);
                                    loopLine.push(i);
                                    loops.pop();
                                    loops.push("if-else");
                                }
                            } else {
                                throw new InterpreterException("else statment must be placed after an if statment not " + last);
                            }
                        }
                        break;
                    case 8: //ifend
                        {
                            String last = loops.peek();
                            if (last.startsWith("if")) {
                                int index = loopLine.pop();
                                program[index].getParameters()[2].setValue(i);
                                loops.pop();
                            } else {
                                throw new InterpreterException("ifend statment must be placed after an if statment not " + last);
                            }
                        }
                        break;
                    case 7: //while
                        loops.push("while");
                        loopLine.push(i);
                        break;
                    case 9: //wend
                        String last = loops.peek();
                        if (last.startsWith("while")) {
                            int index = loopLine.pop();
                            program[i].getParameters()[0].setValue(index - 1);
                            program[index].getParameters()[2].setValue(i);
                            loops.pop();
                        } else {
                            throw new InterpreterException("wend statment must be placed after a while statment not " + last);
                        }
                        break;
                }
            } catch (InterpreterException e) {
                System.err.println("Error line " + (i + 1));
                System.err.println("\t" + e.getMessage());
                ok = false;
            }
        }
    }

    public void run() {
        if (!ok)
            return;
        Screen surface = new Screen();
        surface.setVisible(true);
        //setting up the values
        stack = new Stack<Integer>();
        function = new Stack<String>();

        inIf = new Stack<Integer>();
        inWhile = new Stack<Stack<Integer>>();

        mem = new Memory();
        pointer = 0;
        
        try (Scanner scanner = new Scanner(System.in)) {
            //going to the first function
            while (program[pointer].getCommand() != 3) {
                if (++pointer >= program.length) {
                    throw new InterpreterException("No function to run the program");
                }
            }
            runFunction(program[pointer].getParameters()[0].getName(), new int[0]);
            //removing the useless variable from the stack
            stack.pop();
            pointer++;
            //running the program
            while (!function.isEmpty()) {
                int cmd = program[pointer].getCommand();
                int mod = program[pointer].getMod();
                Parameter[] p = program[pointer].getParameters();
                switch (cmd) {
                    case 0, 1, 8:
                        break;
                    case 6: //if
                        {
                            //evaluating the expression
                            boolean s = false;
                            int val0 = 0;
                            int val1 = 0;
                            if (mod < 6) {
                                val0 = getValue(p[0]);
                                val1 = getValue(p[1]);
                            }
                            switch (mod) {
                                case 0:
                                    s = val0 > val1;
                                    break;
                                case 1:
                                    s = val0 >= val1;
                                    break;
                                case 2:
                                    s = val0 == val1;
                                    break;
                                case 3:
                                    s = val0 != val1;
                                    break;
                                case 4:
                                    s = val0 <= val1;
                                    break;
                                case 5:
                                    s = val0 < val1;
                                    break;
                                case 6, 7, 8, 9:
                                    s = surface.getPressedKey()[mod - 6];
                                    break;
                            }
                            //if expression is false
                            if (!s) {
                                pointer = p[2].getValue();
                            }
                        }
                        break;
                    case 4: //else
                        {
                            pointer = p[2].getValue();
                        }
                        break;
                    case 200: //io.print[~]
                        {   
                            switch (mod) {
                                case 0:
                                    System.out.println(getValue(p[0]));
                                    break;
                                case 1:
                                    System.out.println(mem.getString(getValue(p[0])));
                                    break;
                                case 2:
                                    System.out.println(p[0].getName());
                                    break;
                            }
                        }
                        break;
                    case 7: //while
                        {
                            //evaluating the expression
                            boolean s = false;
                            int val0 = 0;
                            int val1 = 0;
                            if (mod < 6) {
                                val0 = getValue(p[0]);
                                val1 = getValue(p[1]);
                            }
                            switch (mod) {
                                case 0:
                                    s = val0 > val1;
                                    break;
                                case 1:
                                    s = val0 >= val1;
                                    break;
                                case 2:
                                    s = val0 == val1;
                                    break;
                                case 3:
                                    s = val0 != val1;
                                    break;
                                case 4:
                                    s = val0 <= val1;
                                    break;
                                case 5:
                                    s = val0 < val1;
                                    break;
                                case 6, 7, 8, 9:
                                    s = surface.getPressedKey()[mod - 6];
                                    break;
                            }
                            //if not true
                            if (!s) {
                                pointer = p[2].getValue();
                            }
                        }
                        break;
                    case 9: //wend
                        {   
                            pointer = p[0].getValue();
                        }
                        break;
                    case 11: //wait
                        {
                            int amound = getValue(p[0]) * 1000 / 20;//convert to gametick wich is 1/20 seconds
                            try { Thread.sleep(amound); } catch (InterruptedException e) {}
                        }
                         break;
                    case 12: //return
                        {
                            int value = getValue(p[0]);
                            if (stack.isEmpty()) {
                                System.out.println("Program ended with status " + value);
                                return;
                            } else {
                                popStack();
                                mem.setReturn(value);
                            }
                        }
                        break;
                    case 13: //call
                        {   
                            int argc = 0;
                            for (int i = 0; i < 6; i++) {
                                if (p[i + 1].getName().isEmpty()) {
                                    argc = i;
                                    break;
                                }
                            }
                            int[] args = new int[argc];
                            for (int i = 0; i < argc; i++) {
                                args[i] = getValue(p[i + 1]);
                            }
                            runFunction(p[0].getName(), args);
                        }
                        break;
                    case 100: //variable math
                        {
                            String var = p[0].getName();
                            int var1 = 0;
                            int var2 = 0;
                            if (mod >= 1 && mod <= 5 || mod == 18) {
                                var1 = getValue(p[1]);
                                var2 = getValue(p[2]);
                            } else if (mod >= 6 && mod <=10 || mod == 0){
                                var1 = mem.getLocal(var);
                                var2 = getValue(p[1]);
                            }
                            switch (mod) {
                            case 0: // = a
                                mem.setLocal(var, var2);
                                break;
                            case 1: // = a + b
                                mem.setLocal(var, var1 + var2);
                                break;
                            case 2: // = a - b
                                mem.setLocal(var, var1 - var2);
                                break;
                            case 3: // = a * b
                                mem.setLocal(var, var1 * var2);
                                break;
                            case 4: // = a / b
                                mem.setLocal(var, var1 / var2);
                                break;
                            case 5: // = a % b
                                mem.setLocal(var, var1 % var2);
                                break;
                            case 6: // += a
                                mem.setLocal(var, var1 + var2);
                                break;
                            case 7: // -= a
                                mem.setLocal(var, var1 - var2);
                                break;
                            case 8: // *= a
                                mem.setLocal(var, var1 * var2);
                                break;
                            case 9: // /= a
                                mem.setLocal(var, var1 / var2);
                                break;
                            case 10: // %= a
                                mem.setLocal(var, var1 % var2);
                                break;
                            case 11: // = *a
                                mem.setLocal(var, mem.getLocal("*a"));
                                break;
                            case 12: // = *b
                                mem.setLocal(var, mem.getLocal("*b"));
                                break;
                            case 13: // = *c
                                mem.setLocal(var, mem.getLocal("*c"));
                                break;
                            case 14: // = *d
                                mem.setLocal(var, mem.getLocal("*d"));
                                break;
                            case 15: // = *e
                                mem.setLocal(var, mem.getLocal("*e"));  
                                break;
                            case 16: // = *f
                                mem.setLocal(var, mem.getLocal("*f"));
                                break;
                            case 17: // = *return
                                mem.setLocal(var, mem.getLocal("*return"));
                                break;
                            case 18: // = rand(a, b)
                                mem.setLocal(var, random(var1, var2));
                                break;
                            }
                        }
                        break;
                    case 101: //x++
                        {
                            String var = p[0].getName();
                            int val = mem.getLocal(var);
                            mem.setLocal(var, val + 1);
                        }
                        break;
                    case 102: //x--
                        {
                            String var = p[0].getName();
                            int val = mem.getLocal(var);
                            mem.setLocal(var, val - 1);
                        }
                        break;
                    case 300: //str.create
                        {   
                            String ptr = p[0].getName();
                            mem.createString(ptr);
                        }
                        break;
                    case 301: //str.empty
                        {   
                            int ptr = getValue(p[0]);
                            mem.setString(ptr, "");
                        }
                        break;
                    case 302: //str.setChar
                        {
                            int success = -1;
                            int ptr = getValue(p[0]);
                            int pos = getValue(p[1]);
                            int c = getValue(p[2]);
                            StringBuilder str = new StringBuilder(mem.getString(ptr));
                            if (pos >= 0 && pos < str.length()) {
                                if (mem.getChar(c) != null) {
                                    str.setCharAt(pos, mem.getChar(c));
                                    mem.setString(ptr, str.toString());
                                    success = 0;
                                }
                            }
                            mem.setReturn(success);
                        }
                        break;
                    case 303: //str.getChar
                        {
                            String str = mem.getString(getValue(p[0]));
                            int index = getValue(p[1]);
                            if (index >= 0 && index < str.length()) {
                                int result = mem.getChar(str.charAt(index));
                                mem.setReturn(result);
                            } else {
                                mem.setReturn(-1);
                            }
                        }
                        break;
                    case 304: //str.append[~]
                        {
                            int ptr = getValue(p[0]);
                            String append = "";
                            switch (mod) {
                                case 0:
                                    append += getValue(p[1]);
                                    break;
                                case 1:
                                    append += mem.getChar(getValue(p[1]));
                                    break;
                                case 2:
                                    append += mem.getString(getValue(p[1]));
                                    break;
                                case 3:
                                    append += p[1].getName();
                                    break;
                            }
                            mem.setString(ptr, mem.getString(ptr) + append);
                        }
                        break;
                    case 400: //gpu.clear
                        {
                            int color = getValue(p[0]);
                            surface.clear(color);
                        }
                        break;
                    case 401: //gpu.pixel
                        {
                            int x = getValue(p[0]);
                            int y = getValue(p[1]);
                            int color = getValue(p[2]);
                            surface.pixel(x, y, color);
                        }
                        break;
                    case 402: //gpu.line
                        {
                            int x0 = getValue(p[0]);
                            int y0 = getValue(p[1]);
                            int x1 = getValue(p[2]);
                            int y1 = getValue(p[3]);
                            int color = getValue(p[4]);
                            surface.line(x0, y0, x1, y1, color);
                        }
                        break;
                    case 403: //gpu.screen
                        {
                            int index = getValue(p[0]);
                            surface.loadScreen(index);
                        }
                        break;
                    case 404: //gpu.fill
                        {
                            int x0 = getValue(p[0]);
                            int y0 = getValue(p[1]);
                            int x1 = getValue(p[2]);
                            int y1 = getValue(p[3]);
                            int color = getValue(p[4]);
                            surface.fill(x0, y0, x1, y1, color);
                        }
                        break;
                    case 201: //io.mem
                        {
                            int p0 = 0;
                            int p1 = 0;
                            int p2 = 0;
                            String val;
                            if (mod < 3) {
                                p0 = getValue(p[0]);
                                val = p[1].getName();
                            } else {
                                p0 = getValue(p[0]);
                                p1 = getValue(p[1]);
                                p2 = getValue(p[2]);
                                val = p[3].getName();
                            }
                            switch (mod) {
                                case 0: // [] ->
                                    mem.setLocal(val, mem.getGlobal(p0));
                                    break;
                                case 1: // [] <-
                                    mem.setGlobal(p0, getValue(p[1]));
                                    break;
                                case 2: // [] <->
                                    {
                                        int v = mem.getLocal(val);
                                        mem.setLocal(val, mem.getGlobal(p0));
                                        mem.setGlobal(p0, v);
                                    }
                                    break;
                                case 3: // [][][] ->
                                    mem.setLocal(val, mem.getGlobal(p0, p1, p2));
                                    break;
                                case 4: // [][][] <-
                                    mem.setGlobal(p0, p1, p2, getValue(p[3]));
                                    break;
                                case 5: // [][][] <->
                                    {
                                        int v = mem.getLocal(val);
                                        mem.setLocal(val, mem.getGlobal(p0, p1, p2));
                                        mem.setGlobal(p0, p1, p2, v);
                                    }
                                    break;
                            }
                        }
                        break;
                    case 202: //io.get[~]
                        {
                            switch (mod) {
                                case 0:
                                    {   
                                        int val;
                                        boolean ok = false;
                                        while (!ok) {
                                            System.out.print("i>" + p[0].getName() + ": ");
                                            if (!scanner.hasNextInt())
                                                scanner.next();
                                            else
                                                ok = true;
                                        }
                                        val = scanner.nextInt();
                                        mem.setReturn(val);
                                    }
                                    break;
                                case 1:
                                    {
                                        System.out.print("s>" + p[0].getName() + ": ");
                                        mem.createString("*return");
                                        mem.setString(mem.getLocal("*return"), scanner.next());
                                    }
                                    break;
                            }
                        }
                        break;
                }
                pointer++;
                if (pointer >= program.length) {
                    throw new InterpreterException("No return for the main function");
                }
            }
        } catch (InterpreterException e) {
            error(e.getMessage());
        }
    }

    private void runFunction(String name, int[] args) {
        //finding the function
        stack.push(pointer);
        function.push(name);
        inIf.push(0);
        inWhile.push(new Stack<Integer>());
        pointer = 0;
        String sr = "";
        while (!name.equals(sr)) {
            while (program[pointer].getCommand() != 3) {
                pointer++;
                if (pointer > program.length)
                    throw new InterpreterException("No function named " + name);
            }
            sr = program[pointer].getParameters()[0].getName();
            pointer++;
        }
        pointer--;
        if (program[pointer].getMod() != args.length)
            throw new InterpreterException("Given " + args.length + " argument but function " + name + " has " + program[pointer].getMod());
        //getting the local variables
        List<String> vars = new ArrayList<String>();
        stack.push(pointer);
        pointer--;
        while (program[pointer].getCommand() == 0 || program[pointer].getCommand() == 1 || program[pointer].getCommand() == 2) {
            if (program[pointer].getCommand() == 2) {
                vars.add(program[pointer].getParameters()[0].getName());
            }
            if (--pointer < 0)
                break;
        }
        mem.add(vars, args);
        pointer = stack.pop();
    }

    private int getValue(Parameter p) {
        if (p.getType() == 0) {
            return p.getValue();
        } else if (p.getType() == 1) {
            return mem.getLocal(p.getName());
        }
        throw new InterpreterException("Type is neither 0 nor 1 (is:" + p.getType() + ")");
    }

    private void popStack() {
        if (!stack.isEmpty()) pointer = stack.pop();
        function.pop();
        mem.pop();
        inIf.pop();
        inWhile.pop();
    }


    private void error(String message) {
        System.err.println("Runtime error: " + message);
        stack.push(pointer);
        while (!stack.isEmpty()) {
            System.err.println("    at - " + ((function.size() > 0)? function.pop() + "() " : "") + "line:" + (stack.pop() + 1));
        }
    }

    private int random(int min, int max) {
        return (int)(Math.random() * (max - min) + min);
    }
}