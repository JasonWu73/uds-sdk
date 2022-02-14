package com.qgschina.udssdk.server.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 注解 {@code public} 方法上, 标识可供 Client 调用的方法, 且不能存在同名的"方法调用"
 * <p>
 * 注意: 若方法返回值存在 <b>{@code byte[]}</b>, 则会被转为 Base64 字符串再给 Client
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface UdsMethod {

  /**
   * Client 调用时所使用的方法名
   */
  String value() default "";
}
