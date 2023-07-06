package com.tgse.index

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import java.net.Proxy
import kotlin.properties.Delegates

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
    var type by Delegates.notNull<Proxy.Type>()
    var ip by Delegates.notNull<String>()
    var port by Delegates.notNull<Int>()
}

@SpringBootApplication
@EnableScheduling
class IndexApplication

fun main(args: Array<String>) {
    runApplication<IndexApplication>(*args)
}