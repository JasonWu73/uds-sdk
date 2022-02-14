package com.qgschina.udssdk.server.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 注解在 {@code public} 方法上, 标识可供 Client 触发的信号, 且不能存在同名的"信号触发"
 *
 * <ul>
 *   <li>该注解的方法会被 Client 异步调用, 故方法可不需要返回值</li>
 *   <li>"信号订阅"需要调用
 *   {@link com.qgschina.udssdk.server.Server#registerSubSignal(String)}</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface UdsSignal {

  /**
   * Client 调用时所使用的方法名
   */
  String value() default "";
}
