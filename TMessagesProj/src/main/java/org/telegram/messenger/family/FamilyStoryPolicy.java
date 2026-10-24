package org.telegram.messenger.family;
/** Privacy decisions share the same family/partner exception for viewing and presentation. */
public final class FamilyStoryPolicy {
    private FamilyStoryPolicy(){}
    public static boolean exempt(long peer,long self,long partner,boolean family){return peer!=0&&(peer==self||peer==partner&&partner!=0||family);}
    public static boolean hideView(boolean ghost,boolean exempt){return ghost&&!exempt;}
    public static boolean show(boolean familyOnly,boolean exempt){return !familyOnly||exempt;}
}
