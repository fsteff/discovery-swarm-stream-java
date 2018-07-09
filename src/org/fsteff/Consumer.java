package org.fsteff;

/**
 * Replacement for the java.util.function.Consumer to get rid of java 8.
 */
public interface Consumer<T> {
	public void accept(T data);
}
