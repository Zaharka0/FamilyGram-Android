package org.telegram.messenger.family;
import java.util.*;
/** Stable section IDs; saved choices survive upgrades and ignore unknown entries. */
public final class FamilySections {
    public static final List<String> ALL=Collections.unmodifiableList(Arrays.asList("tasks","calendar","album","ours","hug","question","goals","watch","voice","games","bill","stats"));
    private FamilySections(){}
    public static List<String> order(String csv){
        LinkedHashSet<String> result=new LinkedHashSet<>();
        for(String id:csv.split(","))if(ALL.contains(id))result.add(id);
        result.addAll(ALL);return new ArrayList<>(result);
    }
    public static Set<String> selected(String csv){
        Set<String> result=new HashSet<>();for(String id:csv.split(","))if(ALL.contains(id))result.add(id);return result;
    }
    public static List<String> visible(String order,String hidden,String favorites){
        List<String> result=order(order);Set<String> hide=selected(hidden),pin=selected(favorites);
        result.removeAll(hide);result.sort((a,b)->Boolean.compare(pin.contains(b),pin.contains(a)));return result;
    }
}
