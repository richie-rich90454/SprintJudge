package com.sprintjudge.repository;

import com.sprintjudge.domain.models.AdminSetting;
import com.sprintjudge.util.RepoUtil;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class AdminSettingsRepository {

	private final DSLContext dsl;

	public AdminSettingsRepository(DSLContext dsl) {
		this.dsl = dsl;
	}

	public Map<String, String> findAllAsMap() {
		List<AdminSetting> all = dsl.selectFrom(Tables.ADMIN_SETTINGS)
				.fetch(new org.jooq.RecordMapper<org.jooq.Record, AdminSetting>() {
					@Override
					public AdminSetting map(org.jooq.Record r) {
						return new AdminSetting(r.get(Tables.SET_KEY), r.get(Tables.SET_VALUE),
								Instant.ofEpochSecond(RepoUtil.asLong(r.get(Tables.SET_UPDATED))));
					}
				});
		Map<String, String> result = new HashMap<>();
		for (AdminSetting s : all) {
			result.put(s.getKey(), s.getValue());
		}
		return result;
	}

	public void put(String key, String value) {
		long now = Instant.now().getEpochSecond();
		// ponytail: atomic upsert replaces the TOCTOU check-then-insert.
		dsl.insertInto(Tables.ADMIN_SETTINGS).columns(Tables.SET_KEY, Tables.SET_VALUE, Tables.SET_UPDATED)
				.values(key, value, now).onConflict(Tables.SET_KEY).doUpdate().set(Tables.SET_VALUE, value)
				.set(Tables.SET_UPDATED, now).execute();
	}

	public Optional<AdminSetting> findByKey(String key) {
		return dsl.selectFrom(Tables.ADMIN_SETTINGS).where(Tables.SET_KEY.eq(key))
				.fetchOptional(new org.jooq.RecordMapper<org.jooq.Record, AdminSetting>() {
					@Override
					public AdminSetting map(org.jooq.Record r) {
						return new AdminSetting(r.get(Tables.SET_KEY), r.get(Tables.SET_VALUE),
								Instant.ofEpochSecond(RepoUtil.asLong(r.get(Tables.SET_UPDATED))));
					}
				});
	}
}
