package com.fractallabs.assignment;

import twitter4j.*;

import java.io.IOException;
import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;


public class TwitterScanner {
    private final String companyName;
    public int count; // count of occurrences (in current time interval)
    private final Object lock = new Object(); // used when modifying `count`
    public ArrayList<TSValue> trend; // store TSValues
    private Instant lastAnchor; // beginning moment of current interval
    static final int INTERVAL = 100; // time interval (in seconds)
    private Timer timer; // schedules task to be executed every INTERVAL seconds
    private final Logger logger = Logger.getLogger(TwitterScanner.class.getName());

    public static class TSValue{
        private final Instant timestamp; // timestamp of the start of last interval
        private final double val; // relative change ratio (in percent)
        private final int count;
        private final int lastCount;
        public TSValue(Instant ts, int cnt, int lastCnt){
            this.timestamp = ts;
            this.count = cnt;
            this.lastCount = lastCnt;
            double ratio = (this.count - this.lastCount) * 100.0 / this.lastCount;
            this.val = ratio;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public double getVal() {
            return val;
        }

        public int getCount() { return count; }

    }

    public static void main(String[] args) {
        TwitterScanner scanner = new TwitterScanner("Facebook");
        scanner.start();
    }

    public TwitterScanner(String company_name){
        this.companyName = company_name.toLowerCase();
        this.trend = new ArrayList<TSValue>();
        this.lastAnchor = Instant.now();
        try { // configure the logger with handler and formatter
            FileHandler fh;
            fh = new FileHandler("twitters.log");
            SimpleFormatter formatter = new SimpleFormatter();
            fh.setFormatter(formatter);
            logger.addHandler(fh);
            logger.setUseParentHandlers(false);//remove console output
        } catch (IOException e) {e.printStackTrace();}

    }

    public void start(){
        TwitterStream twitterStream = new TwitterStreamFactory().getInstance();
        twitterStream.addListener(twitterListener);
        this.lastAnchor = Instant.now();
        twitterStream.sample();
        System.out.println(String.format("[%s] start monitoring \"%s\" on twitter.",
                Date.from(lastAnchor), companyName));
        this.timer = new Timer(true);
        this.timer.schedule(updatePerInterval,Date.from(lastAnchor.plusSeconds(INTERVAL)),(long)INTERVAL*1000);
        // `start()` method in `updatePerInterval` will be executed every INTERVAL seconds
    }

    private final StatusListener twitterListener= new StatusListener(){
        // this listener handles the inflow of twitters
        @Override
        public void onStatus(Status status) {
            String text = status.getText();
            if(text.toLowerCase().contains(companyName)){
                logger.info( String.format("[%s] %s", status.getCreatedAt(), text) );
                long timeDiff =Duration.between(lastAnchor, status.getCreatedAt().toInstant()).getSeconds();
                if(timeDiff>=0 && timeDiff <= INTERVAL)
                    synchronized (lock){ count++;}
            }
        }

        @Override
        public void onException(Exception e) {e.printStackTrace();}

        @Override
        public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {}

        @Override
        public void onTrackLimitationNotice(int i) {}

        @Override
        public void onScrubGeo(long l, long l1) {}

        @Override
        public void onStallWarning(StallWarning stallWarning) {}
    };

    public final TimerTask updatePerInterval = new TimerTask() {
        // this task will be executed every INTERVAL seconds
        @Override
        public void run() {
            lastAnchor = lastAnchor.plusSeconds(INTERVAL);
            synchronized (lock) {
                int lastCnt = (trend.size() == 0 ? 0:trend.get(trend.size() - 1).getCount() );
                TSValue tsv = new TSValue(lastAnchor, count, lastCnt);
                System.out.println(report(tsv));
                storeValue(tsv);
                count = 0;
            }
        }
    };

    private String report(TSValue tsv){ // interpret the tsvalue with a user-friendly report sentence
        double ratio = tsv.getVal();
        if(ratio==0)
            return String.format(
                    "[%s] Twitter trend for \"%s\" has stayed unchanged in the past %d seconds (%d tweets).",
                    Date.from(lastAnchor), companyName, INTERVAL, count);
        else if (Double.isInfinite(ratio))
            return String.format(
                    "[%s] Twitter trend for \"%s\" has jumped from 0 to a positive value in the past %d seconds (%d tweets).",
                    Date.from(lastAnchor), companyName, INTERVAL, count);
        else if (Double.isNaN(ratio)) // NaN appears when we do 0/0
            return String.format(
                    "[%s] Twitter trend for \"%s\" has been stayed 0 in the past %d seconds.",
                    Date.from(lastAnchor), companyName, INTERVAL);
        return String.format("[%s] Twitter trend for \"%s\" has %s by %.2f%% in the past %d seconds (%d tweets).",
                Date.from(lastAnchor), companyName, (ratio >= 0 ? "increased" : "decreased"),
                Math.abs(ratio), INTERVAL, count);
    }

    private void storeValue(TSValue tsvalue) {
        this.trend.add(tsvalue);
        logger.info( String.format("%s: count=%d, change ratio=%.2f%%",
                tsvalue.getTimestamp(), tsvalue.getCount(), tsvalue.getVal() ) );
    }

}

