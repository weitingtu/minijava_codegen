// Test
//   Instantiation of a class and invoking a method
class test21
{
    public static void main( String[] _str )
    {
        Foo f;
        {
            System.out.println( ( new Foo() ).f() );
        }
    }
}

class Foo
{
    int a;
    int b;
    public int f()
    {
        a = 999;
        b = 1000;
        System.out.println( a );
        System.out.println( b );
        return a;
    }
}


