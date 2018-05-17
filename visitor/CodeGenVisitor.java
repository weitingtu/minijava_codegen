package visitor;

import syntaxtree.*;
import java.io.PrintWriter;

public class CodeGenVisitor extends DepthFirstVisitor
{

    static Class currClass;
    static Method currMethod;
    static SymbolTable symbolTable;
    PrintWriter out;
    int label_count;
    Class callClass;

    public CodeGenVisitor( SymbolTable s, PrintWriter out )
    {
        symbolTable = s;
        this.out = out;
        this.label_count = 0;
        this.callClass = null;
    }

    // MainClass m;
    // ClassDeclList cl;
    public void visit( Program n )
    {
        // Data segment
        out.println(
            ".data\n" +
            "newline: .asciiz \"\\n\"\n" +    // to be used by cgen for "System.out.println()"
            "msg_index_out_of_bound_exception: .asciiz \"Index out of bound exception\\n\"\n" +
            "msg_null_pointer_exception: .asciiz \"Null pointer exception\\n\"\n" +
            "\n" +
            ".text\n"
        );

        n.m.accept( this );

        out.println(  // Code to terminate the program
            "# exit\n" +
            "li $v0, 10\n" +
            "syscall\n"
        );

        // Code for all methods
        for ( int i = 0; i < n.cl.size(); i++ )
        {
            n.cl.elementAt( i ).accept( this );
        }

        // Code for some utility functions
        cgen_supporting_functions();
    }

    // Identifier i1,i2;
    // VarDeclList vl;
    // Statement s;
    public void visit( MainClass n )
    {
        String i1 = n.i1.toString();
        currClass = symbolTable.getClass( i1 );
        currMethod = currClass.getMethod( "main" ); // This is a hack (treat main() as instance method.)

        // Can ignore the parameter of main()

        // Info about local variables are kept in "currMethod"

        // Generate code to reserve space for local variables in stack
        out.println( "move $fp, $sp" );
        out.println( "addiu $sp, $sp, " + -4 * ( currMethod.getVarSize() + 2 ) + "\n" );
        // Optionally, generate code to reserve space for temps

        n.s.accept( this );
    }

    // Identifier i;
    // VarDeclList vl;
    // MethodDeclList ml;
    public void visit( ClassDeclSimple n )
    {
        currClass = symbolTable.getClass( n.i.toString() );
        for ( int i = 0; i < n.ml.size(); i++ )
        {
            n.ml.elementAt( i ).accept( this );
        }
    }

    // Type t;
    // Identifier i;
    // FormalList fl;
    // VarDeclList vl;
    // StatementList sl;
    // Exp e;
    // cgen: t i(fl) { vl sl return e; }
    public void visit( MethodDecl n )
    {
        String id = n.i.toString();
        currMethod = currClass.getMethod( id );
        String label = get_function_label( currClass.getId(), currMethod.getId() );
        out.println( label + ":\n" );
        out.println( "move $fp, $sp" );
        out.println( "sw $ra, 0($sp) # push $ra" ); // push $ra
        out.println( "addiu $sp, $sp, -4" );
        out.println( "addiu $sp, $sp, " + -4 * ( currMethod.getVarSize() + 2 ) + "\n" );

        for ( int i = 0; i < n.sl.size(); i++ )
        {
            n.sl.elementAt( i ).accept( this );
        }
        n.e.accept( this );
        // handle return value
        out.println( "move $v0, $a0 # save return value" );

        out.println( "addiu $sp, $sp, " + 4 * ( currMethod.getVarSize() + 2 ) + "\n" );
        out.println( "lw $ra, 4($sp) # restore $ra" ); // restore $ra
        out.println( "addiu $sp, $sp, " + ( currMethod.params.size() * 4 + 12 ) );
        out.println( "lw $fp, 0($sp) # restore $fp" ); // restore $fp
        out.println( "jr $ra" );
    }

    // Exp e;
    // Statement s1,s2;
    // cgen: if (e) s1 else s2
    public void visit( If n )
    {
        n.e.accept( this );
        String label1 = "$L" + label_count++;
        String label2 = "$L" + label_count++;

        // beq  $2,$0,$L2
        out.println( "beq $a0, 0, " + label1 + "\n" );
        n.s1.accept( this );
        out.println( "j " + label2 + "\n" );

        // label1 -> s1
        out.println( label1 + ":\n" );
        n.s2.accept( this );

        // label2 -> next
        out.println( label2 + ":\n" );
    }

