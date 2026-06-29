/**
 * A small, zero-dependency JSON parser and writer.
 *
 * <p>{@link rsp.util.json.Json} is the entry point: {@code Json.parse(text)} builds a
 * {@link rsp.util.json.JsonDataType} tree and {@code Json.write(value)} serialises one back to text.
 * Parsing is strict (RFC 8259) and bounded by {@link rsp.util.json.JsonLimits} to keep adversarial
 * input from exhausting the stack or the heap.
 */
package rsp.util.json;
