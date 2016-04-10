package com.fractallabs.test;
import static org.junit.Assert.assertEquals;
import com.fractallabs.assignment.TwitterScanner;


public class TwitterScannerTest {
    public static void main(String[] args){
        testRatioCalculation();
    }

    public static void testRatioCalculation(){
        int[] testCounts = {100, 70, 70, 0, 0, 200, 100};
        double[] testRatios = {Double.POSITIVE_INFINITY, -30, 0, -100, Double.NaN, Double.POSITIVE_INFINITY, -50};
        TwitterScanner ts = new TwitterScanner("foo");
        for(int c:testCounts){
            ts.count = c;
            ts.updatePerInterval.run();
        }


        for (int i = 0; i <testCounts.length; i++) {
            try{
                assertEquals(testRatios[i], ts.trend.get(i).getVal(), 0.0001);
            }catch (AssertionError e){
                e.printStackTrace();
            }
        }
        System.out.println("all testcase passed!");
    }
}
