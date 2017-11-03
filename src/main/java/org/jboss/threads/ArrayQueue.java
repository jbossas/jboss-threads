/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.threads;

import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Arrays;
import java.util.ConcurrentModificationException;

import org.wildfly.common.Assert;

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
        Assert.checkNotNullParam("e", e);
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
