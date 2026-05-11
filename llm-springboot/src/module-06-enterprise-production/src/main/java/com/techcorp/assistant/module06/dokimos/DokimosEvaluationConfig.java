package com.techcorp.assistant.module06.dokimos;

import dev.dokimos.core.JudgeLM;
import dev.dokimos.springai.SpringAiSupport;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DokimosEvaluationConfig {

    @Value("${openai.api.key}")
    private String openAiApiKey;

    @Value("${dokimos.judge.model:gpt-4o}")
    private String judgeModelName;

    @Value("${dokimos.judge.temperature:0.0}")
    private Double judgeTemperature;

    /**
     * Spring AI ChatModel for Dokimos judge LLM.
     * Uses separate model configuration optimized for evaluation.
     */
    @Bean("dokimosJudgeChatModel")
    public ChatModel dokimosJudgeChatModel() {
        return new OpenAiChatModel(
                new OpenAiApi(openAiApiKey),
                OpenAiChatOptions.builder()
                        .withModel(judgeModelName)
                        .withTemperature(judgeTemperature)
                        .build()
        );
    }

    /**
     * Dokimos JudgeLM using Spring AI ChatModel.
     * This LLM is used for LLM-as-judge evaluations.
     */
    @Bean
    public JudgeLM judgeLM(ChatModel dokimosJudgeChatModel) {
        return SpringAiSupport.asJudge(dokimosJudgeChatModel);
    }
}
