package io.github.jan1a234.goblinforest.game;

/** Angreifbare Gebäude einer Festung. */
public enum Structure {
	CORE("core"),
	TOWER("tower");

	private final String id;

	Structure(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public String translationKey() {
		return "structure.goblinforest." + id;
	}
}
