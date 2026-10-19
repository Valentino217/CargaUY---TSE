package uy.edu.fing.grupo07.CargaUY.domain.entity;

import java.time.Instant;
import jakarta.persistence.*;


@Entity @Table(name="flujos_identidad")
public class FlujoIdentidad {
	@Id @Column(length=64)
	private String hash;
	
	@Column(nullable=false,length=43)
	private String challenge;
	
	@Column(nullable=false,length=43)
	private String nonce;
	
	@Column(nullable=false)
	private Instant vence;
	
	protected FlujoIdentidad(){
	}
	 
	public FlujoIdentidad(String hash,String challenge,String nonce,Instant vence){
		this.hash=hash;this.challenge=challenge;this.nonce=nonce;this.vence=vence;
	}
	 
	 public String getChallenge(){
		 return challenge;
	}
	 
	 public String getNonce(){
		 return nonce;
	}
	 
	 public Instant getVence(){
		 return vence;
	}
}