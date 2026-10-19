package uy.edu.fing.grupo07.CargaUY.service;

public final class Validaciones {
    private Validaciones() {}
    public static String texto(String valor, String campo, int maximo) {
        if (valor == null || valor.isBlank())
            throw new ErrorNegocio(campo + " es obligatorio");
        String limpio = valor.trim();
        if (limpio.length() > maximo)
            throw new ErrorNegocio(campo + " supera " + maximo + " caracteres");
        return limpio;
    }
    public static int positivo(int valor, String campo) {
        if (valor <= 0) throw new ErrorNegocio(campo + " debe ser positivo");
        return valor;
    }
}