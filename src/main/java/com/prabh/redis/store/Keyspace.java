package com.prabh.redis.store;

import java.util.concurrent.ConcurrentHashMap;

public class Keyspace {
    private final ConcurrentHashMap<Key, byte[]> data = new ConcurrentHashMap<>();

    public void set(Key key, byte[] value) {
        data.put(key, value);
    }
    public byte[] get(Key key) {
        return data.getOrDefault(key, null);
    }

    public boolean delete(Key key){
        return data.remove(key) != null;
    }

    public boolean exists(Key key){
        return data.containsKey(key);
    }
}
