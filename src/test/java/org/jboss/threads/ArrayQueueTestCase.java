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

import junit.framework.TestCase;

/**
 *
 */
public final class ArrayQueueTestCase extends TestCase {

    public void testBasic() {
        final int max = 40;
        final ArrayQueue<Object> queue = new ArrayQueue<Object>(max);
        for (int i = 0; i < max; i ++) {
            assertEquals("Size mismatch, iteration " + i, i, queue.size());
            assertTrue("Iteration " + i, queue.offer(new Object()));
        }
        assertFalse("Queue took extra element", queue.offer(new Object()));
        for (int i = 0; i < max; i ++) {
            assertNotNull("Queue missing elements", queue.poll());
        }
        assertNull("Queue has extra elements", queue.poll());
    }

    public void testContents() {
        final int max = 40;
        final ArrayQueue<Object> queue = new ArrayQueue<Object>(max);
        for (int cnt = 1; cnt < max; cnt ++) {
            for (int i = 0; i < cnt; i ++) {
                assertEquals("Size mismatch, iteration " + i, i, queue.size());
                assertTrue("Queue won't take element, iteration " + cnt + "/" + i, queue.offer(new Integer(i)));
            }
            for (int i = 0; i < cnt; i ++) {
                assertTrue("Missing int value, iteration " + cnt + "/" + i, queue.contains(new Integer(i)));
            }
            for (int i = 0; i < cnt; i ++) {
                assertEquals("Size mismatch, iteration " + i, cnt - i, queue.size());
                assertEquals("Wrong object value, iteration " + cnt + "/" + i, new Integer(i), queue.poll());
            }
            assertNull("Extra value, iteration " + cnt, queue.poll());
            for (int i = 0; i < cnt; i ++) {
                assertFalse("Nonremoved value, iteration " + cnt + "/" + i, queue.contains(new Integer(i)));
            }
        }
    }

    public void testClear() {
        final int max = 40;
        final ArrayQueue<Object> queue = new ArrayQueue<Object>(max);
        for (int cnt = 1; cnt < max; cnt ++) {
            for (int i = 0; i < cnt; i ++) {
                assertEquals("Size mismatch, iteration " + i, i, queue.size());
                assertTrue("Queue won't take element, iteration " + cnt + "/" + i, queue.offer(new Integer(i)));
            }
            for (int i = 0; i < cnt; i ++) {
                assertTrue("Missing int value, iteration " + cnt + "/" + i, queue.contains(new Integer(i)));
            }
            queue.clear();
            assertNull("Extra value, iteration " + cnt, queue.poll());
            for (int i = 0; i < cnt; i ++) {
                assertFalse("Nonremoved value, iteration " + cnt + "/" + i, queue.contains(new Integer(i)));
            }
        }
    }
}
