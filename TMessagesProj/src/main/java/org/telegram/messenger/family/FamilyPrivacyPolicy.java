package org.telegram.messenger.family;
import java.util.Set;
/** Never replace a server exception list with a partially resolved local list. */
public final class FamilyPrivacyPolicy {
    private FamilyPrivacyPolicy() {}
    public static boolean canWrite(boolean connected,Set<Long> desired,Set<Long> resolved) {
        return connected && resolved.containsAll(desired);
    }
    public static boolean matches(Set<Long> desired,Set<Long> allowed,boolean nobody,boolean otherRules) {
        return nobody && !otherRules && desired.equals(allowed);
    }
}
