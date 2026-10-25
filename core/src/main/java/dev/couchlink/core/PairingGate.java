package dev.couchlink.core;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.function.LongSupplier;

/** One-use pairing windows and hashed, revocable credentials. Secrets never go into discovery. */
public final class PairingGate {
    private final SecureRandom random=new SecureRandom();
    private final LongSupplier clock;
    private final Set<String> hashes;
    private String code="";
    private long expires;
    private int failures;
    public PairingGate(Set<String> hashes,LongSupplier clock){this.hashes=new HashSet<>(hashes);this.clock=clock;}
    public synchronized String newCode(){code=String.format(Locale.US,"%06d",random.nextInt(1000000));expires=clock.getAsLong()+300000;failures=0;return code;}
    public synchronized String displayCode(){return expires>clock.getAsLong() && failures<5?code:"------";}
    public synchronized String enroll(String supplied) {
        if(code.isEmpty() || expires<=clock.getAsLong() || failures>=5 || hashes.size()>=8) return null;
        if(!MessageDigest.isEqual(code.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))){failures++;return null;}
        byte[] bytes=new byte[32];random.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        hashes.add(hash(token));code="";expires=0;return token;
    }
    public synchronized boolean authorized(String token){
        if(token==null || !token.matches("[A-Za-z0-9_-]{43}"))return false;
        byte[] digest=hash(token).getBytes(StandardCharsets.US_ASCII);
        boolean found=false;
        for(String stored:hashes)found|=MessageDigest.isEqual(digest,stored.getBytes(StandardCharsets.US_ASCII));
        return found;
    }
    public synchronized void revokeAll(){hashes.clear();code="";expires=0;}
    public synchronized void revoke(String token){hashes.remove(hash(token));}
    public synchronized Set<String> snapshot(){return new HashSet<>(hashes);}
    public static String hash(String text){return fingerprint(text.getBytes(StandardCharsets.UTF_8));}
    public static String fingerprint(byte[] bytes){
        try {byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder s=new StringBuilder();for(byte b:digest)s.append(String.format(Locale.US,"%02x",b&255));return s.toString();}
        catch(GeneralSecurityException e){throw new IllegalStateException(e);}
    }
}
