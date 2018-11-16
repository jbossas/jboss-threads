/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
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

import javax.management.ObjectInstance;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

final class Substitutions {
    @TargetClass(JDKSpecific.class)
    static final class Target_JDKSpecific {
        @Substitute
        static void onSpinWait() {
            Target_PauseNode.pause();
        }
    }

    @TargetClass(className = "org.graalvm.compiler.nodes.PauseNode")
    static final class Target_PauseNode {
        @Alias
        public static native void pause();
    }

    @TargetClass(ThreadLocalResettingRunnable.Resetter.class)
    @Substitute
    static final class Target_ThreadLocalResettingRunnable_Resetter {
        @Substitute
        static void run() {
            Target_java_lang_Thread thread = (Target_java_lang_Thread) (Object) Thread.currentThread();
            thread.threadLocals = null;
            thread.inheritableThreadLocals = null;
        }
    }

    @TargetClass(Thread.class)
    static final class Target_java_lang_Thread {
        @Alias
        Target_java_lang_ThreadLocal_ThreadLocalMap threadLocals;
        @Alias
        Target_java_lang_ThreadLocal_ThreadLocalMap inheritableThreadLocals;
    }

    @TargetClass(className = "java.lang.ThreadLocal$ThreadLocalMap")
    static final class Target_java_lang_ThreadLocal_ThreadLocalMap {
    }

    @TargetClass(EnhancedQueueExecutor.MBeanRegisterAction.class)
    static final class Target_EnhancedQueueExecutor_MBeanRegisterAction {

        @Substitute
        public ObjectInstance run() {
            return null;
        }
    }
}
