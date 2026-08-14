package com.zhangbo.yuaiagent.service;

import com.zhangbo.yuaiagent.constant.FileConstant;
import com.zhangbo.yuaiagent.rag.MyKeywordEnricher;
import com.zhangbo.yuaiagent.rag.MyTokenTextSplitter;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识库服务
 * 管理上传的文件，自动加载、分割、向量化，支持检索
 */
@Service
@Slf4j
public class KnowledgeBaseService {

    @Resource
    private EmbeddingModel dashscopeEmbeddingModel;

    @Resource
    private MyTokenTextSplitter myTokenTextSplitter;

    @Resource
    private MyKeywordEnricher myKeywordEnricher;

    // 知识库向量库（内存版）
    private SimpleVectorStore knowledgeVectorStore;

    // 上传文件保存目录
    private static final String UPLOAD_DIR = FileConstant.FILE_SAVE_DIR + "/upload";

    // 向量库持久化文件
    private static final String VECTOR_STORE_FILE = FileConstant.FILE_SAVE_DIR + "/knowledge_vector_store.json";

    @PostConstruct
    public void init() {
        // 初始化向量库
        knowledgeVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel).build();

        File storeFile = new File(VECTOR_STORE_FILE);
        if (storeFile.exists() && storeFile.length() > 0) {
            // 已有持久化文件，直接加载（毫秒级，不阻塞启动）
            knowledgeVectorStore.load(storeFile);
            log.info("知识库向量库从本地文件加载完成: {}", VECTOR_STORE_FILE);
        } else {
            // 首次启动，异步加载已上传文件，不阻塞 Spring 启动
            log.info("未找到向量库持久化文件，启动异步加载任务");
            new Thread(() -> {
                try {
                    loadExistingFiles();
                    knowledgeVectorStore.save(storeFile);
                    log.info("知识库向量库已持久化到 {}", VECTOR_STORE_FILE);
                } catch (Exception e) {
                    log.error("异步加载知识库失败", e);
                }
            }, "knowledge-base-loader").start();
        }
    }

    /**
     * 加载已上传的文件到知识库
     */
    private void loadExistingFiles() {
        File dir = new File(UPLOAD_DIR);
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return;
        }
        log.info("开始加载已上传的文件到知识库，共 {} 个文件", files.length);
        for (File file : files) {
            if (file.isFile()) {
                try {
                    addFileToKnowledgeBase(file);
                } catch (Exception e) {
                    log.error("加载文件到知识库失败: {}", file.getName(), e);
                }
            }
        }
        log.info("已上传文件加载完成");
    }

    /**
     * 添加文件到知识库
     *
     * @param file 上传的文件
     */
    public void addFileToKnowledgeBase(File file) {
        String filename = file.getName();
        String extension = getFileExtension(filename).toLowerCase();

        List<Document> documents = new ArrayList<>();

        try {
            FileSystemResource resource = new FileSystemResource(file);

            // 根据文件类型选择不同的加载器
            if (extension.equals("md") || extension.equals("markdown")) {
                // Markdown 文件
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(true)
                        .withIncludeBlockquote(true)
                        .withAdditionalMetadata("filename", filename)
                        .withAdditionalMetadata("source", "upload")
                        .build();
                MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);
                documents.addAll(reader.get());
            } else {
                // 文本文件（txt、json、xml 等都按文本处理）
                TextReader textReader = new TextReader(resource);
                textReader.getCustomMetadata().put("filename", filename);
                textReader.getCustomMetadata().put("source", "upload");
                documents.addAll(textReader.get());
            }

            if (documents.isEmpty()) {
                log.warn("文件 {} 没有可加载的内容", filename);
                return;
            }

            // 文本分割
            List<Document> splitDocuments = myTokenTextSplitter.splitCustomized(documents);

            // 关键词丰富
            List<Document> enrichedDocuments = myKeywordEnricher.enrichDocuments(splitDocuments);

            // 添加到向量库
            knowledgeVectorStore.add(enrichedDocuments);
            // 持久化到本地文件，下次启动直接加载
            knowledgeVectorStore.save(new File(VECTOR_STORE_FILE));

            log.info("文件 {} 已添加到知识库，共 {} 个文档块", filename, enrichedDocuments.size());

        } catch (Exception e) {
            log.error("添加文件到知识库失败: {}", filename, e);
            throw new RuntimeException("添加文件到知识库失败: " + e.getMessage());
        }
    }

    /**
     * 从知识库中检索相关内容
     *
     * @param query  查询问题
     * @param topK   返回结果数量
     * @return 相关文档列表
     */
    public List<Document> search(String query, int topK) {
        if (knowledgeVectorStore == null) {
            return new ArrayList<>();
        }
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build();
        return knowledgeVectorStore.similaritySearch(request);
    }

    /**
     * 从知识库中检索相关内容（默认返回 5 条）
     *
     * @param query 查询问题
     * @return 相关文档列表
     */
    public List<Document> search(String query) {
        return search(query, 5);
    }

    /**
     * 清空知识库
     */
    public void clear() {
        // SimpleVectorStore 没有直接的 clear 方法，重新创建一个
        knowledgeVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel).build();
        // 删除持久化文件
        File storeFile = new File(VECTOR_STORE_FILE);
        if (storeFile.exists() && storeFile.delete()) {
            log.info("知识库持久化文件已删除: {}", VECTOR_STORE_FILE);
        }
        log.info("知识库已清空");
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) {
            return "";
        }
        return filename.substring(lastDot + 1);
    }
}
