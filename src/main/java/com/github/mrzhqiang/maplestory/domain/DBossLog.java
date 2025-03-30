package com.github.mrzhqiang.maplestory.domain;

import io.ebean.Model;
import io.ebean.annotation.NotNull;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "bosslog")
public class DBossLog extends Model {

    @Id
    Integer id;
    @NotNull
    @ManyToOne
    @JoinColumn(name = "character_id")
    DCharacter character;
    @NotNull
    String bossId;
    @NotNull
    @Column(name = "lastattempt")
    LocalDateTime lastAttempt;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public DCharacter getCharacter() {
        return character;
    }

    public void setCharacter(DCharacter character) {
        this.character = character;
    }

    public String getBossId() {
        return bossId;
    }

    public void setBossId(String bossId) {
        this.bossId = bossId;
    }

    public LocalDateTime getLastAttempt() {
        return lastAttempt;
    }

    public void setLastAttempt(LocalDateTime lastAttempt) {
        this.lastAttempt = lastAttempt;
    }
}
