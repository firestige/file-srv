package tech.icc.filesrv.core.testdata.fixtures;

import tech.icc.filesrv.core.domain.files.FileReference;
import tech.icc.filesrv.core.testdata.TestDataBuilders;

/**
 * 文件场景预设
 * <p>
 * 提供常见文件类型的快捷创建方法，简化测试代码。
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 创建图片文件
 * FileReference image = FileFixtures.imageFile();
 *
 * // 创建文档文件
 * FileReference doc = FileFixtures.documentFile();
 *
 * // 创建大文件
 * FileReference large = FileFixtures.largeFile();
 * }</pre>
 */
public class FileFixtures {

    /**
     * 创建图片文件（JPEG）
     */
    public static FileReference imageFile() {
        return TestDataBuilders.aFileReference()
                .withFilename("photo.jpg")
                .withContentType("image/jpeg")
                .withSize(2 * 1024 * 1024L) // 2MB
                .build();
    }

    /**
     * 创建 PNG 图片文件
     */
    public static FileReference pngImage() {
        return TestDataBuilders.aFileReference()
                .withFilename("screenshot.png")
                .withContentType("image/png")
                .withSize(1 * 1024 * 1024L) // 1MB
                .build();
    }

    /**
     * 创建 PDF 文档文件
     */
    public static FileReference documentFile() {
        return TestDataBuilders.aFileReference()
                .withFilename("report.pdf")
                .withContentType("application/pdf")
                .withSize(500 * 1024L) // 500KB
                .build();
    }

    /**
     * 创建 Word 文档文件
     */
    public static FileReference wordDocument() {
        return TestDataBuilders.aFileReference()
                .withFilename("document.docx")
                .withContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .withSize(150 * 1024L) // 150KB
                .build();
    }

    /**
     * 创建视频文件（MP4）
     */
    public static FileReference videoFile() {
        return TestDataBuilders.aFileReference()
                .withFilename("video.mp4")
                .withContentType("video/mp4")
                .withSize(50 * 1024 * 1024L) // 50MB
                .build();
    }

    /**
     * 创建音频文件（MP3）
     */
    public static FileReference audioFile() {
        return TestDataBuilders.aFileReference()
                .withFilename("music.mp3")
                .withContentType("audio/mpeg")
                .withSize(5 * 1024 * 1024L) // 5MB
                .build();
    }

    /**
     * 创建压缩文件（ZIP）
     */
    public static FileReference zipFile() {
        return TestDataBuilders.aFileReference()
                .withFilename("archive.zip")
                .withContentType("application/zip")
                .withSize(10 * 1024 * 1024L) // 10MB
                .build();
    }

    /**
     * 创建文本文件
     */
    public static FileReference textFile() {
        return TestDataBuilders.aFileReference()
                .withFilename("readme.txt")
                .withContentType("text/plain")
                .withSize(10 * 1024L) // 10KB
                .build();
    }

    /**
     * 创建 JSON 文件
     */
    public static FileReference jsonFile() {
        return TestDataBuilders.aFileReference()
                .withFilename("config.json")
                .withContentType("application/json")
                .withSize(5 * 1024L) // 5KB
                .build();
    }

    /**
     * 创建大文件（100MB）
     */
    public static FileReference largeFile() {
        return TestDataBuilders.aFileReference()
                .withRandomFilename()
                .withRandomContentType()
                .withSize(100 * 1024 * 1024L) // 100MB
                .build();
    }

    /**
     * 创建小文件（1KB）
     */
    public static FileReference smallFile() {
        return TestDataBuilders.aFileReference()
                .withFilename("tiny.txt")
                .withContentType("text/plain")
                .withSize(1024L) // 1KB
                .build();
    }

    /**
     * 创建公开访问的文件
     */
    public static FileReference publicFile() {
        return TestDataBuilders.aFileReference()
                .withFilename("public-image.jpg")
                .withContentType("image/jpeg")
                .withPublicAccess()
                .build();
    }

    /**
     * 创建带随机属性的文件
     */
    public static FileReference randomFile() {
        return TestDataBuilders.aFileReference()
                .withRandomFilename()
                .withRandomContentType()
                .withRandomSize()
                .build();
    }
}
