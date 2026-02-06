package tech.icc.filesrv.common.context;

import tech.icc.filesrv.common.vo.task.DerivedFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 衍生文件上下文
 * <p>
 * 管理插件生成的衍生文件列表（缩略图、转码文件等）
 */
public class DerivedFilesContext {
    
    private final List<DerivedFile> derivedFiles;

    public DerivedFilesContext() {
        this.derivedFiles = new ArrayList<>();
    }

    /**
     * 添加衍生文件
     */
    public void add(DerivedFile derivedFile) {
        if (derivedFile == null) {
            throw new IllegalArgumentException("derivedFile cannot be null");
        }
        derivedFiles.add(derivedFile);
    }

    /**
     * 获取所有衍生文件
     */
    public List<DerivedFile> getAll() {
        return Collections.unmodifiableList(derivedFiles);
    }

    /**
     * 获取衍生文件数量
     */
    public int count() {
        return derivedFiles.size();
    }

    /**
     * 是否为空
     */
    public boolean isEmpty() {
        return derivedFiles.isEmpty();
    }

    /**
     * 清空衍生文件列表
     */
    public void clear() {
        derivedFiles.clear();
    }

    @Override
    public String toString() {
        return "DerivedFilesContext{count=" + derivedFiles.size() + '}';
    }
}
