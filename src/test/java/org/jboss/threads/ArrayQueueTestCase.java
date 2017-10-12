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
