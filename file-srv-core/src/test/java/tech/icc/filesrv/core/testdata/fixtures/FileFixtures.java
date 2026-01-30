package tech.icc.filesrv.core.testdata.fixtures;

import tech.icc.filesrv.core.domain.files.FileReference;

/**
 * 文件场景预设
 * <p>
 * 提供常见文件类型和场景的快捷创建方法。
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 创建图片文件
 * FileReference image = FileFixtures.imageFile();
 * 
 * // 创建大文件
 * FileReference large = FileFixtures.largeFile();
 * 
 * // 创建公开文件
 * FileReference pub = FileFixtures.publicFile();
 * }</pre>
 */
public class FileFixtures {

    /**
     * 图片文件（通用）
     * <p>
     * 类型: image/jpeg<br>
     * 大小: 2MB<br>
     * 文件名: photo.jpg<br>
     * 用途: 测试图片上传、缩略图生成
     *
     * @return JPEG 图片文件引用
     */
    public static FileReference imageFile() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * PNG 图片文件
     * <p>
     * 类型: image/png<br>
     * 大小: 1.5MB<br>
     * 文件名: screenshot.png<br>
     * 用途: 测试 PNG 格式处理
     *
     * @return PNG 图片文件引用
     */
    public static FileReference pngImage() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 文档文件（通用）
     * <p>
     * 类型: application/pdf<br>
     * 大小: 5MB<br>
     * 文件名: document.pdf<br>
     * 用途: 测试文档上传、预览
     *
     * @return PDF 文档文件引用
     */
    public static FileReference documentFile() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * Word 文档
     * <p>
     * 类型: application/vnd.openxmlformats-officedocument.wordprocessingml.document<br>
     * 大小: 3MB<br>
     * 文件名: report.docx<br>
     * 用途: 测试 Office 文件处理
     *
     * @return DOCX 文档文件引用
     */
    public static FileReference wordDocument() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 视频文件
     * <p>
     * 类型: video/mp4<br>
     * 大小: 50MB<br>
     * 文件名: video.mp4<br>
     * 用途: 测试大文件上传、视频处理
     *
     * @return MP4 视频文件引用
     */
    public static FileReference videoFile() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 音频文件
     * <p>
     * 类型: audio/mpeg<br>
     * 大小: 8MB<br>
     * 文件名: audio.mp3<br>
     * 用途: 测试音频文件处理
     *
     * @return MP3 音频文件引用
     */
    public static FileReference audioFile() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 压缩文件
     * <p>
     * 类型: application/zip<br>
     * 大小: 20MB<br>
     * 文件名: archive.zip<br>
     * 用途: 测试压缩文件上传、解压
     *
     * @return ZIP 压缩文件引用
     */
    public static FileReference zipFile() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 文本文件
     * <p>
     * 类型: text/plain<br>
     * 大小: 100KB<br>
     * 文件名: readme.txt<br>
     * 用途: 测试文本文件处理
     *
     * @return TXT 文本文件引用
     */
    public static FileReference textFile() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * JSON 文件
     * <p>
     * 类型: application/json<br>
     * 大小: 50KB<br>
     * 文件名: config.json<br>
     * 用途: 测试 JSON 文件解析
     *
     * @return JSON 文件引用
     */
    public static FileReference jsonFile() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 大文件（超过10MB）
     * <p>
     * 类型: application/octet-stream<br>
     * 大小: 100MB<br>
     * 文件名: large-file.bin<br>
     * 用途: 测试分片上传、大文件处理
     *
     * @return 100MB 大文件引用
     */
    public static FileReference largeFile() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 小文件（可同步上传）
     * <p>
     * 类型: text/plain<br>
     * 大小: 1KB<br>
     * 文件名: small.txt<br>
     * 用途: 测试同步上传、秒传
     *
     * @return 1KB 小文件引用
     */
    public static FileReference smallFile() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 公开访问的文件
     * <p>
     * AccessControl: public<br>
     * 用途: 测试公开访问、外链生成
     *
     * @return 公开访问的文件引用
     */
    public static FileReference publicFile() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 随机文件（使用 DataFaker）
     * <p>
     * 所有字段随机生成<br>
     * 用途: 压力测试、批量数据生成
     *
     * @return 随机文件引用
     */
    public static FileReference randomFile() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }
}