    // Exp e;
    // Statement s;
    // cgen: while (e) s;
    public void visit( While n )
    {
        String label1 = "$Loop" + label_count++;
        String label2 = "$Loop_exit" + label_count++;
        out.println( label1 + ":\n" );
        n.e.accept( this );
        out.println( "beq $a0, 0, " + label2 + "\n" );
        n.s.accept( this );
        out.println( "j " + label1 + "\n" );
        out.println( label2 + ":\n" );
    }

    // Exp e;
    // cgen: System.out.println(e)
    public void visit( Print n )
    {
        n.e.accept( this );
        out.println( "jal _print_int\n" );
    }

    // Identifier i;
    // Exp e;
    // cgen: i = e
    public void visit( Assign n )
    {
        n.e.accept( this );
        Variable v = null;
        if ( currMethod.containsParam( n.i.toString() ) )
        {
            v = currMethod.getParam( n.i.toString() );
            out.println( "sw $a0, " + 4 * ( v.idx() + 1 ) + "($fp) # save parameter " + v.id() + "\n" );
        }
        else if ( currMethod.containsVar( n.i.toString() ) )
        {
            v = currMethod.getVar( n.i.toString() );
            out.println( "sw $a0, " + -4 * ( v.idx() + 1 ) + "($fp) # save local variable " + v.id() + "\n" );
        }
        else if ( currClass.containsVar( n.i.toString() ) )
        {
            v = currClass.getVar( n.i.toString() );
            out.println( "lw $t0, " + 4 * ( currMethod.params.size() + 1 ) + "($fp) # load this" );
            out.println( "sw $a0, " + 4 * ( v.idx() + 3 ) + "($t0) # save object variable " + v.id() + "\n" );
        }
        else
        {
            System.out.println( "Cannot find " + n.i.toString() + " in method " + currMethod.getId() );
            System.exit( -1 );
        }
        if ( ( v.type() instanceof IdentifierType ) || ( v.type() instanceof IntArrayType ) )
        {
            out.println( "beq  $a0, $zero, _null_pointer_exception\n" );
        }
    }

    // Identifier i;
    // Exp e1,e2;
    // cgen: i[e1] = e2
    public void visit( ArrayAssign n )
    {
        n.e2.accept( this );
        out.println( "sw $a0, 0($sp)" );   // push value of e2 to stack
        out.println( "addiu $sp, $sp, -4" );

        n.e1.accept( this );
        out.println( "sw $a0, 0($sp)" );     // push e1 value to stack
        out.println( "addiu $sp, $sp, -4" );
        out.println( "addi $a0, $a0, 1 # ArrayAssign" );   // index = e + 1 (length)
        out.println( "sw $a0, 0($sp)" );     // push value to stack
        out.println( "addiu $sp, $sp, -4" );
        out.println( "lw $t1, 4($sp)" );     // $t1 = stack top
        out.println( "li $a0, 4" );          // $a0 = 4
        out.println( "mult $t1, $a0" );      // $a0 = stack top * 4
        out.println( "mflo $a0" );           // 32 least significant bits of multiplication to $a0
        out.println( "addiu $sp, $sp, 4" );  // pop

        out.println( "sw $a0, 0($sp)" );     // push value to stack
        out.println( "addiu $sp, $sp, -4" );

        // local variable
        Variable v = null;
        if ( currMethod.containsParam( n.i.toString() ) )
        {
            v = currMethod.getParam( n.i.toString() );
            out.println( "lw $a0, " + 4 * ( v.idx() + 1 ) + "($fp) # load parameter " + v.id() + "\n" );
        }
        else if ( currMethod.containsVar( n.i.toString() ) )
        {
            v = currMethod.getVar( n.i.toString() );
            out.println( "lw $a0, " + -4 * ( v.idx() + 1 ) + "($fp) # load local variable " + v.id() + "\n" );
        }
        else if ( currClass.containsVar( n.i.toString() ) )
        {
            v = currClass.getVar( n.i.toString() );
            out.println( "lw $a0, " + 4 * ( currMethod.params.size() + 1 ) + "($fp) # load this" );
            out.println( "lw $a0, " + 4 * ( v.idx() + 3 ) + "($a0) # load object variable " + v.id() + "\n" );
        }
        else
        {
            System.out.println( "Cannot find " + n.i.toString() + " in method " + currMethod.getId() );
            System.exit( -1 );
        }

        if ( ( v.type() instanceof IdentifierType ) || ( v.type() instanceof IntArrayType ) )
        {
            out.println( "beq  $a0, $zero, _null_pointer_exception\n" );
        }

        out.println( "lw $t1, 8($sp)" );     // e1
        out.println( "lw $t2, 0($a0)" );     // length
        out.println( "bge $t1, $t2, _array_index_out_of_bound_exception" );

        out.println( "lw $t1, 4($sp)" );     // $t1 = stack top, e1, index
        out.println( "lw $t2, 12($sp)" );    // e2

        out.println( "add $a0, $a0, $t1" );
        out.println( "sw $t2, 0($a0)" );
        out.println( "addiu $sp, $sp, 12" );
    }

