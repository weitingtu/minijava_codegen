// Test
//   Instantiation of a class and invoking a method
class test21
{
    public static void main( String[] _str )
    {
        Foo f;
        int x;
        int y;
        {
            x = 15;
            y = 255;
            System.out.println( ( new Foo() ).f( x, y ) );
        }
    }
}

class Foo
{
    int a;
    int b;
    public int f( int x, int y )
    {
        System.out.println( x );
        System.out.println( y );
        a = x;
        b = y;
        System.out.println( a );
        System.out.println( b );
        return a;
    }
}


