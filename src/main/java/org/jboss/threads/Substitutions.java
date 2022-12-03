package org.jboss.threads;

import javax.management.ObjectInstance;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

final class Substitutions {
    @TargetClass(EnhancedQueueExecutor.MBeanRegisterAction.class)
    static final class Target_EnhancedQueueExecutor_MBeanRegisterAction {

        @Substitute
        public ObjectInstance run() {
            return null;
        }
    }
}
