package tech.icc.filesrv.common.exception.validation;

import lombok.Getter;
import tech.icc.filesrv.common.constants.ResultCode;

@Getter
public class FileKeyTooLongException extends ValidationException {
    
    /** 异常消息中显示的文件标识最大长度 */
    private static final int MAX_DISPLAY_LENGTH = 32;
    
    private final int maxLength;
    
    public FileKeyTooLongException(int maxLength, String fKey) {
        super(fKey, ResultCode.INVALID_FKEY, formatMessage(fKey, maxLength));
        this.maxLength = maxLength;
    }

    @Override
    public String getSource() {
        return (String) super.source;
    }
    
    /**
     * 格式化异常消息，对超长文件标识进行截断
     * <p>
     * 示例输出：
     * - 短字符串：文件标识 'abc' 长度超过限制 128
     * - 长字符串：文件标识 'abcdefghijklmnopqrstuvwxyz123456...'(实际长度: 500) 长度超过限制 128
     */
    private static String formatMessage(String fKey, int maxLength) {
        if (fKey == null) {
            return String.format("文件标识为空，长度超过限制 %d", maxLength);
        }
        
        String displayKey = fKey.length() <= MAX_DISPLAY_LENGTH 
            ? fKey 
            : fKey.substring(0, MAX_DISPLAY_LENGTH) + "...(实际长度: " + fKey.length() + ")";
        
        return String.format("文件标识 '%s' 长度超过限制 %d", displayKey, maxLength);
    }
}
