package com.aiweb.config;

import com.aiweb.service.RagService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 语料加载器：应用启动时把考研知识库灌进向量库。
 * 位置：src/main/java/com/aiweb/config/CorpusInitializer.java
 * 作用：
 *   1. 若磁盘上已有持久化文件（kaoyan-vectorstore.json），直接 load，省去重新算向量；
 *   2. 否则读取 resources/kaoyan-corpus.json，转成 Document 入库，再 save 到磁盘。
 * 这样重启不重复做耗时的"文字→向量"计算。
 *
 * 注意：首次启动会从 hugging-face.cn 下载 e5-small-v2 模型（约上百 MB），需要联网；
 * 若下载失败，这里 catch 住异常，保证"认知科普"之外的功能照常运行。
 */
@Component
public class CorpusInitializer {

    /** 向量库持久化文件路径（项目根目录，已加入 .gitignore，不进仓库） */
    private static final Path STORE_FILE = Path.of("kaoyan-vectorstore.json");

    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;

    public CorpusInitializer(VectorStore vectorStore, ObjectMapper objectMapper) {
        this.vectorStore = vectorStore;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        try {
            if (Files.exists(STORE_FILE)) {
                ((SimpleVectorStore) vectorStore).load(STORE_FILE.toFile());
                System.out.println("[RAG] 已从 " + STORE_FILE + " 加载向量库，无需重新计算向量");
                return;
            }

            List<CorpusItem> items = loadCorpus();
            List<Document> docs = new ArrayList<>();
            for (CorpusItem it : items) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("category", it.category);
                meta.put("title", it.title);
                meta.put("sourceId", it.id);
                // id 作为文档主键，content 是真正被嵌入和检索的文本
                docs.add(new Document(it.id, it.content, meta));
            }

            vectorStore.add(docs);
            ((SimpleVectorStore) vectorStore).save(STORE_FILE.toFile());
            System.out.println("[RAG] 已构建向量库并写入 " + STORE_FILE + "，共 " + docs.size() + " 条资料");
        } catch (Exception e) {
            // 不向上抛，避免拖垮整个应用；其他模块（诊断/计划/对话）仍可正常使用
            System.err.println("[RAG] 知识库初始化失败（不影响其他功能）：" + e.getMessage());
        }
    }

    private List<CorpusItem> loadCorpus() throws Exception {
        try (InputStream in = new ClassPathResource("kaoyan-corpus.json").getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<List<CorpusItem>>() {});
        }
    }

    /** 语料 JSON 的结构映射 */
    public static class CorpusItem {
        public String id;
        public String category;
        public String title;
        public String content;
    }
}