    // Exp e1,e2;
    // cgen: e1 && e2
    public void visit( And n )
    {
        n.e1.accept( this );
        out.println( "sw $a0, 0($sp)" );   // push value of e1 to stack
        out.println( "addiu $sp, $sp, -4" );

        n.e2.accept( this );
        out.println( "lw $t1, 4($sp)" );      // $t1 = stack top
        out.println( "and $a0, $t1, $a0" );   // $a0 = $a0 && stack top
        out.println( "addiu $sp, $sp, 4\n" ); // pop
    }

    // Exp e1,e2;
    // cgen: e1 < e2
    public void visit( LessThan n )
    {
        n.e1.accept( this );
        out.println( "sw $a0, 0($sp)" );   // push value of e1 to stack
        out.println( "addiu $sp, $sp, -4" );

        n.e2.accept( this );
        out.println( "lw $t1, 4($sp)" );      // $t1 = stack top
        out.println( "slt $a0, $t1, $a0" );   // $a0 = $a0 < stack top
        out.println( "addiu $sp, $sp, 4\n" ); // pop
    }

    // Exp e1,e2;
    // cgen: e1 + e2
    public void visit( Plus n )
    {
        n.e1.accept( this );
        out.println( "sw $a0, 0($sp)" );   // push value of e1 to stack
        out.println( "addiu $sp, $sp, -4" );

        n.e2.accept( this );
        out.println( "lw $t1, 4($sp)" );      // $t1 = stack top
        out.println( "add $a0, $t1, $a0" );   // $a0 = $a0 + stack top
        out.println( "addiu $sp, $sp, 4\n" ); // pop
    }

    // Exp e1,e2;
    // cgen: e1 - e2
    public void visit( Minus n )
    {
        n.e1.accept( this );
        out.println( "sw $a0, 0($sp)" );   // push value of e1 to stack
        out.println( "addiu $sp, $sp, -4" );

        n.e2.accept( this );
        out.println( "lw $t1, 4($sp)" );      // $t1 = stack top
        out.println( "sub $a0, $t1, $a0" );   // $a0 = stack top - $a0
        out.println( "addiu $sp, $sp, 4\n" ); // pop
    }

    // Exp e1,e2;
    // cgen: e1 * e2
    public void visit( Times n )
    {
        n.e1.accept( this );
        out.println( "sw $a0, 0($sp)" );   // push value of e1 to stack
        out.println( "addiu $sp, $sp, -4" );

        n.e2.accept( this );
        out.println( "lw $t1, 4($sp)" );      // $t1 = stack top
        out.println( "mult $t1, $a0" );       // $a0 = stack top * $a0
        out.println( "mflo $a0" );            // 32 least significant bits of multiplication to $a0
        out.println( "addiu $sp, $sp, 4\n" ); // pop
    }

    // Exp e1,e2;
    // cgen: e1[e2]
    public void visit( ArrayLookup n )
    {
        n.e2.accept( this );
        out.println( "sw $a0, 0($sp)" );     // push e2 value to stack
        out.println( "addiu $sp, $sp, -4" );
        out.println( "addi $a0, $a0, 1 # ArrayLookUp" );   // index = e + 1 (length)
        out.println( "sw $a0, 0($sp)" );     // push value to stack
        out.println( "addiu $sp, $sp, -4" );
        out.println( "lw $t1, 4($sp)" );     // $t1 = stack top
        out.println( "li $a0, 4" );          // $a0 = 4
        out.println( "mult $t1, $a0" );      // $a0 = stack top * 4
        out.println( "mflo $a0" );           // 32 least significant bits of multiplication to $a0
        out.println( "addiu $sp, $sp, 4" );  // pop

        out.println( "sw $a0, 0($sp)" );     // push value to stack
        out.println( "addiu $sp, $sp, -4" );


        n.e1.accept( this );

        out.println( "lw $t1, 8($sp)" );     // e2
        out.println( "lw $t2, 0($a0)" );     // length
        out.println( "bge $t1, $t2, _array_index_out_of_bound_exception" );

        out.println( "lw $t1, 4($sp)" );     // $t1 = stack top
        out.println( "add $a0, $a0, $t1" );  // address + index
        out.println( "addiu $sp, $sp, 4" );  // pop
        out.println( "addiu $sp, $sp, 4" );  // pop e2
        out.println( "lw $a0, 0($a0)\n" );
    }

