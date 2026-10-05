package com.starstack.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "messages")
@Data
@NoArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String msg;

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    public Message(String name, String msg) {
        this.name = name;
        this.msg = msg;
    }
}