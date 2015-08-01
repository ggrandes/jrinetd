package org.javastack.jrinetd;

import java.util.List;

public interface NodeListChangeEvent<T> {
	/**
	 * New nodes are resolved
	 * 
	 * @param nodes
	 */
	public void onResolve(final List<T> nodes);
}
