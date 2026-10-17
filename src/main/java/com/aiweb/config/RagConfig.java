package com.aiweb.config;

import com.aiweb.rag.NormalizingEmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 向量库配置。
 * 位置：src/main/java/com/aiweb/config/RagConfig.java
 * 作用：把"本地 ONNX 嵌入模型"和"内存向量库 SimpleVectorStore"组装成一个 VectorStore Bean。
 *
 * 为什么注入具体类型 TransformersEmbeddingModel？
 *   OpenAI 的 starter 因为有 api-key，也会自动建一个 EmbeddingModel Bean；
 *   两个 EmbeddingModel 同时存在时，若按接口类型注入会"不知道选谁"而报错。
 *   这里直接要具体的 TransformersEmbeddingModel 类型，就只会匹配本地这一个，消歧。
 *
 * 为什么外面要包一层 NormalizingEmbeddingModel？
 *   e5 官方用法要求嵌入向量 L2 归一化，但 ONNX 模型与 Spring AI 都不做
 *   （详见 NormalizingEmbeddingModel 的注释：不归一化时余弦相似度全糊在 0.86+，
 *   检索排序随机）。包装后语料构建与检索两侧都走归一化，语义度量才正确。
 */
@Configuration
public class RagConfig {

    @Bean
    public VectorStore vectorStore(TransformersEmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(new NormalizingEmbeddingModel(embeddingModel)).build();
    }
}
