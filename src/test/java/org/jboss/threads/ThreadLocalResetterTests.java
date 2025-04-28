package org.jboss.threads;

import org.junit.jupiter.api.Test;

public class ThreadLocalResetterTests {
    @Test
    public void testResetter() {
        JDKSpecific.ThreadAccess.clearThreadLocals();
    }
}
