/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.threads;

import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Arrays;
import java.util.ConcurrentModificationException;

/**
 * A simple array-backed queue with a fixed size.
 */
public final class ArrayQueue<E> extends AbstractQueue<E> implements Queue<E> {
    private final E[] elements;
    // elements are added at head
    private int head;
    // elements are removed at tail
    private int tail;
    private int modCnt;

    @SuppressWarnings({ "unchecked" })
    public ArrayQueue(final int capacity) {
        elements = (E[]) new Object[capacity];
    }

    public Iterator<E> iterator() {
        return new Iterator<E>() {
            private int pos = 0;
            private int size = size();
            private final int modIdx = modCnt;

            public boolean hasNext() {
                if (modCnt != modIdx) {
                    throw new ConcurrentModificationException();
                }
                return pos < size;
            }

            public E next() {
                if (modCnt != modIdx) {
                    throw new ConcurrentModificationException();
                }
                final int pos = this.pos;
                if (pos >= size) {
                    throw new NoSuchElementException();
                }
                final E[] elements = ArrayQueue.this.elements;
                final E value = elements[(tail + pos) % elements.length];
                this.pos = pos + 1;
                return value;
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    public boolean isEmpty() {
        return elements[tail] == null;
    }

    public int size() {
        final int head = this.head;
        final int tail = this.tail;
        final E[] elements = this.elements;
        if (elements[tail] == null) {
            return 0;
        } else if (head > tail) {
            return head - tail;
        } else {
            return head + elements.length - tail;
        }
    }

    public boolean offer(final E e) {
        if (e == null) {
            throw new NullPointerException("e is null");
        }
        final int head = this.head;
        final E[] elements = this.elements;
        final int len = elements.length;
        if (elements[head] != null) {
            return false;
        } else {
            elements[head] = e;
            this.head = (head + 1) % len;
            modCnt++;
            return true;
        }
    }

    public E poll() {
        final int tail = this.tail;
        final E[] elements = this.elements;
        final E value = elements[tail];
        if (value != null) {
            modCnt++;
            elements[tail] = null;
            this.tail = (tail + 1) % elements.length;
        }
        return value;
    }

    public E peek() {
        final int tail = this.tail;
        return tail == -1 ? null : elements[tail];
    }

    public void clear() {
        Arrays.fill(elements, null);
        modCnt++;
        tail = head = 0;
    }
}
