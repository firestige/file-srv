package tech.icc.filesrv.common.exception.validation;

import lombok.Getter;
import tech.icc.filesrv.common.constants.ResultCode;

/**
 * 文件未找到异常
 * <p>
 * 当指定的文件不存在时抛出
 */
@Getter
public class FileNotFoundException extends ValidationException {
    
    /** 异常消息中显示的文件标识最大长度 */
    private static final int MAX_DISPLAY_LENGTH = 64;
    
    public FileNotFoundException(String fileKey) {
        super(fileKey, ResultCode.FILE_NOT_FOUND, formatMessage(fileKey));
    }
    
    @Override
    public String getSource() {
        return (String) super.source;
    }
    
    /**
     * 格式化异常消息，对超长文件标识进行截断
     */
    private static String formatMessage(String fileKey) {
        if (fileKey == null) {
            return "文件不存在: null";
        }
        
        String displayKey = fileKey.length() <= MAX_DISPLAY_LENGTH
            ? fileKey
            : fileKey.substring(0, MAX_DISPLAY_LENGTH) + "...(实际长度: " + fileKey.length() + ")";
        
        return String.format("文件不存在: '%s'", displayKey);
    }
}
