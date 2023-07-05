package com.tgse.index

/**
 * 初始化命令异常
 */
class SetCommandException(msg: String) : RuntimeException(msg)

/**
 * 不匹配异常
 */
class MismatchException(msg: String) : RuntimeException(msg)

class ElasticSearchException(exception: Throwable):RuntimeException(exception.message,exception)