package io.github.jan1a234.goblinforest.upgrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.jan1a234.goblinforest.unit.UnitType;
import org.junit.jupiter.api.Test;

class UpgradeKeyTest {
	@Test
	void roundTrip() {
		for (UpgradeKey key : UpgradeKey.all(type -> type == UnitType.ARCHER)) {
			assertEquals(key, UpgradeKey.parse(key.serialize()));
		}
	}

	@Test
	void rejectsInvalidInput() {
		assertNull(UpgradeKey.parse(null));
		assertNull(UpgradeKey.parse("armor"));
		assertNull(UpgradeKey.parse("walls:warrior"));
		assertNull(UpgradeKey.parse("armor:dragon"));
		assertNull(UpgradeKey.parse("nonsense"));
	}
}
