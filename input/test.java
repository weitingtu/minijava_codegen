class test
{
    public static void main( String[] a )
    {
        int x;
        int y;
        int [] A;
        Foo fobj;

        {
            y = 100;
            x = 10 + 4 + y;
            System.out.println( x );
            //System.out.println( 10 + 4 );
            //System.out.println( 10 - 4 );
            //System.out.println( 10 * 4 );
        }

    }
}

class Foo
{
    int x;
    int y;
}


