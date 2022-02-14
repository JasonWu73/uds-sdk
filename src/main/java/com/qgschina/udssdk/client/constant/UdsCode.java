package com.qgschina.udssdk.client.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * UDS 响应码
 */
@RequiredArgsConstructor
public enum UdsCode {

  /**
   * 正常返回
   */
  SUCCESS(0, "正常返回"),

  /**
   * 未连接
   */
  NOT_CONNECTED(1, "未连接"),

  /**
   * 超时
   */
  OVER_TIME(2, "超时"),

  /**
   * 函数调用失败
   */
  METHOD_CALL_ERROR(3, "函数调用失败"),

  /**
   * 信号订阅失败
   */
  SIGNAL_SUB_ERROR(4, "信号订阅失败");

  private final int value;

  @Getter
  private final String description;

  public int value() {
    return value;
  }
}
