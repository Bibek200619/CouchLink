package dev.couchlink.core;

import javax.net.ssl.*;
import java.security.*;
import java.security.cert.*;
import java.util.Locale;

/** Pairing accepts the 12 hex characters read from the TV. Later connections pin all 256 bits. */
public final class PinnedTls {
    private PinnedTls(){}
    public static SSLSocketFactory factory(String pin) throws GeneralSecurityException {
        String normalized=pin.replace(" ","").replace(":","").toLowerCase(Locale.US);
        if(!normalized.matches("([0-9a-f]{12}|[0-9a-f]{64})"))throw new GeneralSecurityException("Enter the 12-character TV fingerprint");
        X509TrustManager trust=new X509TrustManager(){
            public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
            public void checkClientTrusted(X509Certificate[] chain,String auth)throws CertificateException{throw new CertificateException("Not a server");}
            public void checkServerTrusted(X509Certificate[] chain,String auth)throws CertificateException{
                if(chain==null||chain.length==0)throw new CertificateException("Missing TV certificate");
                String actual=PairingGate.fingerprint(chain[0].getEncoded());
                if(!MessageDigest.isEqual(actual.substring(0,normalized.length()).getBytes(java.nio.charset.StandardCharsets.US_ASCII),normalized.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))throw new CertificateException("TV fingerprint does not match");
                chain[0].checkValidity();
            }
        };
        SSLContext context=SSLContext.getInstance("TLS");context.init(null,new TrustManager[]{trust},new SecureRandom());return context.getSocketFactory();
    }
}
