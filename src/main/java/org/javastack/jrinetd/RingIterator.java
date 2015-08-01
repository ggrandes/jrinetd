package org.javastack.jrinetd;

import java.util.Iterator;
import java.util.List;

public class RingIterator<E> implements Iterator<E> {
	final List<E> list;
	final int start;
	int current;

	public RingIterator(final List<E> list, final int start) {
		this.list = list;
		this.start = (start & Integer.MAX_VALUE);
		this.current = this.start;
	}

	@Override
	public boolean hasNext() {
		final int next = ((current + 1) & Integer.MAX_VALUE);
		return ((next > start) && (next <= (start + list.size())));
	}

	@Override
	public E next() {
		if (list.isEmpty())
			return null;
		final E e = list.get(current % list.size());
		current = ((current + 1) & Integer.MAX_VALUE);
		return e;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}
}
