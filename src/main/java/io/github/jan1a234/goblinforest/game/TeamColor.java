package io.github.jan1a234.goblinforest.game;

/** Die beiden Clans eines Matches. */
public enum TeamColor {
	RED("red", 0xC0392B),
	GREEN("green", 0x27AE60);

	private final String id;
	private final int rgb;

	TeamColor(String id, int rgb) {
		this.id = id;
		this.rgb = rgb;
	}

	public String id() {
		return id;
	}

	public int rgb() {
		return rgb;
	}

	public TeamColor opponent() {
		return this == RED ? GREEN : RED;
	}

	public String translationKey() {
		return "team.goblinforest." + id;
	}
}
