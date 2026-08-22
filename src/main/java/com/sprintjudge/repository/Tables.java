package com.sprintjudge.repository;

import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

public final class Tables {

    private Tables() {}

    public static final Table<?> USERS = DSL.table("users");
    public static final Field<String> USERS_ID = DSL.field("id", SQLDataType.VARCHAR);
    public static final Field<String> USERS_EMAIL = DSL.field("email", SQLDataType.VARCHAR);
    public static final Field<String> USERS_NAME = DSL.field("name", SQLDataType.VARCHAR);
    public static final Field<String> USERS_AVATAR = DSL.field("avatar_url", SQLDataType.VARCHAR);
    public static final Field<String> USERS_ROLE = DSL.field("role", SQLDataType.VARCHAR);
    public static final Field<Long> USERS_CREATED_AT = DSL.field("created_at", SQLDataType.BIGINT);

    public static final Table<?> QUIZZES = DSL.table("quizzes");
    public static final Field<String> QUIZZES_ID = DSL.field("id", SQLDataType.VARCHAR);
    public static final Field<String> QUIZZES_TITLE = DSL.field("title", SQLDataType.VARCHAR);
    public static final Field<String> QUIZZES_DESC = DSL.field("description", SQLDataType.VARCHAR);
    public static final Field<String> QUIZZES_CREATED_BY = DSL.field("created_by", SQLDataType.VARCHAR);
    public static final Field<Long> QUIZZES_CREATED_AT = DSL.field("created_at", SQLDataType.BIGINT);
    public static final Field<Boolean> QUIZZES_TEMPLATE = DSL.field("is_template", SQLDataType.BOOLEAN);

    public static final Table<?> QUESTIONS = DSL.table("questions");
    public static final Field<String> QUESTIONS_ID = DSL.field("id", SQLDataType.VARCHAR);
    public static final Field<String> QUESTIONS_QUIZ_ID = DSL.field("quiz_id", SQLDataType.VARCHAR);
    public static final Field<String> QUESTIONS_TITLE = DSL.field("title", SQLDataType.VARCHAR);
    public static final Field<String> QUESTIONS_DESC = DSL.field("description", SQLDataType.VARCHAR);
    public static final Field<String> QUESTIONS_TYPE = DSL.field("question_type", SQLDataType.VARCHAR);
    public static final Field<String> QUESTIONS_LANGS = DSL.field("languages_allowed", SQLDataType.VARCHAR);
    public static final Field<Integer> QUESTIONS_TIME = DSL.field("time_limit_sec", SQLDataType.INTEGER);
    public static final Field<Integer> QUESTIONS_POINTS = DSL.field("points_base", SQLDataType.INTEGER);
    public static final Field<String> QUESTIONS_CONFIG = DSL.field("config", SQLDataType.VARCHAR);
    public static final Field<Integer> QUESTIONS_ORDER = DSL.field("order_index", SQLDataType.INTEGER);
    public static final Field<Long> QUESTIONS_CREATED_AT = DSL.field("created_at", SQLDataType.BIGINT);

    public static final Table<?> GAME_SESSIONS = DSL.table("game_sessions");
    public static final Field<String> SESS_ID = DSL.field("id", SQLDataType.VARCHAR);
    public static final Field<String> SESS_QUIZ_ID = DSL.field("quiz_id", SQLDataType.VARCHAR);
    public static final Field<String> SESS_PIN = DSL.field("pin_code", SQLDataType.VARCHAR);
    public static final Field<String> SESS_HOST = DSL.field("host_user_id", SQLDataType.VARCHAR);
    public static final Field<String> SESS_STATUS = DSL.field("status", SQLDataType.VARCHAR);
    public static final Field<Integer> SESS_INDEX = DSL.field("current_question_index", SQLDataType.INTEGER);
    public static final Field<Long> SESS_STARTED = DSL.field("started_at", SQLDataType.BIGINT);
    public static final Field<Long> SESS_ENDED = DSL.field("ended_at", SQLDataType.BIGINT);
    public static final Field<String> SESS_OVERRIDE = DSL.field("settings_override", SQLDataType.VARCHAR);
    public static final Field<Long> SESS_CREATED = DSL.field("created_at", SQLDataType.BIGINT);

    public static final Table<?> SUBMISSIONS = DSL.table("submissions");
    public static final Field<String> SUB_ID = DSL.field("id", SQLDataType.VARCHAR);
    public static final Field<String> SUB_SESS = DSL.field("game_session_id", SQLDataType.VARCHAR);
    public static final Field<String> SUB_QUESTION = DSL.field("question_id", SQLDataType.VARCHAR);
    public static final Field<String> SUB_PNAME = DSL.field("player_name", SQLDataType.VARCHAR);
    public static final Field<String> SUB_PUUID = DSL.field("player_uuid", SQLDataType.VARCHAR);
    public static final Field<String> SUB_DATA = DSL.field("response_data", SQLDataType.VARCHAR);
    public static final Field<Integer> SUB_SCORE = DSL.field("score_earned", SQLDataType.INTEGER);
    public static final Field<Boolean> SUB_CORRECT = DSL.field("is_correct", SQLDataType.BOOLEAN);
    public static final Field<String> SUB_LOG = DSL.field("judge_log", SQLDataType.VARCHAR);
    public static final Field<Integer> SUB_ATTEMPTS = DSL.field("attempt_count", SQLDataType.INTEGER);
    public static final Field<Long> SUB_AT = DSL.field("submitted_at", SQLDataType.BIGINT);

    public static final Table<?> ADMIN_SETTINGS = DSL.table("admin_settings");
    public static final Field<String> SET_KEY = DSL.field("key", SQLDataType.VARCHAR);
    public static final Field<String> SET_VALUE = DSL.field("value", SQLDataType.VARCHAR);
    public static final Field<Long> SET_UPDATED = DSL.field("updated_at", SQLDataType.BIGINT);
}
