package tech.icc.filesrv.core.infra.plugin;

import tech.icc.filesrv.common.context.TaskContext;

public interface SharedPlugin {
    void init();
    void apply(TaskContext ctx);
    void release();
}
