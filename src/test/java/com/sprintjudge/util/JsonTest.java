package com.sprintjudge.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("all")
class JsonTest {

	@Test
	void writeThenReadRoundTrip() {
		String json = Json.write(Map.of("a", 1, "b", "two"));
		Map<String, Object> back = Json.readMap(json);
		assertEquals(1, ((Number) back.get("a")).intValue());
		assertEquals("two", back.get("b"));
	}

	@Test
	void readTreeParsesNested() {
		JsonNode node = Json.readTree("{\"x\":{\"y\":[1,2]}}");
		assertEquals(2, node.path("x").path("y").size());
	}

	@Test
	void readMapReturnsTypedValues() {
		Map<String, Object> m = Json.readMap("{\"correctIndex\":2,\"tag\":\"mcq\"}");
		assertEquals(2, ((Number) m.get("correctIndex")).intValue());
		assertEquals("mcq", m.get("tag"));
	}

	@Test
	void readMapEmptyObject() {
		assertTrue(Json.readMap("{}").isEmpty());
	}

	@ParameterizedTest
	@ValueSource(strings = {"{not json", "[1,2]", "\"scalar\"", "42", ""})
	void readMapRejectsNonObjects(String raw) {
		assertThrows(Exception.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() {
				Json.readMap(raw);
			}
		});
	}

	@Test
	void readTreeRejectsGarbage() {
		assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() {
				Json.readTree("{nope");
			}
		});
	}

	@Test
	void unknownFieldsAreIgnoredOnBinding() throws Exception {
		Target t = Json.MAPPER.readValue("{\"a\":1,\"zzz\":9}", Target.class);
		assertEquals(1, t.getA());
	}

	@Test
	void nullInputToWriteProducesNullLiteral() {
		assertEquals("null", Json.write(null));
	}

	@Test
	void readMapPreservesNestedStructures() {
		Map<String, Object> m = Json.readMap("{\"cfg\":{\"list\":[1,2],\"flag\":true}}");
		Object nested = m.get("cfg");
		assertTrue(nested instanceof Map);
		assertEquals(2, ((java.util.List<?>) ((Map<?, ?>) nested).get("list")).size());
		assertEquals(Boolean.TRUE, ((Map<?, ?>) nested).get("flag"));
	}

	@Test
	void writeThrowsOnUnserializable() {
		assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() {
				Json.write(new Object());
			}
		});
	}

	@Test
	void readThrowsOnInvalidJson() {
		assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() {
				Json.read("not json", String.class);
			}
		});
	}

	@Test
	void readBindsTypedObject() {
		Map<?, ?> m = Json.read("{\"a\":7}", Map.class);
		assertEquals(7, ((Number) m.get("a")).intValue());
	}

	@Test
	void readBindsRecord() {
		T t = Json.read("{\"a\":1,\"b\":\"x\"}", T.class);
		assertEquals(1, t.getA());
		assertEquals("x", t.getB());
	}

	static final class Target {
		private int a;

		Target() {
		}

		Target(int a) {
			this.a = a;
		}

		public int getA() {
			return a;
		}

		public void setA(int a) {
			this.a = a;
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof Target)) {
				return false;
			}
			return a == ((Target) o).a;
		}

		@Override
		public int hashCode() {
			return a;
		}

		@Override
		public String toString() {
			return "Target[a=" + a + "]";
		}
	}

	static final class T {
		private int a;
		private String b;

		T() {
		}

		T(int a, String b) {
			this.a = a;
			this.b = b;
		}

		public int getA() {
			return a;
		}

		public void setA(int a) {
			this.a = a;
		}

		public String getB() {
			return b;
		}

		public void setB(String b) {
			this.b = b;
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof T)) {
				return false;
			}
			T other = (T) o;
			return a == other.a && java.util.Objects.equals(b, other.b);
		}

		@Override
		public int hashCode() {
			return 31 * a + java.util.Objects.hashCode(b);
		}

		@Override
		public String toString() {
			return "T[a=" + a + ", b=" + b + "]";
		}
	}
}
