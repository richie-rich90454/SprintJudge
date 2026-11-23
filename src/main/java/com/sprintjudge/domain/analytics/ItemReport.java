package com.sprintjudge.domain.analytics;

/** Authored bank statistics alongside statistics observed from submissions. */
public final class ItemReport {
	private String questionId;
	private String bankId;
	private String title;
	private String unit;
	private String topic;
	private int attempts;
	private int correct;
	private double pValue;
	private double discrimination;
	private double avgAttempts;
	private Double bankDifficulty;
	private Double bankDiscrimination;
	private Double p25Sec;
	private Double medianSec;
	private Double p75Sec;

	@SuppressWarnings("all")
	public ItemReport() {
	}

	public ItemReport(String questionId, String bankId, String title, String unit, String topic, int attempts,
			int correct, double pValue, double discrimination, double avgAttempts, Double bankDifficulty,
			Double bankDiscrimination, Double p25Sec, Double medianSec, Double p75Sec) {
		this.questionId = questionId;
		this.bankId = bankId;
		this.title = title;
		this.unit = unit;
		this.topic = topic;
		this.attempts = attempts;
		this.correct = correct;
		this.pValue = pValue;
		this.discrimination = discrimination;
		this.avgAttempts = avgAttempts;
		this.bankDifficulty = bankDifficulty;
		this.bankDiscrimination = bankDiscrimination;
		this.p25Sec = p25Sec;
		this.medianSec = medianSec;
		this.p75Sec = p75Sec;
	}

	public String getQuestionId() {
		return questionId;
	}

	public void setQuestionId(String questionId) {
		this.questionId = questionId;
	}

	public String getBankId() {
		return bankId;
	}

	public void setBankId(String bankId) {
		this.bankId = bankId;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getUnit() {
		return unit;
	}

	public void setUnit(String unit) {
		this.unit = unit;
	}

	public String getTopic() {
		return topic;
	}

	public void setTopic(String topic) {
		this.topic = topic;
	}

	public int getAttempts() {
		return attempts;
	}

	public void setAttempts(int attempts) {
		this.attempts = attempts;
	}

	public int getCorrect() {
		return correct;
	}

	public void setCorrect(int correct) {
		this.correct = correct;
	}

	public double getPValue() {
		return pValue;
	}

	public void setPValue(double pValue) {
		this.pValue = pValue;
	}

	public double getDiscrimination() {
		return discrimination;
	}

	public void setDiscrimination(double discrimination) {
		this.discrimination = discrimination;
	}

	public double getAvgAttempts() {
		return avgAttempts;
	}

	public void setAvgAttempts(double avgAttempts) {
		this.avgAttempts = avgAttempts;
	}

	public Double getBankDifficulty() {
		return bankDifficulty;
	}

	public void setBankDifficulty(Double bankDifficulty) {
		this.bankDifficulty = bankDifficulty;
	}

	public Double getBankDiscrimination() {
		return bankDiscrimination;
	}

	public void setBankDiscrimination(Double bankDiscrimination) {
		this.bankDiscrimination = bankDiscrimination;
	}

	public Double getP25Sec() {
		return p25Sec;
	}

	public void setP25Sec(Double p25Sec) {
		this.p25Sec = p25Sec;
	}

	public Double getMedianSec() {
		return medianSec;
	}

	public void setMedianSec(Double medianSec) {
		this.medianSec = medianSec;
	}

	public Double getP75Sec() {
		return p75Sec;
	}

	public void setP75Sec(Double p75Sec) {
		this.p75Sec = p75Sec;
	}
}
