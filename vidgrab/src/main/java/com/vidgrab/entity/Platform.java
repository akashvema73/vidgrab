package com.vidgrab.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "platforms")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Platform {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(nullable = false)
    private String domains;

    @Column(nullable = false)
    private boolean active = true;
}
