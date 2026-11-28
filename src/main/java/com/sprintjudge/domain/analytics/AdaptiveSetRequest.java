package com.sprintjudge.domain.analytics;

/** Selection inputs for an adaptive or balanced practice set. */
public final class AdaptiveSetRequest {
	private String subject;
	private String unit;
	private int size;
	private String mode;
	private double targetDifficulty;
	private String title;
	private long seed;

	@SuppressWarnings("all")
	public AdaptiveSetRequest() {
	}

	public String getSubject() {
		return subject;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

	public String getUnit() {
		return unit;
	}

	public void setUnit(String unit) {
		this.unit = unit;
	}

	public int getSize() {
		return size;
	}

	public void setSize(int size) {
		this.size = size;
	}

	public String getMode() {
		return mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	public double getTargetDifficulty() {
		return targetDifficulty;
	}

	public void setTargetDifficulty(double targetDifficulty) {
		this.targetDifficulty = targetDifficulty;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public long getSeed() {
		return seed;
	}

	public void setSeed(long seed) {
		this.seed = seed;
	}
}
