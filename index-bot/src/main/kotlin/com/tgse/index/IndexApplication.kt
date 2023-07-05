package com.tgse.index

import com.tgse.index.infrastructure.provider.BotProvider
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.ContextClosedEvent
import org.springframework.scheduling.annotation.EnableScheduling
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.bots.DefaultBotOptions.ProxyType
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import kotlin.properties.Delegates
import kotlin.system.exitProcess

@Configuration
@ConfigurationProperties(prefix = "bot")
class BotProperties {
    var creator by Delegates.notNull<String>()
    var token by Delegates.notNull<String>()
}

@Configuration
@ConfigurationProperties(prefix = "elastic")
class ElasticProperties {
    var schema by Delegates.notNull<String>()
    var hostname by Delegates.notNull<String>()
    var port by Delegates.notNull<Int>()
}

@Configuration
@ConfigurationProperties(prefix = "proxy")
class ProxyProperties {
    var enabled by Delegates.notNull<Boolean>()
    var type by Delegates.notNull<ProxyType>()
    var ip by Delegates.notNull<String>()
    var port by Delegates.notNull<Int>()
}



@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = ["org.telegram.telegrambots.starter","com.tgse.index"])
class IndexApplication {
    @Bean
    fun defaultBotOption(proxyProperties:ProxyProperties) = DefaultBotOptions().apply {
        if (proxyProperties.enabled) {
            proxyType = proxyProperties.type
            proxyHost = proxyProperties.ip
            proxyPort = proxyProperties.port
        }
    }

    val logger get() = LoggerFactory.getLogger(this::class.java)
    @Bean
    fun listenCloseEvent(botProvider: BotProvider,botProperties: BotProperties) = ApplicationListener<ContextClosedEvent> {
        val logger = logger
        logger.info("context is done ${it.source}",)
        botProvider.destroy()
        logger.info("bot destroyed")
        botProvider.send(
            SendMessage(botProperties.creator,"bot destroyed")
        )
//        exitProcess(1)
    }


}

fun main(args: Array<String>) {
    runApplication<IndexApplication>(*args)
}

