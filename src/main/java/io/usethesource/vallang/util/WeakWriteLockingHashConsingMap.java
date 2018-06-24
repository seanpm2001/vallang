package io.usethesource.vallang.util;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

public class WeakWriteLockingHashConsingMap<T> implements HashConsingMap<T> {
    private static class WeakReferenceWrap<T> extends WeakReference<T> {
        private final int hash;

        public WeakReferenceWrap(int hash, T referent, ReferenceQueue<? super T> q) {
            super(referent, q);
            this.hash = hash;
        }
        
        @Override
        public int hashCode() {
            return hash;
        }
        @SuppressWarnings("unchecked")
        @Override
        public boolean equals(Object obj) {
            if (obj == null || obj.hashCode() != hash) {
                return false;
            }
            T self = get();
            if (self == null) {
            	return false;
            }
            T other;
            if ((obj instanceof WeakReferenceWrap<?>)) {
                other = ((WeakReferenceWrap<T>) obj).get();
            }
            else {
            	other = ((LookupWrapper<T>)obj).ref;
            }
            return other != null && other.equals(self);
        }
    }
    
    private static final class LookupWrapper<T> {
    	private final int hash;
		private final T ref;
    	public LookupWrapper(int hash, T ref) {
			this.hash = hash;
			this.ref = ref;
		}
    	
    	@Override
    	public int hashCode() {
    		return hash;
    	}
    	
    	@Override
    	public boolean equals(Object obj) {
    		if (obj instanceof WeakReferenceWrap<?>) {
    			return obj.equals(this);
    		}
    		return false;
    	}
    }
    

    private final Map<WeakReferenceWrap<T>,WeakReferenceWrap<T>> data = new HashMap<>();
    private final ReferenceQueue<T> cleared = new ReferenceQueue<>();
    
    
    public WeakWriteLockingHashConsingMap() {
        Cleanup.register(this);
    }
    

    @Override
    public T get(T key) {
    	LookupWrapper<T> keyLookup = new LookupWrapper<>(key.hashCode(), key);
        @SuppressWarnings("unlikely-arg-type")
		WeakReferenceWrap<T> result = data.get(keyLookup);
        if (result != null) {
        	T actualResult = result.get();
        	if (actualResult != null) {
                return actualResult;
        	}
        }
        synchronized (this) {
        	WeakReferenceWrap<T> keyPut = new WeakReferenceWrap<>(keyLookup.hash, key, cleared);
        	while (true) {
        		result = data.merge(keyPut, keyPut, (oldValue, newValue) -> oldValue.get() == null ? newValue : oldValue);
        		if (result == keyPut) {
        			// a regular put
        			return key;
        		}
        		else {
        			T actualResult = result.get();
        			if (actualResult != null) {
        				// value was already in there, and also still held a reference (which is true for most cases)
        				keyPut.clear(); // avoid getting a cleared reference in the queue
        				return actualResult;
        			}
        		}
        	}
		}
    }

    private void cleanup() {
        WeakReferenceWrap<?> c = (WeakReferenceWrap<?>) cleared.poll();
        if (c != null) {
        	synchronized (this) {
        		while (c != null) {
                    data.remove(c);
        			c = (WeakReferenceWrap<?>) cleared.poll();
                }
			}
        }
    }

    private static class Cleanup extends Thread {
        private final ConcurrentLinkedDeque<WeakReference<WeakWriteLockingHashConsingMap<?>>> caches;

        private Cleanup() { 
            caches = new ConcurrentLinkedDeque<>();
            setDaemon(true);
            setName("Cleanup Thread for " + WeakWriteLockingHashConsingMap.class.getName());
            start();
        }

        private static class InstanceHolder {
            static final Cleanup INSTANCE = new Cleanup();
        }

        public static void register(WeakWriteLockingHashConsingMap<?> cache) {
            InstanceHolder.INSTANCE.caches.add(new WeakReference<>(cache));
        }

        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
                try {
                    Iterator<WeakReference<WeakWriteLockingHashConsingMap<?>>> it = caches.iterator();
                    while (it.hasNext()) {
                        WeakWriteLockingHashConsingMap<?> cur = it.next().get();
                        if (cur == null) {
                            it.remove();
                        }
                        else {
                            cur.cleanup();
                        }
                    }
                }
                catch (Throwable e) {
                    System.err.println("Cleanup thread failed with: " + e.getMessage());
                    e.printStackTrace(System.err);
                }
            }
        }



    }


}
