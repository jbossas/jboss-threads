module org.jboss.threads {
    requires java.management;
    requires jdk.unsupported;
    requires org.jboss.logging;
    requires static org.jboss.logging.annotations;
    requires static org.graalvm.nativeimage;
    requires org.wildfly.common;
    requires io.smallrye.common.annotation;
    requires io.smallrye.common.constraint;
    requires io.smallrye.common.cpu;
    requires io.smallrye.common.function;

    exports org.jboss.threads;
    exports org.jboss.threads.management;
}