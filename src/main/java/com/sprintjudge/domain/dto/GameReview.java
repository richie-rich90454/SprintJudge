package com.sprintjudge.domain.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Objects;

/**
 * Post-game review data sent to all players after the game ends.
 * Contains answer key, per-student results, and class-wide difficulty analysis.
 */
public final class GameReview {
    private String type;
    private List<LeaderboardEntry> rankings;
    private List<QuestionReview> questions;
    private List<PlayerReview> players;
    private ClassStats classStats;
    private String sessionId;
    private List<TeamReview> teams;

    public GameReview() {}

    public GameReview(String type, List<LeaderboardEntry> rankings, List<QuestionReview> questions,
                      List<PlayerReview> players, ClassStats classStats, String sessionId,
                      List<TeamReview> teams) {
        this.type = type;
        this.rankings = rankings;
        this.questions = questions;
        this.players = players;
        this.classStats = classStats;
        this.sessionId = sessionId;
        this.teams = teams;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public List<LeaderboardEntry> getRankings() { return rankings; }
    public void setRankings(List<LeaderboardEntry> rankings) { this.rankings = rankings; }

    public List<QuestionReview> getQuestions() { return questions; }
    public void setQuestions(List<QuestionReview> questions) { this.questions = questions; }

    public List<PlayerReview> getPlayers() { return players; }
    public void setPlayers(List<PlayerReview> players) { this.players = players; }

    public ClassStats getClassStats() { return classStats; }
    public void setClassStats(ClassStats classStats) { this.classStats = classStats; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public List<TeamReview> getTeams() { return teams; }
    public void setTeams(List<TeamReview> teams) { this.teams = teams; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GameReview)) return false;
        GameReview other = (GameReview) o;
        return Objects.equals(type, other.type)
                && Objects.equals(rankings, other.rankings)
                && Objects.equals(questions, other.questions)
                && Objects.equals(players, other.players)
                && Objects.equals(classStats, other.classStats)
                && Objects.equals(sessionId, other.sessionId)
                && Objects.equals(teams, other.teams);
    }

    @Override public int hashCode() {
        return Objects.hash(type, rankings, questions, players, classStats, sessionId, teams);
    }

    @Override public String toString() {
        return "GameReview[type=" + type
                + ", rankings=" + rankings
                + ", questions=" + questions
                + ", players=" + players
                + ", classStats=" + classStats
                + ", sessionId=" + sessionId
                + ", teams=" + teams + "]";
    }

    public static final class QuestionReview {
        private String questionId;
        private String title;
        private String questionType;
        private int timeLimitSec;
        private int pointsBase;
        private JsonNode answer;
        private int totalAttempts;
        private int correctCount;
        private double correctRate;
        private double avgAttempts;
        private List<String> options;

        public QuestionReview() {}

        public QuestionReview(String questionId, String title, String questionType,
                              int timeLimitSec, int pointsBase, JsonNode answer,
                              int totalAttempts, int correctCount, double correctRate,
                              double avgAttempts, List<String> options) {
            this.questionId = questionId;
            this.title = title;
            this.questionType = questionType;
            this.timeLimitSec = timeLimitSec;
            this.pointsBase = pointsBase;
            this.answer = answer;
            this.totalAttempts = totalAttempts;
            this.correctCount = correctCount;
            this.correctRate = correctRate;
            this.avgAttempts = avgAttempts;
            this.options = options;
        }

