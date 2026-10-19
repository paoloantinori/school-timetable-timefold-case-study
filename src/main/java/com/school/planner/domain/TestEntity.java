package com.school.planner.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity
public class TestEntity extends PanacheEntityBase {
    @Id
    public Long id;
}
