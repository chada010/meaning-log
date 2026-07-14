package com.chad.meaninglog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MqConfig {

    public static final String EXCHANGE = "ai.task.exchange";
    public static final String DLX_EXCHANGE = "ai.task.dlx";

    public static final String QUEUE_LOG_ANALYZE = "ai.task.log.analyze";
    public static final String QUEUE_LOG_REFINE = "ai.task.log.refine";
    public static final String QUEUE_REPORT_GENERATE = "ai.task.report.generate";
    public static final String QUEUE_REPORT_REFINE = "ai.task.report.refine";
    public static final String QUEUE_CHAT = "ai.task.chat";
    public static final String QUEUE_DLQ = "ai.task.dlq";

    public static final String RK_LOG_ANALYZE = "ai.task.log.analyze";
    public static final String RK_LOG_REFINE = "ai.task.log.refine";
    public static final String RK_REPORT_GENERATE = "ai.task.report.generate";
    public static final String RK_REPORT_REFINE = "ai.task.report.refine";
    public static final String RK_CHAT = "ai.task.chat";

    @Bean
    public TopicExchange aiTaskExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public FanoutExchange aiTaskDlxExchange() {
        return new FanoutExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue aiTaskDlq() {
        return QueueBuilder.durable(QUEUE_DLQ).build();
    }

    @Bean
    public Binding aiTaskDlqBinding() {
        return BindingBuilder.bind(aiTaskDlq()).to(aiTaskDlxExchange());
    }

    private Queue businessQueue(String name) {
        return QueueBuilder.durable(name)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .build();
    }

    @Bean
    public Queue aiTaskLogAnalyzeQueue() {
        return businessQueue(QUEUE_LOG_ANALYZE);
    }

    @Bean
    public Queue aiTaskLogRefineQueue() {
        return businessQueue(QUEUE_LOG_REFINE);
    }

    @Bean
    public Queue aiTaskReportGenerateQueue() {
        return businessQueue(QUEUE_REPORT_GENERATE);
    }

    @Bean
    public Queue aiTaskReportRefineQueue() {
        return businessQueue(QUEUE_REPORT_REFINE);
    }

    @Bean
    public Queue aiTaskChatQueue() {
        return businessQueue(QUEUE_CHAT);
    }

    @Bean
    public Binding aiTaskLogAnalyzeBinding() {
        return BindingBuilder.bind(aiTaskLogAnalyzeQueue()).to(aiTaskExchange()).with(RK_LOG_ANALYZE);
    }

    @Bean
    public Binding aiTaskLogRefineBinding() {
        return BindingBuilder.bind(aiTaskLogRefineQueue()).to(aiTaskExchange()).with(RK_LOG_REFINE);
    }

    @Bean
    public Binding aiTaskReportGenerateBinding() {
        return BindingBuilder.bind(aiTaskReportGenerateQueue()).to(aiTaskExchange()).with(RK_REPORT_GENERATE);
    }

    @Bean
    public Binding aiTaskReportRefineBinding() {
        return BindingBuilder.bind(aiTaskReportRefineQueue()).to(aiTaskExchange()).with(RK_REPORT_REFINE);
    }

    @Bean
    public Binding aiTaskChatBinding() {
        return BindingBuilder.bind(aiTaskChatQueue()).to(aiTaskExchange()).with(RK_CHAT);
    }

    @Bean
    public MessageConverter aiTaskMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter aiTaskMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(aiTaskMessageConverter);
        return template;
    }
}
