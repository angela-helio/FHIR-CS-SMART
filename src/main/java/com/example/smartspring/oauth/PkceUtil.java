package com.example.smartspring.oauth;
import java.security.SecureRandom; import java.util.Base64; import java.security.MessageDigest; import java.nio.charset.StandardCharsets;
public final class PkceUtil {
  private static final SecureRandom RNG=new SecureRandom(); private PkceUtil(){}
  public static String generateCodeVerifier(){ byte[] b=new byte[64]; RNG.nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
  public static String codeChallengeS256(String verifier){ try{ MessageDigest md=MessageDigest.getInstance("SHA-256"); return Base64.getUrlEncoder().withoutPadding().encodeToString(md.digest(verifier.getBytes(StandardCharsets.US_ASCII))); }catch(Exception e){ throw new RuntimeException(e);}}
}
