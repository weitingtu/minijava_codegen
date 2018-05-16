// Test
//   Array (NewArray, ArrayLookup, ArrayLength)
class test18
{
    public static void main( String[] a )
    {
        int [] A;
        int x;
        {
            A = new int [10];
            System.out.println( A.length );

            A[0] = 0;
            A[8] = 8;
            A[9] = 9;
            x = A[0];
            System.out.println( x );
            x = A[8];
            System.out.println( x );
            x = A[9];
            System.out.println( x );
            x = A[10];
            System.out.println( x );
        }
    }
}