        public String getQuestionId() { return questionId; }
        public void setQuestionId(String questionId) { this.questionId = questionId; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getQuestionType() { return questionType; }
        public void setQuestionType(String questionType) { this.questionType = questionType; }

        public int getTimeLimitSec() { return timeLimitSec; }
        public void setTimeLimitSec(int timeLimitSec) { this.timeLimitSec = timeLimitSec; }

        public int getPointsBase() { return pointsBase; }
        public void setPointsBase(int pointsBase) { this.pointsBase = pointsBase; }

        public JsonNode getAnswer() { return answer; }
        public void setAnswer(JsonNode answer) { this.answer = answer; }

        public int getTotalAttempts() { return totalAttempts; }
        public void setTotalAttempts(int totalAttempts) { this.totalAttempts = totalAttempts; }

        public int getCorrectCount() { return correctCount; }
        public void setCorrectCount(int correctCount) { this.correctCount = correctCount; }

        public double getCorrectRate() { return correctRate; }
        public void setCorrectRate(double correctRate) { this.correctRate = correctRate; }

        public double getAvgAttempts() { return avgAttempts; }
        public void setAvgAttempts(double avgAttempts) { this.avgAttempts = avgAttempts; }

        public List<String> getOptions() { return options; }
        public void setOptions(List<String> options) { this.options = options; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof QuestionReview)) return false;
            QuestionReview other = (QuestionReview) o;
            return timeLimitSec == other.timeLimitSec
                    && pointsBase == other.pointsBase
                    && totalAttempts == other.totalAttempts
                    && correctCount == other.correctCount
                    && Double.compare(correctRate, other.correctRate) == 0
                    && Double.compare(avgAttempts, other.avgAttempts) == 0
                    && Objects.equals(questionId, other.questionId)
                    && Objects.equals(title, other.title)
                    && Objects.equals(questionType, other.questionType)
                    && Objects.equals(answer, other.answer)
                    && Objects.equals(options, other.options);
        }

        @Override public int hashCode() {
            return Objects.hash(questionId, title, questionType, timeLimitSec, pointsBase, answer,
                    totalAttempts, correctCount, correctRate, avgAttempts, options);
        }

