package tech.icc.filesrv.core.application.entrypoint;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.icc.filesrv.common.constants.SystemConstant;
import tech.icc.filesrv.core.application.service.FileService;

@Slf4j
@RestController
@RequestMapping(SystemConstant.STATIC_RESOURCES_PATH)
@AllArgsConstructor
public class StaticResourceController {

    private final FileService service;

    @GetMapping("/{fkey}")
    public ResponseEntity<Resource> staticResource(@PathVariable String fkey) {
        return null;
    }
}
