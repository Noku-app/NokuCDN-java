package com.noku.utils;

import java.util.Map;

public class DynamicEntry<U, V> implements Map.Entry<U, V> {
    private final U key;
    private V val;
    
    public DynamicEntry(U key, V val){
        this.key = key;
        this.val = val;
    }
    @Override
    public U getKey() {
        return key;
    }
    
    @Override
    public V getValue() {
        return val;
    }
    
    @Override
    public V setValue(V value) {
        return (val = value);
    }
}
