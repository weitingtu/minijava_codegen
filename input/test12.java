class test
{
    public static void main( String[] a )
    {
        int x;
        int y;
        int [] A;
        Foo fobj;

        {
            System.out.println( fobj.foo() );
            System.out.println( fobj.bar() );
            System.out.println( 10 );
        }

    }
}

class Bar
{
    int a;
    int b;
    public int bar()
    {
        return 100;
    }
}

class Foo extends Bar
{
    int x;
    int y;
    public int foo()
    {
        return 999;
    }
}


