package com.noku.cdn.javase;

import java.util.*;

public final class CDNQueue<T> implements Queue<T> {
    private final ArrayList<T> data = new ArrayList<>();
    public CDNQueue(){
    
    }
    
    public int size() {
        return data.size();
    }
    
    public boolean isEmpty() {
        return data.size() == 0;
    }
    
    public boolean contains(Object o) {
        return data.contains(o);
    }
    
    public Iterator<T> iterator() {
        return data.iterator();
    }
    
    public Object[] toArray() {
        return data.toArray(new Object[0]);
    }
    
    public <T1> T1[] toArray(T1[] a) {
        return data.toArray(a);
    }
    
    public boolean add(T t) {
        return data.add(t);
    }
    
    public boolean remove(Object o) {
        boolean ret = data.remove(o);
        data.remove(Collections.singleton(null));
        return ret;
    }
    
    public boolean containsAll(Collection<?> c) {
        return data.containsAll(c);
    }
    
    public boolean addAll(Collection<? extends T> c) {
        return data.addAll(c);
    }
    
    public boolean removeAll(Collection<?> c) {
        boolean ret = data.removeAll(c);
        data.remove(Collections.singleton(null));
        return ret;
    }
    
    public boolean retainAll(Collection<?> c) {
        return data.retainAll(c);
    }
    
    public void clear() {
        data.clear();
    }
    
    public boolean offer(T t) {
        return data.add(t);
    }
    
    public T remove() {
        if(data.size() == 0) throw new ArrayIndexOutOfBoundsException("Queue is empty.");
        T ret = data.get(0);
        this.data.remove(ret);
        return ret;
    }
    
    public T poll() {
        if(data.size() == 0) return null;
        
        T ret = data.get(0);
        this.data.remove(ret);
        return ret;
    }
    
    public T element() {
        if(data.size() == 0) throw new ArrayIndexOutOfBoundsException("Queue is empty.");
        return data.get(0);
    }
    
    public T peek() {
        if(data.size() == 0) return null;
        return data.get(0);
    }
}
