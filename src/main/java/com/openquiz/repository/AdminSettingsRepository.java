package com.openquiz.repository;

import com.openquiz.domain.models.AdminSetting;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class AdminSettingsRepository {

    private final DSLContext dsl;

    public AdminSettingsRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Map<String, String> findAllAsMap() {
        List<AdminSetting> all = dsl.selectFrom(Tables.ADMIN_SETTINGS)
                .fetch((org.jooq.Record r) -> new AdminSetting(
                        r.get(Tables.SET_KEY), r.get(Tables.SET_VALUE),
                        Instant.ofEpochSecond(r.get(Tables.SET_UPDATED))));
        return all.stream().collect(Collectors.toMap(s -> s.key(), s -> s.value()));
    }

    public void put(String key, String value) {
        long now = Instant.now().getEpochSecond();
        boolean exists = dsl.fetchExists(Tables.ADMIN_SETTINGS, Tables.SET_KEY.eq(key));
        if (exists) {
            dsl.update(Tables.ADMIN_SETTINGS).set(Tables.SET_VALUE, value)
                .set(Tables.SET_UPDATED, now).where(Tables.SET_KEY.eq(key)).execute();
        } else {
            dsl.insertInto(Tables.ADMIN_SETTINGS)
                .columns(Tables.SET_KEY, Tables.SET_VALUE, Tables.SET_UPDATED)
                .values(key, value, now).execute();
        }
    }

    public Optional<AdminSetting> findByKey(String key) {
        return dsl.selectFrom(Tables.ADMIN_SETTINGS).where(Tables.SET_KEY.eq(key))
                .fetchOptional(r -> new AdminSetting(r.get(Tables.SET_KEY), r.get(Tables.SET_VALUE),
                        Instant.ofEpochSecond(r.get(Tables.SET_UPDATED))));
    }
}
