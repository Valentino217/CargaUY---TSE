package uy.edu.fing.grupo07.CargaUY.security;

import java.time.LocalDate;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.*;
import jakarta.persistence.*;
import uy.edu.fing.grupo07.CargaUY.domain.entity.*;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;

@Singleton @Startup
public class BootstrapAdmin {
    @PersistenceContext(unitName="CargaUYPersistenceUnit") private EntityManager em;
    @PostConstruct
    public void iniciar() {
        if (!Boolean.parseBoolean(Config.valor("p1.bootstrap","false"))) return;
        if (!Boolean.parseBoolean(Config.valor("p1.entorno.local","false")))
            throw new IllegalStateException("Bootstrap solo permitido en entorno local");
        String uid = Config.requerido("bootstrap.admin.uid");
        if (!uid.matches("[a-zA-Z0-9._-]{1,64}")) throw new IllegalStateException("UID inválido");
        long existe = em.createQuery(
            "select count(u) from Usuario u where u.origenIdentidad='LDAP' and u.uidExterno=:uid",
            Long.class).setParameter("uid",uid).getSingleResult();
        if (existe>0) return; // No reactivar ni cambiar roles de una cuenta existente.
        int ci = Integer.parseInt(Config.requerido("bootstrap.admin.ci"));
        if (em.createQuery("select count(u) from Usuario u where u.ci=:ci",Long.class)
                .setParameter("ci",ci).getSingleResult()>0)
            throw new IllegalStateException("La CI de bootstrap ya está ocupada");
        Administrador admin = new Administrador(Config.requerido("bootstrap.admin.nombre"),
            Config.requerido("bootstrap.admin.mail"),LocalDate.parse(
                Config.requerido("bootstrap.admin.nacimiento")),ci,"{LDAP}");
        admin.setOrigenIdentidad("LDAP"); admin.setUidExterno(uid);
        admin.getRoles().add(TipoUsuario.ADMINISTRADOR);
        em.persist(admin);
        // No crea un usuario LDAP, no conoce su contraseña y no modifica datos existentes.
    }
}
