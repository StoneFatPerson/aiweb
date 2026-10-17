package com.aiweb.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * L2 归一化 Embedding 装饰器。
 * 位置：src/main/java/com/aiweb/rag/NormalizingEmbeddingModel.java
 *
 * 为什么需要它（排查过程见提交历史）：
 *   e5 系列模型的官方用法明确要求「嵌入向量必须 L2 归一化」——归一化后余弦相似度
 *   才等于向量点积，语义方向才被正确度量。但：
 *     1) intfloat/e5-small-v2 的 ONNX 导出版不在模型内部做归一化；
 *     2) Spring AI 1.1.8 的 TransformersEmbeddingModel 的 mean pooling 之后
 *        也直接输出，同样不做归一化（反编译字节码确认）。
 *   后果：每个向量带一个巨大的「共模分量」（实测范数约 7.9，归一化后应为 1.0），
 *   余弦相似度被共模主导——所有文档与任意查询的分数挤在 0.86~0.87，
 *   检索排序基本随机（用户短句问「学硕专硕区别」排不进 top4）。
 *
 * 做法：包装自动配置的 TransformersEmbeddingModel，对每次输出做 L2 归一化，
 *   向量库的构建（CorpusInitializer）与检索（RagService）都经过它，
 *   两侧语义空间一致，余弦相似度恢复正常区分度。
 */
public class NormalizingEmbeddingModel implements EmbeddingModel {

    private final EmbeddingModel delegate;

    public NormalizingEmbeddingModel(EmbeddingModel delegate) {
        this.delegate = delegate;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        EmbeddingResponse response = delegate.call(request);
        List<Embedding> normalized = new ArrayList<>(response.getResults().size());
        int i = 0;
        for (Embedding e : response.getResults()) {
            normalized.add(new Embedding(normalize(e.getOutput()), i++));
        }
        return new EmbeddingResponse(normalized, response.getMetadata());
    }

    @Override
    public float[] embed(Document document) {
        return normalize(delegate.embed(document));
    }

    /** L2 归一化：v / ||v||；零向量原样返回（防御） */
    static float[] normalize(float[] v) {
        if (v == null || v.length == 0) return v;
        double sum = 0;
        for (float x : v) sum += (double) x * x;
        double norm = Math.sqrt(sum);
        if (norm == 0 || !Double.isFinite(norm)) return v;
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = (float) (v[i] / norm);
        return out;
    }
}
