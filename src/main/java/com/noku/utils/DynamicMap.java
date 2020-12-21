package com.noku.utils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DynamicMap<U, V> implements Map<U, V> {
    private final DynamicArray<U> keys;
    private final DynamicArray<V> values;
    
    public DynamicMap(DynamicBuilder<U> uBuilder, DynamicBuilder<V> vBuilder){
        values = new DynamicArray<>(vBuilder);
        keys = new DynamicArray<>(uBuilder);
    }
    
    public int size() {
        return values.size();
    }
    
    @Override
    public boolean isEmpty() {
        return size() == 0;
    }
    
    @Override
    public boolean containsKey(Object key) {
        Class<U> type = keys.getTypeClass();
        if(type == null) return false;
        
        if(type.isInstance(key)) return keys.contains((U)key, false);
        return false;
    }
    
    @Override
    public boolean containsValue(Object value) {
        Class<V> type = values.getTypeClass();
        if(type == null) return false;
    
        if(type.isInstance(value)) return values.contains((V)value, false);
        return false;
    }
    
    @Override
    public V get(Object key) {
        if(!containsKey(key)) return null;
        int index = keys.indexOf((U)key, false);
        return values.get(index);
    }
    
    @Override
    public V put(U key, V value) {
        if(containsKey(key)){
            int index = keys.indexOf(key, false);
            values.set(value, index);
            
            return value;
        } else {
            return keys.add(key) && values.add(value) ? value : null;
        }
    }
    
    @Override
    public V remove(Object key) {
        if(!containsKey(key)) return null;
        int index = keys.indexOf((U)key, false);
        V ret = values.get(index);
        return keys.remove(index) && values.remove(index) ? ret : null;
    }
    
    @Override
    public void putAll(Map<? extends U, ? extends V> m) {
        Set<? extends U> set = m.keySet();
        for(U u : set) put(u, m.get(u));
    }
    
    @Override
    public void clear() {
        values.clear();
        keys.clear();
    }
    
    @Override
    public Set<U> keySet() {
        Set<U> ret = new HashSet<>();
        for(int i = 0; i < keys.size(); i++) ret.add(keys.get(i));
        return ret;
    }
    
    @Override
    public Collection<V> values() {
        Collection<V> ret = new HashSet<>();
        for(int i = 0; i < values.size(); i++) ret.add(values.get(i));
        return ret;
    }
    
    @Override
    public Set<Entry<U, V>> entrySet() {
        Set<Entry<U, V>> ret = new HashSet<>();
        for(int i = 0; i < keys.size(); i++){
            ret.add(new DynamicEntry<>(keys.get(i), values.get(i)));
        }
        return ret;
    }
    
    public DynamicArray<U> keySetAsDynamicArray() {
        return keys;
    }
}
