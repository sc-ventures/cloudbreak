package com.sequenceiq.it;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public class StructuredEventAwaiter {

    private Map<String, ClusterStatusAwaiter> awaiters;

    public StructuredEventAwaiter() {
        awaiters = new ConcurrentHashMap<>();
    }

    public Map<String, ClusterStatusAwaiter> getAwaiters() {
        return awaiters;
    }

    public CountDownLatch getLatch(String key) {
        return awaiters.get(key).getLatch();
    }

    public void addClusterAwaiter(String clusterName, String awaitedStatus) {
        if (clusterName == null || awaitedStatus == null) {
            throw new RuntimeException("Incorrect await condition added clusterName: "
                    + String.valueOf(clusterName) + "awaitedStatus: " + String.valueOf(awaitedStatus));
        }
        awaiters.put(clusterName, new ClusterStatusAwaiter(clusterName, awaitedStatus));
    }

    public static class ClusterStatusAwaiter {

        private String clusterName;

        private String clusterStatus;

        private CountDownLatch latch;

        ClusterStatusAwaiter(String clusterName, String clusterStatus) {
            this.clusterName = clusterName;
            this.clusterStatus = clusterStatus;
            latch = new CountDownLatch(1);
        }

        public String getClusterName() {
            return clusterName;
        }

        public String getClusterStatus() {
            return clusterStatus;
        }

        public CountDownLatch getLatch() {
            return latch;
        }
    }
}
