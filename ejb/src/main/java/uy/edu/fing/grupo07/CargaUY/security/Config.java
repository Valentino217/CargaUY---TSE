package uy.edu.fing.grupo07.CargaUY.security;

public final class Config {
    private Config() {}
    public static String valor(String clave, String defecto) {
        String valor = System.getProperty(clave);
        
        if (valor == null || valor.isBlank())
            valor = System.getenv(clave.toUpperCase().replace('.', '_'));
        
        return valor == null || valor.isBlank() ? defecto : valor;
    }
    
    public static String requerido(String clave) {
        String v = valor(clave, "");
        
        if (v.isBlank()) 
        	throw new IllegalStateException("Falta configurar " + clave);
        
        return v;
    }
}