    // Exp e;
    // cgen: e.length
    public void visit( ArrayLength n )
    {
        n.e.accept( this );
        out.println( "lw $a0, 0($a0)\n" );
    }

    // Exp e;
    // Identifier i;
    // ExpList el;
    // cgen: e.i(el)
    public void visit( Call n )
    {
        n.e.accept( this );

        Class  prevClass  = currClass;
        Method prevMethod = currMethod;
        if ( null == callClass )
        {
            System.out.println( "Call class is null when call method " + n.i.toString() );
            System.exit( -1 );
        }

        String id = n.i.toString();
        if ( !callClass.containsMethod( id ) )
        {
            System.out.println( "Cannot find " + id + " in class " + callClass.getId() );
            System.exit( -1 );
        }
        Class  callClass2 = callClass; // backup callClass since callClass may change when evaluate parameters
        Method callMethod = callClass.getMethod( id );

        // frame layout
        // old fp
        // this
        // en
        // e...
        // e1
        out.println( "sw $fp, 0($sp) # push $fp, Call class " + callClass.getId() + " method " +
                     currMethod.getId() ); // push $fp
        out.println( "addiu $sp, $sp, -4" );
        out.println( "sw $a0, 0($sp) # push This " );
        out.println( "addiu $sp, $sp, -4\n" );

        // set parameter
        for ( int i = n.el.size() - 1; i >= 0; i-- )
        {
            n.el.elementAt( i ).accept( this );
            out.println( "sw $a0, 0($sp) # push e" + i );
            out.println( "addiu $sp, $sp, -4\n" );
        }

        currClass  = callClass2;
        currMethod = callMethod;
        // call body
        String label = get_function_label( currClass.getId(), currMethod.getId() );
        out.println( "jal " + label + "\n" );

        // handle return value
        out.println( "move $a0, $v0 # load return value" );
        // set callClass from return type
        set_call_class( currMethod.type() );

        currMethod = prevMethod;
        currClass  = prevClass;
    }

    // Exp e;
    // cgen: new int [e]
    public void visit( NewArray n )
    {
        n.e.accept( this );
        // li $a0, 24 # 5 elements, 4 bytes each, plus 4 bytes for length
        out.println( "addi $a0, $a0, 1 # NewArray" );   // size + 1 (length)
        out.println( "move $t2, $a0\n" );    // size of array
        out.println( "sw $a0, 0($sp)" );     // push value to stack
        out.println( "addiu $sp, $sp, -4" );
        out.println( "lw $t1, 4($sp)" );     // $t1 = stack top
        out.println( "li $a0, 4" );          // $a0 = 4
        out.println( "mult $t1, $a0" );      // $a0 = stack top * 4
        out.println( "mflo $a0" );           // 32 least significant bits of multiplication to $a0
        out.println( "addiu $sp, $sp, 4" );  // pop
        out.println( "li $v0, 9" );          // syscall with service 9 = allocate space on heap
        out.println( "syscall" );
        out.println( "move $a0, $v0\n" );    // store the address of A in stack

        // initialize
        out.println( "sw $a0, 0($sp) # initialize array" ); // push value to stack
        out.println( "addiu $sp, $sp, -4" );

        // set the size of the array
        out.println( "addi $t2, $t2, -1" );
        out.println( "sw $t2, 0($a0)\n" );   // set the size of the array

        String label1 = "$Initialize" + label_count++;
        String label2 = "$Ini_loop" + label_count++;
        String label3 = "$exit" + label_count++;
        out.println( label1 + ":\n" );
        out.println( "li $t0, 1 \n" ); // set the start index of for-loop
        out.println( label2 + ":\n" );
        out.println( "bgt $t0, $t2, " + label3 + "\n" ); // go to the branch exit if $t0 > $t2
        out.println( "addi $a0, $a0, 4" );        // go to the address of the current element
        out.println( "sw $zero, 0($a0)\n" );      // set the element to be 0
        out.println( "addi $t0, $t0, 1" );        // increase the index
        out.println( "j " + label2 );             // jump to the beginning of the loop

        out.println( label3 + ":\n" );


        out.println( "lw $a0, 4($sp)" );     // $t1 = stack top
        out.println( "addiu $sp, $sp, 4" );  // pop
    }

