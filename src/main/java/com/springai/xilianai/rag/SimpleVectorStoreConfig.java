package com.springai.xilianai.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 初始化基于内存的向量数据库配置 Bean
 */
@Configuration
public class SimpleVectorStoreConfig {
    @Resource
    private DocumentLoader documentLoader;

    @Resource
    private TokenTextSplitter tokenTextSplitter;

    @Resource
    private KeywordEnricher keywordEnricher;

    @Bean
    VectorStore loveAppVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        org.springframework.ai.vectorstore.SimpleVectorStore simpleVectorStore = org.springframework.ai.vectorstore.SimpleVectorStore.builder(dashscopeEmbeddingModel).build();
        // 加载文档
        List<Document> documentList = documentLoader.loadMarkdowns();
        // 自主切分文档
//        List<Document> splitDocuments = tokenTextSplitter.splitCustomized(documentList);
        // 自动补充关键词元信息
        List<Document> enrichedDocuments = keywordEnricher.enrichDocuments(documentList);
        simpleVectorStore.add(enrichedDocuments);
        return simpleVectorStore;
    }
}
