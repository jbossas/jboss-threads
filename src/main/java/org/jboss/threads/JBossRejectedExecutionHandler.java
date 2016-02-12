/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.threads;

import java.util.concurrent.RejectedExecutionHandler;

/**
 *
 * @author panos
 */
public interface JBossRejectedExecutionHandler {
    void rejectedExecution(Runnable r, JBossThreadPoolExecutorReuseIdleThreads executor);
}
