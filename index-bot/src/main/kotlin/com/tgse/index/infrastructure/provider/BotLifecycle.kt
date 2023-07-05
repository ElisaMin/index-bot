package com.tgse.index.infrastructure.provider

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

abstract class BotLifecycle: CoroutineScope by CoroutineScope(userRequestDispatcher), DisposableBean {

    @Suppress("NOTHING_TO_INLINE")
    protected inline fun makeCoroutine (noinline block:suspend CoroutineScope.() ->Unit ) = launch(
        context = userRequestDispatcher,
        block = block
    )
    protected suspend inline fun <R> withCoroutine (noinline block:suspend CoroutineScope.() -> R ) = withContext(
        context = userRequestDispatcher,
        block = block
    )

    override fun destroy() {
        cancel()
        logger.info("BotLifecycle destroyed")
    }
    companion object {
        private val logger = LoggerFactory.getLogger(BotLifecycle::class.java)
        @JvmStatic
        protected val pool: ExecutorService = Executors.newCachedThreadPool {
            Thread(it).apply {
                name = "处理请求@$name"
                isDaemon = true
            }
        }
        @JvmStatic
        protected val userRequestDispatcher = pool.asCoroutineDispatcher()
//        @JvmStatic
//        val userRequestDispatcher = Dispatchers.IO
    }
}