package com.springai.xilianai.util;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class SentenceSegmenter {
    
    // 中文标点：。！？；，、
    // 英文标点：.!?;,
    private static final Pattern SENTENCE_END_PATTERN = 
        Pattern.compile("([。！？.!?])(?![^\\{<\\[（(]*[\\}>\\])）)])");
    
    private static final Pattern PAUSE_PATTERN = 
        Pattern.compile("([，,；;])\\s*");
    
    /**
     * 智能分割文本为句子
     * @param text 输入文本
     * @return 句子列表
     */
    public List<String> segmentIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();
        
        if (text == null || text.trim().isEmpty()) {
            return sentences;
        }
        
        // 使用正则表达式找到句子边界
        String[] parts = SENTENCE_END_PATTERN.split(text);
        
        // 重建句子（包括标点）
        int startIndex = 0;
        java.util.regex.Matcher matcher = SENTENCE_END_PATTERN.matcher(text);
        
        while (matcher.find()) {
            int endIndex = matcher.end();
            String sentence = text.substring(startIndex, endIndex).trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
            startIndex = endIndex;
        }
        
        // 处理最后一段（可能没有结束标点）
        if (startIndex < text.length()) {
            String lastPart = text.substring(startIndex).trim();
            if (!lastPart.isEmpty()) {
                sentences.add(lastPart);
            }
        }
        
        return sentences;
    }
    
    /**
     * 是否需要立即合成（根据标点判断）
     */
    public boolean shouldSynthesizeImmediately(String sentence) {
        if (sentence == null || sentence.isEmpty()) {
            return false;
        }
        
        // 以句号、感叹号、问号结尾的句子需要立即合成
        char lastChar = sentence.charAt(sentence.length() - 1);
        return lastChar == '。' || lastChar == '！' || lastChar == '？' ||
               lastChar == '.' || lastChar == '!' || lastChar == '?';
    }
    
    /**
     * 检查是否为自然停顿点
     */
    public boolean isNaturalPause(String text) {
        if (text.length() < 10) return false; // 太短的文本不视为停顿
        
        // 检查是否有逗号、分号等停顿标点
        return PAUSE_PATTERN.matcher(text).find();
    }
}