    // Identifier i;
    // cgen: new n
    public void visit( NewObject n )
    {
        Class c = symbolTable.getClass( n.i.toString() );
        // set callClass here
        callClass = c;
        if ( null == c )
        {
            System.out.println( "Cannot find class " + n.i.toString() );
            System.exit( -1 );
        }

        // initialize
        int size = get_object_size( n.i.toString() );
        out.println( "li $a0, " + size * 4 + " # NewObject" ); // size * 4 bytes
        out.println( "li $v0, 9" );           // syscall with service 9 = allocate space on heap
        out.println( "syscall" );
        out.println( "move $a0, $v0\n" );     // store the address of A in stack

        out.println( "sw $a0, 0($sp)" );      // push value to stack
        out.println( "addiu $sp, $sp, -4" );

        out.println( "li $t0, " + c.idx() );  // class idx
        out.println( "sw $t0, 0($a0)\n" );    // set the class idx of the array
        out.println( "addi $a0, $a0, 4" );    // go to the address of the current element
        out.println( "li $t0, " + size * 4 ); // object size
        out.println( "sw $t0, 0($a0)\n" );    // set the object size of the array
        out.println( "addi $a0, $a0, 4" );    // go to the address of the current element
        out.println( "li $t0, " + 0 );        // dispatch ptr
        out.println( "sw $t0, 0($a0)\n" );    // set the dispatch ptr of the array
        out.println( "li $t2, " + ( size - 3 ) ); // object attribute size

        if ( ( size - 3 ) < 0 )
        {
            System.out.println( "Class " + n.i.toString() + " object size " + size + " too small" );
            System.exit( -1 );
        }

        String label1 = "$Initialize" + label_count++;
        String label2 = "$Ini_loop" + label_count++;
        String label3 = "$exit" + label_count++;
        out.println( label1 + ":\n" );
        out.println( "li $t0, 0 \n" );            // set the start index of for-loop
        out.println( label2 + ":\n" );
        // bge or bgt???
        out.println( "bge $t0, $t2, " + label3 + "\n" ); // go to the branch exit if $t0 > $t2
        out.println( "addi $a0, $a0, 4" );        // go to the address of the current element
        out.println( "sw $zero, 0($a0)\n" );      // set the element to be 0
        out.println( "addi $t0, $t0, 1" );        // increase the index
        out.println( "j " + label2 );             // jump to the beginning of the loop

        out.println( label3 + ":\n" );


        out.println( "lw $a0, 4($sp)" );     // $t1 = stack top
        out.println( "addiu $sp, $sp, 4" );  // pop
    }

    // Exp e;
    // cgen: !e
    public void visit( Not n )
    {
        n.e.accept( this );
        out.println( "nor $a0, $a0, $a0 # Not \n" ); // nor $t1, $t1, $t1
    }

    // cgen: this
    public void visit( This n )
    {
        // set callClass here
        callClass = currClass;
        out.println( "lw $a0, " + 4 * ( currMethod.params.size() + 1 ) + "($fp) # load this\n" );
    }

    // int i;
    // cgen: Load immediate the value of n.i
    public void visit( IntegerLiteral n )
    {
        out.println( "li $a0, " + n.i + " # IntegerLiteral " + n.i + "\n" );
    }

    // cgen: Load immeidate the value of "true"
    public void visit( True n )
    {
        out.println( "li $a0, 0 # True" );
        out.println( "nor $a0, $a0, $a0 # Not \n" ); // nor $t1, $t1, $t1
    }

    // cgen: Load immeidate the value of "false"
    public void visit( False n )
    {
        out.println( "li $a0, 0 # False\n" );
    }

