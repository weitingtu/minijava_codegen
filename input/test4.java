// Test
//   Array (NewArray, ArrayLookup, ArrayLength)
class test18
{
    public static void main( String[] a )
    {
        int [] A;
        {
            A = new int [10];
            System.out.println( A.length );

            A[0] = 0;
            System.out.println( A[0] );
            A[1] = 1;
            System.out.println( A[1] );
            A[9] = 9;
            System.out.println( A[9] );
            A[10] = 10;
            System.out.println( A[10] );
            A[11] = 11;
            System.out.println( A[11] );
        }
    }
}

