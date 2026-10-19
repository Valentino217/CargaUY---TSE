package uy.edu.fing.grupo07.CargaUY.security;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

public final class Cripto {
    private static final SecureRandom RANDOM = new SecureRandom();
    private Cripto() {
    }
    
    public static String aleatorio() {
        byte[] datos = new byte[32]; RANDOM.nextBytes(datos);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(datos);
    }
    
    public static String hash(String valor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(valor.getBytes(StandardCharsets.UTF_8)));
        } 
        catch (NoSuchAlgorithmException e) { 
        	throw new IllegalStateException(e); 
        }
    }
    
    public static String pkce(String valor) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.US_ASCII)));
        } 
        catch (NoSuchAlgorithmException e) { 
        	throw new IllegalStateException(e); 
        }
    }
    
    public static boolean igual(String a, String b) {
        return a != null && b != null && MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}