package com.openquiz.repository;

import com.openquiz.domain.models.User;
import com.openquiz.util.Ids;
import com.openquiz.util.Json;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {

    private final DSLContext dsl;

    public UserRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public User upsertByEmail(String email, String name, String avatarUrl) {
        Optional<User> existing = findByEmail(email);
        if (existing.isPresent()) {
            return existing.get();
        }
        String id = Ids.uuid();
        long now = Instant.now().getEpochSecond();
        dsl.insertInto(Tables.USERS)
            .columns(Tables.USERS_ID, Tables.USERS_EMAIL, Tables.USERS_NAME,
                    Tables.USERS_AVATAR, Tables.USERS_ROLE, Tables.USERS_CREATED_AT)
            .values(id, email, name, avatarUrl, "ADMIN", now)
            .execute();
        return new User(id, email, name, avatarUrl, "ADMIN", Instant.ofEpochSecond(now));
    }

    public Optional<User> findByEmail(String email) {
        return dsl.selectFrom(Tables.USERS)
                .where(Tables.USERS_EMAIL.eq(email))
                .fetchOptional(r -> new User(
                        r.get(Tables.USERS_ID), r.get(Tables.USERS_EMAIL),
                        r.get(Tables.USERS_NAME), r.get(Tables.USERS_AVATAR),
                        r.get(Tables.USERS_ROLE), Instant.ofEpochSecond(r.get(Tables.USERS_CREATED_AT))));
    }

    public List<User> findAll() {
        return dsl.selectFrom(Tables.USERS)
                .fetch(r -> new User(
                        r.get(Tables.USERS_ID), r.get(Tables.USERS_EMAIL),
                        r.get(Tables.USERS_NAME), r.get(Tables.USERS_AVATAR),
                        r.get(Tables.USERS_ROLE), Instant.ofEpochSecond(r.get(Tables.USERS_CREATED_AT))));
    }
}
