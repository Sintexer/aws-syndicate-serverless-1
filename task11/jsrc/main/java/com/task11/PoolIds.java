package com.task11;

public class PoolIds {
    private final String poolId;
    private final String clientId;

    public PoolIds(String poolId, String clientId) {
        this.poolId = poolId;
        this.clientId = clientId;
    }

    public String getPoolId() {
        return poolId;
    }

    public String getClientId() {
        return clientId;
    }

    @Override
    public String toString() {
        return "PoolIds{" +
                "poolId='" + poolId + '\'' +
                ", clientId='" + clientId + '\'' +
                '}';
    }
}
