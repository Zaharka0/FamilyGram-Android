package org.telegram.messenger.family;
import java.util.LinkedHashMap;
import java.util.Map;
/** Monotonic-time debounce. Bounded and account-owned by the caller. */
public final class FamilyTapGuard {
    private final Map<String,Long> taps=new LinkedHashMap<>();
    public synchronized boolean accept(String key,long now,long window){
        Long previous=taps.get(key);
        if(previous!=null&&now>=previous&&now-previous<window)return false;
        taps.put(key,now);
        if(taps.size()>256)taps.remove(taps.keySet().iterator().next());
        return true;
    }
}
