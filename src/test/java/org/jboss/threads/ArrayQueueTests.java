package org.jboss.threads;

import static org.junit.Assert.*;

import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class ArrayQueueTests {

    static EnhancedQueueExecutor eqe;

    static EnhancedQueueExecutor.AbstractScheduledFuture<?>[] ITEMS;

    @BeforeClass
    public static void beforeAll() {
        eqe = new EnhancedQueueExecutor.Builder().build();
        ITEMS = new EnhancedQueueExecutor.AbstractScheduledFuture[32];
        for (int i = 0; i < 32; i ++) {
            final String toString = "[" + i + "]";
            ITEMS[i] = eqe.new RunnableScheduledFuture(new Runnable() {
                public void run() {
                    // nothing
                }

                public String toString() {
                    return toString;
                }
            }, 0, TimeUnit.DAYS);
        }
    }

    @Test
    public void testMoveForward() {
        EnhancedQueueExecutor.ArrayQueue aq = new EnhancedQueueExecutor.ArrayQueue(16);

        int head = 5;
        aq.testPoint_setHead(head);
        aq.testPoint_setArrayItem(head + 0, ITEMS[0]);
        aq.testPoint_setArrayItem(head + 1, ITEMS[1]);
        aq.testPoint_setArrayItem(head + 2, ITEMS[2]);
        aq.testPoint_setArrayItem(head + 3, ITEMS[3]);
        aq.testPoint_setSize(4);

        aq.moveForward(2, ITEMS[4]);

        assertEquals(head, aq.testPoint_head());

        assertSame(ITEMS[0], aq.testPoint_getArrayItem(head + 0));
        assertSame(ITEMS[1], aq.testPoint_getArrayItem(head + 1));
        assertSame(ITEMS[4], aq.testPoint_getArrayItem(head + 2));
        assertSame(ITEMS[2], aq.testPoint_getArrayItem(head + 3));
        assertSame(ITEMS[3], aq.testPoint_getArrayItem(head + 4));
    }

    @Test
    public void testMoveForwardWrap() {
        EnhancedQueueExecutor.ArrayQueue aq = new EnhancedQueueExecutor.ArrayQueue(16);

        int head = 14;
        aq.testPoint_setHead(head);
        aq.testPoint_setArrayItem(head + 0, ITEMS[0]);
        aq.testPoint_setArrayItem(head + 1, ITEMS[1]);
        aq.testPoint_setArrayItem(head + 2, ITEMS[2]);
        aq.testPoint_setArrayItem(head + 3, ITEMS[3]);
        aq.testPoint_setSize(4);

        aq.moveForward(2, ITEMS[4]);

        assertEquals(head, aq.testPoint_head());

        assertSame(ITEMS[0], aq.testPoint_getArrayItem(head + 0));
        assertSame(ITEMS[1], aq.testPoint_getArrayItem(head + 1));
        assertSame(ITEMS[4], aq.testPoint_getArrayItem(head + 2));
        assertSame(ITEMS[2], aq.testPoint_getArrayItem(head + 3));
        assertSame(ITEMS[3], aq.testPoint_getArrayItem(head + 4));
    }

    @Test
    public void testMoveBackward() {
        EnhancedQueueExecutor.ArrayQueue aq = new EnhancedQueueExecutor.ArrayQueue(16);

        int head = 5;
        aq.testPoint_setHead(head);
        aq.testPoint_setArrayItem(head + 0, ITEMS[0]);
        aq.testPoint_setArrayItem(head + 1, ITEMS[1]);
        aq.testPoint_setArrayItem(head + 2, ITEMS[2]);
        aq.testPoint_setArrayItem(head + 3, ITEMS[3]);
        aq.testPoint_setSize(4);

        aq.moveBackward(2, ITEMS[4]);

        assertEquals(head - 1, aq.testPoint_head());
        head--;

        assertSame(ITEMS[0], aq.testPoint_getArrayItem(head + 0));
        assertSame(ITEMS[1], aq.testPoint_getArrayItem(head + 1));
        assertSame(ITEMS[4], aq.testPoint_getArrayItem(head + 2));
        assertSame(ITEMS[2], aq.testPoint_getArrayItem(head + 3));
        assertSame(ITEMS[3], aq.testPoint_getArrayItem(head + 4));
    }

    @Test
    public void testMoveBackwardWrap() {
        EnhancedQueueExecutor.ArrayQueue aq = new EnhancedQueueExecutor.ArrayQueue(16);

        int head = 14;
        aq.testPoint_setHead(head);
        aq.testPoint_setArrayItem(head + 0, ITEMS[0]);
        aq.testPoint_setArrayItem(head + 1, ITEMS[1]);
        aq.testPoint_setArrayItem(head + 2, ITEMS[2]);
        aq.testPoint_setArrayItem(head + 3, ITEMS[3]);
        aq.testPoint_setSize(4);

        aq.moveBackward(2, ITEMS[4]);

        assertEquals(head - 1, aq.testPoint_head());
        head--;

        assertSame(ITEMS[0], aq.testPoint_getArrayItem(head + 0));
        assertSame(ITEMS[1], aq.testPoint_getArrayItem(head + 1));
        assertSame(ITEMS[4], aq.testPoint_getArrayItem(head + 2));
        assertSame(ITEMS[2], aq.testPoint_getArrayItem(head + 3));
        assertSame(ITEMS[3], aq.testPoint_getArrayItem(head + 4));
    }

    @Test
    public void testQueueBehavior() {
        EnhancedQueueExecutor.ArrayQueue aq = new EnhancedQueueExecutor.ArrayQueue(16);

        // tc 0 (n/a)
        assertTrue(aq.isEmpty());
        assertEquals(0, aq.size());
        assertEquals(0, aq.testPoint_head());
        assertEquals(16, aq.testPoint_arrayLength());

        // tc 1
        aq.insertAt(0, ITEMS[0]);
        assertFalse(aq.isEmpty());
        assertEquals(1, aq.size());
        assertEquals(0, aq.testPoint_head());
        assertSame(ITEMS[0], aq.testPoint_getArrayItem(0));
        assertNull(aq.testPoint_getArrayItem(1));
        assertNull(aq.testPoint_getArrayItem(15));

        // removal
        assertSame(ITEMS[0], aq.pollFirst());
        assertTrue(aq.isEmpty());
        assertEquals(0, aq.size());
        assertEquals(1, aq.testPoint_head());
        assertNull(aq.testPoint_getArrayItem(0));
        assertNull(aq.testPoint_getArrayItem(1));

        // tc 1 (but this time with head == 1)
        aq.insertAt(0, ITEMS[1]);
        assertFalse(aq.isEmpty());
        assertEquals(1, aq.size());
        assertEquals(1, aq.testPoint_head());
        assertNull(aq.testPoint_getArrayItem(0));
        assertSame(ITEMS[1], aq.testPoint_getArrayItem(1));
        assertNull(aq.testPoint_getArrayItem(2));

        // tc 1 (but with head == 1 and size == 1)
        aq.insertAt(1, ITEMS[2]);
        assertFalse(aq.isEmpty());
        assertEquals(2, aq.size());
        assertEquals(1, aq.testPoint_head());
        assertNull(aq.testPoint_getArrayItem(0));
        assertSame(ITEMS[1], aq.testPoint_getArrayItem(1));
        assertSame(ITEMS[2], aq.testPoint_getArrayItem(2));
        assertNull(aq.testPoint_getArrayItem(3));

        // tc 2 (but with head == 1 and size == 2)
        aq.insertAt(0, ITEMS[3]);
        assertFalse(aq.isEmpty());
        assertEquals(3, aq.size()); // halfSize == 2
        // head moves back to 0
        assertEquals(0, aq.testPoint_head());
        assertNull(aq.testPoint_getArrayItem(15));
        assertSame(ITEMS[3], aq.testPoint_getArrayItem(0));
        assertSame(ITEMS[1], aq.testPoint_getArrayItem(1));
        assertSame(ITEMS[2], aq.testPoint_getArrayItem(2));
        assertNull(aq.testPoint_getArrayItem(3));

        // tc 2 (but with head == 0 and size == 3)
        aq.insertAt(0, ITEMS[4]);
        assertFalse(aq.isEmpty());
        assertEquals(4, aq.size());
        // head wraps around to 15
        assertEquals(15, aq.testPoint_head());
        assertNull(aq.testPoint_getArrayItem(14));
        assertSame(ITEMS[4], aq.testPoint_getArrayItem(15));
        assertSame(ITEMS[3], aq.testPoint_getArrayItem(0));
        assertSame(ITEMS[1], aq.testPoint_getArrayItem(1));
        assertSame(ITEMS[2], aq.testPoint_getArrayItem(2));
        assertNull(aq.testPoint_getArrayItem(3));

        // tc 2 (but with head == 15 and size == 4)
        aq.insertAt(0, ITEMS[5]);
        assertFalse(aq.isEmpty());
        assertEquals(5, aq.size());
        assertEquals(14, aq.testPoint_head());
        assertNull(aq.testPoint_getArrayItem(13));
        assertSame(ITEMS[5], aq.testPoint_getArrayItem(14));
        assertSame(ITEMS[4], aq.testPoint_getArrayItem(15));
        assertSame(ITEMS[3], aq.testPoint_getArrayItem(0));
        assertSame(ITEMS[1], aq.testPoint_getArrayItem(1));
        assertSame(ITEMS[2], aq.testPoint_getArrayItem(2));
        assertNull(aq.testPoint_getArrayItem(3));

        // tc
        aq.insertAt(1, ITEMS[6]);

        assertNull(aq.testPoint_getArrayItem(12));
        assertSame(ITEMS[5], aq.testPoint_getArrayItem(13));
        assertSame(ITEMS[6], aq.testPoint_getArrayItem(14));
        assertSame(ITEMS[4], aq.testPoint_getArrayItem(15));
        assertSame(ITEMS[3], aq.testPoint_getArrayItem(0));
        assertSame(ITEMS[1], aq.testPoint_getArrayItem(1));
        assertSame(ITEMS[2], aq.testPoint_getArrayItem(2));
        assertNull(aq.testPoint_getArrayItem(3));

        aq.insertAt(0, ITEMS[7]);

        assertNull(aq.testPoint_getArrayItem(11));
        assertSame(ITEMS[7], aq.testPoint_getArrayItem(12));
        assertSame(ITEMS[5], aq.testPoint_getArrayItem(13));
        assertSame(ITEMS[6], aq.testPoint_getArrayItem(14));
        assertSame(ITEMS[4], aq.testPoint_getArrayItem(15));
        assertSame(ITEMS[3], aq.testPoint_getArrayItem(0));
        assertSame(ITEMS[1], aq.testPoint_getArrayItem(1));
        assertSame(ITEMS[2], aq.testPoint_getArrayItem(2));
        assertNull(aq.testPoint_getArrayItem(3));
    }

    @AfterClass
    public static void afterAll() throws InterruptedException {
        try {
            eqe.shutdown();
            eqe.awaitTermination(30, TimeUnit.SECONDS);
        } finally {
            eqe = null;
        }
    }
}