        @Override public String toString() {
            return "QuestionReview[questionId=" + questionId
                    + ", title=" + title
                    + ", questionType=" + questionType
                    + ", timeLimitSec=" + timeLimitSec
                    + ", pointsBase=" + pointsBase
                    + ", answer=" + answer
                    + ", totalAttempts=" + totalAttempts
                    + ", correctCount=" + correctCount
                    + ", correctRate=" + correctRate
                    + ", avgAttempts=" + avgAttempts
                    + ", options=" + options + "]";
        }
    }

    public static final class PlayerReview {
        private String playerUuid;
        private String playerName;
        private int totalScore;
        private List<PlayerAnswer> answers;

        public PlayerReview() {}

        public PlayerReview(String playerUuid, String playerName, int totalScore,
                            List<PlayerAnswer> answers) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.totalScore = totalScore;
            this.answers = answers;
        }

        public String getPlayerUuid() { return playerUuid; }
        public void setPlayerUuid(String playerUuid) { this.playerUuid = playerUuid; }

        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }

        public int getTotalScore() { return totalScore; }
        public void setTotalScore(int totalScore) { this.totalScore = totalScore; }

        public List<PlayerAnswer> getAnswers() { return answers; }
        public void setAnswers(List<PlayerAnswer> answers) { this.answers = answers; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PlayerReview)) return false;
            PlayerReview other = (PlayerReview) o;
            return totalScore == other.totalScore
                    && Objects.equals(playerUuid, other.playerUuid)
                    && Objects.equals(playerName, other.playerName)
                    && Objects.equals(answers, other.answers);
        }

        @Override public int hashCode() { return Objects.hash(playerUuid, playerName, totalScore, answers); }

        @Override public String toString() {
            return "PlayerReview[playerUuid=" + playerUuid
                    + ", playerName=" + playerName
                    + ", totalScore=" + totalScore
                    + ", answers=" + answers + "]";
        }
    }

    public static final class PlayerAnswer {
        private String questionId;
        private boolean correct;
        private int scoreEarned;
        private int attemptCount;

        public PlayerAnswer() {}

        public PlayerAnswer(String questionId, boolean correct, int scoreEarned, int attemptCount) {
            this.questionId = questionId;
            this.correct = correct;
            this.scoreEarned = scoreEarned;
            this.attemptCount = attemptCount;
        }

        public String getQuestionId() { return questionId; }
        public void setQuestionId(String questionId) { this.questionId = questionId; }

        public boolean isCorrect() { return correct; }
        public void setCorrect(boolean correct) { this.correct = correct; }

        public int getScoreEarned() { return scoreEarned; }
        public void setScoreEarned(int scoreEarned) { this.scoreEarned = scoreEarned; }

        public int getAttemptCount() { return attemptCount; }
        public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PlayerAnswer)) return false;
            PlayerAnswer other = (PlayerAnswer) o;
            return correct == other.correct
                    && scoreEarned == other.scoreEarned
                    && attemptCount == other.attemptCount
                    && Objects.equals(questionId, other.questionId);
        }

        @Override public int hashCode() { return Objects.hash(questionId, correct, scoreEarned, attemptCount); }

        @Override public String toString() {
            return "PlayerAnswer[questionId=" + questionId
                    + ", correct=" + correct
                    + ", scoreEarned=" + scoreEarned
                    + ", attemptCount=" + attemptCount + "]";
        }
    }

    public static final class ClassStats {
        private int totalPlayers;
        private int totalQuestions;
        private double avgScore;
        private int totalCorrect;
        private int totalAttempts;
        private String hardestQuestionId;
        private String easiestQuestionId;

        public ClassStats() {}

        public ClassStats(int totalPlayers, int totalQuestions, double avgScore,
                          int totalCorrect, int totalAttempts, String hardestQuestionId,
                          String easiestQuestionId) {
            this.totalPlayers = totalPlayers;
            this.totalQuestions = totalQuestions;
            this.avgScore = avgScore;
            this.totalCorrect = totalCorrect;
            this.totalAttempts = totalAttempts;
            this.hardestQuestionId = hardestQuestionId;
            this.easiestQuestionId = easiestQuestionId;
        }

        public int getTotalPlayers() { return totalPlayers; }
        public void setTotalPlayers(int totalPlayers) { this.totalPlayers = totalPlayers; }

        public int getTotalQuestions() { return totalQuestions; }
        public void setTotalQuestions(int totalQuestions) { this.totalQuestions = totalQuestions; }

        public double getAvgScore() { return avgScore; }
        public void setAvgScore(double avgScore) { this.avgScore = avgScore; }

        public int getTotalCorrect() { return totalCorrect; }
        public void setTotalCorrect(int totalCorrect) { this.totalCorrect = totalCorrect; }

        public int getTotalAttempts() { return totalAttempts; }
        public void setTotalAttempts(int totalAttempts) { this.totalAttempts = totalAttempts; }

        public String getHardestQuestionId() { return hardestQuestionId; }
        public void setHardestQuestionId(String hardestQuestionId) { this.hardestQuestionId = hardestQuestionId; }

        public String getEasiestQuestionId() { return easiestQuestionId; }
        public void setEasiestQuestionId(String easiestQuestionId) { this.easiestQuestionId = easiestQuestionId; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ClassStats)) return false;
            ClassStats other = (ClassStats) o;
            return totalPlayers == other.totalPlayers
                    && totalQuestions == other.totalQuestions
                    && Double.compare(avgScore, other.avgScore) == 0
                    && totalCorrect == other.totalCorrect
                    && totalAttempts == other.totalAttempts
                    && Objects.equals(hardestQuestionId, other.hardestQuestionId)
                    && Objects.equals(easiestQuestionId, other.easiestQuestionId);
        }

        @Override public int hashCode() {
            return Objects.hash(totalPlayers, totalQuestions, avgScore, totalCorrect, totalAttempts,
                    hardestQuestionId, easiestQuestionId);
        }

        @Override public String toString() {
            return "ClassStats[totalPlayers=" + totalPlayers
                    + ", totalQuestions=" + totalQuestions
                    + ", avgScore=" + avgScore
                    + ", totalCorrect=" + totalCorrect
                    + ", totalAttempts=" + totalAttempts
                    + ", hardestQuestionId=" + hardestQuestionId
                    + ", easiestQuestionId=" + easiestQuestionId + "]";
        }
    }

    public static final class TeamReview {
        private String id;
        private String name;
        private long score;
        private List<String> memberUuids;

        public TeamReview() {}

        public TeamReview(String id, String name, long score, List<String> memberUuids) {
            this.id = id;
            this.name = name;
            this.score = score;
            this.memberUuids = memberUuids;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public long getScore() { return score; }
        public void setScore(long score) { this.score = score; }

        public List<String> getMemberUuids() { return memberUuids; }
        public void setMemberUuids(List<String> memberUuids) { this.memberUuids = memberUuids; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TeamReview)) return false;
            TeamReview other = (TeamReview) o;
            return score == other.score
                    && Objects.equals(id, other.id)
                    && Objects.equals(name, other.name)
                    && Objects.equals(memberUuids, other.memberUuids);
        }

        @Override public int hashCode() { return Objects.hash(id, name, score, memberUuids); }

        @Override public String toString() {
            return "TeamReview[id=" + id
                    + ", name=" + name
                    + ", score=" + score
                    + ", memberUuids=" + memberUuids + "]";
        }
    }
}
