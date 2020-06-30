/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.threads.combiner;

import java.util.concurrent.locks.LockSupport;

public interface Combiner {
    interface IdleStrategy {
        int idle(int idleCount);

        static IdleStrategy busySpin() {
            return ignore -> ignore;
        }

        static IdleStrategy yield(int maxSpins) {
            return idleCount -> {
                if (idleCount < maxSpins) {
                    idleCount++;
                } else {
                    Thread.yield();
                }
                return idleCount;
            };
        }

        static IdleStrategy park(int maxSpins) {
            return idleCount -> {
                if (idleCount < maxSpins) {
                    idleCount++;
                } else {
                    LockSupport.parkNanos(1);
                }
                return idleCount;
            };
        }
    }

    void combine(Runnable action, IdleStrategy idleStrategy);
}
