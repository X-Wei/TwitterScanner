package com.fractallabs.assigment;

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
    private int count; // count of occurrences (in current time interval)
    private final Object lock = new Object(); // used when modifying `count`
    private ArrayList<TSValue> trend; // store TSValues
    private Instant lastAnchor; // beginning moment of current interval
    static final int INTERVAL = 100; // time interval (in seconds)
    private Timer timer; // schedules task to be executed every INTERVAL seconds
    private final Logger logger = Logger.getLogger(TwitterScanner.class.getName());

    public static class TSValue{
        private final Instant timestamp;
        private final double val;
        private final int count;
        public TSValue(Instant ts, double v, int cnt){
            this.timestamp = ts;
            this.val = v;
            this.count = cnt;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public double getVal() {
            return val;
        }

        public int getCount() { return count; }
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

    private final TimerTask update = new TimerTask() {
        // this task will be executed every INTERVAL seconds
        @Override
        public void run() {
            lastAnchor = lastAnchor.plusSeconds(INTERVAL);
            synchronized (lock) {
                int lastCnt = (trend.size() == 0 ? 0:trend.get(trend.size() - 1).getCount() );
                double ratio = (count - lastCnt) * 100.0 / lastCnt;
                System.out.println(
                        String.format("[%s] Twitter trend for \"%s\" has %s by %.2f%% in the past %d seconds (%d tweets).",
                                Date.from(lastAnchor), companyName, (ratio >= 0 ? "increased" : "decreased"), Math.abs(ratio), INTERVAL, count));
                storeValue(new TSValue(lastAnchor, ratio, count));
                count = 0;
            }

        }
    };
    
    public static void main(String[] args) {
        TwitterScanner scanner = new TwitterScanner("Facebook");
        scanner.run();
    }

    public TwitterScanner(String company_name){
        this.companyName = company_name.toLowerCase();
        this.trend = new ArrayList<TSValue>();
        FileHandler fh;
        try { // configure the logger with handler and formatter
            fh = new FileHandler("twitters.log");
            SimpleFormatter formatter = new SimpleFormatter();
            fh.setFormatter(formatter);
            logger.addHandler(fh);
            logger.setUseParentHandlers(false);//remove console output
        } catch (IOException e) {e.printStackTrace();}

    }

    public void run(){
        TwitterStream twitterStream = new TwitterStreamFactory().getInstance();
        twitterStream.addListener(twitterListener);
        this.lastAnchor = Instant.now();
        twitterStream.sample();
        System.out.println(String.format("[%s] start monitoring \"%s\" on twitter.",
                                            Date.from(lastAnchor), companyName));
        this.timer = new Timer(true);
        this.timer.schedule(update,Date.from(lastAnchor.plusSeconds(INTERVAL)),(long)INTERVAL*1000);
        // `run()` method in `update` will be executed every INTERVAL seconds
    }

    private void storeValue(TSValue value) {
        this.trend.add(value);
    }

}

