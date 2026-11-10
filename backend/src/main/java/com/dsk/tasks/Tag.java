package com.dsk.tasks;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A label shared by many tasks. Names are stored normalized (lowercase), so a
 * task carries a chip, not a free-text field.
 */
@Entity
@Table(name = "tag")
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String name;

    protected Tag() {
    }

    public Tag(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    /**
     * The name is the whole value: a tag serializes as {@code "work"}, so
     * {@code Task.tags} reads as {@code ["work","home"]} — the same shape the
     * request accepts.
     */
    @JsonValue
    public String getName() {
        return name;
    }
}
