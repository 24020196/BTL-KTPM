package org.example.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "categories")
public class Category {
    @Id
    private Long id;
}
