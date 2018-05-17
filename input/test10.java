// Test
//   Class and method
class test23
{
    public static void main( String[] _str )
    {
        Foo f;
        int a;
        int [] A;
        {
            //A = new int [10];
            //A[0] = 1000;
            //A[2] = 2000;
            f = new Foo();
            //a = 5;
            //System.out.println( a );
            a = f.m1( A );
            //System.out.println( a );
        }
    }
}

class Foo
{
    public int m1( int [] A )
    {
        A[1] = 999;

        System.out.println( A[0] );
        System.out.println( A[1] );
        return A[2];
    }

}

