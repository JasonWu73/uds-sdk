package com.qgschina.udssdk.common.constant;

import lombok.RequiredArgsConstructor;

/**
 * 各语言 SDK 内部间传递的请求类型
 */
@RequiredArgsConstructor
public enum InnerReqType {

  /**
   * 方法调用
   */
  CALL_METHOD("callMethod"),

  /**
   * 信号触发 (异步方法调用)
   */
  SIGNAL("triggerSignal"),

  /**
   * 信号订阅
   */
  SIGNAL_SUB("subSignal"),

  /**
   * 获取地址空间 - 获取可被调用的方法
   */
  GET_ALL_METHODS("getMethod"),

  /**
   * 获取地址空间 - 获取可被触发或订阅的信号
   */
  GET_ALL_SIGNALS("getSignal"),

  /**
   * 获取地址空间 - 获取可被调用的方法, 及可被触发或订阅的信号
   */
  GET_ALL_METHODS_SIGNALS("getMethodAndSignal");

  private final String value;

  public String value() {
    return value;
  }
}
