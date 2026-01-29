package tech.icc.filesrv.common.vo.task;

import lombok.Builder;

import java.util.List;

/**
 * Callback configuration for post-upload processing.
 *
 * @param name   plugin name
 * @param params plugin parameters
 */
@Builder
public record CallbackConfig(
        String name,
        List<CallbackParam> params
) {
    /**
     * Callback parameter
     *
     * @param key   parameter key
     * @param value parameter value
     */
    @Builder
    public record CallbackParam(
            String key,
            String value
    ) {}
}
