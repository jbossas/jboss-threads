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

package org.jboss.threads.combiner;

import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;


abstract class Pad0Combiner {

    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16;

    protected static class UnsafeAccess {

        public static final Unsafe UNSAFE;

        public UnsafeAccess() {
        }

        public static long fieldOffset(Class clz, String fieldName) throws RuntimeException {
            try {
                return UNSAFE.objectFieldOffset(clz.getDeclaredField(fieldName));
            } catch (NoSuchFieldException var3) {
                throw new RuntimeException(var3);
            }
        }

        static {
            Unsafe instance;
            try {
                Field field = Unsafe.class.getDeclaredField("theUnsafe");
                field.setAccessible(true);
                instance = (Unsafe) field.get((Object) null);
            } catch (Exception var5) {
                try {
                    Constructor<Unsafe> c = Unsafe.class.getDeclaredConstructor();
                    c.setAccessible(true);
                    instance = (Unsafe) c.newInstance();
                } catch (Exception var4) {
                    throw new RuntimeException(var4);
                }
            }

            UNSAFE = instance;
        }
    }


}

abstract class TailCombiner extends Pad0Combiner {

    abstract static class Pad0Node {
        long p00, p01, p02, p03, p04, p05, p06, p07;
        long p10, p11, p12, p13, p14, p15, p16;
    }

    abstract static class WaitFieldNode extends Pad0Node {
        private static final long WAIT_FIELD_OFFSET = UnsafeAccess.fieldOffset(WaitFieldNode.class, "wait");

        protected int wait = 0;

        protected int lvWait() {
            return UnsafeAccess.UNSAFE.getIntVolatile(this, WAIT_FIELD_OFFSET);
        }

        protected void soWait(int value) {
            UnsafeAccess.UNSAFE.putOrderedInt(this, WAIT_FIELD_OFFSET, value);
        }

        protected void svWait(int value) {
            UnsafeAccess.UNSAFE.putIntVolatile(this, WAIT_FIELD_OFFSET, value);
        }
    }

    abstract static class Pad1Node extends WaitFieldNode {
        long p00, p01, p02, p03, p04, p05, p06;
        long p10, p11, p12, p13, p14, p15, p16, p17;
    }

    protected static final class Node extends Pad1Node {

        private static final long NEXT_FIELD_OFFSET = UnsafeAccess.fieldOffset(Node.class, "next");

        protected Runnable request;
        protected Node next = null;

        protected void soNext(Node next) {
            UnsafeAccess.UNSAFE.putOrderedObject(this, NEXT_FIELD_OFFSET, next);
        }

        protected void svNext(Node next) {
            UnsafeAccess.UNSAFE.putObjectVolatile(this, NEXT_FIELD_OFFSET, next);
        }

        protected Node lvNext() {
            return (Node) UnsafeAccess.UNSAFE.getObjectVolatile(this, NEXT_FIELD_OFFSET);
        }
    }

    private static final long TAIL_FIELD_OFFSET = UnsafeAccess.fieldOffset(TailCombiner.class, "_tail");

    protected Node _tail;

    protected Node getAndSetTail(Node newValue) {
        //it doesn't involve any CAS loop :P
        return (Node) UnsafeAccess.UNSAFE.getAndSetObject(this, TAIL_FIELD_OFFSET, newValue);
    }
}

abstract class Pad1Combiner extends TailCombiner {

    long p00, p01, p02, p03, p04, p05, p06;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}

public final class OptimizedCombiner extends Pad1Combiner implements Combiner {

    // this could be replaced by Netty threads
    private final ThreadLocal<Node> _myNode = ThreadLocal.withInitial(Node::new);

    public OptimizedCombiner() {
        _tail = new Node();
    }

    @Override
    public void combine(Runnable action, IdleStrategy idleStrategy) {
        final ThreadLocal<Node> _myNode = this._myNode;
        Node nextNode = _myNode.get();
        //single writer
        nextNode.wait = 1;
        Node curNode = getAndSetTail(nextNode);
        //nextNode.wait = 1 is seq cst store released
        _myNode.set(curNode);

        //
        // There's now a window where nextNode/_tail can't be reached.
        // So, any communication has to be done via the previous node
        // in the list, curNode.
        //

        curNode.request = action;
        curNode.soNext(nextNode);

        // Wait until our request has been fulfilled or we are the combiner.

        int idleCount = 0;
        while (curNode.lvWait() == 1) {
            idleCount = idleStrategy.idle(idleCount);
        }

        if (curNode.request == null)
            return;

        // We are now the combiner. We copy n's Next field into nn, as n will
        // become untouchable after n.soWait(0), due to reuse.

        Node n = curNode;
        Node nn;
        for (; (nn = n.lvNext()) != null; n = nn) {
            try {
                n.request.run();
            } finally {
                n.next = null;
                n.request = null;
                //store release the other fields
                n.soWait(0);
            }
        }
        // Make someone else the combiner.
        n.soWait(0);
    }
}
