package com.noku.utils;

import java.util.Collection;

public final class DynamicArray<T>{
    private final DynamicBuilder<T> builder;
    private T[] data;
    
    
    public DynamicArray(DynamicBuilder<T> builder){
        this.builder = builder;
        data = builder.build(0);
    }
    
    public boolean addAll(Collection<? extends T> data){
        try {
            T[] fin = builder.build(size());
            System.arraycopy(this.data, 0, fin, 0, size());
    
            while(data.iterator().hasNext()) {
                T d = data.iterator().next();
                fin = add(d, builder, fin);
            }
    
            this.data = fin;
            return true;
        } catch (Exception e){
            return false;
        }
    }
    
    public boolean add(T t){
        try{
            this.data = add(t, builder, data);
            
            return true;
        } catch (Exception e){
            return false;
        }
    }
    
    public T[] add(T t, DynamicBuilder<T> builder, T[] data){
        try{
            int nSize = size() + 1;
            T[] nData = builder.build(nSize);
            
            System.arraycopy(data, 0, nData, 0, size());
            nData[size()] = t;
            
            return nData;
        } catch (Exception e){
            return data;
        }
    }
    
    public boolean remove(int index){
        if(index < 0) return false;
        if(index >= size()) return false;
        try{
            T[] nData = builder.build(size() - 1);
            if(size() < 2){ this.data = nData; return true; }
            if(index == 0 || index == size() - 1){
                System.arraycopy(data, index == 0 ? 1 : 0, nData, 0, nData.length);
            } else {
                System.arraycopy(data, 0, nData, 0, index);
                System.arraycopy(data, index + 1, nData, index, nData.length - index);
            }
            
            this.data = nData;
            return true;
        } catch (Exception e){
            return false;
        }
    }
    
    public boolean remove(T val, boolean identity){
        int ret = -1;
        for(int i = 0; i < size(); i++){
            if(identity ? data[i] == val : data[i].equals(val)){
                ret = i;
                break;
            }
        }
        return remove(ret);
    }
    
    public int indexOf(T val, boolean identity){
        int ret = -1;
        for(int i = 0; i < size(); i++){
            if(identity ? data[i] == val : data[i].equals(val)){
                ret = i;
                break;
            }
        }
        return ret;
    }
    
    public boolean contains(T val, boolean identity){
        for(int i = 0; i < size(); i++){
            if(identity ? data[i] == val : data[i].equals(val)){
                return true;
            }
        }
        return false;
    }
    
    public T popFirst(){
        T ret = data[0];
        remove(0);
        
        return ret;
    }
    
    public T popLast(){
        int index = size() - 1;
        T ret = data[index];
        remove(index);
        
        return ret;
    }
    
    public T peekFirst(){
        return data[0];
    }
    
    public T peekLast(){
        return data[size() - 1];
    }
    
    public T get(int index){
        return data[index];
    }
    
    public void clear(){
        this.data = builder.build(0);
    }
    
    public int size(){
        return data.length;
    }
    
    public boolean set(T val, int index){
        if(size() - 1 < index){
            T[] nData = builder.build(index + 1);
            System.arraycopy(data, 0, nData, 0, data.length);
            nData[index] = val;
            
            return false;
        } else {
            this.data[index] = val;
            
            return true;
        }
    }
    
    public Class<T> getTypeClass(){
        if(size() == 0) return null;
        T dat = peekFirst();
        
        return (Class<T>)dat.getClass();
    }
}