    // String s;
    // cgen: Load the value of the variable n.s (which can be a local variable, parameter, or field)
    public void visit( IdentifierExp n )
    {
        // local variable
        Variable v = null;
        if ( currMethod.containsParam( n.s ) )
        {
            v = currMethod.getParam( n.s );
            out.println( "lw $a0, " + 4 * ( v.idx() + 1 ) + "($fp) # load parameter " + v.id() + "\n" );

        }
        else if ( currMethod.containsVar( n.s ) )
        {
            v = currMethod.getVar( n.s );
            out.println( "lw $a0, " + -4 * ( v.idx() + 1 ) + "($fp) # load local variable " + v.id() + "\n" );
        }
        else if ( currClass.containsVar( n.s ) )
        {
            v = currClass.getVar( n.s );
            out.println( "lw $a0, " + 4 * ( currMethod.params.size() + 1 ) + "($fp) # load this" );
            out.println( "lw $a0, " + 4 * ( v.idx() + 3 ) + "($a0) # load object variable " + v.id() + "\n" );
        }
        else
        {
            System.out.println( "Cannot find " + n.s + " in method " + currMethod.getId() );
            System.exit( -1 );
        }
        // set callClass here
        set_call_class( v.type() );
    }

    void cgen_supporting_functions()
    {
        out.println(
            "_print_int: # System.out.println(int)\n" +
            "li $v0, 1\n" +
            "syscall\n" +
            "la $a0, newline\n" +
            "li $a1, 1\n" +
            "li $v0, 4   # print newline\n" +
            "syscall\n" +
            "jr $ra\n"
        );

        out.println(
            "_null_pointer_exception:\n" +
            "la $a0, msg_null_pointer_exception\n" +
            "li $a1, 23\n" +
            "li $v0, 4\n" +
            "syscall\n" +
            "li $v0, 10\n" +
            "syscall\n"
        );

        out.println(
            "_array_index_out_of_bound_exception:\n" +
            "la $a0, msg_index_out_of_bound_exception\n" +
            "li $a1, 29\n" +
            "li $v0, 4\n" +
            "syscall\n" +
            "li $v0, 10\n" +
            "syscall\n"
        );

        out.println(
            "_alloc_int_array: # new int [$a0]\n" +
            "addi $a2, $a0, 0  # Save length in $a2\n" +
            "addi $a0, $a0, 1  # One more word to store the length\n" +
            "sll $a0, $a0, 2   # multiple by 4 bytes\n" +
            "li $v0, 9         # allocate space\n" +
            "syscall\n" +
            "\n" +
            "sw $a2, 0($v0)    # Store array length\n" +
            "addi $t1, $v0, 4  # begin address = ($v0 + 4); address of the first element\n" +
            "add $t2, $v0, $a0 # loop until ($v0 + 4*(length+1)), the address after the last element\n" +
            "\n" +
            "_alloc_int_array_loop:\n" +
            "beq $t1, $t2, _alloc_int_array_loop_end\n" +
            "sw $0, 0($t1)\n" +
            "addi $t1, $t1, 4\n" +
            "j _alloc_int_array_loop\n" +
            "_alloc_int_array_loop_end:\n" +
            "\n" +
            "jr $ra\n"
        );
    }

    int get_object_size( String s )
    {
        Class c = symbolTable.getClass( s );
        if ( null == c )
        {
            System.out.println( "Cannot find class " + s );
            System.exit( -1 );
        }

        int field_size = 0;
        while ( c != null )
        {
            field_size += c.fields.size();
            if ( null == c.parent() )
            {
                c = null;
            }
            else
            {
                c = symbolTable.getClass( c.parent() );
            }
        }
        // Class Tag
        // Object Size
        // Dispatch Ptr
        // Attribute 1
        // Attribute 2 ...
        return field_size + 3;
    }

    String get_function_label( String class_name, String method_name )
    {
        return class_name + "_" + method_name + "_f_entry";
    }

    void set_call_class( Type type )
    {
        if ( ! ( type instanceof IdentifierType ) )
        {
            callClass = null;
            return;
        }

        IdentifierType id_type = ( IdentifierType ) type;
        if ( ! symbolTable.containsClass( id_type.s ) )
        {
            System.out.println( "Cannnot find class " + id_type.s );
            System.exit( -1 );
        }
        callClass = symbolTable.getClass( id_type.s );
    }
    
    void check_null_pointer( Type type )
    {
        if ( ! ( ( type instanceof IdentifierType ) || ( type instanceof IntArrayType ) ) )
        {
            return;
        }
        System.out.println( "beq, $a0, $zero, _null_pointer_exception\n");
    }
}

