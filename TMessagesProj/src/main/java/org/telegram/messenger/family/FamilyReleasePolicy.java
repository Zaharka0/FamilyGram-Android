package org.telegram.messenger.family;
import java.net.URI;
import java.util.regex.*;
/** Public release tags and URLs only. Actions artifacts require authentication and are not APK URLs. */
public final class FamilyReleasePolicy {
    public static final String REPO="Zaharka0/FamilyGram-Android";
    private static final Pattern TAG=Pattern.compile("^(?:familygram-|v)?(\\d{1,6})\\.(\\d{1,6})\\.(\\d{1,6})(?:-b(\\d{1,10}))?$");
    private FamilyReleasePolicy(){}
    private static long[] parse(String tag){
        Matcher m=TAG.matcher(tag);if(!m.matches())throw new IllegalArgumentException("Invalid release tag");
        return new long[]{Long.parseLong(m.group(1)),Long.parseLong(m.group(2)),Long.parseLong(m.group(3)),m.group(4)==null?0:Long.parseLong(m.group(4))};
    }
    public static boolean valid(String tag){try{parse(tag);return true;}catch(Exception e){return false;}}
    public static int compare(String a,String b){long[] x=parse(a),y=parse(b);for(int i=0;i<4;i++){int c=Long.compare(x[i],y[i]);if(c!=0)return c;}return 0;}
    public static boolean newer(String tag,String version,long installedCode){
        long[] remote=parse(tag),local=parse(version);for(int i=0;i<3;i++){if(remote[i]!=local[i])return remote[i]>local[i];}
        return remote[3]>installedCode;
    }
    public static boolean trusted(String url){
        try{URI u=new URI(url);String path=u.getPath();return "https".equals(u.getScheme())&&"github.com".equalsIgnoreCase(u.getHost())&&u.getUserInfo()==null&&(u.getPort()==-1||u.getPort()==443)&&u.getQuery()==null&&u.getFragment()==null&&path!=null&&path.startsWith("/"+REPO+"/releases/download/")&&path.equals(u.normalize().getPath())&&!path.contains("/../")&&!path.contains("/./")&&!path.contains("\\")&&path.endsWith(".apk");}catch(Exception e){return false;}
    }
}
